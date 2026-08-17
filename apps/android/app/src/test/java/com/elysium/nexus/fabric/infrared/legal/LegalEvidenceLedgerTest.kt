package com.elysium.nexus.fabric.infrared.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalEvidenceLedgerTest {

    @Test
    fun `state machine allows canonical progression`() {
        assertNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.UNREVIEWED, LegalEvidenceStatus.REVIEW_REQUIRED))
        assertNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.REVIEW_REQUIRED, LegalEvidenceStatus.DOCUMENTED))
        assertNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.DOCUMENTED, LegalEvidenceStatus.SATISFIED))
        assertNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.SATISFIED, LegalEvidenceStatus.REVIEW_REQUIRED))
    }

    @Test
    fun `state machine rejects illegal transitions`() {
        assertNotNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.UNREVIEWED, LegalEvidenceStatus.SATISFIED))
        assertNotNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.REVIEW_REQUIRED, LegalEvidenceStatus.SATISFIED))
        assertNotNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.DOCUMENTED, LegalEvidenceStatus.UNREVIEWED))
        assertNotNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.BLOCKED, LegalEvidenceStatus.DOCUMENTED))
        assertNotNull(LegalEvidenceLedger.transitionGuard(LegalEvidenceStatus.SATISFIED, LegalEvidenceStatus.UNREVIEWED))
    }

    @Test
    fun `blocked is a sink state`() {
        assertTrue(LegalEvidenceLedger.canTransition(LegalEvidenceStatus.UNREVIEWED, LegalEvidenceStatus.BLOCKED))
        assertTrue(!LegalEvidenceLedger.canTransition(LegalEvidenceStatus.BLOCKED, LegalEvidenceStatus.BLOCKED))
    }

    @Test
    fun `fixture entry honors the guard`() {
        val entry = LegalEvidenceEntry(
            id = "probonopd-notification",
            title = "pre-use notification",
            status = LegalEvidenceStatus.DOCUMENTED,
            artifactPath = "ir-data/sources/lock/probonopd-irdb.lock.json",
            obligations = listOf("pre-use notification")
        )
        assertEquals(LegalEvidenceStatus.DOCUMENTED, entry.status)
        assertNull(LegalEvidenceLedger.transitionGuard(entry.status, LegalEvidenceStatus.REVIEW_REQUIRED))
    }
}