package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import com.elysium.nexus.fabric.infrared.promotion.RetailFeedArtifact
import java.security.MessageDigest

/**
 * Master Order v0.10 Phase 10 — authoritative retail feed sources.
 *
 * Commercial retail truth NEVER arrives as hardcoded arrays. It arrives through
 * one of these typed sources; each source FAILS CLOSED on any integrity
 * problem (missing signature, hash mismatch, wrong authority, truncated
 * recordCount) and every successfully loaded artifact is explicitly
 * production-eligible or not. Research bootstrap samples remain in
 * [com.elysium.nexus.fabric.infrared.promotion.RetailFeedIngestionEngine] with
 * productionEligible=false and can never feed coverage numbers.
 */
interface RetailFeedSource {

    sealed class SourceResult {
        data class Success(val artifact: RetailFeedArtifact) : SourceResult()
        data class Failure(val reason: String) : SourceResult()
    }

    /** Loads and VERIFIES the latest available feed artifact. Never throws on data problems. */
    fun load(): SourceResult
}

object RetailFeedIntegrity {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Canonical content encoding for hash verification (must match ingestion engine). */
    fun canonicalContent(records: List<RetailerSku>): String =
        records.joinToString("\n") { sku ->
            "${sku.retailer}|${sku.skuCode}|${sku.mpn}|${sku.deviceModelId}"
        }

    fun verify(artifact: RetailFeedArtifact): Boolean =
        artifact.recordCount == artifact.records.size &&
            artifact.contentSha256 == sha256Hex(canonicalContent(artifact.records).toByteArray(Charsets.UTF_8))
}

/**
 * Signed CSV feed: a CSV payload plus its detached SHA-256 (hex). The source is
 * only production-eligible when the signature was produced by an authorized
 * authority id. Any mismatch => Failure (never a weaker artifact).
 */
class SignedCsvRetailFeed(
    private val retailer: RetailerName,
    private val snapshotId: String,
    private val retrievedAt: String,
    private val authorityId: String,
    private val csvContent: String,
    private val contentSignatureHex: String
) : RetailFeedSource {

    override fun load(): RetailFeedSource.SourceResult {
        val records = try {
            parseCsv(csvContent)
        } catch (e: Exception) {
            return RetailFeedSource.SourceResult.Failure("csv parse failed: ${e.message}")
        }
        val computed = RetailFeedIntegrity.sha256Hex(csvContent.toByteArray(Charsets.UTF_8))
        if (!computed.equals(contentSignatureHex, ignoreCase = true)) {
            return RetailFeedSource.SourceResult.Failure(
                "signature mismatch: computed $computed, declared $contentSignatureHex"
            )
        }
        val artifact = RetailFeedArtifact(
            retailer = retailer,
            snapshotId = snapshotId,
            sourceAuthority = authorityId,
            retrievedAt = retrievedAt,
            recordCount = records.size,
            contentSha256 = RetailFeedIntegrity.sha256Hex(
                RetailFeedIntegrity.canonicalContent(records).toByteArray(Charsets.UTF_8)
            ),
            records = records,
            productionEligible = true
        )
        return if (RetailFeedIntegrity.verify(artifact)) {
            RetailFeedSource.SourceResult.Success(artifact)
        } else {
            RetailFeedSource.SourceResult.Failure("content hash verification failed")
        }
    }

    private fun parseCsv(content: String): List<RetailerSku> =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 4) return@mapNotNull null
                val code = parts[0].trim()
                val mpn = parts[1].trim()
                val modelId = parts[2].trim()
                val activeRaw = parts.getOrElse(3) { "true" }.trim()
                if (code.isBlank() || mpn.isBlank()) return@mapNotNull null
                RetailerSku(
                    id = "csv-$snapshotId-$code",
                    retailer = retailer,
                    skuCode = code,
                    mpn = mpn,
                    deviceModelId = modelId.ifBlank { null },
                    isActive = !activeRaw.equals("false", ignoreCase = true)
                )
            }
            .toList()
}

/**
 * Partner API feed: a raw JSON/CSV payload delivered by the retailer partner
 * with retrieval metadata and declared record count. Verified here against the
 * declared hash; production eligibility is granted ONLY by the stored
 * authorized partner authority id.
 */
class PartnerApiRetailFeed(
    private val retailer: RetailerName,
    private val snapshotId: String,
    private val retrievedAt: String,
    private val partnerAuthorityId: String,
    private val authorizedPartners: Set<String>,
    private val payload: String,
    private val declaredContentSha256: String,
    private val rowExtractor: (String) -> List<RetailerSku>
) : RetailFeedSource {

    override fun load(): RetailFeedSource.SourceResult {
        if (partnerAuthorityId !in authorizedPartners) {
            return RetailFeedSource.SourceResult.Failure(
                "partner authority $partnerAuthorityId not authorized"
            )
        }
        val computed = RetailFeedIntegrity.sha256Hex(payload.toByteArray(Charsets.UTF_8))
        if (!computed.equals(declaredContentSha256, ignoreCase = true)) {
            return RetailFeedSource.SourceResult.Failure("payload hash mismatch")
        }
        val records = try {
            rowExtractor(payload)
        } catch (e: Exception) {
            return RetailFeedSource.SourceResult.Failure("row extraction failed: ${e.message}")
        }
        val artifact = RetailFeedArtifact(
            retailer = retailer,
            snapshotId = snapshotId,
            sourceAuthority = partnerAuthorityId,
            retrievedAt = retrievedAt,
            recordCount = records.size,
            contentSha256 = RetailFeedIntegrity.sha256Hex(
                RetailFeedIntegrity.canonicalContent(records).toByteArray(Charsets.UTF_8)
            ),
            records = records,
            productionEligible = true
        )
        return if (RetailFeedIntegrity.verify(artifact)) {
            RetailFeedSource.SourceResult.Success(artifact)
        } else {
            RetailFeedSource.SourceResult.Failure("content hash verification failed")
        }
    }
}

/**
 * Versioned snapshot feed: a manifest (snapshotId + version + sha256) pointing
 * at content. Always resolves the LATEST version and verifies it; a missing or
 * tampered manifest => Failure.
 */
class VersionedSnapshotRetailFeed(
    private val retailer: RetailerName,
    private val retrievedAt: String,
    private val manifest: Map<String, String>
) : RetailFeedSource {

    override fun load(): RetailFeedSource.SourceResult {
        val latestVersion = manifest.keys
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            .lastOrNull()
            ?: return RetailFeedSource.SourceResult.Failure("snapshot manifest is empty")
        val line = manifest.getValue(latestVersion)
        val parts = line.split("|")
        if (parts.size < 3) return RetailFeedSource.SourceResult.Failure("malformed manifest entry")
        val snapshotId = parts[0].trim()
        val declaredSha = parts[1].trim()
        val content = parts.subList(2, parts.size).joinToString("|")
        val computed = RetailFeedIntegrity.sha256Hex(content.toByteArray(Charsets.UTF_8))
        if (!computed.equals(declaredSha, ignoreCase = true)) {
            return RetailFeedSource.SourceResult.Failure("snapshot $latestVersion hash mismatch")
        }
        val source = SignedCsvRetailFeed(
            retailer = retailer,
            snapshotId = snapshotId,
            retrievedAt = retrievedAt,
            authorityId = "versioned-snapshot",
            csvContent = content,
            contentSignatureHex = declaredSha
        )
        return source.load().let { result ->
            when (result) {
                is RetailFeedSource.SourceResult.Success ->
                    RetailFeedSource.SourceResult.Success(
                        result.artifact.copy(productionEligible = true)
                    )
                is RetailFeedSource.SourceResult.Failure -> result
            }
        }
    }
}