package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import com.elysium.nexus.tvnode.credential.InMemoryTvCredentialVault
import com.elysium.nexus.tvnode.pairing.PairingClock
import com.elysium.nexus.tvnode.pairing.PairingNonce
import com.elysium.nexus.tvnode.pairing.PairingSession
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * PairingGateTest — PR2 slice 5 (§10 "Unknown peer: REJECT"). The wire
 * handshake proves channel-key possession, but NOT that the phone saw the
 * pairing screen or knows the code; the gate adds that proof and the durable
 * pin. Unit tests exercise the decision logic; the last two run the real
 * loopback socket with the gate wired into [TvLinkServer].
 */
class PairingGateTest {

    private class FakeClock(var now: Long = 1_000_000L) : PairingClock {
        override fun nowMillis(): Long = now
    }

    private fun fingerprintOfKey(pair: TvChannelCrypto.KeyPair): String =
        TvChannelCrypto.fingerprintOf(pair.publicKeyBytes)

    private fun newSession(
        clock: FakeClock,
        deviceId: String = "universal:test:abs"
    ): PairingSession = PairingSession.create(
        clock = clock,
        nonce = PairingNonce.generate(),
        deviceId = deviceId,
        protocolVersion = 1,
        ttlMillis = 60_000,
        maxCodeAttempts = 5
    )

    // ------------------------------------------------------------------
    // PairingConfirm codec
    // ------------------------------------------------------------------

    @Test
    fun `pairing confirm encodes and parses back exactly`() {
        val confirm = PairingConfirm(code = "123456", nonce = "0123456789abcdef")
        val encoded = confirm.encode()
        assertEquals(1 + 6 + 16, encoded.size)
        val parsed = PairingConfirm.parse(encoded)
        assertNotNull(parsed)
        assertEquals(confirm.code, parsed!!.code)
        assertEquals(confirm.nonce, parsed.nonce)
    }

    @Test
    fun `malformed pairing confirm payloads are rejected`() {
        assertNull(PairingConfirm.parse(ByteArray(0)))
        assertNull(PairingConfirm.parse(ByteArray(1 + 6 + 16) { 0 })) // non-blank but invalid nonce chars
        assertNull(PairingConfirm.parse(ByteArray(1 + 5 + 16) { '0'.code.toByte() })) // short code
        val badNonce = byteArrayOf(6, '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(),
            '5'.code.toByte(), '6'.code.toByte()) + "zzzzzzzzzzzzzzzz".toByteArray(Charsets.UTF_8)
        assertNull(PairingConfirm.parse(badNonce))
    }

    // ------------------------------------------------------------------
    // Gate decision logic (no socket)
    // ------------------------------------------------------------------

    @Test
    fun `already pinned peer is authorized without a code - reconnect path`() {
        val vault = InMemoryTvCredentialVault()
        val phone = TvChannelCrypto.generateKeyPair()
        vault.pinPeerAndCheckFingerprint(fingerprintOfKey(phone))
        val gate = CodeConfirmPairingGate(vault, session = null) // no active session

        val verdict = gate.authorize(fingerprintOfKey(phone), confirm = null)
        assertEquals(PairingGate.Verdict.Authorized::class, verdict::class)
    }

    @Test
    fun `first pairing with correct code and matching nonce pins the peer`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock)
        val phone = TvChannelCrypto.generateKeyPair()
        val gate = CodeConfirmPairingGate(vault, session)

        val displayed = session.displayCode()!!
        val qr = session.qrPayload()!!
        val confirm = PairingConfirm(displayed.value, qr.nonce.value)

        val verdict = gate.authorize(fingerprintOfKey(phone), confirm)
        assertEquals(PairingGate.Verdict.Authorized::class, verdict::class)
        assertTrue("correct pairing must pin the peer durably", vault.isPeerPinned(fingerprintOfKey(phone)))
    }

    @Test
    fun `wrong pairing code is denied and the peer is never pinned`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock)
        val phone = TvChannelCrypto.generateKeyPair()
        val gate = CodeConfirmPairingGate(vault, session)

        val qr = session.qrPayload()!!
        val confirm = PairingConfirm(code = "999999", nonce = qr.nonce.value)

        val verdict = gate.authorize(fingerprintOfKey(phone), confirm)
        assertEquals(PairingGate.Verdict.Denied::class, verdict::class)
        val denied = verdict as PairingGate.Verdict.Denied
        assertTrue(denied.reason.contains("pairing code rejected", ignoreCase = true))
        assertTrue(!vault.isPeerPinned(fingerprintOfKey(phone)))
    }

    @Test
    fun `nonce mismatch is denied even with the correct code`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock)
        val phone = TvChannelCrypto.generateKeyPair()
        val gate = CodeConfirmPairingGate(vault, session)

        val displayed = session.displayCode()!!
        val confirm = PairingConfirm(displayed.value, "ffffffffffffffff") // NOT this session's nonce

        val verdict = gate.authorize(fingerprintOfKey(phone), confirm)
        assertEquals(PairingGate.Verdict.Denied::class, verdict::class)
        val denied = verdict as PairingGate.Verdict.Denied
        assertTrue(denied.reason.contains("nonce mismatch", ignoreCase = true))
        assertTrue(!vault.isPeerPinned(fingerprintOfKey(phone)))
    }

    @Test
    fun `unknown peer with an expired session is denied`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock, deviceId = "universal:test:offline")
        clock.now += 90_000 // past the 60s TTL
        val phone = TvChannelCrypto.generateKeyPair()
        val gate = CodeConfirmPairingGate(vault, session)

        val confirm = PairingConfirm("123456", "0000000000000000")
        val verdict = gate.authorize(fingerprintOfKey(phone), confirm)
        assertEquals(PairingGate.Verdict.Denied::class, verdict::class)
        assertTrue(!vault.isPeerPinned(fingerprintOfKey(phone)))
    }

    @Test
    fun `no session and unpinned peer is rejected`() {
        val vault = InMemoryTvCredentialVault()
        val phone = TvChannelCrypto.generateKeyPair()
        val gate = CodeConfirmPairingGate(vault, session = null)

        val verdict = gate.authorize(fingerprintOfKey(phone), PairingConfirm("123456", "0000000000000000"))
        assertEquals(PairingGate.Verdict.Denied::class, verdict::class)
        assertTrue(!vault.isPeerPinned(fingerprintOfKey(phone)))
    }

    @Test
    fun `missing pairing confirm on first pairing is denied`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock)
        val phone = TvChannelCrypto.generateKeyPair()
        val gate = CodeConfirmPairingGate(vault, session)

        val verdict = gate.authorize(fingerprintOfKey(phone), confirm = null)
        assertEquals(PairingGate.Verdict.Denied::class, verdict::class)
        assertTrue(!vault.isPeerPinned(fingerprintOfKey(phone)))
    }

    // ------------------------------------------------------------------
    // Wire-level: gate wired into TvLinkServer + TvLinkClient confirm
    // ------------------------------------------------------------------

    @Test
    fun `first pairing over a real socket authorizes and serves actions`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock)
        val gate = CodeConfirmPairingGate(vault, session)
        val outcome = AtomicReference<TvLinkServer.Outcome>()

        val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val server = TvLinkServer(EchoDispatcher(), pairingGate = gate)
        val serverThread = Thread {
            outcome.set(server.handle(serverSocket.accept()))
        }.apply { isDaemon = true; start() }

        val displayed = session.displayCode()!!
        val qr = session.qrPayload()!!
        val confirm = PairingConfirm(displayed.value, qr.nonce.value)
        val client = TvLinkClient(connectionId, pairingConfirm = confirm)

        Socket(java.net.InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
            client.connect(socket) as TvLinkClient.Result.Established
            val envelope = TvLinkProtocol.TvEnvelope(
                protocolVersion = TvLinkProtocol.PROTOCOL_VERSION,
                messageId = 7,
                connectionId = connectionId,
                deviceId = "universal:test:abs",
                action = TvLinkProtocol.encodeAction(
                    UniversalAction.Navigate(DeviceId("universal:test:abs"), Direction.Up)
                ),
                timestampMillis = 1,
                deadlineMillis = 2,
                sequenceNumber = 1,
                capabilityContext = "jvm-tv",
                authMetadata = client.serverIdentity!!.fingerprint
            )
            val response = client.sendAction(envelope)
            assertNotNull("paired link must serve actions", response)
            assertEquals(TvLinkProtocol.TvResponseState.EXECUTED, response!!.state)
            client.close(socket)
        }
        serverSocket.close()
        serverThread.join(2_000)

        assertEquals(TvLinkServer.Outcome.Clean(1), outcome.get())
        assertTrue(
            "peer (phone) must be pinned after a real pairing",
            vault.isPeerPinned(TvChannelCrypto.fingerprintOf(client.myPublicKeyBytes))
        )
    }

    @Test
    fun `peer that proves a WRONG code is refused on the wire`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = newSession(clock)
        val gate = CodeConfirmPairingGate(vault, session)
        val outcome = AtomicReference<TvLinkServer.Outcome>()

        val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getLoopbackAddress())
        val server = TvLinkServer(EchoDispatcher(), pairingGate = gate)
        val serverThread = Thread {
            outcome.set(server.handle(serverSocket.accept()))
        }.apply { isDaemon = true; start() }

        val qr = session.qrPayload()!!
        val wrongConfirm = PairingConfirm(code = "999999", nonce = qr.nonce.value)
        val client = TvLinkClient(connectionId, pairingConfirm = wrongConfirm)
        Socket(java.net.InetAddress.getLoopbackAddress(), serverSocket.localPort).use { socket ->
            val result = client.connect(socket)
            // Server sends ERROR and tears down; the client never sees CHANNEL_READY.
            assertEquals(TvLinkClient.Result.Failed::class, result::class)
            client.close(socket)
        }
        serverSocket.close()
        serverThread.join(2_000)

        val o = outcome.get()
        assertTrue(
            "server must refuse a peer that proves the wrong code (got $o)",
            o is TvLinkServer.Outcome.Failed
        )
        assertTrue(!vault.isPeerPinned(client.myPublicKeyBytes.let { TvChannelCrypto.fingerprintOf(it) }))
    }

    private fun EchoDispatcher(): TvActionDispatcher = object : TvActionDispatcher {
        override fun dispatch(
            envelope: TvLinkProtocol.TvEnvelope,
            action: UniversalAction?
        ): TvLinkProtocol.TvResponseBody = TvLinkProtocol.TvResponseBody(
            TvLinkProtocol.TvResponseState.EXECUTED,
            envelope.messageId,
            "test-ok"
        )
    }

    private companion object {
        const val connectionId = 0x5152_5354_0000_0002L
    }
}
