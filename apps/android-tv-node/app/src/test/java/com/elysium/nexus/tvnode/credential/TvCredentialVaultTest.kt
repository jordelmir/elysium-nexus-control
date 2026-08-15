package com.elysium.nexus.tvnode.credential

import com.elysium.nexus.tvnode.channel.TvChannelCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TvCredentialVaultTest — the vault contract, exercised against the JVM
 * twin [InMemoryTvCredentialVault] (PR2 slice 4, §10 pinning + credential
 * storage). The Android-Keystore impl shares this interface and is verified
 * on-device later; the contract here is what both must honor.
 */
class TvCredentialVaultTest {

    private val vault = InMemoryTvCredentialVault()

    @Test
    fun `pin stores once and reports it back`() {
        val fp = "a1b2c3d4"
        val first = vault.pinPeerAndCheckFingerprint(fp)
        assertTrue(TvCredentialVault.isOk(first))
        assertTrue(vault.isPeerPinned(fp))
        val again = vault.pinPeerAndCheckFingerprint(fp)
        assertEquals(TvCredentialVault.VaultResult.AlreadyPinned, again)
    }

    @Test
    fun `unpin removes the pin`() {
        val fp = "deadbeef"
        vault.pinPeerAndCheckFingerprint(fp)
        assertEquals(TvCredentialVault.VaultResult.Stored, vault.unpinPeer(fp))
        assertFalse(vault.isPeerPinned(fp))
        assertEquals(TvCredentialVault.VaultResult.NotFound, vault.unpinPeer(fp))
    }

    @Test
    fun `distinct pins are distinct and pointers are case-sensitive`() {
        val a = "aa11bb22"
        val b = "AA11BB22"
        vault.pinPeerAndCheckFingerprint(a)
        assertTrue(vault.isPeerPinned(a))
        assertFalse(vault.isPeerPinned(b))
    }

    @Test
    fun `channel credentials store and load for a connection`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val keys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        assertEquals(TvCredentialVault.VaultResult.Stored, vault.saveChannelCredential(123L, keys))
        val loaded = vault.loadChannelCredential(123L)
        assertNotNull(loaded)
        assertArrayEquals(keys.txKeyBytes, loaded!!.txKeyBytes)
        assertArrayEquals(keys.rxKeyBytes, loaded.rxKeyBytes)
    }

    @Test
    fun `revoke drops only that connection`() {
        val tv = TvChannelCrypto.generateKeyPair()
        val phone = TvChannelCrypto.generateKeyPair()
        val keys = TvChannelCrypto.deriveChannelKeys(tv, phone.publicKeyBytes, TvChannelCrypto.LinkSide.TV)
        vault.saveChannelCredential(1L, keys)
        vault.saveChannelCredential(2L, keys)
        assertEquals(TvCredentialVault.VaultResult.Stored, vault.revokeConnection(1L))
        assertNull(vault.loadChannelCredential(1L))
        assertNotNull(vault.loadChannelCredential(2L))
        assertEquals(TvCredentialVault.VaultResult.NotFound, vault.revokeConnection(1L))
    }

    @Test
    fun `missing connection credential loads as null`() {
        assertNull(vault.loadChannelCredential(404L))
    }
}
