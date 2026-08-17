package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailCompatibilityCertificate
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import java.security.MessageDigest
import java.util.UUID

/**
 * Phase 19 — Compatibility Certificate Engine (Master Order v0.10 Phases 7/8).
 *
 * Issues cryptographically verifiable Retail Compatibility Certificates with a
 * REAL asymmetric signature (Ed25519 via [CertificateSigner]).
 *
 * Fail-closed validation BEFORE signing:
 * - The SKU must resolve to a model and every evidence row MUST belong to the
 *   SAME model (wrong-device dispatch is impossible by construction).
 * - Any FAILED/REGRESSION evidence blocks issuance.
 * - Every applicable CORE action (CoreActionPolicy) needs passing evidence.
 * - Canonical payload includes schema version, policy version, build identity,
 *   validity window and keyId — signed as a single deterministic document.
 */
object CompatibilityCertificateEngine {

    val REQUIRED_CORE_ACTIONS: Set<String> = CoreActionPolicy.TV_CORE_ACTIONS

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Deterministic canonical payload — the only document that is ever signed.
     * Field order and list ordering are fixed to prevent ambiguous re-parsing.
     */
    fun canonicalPayload(cert: RetailCompatibilityCertificate): ByteArray =
        canonicalPayload(
            schemaVersion = cert.schemaVersion,
            policyVersion = cert.policyVersion,
            certificateId = cert.certificateId,
            retailer = cert.retailer.name,
            skuCode = cert.skuCode,
            exactMpn = cert.exactMpn,
            deviceModelId = cert.deviceModelId,
            coreActions = cert.coreActionsVerified,
            extendedActions = cert.extendedActionsVerified,
            evidenceIds = cert.evidenceIds,
            evidenceShas = cert.physicalEvidenceShaList,
            appCommit = cert.appCommit,
            catalogBuildId = cert.catalogBuildId,
            verifiedAt = cert.verifiedAtTimestamp,
            validFrom = cert.validFromTimestamp,
            validUntil = cert.validUntilTimestamp,
            keyId = cert.keyId,
            signatureAlgorithm = cert.signatureAlgorithm
        )

    fun canonicalPayload(
        schemaVersion: Int,
        policyVersion: String,
        certificateId: String,
        retailer: String,
        skuCode: String,
        exactMpn: String,
        deviceModelId: String,
        coreActions: Set<String>,
        extendedActions: Set<String>,
        evidenceIds: List<String>,
        evidenceShas: List<String>,
        appCommit: String,
        catalogBuildId: String,
        verifiedAt: Long,
        validFrom: Long,
        validUntil: Long,
        keyId: String,
        signatureAlgorithm: String
    ): ByteArray {
        val parts = listOf(
            "schemaVersion=$schemaVersion",
            "policyVersion=$policyVersion",
            "certificateId=$certificateId",
            "retailer=$retailer",
            "skuCode=$skuCode",
            "exactMpn=$exactMpn",
            "deviceModelId=$deviceModelId",
            "coreActions=${coreActions.sorted().joinToString(",")}",
            "extendedActions=${extendedActions.sorted().joinToString(",")}",
            "evidenceIds=${evidenceIds.sorted().joinToString(",")}",
            "evidenceShas=${evidenceShas.sorted().joinToString(",")}",
            "appCommit=$appCommit",
            "catalogBuildId=$catalogBuildId",
            "verifiedAt=$verifiedAt",
            "validFrom=$validFrom",
            "validUntil=$validUntil",
            "keyId=$keyId",
            "signatureAlgorithm=$signatureAlgorithm"
        )
        return parts.joinToString("|").toByteArray(Charsets.UTF_8)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Issues a signed certificate for an exact retailer SKU.
     * Returns null if ANY fail-closed validation fails.
     */
    fun issueCertificate(
        retailerSku: RetailerSku,
        evidenceList: List<PhysicalTestEvidence>,
        signer: CertificateSigner,
        appCommit: String,
        catalogBuildId: String,
        validUntilTimestamp: Long,
        deviceType: String = "TV"
    ): RetailCompatibilityCertificate? {
        if (evidenceList.isEmpty()) return null

        val modelId = retailerSku.deviceModelId ?: return null

        // P0-7: evidence MUST belong to the same model as the SKU.
        if (evidenceList.any { it.deviceModelId != modelId }) return null

        // Any failure/regression blocks issuance.
        if (evidenceList.any { it.status.isFailure }) return null

        val required = CoreActionPolicy.coreActionsFor(deviceType)
        val verifiedActions = evidenceList.filter { it.status.isPass }.map { it.actionKey }.toSet()
        val missingActions = required - verifiedActions
        if (missingActions.isNotEmpty()) return null

        val now = System.currentTimeMillis()
        val certificateId = "CERT-${retailerSku.retailer}-${retailerSku.skuCode}-${UUID.randomUUID().toString().take(8)}"
        val evidenceIds = evidenceList.map { it.id }
        val evidenceShas = evidenceList.map { it.physicalSha256 }.sorted()

        val payload = canonicalPayload(
            schemaVersion = 1,
            policyVersion = CoreActionPolicy.POLICY_VERSION,
            certificateId = certificateId,
            retailer = retailerSku.retailer.name,
            skuCode = retailerSku.skuCode,
            exactMpn = retailerSku.mpn,
            deviceModelId = modelId,
            coreActions = verifiedActions,
            extendedActions = emptySet(),
            evidenceIds = evidenceIds,
            evidenceShas = evidenceShas,
            appCommit = appCommit,
            catalogBuildId = catalogBuildId,
            verifiedAt = now,
            validFrom = now,
            validUntil = validUntilTimestamp,
            keyId = signer.keyId,
            signatureAlgorithm = signer.signatureAlgorithm
        )

        return RetailCompatibilityCertificate(
            certificateId = certificateId,
            retailer = retailerSku.retailer,
            skuCode = retailerSku.skuCode,
            exactMpn = retailerSku.mpn,
            deviceModelId = modelId,
            coreActionsVerified = verifiedActions,
            extendedActionsVerified = emptySet(),
            physicalEvidenceShaList = evidenceShas,
            evidenceIds = evidenceIds,
            schemaVersion = 1,
            policyVersion = CoreActionPolicy.POLICY_VERSION,
            appCommit = appCommit,
            catalogBuildId = catalogBuildId,
            verifiedAtTimestamp = now,
            validFromTimestamp = now,
            validUntilTimestamp = validUntilTimestamp,
            keyId = signer.keyId,
            signatureAlgorithm = signer.signatureAlgorithm,
            digitalSignature = hex(signer.sign(payload))
        )
    }

    /**
     * Verifies the real cryptographic signature of a certificate.
     */
    fun verifyCertificate(cert: RetailCompatibilityCertificate, verifier: CertificateVerifier): Boolean {
        if (cert.signatureAlgorithm != verifier.signatureAlgorithm) return false
        if (cert.keyId != verifier.keyId) return false
        val signatureBytes = try {
            hexToBytes(cert.digitalSignature)
        } catch (e: Exception) {
            return false
        }
        if (signatureBytes.isEmpty()) return false
        return try {
            verifier.verify(canonicalPayload(cert), signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    private fun hexToBytes(hexString: String): ByteArray {
        if (hexString.isEmpty() || hexString.length % 2 != 0) return ByteArray(0)
        return ByteArray(hexString.length / 2) {
            hexString.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}