package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack

internal class NexusScreenCapturer(private val context: Context) {
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null

    fun start(
        factory: PeerConnectionFactory,
        eglContext: EglBase.Context,
        projectionGrant: Intent,
        onProjectionStopped: () -> Unit
    ): VideoTrack {
        val screenCapturer = ScreenCapturerAndroid(
            projectionGrant,
            object : MediaProjection.Callback() {
                override fun onStop() = onProjectionStopped()
            }
        )
        capturer = screenCapturer
        val source = factory.createVideoSource(true)
        textureHelper = SurfaceTextureHelper.create("NexusScreenCapture", eglContext)
            ?: error("Could not create the WebRTC screen capture thread")
        screenCapturer.initialize(textureHelper, context, source.capturerObserver)
        screenCapturer.startCapture(1920, 1080, 30)
        Log.i(TAG, "User-approved MediaProjection capture started at 1920x1080@30")
        return factory.createVideoTrack("NEXUS_ANDROID_SCREEN", source)
    }

    fun stop() {
        try {
            capturer?.stopCapture()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            Log.w(TAG, "Error stopping screen capture", error)
        }
        capturer?.dispose()
        capturer = null
        textureHelper?.dispose()
        textureHelper = null
    }

    private companion object {
        const val TAG = "NexusScreenCapture"
    }
}
