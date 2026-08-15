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
 * Realistic scope for THIS slice (transport + mirror + parity):
 *   - `pinPeerFingerprint`: durable public-key pinning (§10 certificate/
 *     public-key pinning). The QR shows the TV fingerprint; after pairing the
 *     phone stores it and the TV stores the phone's — subsequent sessions
 *     MUST match or the link refuses to establish.
 *   - `saveChannelCredential`: persists the derived directional channel keys
 *     (wrapped) keyed by connectionId so a re-established session can prove
 *     continuity / revocation.
 */
interface TvCredentialVault {
    /** Durably store a peer's 8-hex public-key fingerprint as pinned. */
    fun pinPeerAndCheckFingerprint(fingerprint: String): VaultResult

    /** Forget a peer's pin (revocation path, §10). */
    fun unpinPeer(fingerprint: String): VaultResult

    /** True if the fingerprint was previously pinned by [pinPeerAndCheckFingerprint]. */
    fun isPeerPinned(fingerprint: String): Boolean

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
        fun isOk(r: VaultResult): Boolean = r is VaultResult.Stored ||
            r is VaultResult.AlreadyPinned ||
            r is VaultResult.NotFound
    }
}
