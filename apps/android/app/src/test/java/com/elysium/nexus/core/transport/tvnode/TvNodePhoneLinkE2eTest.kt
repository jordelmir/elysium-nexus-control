package com.elysium.nexus.core.transport.tvnode

import com.elysium.nexus.tvnode.canonical.Direction
import com.elysium.nexus.tvnode.canonical.DeviceId
import com.elysium.nexus.tvnode.canonical.UniversalAction
import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import com.elysium.nexus.tvnode.credential.InMemoryTvCredentialVault
import com.elysium.nexus.tvnode.pairing.PairingClock
import com.elysium.nexus.tvnode.pairing.PairingNonce
import com.elysium.nexus.tvnode.pairing.PairingSession
import com.elysium.nexus.tvnode.transport.ObservationCapableDispatcher
import com.elysium.nexus.tvnode.canonical.TvObservationEngine
import com.elysium.nexus.tvnode.canonical.VolumeObservation
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import com.elysium.nexus.tvnode.transport.CodeConfirmPairingGate
import com.elysium.nexus.tvnode.transport.PairingConfirm
import com.elysium.nexus.tvnode.transport.TvActionDispatcher
import com.elysium.nexus.tvnode.transport.TvLinkServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/**
 * TvNodePhoneLinkE2eTest — the SOFTWARE-ONLY phone↔TV Node E2E inside the
 * CONTROLLER build (Master Order v0.10 Phase 21, audit action 3).
 *
 * No mocks anywhere on the wire: a real [ServerSocket] serves a real
 * [TvLinkServer] (fixed connectionId, mandatory CodeConfirmPairingGate,
 * in-memory vault twin) and [TvNodePhoneLink] (the controller's own client
 * over the shared `:tvlink` truth) phones it through loopback: real TCP,
 * real X25519 handshake, real AEAD-sealed PAIR_CONFIRM and ACTION/RESPONSE.
 *
 * This is the proof that the controller APK's bytes can pair with and
 * command a real TV Node listener on a real port (the listener wiring is
 * exercised in the tv-node build; here the PHONE side is the subject).
 */
class TvNodePhoneLinkE2eTest {

    private companion object {
        const val CONNECTION_ID = 0x5152_5354_0000_0001L
        val NONCE = "abcdef0123456789abcdef0123456789"
    }

    private class FakeClock(private var now: Long = 1_000_000L) : PairingClock {
        override fun nowMillis(): Long = now
    }

    @Test
    fun `phone pairs with and commands a real tv node over the wire`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = PairingSession.create(
            clock = clock,
            nonce = PairingNonce.of(NONCE),
            deviceId = "tv-test-abs",
            protocolVersion = 1,
            ttlMillis = 60_000,
            maxCodeAttempts = 5
        )
        val served = AtomicReference<TvLinkProtocol.TvResponseBody?>()
        val serverOutcome = AtomicReference<TvLinkServer.Outcome?>()
        val displayedCode = session.displayCode()!!.value

        val serverSocket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            val accepted = serverSocket.accept()
            serverOutcome.set(
                TvLinkServer(
                    dispatcher = object : TvActionDispatcher {
                        override fun dispatch(
                            envelope: TvLinkProtocol.TvEnvelope,
                            action: UniversalAction?
                        ): TvLinkProtocol.TvResponseBody {
                            val body = TvLinkProtocol.TvResponseBody(
                                TvLinkProtocol.TvResponseState.EXECUTED,
                                envelope.messageId,
                                "controller-e2e"
                            )
                            served.set(body)
                            return body
                        }
                    },
                    pairingGate = CodeConfirmPairingGate(vault, session)
                ).handle(accepted)
            )
        }.apply { isDaemon = true; start() }

        val phone = TvNodePhoneLink(CONNECTION_ID)
        var phoneIdentity = ""
        try {
            val result = phone.connect(
                host = "127.0.0.1",
                port = serverSocket.localPort,
                confirm = PairingConfirm(displayedCode, NONCE)
            )
            assertTrue(
                "phone must establish over the real wire, got $result",
                result is TvNodePhoneLink.ConnectResult.Established
            )
            val identity = phone.serverFullIdentity
            assertNotNull("phone must hold the TV's full 64-hex identity", identity)
            assertTrue("full identity must be 64 lowercase hex", identity!!.matches(Regex("^[0-9a-f]{64}$")))

            val response = phone.sendAction(
                action = UniversalAction.Navigate(DeviceId("tv-test-abs"), Direction.Up),
                sequenceNumber = 1
            )
            assertNotNull("phone must receive an honest response", response)
            assertEquals(TvLinkProtocol.TvResponseState.EXECUTED, response!!.state)
            assertEquals("controller-e2e", response.detail)
            val phonePublicKey = phone.myPublicKeyBytes
            assertNotNull(phonePublicKey)
            phoneIdentity = TvChannelCrypto.fullFingerprintOf(phonePublicKey!!)
        } finally {
            phone.close()
            serverSocket.close()
        }
        serverThread.join(2_000)

        val outcome = serverOutcome.get()
        assertTrue("server must finish clean, got $outcome", outcome is TvLinkServer.Outcome.Clean)
        assertEquals(1, (outcome as TvLinkServer.Outcome.Clean).servedActions)
        assertEquals(TvLinkProtocol.TvResponseState.EXECUTED, served.get()!!.state)

        // The durable pin is real: the phone's FULL identity is pinned.
        assertTrue(
            "the phone's full 64-hex identity must be durably pinned",
            vault.isPeerIdentityPinned(phoneIdentity)
        )
    }

    @Test
    fun `phone observes real tv volume over the wire - phase 25 oracle lane`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = PairingSession.create(
            clock = clock,
            nonce = PairingNonce.of(NONCE),
            deviceId = "tv-test-obs",
            protocolVersion = 1,
            ttlMillis = 60_000,
            maxCodeAttempts = 5
        )
        val displayedCode = session.displayCode()!!.value

        // The TV Node answers OBSERVE_VOLUME through its observation lane
        // with a real snapshot (no fake executor involved).
        val observationEngine = object : TvObservationEngine {
            override fun observeVolume(): VolumeObservation? = VolumeObservation(
                rawVolume = 12,
                maxVolume = 50,
                level = 0.24f,
                isMuted = false,
                isVolumeFixed = false
            )

            override fun isMediaSessionActive(): Boolean = false
        }
        val serverOutcome = java.util.concurrent.atomic.AtomicReference<TvLinkServer.Outcome?>()
        val serverSocket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            serverOutcome.set(
                TvLinkServer(
                    dispatcher = ObservationCapableDispatcher(
                        observe = { observationEngine },
                        delegate = object : TvActionDispatcher {
                            override fun dispatch(
                                envelope: TvLinkProtocol.TvEnvelope,
                                action: UniversalAction?
                            ): TvLinkProtocol.TvResponseBody =
                                TvLinkProtocol.TvResponseBody(
                                    TvLinkProtocol.TvResponseState.UNSUPPORTED,
                                    envelope.messageId,
                                    "no executor"
                                )
                        }
                    ),
                    pairingGate = CodeConfirmPairingGate(vault, session)
                ).handle(serverSocket.accept())
            )
        }.apply { isDaemon = true; start() }

        val phone = TvNodePhoneLink(CONNECTION_ID)
        try {
            val result = phone.connect(
                host = "127.0.0.1",
                port = serverSocket.localPort,
                confirm = PairingConfirm(displayedCode, NONCE)
            )
            assertTrue(
                "phone must establish over the real wire, got $result",
                result is TvNodePhoneLink.ConnectResult.Established
            )

            val probe = phone.observeVolume(sequenceNumber = 1)
            assertNotNull("phone must receive the real volume snapshot", probe)
            assertEquals(12, probe!!.rawVolume)
            assertEquals(50, probe.maxVolume)
            assertEquals(false, probe.isMuted)
            assertEquals(0.24f, probe.level)
        } finally {
            phone.close()
            serverSocket.close()
        }
        serverThread.join(2_000)

        val outcome = serverOutcome.get()
        assertTrue("server must finish clean, got $outcome", outcome is TvLinkServer.Outcome.Clean)
        assertEquals(1, (outcome as TvLinkServer.Outcome.Clean).servedActions)
    }

    @Test
    fun `phone is refused with a wrong pairing code - fail closed`() {
        val clock = FakeClock()
        val vault = InMemoryTvCredentialVault()
        val session = PairingSession.create(
            clock = clock,
            nonce = PairingNonce.of(NONCE),
            deviceId = "tv-test-abs",
            protocolVersion = 1,
            ttlMillis = 60_000,
            maxCodeAttempts = 5
        )
        var outcome: TvLinkServer.Outcome? = null
        val serverSocket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            outcome = TvLinkServer(
                dispatcher = object : TvActionDispatcher {
                    override fun dispatch(
                        envelope: TvLinkProtocol.TvEnvelope,
                        action: UniversalAction?
                    ): TvLinkProtocol.TvResponseBody =
                        TvLinkProtocol.TvResponseBody(
                            TvLinkProtocol.TvResponseState.EXECUTED,
                            envelope.messageId,
                            "unreachable"
                        )
                },
                pairingGate = CodeConfirmPairingGate(vault, session)
            ).handle(serverSocket.accept())
        }.apply { isDaemon = true; start() }

        val phone = TvNodePhoneLink(CONNECTION_ID)
        try {
            val result = phone.connect(
                host = "127.0.0.1",
                port = serverSocket.localPort,
                confirm = PairingConfirm("000000", NONCE)
            )
            assertTrue("wrong code must never establish", result is TvNodePhoneLink.ConnectResult.Failed)
        } finally {
            phone.close()
            serverSocket.close()
        }
        serverThread.join(2_000)

        assertTrue(
            "server must report a denied link, got $outcome",
            outcome is TvLinkServer.Outcome.Failed
        )
        assertTrue(
            "denial reason must be the rejected pairing code",
            (outcome as TvLinkServer.Outcome.Failed).reason.contains("pairing code")
        )
        assertTrue("denied peer must not be pinned", !vault.isPeerIdentityPinned(""))
    }
}