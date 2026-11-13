import Foundation
import WebRTC

/// Gestor completo del PeerConnection WebRTC para la Mac (lado receptor).
/// Maneja: SDP negociación, ICE candidate exchange, DataChannel para metadatos, video track.
class RTCController: NSObject, ObservableObject {
    private var peerConnectionFactory: RTCPeerConnectionFactory
    private var peerConnection: RTCPeerConnection?
    let videoSink = RTCVideoSink()
    
    // ICE candidate callback — se conecta al SignalingClient
    var onLocalIceCandidate: ((RTCIceCandidate) -> Void)?
    
    // Estado observable de la conexión P2P
    @Published var iceConnectionState: RTCIceConnectionState = .new
    @Published var isP2PConnected = false
    @Published var currentVideoTrack: RTCVideoTrack?
    @Published var latestPixelBuffer: CVPixelBuffer?
    
    // DataChannel callback para metadatos del Android (fold state, orientación)
    var onDataChannelMessage: ((String) -> Void)?
    var onDataChannelBinaryMessage: ((Data) -> Void)?
    private var controlDataChannel: RTCDataChannel?
    
    // Configuración base P2P (Sin STUN en v1 para latencia pura LAN)
    private var rtcConfig: RTCConfiguration = {
        let config = RTCConfiguration()
        config.sdpSemantics = .unifiedPlan
        // Bloqueamos priorización externa, todo candidato será interno (LAN Host)
        config.iceServers = []
        return config
    }()
    private var localScreenSource: RTCVideoSource?
    private var localScreenCapturer: RTCVideoCapturer?
    private var hasRemoteDescription = false
    private var pendingRemoteIceCandidates: [RTCIceCandidate] = []
    private let remoteIceLock = NSLock()
    
    override init() {
        RTCInitializeSSL()
        let videoEncoderFactory = RTCDefaultVideoEncoderFactory()
        let videoDecoderFactory = RTCDefaultVideoDecoderFactory()
        
        // El factor importante: el SDK intentará delegar esto a VideoToolbox si puede.
        self.peerConnectionFactory = RTCPeerConnectionFactory(
            encoderFactory: videoEncoderFactory,
            decoderFactory: videoDecoderFactory
        )
        super.init()
    }
    
    func createPeerConnection(
        iceServers: [NexusCloudIceServer] = [],
        sendsMacScreen: Bool = false,
        relayOnly: Bool = false
    ) {
        remoteIceLock.lock()
        hasRemoteDescription = false
        pendingRemoteIceCandidates.removeAll(keepingCapacity: false)
        remoteIceLock.unlock()
        rtcConfig.iceServers = iceServers.map { server in
            RTCIceServer(
                urlStrings: server.urls,
                username: server.username,
                credential: server.credential
            )
        }
        rtcConfig.iceTransportPolicy = relayOnly ? .relay : .all
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        peerConnection = peerConnectionFactory.peerConnection(with: rtcConfig, constraints: constraints, delegate: self)
        if sendsMacScreen, let peerConnection {
            let source = peerConnectionFactory.videoSource(forScreenCast: true)
            source.adaptOutputFormat(toWidth: 1920, height: 1080, fps: 30)
            let capturer = RTCVideoCapturer(delegate: source)
            let track = peerConnectionFactory.videoTrack(with: source, trackId: "NEXUS_MAC_SCREEN")
            guard peerConnection.add(track, streamIds: ["NEXUS_MAC_SCREEN"]) != nil else {
                print("WebRTC could not attach the Mac screen track")
                return
            }
            localScreenSource = source
            localScreenCapturer = capturer
            print("WebRTC Mac screen track ready; frames remain gated by ScreenCaptureKit permission")
        }
        print("WebRTC PeerConnection creado. Preparado para recibir Offer/SDP.")
    }

    /// Feeds user-authorized ScreenCaptureKit frames to the negotiated Mac→Android track.
    func captureMacFrame(_ pixelBuffer: CVPixelBuffer, presentationTime: CMTime) {
        guard let source = localScreenSource, let capturer = localScreenCapturer else { return }
        let frameBuffer = RTCCVPixelBuffer(pixelBuffer: pixelBuffer)
        let seconds = CMTimeGetSeconds(presentationTime)
        let timestampNs = seconds.isFinite && seconds > 0
            ? Int64(seconds * 1_000_000_000)
            : Int64(ProcessInfo.processInfo.systemUptime * 1_000_000_000)
        let frame = RTCVideoFrame(buffer: frameBuffer, rotation: ._0, timeStampNs: timestampNs)
        source.capturer(capturer, didCapture: frame)
    }
    
    /// Recibe el SDP Offer del Android y genera la Answer
    func handleRemoteOffer(sdp: String, completion: @escaping (String) -> Void) {
        // Preserve the negotiated SDP. Renaming codecs in SDP can produce
        // invalid payload mappings and needlessly excludes Android devices.
        let sessionDescription = RTCSessionDescription(type: .offer, sdp: sdp)
        
        peerConnection?.setRemoteDescription(sessionDescription) { [weak self] error in
            guard error == nil else {
                print("Error seteando Remote SDP: \(error!)")
                return
            }
            guard let self else { return }
            self.remoteIceLock.lock()
            self.hasRemoteDescription = true
            let pending = self.pendingRemoteIceCandidates
            self.pendingRemoteIceCandidates.removeAll(keepingCapacity: false)
            self.remoteIceLock.unlock()
            pending.forEach { self.applyRemoteIceCandidate($0) }
            self.createAnswer(completion: completion)
        }
    }
    
    private func createAnswer(completion: @escaping (String) -> Void) {
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        peerConnection?.answer(for: constraints) { [weak self] sdp, error in
            guard let sdp = sdp, error == nil else { return }
            
            self?.peerConnection?.setLocalDescription(sdp) { error in
                if error == nil {
                    completion(sdp.sdp)
                } else {
                    print("Error seteando Local SDP: \(error!)")
                }
            }
        }
    }
    
    /// Aplica un ICE candidate recibido del Android via signaling
    func addRemoteIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        let iceCandidate = RTCIceCandidate(
            sdp: candidate,
            sdpMLineIndex: Int32(sdpMLineIndex),
            sdpMid: sdpMid
        )
        remoteIceLock.lock()
        guard hasRemoteDescription else {
            pendingRemoteIceCandidates.append(iceCandidate)
            remoteIceLock.unlock()
            return
        }
        remoteIceLock.unlock()
        applyRemoteIceCandidate(iceCandidate)
    }

    private func applyRemoteIceCandidate(_ iceCandidate: RTCIceCandidate) {
        peerConnection?.add(iceCandidate) { error in
            if let error = error {
                print("Error aplicando ICE candidate remoto: \(error)")
            } else {
                print("ICE candidate remoto aplicado exitosamente")
            }
        }
    }

    /// Sends a protocol frame over the DTLS-SRTP protected control channel.
    @discardableResult
    func sendDataChannelMessage(_ data: Data) -> Bool {
        guard let channel = controlDataChannel, channel.readyState == .open else { return false }
        return channel.sendData(RTCDataBuffer(data: data, isBinary: true))
    }
    
    /// Limpieza completa de recursos WebRTC.
    /// Debe llamarse antes de liberar la instancia.
    func dispose() {
        peerConnection?.close()
        peerConnection = nil
        remoteIceLock.lock()
        hasRemoteDescription = false
        pendingRemoteIceCandidates.removeAll(keepingCapacity: false)
        remoteIceLock.unlock()
        controlDataChannel?.delegate = nil
        controlDataChannel = nil
        localScreenCapturer = nil
        localScreenSource = nil
        onLocalIceCandidate = nil
        onDataChannelMessage = nil
        onDataChannelBinaryMessage = nil
        RTCCleanupSSL()
        print("RTCController disposed.")
    }
}

// MARK: - PeerConnection Delegate
extension RTCController: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {
        print("Signaling State: \(stateChanged.rawValue)")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        // [Crítico]: WebRTC ejecuta esto en su hilo C++ worker interno (Signaling Thread)
        DispatchQueue.main.async {
            print("✅ Track de video inyectado desde Android. Tracks: \(stream.videoTracks.count)")
            if let videoTrack = stream.videoTracks.first { self.attachRemoteVideoTrack(videoTrack) }
        }
    }

    /// Unified Plan delegate callback used by current WebRTC SDKs.
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd receiver: RTCRtpReceiver, streams: [RTCMediaStream]) {
        guard let videoTrack = receiver.track as? RTCVideoTrack else { return }
        DispatchQueue.main.async { self.attachRemoteVideoTrack(videoTrack) }
    }

    private func attachRemoteVideoTrack(_ track: RTCVideoTrack) {
        guard currentVideoTrack !== track else { return }
        currentVideoTrack?.remove(videoSink)
        track.add(videoSink)
        currentVideoTrack = track
        print("✅ Unified Plan remote video track attached id='\(track.trackId)'")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {
        print("Stream removido del Android")
    }
    
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {
        print("Renegociación necesaria")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        let stateName: String
        switch newState {
        case .new: stateName = "NEW"
        case .checking: stateName = "CHECKING"
        case .connected: stateName = "CONNECTED"
        case .completed: stateName = "COMPLETED"
        case .failed: stateName = "FAILED"
        case .disconnected: stateName = "DISCONNECTED"
        case .closed: stateName = "CLOSED"
        case .count: stateName = "COUNT"
        @unknown default: stateName = "UNKNOWN"
        }
        
        print("ICE Connection State: \(stateName)")
        
        DispatchQueue.main.async {
            self.iceConnectionState = newState
            self.isP2PConnected = (newState == .connected || newState == .completed)
        }
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
        let stateName: String
        switch newState {
        case .new: stateName = "NEW"
        case .gathering: stateName = "GATHERING"
        case .complete: stateName = "COMPLETE"
        @unknown default: stateName = "UNKNOWN"
        }
        print("ICE Gathering State: \(stateName)")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        // Candidate strings contain private/public network addresses; never log them.
        onLocalIceCandidate?(candidate)
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
        print("✅ DataChannel P2P abierto: '\(dataChannel.label)' (id: \(dataChannel.channelId))")
        controlDataChannel = dataChannel
        dataChannel.delegate = self
    }
}

// MARK: - DataChannel Delegate
extension RTCController: RTCDataChannelDelegate {
    func dataChannelDidChangeState(_ dataChannel: RTCDataChannel) {
        print("DataChannel '\(dataChannel.label)' state: \(dataChannel.readyState.rawValue)")
    }
    
    func dataChannel(_ dataChannel: RTCDataChannel, didReceiveMessageWith buffer: RTCDataBuffer) {
        if buffer.isBinary {
            Task { @MainActor in self.onDataChannelBinaryMessage?(buffer.data) }
            return
        }
        guard let message = String(data: buffer.data, encoding: .utf8) else { return }
        print("DataChannel ← \(message)")
        
        // Despachar en el main thread para mutar UI/Layout
        Task { @MainActor in
            self.onDataChannelMessage?(message)
        }
    }
}
