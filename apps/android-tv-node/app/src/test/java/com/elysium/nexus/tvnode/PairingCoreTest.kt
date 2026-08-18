package com.elysium.nexus.tvnode.pairing

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock(var now: Long = 1_000_000L) : PairingClock {
    override fun nowMillis(): Long = now
}

class PairingSessionTest {

    /** A phone-side key pair, simulating the controller's mirror. */
    private fun phoneSideKeys(): TvChannelCrypto.KeyPair = TvChannelCrypto.generateKeyPair()

    @Test
    fun `open session shows QR with the real pinned fingerprint and nonce`() {
        val clock = FakeClock()
        val nonce = PairingNonce.generate()
        val session = PairingSession.create(clock, nonce, "universal:test:abs")
        val qr = session.qrPayload()
        assertEquals("universal:test:abs", qr?.deviceId)
        assertEquals(TvChannelCrypto.fingerprintOf(session.myPublicKeyBytes), qr?.pubKeyFingerprint)
        assertEquals(8, qr?.pubKeyFingerprint?.length)
        assertEquals(nonce, qr?.nonce)
        assertTrue(qr!!.encode().startsWith("elysium-pairing|v1|universal:test:abs|"))
    }

    @Test
    fun `QR payload parses back exactly`() {
        val qr = QrPairingPayload(1, "universal:test:abs", PairingNonce.of("0123456789abcdef0123456789abcdef"), "a1b2c3d4")
        val parsed = QrPairingPayload.parse(qr.encode())
        assertEquals(qr, parsed)
    }

    @Test
    fun `malformed QR is rejected and never advances`() {
        assertNull(QrPairingPayload.parse("garbage"))
        assertNull(QrPairingPayload.parse("elysium-pairing|v1|x|bad|bad"))
        assertNull(QrPairingPayload.parse("elysium-pairing|v999|x|ffffffffffffffff|a1b2c3d4"))
    }

    @Test
    fun `wrong code consumes attempts and fails closed at limit`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test", maxCodeAttempts = 3)
        assertEquals(PairingSession.State.Open, session.verifyCode("000000"))
        assertEquals(1, session.codeAttempts)
        assertEquals(PairingSession.State.Open, session.verifyCode("111111"))
        assertEquals(PairingSession.State.Failed, session.verifyCode("222222"))
        assertEquals(3, session.codeAttempts)
        assertEquals(PairingSession.State.Failed, session.verifyCode("999999"))
        assertNull(session.qrPayload())
    }

    @Test
    fun `malformed input still consumes an attempt`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test", maxCodeAttempts = 2)
        session.verifyCode(null)
        session.verifyCode("")
        assertEquals(PairingSession.State.Failed, session.state)
    }

    @Test
    fun `correct code advances to verified then to established with the channel bound`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test")
        val displayed = session.displayCode()
        assertTrue(session.state is PairingSession.State.Open)

        assertEquals(PairingSession.State.CodeVerified, session.verifyCode(displayed?.value))

        assertNull(session.displayCode())
        assertEquals(PairingSession.State.CodeVerified, session.verifyCode(displayed?.value))

        val phone = phoneSideKeys()
        assertEquals(PairingSession.State.Established, session.bindChannel(phone.publicKeyBytes))
        assertNotNull(session.channelKeys)
        // The TV's receive key matches the phone's transmit key (directional twin).
        val phoneKeys = TvChannelCrypto.deriveChannelKeys(phone, session.myPublicKeyBytes, TvChannelCrypto.LinkSide.PHONE)
        assertTrue(session.channelKeys!!.rxKeyBytes.contentEquals(phoneKeys.txKeyBytes))
        assertTrue(session.channelKeys!!.txKeyBytes.contentEquals(phoneKeys.rxKeyBytes))
        assertNull(session.displayCode())
    }

    @Test
    fun `expired session never reveals the code`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test", ttlMillis = 5_000)
        assertNotNull(session.displayCode())
        clock.now += 5_001
        assertNull(session.displayCode())
    }

    @Test
    fun `session expires and behaves fail-closed after ttl`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test", ttlMillis = 5_000)
        assertEquals(PairingSession.State.Open, session.state)
        clock.now += 5_001
        assertNull(session.qrPayload())
        assertEquals(PairingSession.State.Expired, session.verifyCode("000000"))
        assertEquals(PairingSession.State.Expired, session.bindChannel(phoneSideKeys().publicKeyBytes))
        assertNull(session.channelKeys)
    }

    @Test
    fun `binding requires verified code first`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test")
        assertEquals(PairingSession.State.Open, session.bindChannel(phoneSideKeys().publicKeyBytes))
        assertNull(session.channelKeys)
    }

    @Test
    fun `binding with malformed peer key fails closed without advancing`() {
        val clock = FakeClock()
        val session = PairingSession.create(clock, PairingNonce.generate(), "universal:test")
        session.verifyCode(session.displayCode()?.value)
        assertTrue(runCatching { session.bindChannel(ByteArray(16)) }.isFailure)
        assertEquals(PairingSession.State.CodeVerified, session.state)
        assertNull(session.channelKeys)
    }
}

class PairingCodeTest {

    @Test
    fun `generated code is always 6 decimal digits`() {
        repeat(200) {
            val code = PairingCode.generate()
            assertTrue(code.value.matches(Regex("^\\d{6}$")))
        }
    }

    @Test
    fun `generated codes vary`() {
        val a = PairingCode.generate()
        val b = PairingCode.generate()
        assertNotEquals(a.value, b.value)
    }

    @Test
    fun `verification is exact and timing-safe`() {
        val code = PairingCode.generate()
        assertTrue(code.matches(code.value))
        assertFalse(code.matches("000000"))
        assertFalse(code.matches(null))
        assertFalse(code.matches(code.value + "0"))
        assertFalse(code.matches(code.value.dropLast(1)))
    }

    @Test
    fun `malformed code is rejected at construction`() {
        assertTrue(runCatching { PairingCode.of("12345") }.isFailure)
        assertTrue(runCatching { PairingCode.of("1234567") }.isFailure)
        assertTrue(runCatching { PairingCode.of("abcdef") }.isFailure)
    }
}

class PairingNonceTest {

    @Test
    fun `nonce is 32 hex chars and unique`() {
        val a = PairingNonce.generate()
        val b = PairingNonce.generate()
        assertTrue(a.value.matches(Regex("^[0-9a-f]{32}$")))
        assertNotEquals(a.value, b.value)
    }

    @Test
    fun `nonce rejects truncated malformed values`() {
        assertTrue(runCatching { PairingNonce.of("abc") }.isFailure)
        assertTrue(runCatching { PairingNonce.of("0123456789ABCDEF") }.isFailure) // uppercase not accepted
    }
}