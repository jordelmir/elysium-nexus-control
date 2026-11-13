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
    private let keyPair: Curve25519.KeyAgreement.PrivateKey
    private let state: AgentState
    private let onClose: ((ConnectionHandler) -> Void)?
    private let sessionID = String(UUID().uuidString.prefix(8))
    private var state_: State = .awaitingHello
    private var receiveBuffer = Data()
    private var pinWindow: PINWindow?
    private let diagnosticsLock = NSLock()
    private var receivedFramesByType: [String: UInt64] = [:]
    private var decodedInputEvents: UInt64 = 0
    private var rejectedInputEvents: UInt64 = 0
    private var screenFramesSent: UInt64 = 0
    private var screenBytesSent: UInt64 = 0
    private var diagnosticsLastLogAt = Date.distantPast

    init(connection: NWConnection, keyPair: Curve25519.KeyAgreement.PrivateKey, state: AgentState, onClose: ((ConnectionHandler) -> Void)? = nil) {
        self.connection = connection
        self.keyPair = keyPair
        self.state = state
        self.onClose = onClose
    }

    func start() {
        Log.info("Conn[\(sessionID)]: accepted endpoint=\(String(describing: connection.endpoint))")
        connection.stateUpdateHandler = { [weak self] newState in
            guard let self = self else { return }
            switch newState {
            case .ready:
                Log.info("Conn[\(self.sessionID)]: TCP ready")
                self.transition(to: .awaitingHello)
                self.scheduleReceive()
            case .failed(let error):
                Log.error("Conn[\(self.sessionID)]: connection failed: \(error)")
                self.transition(to: .closed)
            case .cancelled:
                Log.info("Conn[\(self.sessionID)]: cancelled")
                self.transition(to: .closed)
            default:
                break
            }
        }
        connection.start(queue: .global())
    }

    // MARK: - Send / receive

    private func send(_ data: Data) {
        connection.send(content: data, completion: .contentProcessed { error in
            if let error = error {
                Log.error("Conn[\(self.sessionID)]: send failed bytes=\(data.count) error=\(error)")
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
                Log.error("Conn[\(self.sessionID)]: receive error: \(error)")
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
            Log.info("Conn[\(sessionID)]: pairing awaiting user PIN digitsReceived=\(digits); PIN redacted")
            Task { @MainActor in
                self.state.status = .pairing
                self.state.currentPin = pin
            }
            if digits == 0 {
                showPinWindow(pin: pin)
                MacNotificationManager.shared.notify(
                    id: "nexus_pin",
                    title: "⚡ Elysium Nexus — Emparejamiento",
                    subtitle: "PIN: \(pin)",
                    body: "Introduce este código de 6 dígitos en tu teléfono Android."
                )
            }
        case .ready:
            Log.info("Conn[\(sessionID)]: pairing READY encrypted channel active")
            MacNotificationManager.shared.notify(
                id: "nexus_connected",
                title: "🟢 Elysium Nexus — Conectado",
                subtitle: "Dispositivo Android Conectado",
                body: "Control de mouse, teclado y pantalla activo.",
                category: "NEXUS_REMOTE_SESSION"
            )
            Task { @MainActor in
                self.state.status = .connected
                self.state.currentPin = nil
                self.state.lastEventDescription = "Conectado"
            }
        case .closed:
            Log.info("Conn[\(sessionID)]: closed")
            MacNotificationManager.shared.notify(
                id: "nexus_connected",
                title: "🔴 Elysium Nexus — Desconectado",
                body: "La sesión remota con Android ha finalizado."
            )
            ScreenCaptureService.shared.stop()
            connection.cancel()
            onClose?(self)
            Task { @MainActor in
                self.state.currentPin = nil
                if self.state.status != .connected {
                    self.state.status = .waiting
                }
                self.pinWindow?.close()
            }
        default:
            break
        }
    }

    private func handle(_ frame: Frame) {
        recordReceivedFrame(frame.type)
        switch (state_, frame.type) {
        case (.awaitingHello, .hello), (.sendingHelloAck, .hello):
            Log.info("Conn[\(sessionID)]: received HELLO bytes=\(frame.payload.count)")
            guard frame.payload.count == 32 else {
                Log.error("Conn: HELLO payload must be 32 bytes, got \(frame.payload.count)")
                transition(to: .closed)
                return
            }
            let theirPubKey: Curve25519.KeyAgreement.PublicKey
            do {
                theirPubKey = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: frame.payload)
            } catch {
                Log.error("Conn: invalid HELLO public key: \(error)")
                transition(to: .closed)
                return
            }
            // Send our HELLO_ACK with the
            // server's public key.
            let ourPubKey = self.keyPair.publicKey.rawRepresentation
            let helloAck = FrameEncoder.encode(.helloAck, payload: ourPubKey)
            self.send(helloAck)
            Log.info("Conn[\(sessionID)]: sent HELLO_ACK publicKeyBytes=\(ourPubKey.count)")

            // Derive the channel key (server side).
            let key = ChannelCipher.deriveKey(myPrivate: keyPair, theirPublic: theirPubKey)
            awaitingPinKey = key
            let counter = NonceCounter()
            nonceCounter = counter

            let route = isLocalLoopback() ? "adb-reverse" : "network"
            let pin = generatePin()
            Log.info("Conn[\(sessionID)]: PIN generated for route=\(route) and displayed; value redacted")
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
                let pinChar = pin[pin.index(pin.startIndex, offsetBy: count)]
                let expectedDigit = UInt8(pinChar.wholeNumberValue ?? 0)
                if receivedDigit == expectedDigit {
                    let nextCount = count + 1
                    if nextCount >= pin.utf8.count {
                        // All digits matched. Send
                        // PAIR_OK (encrypted) and
                        // transition to ready.
                        let okPayload = Data([0x01]) // 1 = ok
                        let enc = ChannelCipher.seal(okPayload, key: key)
                        send(FrameEncoder.encode(.pairOk, payload: enc))
                        Log.info("Conn[\(sessionID)]: PIN accepted")
                        transition(to: .ready(channelKey: key, nonceCounter: counter))
                    } else {
                        transition(to: .awaitingPin(generatedPin: pin, digitsReceived: nextCount))
                    }
                } else {
                    Log.warn("Conn[\(sessionID)]: PIN rejected; digit mismatch index=\(count+1); values redacted")
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
                recordInputDecode(type: "mouseMove", accepted: true)
                EventInjector.shared.moveBy(dx: payload.0, dy: payload.1)
            } else {
                recordInputDecode(type: "mouseMove", accepted: false)
            }
        case (.ready(let key, let counter), .mouseAbsMove):
            if let payload = decodeMouseAbsMove(frame.payload, key: key, counter: counter) {
                recordInputDecode(type: "mouseAbsMove", accepted: true)
                EventInjector.shared.moveTo(normX: payload.0, normY: payload.1)
            } else {
                recordInputDecode(type: "mouseAbsMove", accepted: false)
            }
        case (.ready(let key, let counter), .mouseButton):
            if let payload = decodeMouseButton(frame.payload, key: key, counter: counter) {
                recordInputDecode(type: "mouseButton", accepted: true)
                EventInjector.shared.click(button: payload.0, state: payload.1)
            } else {
                recordInputDecode(type: "mouseButton", accepted: false)
            }
        case (.ready(let key, let counter), .scroll):
            if let payload = decodeScroll(frame.payload, key: key, counter: counter) {
                recordInputDecode(type: "scroll", accepted: true)
                EventInjector.shared.scroll(dx: payload.0, dy: payload.1)
            } else {
                recordInputDecode(type: "scroll", accepted: false)
            }
        case (.ready(let key, let counter), .key):
            if let payload = decodeKey(frame.payload, key: key, counter: counter) {
                recordInputDecode(type: "key", accepted: true)
                EventInjector.shared.key(action: payload.0, hidUsage: payload.1, modifiers: payload.2)
            }
        case (.ready(let key, let counter), .pinch):
            if let payload = decodePinch(frame.payload, key: key, counter: counter) {
                recordInputDecode(type: "pinch", accepted: true)
                EventInjector.shared.pinch(factor: payload)
            } else {
                recordInputDecode(type: "pinch", accepted: false)
            }
        case (.ready(let key, let counter), .media):
            if let keyCode = decodeMedia(frame.payload, key: key, counter: counter) {
                recordInputDecode(type: "media", accepted: true)
                if let media = EventInjector.MediaKey(rawValue: Int(keyCode)) {
                    EventInjector.shared.media(media)
                }
            } else {
                recordInputDecode(type: "media", accepted: false)
            }
        case (.ready(let key, _), .screenRequest):
            guard let plain = try? ChannelCipher.open(frame.payload, key: key),
                  let command = plain.first else {
                Log.warn("Conn[\(sessionID)]: SCREEN_REQUEST authentication or payload validation failed bytes=\(frame.payload.count)")
                return
            }
            switch command {
            case 0x01:
                Log.info("Conn[\(sessionID)]: screen capture START requested")
                MacNotificationManager.shared.notify(
                    id: "nexus_screen",
                    title: "📺 Elysium Nexus — Pantalla en vivo",
                    subtitle: "Transmisión iniciada",
                    body: "Transmitiendo pantalla a Android en tiempo real.",
                    category: "NEXUS_REMOTE_SESSION"
                )
                let result = ScreenCaptureService.shared.start(send: { [weak self] jpegPayload in
                    guard let self else { return }
                    self.recordScreenFrameSent(bytes: jpegPayload.count)
                    let encryptedPayload = ChannelCipher.seal(jpegPayload, key: key)
                    self.send(FrameEncoder.encode(.screenFrame, payload: encryptedPayload))
                }, status: { [weak self] status in
                    self?.sendScreenStatus(status, key: key)
                })
                sendScreenStatus(result, key: key)
            case 0x00:
                Log.info("Conn[\(sessionID)]: screen capture STOP requested")
                MacNotificationManager.shared.notify(
                    id: "nexus_screen",
                    title: "⏸ Elysium Nexus — Pantalla pausada",
                    body: "La transmisión de pantalla hacia Android se ha detenido."
                )
                ScreenCaptureService.shared.stop()
                sendScreenStatus(.stopped, key: key)
            case 0x02 where plain.count >= 2:
                let quality = CGFloat(plain[1]) / 100.0
                Log.info("Conn[\(sessionID)]: screen quality requested percent=\(plain[1])")
                ScreenCaptureService.shared.setQuality(quality)
            default:
                Log.warn("Conn[\(sessionID)]: unsupported SCREEN_REQUEST command=\(command)")
            }
        case (.ready, .heartbeat), (.ready, .goodbye):
            break // No-op
        default:
            Log.warn("Conn: unexpected frame \(frame.type) in state \(state_)")
        }
    }

    private func sendScreenStatus(_ status: ScreenCaptureStatus, key: SymmetricKey) {
        let encrypted = ChannelCipher.seal(Data([status.rawValue]), key: key)
        send(FrameEncoder.encode(.screenStatus, payload: encrypted))
        Log.info("Conn[\(sessionID)]: screen status sent status=\(status)")
    }

    private func recordReceivedFrame(_ type: FrameType) {
        diagnosticsLock.lock()
        let name = String(describing: type)
        receivedFramesByType[name, default: 0] += 1
        let now = Date()
        let shouldLog = now.timeIntervalSince(diagnosticsLastLogAt) >= 5
        let snapshot = shouldLog ? receivedFramesByType : [:]
        if shouldLog { diagnosticsLastLogAt = now }
        diagnosticsLock.unlock()
        if shouldLog {
            let summary = snapshot.keys.sorted().map { "\($0)=\(snapshot[$0] ?? 0)" }.joined(separator: ",")
            Log.info("Conn[\(sessionID)]: RX_METRICS frameCounts={\(summary)} last=\(name)")
        }
    }

    private func recordInputDecode(type: String, accepted: Bool) {
        diagnosticsLock.lock()
        if accepted { decodedInputEvents += 1 } else { rejectedInputEvents += 1 }
        let decoded = decodedInputEvents
        let rejected = rejectedInputEvents
        diagnosticsLock.unlock()
        if decoded + rejected == 1 || (decoded + rejected) % 120 == 0 {
            Log.info("Conn[\(sessionID)]: INPUT_METRICS decoded=\(decoded) rejected=\(rejected) lastType=\(type) lastResult=\(accepted ? "accepted" : "rejected")")
        }
    }

    private func recordScreenFrameSent(bytes: Int) {
        diagnosticsLock.lock()
        screenFramesSent += 1
        screenBytesSent += UInt64(max(0, bytes))
        let count = screenFramesSent
        let totalBytes = screenBytesSent
        diagnosticsLock.unlock()
        if count == 1 || count % 120 == 0 {
            Log.info("Conn[\(sessionID)]: SCREEN_TX_METRICS frames=\(count) lastJpegBytes=\(bytes) totalJpegBytes=\(totalBytes)")
        }
    }

    // MARK: - Decoders (post-handshake frames are
    //         ChaCha20-Poly1305 sealed; we open
    //         them before dispatching).

    private func decodeMouseMove(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (Float, Float)? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else {
            return nil
        }
        guard plain.count == 8 else {
            return nil
        }
        let rawDx = plain.subdata(in: 0..<4).withUnsafeBytes { $0.load(as: UInt32.self) }
        let rawDy = plain.subdata(in: 4..<8).withUnsafeBytes { $0.load(as: UInt32.self) }
        let dx = Float32(bitPattern: UInt32(bigEndian: rawDx))
        let dy = Float32(bitPattern: UInt32(bigEndian: rawDy))
        // Skip MainActor state update for mouse
        // moves — they fire 60+ times/second and
        // the dispatch overhead adds latency.
        return (dx, dy)
    }
    private func decodeMouseAbsMove(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (Float, Float)? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 8 else { return nil }
        let rawX = plain.subdata(in: 0..<4).withUnsafeBytes { $0.load(as: UInt32.self) }
        let rawY = plain.subdata(in: 4..<8).withUnsafeBytes { $0.load(as: UInt32.self) }
        let normX = Float32(bitPattern: UInt32(bigEndian: rawX))
        let normY = Float32(bitPattern: UInt32(bigEndian: rawY))
        return (normX, normY)
    }
    private func decodeMouseButton(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (MouseButton, ButtonState)? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 2 else { return nil }
        let b = MouseButton(rawValue: plain[0]) ?? .left
        let s = ButtonState(rawValue: plain[1]) ?? .up
        Task { @MainActor in
            self.state.lastEventDescription = "Click \(b) \(s)"
            self.state.lastEventAt = Date()
            self.state.eventCount += 1
        }
        return (b, s)
    }
    private func decodeScroll(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (Float, Float)? {
        return decodeMouseMove(payload, key: key, counter: counter)
            .map { ($0.0, $0.1) }
    }
    private func decodeKey(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> (KeyAction, UInt32, Modifiers)? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else {
            Log.warn("decodeKey: decryption failed")
            return nil
        }
        guard plain.count == 9 else {
            Log.warn("decodeKey: expected 9 bytes, got \(plain.count)")
            return nil
        }
        let action = KeyAction(rawValue: plain[0]) ?? .down
        // Android sends big-endian UInt32 for HID
        // usage and modifiers — convert from big-
        // endian to native before interpreting.
        let rawUsage = plain.subdata(in: 1..<5).withUnsafeBytes { $0.load(as: UInt32.self) }
        let usage = UInt32(bigEndian: rawUsage)
        let rawMods = plain.subdata(in: 5..<9).withUnsafeBytes { $0.load(as: UInt32.self) }
        let mods = Modifiers(rawValue: UInt32(bigEndian: rawMods))
        Task { @MainActor in
            self.state.lastEventDescription = "Key \(action) usage=0x\(String(usage, radix: 16))"
            self.state.lastEventAt = Date()
            self.state.eventCount += 1
        }
        return (action, usage, mods)
    }
    private func decodePinch(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> Float? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else {
            Log.warn("decodePinch: decryption failed")
            return nil
        }
        guard plain.count == 4 else {
            Log.warn("decodePinch: expected 4 bytes, got \(plain.count)")
            return nil
        }
        // Android sends big-endian Float32.
        let raw = plain.withUnsafeBytes { $0.load(as: UInt32.self) }
        return Float32(bitPattern: UInt32(bigEndian: raw))
    }
    private func decodeMedia(_ payload: Data, key: SymmetricKey, counter: NonceCounter) -> UInt32? {
        guard let plain = try? ChannelCipher.open(payload, key: key) else { return nil }
        guard plain.count == 1 else { return nil }
        return UInt32(plain[0])
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

    private func isLocalLoopback() -> Bool {
        if case .hostPort(let host, _) = connection.endpoint {
            let hostStr = "\(host)"
            return hostStr.contains("127.0.0.1") || hostStr.contains("localhost") || hostStr.contains("::1")
        }
        return true
    }

}
