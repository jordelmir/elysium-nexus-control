package com.elysium.nexus.core.transport.tvnode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPairingFlowControllerTest {

    private val QR = "elysium-pairing|v1|universal:test:abs|abcdef0123456789abcdef0123456789|1a2b3c4d"

    private class FakeGateway(
        var resolveResult: TvPairingFlowController.DiscoveredTv? =
            TvPairingFlowController.DiscoveredTv("192.168.1.50", 43110),
        var connectResult: TvPairingFlowController.TvLinkHandle? = null,
        var confirmResult: Boolean = true,
        val identity: String = "ab".repeat(32)
    ) : TvPairingFlowController.Gateway {
        var resolveCalls = 0
        var connectCalls = 0
        var lastPort: Int? = null

        override fun resolveTv(): TvPairingFlowController.DiscoveredTv? {
            resolveCalls++
            return resolveResult
        }

        override fun connect(host: String, port: Int): TvPairingFlowController.TvLinkHandle? {
            connectCalls++
            lastPort = port
            return connectResult
        }

        fun handle() = object : TvPairingFlowController.TvLinkHandle {
            override fun confirmWithCode(code: String): Boolean = confirmResult
            override fun fullIdentity(): String = identity
        }
    }

    @Test
    fun `happy path scans resolves connects confirms and becomes ready`() {
        val gateway = FakeGateway().apply { connectResult = handle() }
        val flow = TvPairingFlowController(gateway)

        val afterScan = flow.onQrScanned(QR)
        assertTrue(afterScan is TvPairingFlowController.State.AwaitingCode)
        assertEquals("universal:test:abs", (afterScan as TvPairingFlowController.State.AwaitingCode).deviceId)

        val afterCode = flow.onCodeEntered("123456")
        assertEquals(TvPairingFlowController.State.Ready("ab".repeat(32)), afterCode)
        assertEquals(43110, gateway.lastPort)
    }

    @Test
    fun `malformed QR never advances`() {
        val gateway = FakeGateway()
        val flow = TvPairingFlowController(gateway)
        val state = flow.onQrScanned("not-a-pairing-payload")
        assertTrue(state is TvPairingFlowController.State.Failed)
        assertEquals(0, gateway.resolveCalls)
    }

    @Test
    fun `missing TV on network fails honestly`() {
        val gateway = FakeGateway(resolveResult = null)
        val flow = TvPairingFlowController(gateway)
        val state = flow.onQrScanned(QR)
        assertTrue(state is TvPairingFlowController.State.Failed)
        assertTrue((state as TvPairingFlowController.State.Failed).reason.contains("not found"))
        assertEquals(0, gateway.connectCalls)
    }

    @Test
    fun `connect failure fails honestly`() {
        val gateway = FakeGateway(connectResult = null)
        val flow = TvPairingFlowController(gateway)
        val state = flow.onQrScanned(QR)
        assertTrue(state is TvPairingFlowController.State.Failed)
        assertTrue((state as TvPairingFlowController.State.Failed).reason.contains("cannot connect"))
    }

    @Test
    fun `rejected code fails and does not pin anything`() {
        val gateway = FakeGateway().apply { connectResult = handle(); confirmResult = false }
        val flow = TvPairingFlowController(gateway)
        flow.onQrScanned(QR)
        val state = flow.onCodeEntered("000000")
        assertTrue(state is TvPairingFlowController.State.Failed)
        assertTrue((state as TvPairingFlowController.State.Failed).reason.contains("rejected"))
    }

    @Test
    fun `terminal failed state sticks`() {
        val gateway = FakeGateway(resolveResult = null)
        val flow = TvPairingFlowController(gateway)
        flow.onQrScanned(QR)
        val again = flow.onQrScanned(QR)
        assertTrue(again is TvPairingFlowController.State.Failed)
        assertEquals(1, gateway.resolveCalls) // no second attempt after failure
    }

    @Test
    fun `code format guard rejects non-six-digit input`() {
        val gateway = FakeGateway().apply { connectResult = handle() }
        val flow = TvPairingFlowController(gateway)
        flow.onQrScanned(QR)
        val state = flow.onCodeEntered("12")
        assertTrue(state is TvPairingFlowController.State.Failed)
        assertTrue((state as TvPairingFlowController.State.Failed).reason.contains("6 digits"))
    }
}