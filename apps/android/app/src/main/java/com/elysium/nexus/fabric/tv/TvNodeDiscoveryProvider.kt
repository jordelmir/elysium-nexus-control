package com.elysium.nexus.fabric.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.elysium.nexus.fabric.canonical.DeviceId
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import kotlin.coroutines.resume

/**
 * §9 Phone ↔ TV Discovery via Android NSD / DNS-SD.
 *
 * Discovers TV Nodes advertising the `_elysium-tv._tcp` service type.
 * One physical television has one [TvNodeDiscoveryRecord]; multi-transport
 * bindings are resolved later via the DeviceTwin.
 */
data class TvNodeDiscoveryRecord(
    val deviceId: DeviceId?,
    val displayName: String,
    val host: InetAddress,
    val port: Int,
    val manufacturer: String?,
    val model: String?,
    val txtRecords: Map<String, String> = emptyMap()
)

/**
 * Discovers TV Nodes on the local network via NSD/DNS-SD.
 *
 * The discovery record contains only non-sensitive bootstrap metadata.
 * Do not treat IP address as device identity.
 */
class TvNodeDiscoveryProvider(private val context: Context) {

    companion object {
        private const val TAG = "TvNodeDiscovery"
        /** §9 service type for Elysium TV Nodes. */
        const val TV_NODE_SERVICE_TYPE = "_elysium-tv._tcp"
        const val DISCOVERY_TIMEOUT_MS = 10_000L
    }

    /**
     * Discover TV Nodes on the local network.
     * Suspends until at least one device is found or timeout expires.
     * Returns an empty list if no TV Nodes are advertising.
     */
    suspend fun discover(timeoutMs: Long = DISCOVERY_TIMEOUT_MS): List<TvNodeDiscoveryRecord> {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return emptyList()

        return suspendCancellableCoroutine { continuation ->
            val discovered = mutableListOf<TvNodeDiscoveryRecord>()
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var isDone = false

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "Discovery started for $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service found: ${serviceInfo.serviceName} (type=${serviceInfo.serviceType})")
                    // Resolve each service
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w(TAG, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val txtRecords = mutableMapOf<String, String>()
                            serviceInfo.attributes?.let { attrs ->
                                attrs.keys?.forEach { key ->
                                    attrs[key]?.let { value ->
                                        txtRecords[key] = String(value)
                                    }
                                }
                            }
                            val record = TvNodeDiscoveryRecord(
                                deviceId = null, // set during pairing
                                displayName = txtRecords["name"] ?: serviceInfo.serviceName,
                                host = serviceInfo.host ?: return,
                                port = serviceInfo.port,
                                manufacturer = txtRecords["manufacturer"],
                                model = txtRecords["model"],
                                txtRecords = txtRecords
                            )
                            synchronized(discovered) {
                                discovered.add(record)
                            }
                            Log.d(TAG, "Resolved TV Node: ${record.displayName} @ ${record.host}:${record.port}")
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "Discovery stopped for $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Start discovery failed for $serviceType: $errorCode")
                    if (!isDone) {
                        isDone = true
                        continuation.resume(emptyList())
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Stop discovery failed for $serviceType: $errorCode")
                }
            }

            nsdManager.discoverServices(TV_NODE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            // Timeout: return whatever we found so far
            handler.postDelayed({
                if (!isDone) {
                    isDone = true
                    try {
                        nsdManager.stopServiceDiscovery(discoveryListener)
                    } catch (_: Exception) {}
                    synchronized(discovered) {
                        continuation.resume(discovered.toList())
                    }
                }
            }, timeoutMs)

            continuation.invokeOnCancellation {
                if (!isDone) {
                    isDone = true
                    try {
                        nsdManager.stopServiceDiscovery(discoveryListener)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}