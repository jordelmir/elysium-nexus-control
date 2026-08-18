package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * TvLinkListenerTest — the first REAL bound-port control surface (Master
 * Order v0.10 Phase 20, P0-12).
 *
 * No simulated port, no made-up address: the listener binds an OS-assigned
 * port and the client phones it through the same loopback path a phone on
 * the LAN would use.
 */
class TvLinkListenerTest {

    private val connectionId = 0x5152_5354_0000_0001L
    private val deviceId = DeviceId("universal:test:abs")

    @Test
    fun `listener binds a real port and serves a full phone round trip`() {
        val served = AtomicInteger(0)
        val listener = TvLinkListener(
            dispatcher = object : TvActionDispatcher {
                override fun dispatch(
                    envelope: TvLinkProtocol.TvEnvelope,
                    action: UniversalAction?
                ): TvLinkProtocol.TvResponseBody {
                    served.incrementAndGet()
                    return TvLinkProtocol.TvResponseBody(
                        TvLinkProtocol.TvResponseState.EXECUTED,
                        envelope.messageId,
                        "e2e-ok"
                    )
                }
            },
            pairingGateProvider = { AllowAllPairingGate() }
        )

        val state = listener.start()
        assertTrue("listener must bind a real port", state is TvLinkListener.State.Bound)
        val port = listener.boundPort
        assertTrue("bound port must be a real OS-assigned port", port in 1..65535)

        val client = TvLinkClient(connectionId, PairingConfirm("123456", "0".repeat(32)))
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            client.connect(socket) as TvLinkClient.Result.Established
            val response = client.sendAction(
                TvLinkProtocol.TvEnvelope(
                    protocolVersion = TvLinkProtocol.PROTOCOL_VERSION,
                    messageId = 7,
                    connectionId = connectionId,
                    deviceId = deviceId.value,
                    action = TvLinkProtocol.encodeAction(
                        UniversalAction.Navigate(deviceId, Direction.Up)
                    ),
                    timestampMillis = 1,
                    deadlineMillis = 2,
                    sequenceNumber = 1,
                    capabilityContext = "jvm-tv",
                    authMetadata = client.serverIdentity!!.fingerprint
                )
            )
            assertNotNull("phone must get an honest response over the bound port", response)
            assertEquals(TvLinkProtocol.TvResponseState.EXECUTED, response!!.state)
            client.close(socket)
        }

        // Accept/serve happens on listener daemon threads; give them a moment
        // to report the served action, then verify the count (never guess).
        var deadline = System.currentTimeMillis() + 3_000
        while (served.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertEquals("the listener must really serve the action", 1, served.get())

        listener.stop()
        assertEquals(TvLinkListener.State.Stopped, listener.state())
        assertNotEquals("after stop, a new listener binds a fresh free port", port,
            (TvLinkListener(AllowAllDispatch(), { AllowAllPairingGate() })
                .start() as TvLinkListener.State.Bound).port)
    }

    @Test
    fun `two listeners never share a port`() {
        val a = TvLinkListener(AllowAllDispatch(), { AllowAllPairingGate() }).start()
        val b = TvLinkListener(AllowAllDispatch(), { AllowAllPairingGate() }).start()
        assertTrue(a is TvLinkListener.State.Bound && b is TvLinkListener.State.Bound)
        assertNotEquals((a as TvLinkListener.State.Bound).port, (b as TvLinkListener.State.Bound).port)
        (a as TvLinkListener.State.Bound).let { }
        (b as TvLinkListener.State.Bound).let { }
    }

    private class AllowAllDispatch : TvActionDispatcher {
        override fun dispatch(
            envelope: TvLinkProtocol.TvEnvelope,
            action: UniversalAction?
        ): TvLinkProtocol.TvResponseBody =
            TvLinkProtocol.TvResponseBody(
                TvLinkProtocol.TvResponseState.EXECUTED,
                envelope.messageId,
                "test"
            )
    }
}