package com.elysium.nexus.tvnode.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.content.Context
import com.elysium.nexus.tvnode.application.TvNodeApp

/**
 * NotificationMediaObserver — the ONLY honest route to media sessions
 * (TV-FABRIC.6): the user must explicitly grant notification access in
 * system settings, and the presence of controllers is queried through
 * MediaSessionManager.
 *
 * Play/Pause/Next travel through MediaController.transportControls on
 * the *current* session — observed after every command, never assumed.
 */
class NotificationMediaObserver : NotificationListenerService() {

    private val app: TvNodeApp get() = application as TvNodeApp

    override fun onListenerConnected() {
        super.onListenerConnected()
        app.onNotificationAccessGranted(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) app.refreshMediaSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        app.refreshMediaSessions()
    }

    override fun onDestroy() {
        app.onNotificationAccessRevoked()
        super.onDestroy()
    }

    /** Returns active controllers; empty when no media is playing. */
    fun activeControllers(): List<MediaController> {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return emptyList()
        return manager.getActiveSessions(null)
    }
}