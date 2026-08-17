package com.elysium.nexus.core.transport.tvnode

import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import com.elysium.nexus.tvnode.transport.PairingConfirm
import com.elysium.nexus.tvnode.transport.TvLinkClient
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom

/**
 * TvNodePhoneLink — the phone-side seam to a TV Node (Master Order v0.10
 * Phase 21: the wire truth is shared via `:tvlink`, this facade is the
 * controller's own thin usage of it).
 *
 * Lifecycle: [connect] (TCP + full X25519 handshake + optional PAIR_CONFIRM)
 * → [sendAction] (AEAD-sealed ACTION/RESPONSE round trip) → [close].
 *
 * Pure JVM (no Android imports): the software-only phone↔TV E2E is tested
 * here against the REAL shared [TvLinkServer], not a mock.
 */
class TvNodePhoneLink(
    private val connectionId: Long = DEFAULT_CONNECTION_ID_NS
) {
    private var socket: Socket? = null
    private var client: TvLinkClient? = null

    sealed class ConnectResult {
        data class Established(val serverFingerprint: String) : ConnectResult()
        data class Failed(val reason: String) : ConnectResult()
    }

    val isConnected: Boolean get() = client != null && socket != null

    /** The phone's X25519 public key once established (HELLO payload). */
    val myPublicKeyBytes: ByteArray? get() = client?.myPublicKeyBytes

    /** The TV's full SHA-256 identity once established (64 hex). */
    val serverFullIdentity: String? get() = client?.serverIdentity?.let {
        com.elysium.nexus.tvnode.channel.TvChannelCrypto.fullFingerprintOf(it.publicKeyBytes)
    }

    fun connect(
        host: String,
        port: Int,
        confirm: PairingConfirm? = null
    ): ConnectResult {
        require(port in 1..65535) { "refusing a port that is outside the real range: $port" }
        val s = try {
            Socket(InetAddress.getByName(host), port)
        } catch (e: Exception) {
            return ConnectResult.Failed("connect failed: ${e.message}")
        }
        val c = TvLinkClient(connectionId, confirm)
        return when (val r = c.connect(s)) {
            is TvLinkClient.Result.Established -> {
                socket = s
                client = c
                ConnectResult.Established(c.serverIdentity!!.fingerprint)
            }
            is TvLinkClient.Result.Failed -> {
                runCatching { s.close() }
                ConnectResult.Failed(r.reason)
            }
        }
    }

    /**
     * Send an ACTION envelope from [action] targeted at the TV.
     *
     * @return the honest RESPONSE from the TV, or null if the link tore down
     *   (fail-closed: never invent a success).
     */
    fun sendAction(
        action: UniversalAction,
        sequenceNumber: Long,
        capabilityContext: String = "android-controller"
    ): TvLinkProtocol.TvResponseBody? {
        val c = requireNotNull(client) { "link not established" }
        val s = requireNotNull(socket)
        val envelope = TvLinkProtocol.TvEnvelope(
            protocolVersion = TvLinkProtocol.PROTOCOL_VERSION,
            messageId = sequenceNumber,
            connectionId = connectionId,
            deviceId = action.targetDeviceId.value,
            action = TvLinkProtocol.encodeAction(action),
            timestampMillis = System.currentTimeMillis(),
            deadlineMillis = System.currentTimeMillis() + DEADLINE_MILLIS,
            sequenceNumber = sequenceNumber,
            capabilityContext = capabilityContext,
            authMetadata = c.serverIdentity!!.fingerprint
        )
        return c.sendAction(envelope)
    }

    fun close() {
        val c = client
        val s = socket
        client = null
        socket = null
        c?.close(s)
    }

    companion object {
        const val DEADLINE_MILLIS = 2_000L

        /** Random namespace connection id (mirrors the TV Node client default). */
        val DEFAULT_CONNECTION_ID_NS: Long = SecureRandom().nextLong().let { if (it < 0) -it else it }
    }
}