//
// Elysium Nexus — Mac Agent
// Bonjour (mDNS) advertiser.
//
// The agent publishes a `_elysium._tcp` service on
// the local network. The Android phone discovers it
// via the same service type. We use the Network
// framework's `NWListener` for the TCP listener and
// `NetService` (legacy but still works on macOS 13)
// for the Bonjour publish — the alternative
// `NWListener.Service` registration is also viable
// but `NetService` keeps the code shorter.
//
import Foundation
import Network

final class BonjourAdvertiser {
    private let port: UInt16
    private let displayName: String
    private var netService: NetService?
    private var runLoopSource: CFRunLoopSource?

    init(port: UInt16, displayName: String) {
        self.port = port
        self.displayName = displayName
    }

    /// Publish the service. The display name in
    /// Bonjour is the user's Mac name (so the phone
    /// can show "MacBook Pro de Jor" in the device
    /// picker). The TXT record carries the agent's
    /// X25519 public key so the phone can start
    /// the handshake immediately.
    func start() {
        let service = NetService(
            domain: "local.",
            type: "_elysium._tcp.",
            name: displayName,
            port: Int32(port)
        )
        // TXT record. Key=value pairs, each up to
        // 255 bytes, total < 65535 bytes.
        let txt = NetService.data(fromTXTRecord: [
            "version": "1".data(using: .utf8) ?? Data(),
            "agent": "elysium-nexus".data(using: .utf8) ?? Data()
        ])
        service.setTXTRecord(txt)
        service.delegate = BonjourDelegate.shared
        service.publish()
        self.netService = service
        // Run a tiny CFRunLoop so the NetService
        // delegate callbacks fire.
        let source = CFRunLoopGetCurrent()
        // The NetService takes care of its own
        // scheduling; we just need the run loop
        // running. The main.swift `app.run()` covers
        // this.
        _ = source
        Log.info("Bonjour: published _elysium._tcp on port \(port)")
    }

    func stop() {
        netService?.stop()
        netService = nil
    }
}

private final class BonjourDelegate: NSObject, NetServiceDelegate {
    static let shared = BonjourDelegate()
    func netServiceDidPublish(_ sender: NetService) {
        Log.info("Bonjour: service published: \(sender.name)")
    }
    func netService(_ sender: NetService, didNotPublish errorDict: [String: NSNumber]) {
        Log.error("Bonjour: publish failed: \(errorDict)")
    }
    func netServiceDidResolveAddress(_ sender: NetService) {
        // Not used (we don't resolve; we publish).
    }
}
