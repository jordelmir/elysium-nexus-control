package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import java.security.MessageDigest

/**
 * Phase 15 — Retail Feed Ingestion (Master Order v0.10 Phases 9/10).
 *
 * NO TEMPLATE-AS-TRUTH. Hardcoded arrays are RESEARCH bootstrap samples only:
 * they can drive development but NEVER count as commercial coverage. Authoritative
 * retail truth arrives as versioned artifacts with source authority, retrieval
 * metadata, recordCount and content hash — [RetailCoverageEngine] refuses any
 * snapshot that is not production-eligible.
 */
data class RetailFeedArtifact(
    val retailer: RetailerName,
    val snapshotId: String,
    val sourceAuthority: String,
    val retrievedAt: String,
    val recordCount: Int,
    val contentSha256: String,
    val records: List<RetailerSku>,
    val productionEligible: Boolean
)

object RetailFeedIngestionEngine {

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun contentHash(records: List<RetailerSku>): String {
        val canonical = records.joinToString("\n") { sku ->
            "${sku.retailer}|${sku.skuCode}|${sku.mpn}|${sku.deviceModelId}"
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * Monge Costa Rica — RESEARCH bootstrap sample (8 hand-picked models).
     * NOT the official 51-pantalla baseline: no authoritative artifact with 51
     * records exists in this repository. productionEligible = false.
     */
    fun getMongeResearchBootstrapSample(): RetailFeedArtifact {
        val skus = listOf(
            RetailerSku(id = "monge-1", retailer = RetailerName.MONGE_CR, skuCode = "UN32H5000", mpn = "UN32H5000FPXPA", deviceModelId = "mod-samsung-32h5000"),
            RetailerSku(id = "monge-2", retailer = RetailerName.MONGE_CR, skuCode = "UN43U8000", mpn = "UN43U8000FPXPA", deviceModelId = "mod-samsung-43u8000"),
            RetailerSku(id = "monge-3", retailer = RetailerName.MONGE_CR, skuCode = "UN55U8000", mpn = "UN55U8000FPXPA", deviceModelId = "mod-samsung-55u8000"),
            RetailerSku(id = "monge-4", retailer = RetailerName.MONGE_CR, skuCode = "UN65U8000", mpn = "UN65U8000FPXPA", deviceModelId = "mod-samsung-65u8000"),
            RetailerSku(id = "monge-5", retailer = RetailerName.MONGE_CR, skuCode = "QN65Q7FAA", mpn = "QN65Q7FAAPXPA", deviceModelId = "mod-samsung-65q7"),
            RetailerSku(id = "monge-6", retailer = RetailerName.MONGE_CR, skuCode = "AW55B4Q", mpn = "AW55B4Q", deviceModelId = "mod-aiwa-55b4q"),
            RetailerSku(id = "monge-7", retailer = RetailerName.MONGE_CR, skuCode = "LT55KM958", mpn = "LT55KM958", deviceModelId = "mod-jvc-55km958"),
            RetailerSku(id = "monge-8", retailer = RetailerName.MONGE_CR, skuCode = "TTS043495KK", mpn = "TTS043495KK", deviceModelId = "mod-telstar-43ggtv")
        )
        return RetailFeedArtifact(
            retailer = RetailerName.MONGE_CR,
            snapshotId = "research-bootstrap-monge-2026-08-14",
            sourceAuthority = "research-bootstrap",
            retrievedAt = "2026-08-14",
            recordCount = skus.size,
            contentSha256 = contentHash(skus),
            records = skus,
            productionEligible = false
        )
    }

    /**
     * El Verdugo Costa Rica — RESEARCH bootstrap sample (6 hand-picked models).
     * productionEligible = false.
     */
    fun getVerdugoResearchBootstrapSample(): RetailFeedArtifact {
        val skus = listOf(
            RetailerSku(id = "verdugo-1", retailer = RetailerName.VERDUGO_CR, skuCode = "RC32QL", mpn = "RC32QL", deviceModelId = "mod-rca-32ql"),
            RetailerSku(id = "verdugo-2", retailer = RetailerName.VERDUGO_CR, skuCode = "TTS043495KK", mpn = "TTS043495KK", deviceModelId = "mod-telstar-43ggtv"),
            RetailerSku(id = "verdugo-3", retailer = RetailerName.VERDUGO_CR, skuCode = "43Q4SV", mpn = "43Q4SV", deviceModelId = "mod-hisense-43q4sv"),
            RetailerSku(id = "verdugo-4", retailer = RetailerName.VERDUGO_CR, skuCode = "UN43U8000", mpn = "UN43U8000FPXPA", deviceModelId = "mod-samsung-43u8000"),
            RetailerSku(id = "verdugo-5", retailer = RetailerName.VERDUGO_CR, skuCode = "AW55B4Q", mpn = "AW55B4Q", deviceModelId = "mod-aiwa-55b4q"),
            RetailerSku(id = "verdugo-6", retailer = RetailerName.VERDUGO_CR, skuCode = "UDL50684VMAX", mpn = "UDL50684VMAX", deviceModelId = "mod-konka-50vmax")
        )
        return RetailFeedArtifact(
            retailer = RetailerName.VERDUGO_CR,
            snapshotId = "research-bootstrap-verdugo-2026-08-14",
            sourceAuthority = "research-bootstrap",
            retrievedAt = "2026-08-14",
            recordCount = skus.size,
            contentSha256 = contentHash(skus),
            records = skus,
            productionEligible = false
        )
    }
}

/**
 * Master Order v0.10 Phase 9/10 — coverage guard.
 *
 * Refuses to compute commercial coverage from non-production-eligible feeds.
 * Research bootstrap samples can NEVER feed retail coverage numbers.
 */
object RetailCoverageEngine {

    /** Returns null (commercial refusal) unless the artifact is production-eligible. */
    fun computeCoverage(
        artifact: RetailFeedArtifact,
        evidenceMap: Map<String, List<com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence>>,
        policy: Set<String> = CoreActionPolicy.TV_CORE_ACTIONS
    ): ClaimPromotionEngine.RetailCoverageResult? {
        if (!artifact.productionEligible) return null
        return ClaimPromotionEngine.computeRetailCoverage(
            retailerName = artifact.retailer.name,
            activeSkus = artifact.records,
            evidenceMap = evidenceMap,
            policy = policy
        )
    }
}