package com.elysium.nexus.tvnode.application

import android.app.Application
import android.view.KeyEvent
import com.elysium.nexus.tvnode.accessibility.NexusAccessibilityService
import com.elysium.nexus.tvnode.ime.NexusTvIme
import com.elysium.nexus.tvnode.media.NotificationMediaObserver

/**
 * TvNodeApp — app-level holder wiring the granted system services to the
 * observation surface. This is where the phone/tv pairing session would
 * drive reachable commands; production wiring adds the secure channel
 * (NSD + X25519/Elysium Link), which the node enables only after an
 * explicit user pairing intent.
 */
class TvNodeApp : Application() {

    var accessibility: NexusAccessibilityService? = null
        private set

    var ime: NexusTvIme? = null
        private set

    var notificationObserver: NotificationMediaObserver? = null
        private set

    /** ENGINEERING/LAB OVERRIDE ONLY — never enabled in retail default. */
    var dpadGlobalEnabled: Boolean = BuildConfig.DEBUG

    fun onAccessibilityGranted(service: NexusAccessibilityService) {
        accessibility = service
    }

    fun onImeReady(imeInstance: NexusTvIme) {
        ime = imeInstance
    }

    fun onImeFinalized() {
        ime = null
    }

    fun onNotificationAccessGranted(observer: NotificationMediaObserver) {
        notificationObserver = observer
    }

    fun onNotificationAccessRevoked() {
        notificationObserver = null
    }

    fun onForegroundWindow(packageName: String?) {
        // Observed state — used by the oracle's app-focus evidence lane.
    }

    fun onContentChanged(packageName: String?) {
        // Structural UI change — oracle candidate feedback.
    }

    fun onObservedKey(event: KeyEvent) {
        // Observable, never consumed. Fed to the key-observation lane.
    }

    fun refreshMediaSessions() {
        // Re-query active sessions through the observer.
    }
}