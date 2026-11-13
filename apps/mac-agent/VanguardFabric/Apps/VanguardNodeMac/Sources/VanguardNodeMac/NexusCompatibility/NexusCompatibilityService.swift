import Combine
import CryptoKit
import Foundation

enum Log {
    static func info(_ message: String) { write("INFO", message) }
    static func warn(_ message: String) { write("WARN", message) }
    static func error(_ message: String) { write("ERROR", message) }

    private static func write(_ level: String, _ message: String) {
        let timestamp = ISO8601DateFormatter().string(from: Date())
        print("[Nexus \(level)] \(timestamp) \(message)")
    }
}

@MainActor
final class AgentState: ObservableObject {
    @Published var status: AgentStatus = .starting
    @Published var lastEventDescription = "—"
    @Published var lastEventAt: Date?
    @Published var connectedPeerName: String?
    @Published var pairedPeers: [String] = []
    @Published var publicKeyBase64 = ""
    @Published var currentPin: String?
    @Published var eventCount = 0
    @Published var localIPv4Addresses = LocalNetworkAddresses.current()

    private var addressMonitor: LocalNetworkAddressMonitor?

    init() {
        let monitor = LocalNetworkAddressMonitor { [weak self] addresses in
            Task { @MainActor in self?.localIPv4Addresses = addresses }
        }
        addressMonitor = monitor
        monitor.start()
    }

    enum AgentStatus: String {
        case starting = "Iniciando…"
        case waiting = "Esperando conexión"
        case pairing = "Emparejando"
        case connected = "Conectado"
        case error = "Error"
    }
}

/// Hosts the existing Nexus Android protocol inside the Vanguard Node process.
/// Its wire format and TCP endpoint stay compatible with installed Android clients.
@MainActor
final class NexusCompatibilityService {
    static let port: UInt16 = 7878

    let state: AgentState
    private let keyPair: Curve25519.KeyAgreement.PrivateKey
    private var server: TCPServer?
    private var bonjour: BonjourAdvertiser?
    private var isStarted = false

    init() {
        let keyPair = Curve25519.KeyAgreement.PrivateKey()
        self.keyPair = keyPair
        self.state = AgentState()
        state.publicKeyBase64 = keyPair.publicKey.rawRepresentation.base64EncodedString()
    }

    func start() async throws {
        guard !isStarted else { return }
        let server = TCPServer(port: Self.port, keyPair: keyPair, state: state)
        try await server.start()

        let bonjour = BonjourAdvertiser(
            port: Self.port,
            displayName: Host.current().localizedName ?? "Mac"
        )
        bonjour.start()
        ADBBridgeDaemon.shared.start()

        self.server = server
        self.bonjour = bonjour
        isStarted = true
        state.status = .waiting
    }

    func stop() {
        guard isStarted else { return }
        server?.stop()
        bonjour?.stop()
        ADBBridgeDaemon.shared.stop()
        ScreenCaptureService.shared.stop()
        server = nil
        bonjour = nil
        isStarted = false
        state.status = .starting
    }
}
