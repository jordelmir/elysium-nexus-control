//
// Elysium Nexus — Mac Agent
// X25519 + ChaCha20-Poly1305 + HKDF.
//
// The agent generates an X25519 keypair on launch.
// When a phone connects, both sides exchange public
// keys, derive a shared secret via ECDH, and use
// HKDF-SHA256 to derive a 256-bit symmetric key. All
// post-handshake frames (including the PIN digits)
// are encrypted with ChaCha20-Poly1305 using a fresh
// 12-byte nonce per frame.
//
// The phone sends the PIN one digit at a time, each
// in its own encrypted frame. This lets the agent
// display the digits as they arrive and lets the
// phone "see" the agent typing them (handy for
// debugging the UX). The server compares against
// the locally-generated PIN and emits a PAIR_OK
// frame when all 6 digits match.
//
import Foundation
import CryptoKit

//
// 32-byte ChaCha20-Poly1305 key derived from a
// 32-byte X25519 shared secret via HKDF-SHA256.
//
enum ChannelCipher {
    /// Derive a 32-byte symmetric key from the
    /// agent's private key + the phone's public
    /// key. Both sides will derive the same key
    /// (ECDH).
    static func deriveKey(myPrivate: Curve25519.KeyAgreement.PrivateKey,
                          theirPublic: Curve25519.KeyAgreement.PublicKey) -> SymmetricKey {
        let shared = try! myPrivate.sharedSecretFromKeyAgreement(with: theirPublic)
        return shared.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: "elysium-nexus-v1".data(using: .utf8)!,
            sharedInfo: "elysium-channel".data(using: .utf8)!,
            outputByteCount: 32
        )
    }

    /// Encrypts `plaintext` with the channel key.
    /// Returns `nonce ‖ ciphertext ‖ tag`. The
    /// nonce is fresh per call.
    static func seal(_ plaintext: Data, key: SymmetricKey) -> Data {
        let nonce = ChaChaPoly.Nonce()
        let box = try! ChaChaPoly.seal(plaintext, using: key, nonce: nonce)
        var out = Data()
        out.append(contentsOf: nonce)
        out.append(box.ciphertext)
        out.append(box.tag)
        return out
    }

    /// Decrypts `ciphertextWithNonce` (the same
    /// `nonce ‖ ciphertext ‖ tag` blob produced
    /// by `seal`). Throws on auth failure.
    static func open(_ ciphertextWithNonce: Data, key: SymmetricKey) throws -> Data {
        guard ciphertextWithNonce.count >= 12 + 16 else {
            throw CryptoError.shortCiphertext
        }
        let nonceData = ciphertextWithNonce.prefix(12)
        let tagData = ciphertextWithNonce.suffix(16)
        let body = ciphertextWithNonce.dropFirst(12).dropLast(16)
        let nonce = try ChaChaPoly.Nonce(data: nonceData)
        let box = try ChaChaPoly.SealedBox(nonce: nonce, ciphertext: body, tag: tagData)
        return try ChaChaPoly.open(box, using: key)
    }

    /// Phase 32 — seal with an explicit nonce and
    /// optional AEAD associated data (used by the
    /// directional [ChannelKeys] path; legacy `seal`
    /// above keeps its original random nonce).
    static func seal(_ plaintext: Data, key: SymmetricKey, nonce: Data, ad: Data? = nil) -> Data {
        let n = try! ChaChaPoly.Nonce(data: nonce)
        // Important: when `ad` is nil we must use the
        // no-AAD overload — authenticating an empty
        // Data() would change the tag vs. the Android
        // side (Kotlin omits updateAAD when ad == null).
        let box: ChaChaPoly.SealedBox
        if let ad {
            box = try! ChaChaPoly.seal(plaintext, using: key, nonce: n, authenticating: ad)
        } else {
            box = try! ChaChaPoly.seal(plaintext, using: key, nonce: n)
        }
        var out = Data()
        out.append(contentsOf: nonce)
        out.append(box.ciphertext)
        out.append(box.tag)
        return out
    }

    /// Phase 32 — open with optional AEAD associated
    /// data (mirror of the legacy `open`).
    static func open(_ frame: Data, key: SymmetricKey, ad: Data? = nil) throws -> Data {
        guard frame.count >= 12 + 16 else { throw CryptoError.shortCiphertext }
        let nonceData = frame.prefix(12)
        let tagData = frame.suffix(16)
        let body = frame.dropFirst(12).dropLast(16)
        let nonce = try ChaChaPoly.Nonce(data: nonceData)
        let box = try ChaChaPoly.SealedBox(nonce: nonce, ciphertext: body, tag: tagData)
        if let ad {
            return try ChaChaPoly.open(box, using: key, authenticating: ad)
        }
        return try ChaChaPoly.open(box, using: key)
    }
}

enum CryptoError: Error {
    case shortCiphertext
    case authFailed
    case malformedHello
    case wrongDirection
    case replayRejected
}

//
// V0.7 Phase 32 — directional channel (Kotlin twin
// of MacCrypto.deriveChannelKeys / ChannelKeys on
// the Android side; byte-identical wire format).
//
enum LinkSide {
    case phone
    case mac

    /// Direction byte this side TRANSMITS with.
    var txDomain: UInt8 {
        switch self {
        case .phone: return 0x01 // PHONE_TO_MAC
        case .mac: return 0x02   // MAC_TO_PHONE
        }
    }

    /// Direction byte this side RECEIVES with.
    var rxDomain: UInt8 {
        switch self {
        case .phone: return 0x02 // MAC_TO_PHONE
        case .mac: return 0x01   // PHONE_TO_MAC
        }
    }
}

final class ChannelKeys {
    let side: LinkSide
    let txKey: SymmetricKey
    let rxKey: SymmetricKey
    private let txCounter = NonceCounter(domain: 0)
    private let rxGuard = ReplayGuard()

    private init(side: LinkSide, txKey: SymmetricKey, rxKey: SymmetricKey) {
        self.side = side
        self.txKey = txKey
        self.rxKey = rxKey
        txCounter.domain = Int(side.txDomain)
    }

    /// Phase 32 derivation: two keys from the same
    /// X25519 shared secret, domain-separated via
    /// HKDF info `elysium-channel-tx`/`-rx`.
    /// Alice's TX key == Bob's RX key.
    static func deriveKeys(myPrivate: Curve25519.KeyAgreement.PrivateKey,
                           theirPublic: Curve25519.KeyAgreement.PublicKey,
                           side: LinkSide) -> ChannelKeys {
        let shared = try! myPrivate.sharedSecretFromKeyAgreement(with: theirPublic)
        // The label is bound to the DIRECTION, so the peer's
        // RX key always equals this side's TX key for the
        // same wire direction (Kotlin twin parity).
        let txInfo: String
        let rxInfo: String
        switch side {
        case .phone:
            txInfo = "elysium-channel-phone-to-mac"
            rxInfo = "elysium-channel-mac-to-phone"
        case .mac:
            txInfo = "elysium-channel-mac-to-phone"
            rxInfo = "elysium-channel-phone-to-mac"
        }
        let tx = shared.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: "elysium-nexus-v1".data(using: .utf8)!,
            sharedInfo: txInfo.data(using: .utf8)!,
            outputByteCount: 32
        )
        let rx = shared.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: "elysium-nexus-v1".data(using: .utf8)!,
            sharedInfo: rxInfo.data(using: .utf8)!,
            outputByteCount: 32
        )
        return ChannelKeys(side: side, txKey: tx, rxKey: rx)
    }

    /// Canonical AAD: `elysium-link-v1|domain=<DIR>`.
    /// Mirrors the Android `MacCrypto.channelAd`.
    static func channelAd(direction: UInt8, protocolVersion: Int = 1) -> Data {
        let name: String
        switch direction {
        case 0x01: name = "PHONE_TO_MAC"
        case 0x02: name = "MAC_TO_PHONE"
        default: name = "UNKNOWN"
        }
        return "elysium-link-v\(protocolVersion)|domain=\(name)".data(using: .utf8)!
    }

    /// Seals `plaintext` with THIS side's TX key and
    /// the TX direction nonce domain. Wire format
    /// stays `nonce ‖ ciphertext ‖ tag`.
    func sealToPeer(_ plaintext: Data, ad: Data? = nil) -> Data {
        let nonce = txCounter.next()
        return ChannelCipher.seal(plaintext, key: txKey, nonce: nonce, ad: ad)
    }

    /// Opens a frame sent BY the peer: checks the
    /// nonce belongs to the RX direction, authenticates,
    /// then advances the replay guard (auth first,
    /// guard second — a forged frame never consumes
    /// a sequence slot). Throws [CryptoError] on any
    /// violation.
    func openFromPeer(_ frame: Data, ad: Data? = nil) throws -> Data {
        guard frame.count >= 12 + 16 else { throw CryptoError.shortCiphertext }
        let nonceData = frame.prefix(12)
        let domain = nonceData.first ?? 0
        guard domain == side.rxDomain else { throw CryptoError.wrongDirection }
        let plain = try ChannelCipher.open(frame, key: rxKey, ad: ad)
        let seq = NonceCounter.sequence(of: nonceData)
        guard rxGuard.accept(seq) else { throw CryptoError.replayRejected }
        return plain
    }
}

/// Sliding-window anti-replay guard — Kotlin twin of
/// MacCrypto.ReplayGuard (default window 65,536).
final class ReplayGuard {
    private var seen = Set<UInt64>()
    private var highest: UInt64 = 0
    private let window: UInt64
    private let lock = NSLock()

    init(windowSize: UInt64 = 65_536) { window = windowSize }

    func accept(_ sequence: UInt64) -> Bool {
        lock.lock(); defer { lock.unlock() }
        // Kotlin parity (MacCrypto.ReplayGuard): reject only when
        // the sequence has fallen out of the window behind `highest`;
        // before the window fills, nothing is "too old".
        if highest > 0 {
            let floor = highest >= window ? highest - window : 0
            if sequence <= floor { return false }
        }
        if seen.contains(sequence) { return false }
        seen.insert(sequence)
        if sequence > highest { highest = sequence }
        let floor = highest >= window ? highest - window : 0
        seen = seen.filter { $0 > floor }
        return true
    }
}

//
// A monotonically-incrementing 96-bit nonce for
// each direction. We use a counter instead of
// random nonces to guarantee uniqueness — the
// counter is bound to the connection, never
// reused.
//
final class NonceCounter {
    private var counter: UInt64 = 0
    private let lock = NSLock()
    /// Phase 32: 0x00 legacy, 0x01 phone→mac, 0x02 mac→phone.
    var domain: Int = 0

    init(domain: Int = 0) {
        self.domain = domain
    }

    /// Build a 12-byte nonce from the current
    /// counter. Byte 0 is the domain; top 4 bytes
    /// are 0; the low 8 bytes are the counter,
    /// big-endian. With domain 0 this is byte-
    /// identical to the legacy layout.
    func next() -> Data {
        lock.lock(); defer { lock.unlock() }
        counter += 1
        let value = counter
        var d = Data(count: 12)
        d[0] = UInt8(domain)
        var be = value.bigEndian
        withUnsafeBytes(of: &be) { src in
            d.replaceSubrange(4..<12, with: src.bindMemory(to: UInt8.self))
        }
        return d
    }

    /// Extracts the 64-bit big-endian sequence from
    /// bytes 4..11 of a nonce (Kotlin twin of
    /// `MacCrypto.NonceCounter.sequenceOf`).
    static func sequence(of nonce: Data) -> UInt64 {
        guard nonce.count >= 12 else { return 0 }
        var value: UInt64 = 0
        for i in 4..<12 {
            value = (value << 8) | UInt64(nonce[nonce.index(nonce.startIndex, offsetBy: i)])
        }
        return value
    }
}
