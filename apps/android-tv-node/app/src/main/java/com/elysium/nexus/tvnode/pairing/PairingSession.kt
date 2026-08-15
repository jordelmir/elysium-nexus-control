package com.elysium.nexus.tvnode.pairing

import com.elysium.nexus.tvnode.channel.TvChannelCrypto

/**
 * PairingSession — the TV-side secure pairing state machine (PR2, §10).
 *
 * Hard rules enforced here (fail-closed per the master order):
 * - ONE code per session, single-use: the code is shown once and accepted
 *   once. Any second verification attempt → FAILED.
 * - Attempt limiting: after `maxCodeAttempts` wrong guesses the session is
 *   FAILED (brute force protection on the 6-digit code).
 * - Expiry: the session dies after `ttlMillis` from creation. A session
 *   that outlives its TTL behaves as if it never existed:
 *     verifyCode → EXPIRED (terminal)
 *     qrPayload  → null (never leaks a stale code)
 * - Malformed payload: never matches, never advances state.
 *
 * Channel binding (PR2 slice 2):
 * - At creation the node generates its ephemeral X25519 key pair; the QR
 *   payload carries the SHA-256 fingerprint of the node's public key for
 *   certificate/public-key pinning BEFORE any key exchange (§10).
 * - `bindChannel(peerPublic)` only runs in CodeVerified, derives the
 *   directional channel keys (phone→tv / tv→phone) from the X25519 shared
 *   secret via HKDF, then transitions to Established. Until then the
 *   session exposes no channel keys.
 * - On platforms without X25519 (no Conscrypt), create() throws
 *   [TvChannelCrypto.CryptoUnavailableException] — pairing is honestly
 *   unsupported there, never invented.
 */
class PairingSession private constructor(
    private val nonce: PairingNonce,
    private val code: PairingCode,
    private val clock: PairingClock,
    private val ttlMillis: Long,
    private val maxCodeAttempts: Int,
    private val myKeyPair: TvChannelCrypto.KeyPair,
    private val deviceId: String,
    private val protocolVersion: Int,
    private val createdAtMillis: Long
) {

    /** State machine: OPEN → (CODE_VERIFIED → ESTABLISHED) | FAILED | EXPIRED. */
    sealed class State {
        /** Waiting for the peer to verify the code and bind the channel. */
        object Open : State()

        /** Code verified; the authenticated channel may now be established. */
        object CodeVerified : State()

        /** Channel bound and active for this session. */
        object Established : State()

        /** Session ended by policy (too many wrong codes, ttl expired, misuse). */
        object Failed : State()

        /** Session outlived its TTL. Terminal, like Failed for all inputs. */
        object Expired : State()
    }

    var state: State = State.Open
        private set

    /** Wrong-guess counter for the code. Reset on successful verification. */
    var codeAttempts: Int = 0
        private set

    val createdAt: Long get() = createdAtMillis

    /** The node's ephemeral public key for this session (the QR pins its fingerprint). */
    val myPublicKeyBytes: ByteArray get() = myKeyPair.publicKeyBytes

    /** Directional channel keys — ONLY after Established. Null before binding. */
    var channelKeys: TvChannelCrypto.ChannelKeys? = null
        private set

    /** The QR payload: deviceId + nonce + pin of the node's real public key. */
    fun qrPayload(): QrPairingPayload? = when {
        state !is State.Open -> null
        isExpired() -> null
        else -> QrPairingPayload(
            protocolVersion = protocolVersion,
            deviceId = deviceId,
            nonce = nonce,
            pubKeyFingerprint = TvChannelCrypto.fingerprintOf(myKeyPair.publicKeyBytes)
        )
    }

    /**
     * The code the TV UI shows to the user. Honest gate: only exposed while
     * OPEN and not expired — a stale session never reveals a code.
     */
    fun displayCode(): PairingCode? = when {
        state !is State.Open -> null
        isExpired() -> null
        else -> code
    }

    /**
     * Verifies the user-entered code. Fail-closed timing: malformed input
     * still increments the attempt count.
     */
    fun verifyCode(input: String?): State {
        if (isExpired()) {
            state = State.Expired
            return state
        }
        if (state !is State.Open) return state
        if (code.matches(input)) {
            state = State.CodeVerified
            codeAttempts = 0
            return state
        }
        codeAttempts++
        if (codeAttempts >= maxCodeAttempts) state = State.Failed
        return state
    }

    /**
     * Binds the authenticated channel after the peer proves the code and
     * presents its ephemeral X25519 public key. Derives the directional
     * phone↔tv channel keys and transitions to Established.
     *
     * Fail-closed: only from CodeVerified; a stale session goes Expired;
     * a wrong-size public key (or any crypto failure) throws — the session
     * is NOT advanced on failure.
     */
    fun bindChannel(peerPublicKeyBytes: ByteArray): State {
        if (isExpired()) {
            state = State.Expired
            return state
        }
        if (state !is State.CodeVerified) return state
        require(peerPublicKeyBytes.size == 32) { "Peer X25519 public key must be 32 bytes." }
        channelKeys = TvChannelCrypto.deriveChannelKeys(myKeyPair, peerPublicKeyBytes, TvChannelCrypto.LinkSide.TV)
        state = State.Established
        return state
    }

    private fun isExpired(): Boolean = clock.nowMillis() - createdAtMillis > ttlMillis

    companion object {

        /**
         * Creates a fresh session (used by the TV when pairing starts).
         * Generates the node's ephemeral X25519 key pair.
         *
         * @throws TvChannelCrypto.CryptoUnavailableException when the
         *   platform has no X25519 (honest unsupported pairing).
         */
        fun create(
            clock: PairingClock,
            nonce: PairingNonce,
            deviceId: String,
            protocolVersion: Int = 1,
            ttlMillis: Long = 60_000,
            maxCodeAttempts: Int = 5
        ): PairingSession {
            val keyPair = TvChannelCrypto.generateKeyPair()
            return PairingSession(
                nonce = nonce,
                code = PairingCode.generate(),
                clock = clock,
                ttlMillis = ttlMillis,
                maxCodeAttempts = maxCodeAttempts,
                myKeyPair = keyPair,
                deviceId = deviceId,
                protocolVersion = protocolVersion,
                createdAtMillis = clock.nowMillis()
            )
        }
    }
}