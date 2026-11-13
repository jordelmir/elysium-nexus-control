package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.content.Intent
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

internal class NexusRtcSession(
    context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionState: (PeerConnection.IceConnectionState) -> Unit,
    private val onProjectionStopped: () -> Unit
) {
    private val egl = EglBase.create()
    private val capturer = NexusScreenCapturer(context.applicationContext)
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun start(projectionGrant: Intent) {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST
        }
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onLocalIceCandidate(candidate)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                state?.let(onConnectionState)
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
        }
        peer = requireNotNull(factory).createPeerConnection(rtcConfig, observer)
            ?: error("Could not create WebRTC peer connection")
        val track = capturer.start(requireNotNull(factory), egl.eglBaseContext, projectionGrant, onProjectionStopped)
        check(peer?.addTrack(track, listOf("NEXUS_SCREEN")) != null) {
            "Could not attach Android screen track to the WebRTC session"
        }
        Log.i(TAG, "WebRTC peer ready with Android screen video track")
    }

    fun createOffer(onCreated: (String) -> Unit) {
        val activePeer = peer ?: error("Screen sharing is not running")
        activePeer.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) return
                activePeer.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() = onCreated(description.description)
                    override fun onSetFailure(error: String?) { Log.e(TAG, "Local SDP failed: $error") }
                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP offer failed: $error") }
                }, description)
            }
            override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP offer failed: $error") }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) { Log.e(TAG, "Local SDP failed: $error") }
        }, org.webrtc.MediaConstraints())
    }

    fun setAnswer(sdp: String) {
        val activePeer = peer ?: return
        activePeer.setRemoteDescription(
            object : SdpObserver {
                override fun onSetSuccess() { Log.i(TAG, "Mac SDP answer accepted") }
                override fun onSetFailure(error: String?) { Log.e(TAG, "Mac SDP answer rejected: $error") }
                override fun onCreateSuccess(description: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    fun addRemoteIce(candidate: String, mid: String?, line: Int) {
        peer?.addIceCandidate(IceCandidate(mid ?: "", line, candidate))
    }

    fun close() {
        capturer.stop()
        peer?.close()
        peer?.dispose()
        peer = null
        factory?.dispose()
        factory = null
        egl.release()
    }

    private companion object {
        const val TAG = "NexusRtcSession"
    }
}
