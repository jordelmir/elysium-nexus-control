//
// Elysium Nexus — Mac Agent
// Entry point. Wires the menu bar, Bonjour advertiser,
// TCP server, pairing manager, and event injector.
//
// Architecture:
//   - StatusBarController: shows state in the macOS
//     menu bar (idling / pairing / connected).
//   - BonjourAdvertiser: publishes `_elysium._tcp`
//     so the Android app can find the Mac on the
//     local network.
//   - TCPServer: accepts raw TCP connections on the
//     same port. Each connection runs the pairing
//     handshake and, on success, becomes an
//     event-stream connection.
//   - PairingManager: generates a 6-digit PIN,
//     shows a SwiftUI window with the PIN, and
//     compares against the digits received from
//     the phone over the encrypted channel.
//   - EventInjector: calls CGEvent to inject
//     mouse, scroll, and keyboard events into the
//     active application.
//   - PermissionManager: checks + requests macOS
//     Accessibility + Input Monitoring permissions
//     (required for CGEvent posting).
//
import Foundation
import AppKit
import CryptoKit

@main
struct ElysiumAgentMain {
    static func main() async throws {
        // 1. Bootstrap an NSApplication so the
        //    menu bar item + SwiftUI window can
        //    run. Activation policy is `.accessory`
        //    so we don't show a dock icon — this
        //    is a menu-bar-only app.
        let app = NSApplication.shared
        await MainActor.run {
            app.setActivationPolicy(.accessory)
        }

        // 2. Generate the agent's long-term X25519
        //    keypair. The public half is advertised
        //    via Bonjour + shown in the menu bar so
        //    the user can verify which device they
        //    are talking to. The private half never
        //    leaves the agent's memory.
        let keyPair = X25519.KeyAgreement.PrivateKey()
        let publicKey = keyPair.publicKey.rawRepresentation
        Log.info("Agent public key: \(publicKey.base64EncodedString())")

        // 3. Permission check. macOS requires the
        //    user to grant Accessibility (for
        //    CGEvent.post) and Input Monitoring
        //    (for keystroke capture, future). We
        //    warn early; the agent still runs in
        //    degraded mode if the user has not yet
        //    granted the permission.
        let permissions = PermissionManager()
        if !permissions.hasAccessibility() {
            Log.warn("Accessibility permission NOT granted. Event injection will be silently dropped. Open System Settings → Privacy & Security → Accessibility to grant.")
        }
        if !permissions.hasInputMonitoring() {
            Log.warn("Input Monitoring permission NOT granted. Keyboard capture will be limited. Open System Settings → Privacy & Security → Input Monitoring to grant.")
        }

        // 4. State. The status bar observes this.
        let state = AgentState()

        // 5. Status bar.
        let statusBar = StatusBarController(state: state)
        await MainActor.run { statusBar.install() }

        // 6. Bonjour advertiser.
        let port: UInt16 = 7878
        let bonjour = BonjourAdvertiser(
            port: port,
            displayName: Host.current().localizedName ?? "Mac"
        )
        bonjour.start()

        // 7. TCP server.
        let server = TCPServer(
            port: port,
            keyPair: keyPair,
            state: state
        )
        Task {
            do {
                try await server.start()
                Log.info("TCP server listening on port \(port)")
            } catch {
                Log.error("TCP server failed to start: \(error)")
            }
        }

        // 8. Show the PIN window initially hidden.
        //    PairingManager shows it when a
        //    connection requests pairing.
        let pairing = PairingManager(state: state)
        await MainActor.run { pairing.install() }

        // 9. Run the NSApplication event loop on
        //    the main thread.
        await MainActor.run {
            app.activate(ignoringOtherApps: true)
            app.run()
        }
    }
}

//
// Agent state. The status bar observes this
// and updates the menu bar item. The TCP server
// and pairing manager mutate it.
//
@MainActor
final class AgentState: ObservableObject {
    @Published var status: AgentStatus = .starting
    @Published var lastEventDescription: String = "—"
    @Published var lastEventAt: Date?
    @Published var connectedPeerName: String?
    @Published var pairedPeers: [String] = []
    @Published var publicKeyBase64: String = ""

    enum AgentStatus: String {
        case starting = "Iniciando…"
        case waiting = "Esperando"
        case pairing = "Emparejando"
        case connected = "Conectado"
        case error = "Error"
    }
}

//
// A tiny logger that prints to stdout with a
// timestamp + tag. macOS unified logging would
// be nicer but plain stdout keeps the dependency
// surface minimal.
//
enum Log {
    static func info(_ msg: String) {
        print("[INFO]  \(timestamp()) \(msg)")
    }
    static func warn(_ msg: String) {
        print("[WARN]  \(timestamp()) \(msg)")
    }
    static func error(_ msg: String) {
        print("[ERROR] \(timestamp()) \(msg)")
    }
    private static func timestamp() -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withTime, .withDashSeparatorInDate, .withColonSeparatorInTime]
        return f.string(from: Date())
    }
}
