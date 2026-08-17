package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimPromotionEngineTest {

    private fun evidence(
        id: String,
        model: String,
        action: String,
        status: PhysicalEvidenceStatus
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

    private val tvPolicy = CoreActionPolicy.TV_CORE_ACTIONS

    @Test
    fun `deriveClaimStatus returns STRUCTURAL_VALID when no evidence exists`() {
        val result = ClaimPromotionEngine.deriveClaimStatus(emptyList())
        assertEquals(ClaimPromotionEngine.DerivedClaimStatus.STRUCTURAL_VALID, result.status)
        assertFalse(result.hasRegression)
    }

    @Test
    fun `deriveClaimStatus returns HIL_VERIFIED when HIL evidence exists`() {
        val evidence = listOf(
            evidence("ev-1", "mod-1", "VOLUME_UP", PhysicalEvidenceStatus.HIL_VERIFIED)
        )
        val result = ClaimPromotionEngine.deriveClaimStatus(evidence)
        assertEquals(ClaimPromotionEngine.DerivedClaimStatus.HIL_VERIFIED, result.status)
    }

    @Test
    fun `deriveClaimStatus surfaces regression explicitly`() {
        val evidence = listOf(
            evidence("ev-1", "mod-1", "VOLUME_UP", PhysicalEvidenceStatus.REGRESSION)
        )
        val result = ClaimPromotionEngine.deriveClaimStatus(evidence)
        assertEquals(ClaimPromotionEngine.DerivedClaimStatus.RUNTIME_EXECUTABLE, result.status)
        assertTrue(result.hasRegression)
    }

    @Test
    fun `single action does NOT make a TV core verified`() {
        val matrix = ClaimPromotionEngine.deriveCoreMatrix(
            listOf(evidence("ev-1", "mod-1", "VOLUME_UP", PhysicalEvidenceStatus.HIL_VERIFIED)),
            tvPolicy
        )
        assertFalse("One action must NEVER yield a complete CORE matrix", matrix.isCoreComplete)
        assertEquals(CoreActionResult.PASS, matrix.actionResults["VOLUME_UP"])
        assertEquals(CoreActionResult.PENDING, matrix.actionResults["POWER_TOGGLE"])
    }

    @Test
    fun `full core matrix passes`() {
        val all = CoreActionPolicy.TV_CORE_ACTIONS.mapIndexed { i, action ->
            evidence("ev-$i", "mod-1", action, PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED)
        }
        val matrix = ClaimPromotionEngine.deriveCoreMatrix(all, tvPolicy)
        assertTrue(matrix.isCoreComplete)
        assertFalse(matrix.hasRegression)
    }

    @Test
    fun `regression on one core action marks the whole matrix regressed`() {
        val evidenceList = CoreActionPolicy.TV_CORE_ACTIONS.mapIndexed { i, action ->
            evidence("ev-$i", "mod-1", action, PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED)
        } + evidence("ev-bad", "mod-1", "VOLUME_DOWN", PhysicalEvidenceStatus.REGRESSION)

        val matrix = ClaimPromotionEngine.deriveCoreMatrix(evidenceList, tvPolicy)
        assertFalse(matrix.isCoreComplete)
        assertTrue(matrix.hasRegression)
        assertEquals(CoreActionResult.REGRESSION, matrix.actionResults["VOLUME_DOWN"])
    }

    @Test
    fun `computeRetailCoverage does not count incomplete matrix as core verified`() {
        val skus = listOf(
            RetailerSku(id = "sku-1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1"),
            RetailerSku(id = "sku-2", retailer = RetailerName.MONGE_CR, skuCode = "AW55B4Q", mpn = "AW55B4Q", deviceModelId = "mod-2")
        )
        val evidenceMap = mapOf(
            "mod-1" to listOf(evidence("ev-1", "mod-1", "VOLUME_UP", PhysicalEvidenceStatus.HIL_VERIFIED))
        )

        val result = ClaimPromotionEngine.computeRetailCoverage("MONGE_CR", skus, evidenceMap)
        assertEquals(2, result.activeSkuCount)
        assertEquals("One action must NOT promote a SKU", 0, result.coreVerifiedCount)
        assertEquals(2, result.pendingCount)
        assertEquals(0, result.regressionCount)
        assertEquals(0.0, result.coveragePercentage, 0.01)
        assertFalse(result.is100PercentCoreVerified)
    }

    @Test
    fun `computeRetailCoverage is 100 percent only with full matrix and no regressions`() {
        val skus = listOf(
            RetailerSku(id = "sku-1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1"),
            RetailerSku(id = "sku-2", retailer = RetailerName.MONGE_CR, skuCode = "AW55B4Q", mpn = "AW55B4Q", deviceModelId = "mod-2")
        )
        val fullEvidence = CoreActionPolicy.TV_CORE_ACTIONS.mapIndexed { i, action ->
            evidence("ev-f-$i", "mod-1", action, PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED)
        }
        val fullEvidence2 = CoreActionPolicy.TV_CORE_ACTIONS.mapIndexed { i, action ->
            evidence("ev-g-$i", "mod-2", action, PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED)
        }
        val evidenceMap = mapOf(
            "mod-1" to fullEvidence,
            "mod-2" to fullEvidence2
        )

        val result = ClaimPromotionEngine.computeRetailCoverage("MONGE_CR", skus, evidenceMap)
        assertEquals(2, result.coreVerifiedCount)
        assertEquals(0, result.pendingCount)
        assertEquals(0, result.regressionCount)
        assertEquals(100.0, result.coveragePercentage, 0.01)
        assertTrue(result.is100PercentCoreVerified)
    }

    @Test
    fun `regression on one core action breaks 100 percent claim`() {
        val skus = listOf(
            RetailerSku(id = "sku-1", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-1")
        )
        val evidenceList = CoreActionPolicy.TV_CORE_ACTIONS.mapIndexed { i, action ->
            evidence("ev-$i", "mod-1", action, PhysicalEvidenceStatus.HIL_VERIFIED)
        } + evidence("ev-bad", "mod-1", "VOLUME_DOWN", PhysicalEvidenceStatus.REGRESSION)

        val result = ClaimPromotionEngine.computeRetailCoverage("MONGE_CR", skus, mapOf("mod-1" to evidenceList))
        assertEquals(0, result.coreVerifiedCount)
        assertEquals(1, result.regressionCount)
        assertEquals(0.0, result.coveragePercentage, 0.01)
        assertFalse("Regression must block 100%", result.is100PercentCoreVerified)
    }

    @Test
    fun `isAtLeast uses explicit partial order not ordinals`() {
        assertTrue(
            ClaimPromotionEngine.isAtLeast(
                ClaimPromotionEngine.DerivedClaimStatus.HIL_VERIFIED,
                ClaimPromotionEngine.DerivedClaimStatus.REAL_DEVICE_VERIFIED
            )
        )
        assertFalse(
            ClaimPromotionEngine.isAtLeast(
                ClaimPromotionEngine.DerivedClaimStatus.RUNTIME_EXECUTABLE,
                ClaimPromotionEngine.DerivedClaimStatus.HIL_VERIFIED
            )
        )
    }
}