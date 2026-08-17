package com.elysium.nexus.tvnode.credential

import com.elysium.nexus.tvnode.channel.TvChannelCrypto

/**
 * TvCredentialVault — the durable credential store for the phone↔TV link
 * (PR2 slice 4, §10: "Credential storage: Android Keystore. Encryption:
 * modern AEAD").
 *
 * Stores ONLY wrapped/derived credentials — never a raw peer public key or
 * channel key in plaintext on disk. The production implementation uses the
 * Android Keystore (an AEAD-wrapped blob); the JVM tests use an in-memory
 * twin so the vault contract itself is unit-verifiable without Robolectric.
 *
 * Master Order v0.10 Phase 14 (identity): the pin key is the FULL SHA-256
 * peer identity (64 hex, 256 bits) — NEVER the 8-hex short fingerprint,
 * which is display-only. Phase 17 (fail-closed): callers MUST honor the
 * [VaultResult] — an Error/NotFound means DENY, never proceed.
 *
 * Realistic scope for THIS slice (transport + mirror + parity):
 *   - `pinPeerIdentity`: durable public-key pinning (§10 certificate/
 *     public-key pinning). The QR shows the TV fingerprint; after pairing the
 *     phone stores it and the TV stores the phone's — subsequent sessions
 *     MUST match or the link refuses to establish.
 *   - `saveChannelCredential`: persists the derived directional channel keys
 *     (wrapped) keyed by connectionId so a re-established session can prove
 *     continuity / revocation.
 */
interface TvCredentialVault {
    /** Durably store a peer's full SHA-256 identity (64 hex) as pinned. */
    fun pinPeerIdentity(peerIdentity: String): VaultResult

    /** Forget a peer's pin (revocation path, §10). */
    fun unpinPeer(peerIdentity: String): VaultResult

    /** True if the identity was previously pinned by [pinPeerIdentity]. */
    fun isPeerIdentityPinned(peerIdentity: String): Boolean

    /**
     * Persist wrapped channel credentials. The caller supplies the channel
     * keys; the vault is responsible for sealing them (or refusing).
     * Uniqued by connectionId so revocation can drop one session's keys.
     */
    fun saveChannelCredential(connectionId: Long, keys: TvChannelCrypto.ChannelKeys): VaultResult

    /** Load wrapped channel credentials, or null when absent/invalid. */
    fun loadChannelCredential(connectionId: Long): TvChannelCrypto.ChannelKeys?

    /** Drop all state for one connectionId (session expiration / peer revoke). */
    fun revokeConnection(connectionId: Long): VaultResult

    sealed class VaultResult {
        object Stored : VaultResult()
        object AlreadyPinned : VaultResult()
        object NotFound : VaultResult()
        data class Error(val reason: String) : VaultResult()
    }

    companion object {
        /** True only when the operation landed durably (Stored, or already in the desired state). */
        fun isOk(r: VaultResult): Boolean = r is VaultResult.Stored ||
            r is VaultResult.AlreadyPinned

        /**
         * Phase 14 contract: the pin identity is the FULL 64-hex SHA-256.
         * Anything shorter (e.g. the 8-hex display fingerprint) is refused.
         */
        fun requireFullPeerIdentity(peerIdentity: String) {
            require(peerIdentity.length == 64) {
                "peer identity must be the full 64-hex SHA-256 (got length ${peerIdentity.length})"
            }
            require(peerIdentity.all { it in '0'..'9' || it in 'a'..'f' }) {
                "peer identity must be lowercase hex"
            }
        }
    }
}