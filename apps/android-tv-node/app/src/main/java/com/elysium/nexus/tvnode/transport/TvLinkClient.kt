package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import java.net.Socket
import java.security.SecureRandom

/**
 * TvLinkClient — the PHONE-side mirror of the TV link (PR2 slice 4).
 *
 * Implements the phone half of the wire contract from `TvChannelCrypto`
 * (`LinkSide.PHONE`) byte-for-byte against [TvLinkServer]:
 *
 *   1. send HELLO           (connectionId + phone X25519 pubkey)
 *   2. read HELLO_ACK       (TV X25519 pubkey + 32-byte challenge)
 *   3. seal the challenge through the derived channel, send NONCE_ECHO_ACK
 *      (the possession proof — only a peer holding the derived keys can
 *      produce a frame that authenticates on the TV)
 *   4. read CHANNEL_READY   (link established)
 *   5. send ACTION envelopes, read sealed RESPONSE bodies.
 *
 * Everything is AEAD-sealed with the directional channel keys; nothing
 * sensitive crosses the wire in plaintext. The phone pins the TV's public
 * key via its QR fingerprint BEFORE trusting any channel key (§10).
 */
class TvLinkClient(
    private val connectionId: Long,
    private val rng: SecureRandom = SecureRandom()
) {
    /** State of the phone record after [connect] / before [open]. */
    sealed class Result {
        object Established : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Server's public key + its 8-hex fingerprint — the QR pinning target. */
    data class ServerIdentity(
        val publicKeyBytes: ByteArray,
        val fingerprint: String
    )

    private val myKeyPair = TvChannelCrypto.generateKeyPair()
    private var server: ServerIdentity? = null
    private var keys: TvChannelCrypto.ChannelKeys? = null
    private var stream: TvFrameStream? = null

    /** The phone's X25519 public key carried in HELLO. */
    val myPublicKeyBytes: ByteArray get() = myKeyPair.publicKeyBytes

    /** Server identity once known — pin this fingerprint against the QR. */
    val serverIdentity: ServerIdentity? get() = server

    /** Derived phone channel keys — only after [connect] succeeds (fail-closed). */
    val channelKeys: TvChannelCrypto.ChannelKeys? get() = keys

    // ------------------------------------------------------------------
    // Handshake
    // ------------------------------------------------------------------

    /**
     * Run the 4-step handshake over an already-open socket. Blocking. On
     * ESTABLISHED the client is ready for [sendAction].
     */
    fun connect(socket: Socket): Result {
        val s = TvFrameStream(socket.getInputStream(), socket.getOutputStream())
        stream = s
        return try {
            runHandshake(s)
        } catch (e: TvFrameStream.ProtocolException) {
            Result.Failed("wire protocol violation: ${e.message}")
        } catch (e: Exception) {
            Result.Failed("handshake failed: ${e.message}")
        }
    }

    private fun runHandshake(s: TvFrameStream): Result {
        // 1. HELLO: u64 connectionId BE + phone pubkey (mirror of handshake.parseHello).
        val hello = ByteArray(8 + 32)
        var c = connectionId
        for (i in 7 downTo 0) {
            hello[i] = (c and 0xFF).toByte()
            c = c ushr 8
        }
        System.arraycopy(myPublicKeyBytes, 0, hello, 8, 32)
        s.send(TvLinkProtocol.FrameType.HELLO, hello)

        // 2. HELLO_ACK: TV pubkey(32) + challenge(32).
        val ack = s.read()
            ?: return Result.Failed("server closed during handshake")
        if (ack.type != TvLinkProtocol.FrameType.HELLO_ACK || ack.payload.size != 64) {
            return Result.Failed("expected HELLO_ACK(64), got ${ack.type}(${ack.payload.size})")
        }
        val tvPub = ack.payload.copyOfRange(0, 32)
        val challenge = ack.payload.copyOfRange(32, 64)
        server = ServerIdentity(tvPub, TvChannelCrypto.fingerprintOf(tvPub))

        // 3. Derive channel keys, seal the challenge (possession proof).
        keys = TvChannelCrypto.deriveChannelKeys(
            myKeyPair,
            tvPub,
            TvChannelCrypto.LinkSide.PHONE
        )
        val sealed = keys!!.encryptToPeer(challenge, adPhoneToTv)
        s.send(TvLinkProtocol.FrameType.NONCE_ECHO_ACK, sealed)

        // 4. CHANNEL_READY: link established.
        val ready = s.read()
            ?: return Result.Failed("server closed during handshake (expecting CHANNEL_READY)")
        if (ready.type != TvLinkProtocol.FrameType.CHANNEL_READY) {
            return Result.Failed("expected CHANNEL_READY, got ${ready.type}")
        }
        return Result.Established
    }

    // ------------------------------------------------------------------
    // Established link
    // ------------------------------------------------------------------

    /**
     * Send one ACTION envelope and read the §11 response body. Blocking.
     *
     * @return null if the peer sent no RESPONSE (e.g. an ERROR / teardown).
     */
    fun sendAction(envelope: TvLinkProtocol.TvEnvelope): TvLinkProtocol.TvResponseBody? {
        val k = requireNotNull(keys) { "link not established" }
        val s = requireNotNull(stream)
        val sealed = k.encryptToPeer(TvLinkProtocol.encodeEnvelope(envelope), adPhoneToTv)
        s.send(TvLinkProtocol.FrameType.ACTION, sealed)
        val frame = s.read() ?: return null
        if (frame.type != TvLinkProtocol.FrameType.RESPONSE) {
            return null
        }
        return TvLinkProtocol.decodeResponseBody(k.decryptFromPeer(frame.payload, adTvToPhone))
    }

    /** Close the wire cleanly (GOODBYE) and release the socket resources. */
    fun close(socket: Socket?) {
        try {
            stream?.send(TvLinkProtocol.FrameType.GOODBYE)
        } catch (_: Exception) { /* already closed */ }
        try {
            socket?.close()
        } catch (_: Exception) { /* no-op */ }
        stream = null
        keys = null
    }

    private val adPhoneToTv: ByteArray
        get() = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV)
    private val adTvToPhone: ByteArray
        get() = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.TV_TO_PHONE)
}
