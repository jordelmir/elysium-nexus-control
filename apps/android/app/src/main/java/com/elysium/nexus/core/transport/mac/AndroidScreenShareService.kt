package com.elysium.nexus.core.transport.mac

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elysium.nexus.R
import com.elysium.nexus.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

enum class AndroidScreenShareStatus {
    STOPPED,
    STARTING,
    WAITING_FOR_MAC,
    CONNECTED,
    ERROR
}

object AndroidScreenShareState {
    private val mutableStatus = MutableStateFlow(AndroidScreenShareStatus.STOPPED)
    val status: StateFlow<AndroidScreenShareStatus> = mutableStatus.asStateFlow()

    internal fun update(value: AndroidScreenShareStatus) {
        mutableStatus.value = value
    }
}

object AndroidScreenShareController {
    fun start(context: Context, resultCode: Int, grant: Intent) {
        AndroidScreenShareState.update(AndroidScreenShareStatus.STARTING)
        val intent = Intent(context, AndroidScreenShareService::class.java)
            .setAction(AndroidScreenShareService.ACTION_START)
            .putExtra(AndroidScreenShareService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(AndroidScreenShareService.EXTRA_PROJECTION_GRANT, grant)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, AndroidScreenShareService::class.java)
                .setAction(AndroidScreenShareService.ACTION_STOP)
        )
    }
}

/** User-visible foreground service that owns the user-approved Android screen stream. */
class AndroidScreenShareService : Service() {
    private var rtc: NexusRtcSession? = null
    private var signaling: NexusScreenSignaling? = null
    private var advertiser: NexusScreenSignaling.Advertiser? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projectionGrant = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_GRANT)
        if (resultCode == 0 || projectionGrant == null) {
            fail("Android screen-capture permission was not provided")
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification("Esperando que Elysium Nexus Mac se conecte")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            AndroidScreenShareState.update(AndroidScreenShareStatus.STARTING)

            val server = NexusScreenSignaling()
            signaling = server
            val session = NexusRtcSession(
                context = applicationContext,
                onLocalIceCandidate = { candidate ->
                    server.sendIce(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                },
                onConnectionState = ::onIceState,
                onProjectionStopped = {
                    Log.i(TAG, "Android system revoked screen capture")
                    stopSelf()
                }
            )
            rtc = session
            session.start(projectionGrant)
            server.onOfferRequested = { sendOffer -> session.createOffer(sendOffer) }
            server.onAnswer = session::setAnswer
            server.onIceCandidate = session::addRemoteIce
            val port = server.start()
            val nsd = NexusScreenSignaling.Advertiser(this)
            advertiser = nsd
            nsd.start(port)
            AndroidScreenShareState.update(AndroidScreenShareStatus.WAITING_FOR_MAC)
            updateNotification("Esperando que Elysium Nexus Mac reciba la pantalla")
            Log.i(TAG, "Android screen sharing is ready on Bonjour _jsm_video._tcp port $port")
        } catch (error: Exception) {
            Log.e(TAG, "Could not start Android screen sharing", error)
            fail(error.message ?: "No se pudo iniciar la transmisión")
        }
        return START_NOT_STICKY
    }

    private fun onIceState(state: PeerConnection.IceConnectionState) {
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                AndroidScreenShareState.update(AndroidScreenShareStatus.CONNECTED)
                updateNotification("Compartiendo pantalla con Elysium Nexus Mac")
                Log.i(TAG, "WebRTC media path connected to Mac")
            }
            PeerConnection.IceConnectionState.FAILED -> fail("No se pudo establecer el enlace de vídeo con el Mac")
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                AndroidScreenShareState.update(AndroidScreenShareStatus.WAITING_FOR_MAC)
                updateNotification("Enlace interrumpido; esperando reconexión del Mac")
            }
            else -> Unit
        }
    }

    private fun fail(message: String) {
        AndroidScreenShareState.update(AndroidScreenShareStatus.ERROR)
        Log.e(TAG, message)
        stopSharing()
    }

    private fun stopSharing() {
        advertiser?.stop()
        advertiser = null
        signaling?.stop()
        signaling = null
        rtc?.close()
        rtc = null
        AndroidScreenShareState.update(AndroidScreenShareStatus.STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Compartir pantalla", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indica cuándo Elysium Nexus comparte la pantalla del Android con el Mac."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AndroidScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Elysium Nexus está compartiendo pantalla")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        advertiser?.stop()
        signaling?.stop()
        rtc?.close()
        AndroidScreenShareState.update(AndroidScreenShareStatus.STOPPED)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AndroidScreenShare"
        private const val CHANNEL_ID = "elysium_nexus_screen_share"
        private const val NOTIFICATION_ID = 7878
        const val ACTION_START = "com.elysium.nexus.action.START_SCREEN_SHARE"
        const val ACTION_STOP = "com.elysium.nexus.action.STOP_SCREEN_SHARE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_GRANT = "projection_grant"
    }
}
