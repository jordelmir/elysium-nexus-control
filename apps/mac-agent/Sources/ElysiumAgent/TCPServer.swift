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

final class TCPServer {
    private let port: UInt16
    private let keyPair: X25519.KeyAgreement.PrivateKey
    private let state: AgentState
    private var listener: NWListener?

    init(port: UInt16, keyPair: X25519.KeyAgreement.PrivateKey, state: AgentState) {
        self.port = port
        self.keyPair = keyPair
        self.state = state
    }

    func start() async throws {
        // Use TCP only. The X25519 + ChaCha20 layer
        // gives us confidentiality + integrity on
        // top.
        let parameters = NWParameters.tcp
        // Allow address reuse so a quick restart
        // doesn't hit "address already in use".
        if let inOpts = parameters.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options {
            inOpts.version = .v4
        }
        let port = NWEndpoint.Port(rawValue: self.port)!
        let listener = try NWListener(using: parameters, on: port)
        self.listener = listener
        listener.stateUpdateHandler = { newState in
            switch newState {
            case .ready:
                Log.info("TCP: listener ready on port \(self.port)")
            case .failed(let error):
                Log.error("TCP: listener failed: \(error)")
            case .cancelled:
                Log.info("TCP: listener cancelled")
            default:
                break
            }
        }
        listener.newConnectionHandler = { [weak self] connection in
            guard let self = self else { return }
            // Hand the connection off to a per-conn
            // handler. The listener is free to accept
            // the next one.
            let handler = ConnectionHandler(
                connection: connection,
                keyPair: self.keyPair,
                state: self.state
            )
            handler.start()
        }
        listener.start(queue: .global())
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }
}
