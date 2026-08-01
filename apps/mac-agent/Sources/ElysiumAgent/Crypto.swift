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
    static func deriveKey(myPrivate: X25519.KeyAgreement.PrivateKey,
                          theirPublic: X25519.KeyAgreement.PublicKey) -> SymmetricKey {
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
}

enum CryptoError: Error {
    case shortCiphertext
    case authFailed
    case malformedHello
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
    /// Build a 12-byte nonce from the current
    /// counter. The top 4 bytes are 0; the low 8
    /// bytes are the counter, big-endian.
    func next() -> Data {
        lock.lock(); defer { lock.unlock() }
        counter += 1
        let value = counter
        var d = Data(count: 12)
        var be = value.bigEndian
        withUnsafeBytes(of: &be) { src in
            d.replaceSubrange(4..<12, with: src.bindMemory(to: UInt8.self))
        }
        return d
    }
}
