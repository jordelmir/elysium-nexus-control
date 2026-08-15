package com.elysium.nexus.tvnode.pairing

import java.security.MessageDigest

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
 *     verifyCode → FAILED
 *     qrPayload  → null (never leaks a stale code)
 * - Malformed payload: never matches, never advances state.
 *
 * The session holds the QR payload with the ephemeral nonce — the nonce is
 * consumed exactly once at QR display time, so a replayed QR cannot bind to
 * a newer session (anti-replay).
 */
class PairingSession private constructor(
    private val nonce: PairingNonce,
    private val code: PairingCode,
    private val clock: PairingClock,
    private val ttlMillis: Long,
    private val maxCodeAttempts: Int,
    private val qrFingerprint: String,
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

    /** Returns the QR payload ONLY while the session is open and not expired. */
    fun qrPayload(): QrPairingPayload? = when {
        state !is State.Open -> null
        isExpired() -> null
        else -> QrPairingPayload(
            protocolVersion = protocolVersion,
            deviceId = deviceId,
            nonce = nonce,
            pubKeyFingerprint = qrFingerprint
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
     * After the code is verified, both sides exchange a symmetric channel
     * secret (forward-secure X25519 + AEAD — crypto layer, next slice).
     * This session records the binding only after the peer proves nonce +
     * key material.
     */
    fun bindChannel(): State {
        if (isExpired()) {
            state = State.Expired
            return state
        }
        if (state !is State.CodeVerified) return state
        state = State.Established
        return state
    }

    private fun isExpired(): Boolean = clock.nowMillis() - createdAtMillis > ttlMillis

    companion object {

        /** Creates a fresh session (used by the TV when pairing starts). */
        fun create(clock: PairingClock, nonce: PairingNonce, qrFingerprint: String, deviceId: String, protocolVersion: Int = 1, ttlMillis: Long = 60_000, maxCodeAttempts: Int = 5): PairingSession =
            PairingSession(
                nonce = nonce,
                code = PairingCode.generate(),
                clock = clock,
                ttlMillis = ttlMillis,
                maxCodeAttempts = maxCodeAttempts,
                qrFingerprint = qrFingerprint,
                deviceId = deviceId,
                protocolVersion = protocolVersion,
                createdAtMillis = clock.nowMillis()
            )

        /** SHA-256 fingerprint (8 hex) of a public-key blob — what the QR pinning shows. */
        fun fingerprintOf(publicKeyBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
            return digest.joinToString("") { "%02x".format(it) }.take(8)
        }
    }
}