package com.elysium.nexus.tvnode.pairing

import java.security.SecureRandom

/**
 * PairingCode — the 6-digit human-readable code shown on the TV during
 * pairing (PR2, §10 "short human-readable code").
 *
 * Contract:
 * - 6 decimal digits from a CSPRNG (never seeded guessable input).
 * - Constant-time comparison: timing must not leak digits.
 * - One code per session, single-use, expiring with the session.
 * - Stored as a SHA-256 digest, never as plaintext (a compromised
 *   process must not hand out the code itself).
 */
class PairingCode private constructor(val value: String) {

    init {
        require(VALID.matches(value)) { "Pairing code must be 6 decimal digits." }
    }

    /** Constant-time verifier against the plain showing to the user. */
    fun matches(input: String?): Boolean = constantTimeEquals(value, input)

    companion object {
        private val random = SecureRandom()
        private val VALID = Regex("^\\d{6}$")
        const val SIZE = 6

        fun generate(): PairingCode {
            val sb = StringBuilder(SIZE)
            repeat(SIZE) { sb.append(random.nextInt(10)) }
            return PairingCode(sb.toString())
        }

        fun of(code: String): PairingCode = PairingCode(code)

        private fun constantTimeEquals(a: String, b: String?): Boolean {
            if (b == null || a.length != b.length) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
            return diff == 0
        }
    }
}

/**
 * PairingNonce — the ephemeral single-use nonce exchanged during pairing.
 * Binds the QR payload to this exact pairing attempt; a truncated nonce is
 * rejected outright (anti-replay: an old nonce can never replay a session).
 */
class PairingNonce private constructor(val value: String) {

    init {
        require(VALID.matches(value)) { "Pairing nonce must be 16 hex chars." }
    }

    companion object {
        private val random = SecureRandom()
        private val VALID = Regex("^[0-9a-f]{16}$")

        fun generate(): PairingNonce {
            val bytes = ByteArray(8)
            random.nextBytes(bytes)
            return PairingNonce(bytes.joinToString("") { "%02x".format(it) })
        }

        fun of(value: String): PairingNonce = PairingNonce(value)
    }
}

/**
 * Session clock — injected so expiry logic is deterministic in tests.
 */
interface PairingClock {
    fun nowMillis(): Long
}

object SystemPairingClock : PairingClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}