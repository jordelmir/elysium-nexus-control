//
// Elysium Nexus — Mac Agent
// Per-connection state machine.
//
// States:
//   .awaitingHello — server reads the client's
//                     X25519 public key (32 bytes).
//   .sendingHelloAck — server sends its own
//                      public key.
//   .awaitingPin    — server reads 6 encrypted
//                      PIN digits and compares
//                      against the locally-
//                      generated 6-digit PIN.
//   .ready          — pairing complete; server
//                      reads event frames and
//                      dispatches them to
//                      `EventInjector`.
//   .closed         — connection finished (good
//                      or bad).
//
import Foundation
import Network
import CryptoKit
import AppKit

final class ConnectionHandler {
    enum State {
        case awaitingHello
        case sendingHelloAck
        case awaitingPin(generatedPin: String, digitsReceived: Int)
        case ready(channelKey: SymmetricKey, nonceCounter: NonceCounter)
        case closed
    }

    private let connection: NWConnection
    private let keyPair: X25519.KeyAgreement.PrivateKey
    private let state: AgentState
    private var state_: State = .awaitingHello
    private var receiveBuffer = Data()
    private var pinWindow: PINWindow?

    init(connection: NWConnection, keyPair: X25519.KeyAgreement.PrivateKey, state: AgentState) {
        self.connection = connection
        self.keyPair = keyPair
        self.state = state
    }

    func start() {
        Log.info("Conn: new connection from \(String(describing: connection.endpoint))")
        connection.stateUpdateHandler = { [weak self] newState in
            guard let self = self else { return }
            switch newState {
            case .ready:
                Log.info("Conn: TCP ready")
                // Send our HELLO_ACK with the
                // server's public key first. The
                // client uses this to derive the
                // shared secret.
                let ourPubKey = self.keyPair.publicKey.rawRepresentation
                let helloAck = FrameEncoder.encode(.helloAck, payload: ourPubKey)
                self.send(helloAck)
                self.transition(to: .sendingHelloAck)
                self.scheduleReceive()
            case .failed(let error):
                Log.error("Conn: failed: \(error)")
                self.transition(to: .closed)
            case .cancelled:
                Log.info("Conn: cancelled")
                self.transition(to: .closed)
            case .preparing, .setup, .waiting:
                break
            @unknown default:
                break
            }
        }
        connection.start(queue: .global())
    }

    // MARK: - Send / receive

    private func send(_ data: Data) {
        connection.send(content: data, completion: .contentProcessed { error in
            if let error = error {
                Log.error("Conn: send failed: \(error)")
            }
        })
    }

    private func scheduleReceive() {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            guard let self = self else { return }
            if let data = data, !data.isEmpty {
                self.receiveBuffer.append(data)
                self.drainFrames()
            }
            if let error = error {
                Log.error("Conn: receive error: \(error)")
                self.transition(to: .closed)
                return
            }
            if isComplete {
                self.transition(to: .closed)
                return
            }
            // Keep receiving.
            self.scheduleReceive()
        }
    }

    private func drainFrames() {
        while let frame = FrameParser.read(from: &receiveBuffer) {
            handle(frame)
            if case .closed = state_ { break }
        }
    }

    // MARK: - State machine

    private func transition(to newState: State) {
        state_ = newState
        switch newState {
        case .awaitingPin(let pin, let digits):
            Log.info("Conn: awaiting pin (\(digits)/6 received)")
            // Show the PIN window when we have the
            // generated PIN (i.e. when the first
            // digit arrives).
            if digits == 0 {
                showPinWindow(pin: pin)
            }
        case .ready:
            Log.info("Conn: READY (channel open)")
            Task { @MainActor in
                self.state.status = .connected
                self.state.lastEventDescription = "Conectado"
            }
        case .closed:
            Log.info("Conn: closed")
            connection.cancel()
            pinWindow?.close()
        default:
            break
        }
    }

    private func handle(_ frame: Frame) {
        switch (state_, frame.type) {
        case (.sendingHelloAck, .hello):
            // We expect a HELLO from the client. We
            // sent our public key already; now derive
            // the shared secret.
            guard frame.payload.count == 32 else {
                Log.error("Conn: HELLO payload must be 32 bytes, got \(frame.payload.count)")
                transition(to: .closed)
                return
            }
            let theirPubKey: X25519.KeyAgreement.PublicKey
            do {
                theirPubKey = try X25519.KeyAgreement.PublicKey(rawRepresentation: frame.payload)
            } catch {
                Log.error("Conn: invalid HELLO public key: \(error)")
                transition(to: .closed)
                return
            }
            // Generate the 6-digit PIN and stash it
            // in the state. The user must type this
            // PIN on the phone to confirm.
            let pin = generatePin()
            // Derive the channel key (server side).
            let key = ChannelCipher.deriveKey(myPrivate: keyPair, theirPublic: theirPubKey)
            // Transition to awaitingPin with a fresh
            // nonce counter.
            // We re-use the state_ variable; store
            // the key temporarily.
            awaitingPinKey = key
            nonceCounter = NonceCounter()
            generatedPin = pin
            transition(to: .awaitingPin(generatedPin: pin, digitsReceived: 0))
        case (.awaitingPin(let pin, let count), .pinDigit):
            guard let key = awaitingPinKey, let counter = nonceCounter else {
                Log.error("Conn: PIN_DIGIT received but no key set")
                transition(to: .closed)
                return
            }
            // Decrypt the digit. Payload is the
            // nonce ‖ ciphertext ‖ tag (12+ bytes).
            do {
                let plain = try ChannelCipher.open(frame.payload, key: key)
                guard plain.count == 1 else {
                    Log.error("Conn: PIN_DIGIT plaintext must be 1 byte, got \(plain.count)")
                    transition(to: .closed)
                    return
                }
                let receivedDigit = plain[0]
                let expectedDigit = pin.utf8[pin.utf8.index(pin.utf8.startIndex, offsetBy: count)]
                if receivedDigit == expectedDigit {
                    let nextCount = count + 1
                    if nextCount >= pin.utf8.count {
                        // All digits matched. Send
                        // PAIR_OK (encrypted) and
                        // transition to ready.
                        let okPayload = Data([0x01]) // 1 = ok
                        let enc = ChannelCipher.seal(okPayload, key: key)
                        send(FrameEncoder.encode(.pairOk, payload: enc))
                        transition(to: .ready(channelKey: key, nonceCounter: counter))
                    } else {
                        transition(to: .awaitingPin(generatedPin: pin, digitsReceived: nextCount))
                    }
                } else {
                    Log.warn("Conn: PIN digit \(count+1) mismatch (expected \(expectedDigit), got \(receivedDigit))")
                    let okPayload = Data([0x00]) // 0 = fail
                    let enc = ChannelCipher.seal(okPayload, key: key)
                    send(FrameEncoder.encode(.pairOk, payload: enc))
                    transition(to: .closed)
                }
            } catch {
                Log.error("Conn: failed to decrypt PIN digit: \(error)")
                transition(to: .closed)
            }
        case (.ready(let key, let counter), .mouseMove):
            if let payload = decodeMouseMove(frame.payload, key: key, counter: counter) {
                EventInjector.shared.moveBy(dx: payload.0, dy: payload.1)
            }
        case (.ready(let key, let counter), .mouseButton):
            if let payload = decodeMouseButton(frame.payload, key: key, counter: counter) {
                EventInjector.shared.click(button: payload.0, state: payload.1)
            }
        case (.ready(let key, let counter), .scroll):
            if let payload = decodeScroll(frame.payload, key: key, counter: counter) {
                EventInjector.shared.scroll(dx: payload.0, dy: payload.1)
            }
        case (.ready(let key, let counter), .key):
            if let payload = decodeKey(frame.payload, key: key, counter: counter) {
                EventInjector.shared.key(action: payload.0, hidUsage: payload.1, modifiers: payload.2)
            }
        case (.ready(let key, let counter), .pinch):
            if let payload = decodePinch(frame.payload, key: key, counter: counter) {
                EventInjector.shared.pinch(factor: payload)
            }
        case (.ready, .heartbeat), (.ready, .goodbye):
            break // No-op
        default:
            Log.warn("Conn: unexpected frame \(frame.type) in state \(state_)")
        }
    }

    // MARK: - Decoders (post-handshake frames are
    //         ChaCha20-Poly1305 sealed; we open
    //         them before dispatching).

    private func decodeMouseMove(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (Float, Float)? {
        // The on-the-wire payload for mouseMove is
        // the encrypted blob. Decrypt, then parse
        // (dx, dy).
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 8 else { return nil }
        let dx = plain.subdata(in: 0..<4).withUnsafeBytes { $0.load(as: Float32.self) }
        let dy = plain.subdata(in: 4..<8).withUnsafeBytes { $0.load(as: Float32.self) }
        Task { @MainActor in
            self.state.lastEventDescription = "Mouse dx=\(dx) dy=\(dy)"
            self.state.lastEventAt = Date()
        }
        return (dx, dy)
    }
    private func decodeMouseButton(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (MouseButton, ButtonState)? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 2 else { return nil }
        let b = MouseButton(rawValue: plain[0]) ?? .left
        let s = ButtonState(rawValue: plain[1]) ?? .up
        Task { @MainActor in
            self.state.lastEventDescription = "Click \(b) \(s)"
            self.state.lastEventAt = Date()
        }
        return (b, s)
    }
    private func decodeScroll(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (Float, Float)? {
        return decodeMouseMove(payload, key: key, counter: counter)
            .map { ($0.0, $0.1) }
    }
    private func decodeKey(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (KeyAction, UInt32, Modifiers)? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 9 else { return nil }
        let action = KeyAction(rawValue: plain[0]) ?? .down
        let usage = plain.subdata(in: 1..<5).withUnsafeBytes { $0.load(as: UInt32.self) }
        let mods = Modifiers(rawValue: plain.subdata(in: 5..<9).withUnsafeBytes { $0.load(as: UInt32.self) })
        Task { @MainActor in
            self.state.lastEventDescription = "Key \(action) usage=\(usage)"
            self.state.lastEventAt = Date()
        }
        return (action, usage, mods)
    }
    private func decodePinch(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> Float? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 4 else { return nil }
        return plain.withUnsafeBytes { $0.load(as: Float32.self) }
    }

    // MARK: - State (encryption-related, kept outside
    //         the State enum to keep the enum simple).

    private var awaitingPinKey: SymmetricKey?
    private var nonceCounter: NonceCounter?
    private var generatedPin: String = ""

    // MARK: - PIN generation + window

    private func generatePin() -> String {
        // 6-digit numeric PIN. Uses arc4random for
        // cryptographic strength (we want a fresh
        // PIN per pairing session).
        var pin = ""
        for _ in 0..<6 {
            let d = Int.random(in: 0..<10) // arc4random under the hood
            pin.append(String(d))
        }
        return pin
    }

    private func showPinWindow(pin: String) {
        Task { @MainActor in
            self.state.status = .pairing
            self.pinWindow = PINWindow(pin: pin) { [weak self] confirmed in
                guard let self = self else { return }
                if !confirmed {
                    Log.info("Conn: user rejected pairing")
                    let key = self.awaitingPinKey ?? SymmetricKey(size: .bits256)
                    let fail = Data([0x00])
                    let enc = ChannelCipher.seal(fail, key: key)
                    self.send(FrameEncoder.encode(.pairOk, payload: enc))
                    self.transition(to: .closed)
                }
            }
            self.pinWindow?.show()
        }
    }
}
