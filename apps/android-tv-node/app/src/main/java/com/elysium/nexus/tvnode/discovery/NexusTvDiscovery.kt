package com.elysium.nexus.tvnode.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.elysium.nexus.tvnode.application.TvNodeApp

/**
 * NexusTvDiscovery — advertisement of the TV Node over NSD/DNS-SD
 * (PR2, §9 "Phone ↔ TV Discovery").
 *
 * Advertises `_elysium-tv._tcp` with ONLY non-sensitive bootstrap metadata:
 * - service name: friendly display text (manufacturer + model family)
 * - port: the REAL bound port of the running [TvLinkListener] — never a
 *   made-up value (Master Order v0.10 Phase 20 / audit P0-12: `port=0` is
 *   not advertised; discovery only starts once a listener is actually bound)
 * - TXT records: serviceType=elysium-tv, v=<node version>, api=<Android API>,
 *   platform=<os family>, man=<manufacturer>, model=<model>
 *
 * Never advertised:
 * - MAC, serial, IP as identity, pairing code, public keys, nonces.
 * The IP address is treated as an endpoint, never as identity (§9).
 *
 * The service name deliberately carries no unique ID so a fresh install
 * cannot be fingerprinted on the LAN before pairing; pairing binds the
 * peer via QR + code.
 */
class NexusTvDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registered = false

    /**
     * Advertise the node on [port] — the real bound port of the control
     * listener. Refuses `port <= 0`: advertising a fake port would hand
     * phones a door that does not exist (P0-12).
     */
    fun start(advertisedPort: Int): Boolean {
        if (registered) return advertisedPort == boundPort
        require(advertisedPort in 1..65535) { "advertised port must be the real bound port, got $advertisedPort" }
        val app = context.applicationContext as TvNodeApp
        val facts = app.identity.deviceFacts()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Elysium TV — ${facts.manufacturer} ${facts.model}".take(60)
            serviceType = SERVICE_TYPE
            port = advertisedPort
            setAttribute("svc", "elysium-tv")
            setAttribute("v", NODE_VERSION)
            setAttribute("api", facts.apiLevel.toString())
            setAttribute("platform", facts.platform)
            setAttribute("man", facts.manufacturer)
            setAttribute("model", facts.model)
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        registered = true
        boundPort = advertisedPort
        return true
    }

    /** The port this instance advertised its service on (0 if never started). */
    var boundPort: Int = 0
        private set

    fun stop() {
        if (!registered) return
        runCatching { nsdManager.unregisterService(registrationListener) }
        registered = false
        boundPort = 0
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            // Keep state simple; failures surface through onRegistrationFailed.
        }

        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit

        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
    }

    companion object {
        /** Shared with the phone consumer — one truth for the service type. */
        const val SERVICE_TYPE = com.elysium.nexus.tvnode.protocol.TvLinkProtocol.NSD_SERVICE_TYPE
        const val NODE_VERSION = "0.1.0"
    }
}