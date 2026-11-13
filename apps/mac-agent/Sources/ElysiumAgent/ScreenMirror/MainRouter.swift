import SwiftUI
import MetalKit
import CoreVideo
import CoreMedia
import Combine
import ApplicationServices
import AppKit

enum VideoQuality: String, CaseIterable, Identifiable {
    case hd_1080p = "1080p (FHD)"
    case qhd_2k = "2K (QHD)"
    case uhd_4k = "4K (UHD)"
    
    var id: String { self.rawValue }
    
    var shortLabel: String {
        switch self {
        case .hd_1080p: return "1080p"
        case .qhd_2k: return "2K"
        case .uhd_4k: return "4K"
        }
    }
    
    var size: CGSize {
        switch self {
        case .hd_1080p: return CGSize(width: 1920, height: 1080)
        case .qhd_2k: return CGSize(width: 2560, height: 1440)
        case .uhd_4k: return CGSize(width: 3840, height: 2160)
        }
    }
    
    /// Bitrate óptimo por resolución (bps)
    var targetBitrate: Int {
        switch self {
        case .hd_1080p: return 8_000_000   // 8 Mbps
        case .qhd_2k:   return 16_000_000  // 16 Mbps
        case .uhd_4k:   return 30_000_000  // 30 Mbps
        }
    }
}

/// Orquestador Supremo de la App Mac.
/// Levanta e inyecta las tuberías aisladas: 
/// ScreenCapture -> Compositor -> AssetWriter
/// Bonjour -> Signaling -> WebRTC (ICE + SDP) -> Metal PIP
@MainActor
class RuntimeOrchestrator: ObservableObject {
    @Published var isRecording = false
    @Published var recordingSeconds = 0
    @Published var isBooted = false
    @Published var isBooting = false
    @Published var selectedQuality: VideoQuality = .qhd_2k
    @Published var fps: Int = 0
    @Published var showPermissionAlert = false
    @Published var recordingError: String? = nil
    @Published var isNexusSharing = false
    @Published var isMacScreenSharing = false
    @Published var nexusShareStatus = "Desactivado"
    @Published var nexusPairingCode = ""
    @Published var nexusEndpoint = ""
    @Published var nexusUsbStatus = "USB-C: conecta con depuración USB autorizada"
    @Published var nexusAccessibilityStatus = ""
    
    // ─── Estado de conexión Android ───
    @Published var androidConnectionState: String = "IDLE"
    @Published var androidPosture: String = "—"
    @Published var androidOrientation: String = "—"
    @Published var androidDimensions: String = "—"
    
    private var recordingTimer: Timer?
    private var telemetryTimer: Timer?
    private var lastFrameCount = 0
    
    let screenCapture = ScreenCaptureManager()
    let audioMixer = AudioMixer()
    let assetRecorder = AVAssetRecorder()
    let rtcController = RTCController()
    let bonjourBrowser = BonjourBrowser()
    let signalingClient = SignalingClient()
    let sensory = SensoryFeedbackManager.shared
    let layoutEngine = LayoutMorphEngine()
    let hotKeyObserver = HotKeyObserver()
    private let cloudSignaling = NexusCloudSignalingService()
    private var cloudSession: NexusMacCloudSession?
    private var cloudConnection: NexusMacCloudSignalConnection?
    
    public var metalCompositor: MetalCompositor?
    private var cancellables = Set<AnyCancellable>()

    init() {
        setupPipeline()
        setupHotKeys()
    }

    private func setupPipeline() {
        self.metalCompositor = MetalCompositor()
        
        // ── PATH COMPOSITOR UNIFICADO (FIX 0-BYTES + PIP ANDROID) ──
        metalCompositor?.onFrameComposited = { [weak self] finalPixelBuffer, pts in
            guard let self = self else { return }
            self.assetRecorder.appendVideoFrame(pixelBuffer: finalPixelBuffer, at: pts)
        }
        
        // ScreenCapture envía el frame crudo + el PTS nativo (evita desincronización de audio/video)
        screenCapture.directPixelBufferHandler = { [weak self] pixelBuffer, pts in
            guard let self = self else { return }
            
            // Inyectar posición PIP dinámica desde el LayoutMorphEngine (thread-safe via os_unfair_lock)
            self.metalCompositor?.pipLayoutRect = self.layoutEngine.currentLayout
            
            // Inyectar anotaciones pre-cacheadas (snapshot síncrono, sin async Task)
            self.metalCompositor?.activeAnnotations = EphemeralEngine.cachedCompositorStrokes
            
            self.metalCompositor?.updateMacScreen(pixelBuffer: pixelBuffer, pts: pts)
        }
        screenCapture.nexusPixelBufferHandler = { [weak self] pixelBuffer, pts in
            guard let self, self.isNexusSharing else { return }
            self.rtcController.captureMacFrame(pixelBuffer, presentationTime: pts)
        }
        
        // 2. Vincular el audio del sistema capturado hacia el Mixer Master
        screenCapture.audioSampleBufferHandler = { [weak self] sampleBuffer in
            self?.audioMixer.injectSystemAudio(sampleBuffer: sampleBuffer)
        }
        
        // 2.1 RECIBIR el audio mezclado (Mixer -> Recorder)
        audioMixer.mixedAudioBufferHandler = { [weak self] mixedBuffer in
            self?.assetRecorder.appendAudioFrame(sampleBuffer: mixedBuffer)
        }
        
        // 3. Vincular WebRTC (Android Track) hacia Metal
        rtcController.videoSink.onFrameReceived = { [weak self] pixelBuffer in
            self?.metalCompositor?.updateAndroidStream(pixelBuffer: pixelBuffer)
            DispatchQueue.main.async {
                self?.rtcController.latestPixelBuffer = pixelBuffer
            }
        }
        
        // 4. ── ICE candidates: RTC ↔ Signaling bridge ──
        rtcController.onLocalIceCandidate = { [weak self] candidate in
            guard let self else { return }
            if let cloudConnection = self.cloudConnection {
                let payload = NexusRtcSignalPayload(
                    candidate: candidate.sdp,
                    sdpMid: candidate.sdpMid,
                    sdpMLineIndex: Int(candidate.sdpMLineIndex)
                )
                guard let data = try? JSONEncoder().encode(payload) else { return }
                Task { try? await cloudConnection.send(kind: .ice, body: data) }
            } else {
                self.signalingClient.sendIceCandidate(
                    candidate: candidate.sdp,
                    sdpMid: candidate.sdpMid,
                    sdpMLineIndex: candidate.sdpMLineIndex
                )
            }
        }
        
        signalingClient.onRemoteIceCandidateReceived = { [weak self] candidate, sdpMid, sdpMLineIndex in
            self?.rtcController.addRemoteIceCandidate(
                candidate: candidate,
                sdpMid: sdpMid,
                sdpMLineIndex: sdpMLineIndex
            )
        }
        
        // 5. ── DataChannel: metadatos del Android (fold, orientación) ──
        rtcController.onDataChannelMessage = { [weak self] message in
            self?.handleAndroidDataChannelMessage(message)
        }
        rtcController.onDataChannelBinaryMessage = { [weak self] frame in
            self?.handleNexusControlFrame(frame)
        }
        
        // 6. ── Observar estado de conexión P2P ──
        rtcController.$isP2PConnected.sink { [weak self] connected in
            Task { @MainActor in
                self?.androidConnectionState = connected ? "CONNECTED" : "WAITING"
                if connected {
                    self?.sensory.triggerHapticAlignment()
                }
            }
        }.store(in: &cancellables)
    }
    
    /// Procesa mensajes del DataChannel del Android
    private func handleAndroidDataChannelMessage(_ message: String) {
        let parts = message.components(separatedBy: ":")
        guard let command = parts.first else { return }
        
        switch command {
        case "POSTURE":
            if parts.count >= 2 {
                let posture = parts[1]
                androidPosture = posture
                
                switch posture {
                case "FOLDED":
                    layoutEngine.transition(to: .foldedPortrait)
                    sensory.triggerHapticLevelChange()
                case "UNFOLDED":
                    layoutEngine.transition(to: .unfoldedTablet)
                    sensory.triggerHapticLevelChange()
                case "HALF_OPENED":
                    layoutEngine.transition(to: .unfoldedTablet)
                    sensory.triggerHapticLevelChange()
                default:
                    break
                }
            }
            
        case "ORIENTATION":
            if parts.count >= 4 {
                androidOrientation = parts[1] // PORTRAIT or LANDSCAPE
                androidDimensions = "\(parts[2])×\(parts[3])"
                
                if parts[1] == "LANDSCAPE" {
                    layoutEngine.transition(to: .landscapeGaming)
                }
            }
            
        default:
            print("DataChannel: Comando no reconocido: \(command)")
        }
    }
    
    /// Conecta el HotKeyObserver al LayoutMorphEngine y MetalCompositor
    private func setupHotKeys() {
        hotKeyObserver.$currentLayout.sink { [weak self] layout in
            Task { @MainActor in
                guard let self = self else { return }
                switch layout {
                case .hidden:
                    self.metalCompositor?.isAndroidOverlayEnabled = false
                case .cornerPIP:
                    self.metalCompositor?.isAndroidOverlayEnabled = true
                    self.layoutEngine.transition(to: .foldedPortrait)
                case .splitScreen:
                    self.metalCompositor?.isAndroidOverlayEnabled = true
                    self.layoutEngine.transition(to: .unfoldedTablet)
                }
            }
        }.store(in: &cancellables)
    }
    
    func bootEngine() {
        guard !isBooted, !isBooting else { return }
        isBooting = true
        screenCapture.checkPermissions()
        let canCaptureMacScreen = screenCapture.hasFullRecordingPermission()

        guard canCaptureMacScreen || isNexusSharing else {
            isBooting = false
            self.showPermissionAlert = true
            return
        }

        Task { @MainActor in
            do {
                if canCaptureMacScreen {
                    try await screenCapture.startCapture(quality: selectedQuality.size)
                }
                if !isNexusSharing {
                    bonjourBrowser.startDiscovery()
                    androidConnectionState = "SCANNING"
                } else {
                    androidConnectionState = "WAITING_FOR_ANDROID"
                }
                
                // Telemetría de FPS Master (basada en frames REALES de SCKit)
                telemetryTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
                    Task { @MainActor in
                        guard let self = self else { return }
                        let currentFrames = self.screenCapture.receivedFrameCount
                        self.fps = currentFrames - self.lastFrameCount
                        self.lastFrameCount = currentFrames
                        
                        // Watchdog: si llevamos 3+ segundos sin frames, alertar
                        if self.isBooted && self.fps == 0 && self.recordingSeconds > 3 {
                            self.recordingError = "⚠ Sin señal de video. Verifica permisos de Grabación de Pantalla y reinicia la app."
                        }
                    }
                }
                
                // Inicializamos WebRTC antes del Handshake
                self.rtcController.createPeerConnection(
                    iceServers: self.cloudSession?.iceServers ?? [],
                    sendsMacScreen: self.isNexusSharing && canCaptureMacScreen,
                    relayOnly: self.isNexusSharing
                )
                
                // [Handshake Automático]: Bonjour descubre Android → conecta signaling
                if !isNexusSharing {
                    bonjourBrowser.$discoveredEndpoint.sink { [weak self] endpoint in
                        guard let self = self, let endpoint = endpoint else { return }
                        self.androidConnectionState = "DISCOVERED"
                        self.signalingClient.connect(to: endpoint)
                    }.store(in: &cancellables)
                }
                
                // [SDP Swap]: Android envía Offer → Mac responde con Answer
                if !isNexusSharing {
                    signalingClient.onSDPOfferReceived = { [weak self] offerSdp in
                        Task { @MainActor in self?.androidConnectionState = "NEGOTIATING" }
                        self?.rtcController.handleRemoteOffer(sdp: offerSdp) { answerSdp in
                            self?.signalingClient.sendAnswer(sdp: answerSdp)
                        }
                    }
                } else {
                    if let session = cloudSession {
                        Task { try? await openCloudSignalChannel(for: session) }
                    }
                }
                
                await MainActor.run {
                    self.isBooted = true
                    self.isBooting = false
                    self.sensory.playDigitalConfirmSound()
                }
            } catch {
                await MainActor.run {
                    print("FATAL ERROR al arrancar el motor de captura: \(error)")
                    self.isBooting = false
                    self.isBooted = false
                    self.recordingError = error.localizedDescription
                }
            }
        }
    }
    
    func toggleRecording() {
        if isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    func startRecording() {
        guard screenCapture.hasFullRecordingPermission() else {
            self.showPermissionAlert = true
            return
        }

        let fileManager = FileManager.default
        let downloadsURL = fileManager.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        let targetDirectory = downloadsURL.appendingPathComponent("screen recorder")
        
        if !fileManager.fileExists(atPath: targetDirectory.path) {
            try? fileManager.createDirectory(at: targetDirectory, withIntermediateDirectories: true, attributes: nil)
        }
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        let timestamp = dateFormatter.string(from: Date())
        let fileName = "Elysium_\(timestamp)_\(selectedQuality.shortLabel).mp4"
        let outputURL = targetDirectory.appendingPathComponent(fileName)
        
        do {
            try assetRecorder.startRecording(outputURL: outputURL, size: selectedQuality.size, bitrate: selectedQuality.targetBitrate)
            isRecording = true
            recordingSeconds = 0
            recordingError = nil
            
            recordingTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
                Task { @MainActor in
                    self?.recordingSeconds += 1
                }
            }
            sensory.playDigitalConfirmSound()
            print("Recording started: \(outputURL.lastPathComponent) at \(selectedQuality.shortLabel)")
        } catch {
            let techError = assetRecorder.lastErrorCode ?? "UNKNOWN"
            recordingError = "\(error.localizedDescription) (Código: \(techError))"
            print("Recording failed: \(error)")
        }
    }
    
    func stopRecording() {
        recordingTimer?.invalidate()
        recordingTimer = nil
        assetRecorder.stopRecording { [weak self] url in
            Task { @MainActor in
                self?.isRecording = false
                if let url = url {
                    let fileSize = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0
                    let sizeMB = Double(fileSize) / 1_048_576.0
                    print("✅ Grabación guardada: \(url.lastPathComponent) (\(String(format: "%.1f", sizeMB)) MB)")
                }
            }
        }
        sensory.playMechanicalClick()
    }

    /// Start a consent-gated Nexus screen share. ScreenCaptureKit still enforces
    /// macOS Screen Recording permission; the pairing code authorizes each phone.
    func startNexusSharing() {
        guard !isNexusSharing else { return }
        isNexusSharing = true
        nexusShareStatus = "Creando invitación privada de Internet…"
        nexusEndpoint = ""
        androidConnectionState = "PREPARING"
        Task { @MainActor in
            do {
                let session = try await cloudSignaling.createInvitation()
                cloudSession = session
                guard session.relayAvailable,
                      session.iceServers.contains(where: { server in
                          server.urls.contains(where: { $0.hasPrefix("turn:") || $0.hasPrefix("turns:") })
                      }) else {
                    throw NexusCloudSignalingError.relayUnavailable
                }
                signalingClient.disconnect()
                bonjourBrowser.stopDiscovery()
                rtcController.dispose()
                rtcController.createPeerConnection(
                    iceServers: session.iceServers,
                    sendsMacScreen: true,
                    relayOnly: true
                )
                try await openCloudSignalChannel(for: session)

                // Show the one-time secret only after the private channel is ready.
                nexusPairingCode = session.pairingCode
                nexusEndpoint = "Internet · relay TURN cifrado; IP entre pares oculta"
                nexusShareStatus = "Esperando Android; invitación temporal lista"
                androidConnectionState = "WAITING_FOR_ANDROID"
                isBooted = true
            } catch {
                let failedSession = cloudSession
                cloudSession = nil
                cloudConnection = nil
                if let failedSession { await cloudSignaling.closeSession(failedSession) }
                nexusShareStatus = error.localizedDescription
                nexusPairingCode = ""
                androidConnectionState = "CLOUD_ERROR"
                isNexusSharing = false
                rtcController.dispose()
            }
        }
    }

    func startMacScreenSharing() {
        guard isNexusSharing else {
            nexusShareStatus = "Primero crea una invitación para Android."
            return
        }
        screenCapture.checkPermissions()
        guard screenCapture.hasFullRecordingPermission() else {
            // This is reached only after the user explicitly taps Share Mac Screen.
            screenCapture.requestPermissions()
            nexusShareStatus = "Permite Grabación de pantalla en Ajustes del Sistema y vuelve a tocar Compartir Mac."
            sendNexusScreenStatus(.permissionRequired)
            return
        }
        Task { @MainActor in
            do {
                try await screenCapture.startCapture(quality: selectedQuality.size)
                isMacScreenSharing = true
                nexusShareStatus = "Mac compartiendo; esperando Android o enviando vídeo"
                sendNexusScreenStatus(.streaming)
            } catch {
                nexusShareStatus = "No se pudo iniciar la captura de Mac: \(error.localizedDescription)"
                sendNexusScreenStatus(.captureFailed)
            }
        }
    }

    func stopMacScreenSharing() {
        isMacScreenSharing = false
        if !isRecording {
            Task { try? await screenCapture.stopCapture() }
        }
        if isNexusSharing {
            nexusShareStatus = "Conexión global activa; pantalla de Mac detenida"
            sendNexusScreenStatus(.stopped)
        }
    }

    func stopNexusSharing() {
        let oldSession = cloudSession
        let oldConnection = cloudConnection
        cloudSession = nil
        cloudConnection = nil
        if let oldConnection {
            Task {
                try? await oldConnection.send(kind: .close, body: Data())
                await oldConnection.close()
                if let oldSession { await cloudSignaling.closeSession(oldSession) }
            }
        } else if let oldSession {
            Task { await cloudSignaling.closeSession(oldSession) }
        }
        isNexusSharing = false
        isMacScreenSharing = false
        nexusPairingCode = ""
        nexusEndpoint = ""
        nexusShareStatus = "Desactivado"
        androidConnectionState = "IDLE"
        signalingClient.disconnect()
        bonjourBrowser.stopDiscovery()
        rtcController.dispose()
        if !isRecording {
            Task { try? await screenCapture.stopCapture() }
        }
        isBooted = false
    }

    /// The global path carries typed input inside WebRTC's authenticated
    /// DTLS DataChannel. The rendezvous service never receives input bytes.
    private func handleNexusControlFrame(_ bytes: Data) {
        var buffer = bytes
        guard let frame = FrameParser.read(from: &buffer), buffer.isEmpty else {
            Log.warn("Nexus DataChannel rejected malformed control frame bytes=\(bytes.count)")
            return
        }
        let payload = [UInt8](frame.payload)
        switch frame.type {
        case .mouseMove:
            guard payload.count == 8,
                  let dx = nexusFloat(payload, at: 0), let dy = nexusFloat(payload, at: 4) else { return }
            EventInjector.shared.moveBy(dx: dx, dy: dy)
        case .mouseAbsMove:
            guard payload.count == 8,
                  let x = nexusFloat(payload, at: 0), let y = nexusFloat(payload, at: 4) else { return }
            EventInjector.shared.moveTo(normX: x, normY: y)
        case .mouseButton:
            guard payload.count == 2,
                  let button = MouseButton(rawValue: payload[0]),
                  let state = ButtonState(rawValue: payload[1]) else { return }
            EventInjector.shared.click(button: button, state: state)
        case .scroll:
            guard payload.count == 8,
                  let dx = nexusFloat(payload, at: 0), let dy = nexusFloat(payload, at: 4) else { return }
            EventInjector.shared.scroll(dx: dx, dy: dy)
        case .pinch:
            guard payload.count == 4, let factor = nexusFloat(payload, at: 0) else { return }
            EventInjector.shared.pinch(factor: factor)
        case .key:
            guard payload.count == 9,
                  let action = KeyAction(rawValue: payload[0]),
                  let usage = nexusUInt32(payload, at: 1),
                  let modifierBits = nexusUInt32(payload, at: 5) else { return }
            EventInjector.shared.key(
                action: action,
                hidUsage: usage,
                modifiers: Modifiers(rawValue: modifierBits)
            )
        case .media:
            guard payload.count == 1,
                  let media = EventInjector.MediaKey(rawValue: Int(payload[0])) else { return }
            EventInjector.shared.media(media)
        case .textInput:
            guard payload.count <= 2_048,
                  let text = String(data: frame.payload, encoding: .utf8) else { return }
            EventInjector.shared.typeText(text)
        case .screenRequest:
            guard let command = payload.first else { return }
            if command == 0 {
                stopMacScreenSharing()
                sendNexusScreenStatus(.stopped)
            } else if command == 1 {
                if isMacScreenSharing {
                    sendNexusScreenStatus(.streaming)
                } else {
                    // ScreenCaptureKit starts only after the Mac user enables
                    // the explicit Share Mac Screen control.
                    sendNexusScreenStatus(.stopped)
                    nexusShareStatus = "El Android pidió vídeo; activa Compartir pantalla de Mac para autorizarlo."
                }
            }
        case .goodbye:
            stopNexusSharing()
        case .heartbeat:
            break
        case .hello, .helloAck, .pinDigit, .pairOk, .screenFrame, .screenStatus:
            Log.warn("Nexus DataChannel rejected unexpected frame type=\(frame.type)")
        }
    }

    private func sendNexusScreenStatus(_ status: ScreenCaptureStatus) {
        let frame = FrameEncoder.encode(.screenStatus, payload: Data([status.rawValue]))
        if !rtcController.sendDataChannelMessage(frame) {
            Log.warn("Nexus DataChannel could not send screen status=\(status)")
        }
    }

    private func openCloudSignalChannel(for session: NexusMacCloudSession) async throws {
        guard cloudConnection == nil else { return }
        cloudConnection = try await cloudSignaling.openSignalChannel(
            for: session,
            sender: .mac,
            onSignal: { [weak self] signal in
                await self?.handleCloudSignal(signal)
            }
        )
    }

    private func handleCloudSignal(_ signal: NexusMacVerifiedSignal) async {
        guard isNexusSharing else { return }
        switch signal.kind {
        case .offer:
            guard let payload = try? JSONDecoder().decode(NexusRtcSignalPayload.self, from: signal.body),
                  let sdp = payload.sdp else { return }
            androidConnectionState = "NEGOTIATING"
            rtcController.handleRemoteOffer(sdp: sdp) { [weak self] answerSdp in
                guard let self,
                      let connection = self.cloudConnection,
                      let body = try? JSONEncoder().encode(NexusRtcSignalPayload(sdp: answerSdp)) else { return }
                Task { try? await connection.send(kind: .answer, body: body) }
            }
        case .ice:
            guard let payload = try? JSONDecoder().decode(NexusRtcSignalPayload.self, from: signal.body),
                  let candidate = payload.candidate else { return }
            rtcController.addRemoteIceCandidate(
                candidate: candidate,
                sdpMid: payload.sdpMid,
                sdpMLineIndex: payload.sdpMLineIndex ?? 0
            )
        case .answer:
            Log.warn("Nexus cloud received an unexpected answer from Android")
        case .close:
            stopNexusSharing()
        }
    }

    func copyNexusEndpoint() {
        guard !nexusEndpoint.isEmpty else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(nexusEndpoint, forType: .string)
        sensory.playMechanicalClick()
    }

    func copyNexusPairingCode() {
        guard !nexusPairingCode.isEmpty else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(nexusPairingCode, forType: .string)
        sensory.playMechanicalClick()
    }

    func openRecordingFolder() {
        let fileManager = FileManager.default
        let downloadsURL = fileManager.urls(for: .downloadsDirectory, in: .userDomainMask).first!
        let targetDirectory = downloadsURL.appendingPathComponent("screen recorder")
        
        if fileManager.fileExists(atPath: targetDirectory.path) {
            NSWorkspace.shared.selectFile(nil, inFileViewerRootedAtPath: targetDirectory.path)
        } else {
            NSWorkspace.shared.open(downloadsURL)
        }
    }
    
    /// Apagado completo de todos los subsistemas.
    /// Llamado al cerrar la app o resetear el motor.
    func shutdownEngine() {
        if isNexusSharing {
            stopNexusSharing()
        } else {
            let oldSession = cloudSession
            cloudSession = nil
            cloudConnection = nil
            if let oldSession { Task { await cloudSignaling.closeSession(oldSession) } }
        }
        if isRecording { stopRecording() }
        
        telemetryTimer?.invalidate()
        telemetryTimer = nil
        
        // P2P teardown (orden: RTC → Signaling → Discovery)
        rtcController.dispose()
        signalingClient.disconnect()
        bonjourBrowser.stopDiscovery()
        
        // Capture teardown
        Task { try? await screenCapture.stopCapture() }
        
        cancellables.removeAll()
        
        isBooted = false
        androidConnectionState = "IDLE"
        print("Engine shutdown completo.")
    }
}

private func nexusUInt32(_ bytes: [UInt8], at offset: Int) -> UInt32? {
    guard offset >= 0, offset + 4 <= bytes.count else { return nil }
    return (UInt32(bytes[offset]) << 24) |
        (UInt32(bytes[offset + 1]) << 16) |
        (UInt32(bytes[offset + 2]) << 8) |
        UInt32(bytes[offset + 3])
}

private func nexusFloat(_ bytes: [UInt8], at offset: Int) -> Float? {
    nexusUInt32(bytes, at: offset).map { Float(bitPattern: $0) }
}

// Representable para poder embutir la vista 100% Metal dentro de SwiftUI
struct CompositorCanvasView: NSViewRepresentable {
    let mtkView: MTKView

    func makeNSView(context: Context) -> MTKView {
        return mtkView
    }

    func updateNSView(_ nsView: MTKView, context: Context) {}
}
