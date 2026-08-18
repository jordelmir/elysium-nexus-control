package com.elysium.nexus.core.oracle

import com.elysium.nexus.core.oracle.IROracleEngine.OracleCandidate
import com.elysium.nexus.core.oracle.IROracleEngine.OracleVerdict
import com.elysium.nexus.core.oracle.IROracleEngine.TrialRecord
import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import com.elysium.nexus.fabric.infrared.evidence.JsonLineEvidenceStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 26 — the persisted oracle ledger: append-only JSONL with contiguous
 * sequence fail-closed, plus the promoter that ONLY turns unanimous
 * confirmed runs into REAL_DEVICE_OBSERVED catalogue evidence.
 */
class OracleEvidenceLedgerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun confirmedEntry(
        eventId: String = "oracle-tv1-VOLUME_UP-1",
        trials: Int = 3,
        verdict: String = "CONFIRMED",
        physicalSha: String = "aabbcc"
    ): OracleEvidenceLedger.LedgerEntry {
        val records = (0 until trials).map { i ->
            TrialRecord(
                trialIndex = i,
                beforeRawVolume = 10,
                afterRawVolume = 11,
                restoredRawVolume = 10,
                beforeMuted = false,
                afterMuted = false,
                restoredMuted = false,
                changeOk = true,
                reversalOk = true,
                failReason = null
            )
        }
        return OracleEvidenceLedger.LedgerEntry(
            seq = 0,
            eventId = eventId,
            tvDeviceId = "tv-1",
            actionKey = IROracleEngine.ACTION_VOLUME_UP,
            signalId = "vol-up",
            inverseSignalId = "vol-up-back",
            physicalSha256 = physicalSha,
            carrierHz = 38_000,
            catalogBuildId = "build-42",
            transmitterHardware = "elysium-bridge-ir",
            observationHardware = "tv-node-audio-manager",
            verdict = verdict,
            trialsTotal = trials,
            trialsOk = if (verdict == "CONFIRMED") trials else trials - 1,
            firstFailure = if (verdict == "CONFIRMED") null else "no observed change",
            trials = records,
            timestampMillis = 1_700_000_000_000L
        )
    }

    private fun ledgerFile(name: String): File = File(tmp.root, name)

    @Test
    fun `append and reload reproduce the exact order`() {
        val file = ledgerFile("oracle.jsonl")
        val ledger = OracleEvidenceLedger(file)
        ledger.append(confirmedEntry("e1", trials = 3))
        ledger.append(confirmedEntry("e2", trials = 2))

        val reopened = OracleEvidenceLedger(file)
        val all = reopened.all()

        assertEquals(2, all.size)
        assertEquals(listOf(1L, 2L), all.map { it.seq })
        assertEquals(listOf("e1", "e2"), all.map { it.eventId })
        assertEquals(2, all.last().trials.size)
        assertTrue(all.last().trials.all { it.passed })
    }

    @Test
    fun `non contiguous sequence fails closed`() {
        val file = ledgerFile("oracle.jsonl")
        val ledger = OracleEvidenceLedger(file)
        ledger.append(confirmedEntry("e1"))
        // tamper: hand-append a line with a wrong seq
        file.appendText(
            """{"seq":7,"eventId":"evil","tvDeviceId":"","actionKey":"","signalId":"","inverseSignalId":"","physicalSha256":"","carrierHz":0,"catalogBuildId":"","transmitterHardware":"","observationHardware":"","verdict":"UNCONFIRMED","trialsTotal":1,"trialsOk":0,"firstFailure":null,"trials":[],"timestampMillis":0}""" + "\n"
        )
        try {
            OracleEvidenceLedger(file)
            throw AssertionError("expected contiguity failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("not contiguous"))
        }
    }

    @Test
    fun `duplicate event id is refused`() {
        val ledger = OracleEvidenceLedger(ledgerFile("oracle.jsonl"))
        ledger.append(confirmedEntry("dup"))
        try {
            ledger.append(confirmedEntry("dup"))
            throw AssertionError("expected duplicate rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already recorded"))
        }
    }

    @Test
    fun `unconfirmed runs are recorded honestly with failure detail`() {
        val file = ledgerFile("oracle.jsonl")
        val ledger = OracleEvidenceLedger(file)
        ledger.append(confirmedEntry("e-unc", trials = 3, verdict = "UNCONFIRMED"))

        val entry = ledger.all().single()
        assertEquals("UNCONFIRMED", entry.verdict)
        assertEquals(2, entry.trialsOk)
        assertEquals("no observed change", entry.firstFailure)
    }

    @Test
    fun `promoter appends real device observed evidence from unanimous confirmation`() {
        val file = ledgerFile("oracle.jsonl")
        val ledger = OracleEvidenceLedger(file)
        val entry = ledger.append(confirmedEntry("promote-me", trials = 3))

        val evidenceStore = JsonLineEvidenceStore(ledgerFile("evidence.jsonl"))
        val result = OracleEvidencePromoter.promote(
            entry, evidenceStore,
            deviceModelId = "model-1",
            transmitterHardware = "elysium-bridge-ir",
            receiverHardware = "elysium-bridge-ir"
        )

        assertNotNull(result)
        assertEquals(PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED, result!!.record.status)
        assertEquals("aabbcc", result.record.physicalSha256)
        assertEquals(38_000, result.record.measuredCarrierHz)
        assertEquals("oracle-promote-me", result.record.id)
        assertEquals(1L, result.seq)
    }

    @Test
    fun `promoter refuses unconfirmed run`() {
        val entry = confirmedEntry("nope", trials = 3, verdict = "UNCONFIRMED")
        val evidenceStore = JsonLineEvidenceStore(ledgerFile("evidence.jsonl"))

        assertNull(
            OracleEvidencePromoter.promote(
                entry, evidenceStore, "model-1", "tx", "rx"
            )
        )
        assertTrue(evidenceStore.all().isEmpty())
    }

    @Test
    fun `promoter refuses single-trial confirmation below the minimum`() {
        val entry = confirmedEntry("short", trials = 1)
        val evidenceStore = JsonLineEvidenceStore(ledgerFile("evidence.jsonl"))

        assertNull(
            OracleEvidencePromoter.promote(
                entry, evidenceStore, "model-1", "tx", "rx"
            )
        )
        assertTrue(evidenceStore.all().isEmpty())
    }

    @Test
    fun `promoter never duplicates an already promoted event`() {
        val evidenceStore = JsonLineEvidenceStore(ledgerFile("evidence.jsonl"))
        val ledger = OracleEvidenceLedger(ledgerFile("oracle.jsonl"))
        val entry = ledger.append(confirmedEntry("once"))

        OracleEvidencePromoter.promote(entry, evidenceStore, "model-1", "tx", "rx")
        assertNull(
            OracleEvidencePromoter.promote(entry, evidenceStore, "model-1", "tx", "rx")
        )
        assertEquals(1, evidenceStore.all().size)
    }
}