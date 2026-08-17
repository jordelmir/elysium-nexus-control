package com.elysium.nexus.core.transport.tvnode

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * TvNodeDiscovery — the phone-side consumer of the TV Node's NSD service
 * (Master Order v0.10 Phase 21; P0-12: only REAL bound ports are ever
 * consumed — an advertised port outside 1..65535 is discarded, never
 * connected to).
 *
 * Resolves `_elysium-tv._tcp` (ONE shared constant via `:tvlink`) to a
 * host:port the controller can hand to [TvNodePhoneLink].
 *
 * Thin Android glue by design: no business logic (resolve → validate port →
 * hand over), so the wire truth stays in the shared module and this file
 * stays small enough to be obviously correct.
 */
class TvNodeDiscovery(private val context: Context) {

    sealed class DiscoveryResult {
        data class Found(val host: String, val port: Int) : DiscoveryResult()
        object NotFound : DiscoveryResult()
        data class Failed(val reason: String) : DiscoveryResult()
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Resolve the first TV Node found within [timeoutMillis]. Blocking. */
    fun resolveFirst(timeoutMillis: Long = 3_000): DiscoveryResult {
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<DiscoveryResult>(DiscoveryResult.NotFound)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Resolve this candidate; the resolver validates host+port.
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (info.port in 1..65535) {
                            outcome.set(DiscoveryResult.Found(info.host.hostName, info.port))
                        } else {
                            outcome.set(DiscoveryResult.Failed("advertised port ${info.port} is not a real port"))
                        }
                        latch.countDown()
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                latch.countDown()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                outcome.set(DiscoveryResult.Failed("discovery start failed ($errorCode)"))
                latch.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        return try {
            nsdManager.discoverServices(
                TvLinkProtocol.NSD_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
            val found = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!found) {
                DiscoveryResult.NotFound
            } else {
                outcome.get()
            }
        } catch (e: Exception) {
            DiscoveryResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }
}