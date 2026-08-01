//
// Elysium Nexus — Mac Agent
// SwiftUI window that shows the 6-digit pairing
// PIN to the user. The user must type this same
// PIN on the phone to confirm the pairing. If the
// phone never receives a confirmation (i.e. the
// user closes the window without typing), the
// connection is closed by the server.
//
import Foundation
import SwiftUI
import AppKit

@MainActor
final class PINWindow {
    private let pin: String
    private let onResult: (Bool) -> Void
    private var window: NSWindow?
    private var fieldText: String = ""
    private var errorMessage: String?

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
            contentRect: NSRect(x: 0, y: 0, width: 360, height: 360),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        w.title = "Emparejar Elysium Nexus"
        w.contentViewController = hosting
        w.isReleasedWhenClosed = false
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
        } else {
            errorMessage = "PIN incorrecto. Intenta de nuevo."
        }
    }
}

private struct PINView: View {
    let pin: String
    let onConfirm: (String) -> Void
    let onCancel: () -> Void

    @State private var typed: String = ""
    @State private var error: String?

    var body: some View {
        VStack(spacing: 20) {
            // Header
            HStack {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(.cyan)
                VStack(alignment: .leading) {
                    Text("PIN de emparejamiento")
                        .font(.headline)
                    Text("Tu Mac está lista para conectar.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            // PIN display — 6 large digits
            HStack(spacing: 8) {
                ForEach(Array(pin.enumerated()), id: \.offset) { _, char in
                    Text(String(char))
                        .font(.system(size: 36, weight: .bold, design: .rounded))
                        .foregroundStyle(.cyan)
                        .frame(width: 36, height: 50)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Color.cyan.opacity(0.15))
                        )
                }
            }
            // Explanation
            Text("Escribe este PIN en tu teléfono para confirmar la conexión.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            // Text field
            TextField("Escribe el PIN", text: $typed)
                .textFieldStyle(.roundedBorder)
                .multilineTextAlignment(.center)
                .font(.title3)
            if let error = error {
                Text(error)
                    .foregroundStyle(.red)
                    .font(.subheadline)
            }
            // Actions
            HStack(spacing: 12) {
                Button("Cancelar", role: .cancel) { onCancel() }
                    .buttonStyle(.bordered)
                Spacer()
                Button("Confirmar") { onConfirm(typed) }
                    .buttonStyle(.borderedProminent)
                    .disabled(typed.count != 6)
            }
        }
        .padding(24)
        .frame(width: 360)
    }
}
