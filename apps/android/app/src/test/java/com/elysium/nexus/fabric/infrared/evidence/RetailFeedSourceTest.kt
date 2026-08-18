package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.RetailerName
import com.elysium.nexus.fabric.infrared.database.model.RetailerSku
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetailFeedSourceTest {

    private val csv = """
        UN43U8000,UN43U8000FPXPA,mod-samsung-43u8000,true
        QN65Q7FAA,QN65Q7FAAPXPA,mod-samsung-65q7,true
        AW55B4Q,AW55B4Q,mod-aiwa-55b4q,false
    """.trimIndent()

    private fun sha256(content: String): String =
        RetailFeedIntegrity.sha256Hex(content.toByteArray(Charsets.UTF_8))

    @Test
    fun `signed csv feed loads when signature matches`() {
        val source = SignedCsvRetailFeed(
            retailer = RetailerName.GOLLO_CR,
            snapshotId = "gollo-2026-08-17",
            retrievedAt = "2026-08-17",
            authorityId = "gollo-partner-api",
            csvContent = csv,
            contentSignatureHex = sha256(csv)
        )
        val result = source.load()
        assertTrue("expected success, got $result", result is RetailFeedSource.SourceResult.Success)
        val artifact = (result as RetailFeedSource.SourceResult.Success).artifact
        assertEquals(3, artifact.recordCount)
        assertTrue(artifact.productionEligible)
        assertEquals(
            RetailFeedIntegrity.sha256Hex(RetailFeedIntegrity.canonicalContent(artifact.records).toByteArray(Charsets.UTF_8)),
            artifact.contentSha256
        )
        assertEquals(
            listOf("csv-gollo-2026-08-17-UN43U8000", "csv-gollo-2026-08-17-QN65Q7FAA"),
            artifact.records.filter { it.isActive }.map { it.id }
        )
        assertEquals(1, artifact.records.count { !it.isActive })
    }

    @Test
    fun `signed csv feed fails on tampered signature`() {
        val source = SignedCsvRetailFeed(
            retailer = RetailerName.GOLLO_CR,
            snapshotId = "gollo-2026-08-17",
            retrievedAt = "2026-08-17",
            authorityId = "gollo-partner-api",
            csvContent = csv,
            contentSignatureHex = "0".repeat(64)
        )
        val result = source.load()
        assertTrue(result is RetailFeedSource.SourceResult.Failure)
        assertTrue((result as RetailFeedSource.SourceResult.Failure).reason.contains("mismatch"))
    }

    @Test
    fun `signed csv feed fails on truncated content`() {
        val source = SignedCsvRetailFeed(
            retailer = RetailerName.GOLLO_CR,
            snapshotId = "gollo-2026-08-17",
            retrievedAt = "2026-08-17",
            authorityId = "gollo-partner-api",
            csvContent = csv,
            contentSignatureHex = sha256(csv).dropLast(4)
        )
        assertTrue(source.load() is RetailFeedSource.SourceResult.Failure)
    }

    @Test
    fun `partner api feed refuses unauthorized authority`() {
        val source = PartnerApiRetailFeed(
            retailer = RetailerName.MONGE_CR,
            snapshotId = "monge-api-1",
            retrievedAt = "2026-08-17",
            partnerAuthorityId = "attacker",
            authorizedPartners = setOf("monge-partner-api"),
            payload = csv,
            declaredContentSha256 = sha256(csv),
            rowExtractor = { SignedCsvRetailFeed(RetailerName.MONGE_CR, "x", "2026-08-17", "x", it, sha256(it)).load()
                .let { r -> if (r is RetailFeedSource.SourceResult.Success) r.artifact.records else emptyList() } }
        )
        val result = source.load()
        assertTrue(result is RetailFeedSource.SourceResult.Failure)
        assertTrue((result as RetailFeedSource.SourceResult.Failure).reason.contains("not authorized"))
    }

    @Test
    fun `partner api feed fails on hash mismatch`() {
        val source = PartnerApiRetailFeed(
            retailer = RetailerName.MONGE_CR,
            snapshotId = "monge-api-1",
            retrievedAt = "2026-08-17",
            partnerAuthorityId = "monge-partner-api",
            authorizedPartners = setOf("monge-partner-api"),
            payload = csv,
            declaredContentSha256 = "f".repeat(64),
            rowExtractor = { emptyList() }
        )
        assertTrue(source.load() is RetailFeedSource.SourceResult.Failure)
    }

    @Test
    fun `versioned snapshot resolves latest and verifies`() {
        val manifest = mapOf(
            "1" to "monge-snap-1|${sha256("old\n")}|old",
            "2" to "monge-snap-2|${sha256(csv)}|$csv"
        )
        val source = VersionedSnapshotRetailFeed(
            retailer = RetailerName.MONGE_CR,
            retrievedAt = "2026-08-17",
            manifest = manifest
        )
        val result = source.load()
        assertTrue("expected success, got $result", result is RetailFeedSource.SourceResult.Success)
        val artifact = (result as RetailFeedSource.SourceResult.Success).artifact
        assertEquals("monge-snap-2", artifact.snapshotId)
        assertEquals(3, artifact.recordCount)
        assertTrue(artifact.productionEligible)
    }

    @Test
    fun `versioned snapshot fails on tampered entry`() {
        val manifest = mapOf(
            "1" to "monge-snap-1|${sha256(csv)}|$csv",
            "2" to "monge-snap-2|abc|tampered"
        )
        val source = VersionedSnapshotRetailFeed(
            retailer = RetailerName.MONGE_CR,
            retrievedAt = "2026-08-17",
            manifest = manifest
        )
        assertTrue(source.load() is RetailFeedSource.SourceResult.Failure)
    }

    @Test
    fun `research bootstrap artifact stays non-production-eligible`() {
        val artifact = com.elysium.nexus.fabric.infrared.promotion.RetailFeedIngestionEngine
            .getMongeResearchBootstrapSample()
        assertTrue(RetailFeedIntegrity.verify(artifact))
        assertEquals("research-bootstrap", artifact.sourceAuthority)
        assertTrue(!artifact.productionEligible)
    }
}