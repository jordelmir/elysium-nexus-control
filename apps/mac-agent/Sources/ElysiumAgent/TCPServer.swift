//
// Elysium Nexus — Mac Agent
// TCP server using the modern `Network` framework.
//
// Each accepted connection runs the pairing
// handshake, then becomes an event-stream
// connection: bytes in, frames out, events
// injected via CGEvent.
//
// Why `Network` and not `Socket`? `Network` is
// async-first, integrates with `Task` cleanly, and
// handles TLS for free (we use plain TCP for now
// because the X25519 + ChaCha20 layer already
// provides end-to-end encryption and authenticated
// integrity). TLS can be added later without
// changing the frame format.
//
import Foundation
import Network
import CryptoKit

final class TCPServer {
    private let port: UInt16
    private let keyPair: Curve25519.KeyAgreement.PrivateKey
    private let state: AgentState
    private var activeConnections: [ConnectionHandler] = []
    private var listener: NWListener?
    private let lock = NSLock()

    init(port: UInt16, keyPair: Curve25519.KeyAgreement.PrivateKey, state: AgentState) {
        self.port = port
        self.keyPair = keyPair
        self.state = state
    }

    func removeConnection(_ handler: ConnectionHandler) {
        lock.lock()
        defer { lock.unlock() }
        activeConnections.removeAll { $0 === handler }
    }

    func start() async throws {
        let tcpOpts = NWProtocolTCP.Options()
        tcpOpts.noDelay = true
        let parameters = NWParameters(tls: nil, tcp: tcpOpts)
        parameters.allowLocalEndpointReuse = true
        if let inOpts = parameters.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options {
            inOpts.version = .v4
        }
        let port = NWEndpoint.Port(rawValue: self.port)!
        let listener = try NWListener(using: parameters, on: port)
        self.listener = listener
        listener.newConnectionHandler = { [weak self] connection in
            guard let self = self else {
                Log.error("TCP: accepted connection dropped because server owner was released")
                return
            }
            Log.info("TCP: accepted peer endpoint=\(String(describing: connection.endpoint))")
            let handler = ConnectionHandler(
                connection: connection,
                keyPair: self.keyPair,
                state: self.state,
                onClose: { [weak self] h in
                    self?.removeConnection(h)
                }
            )
            self.lock.lock()
            self.activeConnections.append(handler)
            self.lock.unlock()
            handler.start()
        }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            listener.stateUpdateHandler = { newState in
                switch newState {
                case .ready:
                    Log.info("TCP: listener ready on port \(self.port)")
                    listener.stateUpdateHandler = nil
                    continuation.resume()
                case .failed(let error):
                    Log.error("TCP: listener failed on port \(self.port): \(error)")
                    listener.stateUpdateHandler = nil
                    continuation.resume(throwing: error)
                case .cancelled:
                    listener.stateUpdateHandler = nil
                    continuation.resume(throwing: CancellationError())
                default:
                    break
                }
            }
            listener.start(queue: DispatchQueue(label: "com.elysium.nexus.listener"))
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }
}
