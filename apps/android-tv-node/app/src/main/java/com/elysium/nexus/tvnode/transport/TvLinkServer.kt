package com.elysium.nexus.tvnode.transport

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import com.elysium.nexus.tvnode.protocol.TvLinkHandshake
import com.elysium.nexus.tvnode.protocol.TvLinkProtocol
import java.net.Socket
import java.security.SecureRandom

/**
 * TvLinkServer — the TV-side "Raw server → handshake → ACTION envelopes"
 * pipeline (PR2 slice 4, §11 + §10).
 *
 * Accepts ONE socket, runs the real [TvLinkHandshake] over the wire, then
 * enters the ACTION loop: every ACTION envelope is AEAD-sealed by the phone
 * (see [TvLinkClient]), so it is authenticated + session-bound + replay-
 * guarded BEFORE the dispatcher ever sees it. Responses are sealed back to
 * the phone. Fail-closed on every malformed or unauthorized input; a clean
 * GOODBYE returns the count of served actions.
 *
 * Pure JVM: tests run it against a real loopback socket pair.
 */
class TvLinkServer(
    private val dispatcher: TvActionDispatcher,
    private val pairingGate: PairingGate,
    private val rng: SecureRandom = SecureRandom()
) {
    sealed class Outcome {
        /** Link established, handled [servedActions], then peer went away cleanly. */
        data class Clean(val servedActions: Int) : Outcome() {
            override fun toString(): String = "Clean(servedActions=$servedActions)"
        }
        /** Handshake or established-link protocol failure — nothing further served. */
        data class Failed(val reason: String) : Outcome()
    }

    /**
     * Handle one connection to completion (blocking). The caller owns the
     * socket/thread.
     *
     * @param expectedDeviceChannelAd optional override of the phone→TV AAD
     *   (defaults to the canonical one) — tests pin the canonical binding.
     */
    fun handle(socket: Socket): Outcome {
        val stream = TvFrameStream(socket.getInputStream(), socket.getOutputStream())
        val handshake = TvLinkHandshake(rng)

        return try {
            // --- Phase 1: handshake over the wire ---
            while (handshake.state == TvLinkHandshake.State.WAIT_HELLO ||
                handshake.state == TvLinkHandshake.State.CHALLENGE_SENT
            ) {
                val frame = stream.read()
                    ?: return Outcome.Failed("peer closed the socket during handshake")
                when (val r = handshake.onFrame(frame.type, frame.payload)) {
                    is TvLinkHandshake.Result.Send -> stream.send(r.frameType, r.payload)
                    is TvLinkHandshake.Result.Established -> {
                        when (val gateVerdict = runPairingGate(socket, stream, handshake, r.channelKeys)) {
                            is PairingGate.Verdict.Authorized -> {
                                stream.send(TvLinkProtocol.FrameType.CHANNEL_READY)
                                return handleActions(stream, handshake, r.channelKeys)
                            }
                            is PairingGate.Verdict.Denied -> {
                                stream.send(
                                    TvLinkProtocol.FrameType.ERROR,
                                    gateVerdict.reason.toByteArray(Charsets.UTF_8)
                                )
                                return Outcome.Failed(gateVerdict.reason)
                            }
                        }
                    }
                    is TvLinkHandshake.Result.Failed -> {
                        stream.send(
                            TvLinkProtocol.FrameType.ERROR,
                            r.reason.toByteArray(Charsets.UTF_8)
                        )
                        return Outcome.Failed(r.reason)
                    }
                }
            }
            Outcome.Failed("handshake ended unexpectedly")
        } finally {
            // Fail-closed teardown: the socket is ALWAYS released, whether the
            // link ended clean or hard (§10 disconnection must neutralize).
            runCatching { socket.close() }
        }
    }

    private val adPhoneToTv: ByteArray
        get() = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.PHONE_TO_TV)
    private val adTvToPhone: ByteArray
        get() = TvChannelCrypto.channelAd(TvChannelCrypto.NonceDomain.TV_TO_PHONE)

    /**
     * PR2 slice 5 (§10 + Master Order v0.10 Phase 16): after the AEAD
     * possession proof, the server demands ONE more sealed frame: PAIR_CONFIRM.
     * Only a peer holding the derived channel keys can craft it (it decrypts
     * under the RX key), so the pairing code + QR nonce inside are
     * authenticated end-to-end before CHANNEL_READY is ever emitted.
     *
     * The `pairingGate` is MANDATORY (non-null constructor): no production
     * server can accidentally open without authorization. An allow-all gate
     * exists ONLY in the test/debug source sets, never in production.
     *
     * Fail-closed on silence : a gated server waits a bounded [PAIR_CONFIRM_TIMEOUT]
     * for the frame; a peer that never proves the code times out and is torn
     * down, never left hanging in a half-open state.
     */
    private fun runPairingGate(
        socket: Socket,
        stream: TvFrameStream,
        handshake: TvLinkHandshake,
        keys: TvChannelCrypto.ChannelKeys
    ): PairingGate.Verdict {
        // Bounded wait: fail-closed on silence, never a half-open hang (§10).
        socket.soTimeout = PAIR_CONFIRM_TIMEOUT
        return try {
            stepPairingConfirm(stream, handshake, keys, pairingGate)
        } finally {
            socket.soTimeout = 0
        }
    }

    private fun stepPairingConfirm(
        stream: TvFrameStream,
        handshake: TvLinkHandshake,
        keys: TvChannelCrypto.ChannelKeys,
        gate: PairingGate
    ): PairingGate.Verdict {
        val confirmFrame = try {
            stream.read()
        } catch (e: java.net.SocketTimeoutException) {
            return PairingGate.Verdict.Denied("timed out waiting for PAIR_CONFIRM")
        } ?: return PairingGate.Verdict.Denied("peer closed the socket during pairing confirm")
        if (confirmFrame.type != TvLinkProtocol.FrameType.PAIR_CONFIRM) {
            return PairingGate.Verdict.Denied("expected PAIR_CONFIRM, got ${confirmFrame.type}")
        }
        // Authenticate + decrypt under the RX channel key FIRST (possession proof).
        val plain = try {
            keys.decryptFromPeer(confirmFrame.payload, adPhoneToTv)
        } catch (e: Exception) {
            return PairingGate.Verdict.Denied("PAIR_CONFIRM failed authentication: ${e.message}")
        }
        val confirm = PairingConfirm.parse(plain)
            ?: return PairingGate.Verdict.Denied("PAIR_CONFIRM payload malformed")
        return gate.authorize(handshake.peerIdentity, confirm)
    }

    private fun failAction(stream: TvFrameStream, message: String): Outcome {
        stream.send(
            TvLinkProtocol.FrameType.ERROR,
            message.toByteArray(Charsets.UTF_8)
        )
        return Outcome.Failed(message)
    }

    private fun handleActions(
        stream: TvFrameStream,
        handshake: TvLinkHandshake,
        keys: TvChannelCrypto.ChannelKeys
    ): Outcome {
        var served = 0
        while (true) {
            val frame = stream.read()
                ?: return Outcome.Clean(served)
            when (frame.type) {
                TvLinkProtocol.FrameType.GOODBYE -> return Outcome.Clean(served)
                TvLinkProtocol.FrameType.HEARTBEAT -> stream.send(TvLinkProtocol.FrameType.HEARTBEAT)
                TvLinkProtocol.FrameType.ACTION -> {
                    val envelopeBytes = try {
                        keys.decryptFromPeer(frame.payload, adPhoneToTv)
                    } catch (e: Exception) {
                        return failAction(stream, "ACTION failed authentication: ${e.message}")
                    }
                    val envelope = TvLinkProtocol.decodeEnvelope(envelopeBytes)
                    if (envelope == null) {
                        return failAction(stream, "ACTION envelope undecodable")
                    }
                    if (envelope.connectionId != handshake.connectionId) {
                        return failAction(
                            stream,
                            "connectionId mismatch: session ${handshake.connectionId}, envelope ${envelope.connectionId}"
                        )
                    }
                    val action = TvLinkProtocol.decodeAction(envelope.action, envelope.deviceId)
                    // The dispatcher OWNS the answer for every code, including
                    // forward-compat codes the local tree cannot build and
                    // probes like OBSERVE_VOLUME (Phase 25 oracle lane). A
                    // plain executor answers UNSUPPORTED — never a silent drop.
                    val response = dispatcher.dispatch(envelope, action)
                    stream.send(
                        TvLinkProtocol.FrameType.RESPONSE,
                        keys.encryptToPeer(
                            TvLinkProtocol.encodeResponseBody(response),
                            adTvToPhone
                        )
                    )
                    served++
                }
                else -> {
                    return failAction(stream, "unexpected ${frame.type} on established link")
                }
            }
        }
    }

    companion object {
        /** Bounded wait for the PAIR_CONFIRM frame before CHANNEL_READY (slice 5). */
        const val PAIR_CONFIRM_TIMEOUT: Int = 10_000
    }
}
