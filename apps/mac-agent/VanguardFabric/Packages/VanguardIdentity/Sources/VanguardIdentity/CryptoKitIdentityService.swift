import Foundation
import CryptoKit
import Security
import VanguardDomain

// MARK: - CryptoKit Identity Service

public final class CryptoKitIdentityService: IdentityService, @unchecked Sendable {
    private static let serviceName = "com.elysiumvanguard.fabric.identity"
    private static let metadataAccount = "device-identity-metadata"
    private static let fileBackupURL: URL = {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = appSupport.appendingPathComponent("ElysiumVanguardFabric/Identity", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }()

    public init() {}

    // MARK: - Identity

    public func generateDeviceIdentity() async throws -> DeviceIdentity {
        let nodeID = NodeID()
        let signingKey = Curve25519.Signing.PrivateKey()
        let agreementKey = Curve25519.KeyAgreement.PrivateKey()

        let signingTag = "signing-\(nodeID.rawValue.uuidString)"
        let agreementTag = "agreement-\(nodeID.rawValue.uuidString)"

        do {
            try Self.storeKey(signingKey.rawRepresentation, tag: signingTag)
            try Self.storeKey(agreementKey.rawRepresentation, tag: agreementTag)
        } catch {
            try Self.writeFileBackup(nodeID: nodeID.rawValue, signingKey: signingKey.rawRepresentation, agreementKey: agreementKey.rawRepresentation, signingTag: signingTag, agreementTag: agreementTag)
        }

        let fingerprint = Data(SHA256.hash(
            data: signingKey.publicKey.rawRepresentation + agreementKey.publicKey.rawRepresentation
        ))

        let metadata = PersistedIdentityMetadata(
            nodeID: nodeID.rawValue,
            signingKeyTag: signingTag,
            agreementKeyTag: agreementTag,
            certificateFingerprint: fingerprint
        )
        do {
            try Self.storeMetadata(metadata)
        } catch {
            try Self.writeFileBackupMetadata(metadata)
        }

        return DeviceIdentity(
            nodeID: nodeID,
            signingPublicKey: signingKey.publicKey.rawRepresentation,
            signingPrivateKeyRef: signingTag,
            agreementPublicKey: agreementKey.publicKey.rawRepresentation,
            agreementPrivateKeyRef: agreementTag,
            certificateFingerprint: fingerprint
        )
    }

    public func getOrCreateIdentity() async throws -> DeviceIdentity {
        if let metadata = Self.loadMetadata() ?? Self.loadMetadataFromFile(),
           let signingData = Self.loadKey(tag: metadata.signingKeyTag) ?? Self.loadKeyFromFile(tag: metadata.signingKeyTag),
           let agreementData = Self.loadKey(tag: metadata.agreementKeyTag) ?? Self.loadKeyFromFile(tag: metadata.agreementKeyTag) {
            let signingKey = try Curve25519.Signing.PrivateKey(rawRepresentation: signingData)
            let agreementKey = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: agreementData)
            let fingerprint = Data(SHA256.hash(
                data: signingKey.publicKey.rawRepresentation + agreementKey.publicKey.rawRepresentation
            ))

            return DeviceIdentity(
                nodeID: NodeID(rawValue: metadata.nodeID),
                signingPublicKey: signingKey.publicKey.rawRepresentation,
                signingPrivateKeyRef: metadata.signingKeyTag,
                agreementPublicKey: agreementKey.publicKey.rawRepresentation,
                agreementPrivateKeyRef: metadata.agreementKeyTag,
                certificateFingerprint: fingerprint
            )
        }

        return try await generateDeviceIdentity()
    }

    // MARK: - Signing

    public func signData(_ data: Data, identity: DeviceIdentity) async throws -> Data {
        guard let keyData = Self.loadKey(tag: identity.signingPrivateKeyRef) else {
            throw PairingError.keychainFailure(reason: "Signing key not found")
        }
        let privateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: keyData)
        return try privateKey.signature(for: data)
    }

    public func verifySignature(_ signature: Data, data: Data, publicKey: Data) async throws -> Bool {
        guard let pubKey = try? Curve25519.Signing.PublicKey(rawRepresentation: publicKey) else { return false }
        return pubKey.isValidSignature(signature, for: data)
    }

    // MARK: - Trusted Peers

    public func saveTrustedPeer(_ peer: TrustedPeer) async throws {
        let data = try JSONEncoder().encode(peer)
        try Self.storeKey(data, tag: "peer-\(peer.nodeID.rawValue.uuidString)")
    }

    public func loadTrustedPeer(nodeID: NodeID) async throws -> TrustedPeer? {
        guard let data = Self.loadKey(tag: "peer-\(nodeID.rawValue.uuidString)") else { return nil }
        return try JSONDecoder().decode(TrustedPeer.self, from: data)
    }

    public func loadAllTrustedPeers() async throws -> [TrustedPeer] {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrService as String: Self.serviceName,
            kSecReturnAttributes as String: true,
            kSecMatchLimit as String: kSecMatchLimitAll
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let items = result as? [[String: Any]] else { return [] }

        return items.compactMap { item -> TrustedPeer? in
            guard let tagData = item[kSecAttrApplicationTag as String] as? Data,
                  let tag = String(data: tagData, encoding: .utf8),
                  tag.hasPrefix("peer-") else { return nil }
            guard let data = Self.loadKey(tag: tag) else { return nil }
            return try? JSONDecoder().decode(TrustedPeer.self, from: data)
        }
    }

    public func revokePeer(nodeID: NodeID) async throws {
        Self.deleteKey(tag: "peer-\(nodeID.rawValue.uuidString)")
    }

    // MARK: - Pairing Challenge

    public func generateChallenge() async throws -> PairingChallenge {
        var bytes = [UInt8](repeating: 0, count: 3)
        let status = SecRandomCopyBytes(kSecRandomDefault, 3, &bytes)
        guard status == errSecSuccess else {
            throw PairingError.keychainFailure(reason: "Failed to generate random bytes")
        }
        let code = String(format: "%06d", (Int(bytes[0]) << 16 | Int(bytes[1]) << 8 | Int(bytes[2])) % 1_000_000)
        let fingerprint = Data(SHA256.hash(data: Data(bytes)))
        return PairingChallenge(code: code, expiresAt: Date().addingTimeInterval(120), fingerprint: fingerprint, maxAttempts: 3)
    }

    public func validateChallengeCode(_ code: String, challenge: PairingChallenge) async throws -> Bool {
        guard !challenge.isExpired else { return false }
        return code == challenge.code
    }

    // MARK: - Keychain Helpers

    private struct PersistedIdentityMetadata: Codable {
        let nodeID: UUID
        let signingKeyTag: String
        let agreementKeyTag: String
        let certificateFingerprint: Data
    }

    private static func storeMetadata(_ metadata: PersistedIdentityMetadata) throws {
        let data = try JSONEncoder().encode(metadata)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: metadataAccount
        ]
        SecItemDelete(query as CFDictionary)

        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly

        let addStatus = SecItemAdd(insert as CFDictionary, nil)
        if addStatus != errSecSuccess {
            let updateStatus = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
            if updateStatus != errSecSuccess {
                throw PairingError.keychainFailure(reason: "Failed to store identity metadata: add=\(addStatus) update=\(updateStatus)")
            }
        }
    }

    private static func loadMetadata() -> PersistedIdentityMetadata? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: metadataAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(PersistedIdentityMetadata.self, from: data)
    }

    private static func storeKey(_ data: Data, tag: String) throws {
        guard let tagData = tag.data(using: .utf8) else {
            throw PairingError.keychainFailure(reason: "Invalid tag encoding")
        }

        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrService as String: serviceName
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        let broadDelete: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData
        ]
        SecItemDelete(broadDelete as CFDictionary)

        let insertQuery: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrService as String: serviceName,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data
        ]
        let addStatus = SecItemAdd(insertQuery as CFDictionary, nil)
        if addStatus != errSecSuccess {
            let updateQuery: [String: Any] = [
                kSecClass as String: kSecClassKey,
                kSecAttrApplicationTag as String: tagData,
                kSecAttrService as String: serviceName
            ]
            let updateAttrs: [String: Any] = [
                kSecValueData as String: data
            ]
            let updateStatus = SecItemUpdate(updateQuery as CFDictionary, updateAttrs as CFDictionary)
            if updateStatus != errSecSuccess {
                throw PairingError.keychainFailure(reason: "Failed to store key \(tag): add=\(addStatus) update=\(updateStatus)")
            }
        }
    }

    private static func loadKey(tag: String) -> Data? {
        guard let tagData = tag.data(using: .utf8) else { return nil }
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrService as String: serviceName,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return data
    }

    private static func deleteKey(tag: String) {
        guard let tagData = tag.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrService as String: serviceName
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - File Backup (when Keychain is blocked by code-signing)

    private static func writeFileBackup(nodeID: UUID, signingKey: Data, agreementKey: Data, signingTag: String, agreementTag: String) throws {
        let backup = FileBackup(nodeID: nodeID, signingKeyBase64: signingKey.base64EncodedString(), agreementKeyBase64: agreementKey.base64EncodedString(), signingKeyTag: signingTag, agreementKeyTag: agreementTag)
        let data = try JSONEncoder().encode(backup)
        let url = fileBackupURL.appendingPathComponent("identity-\(nodeID.uuidString).json")
        try data.write(to: url, options: .atomic)
    }

    private static func writeFileBackupMetadata(_ metadata: PersistedIdentityMetadata) throws {
        let backup = FileBackup(nodeID: metadata.nodeID, signingKeyBase64: "", agreementKeyBase64: "", signingKeyTag: metadata.signingKeyTag, agreementKeyTag: metadata.agreementKeyTag)
        let data = try JSONEncoder().encode(backup)
        let url = fileBackupURL.appendingPathComponent("identity-\(metadata.nodeID.uuidString).json")
        try data.write(to: url, options: .atomic)
    }

    private static func loadMetadataFromFile() -> PersistedIdentityMetadata? {
        guard let files = try? FileManager.default.contentsOfDirectory(at: fileBackupURL, includingPropertiesForKeys: nil) else { return nil }
        for file in files where file.pathExtension == "json" {
            guard let data = try? Data(contentsOf: file),
                  let backup = try? JSONDecoder().decode(FileBackup.self, from: data) else { continue }
            return PersistedIdentityMetadata(nodeID: backup.nodeID, signingKeyTag: backup.signingKeyTag, agreementKeyTag: backup.agreementKeyTag, certificateFingerprint: Data())
        }
        return nil
    }

    private static func loadKeyFromFile(tag: String) -> Data? {
        guard let files = try? FileManager.default.contentsOfDirectory(at: fileBackupURL, includingPropertiesForKeys: nil) else { return nil }
        for file in files where file.pathExtension == "json" {
            guard let data = try? Data(contentsOf: file),
                  let backup = try? JSONDecoder().decode(FileBackup.self, from: data) else { continue }
            if tag == backup.signingKeyTag, let keyData = Data(base64Encoded: backup.signingKeyBase64) { return keyData }
            if tag == backup.agreementKeyTag, let keyData = Data(base64Encoded: backup.agreementKeyBase64) { return keyData }
        }
        return nil
    }

    private struct FileBackup: Codable {
        let nodeID: UUID
        let signingKeyBase64: String
        let agreementKeyBase64: String
        let signingKeyTag: String
        let agreementKeyTag: String
    }
}
