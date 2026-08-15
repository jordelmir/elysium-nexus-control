package com.elysium.nexus.tvnode.channel

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TvChannelCrypto — the authenticated channel between the phone
 * (Elysium Nexus controller) and the TV Node (PR2, §10).
 *
 * Faithful twin of the controller's `MacCrypto` (same primitive set,
 * same HKDF recipe, same wire layout) but with DOMAIN-SEPARATED labels
 * owned by THIS link:
 *
 *  - X25519 ECDH → 32-byte shared secret (RFC 7748 wire form).
 *  - HKDF-SHA256, salt `elysium-nexus-v1` (shared project constant),
 *    info label bound to the DIRECTION:
 *       phone→tv : "elysium-channel-phone-to-tv"
 *       tv→phone : "elysium-channel-tv-to-phone"
 *  - ChaCha20-Poly1305 AEAD, 12-byte counter nonce, AAD = channelAd.
 *
 * Wire frame: `nonce ‖ ciphertext ‖ tag` (12+N+16).
 *
 * Because the controller lives in a different module, the phone side
 * implements the mirror with side = PHONE; byte-parity is guaranteed by
 * the shared recipe and MUST be proven by a cross-side test before any
 * retail claim (parity doctesting pattern of Phase 32).
 *
 * `CryptoUnavailableException` on platforms without X25519 (old Android
 * without Conscrypt): the manifest declares the channel UNSUPPORTED
 * there — the node degrades, never invents (§6, §7).
 */
object TvChannelCrypto {

    /** Shared HKDF salt — SAME constant as the controller's MacCrypto. */
    val HKDF_SALT = "elysium-nexus-v1".toByteArray(Charsets.UTF_8)

    /** Directional HKDF info labels — owner of this link (phone↔TV). */
    val HKDF_INFO_PHONE_TO_TV = "elysium-channel-phone-to-tv".toByteArray(Charsets.UTF_8)
    val HKDF_INFO_TV_TO_PHONE = "elysium-channel-tv-to-phone".toByteArray(Charsets.UTF_8)

    private const val X25519_CURVE = "XDH"
    private const val AEAD_TRANSFORMATION = "ChaCha20-Poly1305"
    private const val KEY_SIZE_BYTES = 32
    private const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BYTES = 16

    class CryptoUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /** 32-byte X25519 key pair (RFC 7748 little-endian wire forms). */
    class KeyPair internal constructor(
        val publicKeyBytes: ByteArray,
        val privateScalar: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyPair) return false
            return publicKeyBytes.contentEquals(other.publicKeyBytes)
        }
        override fun hashCode(): Int = publicKeyBytes.contentHashCode()
        override fun toString(): String = "X25519(public=${publicKeyBytes.toHex()})"
    }

    /** Which end of the link this side is. TX/RX key mapping flips. */
    enum class LinkSide { PHONE, TV }

    /** Nonce prefix byte per direction — distinct from the MAC link (0x01/0x02). */
    enum class NonceDomain(val byte: Int) {
        PHONE_TO_TV(0x11),
        TV_TO_PHONE(0x22)
    }

    /** Generates a fresh X25519 key pair (throws [CryptoUnavailableException]). */
    fun generateKeyPair(): KeyPair = try {
        val kpg = try {
            KeyPairGenerator.getInstance("X25519")
        } catch (e: Exception) {
            KeyPairGenerator.getInstance(X25519_CURVE)
        }
        try {
            kpg.initialize(NamedParameterSpec.X25519, SecureRandom())
        } catch (e: Exception) {
            runCatching { kpg.initialize(255, SecureRandom()) }
        }
        val pair = kpg.generateKeyPair()
        val publicKeyBytes = if (pair.public is XECPublicKey) {
            bigIntegerToLittleEndian((pair.public as XECPublicKey).u, 32)
        } else {
            val enc = pair.public.encoded ?: error("No public key bytes")
            enc.takeLast(32).toByteArray()
        }
        val privateScalar = if (pair.private is XECPrivateKey) {
            (pair.private as XECPrivateKey).scalar.orElseGet {
                val enc = pair.private.encoded ?: error("No private key bytes")
                enc.takeLast(32).toByteArray()
            }
        } else {
            val enc = pair.private.encoded ?: error("No private key bytes")
            enc.takeLast(32).toByteArray()
        }
        require(publicKeyBytes.size == 32 && privateScalar.size == 32) { "X25519 keys must be 32 bytes" }
        KeyPair(publicKeyBytes, privateScalar)
    } catch (e: Exception) {
        throw CryptoUnavailableException("X25519 not available on this platform: ${e.message}", e)
    }

    /**
     * Directional channel keys. Both sides derive TWO keys from one shared
     * secret, domain-separated by HKDF info: this side's TX key is the
     * peer's RX key and vice versa. A captured frame can never be replayed
     * back to its sender.
     */
    fun deriveChannelKeys(
        myKeyPair: KeyPair,
        theirPublicKeyBytes: ByteArray,
        side: LinkSide
    ): ChannelKeys {
        val sharedSecret = computeSharedSecret(myKeyPair, theirPublicKeyBytes)
        val txInfo = if (side == LinkSide.PHONE) HKDF_INFO_PHONE_TO_TV else HKDF_INFO_TV_TO_PHONE
        val rxInfo = if (side == LinkSide.PHONE) HKDF_INFO_TV_TO_PHONE else HKDF_INFO_PHONE_TO_TV
        val txKey = hkdfSha256(sharedSecret, HKDF_SALT, txInfo, KEY_SIZE_BYTES)
        val rxKey = hkdfSha256(sharedSecret, HKDF_SALT, rxInfo, KEY_SIZE_BYTES)
        return ChannelKeys(side, txKey, rxKey)
    }

    /** Canonical AAD binding protocol version + direction (no cross-version/direction substitution). */
    fun channelAd(domain: NonceDomain, protocolVersion: Int = 1): ByteArray =
        "elysium-tv-link-v$protocolVersion|domain=${domain.name}".toByteArray(Charsets.UTF_8)

    /** SHA-256 fingerprint (8 hex) of a public-key blob — what QR pinning shows (§10). */
    fun fingerprintOf(publicKeyBytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun computeSharedSecret(myKeyPair: KeyPair, theirPublicKeyBytes: ByteArray): ByteArray {
        require(theirPublicKeyBytes.size == 32) { "X25519 public key must be 32 bytes" }
        return try {
            val algorithm = try {
                KeyFactory.getInstance("X25519")
                "X25519"
            } catch (e: Exception) {
                X25519_CURVE
            }
            val keyFactory = KeyFactory.getInstance(algorithm)
            val paramSpec = try {
                NamedParameterSpec.X25519
            } catch (e: Exception) {
                NamedParameterSpec("X25519")
            }
            val myPrivate = try {
                keyFactory.generatePrivate(XECPrivateKeySpec(paramSpec, myKeyPair.privateScalar))
            } catch (e: Exception) {
                val pkcs8Header = byteArrayOf(
                    0x30.toByte(), 0x2e.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte(),
                    0x30.toByte(), 0x05.toByte(), 0x06.toByte(), 0x03.toByte(), 0x2b.toByte(),
                    0x65.toByte(), 0x6e.toByte(), 0x04.toByte(), 0x22.toByte(), 0x04.toByte(), 0x20.toByte()
                )
                keyFactory.generatePrivate(
                    java.security.spec.PKCS8EncodedKeySpec(pkcs8Header + myKeyPair.privateScalar)
                )
            }
            val theirPublic = try {
                keyFactory.generatePublic(
                    XECPublicKeySpec(paramSpec, littleEndianToBigInteger(theirPublicKeyBytes))
                )
            } catch (e: Exception) {
                val x509Header = byteArrayOf(
                    0x30.toByte(), 0x2a.toByte(), 0x30.toByte(), 0x05.toByte(), 0x06.toByte(),
                    0x03.toByte(), 0x2b.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x03.toByte(),
                    0x21.toByte(), 0x00.toByte()
                )
                keyFactory.generatePublic(
                    java.security.spec.X509EncodedKeySpec(x509Header + theirPublicKeyBytes)
                )
            }
            val ka = KeyAgreement.getInstance(algorithm)
            ka.init(myPrivate)
            ka.doPhase(theirPublic, true)
            ka.generateSecret()
        } catch (e: Exception) {
            throw CryptoUnavailableException("Key derivation failed: ${e.message}", e)
        }
    }

    /** Manual HKDF-SHA256 (RFC 5869) — identical to the controller's MacCrypto. */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter: Byte = 1
        while (pos < length) {
            val input = t + info + counter
            t = hmacSha256(prk, input)
            val toCopy = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, toCopy)
            pos += toCopy
            counter++
        }
        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** Monotonic 12-byte counter nonce; byte 0 = domain, bytes 4..11 = big-endian seq. */
    class NonceCounter(private val domainByte: Int) {
        private var counter: Long = 0
        private val lock = Any()

        fun next(): ByteArray = synchronized(lock) {
            counter += 1
            val nonce = ByteArray(NONCE_SIZE_BYTES)
            nonce[0] = domainByte.toByte()
            val c = counter
            nonce[4] = ((c ushr 56) and 0xFF).toByte()
            nonce[5] = ((c ushr 48) and 0xFF).toByte()
            nonce[6] = ((c ushr 40) and 0xFF).toByte()
            nonce[7] = ((c ushr 32) and 0xFF).toByte()
            nonce[8] = ((c ushr 24) and 0xFF).toByte()
            nonce[9] = ((c ushr 16) and 0xFF).toByte()
            nonce[10] = ((c ushr 8) and 0xFF).toByte()
            nonce[11] = (c and 0xFF).toByte()
            nonce
        }

        companion object {
            fun sequenceOf(nonce: ByteArray): Long {
                require(nonce.size == NONCE_SIZE_BYTES) { "nonce must be 12 bytes" }
                return (0L
                    or ((nonce[4].toLong() and 0xFF) shl 56)
                    or ((nonce[5].toLong() and 0xFF) shl 48)
                    or ((nonce[6].toLong() and 0xFF) shl 40)
                    or ((nonce[7].toLong() and 0xFF) shl 32)
                    or ((nonce[8].toLong() and 0xFF) shl 24)
                    or ((nonce[9].toLong() and 0xFF) shl 16)
                    or ((nonce[10].toLong() and 0xFF) shl 8)
                    or (nonce[11].toLong() and 0xFF))
            }
        }
    }

    /** Thrown when an inbound frame fails anti-replay / direction checks. */
    class ReplayRejectedException(message: String) : RuntimeException(message)

    /** Receiver-side sliding-window replay protection (deny seen/behind-window frames). */
    class ReplayGuard(private val windowSize: Long = 65_536L) {
        private val seen = java.util.TreeSet<Long>()
        private var highest: Long = 0
        private val lock = Any()

        fun accept(sequence: Long): Boolean = synchronized(lock) {
            if (sequence < 0) return@synchronized false
            if (highest > 0 && sequence <= highest - windowSize) return@synchronized false
            if (!seen.add(sequence)) return@synchronized false
            if (sequence > highest) highest = sequence
            seen.removeIf { it <= highest - windowSize }
            true
        }

        fun highestSeen(): Long = synchronized(lock) { highest }
    }

    /** Directional channel keys with per-direction nonces + anti-replay on RX. */
    class ChannelKeys internal constructor(
        val side: LinkSide,
        val txKeyBytes: ByteArray,
        val rxKeyBytes: ByteArray,
        private val replayGuard: ReplayGuard = ReplayGuard()
    ) {
        private val txDomain = if (side == LinkSide.PHONE) NonceDomain.PHONE_TO_TV else NonceDomain.TV_TO_PHONE
        private val rxDomain = if (side == LinkSide.PHONE) NonceDomain.TV_TO_PHONE else NonceDomain.PHONE_TO_TV
        private val txNonce = NonceCounter(txDomain.byte)

        /** Encrypts to the peer: `nonce ‖ ciphertext ‖ tag` with AAD bound. */
        fun encryptToPeer(plaintext: ByteArray, ad: ByteArray? = null): ByteArray {
            val nonce = txNonce.next()
            return aeadSeal(plaintext, txKeyBytes, nonce, ad)
        }

        /** Authenticates + decrypts a peer frame; rejects wrong domain and replays. */
        fun decryptFromPeer(frame: ByteArray, ad: ByteArray? = null): ByteArray {
            require(frame.size >= NONCE_SIZE_BYTES + TAG_SIZE_BYTES) { "ciphertext too short: ${frame.size}" }
            val nonce = frame.copyOfRange(0, NONCE_SIZE_BYTES)
            if ((nonce[0].toInt() and 0xFF) != rxDomain.byte) {
                throw ReplayRejectedException(
                    "nonce domain ${nonce[0].toInt() and 0xFF} does not match receive direction ${rxDomain.byte}"
                )
            }
            val plaintext = aeadOpen(frame, rxKeyBytes, ad)
            // Authenticate FIRST, then advance the guard: a forged frame must
            // never consume a sequence slot.
            if (!replayGuard.accept(NonceCounter.sequenceOf(nonce))) {
                throw ReplayRejectedException("sequence ${NonceCounter.sequenceOf(nonce)} rejected by replay guard")
            }
            return plaintext
        }

        fun peerTxKeyBytes(): ByteArray = rxKeyBytes

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelKeys) return false
            return side == other.side &&
                txKeyBytes.contentEquals(other.txKeyBytes) &&
                rxKeyBytes.contentEquals(other.rxKeyBytes)
        }
        override fun hashCode(): Int = txKeyBytes.contentHashCode() * 31 + rxKeyBytes.contentHashCode()
    }

    private fun aeadSeal(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, ad: ByteArray?): ByteArray =
        try {
            val cipher = Cipher.getInstance(AEAD_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            if (ad != null) cipher.updateAAD(ad)
            val ctWithTag = cipher.doFinal(plaintext)
            val ciphertext = ctWithTag.copyOfRange(0, ctWithTag.size - TAG_SIZE_BYTES)
            val tag = ctWithTag.copyOfRange(ctWithTag.size - TAG_SIZE_BYTES, ctWithTag.size)
            nonce + ciphertext + tag
        } catch (e: Exception) {
            throw CryptoUnavailableException("AEAD encrypt failed: ${e.message}", e)
        }

    private fun aeadOpen(frame: ByteArray, key: ByteArray, ad: ByteArray?): ByteArray =
        try {
            val nonce = frame.copyOfRange(0, NONCE_SIZE_BYTES)
            val body = frame.copyOfRange(NONCE_SIZE_BYTES, frame.size - TAG_SIZE_BYTES)
            val tag = frame.copyOfRange(frame.size - TAG_SIZE_BYTES, frame.size)
            val cipher = Cipher.getInstance(AEAD_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            if (ad != null) cipher.updateAAD(ad)
            cipher.doFinal(body + tag)
        } catch (e: Exception) {
            throw CryptoUnavailableException("AEAD decrypt failed: ${e.message}", e)
        }
}

private fun bigIntegerToLittleEndian(value: BigInteger, size: Int): ByteArray {
    val be = value.toByteArray()
    val trimmed = when {
        be.size == size -> be
        be.size == size + 1 && be[0] == 0.toByte() -> be.copyOfRange(1, be.size)
        be.size < size -> ByteArray(size - be.size) + be
        else -> error("BigInteger too large for $size-byte little-endian: ${be.size}")
    }
    val out = ByteArray(size)
    for (i in 0 until size) out[i] = trimmed[size - 1 - i]
    return out
}

private fun littleEndianToBigInteger(le: ByteArray): BigInteger {
    val be = ByteArray(le.size)
    for (i in le.indices) be[i] = le[le.size - 1 - i]
    return BigInteger(1, be)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }