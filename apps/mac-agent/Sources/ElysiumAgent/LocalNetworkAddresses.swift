import Darwin
import Foundation
import Network

struct LocalNetworkAddress: Identifiable, Equatable {
    let interfaceName: String
    let address: String

    var id: String { "\(interfaceName)-\(address)" }
}

enum LocalNetworkAddresses {
    static func current() -> [LocalNetworkAddress] {
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(head) }

        var found: [LocalNetworkAddress] = []
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let item = cursor {
            defer { cursor = item.pointee.ifa_next }
            let interface = item.pointee
            guard let address = interface.ifa_addr,
                  address.pointee.sa_family == UInt8(AF_INET),
                  (Int32(interface.ifa_flags) & IFF_UP) != 0,
                  (Int32(interface.ifa_flags) & IFF_LOOPBACK) == 0,
                  let namePointer = interface.ifa_name else { continue }

            let name = String(cString: namePointer)
            guard !name.hasPrefix("utun"), !name.hasPrefix("awdl"), !name.hasPrefix("llw") else { continue }

            let socketAddress = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee }
            var rawAddress = socketAddress.sin_addr
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &rawAddress, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { continue }
            let ipv4 = String(cString: buffer)
            guard isPrivateIPv4(ipv4) else { continue }
            found.append(LocalNetworkAddress(interfaceName: name, address: ipv4))
        }

        return found
            .reduce(into: [LocalNetworkAddress]()) { result, address in
                if !result.contains(address) { result.append(address) }
            }
            .sorted { rank($0.interfaceName) == rank($1.interfaceName)
                ? $0.interfaceName < $1.interfaceName
                : rank($0.interfaceName) < rank($1.interfaceName) }
    }

    private static func isPrivateIPv4(_ address: String) -> Bool {
        let octets = address.split(separator: ".").compactMap { Int($0) }
        guard octets.count == 4 else { return false }
        if octets[0] == 10 || octets[0] == 192 && octets[1] == 168 { return true }
        if octets[0] == 172 && (16...31).contains(octets[1]) { return true }
        return false
    }

    private static func rank(_ interface: String) -> Int {
        if interface == "en0" { return 0 }
        if interface == "en1" { return 1 }
        if interface.hasPrefix("en") { return 2 }
        if interface.hasPrefix("bridge") { return 3 }
        return 4
    }
}

final class LocalNetworkAddressMonitor {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.elysium.nexus.local-addresses")
    private let onChange: ([LocalNetworkAddress]) -> Void

    init(onChange: @escaping ([LocalNetworkAddress]) -> Void) {
        self.onChange = onChange
    }

    func start() {
        monitor.pathUpdateHandler = { [weak self] _ in
            guard let self else { return }
            self.onChange(LocalNetworkAddresses.current())
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }
}
