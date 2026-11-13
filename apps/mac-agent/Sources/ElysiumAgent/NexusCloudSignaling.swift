import Foundation
import Supabase

struct NexusMacCloudSession: Sendable {
    let sessionID: UUID
    let topic: String
    let pairingSecret: Data
    let iceServers: [NexusCloudIceServer]
    let relayAvailable: Bool
    let expiresAt: String?

    var pairingCode: String {
        "elysium-nexus://pair/\(sessionID.uuidString.lowercased())/\(pairingSecret.base64URLEncodedString)"
    }
}

struct NexusCloudIceServer: Decodable, Sendable {
    let urls: [String]
    let username: String?
    let credential: String?
}

struct NexusMacVerifiedSignal: Sendable {
    let kind: NexusSignalEnvelope.Kind
    let body: Data
    let sequence: UInt64
}

struct NexusRtcSignalPayload: Codable, Sendable {
    var sdp: String? = nil
    var candidate: String? = nil
    var sdpMid: String? = nil
    var sdpMLineIndex: Int? = nil
}

enum NexusCloudSignalingError: Error, LocalizedError {
    case notConfigured
    case invalidPairingCode
    case nonAnonymousIdentity
    case malformedSession
    case relayUnavailable

    var errorDescription: String? {
        switch self {
        case .notConfigured: "La conexión global no está configurada en esta compilación."
        case .invalidPairingCode: "El código de emparejamiento no es válido o venció."
        case .nonAnonymousIdentity: "La sesión de nube de Nexus debe ser temporal y anónima."
        case .malformedSession: "El servicio devolvió una sesión de Nexus inválida."
        case .relayUnavailable: "El servicio TURN global no está disponible; por privacidad no se conectará directamente."
        }
    }
}

/// Uses Supabase only for private rendezvous. SDP/ICE bodies are encrypted before broadcast.
final class NexusCloudSignalingService {
    private static let functionName = "nexus-session"
    private static let eventName = "nexus-signal"

    private let client: SupabaseClient
    private let configured: Bool

    init(bundle: Bundle = .main) {
        let environment = ProcessInfo.processInfo.environment
        let urlString = (bundle.object(forInfoDictionaryKey: "ElysiumNexusSupabaseURL") as? String)
            ?? environment["ELYSIUM_NEXUS_SUPABASE_URL"]
            ?? ""
        let publishableKey = (bundle.object(forInfoDictionaryKey: "ElysiumNexusPublishableKey") as? String)
            ?? environment["ELYSIUM_NEXUS_PUBLISHABLE_KEY"]
            ?? ""
        guard let url = URL(string: urlString), url.scheme == "https", publishableKey.hasPrefix("sb_publishable_") else {
            configured = false
            client = SupabaseClient(
                supabaseURL: URL(string: "https://trccikkcmdqnutwfjrbf.supabase.co")!,
                supabaseKey: "sb_publishable_not_configured"
            )
            return
        }
        configured = true
        client = SupabaseClient(supabaseURL: url, supabaseKey: publishableKey)
    }

    func createInvitation() async throws -> NexusMacCloudSession {
        try requireConfiguration()
        try await ensureAnonymousSession()
        var secret = Data(count: NexusSignalEnvelope.secretByteCount)
        let status = secret.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, $0.count, $0.baseAddress!) }
        guard status == errSecSuccess else { throw CocoaError(.fileWriteUnknown) }

        let response: NexusSessionResponse = try await client.functions.invoke(
            Self.functionName,
            options: FunctionInvokeOptions(body: NexusSessionRequest(
                action: "create",
                requestId: UUID().uuidString.lowercased(),
                pairingSecret: secret.base64URLEncodedString
            ))
        )
        guard response.pairingSecret == secret.base64URLEncodedString else {
            throw NexusCloudSignalingError.malformedSession
        }
        return try response.makeSession(secret: secret)
    }

    func joinInvitation(_ pairingCode: String) async throws -> NexusMacCloudSession {
        try requireConfiguration()
        try await ensureAnonymousSession()
        guard let invite = NexusMacPairingCode.parse(pairingCode) else {
            throw NexusCloudSignalingError.invalidPairingCode
        }
        let response: NexusSessionResponse = try await client.functions.invoke(
            Self.functionName,
            options: FunctionInvokeOptions(body: NexusSessionRequest(
                action: "join",
                sessionId: invite.sessionID.uuidString.lowercased(),
                pairingSecret: invite.pairingSecret.base64URLEncodedString
            ))
        )
        return try response.makeSession(secret: invite.pairingSecret)
    }

    func openSignalChannel(
        for session: NexusMacCloudSession,
        sender: NexusSignalEnvelope.Sender,
        onSignal: @escaping @Sendable (NexusMacVerifiedSignal) async -> Void
    ) async throws -> NexusMacCloudSignalConnection {
        try requireConfiguration()
        try await ensureAnonymousSession()
        let channel = client.channel(session.topic) { config in
            config.isPrivate = true
            config.broadcast.acknowledgeBroadcasts = true
        }
        let messages = channel.broadcastStream(event: Self.eventName)
        try await channel.subscribeWithError()
        let connection = NexusMacCloudSignalConnection(
            channel: channel,
            session: session,
            sender: sender,
            incomingMessages: messages
        )
        await connection.start(onSignal: onSignal)
        return connection
    }

    func closeSession(_ session: NexusMacCloudSession) async {
        guard configured, (try? await ensureAnonymousSession()) != nil else { return }
        let request = NexusSessionRequest(
            action: "close",
            sessionId: session.sessionID.uuidString.lowercased()
        )
        let _: NexusSessionClosedResponse? = try? await client.functions.invoke(
            Self.functionName,
            options: FunctionInvokeOptions(body: request)
        )
    }

    private func ensureAnonymousSession() async throws {
        if let current = client.auth.currentSession {
            guard current.user.isAnonymous else { throw NexusCloudSignalingError.nonAnonymousIdentity }
            _ = try await client.auth.session
            return
        }
        _ = try await client.auth.signInAnonymously()
    }

    private func requireConfiguration() throws {
        guard configured else { throw NexusCloudSignalingError.notConfigured }
    }
}

actor NexusMacCloudSignalConnection {
    private let channel: RealtimeChannelV2
    private let session: NexusMacCloudSession
    private let sender: NexusSignalEnvelope.Sender
    private let remoteSender: NexusSignalEnvelope.Sender
    private let incomingMessages: AsyncStream<JSONObject>
    private var outgoingSequence: UInt64 = 0
    private var replayWindow = NexusSignalReplayWindow()
    private var receiveTask: Task<Void, Never>?

    init(
        channel: RealtimeChannelV2,
        session: NexusMacCloudSession,
        sender: NexusSignalEnvelope.Sender,
        incomingMessages: AsyncStream<JSONObject>
    ) {
        self.channel = channel
        self.session = session
        self.sender = sender
        self.remoteSender = sender == .mac ? .android : .mac
        self.incomingMessages = incomingMessages
    }

    func start(onSignal: @escaping @Sendable (NexusMacVerifiedSignal) async -> Void = { _ in }) {
        receiveTask?.cancel()
        receiveTask = Task { [weak self, incomingMessages] in
            for await message in incomingMessages {
                guard !Task.isCancelled else { break }
                guard let data = try? JSONEncoder().encode(message),
                      let envelope = try? JSONDecoder().decode(NexusSignalEnvelope.self, from: data) else { continue }
                await self?.receive(envelope, onSignal: onSignal)
            }
        }
    }

    func send(
        kind: NexusSignalEnvelope.Kind,
        body: Data
    ) async throws {
        outgoingSequence += 1
        let envelope = try NexusSignalEnvelope.sign(
            pairingSecret: session.pairingSecret,
            sessionID: session.sessionID,
            sender: sender,
            sequence: outgoingSequence,
            sentAtMillis: Int64(Date().timeIntervalSince1970 * 1_000),
            kind: kind,
            body: body
        )
        try await channel.broadcast(event: "nexus-signal", message: envelope)
    }

    func close() async {
        receiveTask?.cancel()
        receiveTask = nil
        await channel.unsubscribe()
    }

    private func receive(
        _ envelope: NexusSignalEnvelope,
        onSignal: @escaping @Sendable (NexusMacVerifiedSignal) async -> Void
    ) async {
        guard let body = envelope.verifiedBody(
            pairingSecret: session.pairingSecret,
            expectedSessionID: session.sessionID,
            expectedSender: remoteSender
        ), replayWindow.accept(envelope.sequence) else { return }
        await onSignal(NexusMacVerifiedSignal(kind: envelope.kind, body: body, sequence: envelope.sequence))
    }
}

struct NexusMacPairingCode {
    let sessionID: UUID
    let pairingSecret: Data

    static func parse(_ value: String) -> NexusMacPairingCode? {
        guard let components = URLComponents(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
              components.scheme == "elysium-nexus",
              components.host == "pair",
              components.queryItems?.isEmpty != false,
              let parts = URL(string: value)?.pathComponents.filter({ $0 != "/" }),
              parts.count == 2,
              let sessionID = UUID(uuidString: parts[0]),
              let secret = Data(base64URLString: parts[1]),
              secret.count == NexusSignalEnvelope.secretByteCount else { return nil }
        return NexusMacPairingCode(sessionID: sessionID, pairingSecret: secret)
    }
}

private struct NexusSessionRequest: Encodable {
    let action: String
    var requestId: String? = nil
    var sessionId: String? = nil
    var pairingSecret: String? = nil
}

private struct NexusSessionResponse: Decodable {
    let sessionId: String
    let topic: String
    let pairingSecret: String?
    let joinExpiresAt: String?
    let expiresAt: String?
    let iceServers: [NexusCloudIceServer]?
    let relayAvailable: Bool?

    func makeSession(secret: Data) throws -> NexusMacCloudSession {
        guard let id = UUID(uuidString: sessionId), topic == "nexus-session:\(sessionId)" else {
            throw NexusCloudSignalingError.malformedSession
        }
        return NexusMacCloudSession(
            sessionID: id,
            topic: topic,
            pairingSecret: secret,
            iceServers: iceServers ?? [],
            relayAvailable: relayAvailable ?? false,
            expiresAt: expiresAt ?? joinExpiresAt
        )
    }
}

private struct NexusSessionClosedResponse: Decodable {
    let closed: Bool
}

private extension Data {
    var base64URLEncodedString: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    init?(base64URLString: String) {
        guard base64URLString.range(of: #"^[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil else {
            return nil
        }
        var standard = base64URLString.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = standard.count % 4
        if remainder != 0 { standard += String(repeating: "=", count: 4 - remainder) }
        self.init(base64Encoded: standard)
    }
}
