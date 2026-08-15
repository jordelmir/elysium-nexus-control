package com.elysium.nexus.tvnode.application

import android.app.Application
import android.view.KeyEvent
import com.elysium.nexus.tvnode.accessibility.NexusAccessibilityService
import com.elysium.nexus.tvnode.discovery.NexusTvDiscovery
import com.elysium.nexus.tvnode.identity.NexusTvIdentityProvider
import com.elysium.nexus.tvnode.ime.NexusTvIme
import com.elysium.nexus.tvnode.media.NotificationMediaObserver
import com.elysium.nexus.tvnode.pairing.PairingSession

/**
 * TvNodeApp — app-level holder wiring the granted system services to the
 * observation surface. This is where the phone/tv pairing session would
 * drive reachable commands.
 */
class TvNodeApp : Application() {

    private val appIdentity by lazy { NexusTvIdentityProvider(this) }

    val identity: NexusTvIdentityProvider get() = appIdentity

    private var discovery: NexusTvDiscovery? = null

    override fun onCreate() {
        super.onCreate()
        // Advertise _elysium-tv._tcp while the node process lives.
        // NSD is the local-discovery lane (PR2, §9); nothing sensitive is
        // ever advertised.
        discovery = NexusTvDiscovery(this).also { it.start() }
    }

    var accessibility: NexusAccessibilityService? = null
        private set

    var ime: NexusTvIme? = null
        private set

    var notificationObserver: NotificationMediaObserver? = null
        private set

    /** Active pairing session (single at a time). Null when idle. */
    var pairingSession: PairingSession? = null
        private set

    /** ENGINEERING/LAB OVERRIDE ONLY — never enabled in retail default. */
    var dpadGlobalEnabled: Boolean = BuildConfig.DEBUG

    fun startPairing(ttlMillis: Long = 60_000, maxCodeAttempts: Int = 5): PairingSession? {
        if (pairingSession != null) return null // one pairing intent at a time
        val session = PairingSession.create(
            clock = SYSTEM_CLOCK,
            nonce = com.elysium.nexus.tvnode.pairing.PairingNonce.generate(),
            qrFingerprint = "00000000", // replaced when the channel key is generated (next slice)
            deviceId = identity.deviceId.value,
            protocolVersion = 1,
            ttlMillis = ttlMillis,
            maxCodeAttempts = maxCodeAttempts
        )
        pairingSession = session
        return session
    }

    fun onPairingFinished() {
        pairingSession = null
    }

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

    companion object {
        private val SYSTEM_CLOCK = object : com.elysium.nexus.tvnode.pairing.PairingClock {
            override fun nowMillis(): Long = System.currentTimeMillis()
        }
    }
}