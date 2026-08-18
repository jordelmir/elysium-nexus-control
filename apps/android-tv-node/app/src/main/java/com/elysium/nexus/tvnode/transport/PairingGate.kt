package com.elysium.nexus.tvnode.transport

/**
 * PairingGate — the TV-side seam that decides whether an ESTABLISHED channel
 * may reach CHANNEL_READY and its ACTION loop (PR2 slice 5, §10).
 *
 * The wire handshake ([TvLinkHandshake]) proves the peer POSSESSES the derived
 * channel keys via the AEAD nonce echo — but it does NOT, by itself, prove
 * knowledge of the on-screen pairing code or the QR nonce, nor that this peer
 * was ever authorized. Before any ACTION is served, the server asks the gate:
 *
 *   1. If the peer's full SHA-256 identity (256-bit, never the 8-hex
 *      display fingerprint) is already pinned in the credential vault, the
 *      peer is a known, previously-paired device → Authorized without a code
 *      (reconnect path, "both sides pin in vault").
 *   2. Otherwise the peer must produce a valid [PairingConfirm] — the code
 *      shown on the TV PLUS the nonce from the QR it scanned — verified
 *      against the active [PairingSession]; on success the identity is
 *      pinned (durably) and the channel is authorized.
 *   3. Anything else → Denied, and the server tears the link down
 *      (fail-closed, §10 "Unknown peer: REJECT").
 *
 * The gate is pure JVM and does not touch Android, so it is unit-testable
 * with the in-memory vault twin.
 */
interface PairingGate {

    sealed class Verdict {
        /** The peer is authorized; the link may send CHANNEL_READY. */
        object Authorized : Verdict()

        /** The peer is refused; the link must be torn down (fail-closed). */
        data class Denied(val reason: String) : Verdict()
    }

    /**
     * Authorize an established channel.
     *
     * @param peerIdentity the FULL SHA-256 identity (64 hex, 256 bits) of the
     *   peer's X25519 public key (from HELLO) — the durable pin identity
     *   (Master Order v0.10 Phase 14: the 8-hex short fingerprint is
     *   display-only and NEVER used for authorization).
     * @param confirm the decoded [PairingConfirm] the peer sealed in
     *   PAIR_CONFIRM, or null if it sent none.
     */
    fun authorize(peerIdentity: String, confirm: PairingConfirm?): Verdict
}

/**
 * PairingConfirm — the deterministic plaintext the phone seals in the
 * PAIR_CONFIRM frame (PR2 slice 5, §10).
 *
 * Contents:
 *   u8 codeLen | code.utf8 | nonce.utf8 (32 hex chars)
 *
 * - code: the 6-digit code shown on the TV screen — proves the human typed
 *   what the TV displayed (single-use, session-scoped).
 * - nonce: the 32-hex QR nonce the phone scanned — proves THIS pairing
 *   attempt, binding the confirm to the exact QR shown (anti-replay: a
 *   stale nonce can never confirm a later attempt).
 *
 * Both travel AEAD-sealed over the established channel; parse is strict
 * (malformed → null → Denied).
 */
data class PairingConfirm(
    val code: String,
    val nonce: String
) {
    init {
        require(CODE.matches(code)) { "code must be 6 decimal digits" }
        require(NONCE.matches(nonce)) { "nonce must be 32 hex chars" }
    }

    fun encode(): ByteArray {
        val codeBytes = code.toByteArray(Charsets.UTF_8)
        val nonceBytes = nonce.toByteArray(Charsets.UTF_8)
        val out = ByteArray(1 + codeBytes.size + nonceBytes.size)
        out[0] = codeBytes.size.toByte()
        System.arraycopy(codeBytes, 0, out, 1, codeBytes.size)
        System.arraycopy(nonceBytes, 0, out, 1 + codeBytes.size, nonceBytes.size)
        return out
    }

    companion object {
        private val CODE = Regex("^\\d{6}$")
        private val NONCE = Regex("^[0-9a-f]{32}$")

        /** Strict parser: malformed payload → null (fail-closed reject). */
        fun parse(encoded: ByteArray): PairingConfirm? {
            if (encoded.size < 1 + 6 + 32) return null
            val codeLen = encoded[0].toInt() and 0xFF
            if (codeLen != 6) return null
            if (encoded.size != 1 + codeLen + 32) return null
            val code = String(encoded, 1, codeLen, Charsets.UTF_8)
            val nonce = String(encoded, 1 + codeLen, 32, Charsets.UTF_8)
            if (!CODE.matches(code) || !NONCE.matches(nonce)) return null
            return PairingConfirm(code, nonce)
        }

        const val FIXED_NONCE_LEN = 32
    }
}
