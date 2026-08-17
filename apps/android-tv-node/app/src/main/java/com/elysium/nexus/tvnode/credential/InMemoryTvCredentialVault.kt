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

    override fun pinPeerIdentity(peerIdentity: String): TvCredentialVault.VaultResult {
        TvCredentialVault.requireFullPeerIdentity(peerIdentity)
        if (isPeerIdentityPinned(peerIdentity)) return TvCredentialVault.VaultResult.AlreadyPinned
        pins.add(peerIdentity)
        return TvCredentialVault.VaultResult.Stored
    }

    override fun unpinPeer(peerIdentity: String): TvCredentialVault.VaultResult =
        if (pins.remove(peerIdentity)) {
            TvCredentialVault.VaultResult.Stored
        } else {
            TvCredentialVault.VaultResult.NotFound
        }

    override fun isPeerIdentityPinned(peerIdentity: String): Boolean = pins.contains(peerIdentity)

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
