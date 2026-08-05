//
// Elysium Nexus — Mac Agent
// Menu-bar UI. A small icon in the macOS menu bar
// shows the current state (waiting / pairing /
// connected / error) and exposes a menu with the
// agent's public key, the current PIN (when pairing),
// and a Quit item.
//
import Foundation
import AppKit

@MainActor
final class StatusBarController: NSObject, NSMenuDelegate {
    private var statusItem: NSStatusItem!
    private let state: AgentState

    init(state: AgentState) {
        self.state = state
        super.init()
    }

    func install() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "bolt.horizontal.fill", accessibilityDescription: "Elysium")
            button.image?.isTemplate = true
        }
        let menu = NSMenu()
        menu.delegate = self
        statusItem.menu = menu
        refresh()
        // Re-render the menu when the state changes.
        // (Observation isn't ideal here; we
        // periodically refresh from the run loop.)
        Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    private func refresh() {
        guard let menu = statusItem.menu else { return }
        menu.removeAllItems()
        // Title.
        let title = NSMenuItem(title: "Elysium Nexus — \(state.status.rawValue)", action: nil, keyEquivalent: "")
        title.isEnabled = false
        menu.addItem(title)
        // Peer.
        if let peer = state.connectedPeerName {
            let p = NSMenuItem(title: "Conectado a: \(peer)", action: nil, keyEquivalent: "")
            p.isEnabled = false
            menu.addItem(p)
        }
        // Last event.
        if !state.lastEventDescription.isEmpty && state.lastEventDescription != "—" {
            let l = NSMenuItem(title: "Último: \(state.lastEventDescription)", action: nil, keyEquivalent: "")
            l.isEnabled = false
            menu.addItem(l)
        }
        // Public key (for the user to verify).
        menu.addItem(NSMenuItem.separator())
        let pk = NSMenuItem(title: "Llave pública:", action: nil, keyEquivalent: "")
        pk.isEnabled = false
        menu.addItem(pk)
        let pkVal = NSMenuItem(title: state.publicKeyBase64.prefix(40) + "…", action: nil, keyEquivalent: "")
        pkVal.isEnabled = false
        menu.addItem(pkVal)
        // Quit.
        menu.addItem(NSMenuItem.separator())
        let quit = NSMenuItem(title: "Salir", action: #selector(quitAction), keyEquivalent: "q")
        quit.target = self
        menu.addItem(quit)
    }

    @objc private func quitAction() {
        NSApplication.shared.terminate(nil)
    }
}
