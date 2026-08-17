package com.elysium.nexus.fabric.infrared.promotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetailFeedTruthTest {

    @Test
    fun `Monge sample is a research bootstrap, not the official 51 baseline`() {
        val sample = RetailFeedIngestionEngine.getMongeResearchBootstrapSample()
        assertEquals("research-bootstrap", sample.sourceAuthority)
        assertFalse("Research sample must never be production-eligible", sample.productionEligible)
        assertTrue("Hardcoded sample is NOT the claimed 51-record baseline", sample.recordCount < 51)
        assertEquals(sample.recordCount, sample.records.size)
        assertTrue("contentSha256 must be present and consistent", sample.contentSha256.isNotBlank())
    }

    @Test
    fun `Verdugo sample is a research bootstrap`() {
        val sample = RetailFeedIngestionEngine.getVerdugoResearchBootstrapSample()
        assertFalse(sample.productionEligible)
        assertEquals(sample.recordCount, sample.records.size)
    }

    @Test
    fun `RetailCoverageEngine refuses research bootstrap samples`() {
        val sample = RetailFeedIngestionEngine.getMongeResearchBootstrapSample()
        val result = RetailCoverageEngine.computeCoverage(sample, evidenceMap = emptyMap())
        assertNull("Research sample must NEVER produce commercial coverage", result)
    }

    @Test
    fun `content hash is deterministic`() {
        val a = RetailFeedIngestionEngine.getMongeResearchBootstrapSample()
        val b = RetailFeedIngestionEngine.getMongeResearchBootstrapSample()
        assertEquals(a.contentSha256, b.contentSha256)
    }
}