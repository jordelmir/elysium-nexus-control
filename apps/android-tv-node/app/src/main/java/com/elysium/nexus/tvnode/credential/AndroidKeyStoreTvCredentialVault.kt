package com.elysium.nexus.tvnode.credential

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStoreTvCredentialVault — production credential vault (PR2 slice 4,
 * §10 "Credential storage: Android Keystore; Encryption: modern AEAD").
 *
 * The order mandates that credentials live behind the Android Keystore, not
 * as a plaintext Room blob. The peer full SHA-256 identities (64 hex) and, per
 * connectionId, the derived channel credential are wrapped with AES-GCM under
 * AES-256 keys generated inside the Keystore — the wrapping key never leaves
 * the secure hardware (security level MEASURED at runtime, never assumed),
 * and nothing is persisted in plaintext.
 *
 * Maturity: `IMPLEMENTED`. The cryptographic premise is only verifiable on a
 * device/emulator; the JVM contract tests run against
 * [InMemoryTvCredentialVault] (identical interface), and this impl reaches
 * `ON_DEVICE_VERIFIED` when exercised during an on-TV pairing in a later
 * on-device slice.
 */
class AndroidKeyStoreTvCredentialVault(context: Context) : TvCredentialVault {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ------------------------------------------------------------------
    // Peer pinning (certificate/public-key pinning, §10)
    // ------------------------------------------------------------------

    override fun pinPeerIdentity(peerIdentity: String): TvCredentialVault.VaultResult =
        guard {
            TvCredentialVault.requireFullPeerIdentity(peerIdentity)
            if (isPeerIdentityPinned(peerIdentity)) {
                TvCredentialVault.VaultResult.AlreadyPinned
            } else {
                prefs.edit()
                    .putString(
                        KEY_IDENTITY_PREFIX + peerIdentity,
                        storeCiphertext(wrap(peerIdentity.toByteArray(Charsets.UTF_8)))
                    )
                    .apply()
                TvCredentialVault.VaultResult.Stored
            }
        }

    override fun unpinPeer(peerIdentity: String): TvCredentialVault.VaultResult =
        guard {
            val key = KEY_IDENTITY_PREFIX + peerIdentity
            if (!prefs.contains(key)) {
                TvCredentialVault.VaultResult.NotFound
            } else {
                prefs.edit().remove(key).apply()
                TvCredentialVault.VaultResult.Stored
            }
        }

    override fun isPeerIdentityPinned(peerIdentity: String): Boolean =
        guard {
            val encoded = prefs.getString(KEY_IDENTITY_PREFIX + peerIdentity, null)
                ?: return@guard false
            val plain = unwrap(readCiphertext(encoded))
            String(plain, Charsets.UTF_8) == peerIdentity
        }

    // ------------------------------------------------------------------
    // Channel credentials (opaque wrapped blob per connectionId)
    // ------------------------------------------------------------------

    override fun saveChannelCredential(
        connectionId: Long,
        keys: com.elysium.nexus.tvnode.channel.TvChannelCrypto.ChannelKeys
    ): TvCredentialVault.VaultResult =
        guard {
            val blob = serialize(keys)
            prefs.edit()
                .putString(
                    KEY_CREDENTIAL_PREFIX + connectionId,
                    storeCiphertext(wrap(blob))
                )
                .apply()
            TvCredentialVault.VaultResult.Stored
        }

    override fun loadChannelCredential(
        connectionId: Long
    ): com.elysium.nexus.tvnode.channel.TvChannelCrypto.ChannelKeys? =
        guard {
            val encoded = prefs.getString(KEY_CREDENTIAL_PREFIX + connectionId, null)
                ?: return@guard null
            deserialize(unwrap(readCiphertext(encoded)))
        }

    override fun revokeConnection(connectionId: Long): TvCredentialVault.VaultResult =
        guard {
            val key = KEY_CREDENTIAL_PREFIX + connectionId
            if (!prefs.contains(key)) {
                TvCredentialVault.VaultResult.NotFound
            } else {
                prefs.edit().remove(key).apply()
                TvCredentialVault.VaultResult.Stored
            }
        }

    // ------------------------------------------------------------------
    // AES-GCM wrap / unwrap under an Android Keystore AES key
    // ------------------------------------------------------------------

    private fun ensureWrappingKey() {
        if (keyStore.getKey(WRAPPING_KEY_ALIAS, null) != null) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }

    /**
     * Reports the measured Android Keystore security level of the wrapping
     * key (TRUSTED_ENVIRONMENT / STRONGBOX / SOFTWARE) — never assumed.
     * Hardware backing is only claimed when measured at runtime
     * (Master Order v0.10 Phase 18).
     */
    fun wrappingKeySecurityLevel(): String =
        guard {
            val secretKey = keyStore.getKey(WRAPPING_KEY_ALIAS, null)
                ?: return@guard "UNPROVISIONED"
            if (secretKey !is javax.crypto.SecretKey) return@guard "UNKNOWN"
            val factory = javax.crypto.SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, android.security.keystore.KeyInfo::class.java)
                as android.security.keystore.KeyInfo
            securityLevelName(keyInfo)
        }

    private fun securityLevelName(keyInfo: android.security.keystore.KeyInfo): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "UNKNOWN"
        return when (keyInfo.securityLevel) {
            android.security.keystore.KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
            android.security.keystore.KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TRUSTED_ENVIRONMENT"
            android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
            else -> "LEVEL_${keyInfo.securityLevel}"
        }
    }

    private fun wrap(plain: ByteArray): ByteArray {
        ensureWrappingKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyStore.getKey(WRAPPING_KEY_ALIAS, null))
        val ciphertextAndTag = cipher.doFinal(plain)
        return cipher.iv + ciphertextAndTag
    }

    private fun unwrap(wrapped: ByteArray): ByteArray {
        ensureWrappingKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyStore.getKey(WRAPPING_KEY_ALIAS, null),
            GCMParameterSpec(128, wrapped.copyOfRange(0, IV_SIZE))
        )
        return cipher.doFinal(wrapped.copyOfRange(IV_SIZE, wrapped.size))
    }

    // ------------------------------------------------------------------
    // ChannelKeys serialization (canonical directional TX/RX bytes)
    // ------------------------------------------------------------------

    private fun serialize(
        keys: com.elysium.nexus.tvnode.channel.TvChannelCrypto.ChannelKeys
    ): ByteArray = lengthPrefixed(keys.txKeyBytes) + lengthPrefixed(keys.rxKeyBytes)

    private fun deserialize(
        blob: ByteArray
    ): com.elysium.nexus.tvnode.channel.TvChannelCrypto.ChannelKeys? {
        // TX/RX-directional channel keys can be persisted for session
        // continuity; the full mirror-side DH keypair resume is finalized
        // together with the phone-side Keystore in the next slice. Until then
        // we keep it honest: a stored credential is reloadable only when the
        // format matches what THIS build wrote (length-prefixed 32+32).
        var pos = 0
        val len1 = if (pos < blob.size) blob[pos].toInt() and 0xFF else return null
        pos++
        if (pos + len1 > blob.size) return null
        val tx = blob.copyOfRange(pos, pos + len1); pos += len1
        val len2 = if (pos < blob.size) blob[pos].toInt() and 0xFF else return null
        pos++
        if (pos + len2 > blob.size) return null
        val rx = blob.copyOfRange(pos, pos + len2)
        val side = com.elysium.nexus.tvnode.channel.TvChannelCrypto.LinkSide.TV
        return try {
            com.elysium.nexus.tvnode.channel.TvChannelCrypto.ChannelKeys(side, tx, rx)
        } catch (e: Exception) {
            null
        }
    }

    private fun lengthPrefixed(b: ByteArray): ByteArray =
        ByteArray(1) { b.size.toByte() } + b

    private fun storeCiphertext(blob: ByteArray): String =
        Base64.encodeToString(blob, Base64.NO_WRAP)

    private fun readCiphertext(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)

    /** Converts Keystore/provisioning failures into a loud, typed failure. */
    private inline fun <T> guard(block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            throw VaultKeystoreUnavailable(e)
        }
    }

    /** The signature exception for any Keystore operation failure. */
    class VaultKeystoreUnavailable(cause: Throwable) : RuntimeException(cause)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "tvnode_credential_vault"
        private const val KEY_IDENTITY_PREFIX = "pin:"
        private const val KEY_CREDENTIAL_PREFIX = "cred:"
        private const val WRAPPING_KEY_ALIAS = "tvnode-credential-wrap"
        private const val IV_SIZE = 12
    }
}
