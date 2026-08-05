//
// Elysium Nexus — Mac Agent
// Entry point. Full SwiftUI app with futuristic
// neon theme, animated logo, status dashboard,
// and automatic pairing flow.
//
import Foundation
import AppKit
import SwiftUI
import CryptoKit

// ───────────────────────────────────────────────
// 0. Parse Command Line Arguments for Headless & Daemon Installation
let args = CommandLine.arguments
if args.contains("--install-daemon") {
    print("Installing Elysium Nexus Mac Agent LaunchAgent daemon...")
    let success = LaunchAgentManager.installLaunchAgent()
    print(success ? "SUCCESS: LaunchAgent installed at \(LaunchAgentManager.plistURL.path)" : "FAILURE: Could not install LaunchAgent")
    exit(success ? 0 : 1)
} else if args.contains("--uninstall-daemon") {
    print("Uninstalling Elysium Nexus Mac Agent LaunchAgent daemon...")
    let success = LaunchAgentManager.uninstallLaunchAgent()
    print(success ? "SUCCESS: LaunchAgent uninstalled" : "FAILURE: Could not uninstall LaunchAgent")
    exit(success ? 0 : 1)
}

let isHeadless = args.contains("--headless")

// 1. Bootstrap NSApplication — regular mode for GUI, accessory for background headless
let app = NSApplication.shared
app.setActivationPolicy(isHeadless ? .accessory : .regular)

// Explicitly set the application icon image for Stage Manager, Dock & Window Switcher badges
let possibleIconPaths = [
    Bundle.main.path(forResource: "AppIcon", ofType: "icns"),
    Bundle.main.path(forResource: "AppIcon", ofType: "png"),
    "/Applications/Elysium Nexus.app/Contents/Resources/AppIcon.icns",
    "/Applications/Elysium Nexus.app/Contents/Resources/AppIcon.png",
    "/tmp/AppIcon.icns",
    "/tmp/master_1024.png"
].compactMap { $0 }

for path in possibleIconPaths {
    if let img = NSImage(contentsOfFile: path) {
        app.applicationIconImage = img
        app.dockTile.display()
        break
    }
}

// 2. Agent keypair (fresh per launch).
let keyPair = Curve25519.KeyAgreement.PrivateKey()
let publicKey = keyPair.publicKey.rawRepresentation
Log.info("Agent public key: \(publicKey.base64EncodedString())")

// 3. Accessibility permission check.
let permissions = PermissionManager()
if !permissions.hasAccessibility() {
    Log.warn("Accessibility permission NOT granted.")
}
if !permissions.hasInputMonitoring() {
    Log.warn("Input Monitoring permission NOT granted.")
}

// 4. Shared state.
Task { @MainActor in
    let state = AgentState()
    state.publicKeyBase64 = publicKey.base64EncodedString()

    // 5. Status bar (menu bar icon).
    let statusBar = StatusBarController(state: state)
    statusBar.install()

    // 6. USB-C Direct HID Daemon & ADB Auto-Bridge Daemon
    let usbDaemon = USBDaemon()
    usbDaemon.start()
    ADBBridgeDaemon.shared.start()

    // 7. Bonjour advertiser.
    let port: UInt16 = 7878
    let bonjour = BonjourAdvertiser(
        port: port,
        displayName: Host.current().localizedName ?? "Mac"
    )
    bonjour.start()

    // 8. TCP server.
    let server = TCPServer(
        port: port,
        keyPair: keyPair,
        state: state
    )
    Task {
        do {
            try await server.start()
            Log.info("TCP server listening on port \(port)")
            await MainActor.run {
                state.status = .waiting
            }
        } catch {
            Log.error("TCP server failed to start: \(error)")
            await MainActor.run {
                state.status = .error
            }
        }
    }

    // 9. Main window (shown only if NOT running in headless background mode).
    if !isHeadless {
        let mainView = NeonDashboardView(state: state)
        let hosting = NSHostingController(rootView: mainView)
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 520, height: 640),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "Elysium Nexus"
        window.contentViewController = hosting
        window.isReleasedWhenClosed = false
        window.center()
        window.titlebarAppearsTransparent = true
        window.backgroundColor = NSColor(red: 0.02, green: 0.02, blue: 0.07, alpha: 1.0)
        window.minSize = NSSize(width: 440, height: 500)
        window.makeKeyAndOrderFront(nil)
    }
}

// 10. Run the NSApplication event loop.
if !isHeadless {
    app.activate(ignoringOtherApps: true)
}
app.run()

// ───────────────────────────────────────────────
// Agent State — observed by the SwiftUI dashboard
// and the menu bar controller.
// ───────────────────────────────────────────────
@MainActor
final class AgentState: ObservableObject {
    @Published var status: AgentStatus = .starting
    @Published var lastEventDescription: String = "—"
    @Published var lastEventAt: Date?
    @Published var connectedPeerName: String?
    @Published var pairedPeers: [String] = []
    @Published var publicKeyBase64: String = ""
    @Published var currentPin: String?
    @Published var eventCount: Int = 0

    enum AgentStatus: String {
        case starting = "Iniciando…"
        case waiting = "Esperando conexión"
        case pairing = "Emparejando"
        case connected = "Conectado"
        case error = "Error"
    }
}

// ───────────────────────────────────────────────
// NEON DASHBOARD — The main SwiftUI view.
// Futuristic, neon, phosphorescent, masculine
// colors, animated, with shadows.
// ───────────────────────────────────────────────

struct NeonColors {
    // Ultra-deep backgrounds with subtle blue undertone for depth
    static let background   = Color(red: 0.02, green: 0.02, blue: 0.07)
    static let surface      = Color(red: 0.05, green: 0.05, blue: 0.12)
    static let surfaceLight = Color(red: 0.08, green: 0.08, blue: 0.16)
    static let surfaceHover = Color(red: 0.06, green: 0.07, blue: 0.15)
    // Vibrant neon accents — max saturation, high luminance
    static let cyan    = Color(red: 0.0,  green: 0.92, blue: 1.0)    // electric cyan
    static let purple  = Color(red: 0.55, green: 0.15, blue: 1.0)    // deep ultraviolet
    static let magenta = Color(red: 1.0,  green: 0.05, blue: 0.55)   // hot pink
    static let green   = Color(red: 0.0,  green: 1.0,  blue: 0.55)   // phosphor green
    static let orange  = Color(red: 1.0,  green: 0.45, blue: 0.0)    // neon amber
    static let gold    = Color(red: 1.0,  green: 0.78, blue: 0.2)    // warm gold accent
    // Text with proper contrast (WCAG AA+)
    static let textPrimary   = Color(red: 0.95, green: 0.96, blue: 0.98)
    static let textSecondary = Color(red: 0.45, green: 0.48, blue: 0.58)
}

struct NeonDashboardView: View {
    @ObservedObject var state: AgentState
    @State private var pulsePhase: Double = 0
    @State private var glowIntensity: Double = 0.5

    var body: some View {
        ZStack {
            // Ultra-deep background with rich tri-color gradient
            LinearGradient(
                colors: [
                    Color(red: 0.01, green: 0.01, blue: 0.05),
                    NeonColors.background,
                    Color(red: 0.03, green: 0.02, blue: 0.08)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            // Subtle radial glow in center for depth
            RadialGradient(
                colors: [
                    NeonColors.purple.opacity(0.06),
                    Color.clear
                ],
                center: .center,
                startRadius: 40,
                endRadius: 350
            )
            .ignoresSafeArea()

            // Animated background particles — more visible
            NeonParticlesView()
                .opacity(0.5)

            VStack(spacing: 0) {
                // ─── LOGO + HEADER ───
                LogoHeaderView(state: state, pulsePhase: pulsePhase)
                    .padding(.top, 20)
                    .padding(.bottom, 14)

                // ─── STATUS CARD ───
                StatusCardView(state: state, glowIntensity: glowIntensity)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 14)

                // ─── PIN DISPLAY (when pairing) ───
                if let pin = state.currentPin, state.status == .pairing {
                    PinDisplayCardView(pin: pin)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 14)
                        .transition(.opacity.combined(with: .scale(scale: 0.9)))
                }

                // ─── CONNECTION INFO ───
                ConnectionInfoCardView(state: state)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 14)

                // ─── LAST EVENT ───
                if state.status == .connected {
                    LastEventCardView(state: state)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 14)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                }

                Spacer()

                // ─── FOOTER ───
                FooterView(state: state)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
        }
        .frame(minWidth: 440, minHeight: 500)
        .onAppear {
            withAnimation(.easeInOut(duration: 2.5).repeatForever(autoreverses: true)) {
                pulsePhase = 1
            }
            withAnimation(.easeInOut(duration: 3.5).repeatForever(autoreverses: true)) {
                glowIntensity = 1
            }
        }
        .animation(.spring(response: 0.5), value: state.status)
        .animation(.spring(response: 0.5), value: state.currentPin)
    }
}

// ─── ANIMATED LOGO HEADER ───
struct LogoHeaderView: View {
    @ObservedObject var state: AgentState
    let pulsePhase: Double
    @State private var rotation: Double = 0

    var statusColor: Color {
        switch state.status {
        case .starting: return NeonColors.orange
        case .waiting: return NeonColors.cyan
        case .pairing: return NeonColors.orange
        case .connected: return NeonColors.green
        case .error: return NeonColors.magenta
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            // Animated neon hexagon logo
            ZStack {
                // Outer ambient glow
                Circle()
                    .fill(statusColor.opacity(0.08))
                    .frame(width: 110, height: 110)
                    .blur(radius: 20)
                    .scaleEffect(1 + pulsePhase * 0.12)

                // Outer rotating ring — thicker, more vivid
                Circle()
                    .stroke(
                        AngularGradient(
                            colors: [
                                NeonColors.cyan,
                                NeonColors.purple,
                                NeonColors.magenta,
                                NeonColors.green.opacity(0.6),
                                NeonColors.cyan
                            ],
                            center: .center
                        ),
                        lineWidth: 2.5
                    )
                    .frame(width: 92, height: 92)
                    .rotationEffect(.degrees(rotation))
                    .shadow(color: NeonColors.cyan.opacity(0.7), radius: 16)
                    .shadow(color: NeonColors.purple.opacity(0.3), radius: 24)

                // Inner pulsing circle — richer gradient
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                statusColor.opacity(0.35),
                                NeonColors.purple.opacity(0.08),
                                NeonColors.background
                            ],
                            center: .center,
                            startRadius: 5,
                            endRadius: 42
                        )
                    )
                    .frame(width: 74, height: 74)
                    .scaleEffect(1 + pulsePhase * 0.1)

                // Icon — brighter glow
                Image(systemName: "gamecontroller.fill")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [NeonColors.cyan, NeonColors.purple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(color: NeonColors.cyan.opacity(0.9), radius: 10)
                    .shadow(color: NeonColors.purple.opacity(0.4), radius: 16)
            }
            .onAppear {
                withAnimation(.linear(duration: 7).repeatForever(autoreverses: false)) {
                    rotation = 360
                }
            }

            // Title — multi-layer glow for neon sign effect
            Text("ELYSIUM NEXUS")
                .font(.system(size: 24, weight: .black, design: .rounded))
                .tracking(5)
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            NeonColors.cyan,
                            NeonColors.cyan.opacity(0.9),
                            NeonColors.purple
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .shadow(color: NeonColors.cyan.opacity(0.7), radius: 8)
                .shadow(color: NeonColors.cyan.opacity(0.3), radius: 20)

            Text("UNIVERSAL CONTROLLER")
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .tracking(7)
                .foregroundColor(NeonColors.textSecondary.opacity(0.8))
        }
    }
}

// ─── STATUS CARD ───
struct StatusCardView: View {
    @ObservedObject var state: AgentState
    let glowIntensity: Double

    var statusColor: Color {
        switch state.status {
        case .starting: return NeonColors.orange
        case .waiting: return NeonColors.cyan
        case .pairing: return NeonColors.orange
        case .connected: return NeonColors.green
        case .error: return NeonColors.magenta
        }
    }

    var statusIcon: String {
        switch state.status {
        case .starting: return "bolt.fill"
        case .waiting: return "antenna.radiowaves.left.and.right"
        case .pairing: return "lock.shield.fill"
        case .connected: return "checkmark.shield.fill"
        case .error: return "exclamationmark.triangle.fill"
        }
    }

    var body: some View {
        HStack(spacing: 16) {
            // Pulsing status indicator — richer layered glow
            ZStack {
                Circle()
                    .fill(statusColor.opacity(0.12))
                    .frame(width: 56, height: 56)
                    .scaleEffect(1 + glowIntensity * 0.2)
                    .blur(radius: 4)
                Circle()
                    .fill(statusColor.opacity(0.25))
                    .frame(width: 44, height: 44)
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [statusColor.opacity(0.5), statusColor.opacity(0.15)],
                            center: .center,
                            startRadius: 4,
                            endRadius: 18
                        )
                    )
                    .frame(width: 36, height: 36)
                Image(systemName: statusIcon)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .shadow(color: statusColor, radius: 8)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(state.status.rawValue.uppercased())
                    .font(.system(size: 14, weight: .black, design: .rounded))
                    .foregroundColor(statusColor)
                    .shadow(color: statusColor.opacity(0.6), radius: 6)

                Text("Puerto 7878 · Wi-Fi · Cifrado E2E")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundColor(NeonColors.textSecondary)
            }

            Spacer()

            // Status pill — brighter, glassmorphism feel
            Text(state.status == .connected ? "ONLINE" : "READY")
                .font(.system(size: 10, weight: .black, design: .rounded))
                .tracking(2)
                .foregroundColor(statusColor)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(statusColor.opacity(0.12))
                        .overlay(Capsule().stroke(statusColor.opacity(0.6), lineWidth: 1.5))
                        .shadow(color: statusColor.opacity(0.3), radius: 8)
                )
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(
                    LinearGradient(
                        colors: [NeonColors.surface, NeonColors.surfaceLight.opacity(0.5)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(statusColor.opacity(0.35), lineWidth: 1)
                )
                .shadow(color: statusColor.opacity(0.18), radius: 16)
                .shadow(color: Color.black.opacity(0.4), radius: 8, y: 4)
        )
    }
}

// ─── PIN DISPLAY CARD (during pairing) ───
struct PinDisplayCardView: View {
    let pin: String
    @State private var digitScale: [Double] = Array(repeating: 0.5, count: 6)

    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 6) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(NeonColors.orange)
                Text("PIN DE EMPAREJAMIENTO")
                    .font(.system(size: 13, weight: .black, design: .rounded))
                    .tracking(3)
                    .foregroundColor(NeonColors.orange)
            }
            .shadow(color: NeonColors.orange.opacity(0.5), radius: 4)

            // Big neon PIN digits
            HStack(spacing: 10) {
                ForEach(Array(pin.enumerated()), id: \.offset) { index, char in
                    Text(String(char))
                        .font(.system(size: 40, weight: .black, design: .rounded))
                        .foregroundColor(NeonColors.cyan)
                        .frame(width: 52, height: 64)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(NeonColors.cyan.opacity(0.1))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(NeonColors.cyan.opacity(0.6), lineWidth: 2)
                                )
                                .shadow(color: NeonColors.cyan.opacity(0.4), radius: 8)
                        )
                        .scaleEffect(digitScale[index])
                        .onAppear {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.6).delay(Double(index) * 0.08)) {
                                digitScale[index] = 1.0
                            }
                        }
                }
            }

            Text("Escribe este PIN en tu teléfono Android")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(NeonColors.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(NeonColors.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(NeonColors.orange.opacity(0.4), lineWidth: 1)
                )
                .shadow(color: NeonColors.orange.opacity(0.2), radius: 16)
        )
    }
}

// ─── CONNECTION INFO ───
struct ConnectionInfoCardView: View {
    @ObservedObject var state: AgentState

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: "network")
                    .foregroundColor(NeonColors.purple)
                Text("INFORMACIÓN DE CONEXIÓN")
                    .font(.system(size: 11, weight: .black, design: .rounded))
                    .tracking(2)
                    .foregroundColor(NeonColors.purple)
            }

            HStack {
                InfoRowView(
                    icon: "wifi",
                    label: "Red",
                    value: Host.current().localizedName ?? "Local",
                    color: NeonColors.cyan
                )
                Spacer()
                InfoRowView(
                    icon: "number",
                    label: "Puerto",
                    value: "7878",
                    color: NeonColors.green
                )
            }

            HStack {
                InfoRowView(
                    icon: "lock.fill",
                    label: "Cifrado",
                    value: "X25519 + ChaCha20",
                    color: NeonColors.purple
                )
                Spacer()
                InfoRowView(
                    icon: "bonjour",
                    label: "mDNS",
                    value: "_elysium._tcp",
                    color: NeonColors.orange
                )
            }

            if let peer = state.connectedPeerName {
                InfoRowView(
                    icon: "iphone",
                    label: "Dispositivo",
                    value: peer,
                    color: NeonColors.green
                )
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(
                    LinearGradient(
                        colors: [NeonColors.surface, NeonColors.surfaceLight.opacity(0.4)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(NeonColors.purple.opacity(0.25), lineWidth: 1)
                )
                .shadow(color: NeonColors.purple.opacity(0.1), radius: 12)
                .shadow(color: Color.black.opacity(0.3), radius: 6, y: 3)
        )
    }
}

struct InfoRowView: View {
    let icon: String
    let label: String
    let value: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 11))
                .foregroundColor(color)
            VStack(alignment: .leading, spacing: 1) {
                Text(label.uppercased())
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(NeonColors.textSecondary)
                Text(value)
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundColor(NeonColors.textPrimary)
                    .lineLimit(1)
            }
        }
    }
}

// ─── LAST EVENT CARD ───
struct LastEventCardView: View {
    @ObservedObject var state: AgentState

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(NeonColors.green.opacity(0.15))
                    .frame(width: 36, height: 36)
                Image(systemName: "bolt.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(NeonColors.green)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("ÚLTIMO EVENTO")
                    .font(.system(size: 9, weight: .black, design: .rounded))
                    .tracking(2)
                    .foregroundColor(NeonColors.green)
                Text(state.lastEventDescription)
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundColor(NeonColors.textPrimary)
                    .lineLimit(1)
            }

            Spacer()

            Text("\(state.eventCount)")
                .font(.system(size: 20, weight: .black, design: .rounded))
                .foregroundColor(NeonColors.green)
                .shadow(color: NeonColors.green.opacity(0.5), radius: 4)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(
                    LinearGradient(
                        colors: [NeonColors.surface, NeonColors.surfaceLight.opacity(0.3)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(NeonColors.green.opacity(0.25), lineWidth: 1)
                )
                .shadow(color: NeonColors.green.opacity(0.12), radius: 12)
                .shadow(color: Color.black.opacity(0.3), radius: 6, y: 3)
        )
    }
}

// ─── FOOTER ───
struct FooterView: View {
    @ObservedObject var state: AgentState

    var body: some View {
        HStack {
            Text("v1.0.0")
                .font(.system(size: 10, weight: .medium, design: .monospaced))
                .foregroundColor(NeonColors.textSecondary.opacity(0.7))
            Spacer()
            HStack(spacing: 4) {
                Circle()
                    .fill(NeonColors.green)
                    .frame(width: 5, height: 5)
                    .shadow(color: NeonColors.green.opacity(0.8), radius: 3)
                Text("Elysium Nexus · \(Host.current().localizedName ?? "Mac")")
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(NeonColors.textSecondary.opacity(0.7))
            }
        }
    }
}

// ─── ANIMATED NEON PARTICLES ───
struct NeonParticlesView: View {
    @State private var phase: Double = 0

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let time = timeline.date.timeIntervalSinceReferenceDate
                // More particles, varying sizes, richer colors
                for i in 0..<35 {
                    let freq1 = 0.25 + Double(i % 5) * 0.05
                    let freq2 = 0.18 + Double(i % 7) * 0.04
                    let x = (sin(time * freq1 + Double(i) * 0.65) + 1) / 2 * size.width
                    let y = (cos(time * freq2 + Double(i) * 1.05) + 1) / 2 * size.height
                    let r = 1.2 + sin(time * 0.8 + Double(i) * 0.4) * 1.5
                    let alpha = 0.2 + sin(time * 0.4 + Double(i) * 0.25) * 0.15
                    let colors: [Color] = [NeonColors.cyan, NeonColors.purple, NeonColors.magenta, NeonColors.green, NeonColors.gold]
                    let color = colors[i % colors.count]
                    context.fill(
                        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                        with: .color(color.opacity(alpha))
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────────
// Logger
// ───────────────────────────────────────────────
enum Log {
    static func info(_ msg: String) {
        print("[INFO]  \(timestamp()) \(msg)")
        fflush(stdout)
    }
    static func warn(_ msg: String) {
        print("[WARN]  \(timestamp()) \(msg)")
        fflush(stdout)
    }
    static func error(_ msg: String) {
        print("[ERROR] \(timestamp()) \(msg)")
        fflush(stdout)
    }
    private static func timestamp() -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withTime, .withDashSeparatorInDate, .withColonSeparatorInTime]
        return f.string(from: Date())
    }
}
