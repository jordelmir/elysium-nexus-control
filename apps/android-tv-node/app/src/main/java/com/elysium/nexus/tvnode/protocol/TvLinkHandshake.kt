package com.elysium.nexus.tvnode.protocol

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * TvLinkHandshake — the phone ↔ TV Node pairing handshake over the wire
 * (PR2 slice 3, §10 + §11).
 *
 * The QR/code has already proven, out-of-band, that the phone saw the TV
 * screen and knows the 6-digit code; the authenticated channel (slice 2)
 * derives directional X25519/HKDF/ChaCha20 keys. THIS object frames that
 * binding into wire messages and adds possession proof:
 *
 *   1. PHONE → TV  HELLO          (connectionId + phone X25519 pubkey)
 *   2. TV → PHONE  HELLO_ACK      (TV X25519 pubkey + random challenge)
 *   3. PHONE → TV  NONCE_ECHO_ACK (challenge echoed through the sealed
 *                                  channel — proves the phone owns the
 *                                  derived channel keys, not just the code)
 *   4. TV → PHONE  CHANNEL_READY  (link established; ACTION frames follow)
 *
 * Step 3 is the possession proof (challenge/response): the TV only
 * transitions to ESTABLISHED after it AUTHENTICATES a NONCE_ECHO_ACK — i.e.
 * the payload the peer sends back must decrypt under the derived RX channel
 * key AND carry the exact challenge it issued. A peer that merely relays the
 * code cannot produce a frame that authenticates, so the echo proves real
 * possession of the channel keys. Fail-closed on every malformed/unknown
 * input.
 */
class TvLinkHandshake(
    private val rng: SecureRandom = SecureRandom(),
    private val adPhoneToTv: () -> ByteArray = {
        TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV)
    }
) {
    enum class State {
        WAIT_HELLO,
        CHALLENGE_SENT,
        ESTABLISHED,
        FAILED
    }

    /** Outcome of feeding one inbound handshake frame. */
    sealed class Result {
        /** Continue the handshake; the returned bytes frame is the reply to send. */
        data class Send(val frameType: TvLinkProtocol.FrameType, val payload: ByteArray) : Result()

        /** Handshake finished; the link is established with these channel keys. */
        data class Established(val channelKeys: TvChannelCrypto.ChannelKeys) : Result()

        /** Handshake ended in a hard failure — the link must be torn down. */
        data class Failed(val reason: String) : Result()
    }

    var state: State = State.WAIT_HELLO
        private set

    var connectionId: Long = -1
        private set

    private var keys: TvChannelCrypto.ChannelKeys? = null

    /** The peer's X25519 public key (HELLO). Retained for fingerprint pinning. */
    var peerPublicKeyBytes: ByteArray = ByteArray(0)
        private set

    /** 8-hex SHA-256 fingerprint of the peer's X25519 public key (§10 pinning). */
    val peerFingerprint: String
        get() = if (peerPublicKeyBytes.size == 32) {
            TvChannelCrypto.fingerprintOf(peerPublicKeyBytes)
        } else {
            ""
        }

    /** Directional channel keys — ONLY after ESTABLISHED; null before (fail-closed). */
    val channelKeys: TvChannelCrypto.ChannelKeys?
        get() = if (state == State.ESTABLISHED) keys else null

    private var challenge: ByteArray = ByteArray(0)
    private val myKeyPair = TvChannelCrypto.generateKeyPair()

    /** The TV's public key for this handshake — pinned by the QR fingerprint. */
    val myPublicKeyBytes: ByteArray get() = myKeyPair.publicKeyBytes

    /**
     * FEED ONE INBOUND FRAME. The caller passes the RAW wire payload of a
     * handshake frame together with its type. The NONCE_ECHO_ACK is still
     * AEAD-sealed on the wire: this object authenticates and decrypts it with
     * the derived RX key before comparing the echoed challenge — that step
     * IS the possession proof.
     */
    fun onFrame(type: TvLinkProtocol.FrameType, payload: ByteArray): Result = when (type) {
        TvLinkProtocol.FrameType.HELLO -> onHello(payload)
        TvLinkProtocol.FrameType.NONCE_ECHO_ACK -> onNonceEchoAck(payload)
        else -> fail("unexpected frame $type in state $state")
    }

    private fun onHello(payload: ByteArray): Result {
        if (state != State.WAIT_HELLO) return fail("HELLO received in state $state")
        val parts = parseHello(payload) ?: return fail("malformed HELLO payload (${payload.size} bytes)")
        connectionId = parts.first
        peerPublicKeyBytes = parts.second
        val peerPublic = parts.second
        // Derive channel keys BEFORE sending anything challenge-worthy: a
        // wrong-size peer key must fail closed with no bytes emitted.
        keys = TvChannelCrypto.deriveChannelKeys(
            myKeyPair,
            peerPublic,
            TvChannelCrypto.LinkSide.TV
        )
        challenge = ByteArray(32)
        rng.nextBytes(challenge)
        state = State.CHALLENGE_SENT
        val ack = ByteArray(32 + 32)
        System.arraycopy(myPublicKeyBytes, 0, ack, 0, 32)
        System.arraycopy(challenge, 0, ack, 32, 32)
        return Result.Send(TvLinkProtocol.FrameType.HELLO_ACK, ack)
    }

    private fun onNonceEchoAck(payload: ByteArray): Result {
        if (state != State.CHALLENGE_SENT) return fail("NONCE_ECHO_ACK received in state $state")
        val establishedKeys = requireNotNull(keys)
        // Authenticate + decrypt under the RX channel key FIRST. Any frame
        // that fails AEAD here is rejected before the echo is even compared:
        // that is the possession proof — only a peer holding the derived keys
        // can produce a valid ACK.
        val echoed = try {
            establishedKeys.decryptFromPeer(payload, adPhoneToTv())
        } catch (e: Exception) {
            return fail("NONCE_ECHO_ACK failed authentication — possession proof failed")
        }
        if (!constantTimeEquals(challenge, echoed)) {
            return fail("nonce echo mismatch — possession proof failed")
        }
        state = State.ESTABLISHED
        challenge = ByteArray(0)
        return Result.Established(establishedKeys)
    }

    // ------------------------------------------------------------------
    // HELLO payload: u64 connectionId + 32-byte phone X25519 pubkey
    // ------------------------------------------------------------------

    private fun parseHello(payload: ByteArray): Pair<Long, ByteArray>? {
        if (payload.size != 8 + 32) return null
        var connId = 0L
        for (i in 0 until 8) connId = (connId shl 8) or (payload[i].toLong() and 0xFF)
        val pub = payload.copyOfRange(8, 40)
        return connId to pub
    }

    private fun fail(reason: String): Result {
        state = State.FAILED
        return Result.Failed(reason)
    }

    companion object {
        /** Constant-time compare — challenge comparison must not leak timing. */
        fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
            MessageDigest.isEqual(a, b)
    }
}
