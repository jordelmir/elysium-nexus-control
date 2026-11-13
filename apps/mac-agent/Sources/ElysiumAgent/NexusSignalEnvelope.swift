import CryptoKit
import Foundation

/// Authenticates SDP/ICE signaling before it reaches WebRTC.
struct NexusSignalEnvelope: Codable, Sendable {
    enum Sender: String, Codable, Sendable {
        case mac
        case android
    }

    enum Kind: String, Codable, Sendable {
        case offer
        case answer
        case ice
        case close
    }

    let version: Int
    let sessionID: String
    let sender: Sender
    let sequence: UInt64
    let sentAtMillis: Int64
    let kind: Kind
    let bodyBase64URL: String
    let macBase64URL: String

    enum CodingKeys: String, CodingKey {
        case version = "v"
        case sessionID = "sessionId"
        case sender
        case sequence
        case sentAtMillis
        case kind
        case bodyBase64URL = "body"
        case macBase64URL = "mac"
    }

    static let secretByteCount = 32
    static let maxBodyByteCount = 128 * 1024
    private static let nonceByteCount = 12
    private static let tagByteCount = 16
    private static let maxEncryptedBodyByteCount = maxBodyByteCount + nonceByteCount + tagByteCount
    private static let maxFutureSkewMillis: Int64 = 30_000
    private static let maxAgeMillis: Int64 = 120_000
    private static let macKeyInfo = "NEXUS-SIGNAL-MAC-v1"
    private static let encryptionKeyInfo = "NEXUS-SIGNAL-ENC-v1"
    private static let sessionIDPattern = #"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"#

    static func sign(
        pairingSecret: Data,
        sessionID: UUID,
        sender: Sender,
        sequence: UInt64,
        sentAtMillis: Int64,
        kind: Kind,
        body: Data
    ) throws -> NexusSignalEnvelope {
        let nonce = AES.GCM.Nonce()
        let nonceData = nonce.withUnsafeBytes { Data($0) }
        return try signWithNonce(
            pairingSecret: pairingSecret,
            sessionID: sessionID,
            sender: sender,
            sequence: sequence,
            sentAtMillis: sentAtMillis,
            kind: kind,
            body: body,
            nonce: nonceData
        )
    }

    #if DEBUG
    static func signWithNonceForTesting(
        pairingSecret: Data,
        sessionID: UUID,
        sender: Sender,
        sequence: UInt64,
        sentAtMillis: Int64,
        kind: Kind,
        body: Data,
        nonce: Data
    ) throws -> NexusSignalEnvelope {
        try signWithNonce(pairingSecret: pairingSecret, sessionID: sessionID, sender: sender,
                          sequence: sequence, sentAtMillis: sentAtMillis, kind: kind,
                          body: body, nonce: nonce)
    }
    #endif

    private static func signWithNonce(
        pairingSecret: Data,
        sessionID: UUID,
        sender: Sender,
        sequence: UInt64,
        sentAtMillis: Int64,
        kind: Kind,
        body: Data,
        nonce: Data
    ) throws -> NexusSignalEnvelope {
        guard pairingSecret.count == secretByteCount else { throw SignalError.invalidSecret }
        guard sequence > 0, sequence <= UInt64(Int64.max) else { throw SignalError.invalidSequence }
        guard body.count <= maxBodyByteCount else { throw SignalError.bodyTooLarge }
        guard nonce.count == nonceByteCount else { throw SignalError.invalidNonce }

        let id = sessionID.uuidString.lowercased()
        let unsigned = NexusSignalEnvelope(
            version: 1,
            sessionID: id,
            sender: sender,
            sequence: sequence,
            sentAtMillis: sentAtMillis,
            kind: kind,
            bodyBase64URL: "",
            macBase64URL: ""
        )
        let header = unsigned.headerBytes
        let encryptionKey = deriveKey(pairingSecret, sessionID: id, info: encryptionKeyInfo)
        let sealed = try AES.GCM.seal(
            body,
            using: encryptionKey,
            nonce: AES.GCM.Nonce(data: nonce),
            authenticating: header
        )
        guard let encryptedBody = sealed.combined else { throw SignalError.encryptionFailed }
        let withBody = unsigned.with(bodyBase64URL: encryptedBody.base64URLEncodedString())
        let macKey = deriveKey(pairingSecret, sessionID: id, info: macKeyInfo)
        let authenticationCode = HMAC<SHA256>.authenticationCode(for: withBody.canonicalBytes, using: macKey)
        return withBody.with(macBase64URL: Data(authenticationCode).base64URLEncodedString())
    }

    func encodedJSON() throws -> Data {
        try JSONEncoder().encode(self)
    }

    func verifiedBody(
        pairingSecret: Data,
        expectedSessionID: UUID,
        expectedSender: Sender,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1_000)
    ) -> Data? {
        guard version == 1,
              pairingSecret.count == Self.secretByteCount,
              sessionID == expectedSessionID.uuidString.lowercased(),
              Self.isValidSessionID(sessionID),
              sender == expectedSender,
              sequence > 0,
              sequence <= UInt64(Int64.max),
              sentAtMillis <= nowMillis + Self.maxFutureSkewMillis,
              sentAtMillis >= nowMillis - Self.maxAgeMillis,
              let encryptedBody = Data(base64URLString: bodyBase64URL),
              encryptedBody.count >= Self.nonceByteCount + Self.tagByteCount,
              encryptedBody.count <= Self.maxEncryptedBodyByteCount,
              let suppliedMAC = Data(base64URLString: macBase64URL),
              suppliedMAC.count == SHA256.byteCount else {
            return nil
        }

        let macKey = Self.deriveKey(pairingSecret, sessionID: sessionID, info: Self.macKeyInfo)
        guard HMAC<SHA256>.isValidAuthenticationCode(
            suppliedMAC,
            authenticating: canonicalBytes,
            using: macKey
        ) else {
            return nil
        }
        guard let sealed = try? AES.GCM.SealedBox(combined: encryptedBody) else { return nil }
        let encryptionKey = Self.deriveKey(pairingSecret, sessionID: sessionID, info: Self.encryptionKeyInfo)
        guard let body = try? AES.GCM.open(sealed, using: encryptionKey, authenticating: headerBytes),
              body.count <= Self.maxBodyByteCount else {
            return nil
        }
        return body
    }

    private var headerBytes: Data {
        Data([
            "NEXUS-SIGNAL-v\(version)",
            sessionID,
            sender.rawValue,
            String(sequence),
            String(sentAtMillis),
            kind.rawValue,
        ].joined(separator: "\n").utf8)
    }

    private var canonicalBytes: Data {
        Data((String(decoding: headerBytes, as: UTF8.self) + "\n" + bodyBase64URL).utf8)
    }

    private func with(bodyBase64URL: String) -> NexusSignalEnvelope {
        NexusSignalEnvelope(version: version, sessionID: sessionID, sender: sender, sequence: sequence,
                            sentAtMillis: sentAtMillis, kind: kind, bodyBase64URL: bodyBase64URL,
                            macBase64URL: macBase64URL)
    }

    private func with(macBase64URL: String) -> NexusSignalEnvelope {
        NexusSignalEnvelope(version: version, sessionID: sessionID, sender: sender, sequence: sequence,
                            sentAtMillis: sentAtMillis, kind: kind, bodyBase64URL: bodyBase64URL,
                            macBase64URL: macBase64URL)
    }

    private static func deriveKey(_ secret: Data, sessionID: String, info: String) -> SymmetricKey {
        let uuid = UUID(uuidString: sessionID)!
        var rawUUID = uuid.uuid
        let salt = withUnsafeBytes(of: &rawUUID) { Data($0) }
        return HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: secret),
            salt: salt,
            info: Data(info.utf8),
            outputByteCount: 32
        )
    }

    private static func isValidSessionID(_ value: String) -> Bool {
        value.range(of: sessionIDPattern, options: .regularExpression) != nil
    }

    enum SignalError: Error {
        case invalidSecret
        case invalidSequence
        case bodyTooLarge
        case invalidNonce
        case encryptionFailed
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    init?(base64URLString value: String) {
        guard value.range(of: #"^[A-Za-z0-9_-]*$"#, options: .regularExpression) != nil else { return nil }
        var base64 = value.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder != 0 { base64 += String(repeating: "=", count: 4 - remainder) }
        self.init(base64Encoded: base64)
    }
}

/// Sliding anti-replay window permits bounded delivery reordering and rejects duplicates.
struct NexusSignalReplayWindow {
    private let width: UInt64
    private var highestSequence: UInt64 = 0
    private var seenMask: UInt64 = 0

    init(width: UInt64 = 64) {
        precondition((1...64).contains(width))
        self.width = width
    }

    mutating func accept(_ sequence: UInt64) -> Bool {
        guard sequence > 0 else { return false }
        if sequence > highestSequence {
            let shift = sequence - highestSequence
            seenMask = shift >= width ? 1 : (seenMask << shift) | 1
            highestSequence = sequence
            return true
        }
        let offset = highestSequence - sequence
        guard offset < width else { return false }
        let bit = UInt64(1) << offset
        guard seenMask & bit == 0 else { return false }
        seenMask |= bit
        return true
    }
}
