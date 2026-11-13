//
// Elysium Nexus — Mac Agent
// SwiftUI window that shows the 6-digit pairing
// PIN with the futuristic neon theme.
//
import Foundation
import SwiftUI
import AppKit

@MainActor
final class PINWindow {
    private let pin: String
    private let onResult: (Bool) -> Void
    private var window: NSWindow?

    init(pin: String, onResult: @escaping (Bool) -> Void) {
        self.pin = pin
        self.onResult = onResult
    }

    func show() {
        let view = PINView(
            pin: pin,
            onConfirm: { [weak self] typed in
                self?.handleConfirm(typed: typed)
            },
            onCancel: { [weak self] in
                self?.onResult(false)
                self?.close()
            }
        )
        let hosting = NSHostingController(rootView: view)
        let w = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 420, height: 400),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        w.title = "Emparejar — Elysium Nexus"
        w.contentViewController = hosting
        w.isReleasedWhenClosed = false
        w.titlebarAppearsTransparent = true
        w.backgroundColor = NSColor(red: 0.02, green: 0.02, blue: 0.07, alpha: 1.0)
        w.center()
        self.window = w
        w.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    func close() {
        window?.close()
        window = nil
    }

    private func handleConfirm(typed: String) {
        if typed == pin {
            onResult(true)
            close()
        }
    }
}

// ─── Neon-themed PIN View ───
private struct PINView: View {
    let pin: String
    let onConfirm: (String) -> Void
    let onCancel: () -> Void

    @State private var typed: String = ""
    @State private var error: String?
    @State private var digitScale: [Double] = Array(repeating: 0.5, count: 6)
    @State private var pulsePhase: Double = 0

    var body: some View {
        ZStack {
            // Background
            Color(red: 0.02, green: 0.02, blue: 0.07)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                // Header with neon glow
                HStack {
                    ZStack {
                        Circle()
                            .fill(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.2))
                            .frame(width: 44, height: 44)
                            .scaleEffect(1 + pulsePhase * 0.1)
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [
                                        Color(red: 0.0, green: 0.9, blue: 1.0),
                                        Color(red: 0.6, green: 0.2, blue: 1.0)
                                    ],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            )
                            .shadow(color: Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.6), radius: 6)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("PIN DE EMPAREJAMIENTO")
                            .font(.system(size: 14, weight: .black, design: .rounded))
                            .tracking(2)
                            .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                            .shadow(color: Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.4), radius: 4)
                        Text("Tu Mac está lista para conectar.")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(white: 0.55))
                    }
                    Spacer()
                }

                // 6 large neon PIN digits
                HStack(spacing: 10) {
                    ForEach(Array(pin.enumerated()), id: \.offset) { index, char in
                        Text(String(char))
                            .font(.system(size: 38, weight: .black, design: .rounded))
                            .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0))
                            .frame(width: 50, height: 62)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.1))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.5), lineWidth: 2)
                                    )
                                    .shadow(color: Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.3), radius: 8)
                            )
                            .scaleEffect(digitScale[index])
                            .onAppear {
                                withAnimation(.spring(response: 0.4, dampingFraction: 0.6).delay(Double(index) * 0.08)) {
                                    digitScale[index] = 1.0
                                }
                            }
                    }
                }

                // Instruction
                Text("Escribe este PIN en tu teléfono Android para\nconfirmar la conexión cifrada.")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color(white: 0.55))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)

                // Security badge
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.0, green: 1.0, blue: 0.6))
                    Text("Conexión cifrada con X25519 + ChaCha20-Poly1305")
                        .font(.system(size: 10, weight: .semibold, design: .monospaced))
                        .foregroundColor(Color(red: 0.0, green: 1.0, blue: 0.6))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(Color(red: 0.0, green: 1.0, blue: 0.6).opacity(0.1))
                        .overlay(Capsule().stroke(Color(red: 0.0, green: 1.0, blue: 0.6).opacity(0.3), lineWidth: 1))
                )

                // Actions
                HStack(spacing: 12) {
                    Button(action: { onCancel() }) {
                        Text("Cancelar")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(red: 1.0, green: 0.1, blue: 0.6))
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(Color(red: 1.0, green: 0.1, blue: 0.6).opacity(0.15))
                                    .overlay(Capsule().stroke(Color(red: 1.0, green: 0.1, blue: 0.6).opacity(0.4), lineWidth: 1))
                            )
                    }
                    .buttonStyle(.plain)

                    Spacer()
                }
            }
            .padding(28)
        }
        .frame(width: 420)
        .onAppear {
            withAnimation(.easeInOut(duration: 2).repeatForever(autoreverses: true)) {
                pulsePhase = 1
            }
        }
    }
}
