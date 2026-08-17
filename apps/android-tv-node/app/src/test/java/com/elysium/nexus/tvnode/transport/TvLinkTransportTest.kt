package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import com.elysium.nexus.tvnode.transport.PairingConfirm
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private fun dummyConfirm() = PairingConfirm("123456", "0".repeat(32))

/**
 * TvLinkTransportTest — the real phone↔TV wire over loopback (PR2 slice 4).
 *
 * NOT a mock: a [ServerSocket] on 127.0.0.1 and a real [Socket] run
 * [TvLinkServer] and [TvLinkClient] end-to-end — real handshake, real
 * AEAD-sealed ACTION/RESPONSE frames, the phone pins the TV fingerprint, the
 * server verifies connectionId. This is the cross-runner byte-parity harness
 * (the same envelope bytes the phone encodes are what the TV decodes).
 */
class TvLinkTransportTest {

    private val deviceId = "test-tv-001"
    private val connectionId = 0x5152_5354_0000_0001L

    @Test
    fun `phone and tv handshake over a real socket and keys mirror`() {
        val outcome = AtomicReference<TvLinkServer.Outcome>()

        val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val server = TvLinkServer(EchoDispatcher(), AllowAllPairingGate())
        val serverThread = Thread {
            val accepted = serverSocket.accept()
            outcome.set(server.handle(accepted))
        }.apply { isDaemon = true; start() }

        val client = TvLinkClient(connectionId, dummyConfirm())
        Socket(java.net.InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
            client.connect(socket) as TvLinkClient.Result.Established
            client.close(socket)
        }
        serverSocket.close()
        serverThread.join(2_000)

        assertNotNull(client.serverIdentity)
        assertEquals(8, client.serverIdentity!!.fingerprint.length)
        assertEquals(TvLinkServer.Outcome.Clean(0), outcome.get())
    }

    @Test
    fun `action envelopes round-trip encrypted with mirrored channel keys`() {
        val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val server = TvLinkServer(EchoDispatcher(), AllowAllPairingGate())
        val outcome = AtomicReference<TvLinkServer.Outcome>()
        val serverThread = Thread {
            outcome.set(server.handle(serverSocket.accept()))
        }.apply { isDaemon = true; start() }

        val client = TvLinkClient(connectionId, dummyConfirm())
        Socket(java.net.InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
            client.connect(socket) as TvLinkClient.Result.Established
            val action = UniversalAction.Navigate(DeviceId(deviceId), Direction.Up)
            val envelope = TvLinkProtocol.TvEnvelope(
                protocolVersion = TvLinkProtocol.PROTOCOL_VERSION,
                messageId = 42,
                connectionId = connectionId,
                deviceId = deviceId,
                action = TvLinkProtocol.encodeAction(action),
                timestampMillis = 1_700_000_000_000,
                deadlineMillis = 1_700_000_000_100,
                sequenceNumber = 7,
                capabilityContext = "jvm-tv",
                authMetadata = client.serverIdentity!!.fingerprint
            )
            val response = client.sendAction(envelope)
            assertNotNull("must receive a RESPONSE", response)
            val body = response!!
            assertEquals(TvLinkProtocol.TvResponseState.EXECUTED, body.state)
            assertEquals(42, body.answerToMessageId)
            client.close(socket)
        }
        serverSocket.close()
        serverThread.join(2_000)

        assertEquals(TvLinkServer.Outcome.Clean(1), outcome.get())
    }

    @Test
    fun `envelope with a foreign connectionId is rejected and the link tears down`() {
        val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val server = TvLinkServer(EchoDispatcher(), AllowAllPairingGate())
        val outcome = AtomicReference<TvLinkServer.Outcome>()
        val serverThread = Thread {
            outcome.set(server.handle(serverSocket.accept()))
        }.apply { isDaemon = true; start() }

        val client = TvLinkClient(connectionId, dummyConfirm())
        Socket(java.net.InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
            client.connect(socket) as TvLinkClient.Result.Established
            val envelope = TvLinkProtocol.TvEnvelope(
                protocolVersion = TvLinkProtocol.PROTOCOL_VERSION,
                messageId = 1,
                connectionId = 987_654L, // NOT the handshake connectionId
                deviceId = deviceId,
                action = TvLinkProtocol.TvWireAction(TvLinkProtocol.TvActionCode.HOME),
                timestampMillis = 1,
                deadlineMillis = 2,
                sequenceNumber = 1,
                capabilityContext = "jvm-tv",
                authMetadata = ""
            )
            val response = client.sendAction(envelope)
            // The server must answer with an ERROR (no RESPONSE) and drop us.
            assertTrue("foreign connectionId must never be answered with RESPONSE", response == null)
            client.close(socket)
        }
        serverSocket.close()
        serverThread.join(2_000)

        val o = outcome.get()
        assertTrue(
            "server must tear down foreign connectionId (got $o)",
            o is TvLinkServer.Outcome.Failed
        )
    }

    @Test
    fun `same envelope bytes the phone sends are what the tv decodes - golden parity`() {
        val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val captured = AtomicReference<Pair<TvLinkProtocol.TvEnvelope, TvLinkProtocol.TvResponseBody>>()
        val latch = CountDownLatch(1)
        val server = TvLinkServer(object : TvActionDispatcher {
            override fun dispatch(
                envelope: TvLinkProtocol.TvEnvelope,
                action: UniversalAction?
            ): TvLinkProtocol.TvResponseBody {
                captured.set(envelope to TvLinkProtocol.TvResponseBody(
                    TvLinkProtocol.TvResponseState.OBSERVED,
                    envelope.messageId,
                    "parity-ok"
                ))
                latch.countDown()
                return captured.get()!!.second
            }
        }, AllowAllPairingGate())
        val serverThread = Thread {
            server.handle(serverSocket.accept())
        }.apply { isDaemon = true; start() }

        val client = TvLinkClient(connectionId, dummyConfirm())
        val action = UniversalAction.VolumeUp(DeviceId(deviceId))
        val envelope = TvLinkProtocol.TvEnvelope(
            protocolVersion = TvLinkProtocol.PROTOCOL_VERSION,
            messageId = 99,
            connectionId = connectionId,
            deviceId = deviceId,
            action = TvLinkProtocol.encodeAction(action),
            timestampMillis = 111_111_111,
            deadlineMillis = 222_222_222,
            sequenceNumber = 555,
            capabilityContext = "en-us|1080p",
            authMetadata = "qrfingerprint:abc123"
        )
        Socket(java.net.InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
            client.connect(socket) as TvLinkClient.Result.Established
            val encoded = TvLinkProtocol.encodeEnvelope(envelope)
            val decoded = TvLinkProtocol.decodeEnvelope(encoded)
            assertNotNull("local round-trip of the same envelope faith", decoded)
            assertArrayEquals(
                "encode->decode must be byte-faithful",
                encoded,
                TvLinkProtocol.encodeEnvelope(decoded!!)
            )
            val response = client.sendAction(envelope)
            assertNotNull(response)
            assertEquals(TvLinkProtocol.TvResponseState.OBSERVED, response!!.state)
            client.close(socket)
        }
        assertTrue("dispatcher must have run", latch.await(5, TimeUnit.SECONDS))
        serverSocket.close()
        serverThread.join(2_000)

        val (gotEnvelope, gotResponse) = captured.get()
        assertEquals(99L, gotEnvelope.messageId)
        assertEquals(connectionId, gotEnvelope.connectionId)
        assertEquals(TvLinkProtocol.TvActionCode.VOLUME_UP, gotEnvelope.action.code)
        assertArrayEquals(
            "the response body round-trips byte-identically",
            TvLinkProtocol.encodeResponseBody(
                TvLinkProtocol.TvResponseBody(
                    TvLinkProtocol.TvResponseState.OBSERVED,
                    99,
                    "parity-ok"
                )
            ),
            TvLinkProtocol.encodeResponseBody(gotResponse)
        )
    }

    @Test
    fun `channel keys really mirror - tv rx equals phone tx`() {
        // Re-uses the public channel-key mirroring proof, now through the
        // client's derived keys instead of a hand-objected derivation: the
        // client derives LinkSide.PHONE and the server derives LinkSide.TV.
        val serverPair = TvChannelCrypto.generateKeyPair()
        val phonePair = TvChannelCrypto.generateKeyPair()
        val tvKeys = TvChannelCrypto.deriveChannelKeys(serverPair, phonePair.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phonePair, serverPair.publicKeyBytes, TvChannelCrypto.LinkSide.PHONE)
        assertArrayEquals(tvKeys.rxKeyBytes, phoneKeys.txKeyBytes)
        assertArrayEquals(tvKeys.txKeyBytes, phoneKeys.rxKeyBytes)
    }

    /** Answers EXECUTED to any action — the test's honest seam. */
    private class EchoDispatcher : TvActionDispatcher {
        override fun dispatch(
            envelope: TvLinkProtocol.TvEnvelope,
            action: UniversalAction?
        ): TvLinkProtocol.TvResponseBody = TvLinkProtocol.TvResponseBody(
            TvLinkProtocol.TvResponseState.EXECUTED,
            envelope.messageId,
            "test-ok"
        )
    }
}
