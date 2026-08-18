package com.elysium.nexus.fabric.infrared.promotion

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Master Order v0.10 Phase 7 — Certificate Crypto V2.
 *
 * The symmetric-key "signature" (SHA-256(payload + secret)) is DELETED. Certificates
 * are signed with real asymmetric signatures. The production private key NEVER enters
 * the APK or the repository; only public verification keys ship.
 */
interface CertificateSigner {
    val keyId: String
    val signatureAlgorithm: String
    fun sign(canonicalPayload: ByteArray): ByteArray
}

interface CertificateVerifier {
    val keyId: String
    val signatureAlgorithm: String
    fun verify(canonicalPayload: ByteArray, signature: ByteArray): Boolean
}

/**
 * Ed25519 signer. Production usage: private key held by a protected signing
 * environment (HSM / KMS / offline signer); NEVER embedded in code.
 *
 * JVM runs (CI/desktop) and modern Android (API 28+ software provider) support
 * Ed25519 via java.security.
 */
class Ed25519CertificateSigner(
    private val privateKey: PrivateKey,
    override val keyId: String
) : CertificateSigner {

    override val signatureAlgorithm: String = "Ed25519"

    override fun sign(canonicalPayload: ByteArray): ByteArray {
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(canonicalPayload)
        return signature.sign()
    }
}

class Ed25519CertificateVerifier(
    private val publicKey: PublicKey,
    override val keyId: String
) : CertificateVerifier {

    override val signatureAlgorithm: String = "Ed25519"

    override fun verify(canonicalPayload: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(canonicalPayload)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}

/** Convenience factory for tests and offline signing tooling. */
fun generateEd25519KeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()