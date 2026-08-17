package com.elysium.nexus.tvnode.application

import android.app.Application
import android.view.KeyEvent
import com.elysium.nexus.tvnode.BuildConfig
import com.elysium.nexus.tvnode.accessibility.NexusAccessibilityService
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.credential.AndroidKeyStoreTvCredentialVault
import com.elysium.nexus.tvnode.credential.TvCredentialVault
import com.elysium.nexus.tvnode.discovery.NexusTvDiscovery
import com.elysium.nexus.tvnode.identity.NexusTvIdentityProvider
import com.elysium.nexus.tvnode.ime.NexusTvIme
import com.elysium.nexus.tvnode.media.NotificationMediaObserver
import com.elysium.nexus.tvnode.pairing.PairingSession
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import com.elysium.nexus.tvnode.transport.TvActionDispatcher
import com.elysium.nexus.tvnode.transport.TvLinkListener

/**
 * TvNodeApp — app-level holder wiring the granted system services to the
 * observation surface. Owns the production control listener: vault-backed
 * pairing gate + real bound port, advertised AFTER binding (P0-12).
 */
class TvNodeApp : Application() {

    private val appIdentity by lazy { NexusTvIdentityProvider(this) }

    val identity: NexusTvIdentityProvider get() = appIdentity

    private var discovery: NexusTvDiscovery? = null

    private var listener: TvLinkListener? = null

    override fun onCreate() {
        super.onCreate()
        startControlSurface()
    }

    /**
     * Bind the control listener, then advertise the REAL bound port. If the
     * durable Keystore vault cannot be provisioned, the surface stays down
     * (fail-closed: no listener, no advertisement — never a soft in-memory
     * fallback that would hand out unpersisted pins).
     */
    private fun startControlSurface() {
        val vault = try {
            AndroidKeyStoreTvCredentialVault(this)
        } catch (e: Exception) {
            return // no durable vault → no control surface (honest, fail-closed)
        }
        val gate = SessionAwarePairingGate(vault) { pairingSession }
        val l = TvLinkListener(HonestUnsupportedDispatcher(), { gate })
        val state = l.start()
        if (state !is TvLinkListener.State.Bound) return
        listener = l
        // P0-12: advertise only the REAL bound port; never a made-up one.
        discovery = NexusTvDiscovery(this).also { it.start(l.boundPort) }
    }

    fun controlPort(): Int = listener?.boundPort ?: 0

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
        val session = try {
            PairingSession.create(
                clock = SYSTEM_CLOCK,
                nonce = com.elysium.nexus.tvnode.pairing.PairingNonce.generate(),
                deviceId = identity.deviceId.value,
                protocolVersion = 1,
                ttlMillis = ttlMillis,
                maxCodeAttempts = maxCodeAttempts
            )
        } catch (e: com.elysium.nexus.tvnode.channel.TvChannelCrypto.CryptoUnavailableException) {
            null // honest: pairing unsupported on this TV (no X25519) — never invented
        }
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

    /**
     * Baseline dispatcher until the accessibility/volume executor lands:
     * every action is answered UNSUPPORTED — never a made-up success
     * (TV-FABRIC.4: a fake EXECUTED is forbidden).
     */
    private class HonestUnsupportedDispatcher : TvActionDispatcher {
        override fun dispatch(
            envelope: TvLinkProtocol.TvEnvelope,
            action: UniversalAction?
        ): TvLinkProtocol.TvResponseBody =
            TvLinkProtocol.TvResponseBody(
                state = TvLinkProtocol.TvResponseState.UNSUPPORTED,
                answerToMessageId = envelope.messageId,
                detail = "no executor wired yet — never a fake success"
            )
    }

    companion object {
        private val SYSTEM_CLOCK = object : com.elysium.nexus.tvnode.pairing.PairingClock {
            override fun nowMillis(): Long = System.currentTimeMillis()
        }
    }
}