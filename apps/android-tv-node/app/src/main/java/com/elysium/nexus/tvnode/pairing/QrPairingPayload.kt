package com.elysium.nexus.tvnode.pairing

/**
 * QrPairingPayload — the QR contents shown on the TV at first launch
 * (§10: show QR + short human-readable code).
 *
 * Content (bootstrap metadata ONLY, never sensitive, never identity):
 *   elysium-pairing|v1|<deviceId>|<nonce>|<pubKeyFingerprint>
 *
 * - deviceId: the metadata join key (manufacturer:model:device). NOT a
 *   unique physical identity (order §8).
 * - nonce: ephemeral single-use pairing nonce; the peer must echo it back
 *   inside the authenticated handshake to prove possession of the scanned
 *   QR (anti-replay).
 * - pubKeyFingerprint: first 8 hex chars of the SHA-256 of the TV node's
 *   ephemeral X25519 public key material for THIS pairing session, so the
 *   phone can pin it before any key exchange (certificate/public-key
 *   pinning, order §10).
 *
 * The 6-digit PairingCode is displayed SEPARATELY (never inside the QR)
 * so that possession of the QR alone is not sufficient — the attacker
 * must also know the code shown on screen.
 */
data class QrPairingPayload(
    val protocolVersion: Int,
    val deviceId: String,
    val nonce: PairingNonce,
    val pubKeyFingerprint: String
) {

    init {
        require(protocolVersion in 1..99) { "protocolVersion must be in 1..99." }
        require(deviceId.isNotBlank() && deviceId.length <= 160) { "deviceId must be non-blank, ≤160 chars." }
        require(PUBKEY_FP.matches(pubKeyFingerprint)) { "pubKeyFingerprint must be 8 hex chars." }
    }

    fun encode(): String = "elysium-pairing|v$protocolVersion|$deviceId|${nonce.value}|$pubKeyFingerprint"

    companion object {
        private val PUBKEY_FP = Regex("^[0-9a-f]{8}$")
        private val LINE = Regex("^elysium-pairing\\|v(\\d{1,2})\\|([^|]{1,160})\\|([0-9a-f]{16})\\|([0-9a-f]{8})$")

        /**
         * Parses a QR payload. Strict: malformed or unknown version →
         * null (REJECT per §10: malformed frame → reject).
         */
        fun parse(encoded: String): QrPairingPayload? {
            val m = LINE.matchEntire(encoded) ?: return null
            return QrPairingPayload(
                protocolVersion = m.groupValues[1].toInt(),
                deviceId = m.groupValues[2],
                nonce = PairingNonce.of(m.groupValues[3]),
                pubKeyFingerprint = m.groupValues[4]
            )
        }
    }
}