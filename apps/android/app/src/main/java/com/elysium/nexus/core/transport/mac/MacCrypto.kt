package com.elysium.nexus.core.transport.mac

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
 * Elysium Nexus — Mac/PC channel crypto.
 *
 * X25519 + HKDF-SHA256 + ChaCha20-Poly1305. This
 * module is the Kotlin twin of the Swift
 * `ChannelCipher` (Crypto.swift). The on-the-wire
 * format is identical on both sides:
 *
 *  - X25519 ECDH derives a 32-byte shared secret.
 *  - HKDF-SHA256 with `salt = "elysium-nexus-v1"`
 *    and `info = "elysium-channel"` produces the
 *    32-byte channel key.
 *  - ChaCha20-Poly1305 AEAD encrypts every frame
 *    after the handshake. Each frame uses a fresh
 *    12-byte nonce (counter-based, never random,
 *    to guarantee uniqueness).
 *  - The wire layout for an encrypted frame is
 *    `nonce ‖ ciphertext ‖ tag` (12 + N + 16 bytes)
 *    as the frame's `payload`.
 *
 * The Mac side uses CryptoKit's
 * `shared.hkdfDerivedSymmetricKey(...)` and
 * `ChaChaPoly.seal/open`; both produce the same
 * bytes as the JCE primitives used here.
 *
 * ## X25519 wire format
 *
 * RFC 7748 defines the X25519 public-key wire
 * form as the 32-byte little-endian u-coordinate.
 * Java's `XECPublicKeySpec(NamedParameterSpec("X25519"), ...)`
 * expects exactly that 32-byte little-endian
 * representation, so no byte reordering is needed
 * between this module and the Mac agent.
 *
 * ## Android API quirks (vs. desktop JDK)
 *
 * Three differences from the desktop JDK that
 * catch everyone the first time:
 *
 *  1. `KeyAgreement` lives in `javax.crypto`, not
 *     `java.security`.
 *  2. `XECPrivateKey.getScalar()` returns
 *     `Optional<byte[]>`, not `BigInteger`.
 *  3. `XECPrivateKeySpec`'s second constructor
 *     argument is `byte[]`, not `BigInteger`.
 *     (`XECPublicKeySpec` still takes `BigInteger`.)
 *
 * These are documented in the Android source; the
 * JCE was aligned with the rest of OpenJDK on
 * desktop but Android's implementation diverges
 * slightly to keep the API surface lean.
 */
object MacCrypto {

    /** The HKDF salt. Must match the Mac agent's `salt` parameter. */
    private val HKDF_SALT = "elysium-nexus-v1".toByteArray(Charsets.UTF_8)

    /** The HKDF info. Must match the Mac agent's `sharedInfo` parameter. */
    private val HKDF_INFO = "elysium-channel".toByteArray(Charsets.UTF_8)

    /** The X25519 named curve. */
    private const val X25519_CURVE = "XDH"

    /** The ChaCha20-Poly1305 AEAD transformation. */
    private const val AEAD_TRANSFORMATION = "ChaCha20-Poly1305"

    /** The 256-bit HKDF output. */
    private const val KEY_SIZE_BYTES = 32

    /** The 96-bit (12-byte) AEAD nonce. */
    private const val NONCE_SIZE_BYTES = 12

    /** The 128-bit (16-byte) AEAD tag. */
    private const val TAG_SIZE_BYTES = 16

    /**
     * A 32-byte X25519 key pair, freshly generated.
     *
     * The `publicKeyBytes` are the 32-byte
     * little-endian u-coordinate (RFC 7748 wire
     * form). The `privateScalar` is the 32-byte
     * little-endian scalar; it is stored so we can
     * re-create a `PrivateKey` for ECDH without
     * re-keying.
     */
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
        override fun toString(): String = "X25519KeyPair(public=${publicKeyBytes.toHex()})"
    }

    /**
     * Generates a fresh X25519 key pair. Uses the
     * platform's `SecureRandom` for the seed.
     *
     * Throws [CryptoUnavailableException] if the
     * platform does not provide an X25519
     * `KeyPairGenerator` (older Android versions
     * without Conscrypt).
     */
    fun generateKeyPair(): KeyPair {
        return try {
            val kpg = try {
                KeyPairGenerator.getInstance("X25519")
            } catch (e: Exception) {
                KeyPairGenerator.getInstance("XDH")
            }
            try {
                kpg.initialize(NamedParameterSpec.X25519, SecureRandom())
            } catch (e: Exception) {
                try {
                    kpg.initialize(255, SecureRandom())
                } catch (e2: Exception) {
                    // Default init
                }
            }
            val pair = kpg.generateKeyPair()
            val publicKeyBytes = if (pair.public is XECPublicKey) {
                bigIntegerToLittleEndian((pair.public as XECPublicKey).u, 32)
            } else {
                val enc = pair.public.encoded ?: error("No public key bytes")
                require(enc.size >= 32) { "Public key encoding too short: ${enc.size}" }
                enc.takeLast(32).toByteArray()
            }
            val privateScalar = if (pair.private is XECPrivateKey) {
                (pair.private as XECPrivateKey).scalar.orElseGet {
                    val enc = pair.private.encoded ?: error("No private key bytes")
                    enc.takeLast(32).toByteArray()
                }
            } else {
                val enc = pair.private.encoded ?: error("No private key bytes")
                require(enc.size >= 32) { "Private key encoding too short: ${enc.size}" }
                enc.takeLast(32).toByteArray()
            }
            require(publicKeyBytes.size == 32) { "X25519 public key must be 32 bytes, got ${publicKeyBytes.size}" }
            require(privateScalar.size == 32) { "X25519 private scalar must be 32 bytes, got ${privateScalar.size}" }
            KeyPair(publicKeyBytes, privateScalar)
        } catch (e: Exception) {
            throw CryptoUnavailableException("X25519 not available on this platform: ${e.message}", e)
        }
    }

    fun deriveChannelKey(myKeyPair: KeyPair, theirPublicKeyBytes: ByteArray): ChannelKey {
        require(theirPublicKeyBytes.size == 32) { "X25519 public key must be 32 bytes" }
        return try {
            val algorithm = try {
                KeyFactory.getInstance("X25519")
                "X25519"
            } catch (e: Exception) {
                "XDH"
            }
            val keyFactory = KeyFactory.getInstance(algorithm)
            val paramSpec = try {
                NamedParameterSpec.X25519
            } catch (e: Exception) {
                NamedParameterSpec("X25519")
            }

            val myPrivate = try {
                val myPrivateSpec = XECPrivateKeySpec(paramSpec, myKeyPair.privateScalar)
                keyFactory.generatePrivate(myPrivateSpec)
            } catch (e: Exception) {
                // PKCS#8 DER header for X25519
                val pkcs8Header = byteArrayOf(
                    0x30.toByte(), 0x2e.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte(),
                    0x30.toByte(), 0x05.toByte(), 0x06.toByte(), 0x03.toByte(), 0x2b.toByte(),
                    0x65.toByte(), 0x6e.toByte(), 0x04.toByte(), 0x22.toByte(), 0x04.toByte(), 0x20.toByte()
                )
                val pkcs8 = pkcs8Header + myKeyPair.privateScalar
                keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(pkcs8))
            }

            val theirPublic = try {
                val theirPublicSpec = XECPublicKeySpec(
                    paramSpec,
                    littleEndianToBigInteger(theirPublicKeyBytes)
                )
                keyFactory.generatePublic(theirPublicSpec)
            } catch (e: Exception) {
                // X.509 DER header for X25519
                val x509Header = byteArrayOf(
                    0x30.toByte(), 0x2a.toByte(), 0x30.toByte(), 0x05.toByte(), 0x06.toByte(),
                    0x03.toByte(), 0x2b.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x03.toByte(),
                    0x21.toByte(), 0x00.toByte()
                )
                val x509 = x509Header + theirPublicKeyBytes
                keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(x509))
            }

            val ka = KeyAgreement.getInstance(algorithm)
            ka.init(myPrivate)
            ka.doPhase(theirPublic, true)
            val sharedSecret = ka.generateSecret()
            val keyBytes = hkdfSha256(sharedSecret, HKDF_SALT, HKDF_INFO, KEY_SIZE_BYTES)
            ChannelKey(keyBytes, NonceCounter())
        } catch (e: Exception) {
            throw CryptoUnavailableException("Key derivation failed: ${e.message}", e)
        }
    }

    /**
     * Manual HKDF-SHA256 (RFC 5869). The Mac agent
     * uses Apple's
     * `shared.hkdfDerivedSymmetricKey(using:salt:sharedInfo:outputByteCount:)`
     * which is also RFC 5869 with SHA-256. The two
     * produce identical bytes.
     */
    private fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
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

    class CryptoUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * The 32-byte channel key + monotonic nonce
     * counter. `encrypt` and `decrypt` are pure
     * functions over bytes (no Android dependencies);
     * the unit tests exercise them on the JVM.
     */
    class ChannelKey internal constructor(
        val keyBytes: ByteArray,
        val nonceCounter: NonceCounter
    ) {
        fun encrypt(plaintext: ByteArray): ByteArray {
            val nonce = nonceCounter.next()
            return encryptWithNonce(plaintext, nonce)
        }

        fun decrypt(ciphertextWithNonce: ByteArray): ByteArray {
            require(ciphertextWithNonce.size >= NONCE_SIZE_BYTES + TAG_SIZE_BYTES) {
                "ciphertext too short: ${ciphertextWithNonce.size}"
            }
            val nonce = ByteArray(NONCE_SIZE_BYTES)
            val body = ByteArray(ciphertextWithNonce.size - NONCE_SIZE_BYTES - TAG_SIZE_BYTES)
            val tag = ByteArray(TAG_SIZE_BYTES)
            System.arraycopy(ciphertextWithNonce, 0, nonce, 0, NONCE_SIZE_BYTES)
            System.arraycopy(ciphertextWithNonce, NONCE_SIZE_BYTES, body, 0, body.size)
            System.arraycopy(
                ciphertextWithNonce,
                NONCE_SIZE_BYTES + body.size,
                tag,
                0,
                TAG_SIZE_BYTES
            )
            return try {
                val cipher = Cipher.getInstance(AEAD_TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(keyBytes, "ChaCha20"),
                    IvParameterSpec(nonce)
                )
                cipher.doFinal(body + tag)
            } catch (e: Exception) {
                throw CryptoUnavailableException("AEAD decrypt failed: ${e.message}", e)
            }
        }

        private fun encryptWithNonce(plaintext: ByteArray, nonce: ByteArray): ByteArray {
            require(nonce.size == NONCE_SIZE_BYTES) { "nonce must be 12 bytes" }
            return try {
                val cipher = Cipher.getInstance(AEAD_TRANSFORMATION)
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(keyBytes, "ChaCha20"),
                    IvParameterSpec(nonce)
                )
                val ciphertextWithTag = cipher.doFinal(plaintext)
                val ciphertext = ByteArray(ciphertextWithTag.size - TAG_SIZE_BYTES)
                val tag = ByteArray(TAG_SIZE_BYTES)
                System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertext.size)
                System.arraycopy(ciphertextWithTag, ciphertext.size, tag, 0, TAG_SIZE_BYTES)
                nonce + ciphertext + tag
            } catch (e: Exception) {
                throw CryptoUnavailableException("AEAD encrypt failed: ${e.message}", e)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelKey) return false
            return keyBytes.contentEquals(other.keyBytes)
        }
        override fun hashCode(): Int = keyBytes.contentHashCode()
    }

    /**
     * A monotonic 96-bit (12-byte) nonce counter.
     * Top 4 bytes are 0; low 8 bytes are the counter
     * in big-endian. This mirrors the Mac agent's
     * `NonceCounter`.
     */
    class NonceCounter {
        private var counter: Long = 0
        private val lock = Any()
        fun next(): ByteArray {
            synchronized(lock) {
                counter += 1
                val nonce = ByteArray(NONCE_SIZE_BYTES)
                val c = counter
                nonce[4] = ((c ushr 56) and 0xFF).toByte()
                nonce[5] = ((c ushr 48) and 0xFF).toByte()
                nonce[6] = ((c ushr 40) and 0xFF).toByte()
                nonce[7] = ((c ushr 32) and 0xFF).toByte()
                nonce[8] = ((c ushr 24) and 0xFF).toByte()
                nonce[9] = ((c ushr 16) and 0xFF).toByte()
                nonce[10] = ((c ushr 8) and 0xFF).toByte()
                nonce[11] = (c and 0xFF).toByte()
                return nonce
            }
        }
    }
}

/**
 * BigInteger → 32-byte little-endian.
 *
 * RFC 7748 wire form: u-coordinate is stored
 * little-endian. `BigInteger.toByteArray()` returns
 * the big-endian two's-complement form, so we
 * reverse it.
 */
private fun bigIntegerToLittleEndian(value: BigInteger, size: Int): ByteArray {
    val be = value.toByteArray() // may be 33 bytes if sign bit is set
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

/**
 * 32-byte little-endian → BigInteger.
 */
private fun littleEndianToBigInteger(le: ByteArray): BigInteger {
    val be = ByteArray(le.size)
    for (i in le.indices) be[i] = le[le.size - 1 - i]
    return BigInteger(1, be)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
