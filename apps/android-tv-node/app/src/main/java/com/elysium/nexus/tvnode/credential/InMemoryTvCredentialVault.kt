package com.elysium.nexus.tvnode.credential

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemoryTvCredentialVault — JVM twin of the vault contract.
 *
 * Used by the unit suite (and usable as a dev-only store). It does NOT
 * claim Android-Keystore protection; the production path is
 * [AndroidKeyStoreTvCredentialVault]. Keeping the same contract allows the
 * transport/mirror tests to exercise pinning + credential persistence and
 * the on-device vault to swap in without changing call sites.
 */
class InMemoryTvCredentialVault : TvCredentialVault {

    private val pins = ConcurrentHashMap.newKeySet<String>()
    private val byConnection = ConcurrentHashMap<Long, TvChannelCrypto.ChannelKeys>()

    override fun pinPeerAndCheckFingerprint(fingerprint: String): TvCredentialVault.VaultResult {
        if (isPeerPinned(fingerprint)) return TvCredentialVault.VaultResult.AlreadyPinned
        pins.add(fingerprint)
        return TvCredentialVault.VaultResult.Stored
    }

    override fun unpinPeer(fingerprint: String): TvCredentialVault.VaultResult =
        if (pins.remove(fingerprint)) {
            TvCredentialVault.VaultResult.Stored
        } else {
            TvCredentialVault.VaultResult.NotFound
        }

    override fun isPeerPinned(fingerprint: String): Boolean = pins.contains(fingerprint)

    override fun saveChannelCredential(
        connectionId: Long,
        keys: TvChannelCrypto.ChannelKeys
    ): TvCredentialVault.VaultResult {
        byConnection[connectionId] = keys
        return TvCredentialVault.VaultResult.Stored
    }

    override fun loadChannelCredential(connectionId: Long): TvChannelCrypto.ChannelKeys? =
        byConnection[connectionId]

    override fun revokeConnection(connectionId: Long): TvCredentialVault.VaultResult =
        if (byConnection.remove(connectionId) != null) {
            TvCredentialVault.VaultResult.Stored
        } else {
            TvCredentialVault.VaultResult.NotFound
        }
}
