import AppKit
import SwiftUI

/// Vanguard Fabric's console and node panels run as windows owned by the
/// Elysium Nexus Mac process. The Nexus agent remains the only owner of its
/// Android pairing and screen-control listener.
@MainActor
enum VanguardFabricWindows {
    private static var consoleWindow: NSWindow?
    private static var nodeWindow: NSWindow?

    static func showConsole() {
        if let consoleWindow {
            consoleWindow.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }
        let window = makeWindow(
            title: "Elysium Nexus · Vanguard Fabric Console",
            rootView: FabricConsoleHost(),
            size: NSSize(width: 1240, height: 780),
            minimumSize: NSSize(width: 1000, height: 640)
        )
        consoleWindow = window
    }

    static func showNode() {
        if let nodeWindow {
            nodeWindow.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            return
        }
        let window = makeWindow(
            title: "Elysium Nexus · Vanguard Node",
            rootView: FabricNodeHost(),
            size: NSSize(width: 520, height: 700),
            minimumSize: NSSize(width: 440, height: 580)
        )
        nodeWindow = window
    }

    private static func makeWindow<Content: View>(
        title: String,
        rootView: Content,
        size: NSSize,
        minimumSize: NSSize
    ) -> NSWindow {
        let window = NSWindow(
            contentRect: NSRect(origin: .zero, size: size),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = title
        window.contentViewController = NSHostingController(rootView: rootView)
        window.isReleasedWhenClosed = false
        window.minSize = minimumSize
        window.center()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
        return window
    }
}

private struct FabricConsoleHost: View {
    @StateObject private var state = ConsoleAppState()

    var body: some View {
        ConsoleView()
            .environmentObject(state)
    }
}

private struct FabricNodeHost: View {
    @StateObject private var state = NodeAppState()

    var body: some View {
        ContentView()
            .environmentObject(state)
            .onDisappear { Task { await state.stopNode() } }
    }
}
