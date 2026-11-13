import AppKit
import SwiftUI

/// Presents ScreenMirror's Android -> Mac receiver inside the Nexus Mac app.
/// Nexus remains the sole app entry point and keeps ownership of port 7878.
@MainActor
enum ScreenMirrorWindow {
    private static var window: NSWindow?

    static func show() {
        if let window {
            window.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }

        let root = ScreenMirrorDashboardHost()
        let hosting = NSHostingController(rootView: root)
        let newWindow = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 520, height: 860),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        newWindow.title = "Elysium Nexus · Pantalla Android"
        newWindow.contentViewController = hosting
        newWindow.isReleasedWhenClosed = false
        newWindow.center()
        newWindow.minSize = NSSize(width: 420, height: 620)
        newWindow.makeKeyAndOrderFront(nil)
        window = newWindow
        NSApp.activate(ignoringOtherApps: true)
    }
}

private struct ScreenMirrorDashboardHost: View {
    @StateObject private var engine = RuntimeOrchestrator()

    var body: some View {
        DashboardView()
            .environmentObject(engine)
            .onDisappear { engine.shutdownEngine() }
    }
}
