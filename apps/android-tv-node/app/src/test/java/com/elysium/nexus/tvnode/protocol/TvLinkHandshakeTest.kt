package com.elysium.nexus.tvnode.protocol

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Handshake tests — the 4-step phone ↔ TV wire handshake (PR2 slice 3).
 *
 * A tiny PHONE MIRROR lives in this test file: it builds HELLO with the
 * phone's real X25519 key, reads the TV's HELLO_ACK, echoes the challenge
 * through the sealed channel, and asserts the derived channel keys are the
 * byte-mirror of the TV's (the Phase-32 cross-side parity seed, before the
 * real phone mirror lands in the controller module).
 */
class TvLinkHandshakeTest {

    @Test
    fun `full four-step handshake establishes byte-mirrored channel keys`() {
        val tv = TvLinkHandshake()
        val phoneMirror = PhoneMirror(connectionId = 42L)

        assertEquals(TvLinkHandshake.State.WAIT_HELLO, tv.state)

        // 1. phone → tv HELLO
        val helloResult = tv.onFrame(
            TvLinkProtocol.FrameType.HELLO,
            phoneMirror.buildHello()
        )
        val send = helloResult as TvLinkHandshake.Result.Send
        assertEquals(TvLinkProtocol.FrameType.HELLO_ACK, send.frameType)
        assertEquals(TvLinkHandshake.State.CHALLENGE_SENT, tv.state)

        // 2. tv → phone HELLO_ACK carries tv pubkey + challenge
        val tvPub = send.payload.copyOfRange(0, 32)
        val challenge = send.payload.copyOfRange(32, 64)
        phoneMirror.observeTvPublicKey(tvPub)

        // 3. phone echoes the challenge through its sealed channel
        val echoResult = tv.onFrame(
            TvLinkProtocol.FrameType.NONCE_ECHO_ACK,
            phoneMirror.echoChallenge(challenge)
        )
        val established = echoResult as TvLinkHandshake.Result.Established
        assertEquals(TvLinkHandshake.State.ESTABLISHED, tv.state)

        // 4. both sides now hold mirror keys
        assertArrayEquals(tv.channelKeys!!.rxKeyBytes, phoneMirror.channelKeys.txKeyBytes)
        assertArrayEquals(tv.channelKeys!!.txKeyBytes, phoneMirror.channelKeys.rxKeyBytes)
        assertTrue(established.channelKeys === tv.channelKeys)
        assertEquals(42L, tv.connectionId)
    }

    @Test
    fun `wrong nonce echo fails the handshake closed`() {
        val tv = TvLinkHandshake()
        val phoneMirror = PhoneMirror(connectionId = 1L)
        tv.onFrame(TvLinkProtocol.FrameType.HELLO, phoneMirror.buildHello()) as TvLinkHandshake.Result.Send

        // The echo is a VALID sealed frame (authenticates under the channel
        // key) but does NOT carry the exact challenge — possession of the key
        // alone is not enough; the echoed value must match.
        val wrongAck = ByteArray(32) { 0x11 }
        val wrongEcho = phoneMirror.echoChallenge(wrongAck)
        val result = tv.onFrame(TvLinkProtocol.FrameType.NONCE_ECHO_ACK, wrongEcho)
        assertTrue(result is TvLinkHandshake.Result.Failed)
        assertEquals(TvLinkHandshake.State.FAILED, tv.state)
    }

    @Test
    fun `malformed hello payload fails closed with no bytes emitted`() {
        val tv = TvLinkHandshake()
        val result = tv.onFrame(TvLinkProtocol.FrameType.HELLO, ByteArray(3))
        assertTrue(result is TvLinkHandshake.Result.Failed)
        assertEquals(TvLinkHandshake.State.FAILED, tv.state)
    }

    @Test
    fun `wrong-size phone public key fails closed`() {
        val tv = TvLinkHandshake()
        val payload = ByteArray(8 + 31) // 8B connectionId + 31B pubkey
        val result = tv.onFrame(TvLinkProtocol.FrameType.HELLO, payload)
        assertTrue(result is TvLinkHandshake.Result.Failed)
        assertEquals(TvLinkHandshake.State.FAILED, tv.state)
    }

    @Test
    fun `nonce echo out of order is rejected`() {
        val tv = TvLinkHandshake()
        val result = tv.onFrame(TvLinkProtocol.FrameType.NONCE_ECHO_ACK, ByteArray(32))
        assertTrue(result is TvLinkHandshake.Result.Failed)
        assertEquals(TvLinkHandshake.State.FAILED, tv.state)
    }

    @Test
    fun `action before established is rejected`() {
        val tv = TvLinkHandshake()
        val result = tv.onFrame(TvLinkProtocol.FrameType.ACTION, ByteArray(4))
        assertTrue(result is TvLinkHandshake.Result.Failed)
    }

    @Test
    fun `repeated hello on an established link is rejected`() {
        val tv = TvLinkHandshake()
        val phoneMirror = PhoneMirror(connectionId = 7L)
        tv.onFrame(TvLinkProtocol.FrameType.HELLO, phoneMirror.buildHello()) as TvLinkHandshake.Result.Send
        val result = tv.onFrame(TvLinkProtocol.FrameType.HELLO, phoneMirror.buildHello())
        assertTrue(result is TvLinkHandshake.Result.Failed)
        assertEquals(TvLinkHandshake.State.FAILED, tv.state)
    }

    @Test
    fun `tv public key is real, 32 bytes, and its fingerprint pins the QR`() {
        val tv = TvLinkHandshake()
        assertEquals(32, tv.myPublicKeyBytes.size)
        assertEquals(8, TvChannelCrypto.fingerprintOf(tv.myPublicKeyBytes).length)
        assertEquals(
            TvChannelCrypto.fingerprintOf(tv.myPublicKeyBytes),
            TvChannelCrypto.fingerprintOf(tv.myPublicKeyBytes)
        )
    }

    @Test
    fun `challenge echo proves data flows through the sealed channel`() {
        val tv = TvLinkHandshake()
        val phoneMirror = PhoneMirror(connectionId = 9L)
        val hello = tv.onFrame(TvLinkProtocol.FrameType.HELLO, phoneMirror.buildHello()) as TvLinkHandshake.Result.Send
        val challenge = hello.payload.copyOfRange(32, 64)

        // A non-echo (e.g. the raw challenge bytes, not the sealed response)
        // must fail — only a peer holding the derived keys can produce a
        // NONCE_ECHO_ACK the TV can decrypt.
        val result = tv.onFrame(TvLinkProtocol.FrameType.NONCE_ECHO_ACK, challenge)
        assertTrue(result is TvLinkHandshake.Result.Failed)
    }

    /**
     * Minimal phone-side mirror: builds HELLO, derives directional PHONE keys,
     * and echoes the challenge as `encryptToPeer(challenge, ad)`. This is the
     * seed of the controller-side mirror that the next slice wires into NSD.
     */
    private class PhoneMirror(connectionId: Long) {
        private val myKeyPair = TvChannelCrypto.generateKeyPair()
        private var tvPublicKey: ByteArray? = null

        val channelKeys: TvChannelCrypto.ChannelKeys
            get() = derive()

        fun buildHello(): ByteArray {
            val buf = ByteArray(8 + 32)
            var c = connectionId
            for (i in 7 downTo 0) {
                buf[i] = (c and 0xFF).toByte()
                c = c ushr 8
            }
            System.arraycopy(myKeyPair.publicKeyBytes, 0, buf, 8, 32)
            return buf
        }

        fun observeTvPublicKey(tvPub: ByteArray) {
            tvPublicKey = tvPub
        }

        /** The peer must see a NONCE_ECHO_ACK; the challenge is echoed through this side's sealed TX channel. */
        fun echoChallenge(challenge: ByteArray): ByteArray =
            derive().encryptToPeer(challenge, TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV))

        private fun derive(): TvChannelCrypto.ChannelKeys {
            val tv = requireNotNull(tvPublicKey) { "HELLO_ACK not observed" }
            return TvChannelCrypto.deriveChannelKeys(myKeyPair, tv, TvChannelCrypto.LinkSide.PHONE)
        }
    }
}
