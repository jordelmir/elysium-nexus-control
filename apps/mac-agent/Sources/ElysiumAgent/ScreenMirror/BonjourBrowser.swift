import Foundation
import Network

/// Orquestador para el descubrimiento automático (Zero-Config) mediante Bonjour
class BonjourBrowser: ObservableObject {
    private var browser: NWBrowser?
    private let serviceType = "_jsm_video._tcp"
    private let serviceDomain = "local."
    
    @Published var discoveredHost: NWEndpoint.Host?
    @Published var discoveredPort: NWEndpoint.Port?
    @Published var discoveredEndpoint: NWEndpoint?
    @Published var isSearching = false

    func startDiscovery() {
        isSearching = true
        let descriptor = NWBrowser.Descriptor.bonjour(type: serviceType, domain: serviceDomain)
        browser = NWBrowser(for: descriptor, using: .tcp)
        
        browser?.stateUpdateHandler = { newState in
            print("Bonjour Browser state: \(newState)")
        }
        
        browser?.browseResultsChangedHandler = { [weak self] results, changes in
            for result in results {
                if case let .service(name, _, _, interface) = result.endpoint {
                    print("Found service: \(name), interface: \(String(describing: interface))")
                    // Aquí resolveremos el Endpoint a una IP directa para inyectarlo al RTC
                    DispatchQueue.main.async {
                        self?.discoveredEndpoint = result.endpoint
                        self?.isSearching = false
                    }
                }
            }
        }
        
        browser?.start(queue: .main)
    }
    
    func stopDiscovery() {
        browser?.cancel()
        isSearching = false
    }
}
