package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCertificateEngineTest {

    private val keyPair = generateEd25519KeyPair()
    private val signer = Ed25519CertificateSigner(keyPair.private, "test-key-1")
    private val verifier = Ed25519CertificateVerifier(keyPair.public, "test-key-1")

    private fun evidence(
        id: String,
        model: String,
        action: String,
        status: PhysicalEvidenceStatus = PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED
    ): PhysicalTestEvidence = PhysicalTestEvidence(
        id = id,
        deviceModelId = model,
        actionKey = action,
        signalId = "sig-$id",
        physicalSha256 = "sha-$id",
        measuredCarrierHz = 38000,
        transmitterHardware = "NexusBridge",
        receiverHardware = "HIL-Station-1",
        status = status
    )

    private fun fullMatrix(model: String): List<PhysicalTestEvidence> =
        CoreActionPolicy.TV_CORE_ACTIONS.mapIndexed { i, action ->
            evidence("e-$model-$i", model, action)
        }

    private fun sku(model: String): RetailerSku =
        RetailerSku(id = "s1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = model)

    @Test
    fun `issueCertificate returns null when core actions are incomplete`() {
        val cert = CompatibilityCertificateEngine.issueCertificate(
            retailerSku = sku("mod-1"),
            evidenceList = listOf(evidence("e1", "mod-1", "POWER_TOGGLE")),
            signer = signer,
            appCommit = "abc123",
            catalogBuildId = "cat-1",
            validUntilTimestamp = System.currentTimeMillis() + 86_400_000L
        )
        assertNull("Certificate must fail when core actions are incomplete", cert)
    }

    @Test
    fun `issueCertificate rejects evidence from another device model`() {
        val wrongModelEvidence = fullMatrix("mod-OTHER")
        val cert = CompatibilityCertificateEngine.issueCertificate(
            retailerSku = sku("mod-1"),
            evidenceList = wrongModelEvidence,
            signer = signer,
            appCommit = "abc123",
            catalogBuildId = "cat-1",
            validUntilTimestamp = System.currentTimeMillis() + 86_400_000L
        )
        assertNull("Evidence from another model must NEVER be signed", cert)
    }

    @Test
    fun `issueCertificate rejects evidence with failures or regressions`() {
        val badMatrix = fullMatrix("mod-1").map {
            if (it.actionKey == "VOLUME_DOWN") evidence("e-bad", "mod-1", "VOLUME_DOWN", PhysicalEvidenceStatus.REGRESSION)
            else it
        }
        val cert = CompatibilityCertificateEngine.issueCertificate(
            retailerSku = sku("mod-1"),
            evidenceList = badMatrix,
            signer = signer,
            appCommit = "abc123",
            catalogBuildId = "cat-1",
            validUntilTimestamp = System.currentTimeMillis() + 86_400_000L
        )
        assertNull("REGRESSION evidence must block issuance", cert)
    }

    @Test
    fun `issueCertificate returns signed certificate when full core matrix is verified`() {
        val cert = CompatibilityCertificateEngine.issueCertificate(
            retailerSku = sku("mod-1"),
            evidenceList = fullMatrix("mod-1"),
            signer = signer,
            appCommit = "abc123",
            catalogBuildId = "cat-1",
            validUntilTimestamp = System.currentTimeMillis() + 86_400_000L
        )
        assertNotNull(cert)
        assertTrue(cert!!.certificateId.startsWith("CERT-MONGE_CR-UN55U8000"))
        assertEquals("Ed25519", cert.signatureAlgorithm)
        assertEquals("test-key-1", cert.keyId)
        assertEquals("retail-core-policy-v1", cert.policyVersion)
        assertTrue(CompatibilityCertificateEngine.verifyCertificate(cert, verifier))
    }

    @Test
    fun `tampered certificate signature fails verification`() {
        val cert = CompatibilityCertificateEngine.issueCertificate(
            retailerSku = sku("mod-1"),
            evidenceList = fullMatrix("mod-1"),
            signer = signer,
            appCommit = "abc123",
            catalogBuildId = "cat-1",
            validUntilTimestamp = System.currentTimeMillis() + 86_400_000L
        )
        assertNotNull(cert)
        val tampered = cert!!.copy(appCommit = "tampered")
        assertFalse("Tampered payload must fail signature verification", CompatibilityCertificateEngine.verifyCertificate(tampered, verifier))
    }

    @Test
    fun `verification rejects signatures from a different key`() {
        val otherKey = generateEd25519KeyPair()
        val otherVerifier = Ed25519CertificateVerifier(otherKey.public, "other-key")
        val cert = CompatibilityCertificateEngine.issueCertificate(
            retailerSku = sku("mod-1"),
            evidenceList = fullMatrix("mod-1"),
            signer = signer,
            appCommit = "abc123",
            catalogBuildId = "cat-1",
            validUntilTimestamp = System.currentTimeMillis() + 86_400_000L
        )
        assertNotNull(cert)
        assertFalse("Wrong public key must reject", CompatibilityCertificateEngine.verifyCertificate(cert!!, otherVerifier))
    }
}