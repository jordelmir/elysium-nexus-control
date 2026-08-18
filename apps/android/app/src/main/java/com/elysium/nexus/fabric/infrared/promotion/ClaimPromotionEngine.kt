package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku

/**
 * Phase 20 — Claim Promotion Engine & Phase 21 — Retail Coverage Engine.
 *
 * Master Order v0.10 (TRUTH CONVERGENCE, Phases 4/5/6):
 * - Claim status is DERIVED from recorded physical evidence, never written.
 * - CORE is a complete per-action matrix (CoreActionPolicy), never a single action.
 * - A regression on ANY CORE action invalidates 100% claims; regressionCount is
 *   COMPUTED from evidence, never hardcoded.
 * - Status comparisons use an explicit partial order, never enum ordinals.
 */
object ClaimPromotionEngine {

    enum class DerivedClaimStatus {
        SOURCE_IMPORTED,
        STRUCTURAL_VALID,
        RUNTIME_EXECUTABLE,
        OPTICAL_TX_VERIFIED,
        INDEPENDENT_DECODE_VERIFIED,
        REAL_DEVICE_VERIFIED,
        HIL_VERIFIED,
        RETAIL_MATRIX_VERIFIED
    }

    data class DerivationResult(
        val status: DerivedClaimStatus,
        val hasRegression: Boolean
    )

    data class CoreMatrixResult(
        val actionResults: Map<String, CoreActionResult>,
        val isCoreComplete: Boolean,
        val hasRegression: Boolean
    )

    data class RetailCoverageResult(
        val retailerName: String,
        val activeSkuCount: Int,
        val knownModelCount: Int,
        val coreVerifiedCount: Int,
        val pendingCount: Int,
        val regressionCount: Int,
        val coveragePercentage: Double,
        val is100PercentCoreVerified: Boolean
    )

    /**
     * Explicit partial order — the ONLY way statuses are compared.
     * Enum ordinal comparison is forbidden as security/commercial policy.
     * Public contract for the declarative policy cross-check (Phase 3).
     */
    val CLAIM_LADDER = listOf(
        DerivedClaimStatus.SOURCE_IMPORTED,
        DerivedClaimStatus.STRUCTURAL_VALID,
        DerivedClaimStatus.RUNTIME_EXECUTABLE,
        DerivedClaimStatus.OPTICAL_TX_VERIFIED,
        DerivedClaimStatus.INDEPENDENT_DECODE_VERIFIED,
        DerivedClaimStatus.REAL_DEVICE_VERIFIED,
        DerivedClaimStatus.HIL_VERIFIED,
        DerivedClaimStatus.RETAIL_MATRIX_VERIFIED
    )

    private fun orderIndex(status: DerivedClaimStatus): Int {
        val idx = CLAIM_LADDER.indexOf(status)
        require(idx >= 0) { "status $status not in partial order" }
        return idx
    }

    fun isAtLeast(status: DerivedClaimStatus, minimum: DerivedClaimStatus): Boolean =
        orderIndex(status) >= orderIndex(minimum)

    /**
     * Derives per-action CORE results for a device from its evidence list.
     *
     * Fail-closed: a REGRESSION/FAILED result for an action dominates any passing
     * evidence for that same action.
     */
    fun deriveCoreMatrix(
        evidenceList: List<PhysicalTestEvidence>,
        policy: Set<String> = CoreActionPolicy.TV_CORE_ACTIONS
    ): CoreMatrixResult {
        val byAction = evidenceList.groupBy { it.actionKey }
        var hasRegression = false

        val results = policy.associateWith { actionKey ->
            val actionEvidence = byAction[actionKey].orEmpty()
            when {
                actionEvidence.isEmpty() -> CoreActionResult.PENDING
                actionEvidence.any { it.status.isFailure } -> {
                    hasRegression = true
                    CoreActionResult.REGRESSION
                }
                actionEvidence.any { it.status.isPass } -> CoreActionResult.PASS
                else -> CoreActionResult.PENDING
            }
        }

        return CoreMatrixResult(
            actionResults = results,
            isCoreComplete = results.values.all { it.isSatisfied },
            hasRegression = hasRegression
        )
    }

    /**
     * Derives claim status strictly from recorded physical test evidence.
     * A regression anywhere in the evidence set is surfaced explicitly.
     */
    fun deriveClaimStatus(evidenceList: List<PhysicalTestEvidence>): DerivationResult {
        if (evidenceList.isEmpty()) {
            return DerivationResult(DerivedClaimStatus.STRUCTURAL_VALID, hasRegression = false)
        }
        val hasRegression = evidenceList.any { it.status.isFailure }
        val hasHil = evidenceList.any { it.status == com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus.HIL_VERIFIED }
        if (hasHil) return DerivationResult(DerivedClaimStatus.HIL_VERIFIED, hasRegression)

        val hasRealDevice = evidenceList.any { it.status == com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED }
        if (hasRealDevice) return DerivationResult(DerivedClaimStatus.REAL_DEVICE_VERIFIED, hasRegression)

        val hasDecode = evidenceList.any { it.status == com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus.INDEPENDENT_DECODE_VERIFIED }
        if (hasDecode) return DerivationResult(DerivedClaimStatus.INDEPENDENT_DECODE_VERIFIED, hasRegression)

        val hasTx = evidenceList.any { it.status == com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus.ON_DEVICE_TRANSMITTED }
        if (hasTx) return DerivationResult(DerivedClaimStatus.OPTICAL_TX_VERIFIED, hasRegression)

        return DerivationResult(DerivedClaimStatus.RUNTIME_EXECUTABLE, hasRegression)
    }

    /**
     * Computes mathematical retail coverage for a retailer's SKU matrix.
     *
     * A SKU counts as CORE verified ONLY when its complete core matrix passes.
     * regressions are counted from evidence: any REGRESSION/FAILED on a CORE
     * action of a known model increments regressionCount and blocks 100%.
     */
    fun computeRetailCoverage(
        retailerName: String,
        activeSkus: List<RetailerSku>,
        evidenceMap: Map<String, List<PhysicalTestEvidence>>,
        policy: Set<String> = CoreActionPolicy.TV_CORE_ACTIONS
    ): RetailCoverageResult {
        val totalActive = activeSkus.size
        if (totalActive == 0) {
            return RetailCoverageResult(
                retailerName = retailerName,
                activeSkuCount = 0,
                knownModelCount = 0,
                coreVerifiedCount = 0,
                pendingCount = 0,
                regressionCount = 0,
                coveragePercentage = 0.0,
                is100PercentCoreVerified = false
            )
        }

        var knownModelCount = 0
        var coreVerifiedCount = 0
        var pendingCount = 0
        var regressionCount = 0

        for (sku in activeSkus) {
            if (sku.deviceModelId.isNullOrBlank()) {
                pendingCount++
                continue
            }
            knownModelCount++
            val evidence = evidenceMap[sku.deviceModelId].orEmpty()
            val matrix = deriveCoreMatrix(evidence, policy)
            if (matrix.isCoreComplete && !matrix.hasRegression) {
                coreVerifiedCount++
            } else if (matrix.hasRegression) {
                regressionCount++
            } else {
                pendingCount++
            }
        }

        val percentage = (coreVerifiedCount.toDouble() / totalActive.toDouble()) * 100.0
        val is100Verified =
            (totalActive == coreVerifiedCount) && (pendingCount == 0) && (regressionCount == 0)

        return RetailCoverageResult(
            retailerName = retailerName,
            activeSkuCount = totalActive,
            knownModelCount = knownModelCount,
            coreVerifiedCount = coreVerifiedCount,
            pendingCount = pendingCount,
            regressionCount = regressionCount,
            coveragePercentage = percentage,
            is100PercentCoreVerified = is100Verified
        )
    }
}