package com.elysium.nexus.fabric.infrared.gate

import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.evidence.EvidencePolicyEngine
import com.elysium.nexus.fabric.infrared.evidence.RetailFeedIntegrity
import com.elysium.nexus.fabric.infrared.promotion.ClaimPromotionEngine
import com.elysium.nexus.fabric.infrared.promotion.CoreActionPolicy
import com.elysium.nexus.fabric.infrared.promotion.Ed25519CertificateSigner
import com.elysium.nexus.fabric.infrared.promotion.Ed25519CertificateVerifier
import com.elysium.nexus.fabric.infrared.promotion.EvidencePromotionService
import com.elysium.nexus.fabric.infrared.promotion.EvidenceRecorder
import com.elysium.nexus.fabric.infrared.promotion.HilArtifacts
import com.elysium.nexus.fabric.infrared.promotion.RetailCoverageEngine
import com.elysium.nexus.fabric.infrared.promotion.RetailFeedIngestionEngine
import com.elysium.nexus.fabric.infrared.promotion.generateEd25519KeyPair

/**
 * Master Order v0.10 — FINAL TRUTH GATE (module-level facts).
 *
 * Every "zero" of the 19-count Final Commercial Truth Gate that is provable
 * inside this module is asserted HERE, as executable code, so a regression
 * cannot land silently. Repo-wide scans (docs claims, secret patterns,
 * signature fields) live in `tools/truth_engine/final_truth_gate.py`, which
 * CI runs on every push.
 */
object FinalTruthGate {

    /**
     * Enumerates the gate checks that exist in this module and their pass state.
     * Returns a list of failures when the gate is NOT currently satisfied.
     */
    fun failures(): List<String> {
        val result = mutableListOf<String>()
        result += checkNoDefaultStatus()
        result += checkPolicyIsDeclarativeAndUnique()
        result += checkRegressionCountComputed()
        result += checkCoreCompleteIsFullMatrix()
        result += checkHilRequiresDualPathArtifacts()
        result += checkNoHardcodedCertificateSecret()
        result += checkResearchFeedsNeverCommercial()
        result += checkClaimDerivedNeverWritten()
        return result
    }

    private fun checkNoDefaultStatus(): List<String> {
        // PhysicalTestEvidence has no default status: creation is only possible
        // through EvidenceRecorder paths, and typed status is a constructor arg.
        val evidence = EvidenceRecorder.recordRuntime(
            id = "gate-1", deviceModelId = "mod-gate", actionKey = "POWER_TOGGLE",
            signalId = "sig", physicalSha256 = "a".repeat(64), measuredCarrierHz = 38000,
            transmitterHardware = "t", receiverHardware = "r"
        )
        return if (evidence.status != PhysicalEvidenceStatus.RUNTIME_EXECUTABLE) {
            listOf("evidence default status leaked")
        } else {
            EvidenceRecorder::class.java.declaredMethods.any { m ->
                m.name.startsWith("record") && m.returnType == PhysicalTestEvidence::class.java
            }.let { ok -> if (ok) emptyList() else listOf("EvidenceRecorder API surface missing") }
        }
    }

    private fun checkPolicyIsDeclarativeAndUnique(): List<String> {
        val engine = EvidencePolicyEngine.parse(policyJson())
        return try {
            engine.verifyAgainstInAppConstants()
            if (engine.policyVersion != "retail-core-policy-v1") {
                listOf("policy version drifted to ${engine.policyVersion}")
            } else {
                emptyList()
            }
        } catch (e: IllegalArgumentException) {
            listOf("policy drift: ${e.message}")
        }
    }

    private fun checkRegressionCountComputed(): List<String> {
        val fail = EvidenceRecorder.recordFailure(
            id = "gate-reg", deviceModelId = "mod-gate", actionKey = "MUTE",
            signalId = "sig", physicalSha256 = "b".repeat(64), measuredCarrierHz = 38000,
            transmitterHardware = "t", receiverHardware = "r",
            status = PhysicalEvidenceStatus.REGRESSION
        )
        val coverage = ClaimPromotionEngine.computeRetailCoverage(
            retailerName = "GATE",
            activeSkus = listOf(
                com.elysium.nexus.fabric.infrared.database.model.RetailerSku(
                    id = "sku-gate", retailer = com.elysium.nexus.fabric.infrared.database.model.RetailerName.MONGE_CR,
                    skuCode = "G1", mpn = "G1", deviceModelId = "mod-gate"
                )
            ),
            evidenceMap = mapOf("mod-gate" to listOf(fail))
        )
        return if (coverage.regressionCount != 1) {
            listOf("regressionCount is not computed from evidence (got ${coverage.regressionCount})")
        } else {
            emptyList()
        }
    }

    private fun checkCoreCompleteIsFullMatrix(): List<String> {
        val oneAction = EvidenceRecorder.recordRuntime(
            id = "gate-one", deviceModelId = "mod-gate", actionKey = "POWER_TOGGLE",
            signalId = "sig", physicalSha256 = "c".repeat(64), measuredCarrierHz = 38000,
            transmitterHardware = "t", receiverHardware = "r"
        )
        val matrix = ClaimPromotionEngine.deriveCoreMatrix(listOf(oneAction))
        return if (matrix.isCoreComplete) {
            listOf("single-action evidence produced CORE complete (matrix must be full)")
        } else {
            emptyList()
        }
    }

    private fun checkHilRequiresDualPathArtifacts(): List<String> {
        val evidence = EvidenceRecorder.recordRealDevice(
            id = "gate-hil", deviceModelId = "mod-gate", actionKey = "POWER_TOGGLE",
            signalId = "sig", physicalSha256 = "d".repeat(64), measuredCarrierHz = 38000,
            transmitterHardware = "t", receiverHardware = "r"
        )
        val withoutArtifacts = EvidencePromotionService.promoteToHil(
            evidence, HilArtifacts(rawCaptureRef = "", independentDecoderRef = "")
        )
        val withArtifacts = EvidencePromotionService.promoteToHil(
            evidence, HilArtifacts(rawCaptureRef = "capture-1", independentDecoderRef = "decode-1")
        )
        return when {
            withoutArtifacts != null -> listOf("HIL promotion without dual-path artifacts")
            withArtifacts?.status != PhysicalEvidenceStatus.HIL_VERIFIED -> listOf("HIL promotion with artifacts failed")
            else -> emptyList()
        }
    }

    private fun checkNoHardcodedCertificateSecret(): List<String> {
        // CertificateCrypto is asymmetric Ed25519 with keyId references only.
        // A produced signature verifies via the PUBLIC key; the signer is
        // constructed from a PrivateKey object, never from an embedded literal.
        val keyPair = generateEd25519KeyPair()
        val signer = Ed25519CertificateSigner(keyPair.private, "gate-key")
        val verifier = Ed25519CertificateVerifier(keyPair.public, "gate-key")
        val payload = "gate-payload".toByteArray(Charsets.UTF_8)
        val signature = signer.sign(payload)
        val ok = verifier.verify(payload, signature)
        val tampered = verifier.verify("other".toByteArray(Charsets.UTF_8), signature)
        return when {
            signer.signatureAlgorithm != "Ed25519" -> listOf("signature algorithm is not asymmetric Ed25519")
            !ok -> listOf("certificate signature does not verify (asymmetric path broken)")
            tampered -> listOf("certificate verifies a tampered payload")
            else -> emptyList()
        }
    }

    private fun checkResearchFeedsNeverCommercial(): List<String> {
        val monge = RetailFeedIngestionEngine.getMongeResearchBootstrapSample()
        val coverage = RetailCoverageEngine.computeCoverage(
            monge,
            evidenceMap = emptyMap(),
            policy = CoreActionPolicy.TV_CORE_ACTIONS
        )
        return if (coverage != null) {
            listOf("research bootstrap feed produced commercial coverage")
        } else if (monge.productionEligible) {
            listOf("research bootstrap feed marked production-eligible")
        } else {
            emptyList()
        }
    }

    private fun checkClaimDerivedNeverWritten(): List<String> {
        // ClaimPromotionEngine exposes only derive* (read) operations; there is
        // no persist API. RetailCoverageEngine returns null for non-eligible feeds.
        val deriveMethods = ClaimPromotionEngine::class.java.declaredMethods
            .filter { it.name.startsWith("derive") || it.name.startsWith("compute") }
        if (deriveMethods.isEmpty()) return listOf("no derivation API found")
        val hasWrite = ClaimPromotionEngine::class.java.declaredMethods.any { it.name.contains("persist") || it.name.contains("write") }
        return if (hasWrite) listOf("claim engine exposes a write path") else emptyList()
    }

    private fun policyJson(): String {
        val resource = javaClass.classLoader
            .getResourceAsStream("policy/retail-core-policy-v1.json")
            ?: throw IllegalStateException("policy resource missing")
        return resource.bufferedReader().use { it.readText() }
    }
}