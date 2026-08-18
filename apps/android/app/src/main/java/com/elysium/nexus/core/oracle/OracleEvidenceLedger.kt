package com.elysium.nexus.core.oracle

import com.elysium.nexus.core.oracle.IROracleEngine.OracleResult
import com.elysium.nexus.core.oracle.IROracleEngine.TrialRecord
import com.elysium.nexus.core.oracle.IROracleEngine.OracleVerdict
import java.io.File

/**
 * Master Order v0.10 Phase 26 — PERSISTED LOCAL ORACLE EVIDENCE.
 *
 * Append-only JSONL ledger of every oracle run on this phone (trials,
 * verdicts, failures). Same honesty contract as the catalogue
 * [com.elysium.nexus.fabric.infrared.evidence.EvidenceStore]:
 * no re-write, no delete; loads must reproduce the exact append order and
 * fail closed on non-contiguous sequences (tampering / partial writes).
 *
 * Pure JVM (org.json) so the whole Phase 25/26 causality path is
 * unit-testable without Android.
 */
class OracleEvidenceLedger(private val file: File) {

    /** Full durable record of one oracle run. */
    data class LedgerEntry(
        val seq: Long,
        val eventId: String,
        val tvDeviceId: String,
        val actionKey: String,
        val signalId: String,
        val inverseSignalId: String,
        val physicalSha256: String,
        val carrierHz: Int,
        val catalogBuildId: String,
        val transmitterHardware: String,
        val observationHardware: String,
        val verdict: String,
        val trialsTotal: Int,
        val trialsOk: Int,
        val firstFailure: String?,
        val trials: List<TrialRecord>,
        val timestampMillis: Long
    ) {
        val promoted: Boolean get() = verdict == "CONFIRMED"
    }

    private val entries = mutableListOf<LedgerEntry>()

    init {
        if (file.exists()) load()
    }

    private fun load() {
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            val parsed = parseLine(line)
            if (parsed.seq != entries.size + 1L) {
                throw IllegalStateException(
                    "oracle ledger ${file.name} is not contiguous at seq ${parsed.seq} " +
                        "(expected ${entries.size + 1}) — file was rewritten or corrupted"
                )
            }
            entries.add(parsed)
        }
    }

    private fun parseLine(raw: String): LedgerEntry {
        val decoded = org.json.JSONObject(raw)
        val trialsArray = decoded.getJSONArray("trials")
        val trials = (0 until trialsArray.length()).map { i ->
            val t = trialsArray.getJSONObject(i)
            TrialRecord(
                trialIndex = t.getInt("trialIndex"),
                beforeRawVolume = t.getInt("beforeRawVolume"),
                afterRawVolume = t.getInt("afterRawVolume"),
                restoredRawVolume = t.getInt("restoredRawVolume"),
                beforeMuted = t.getBoolean("beforeMuted"),
                afterMuted = t.getBoolean("afterMuted"),
                restoredMuted = t.getBoolean("restoredMuted"),
                changeOk = t.getBoolean("changeOk"),
                reversalOk = t.getBoolean("reversalOk"),
                failReason = if (t.isNull("failReason")) null else t.getString("failReason")
            )
        }
        return LedgerEntry(
            seq = decoded.getLong("seq"),
            eventId = decoded.getString("eventId"),
            tvDeviceId = decoded.getString("tvDeviceId"),
            actionKey = decoded.getString("actionKey"),
            signalId = decoded.getString("signalId"),
            inverseSignalId = decoded.getString("inverseSignalId"),
            physicalSha256 = decoded.getString("physicalSha256"),
            carrierHz = decoded.getInt("carrierHz"),
            catalogBuildId = decoded.getString("catalogBuildId"),
            transmitterHardware = decoded.getString("transmitterHardware"),
            observationHardware = decoded.getString("observationHardware"),
            verdict = decoded.getString("verdict"),
            trialsTotal = decoded.getInt("trialsTotal"),
            trialsOk = decoded.getInt("trialsOk"),
            firstFailure = if (decoded.isNull("firstFailure")) null else decoded.getString("firstFailure"),
            trials = trials,
            timestampMillis = decoded.getLong("timestampMillis")
        )
    }

    @Synchronized
    fun append(entry: LedgerEntry): LedgerEntry {
        require(entry.eventId.isNotBlank()) { "oracle event id must not be blank" }
        require(entries.none { it.eventId == entry.eventId }) { "oracle event ${entry.eventId} already recorded" }
        val durable = entry.copy(seq = entries.size + 1L)
        writeLine(durable)
        entries.add(durable)
        return durable
    }

    private fun writeLine(entry: LedgerEntry) {
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            throw IllegalStateException("cannot create oracle ledger directory ${file.parentFile}")
        }
        val trialsArray = org.json.JSONArray()
        entry.trials.forEach { t ->
            trialsArray.put(
                org.json.JSONObject()
                    .put("trialIndex", t.trialIndex)
                    .put("beforeRawVolume", t.beforeRawVolume)
                    .put("afterRawVolume", t.afterRawVolume)
                    .put("restoredRawVolume", t.restoredRawVolume)
                    .put("beforeMuted", t.beforeMuted)
                    .put("afterMuted", t.afterMuted)
                    .put("restoredMuted", t.restoredMuted)
                    .put("changeOk", t.changeOk)
                    .put("reversalOk", t.reversalOk)
                    .put("failReason", t.failReason ?: org.json.JSONObject.NULL)
            )
        }
        val payload = org.json.JSONObject()
            .put("seq", entry.seq)
            .put("eventId", entry.eventId)
            .put("tvDeviceId", entry.tvDeviceId)
            .put("actionKey", entry.actionKey)
            .put("signalId", entry.signalId)
            .put("inverseSignalId", entry.inverseSignalId)
            .put("physicalSha256", entry.physicalSha256)
            .put("carrierHz", entry.carrierHz)
            .put("catalogBuildId", entry.catalogBuildId)
            .put("transmitterHardware", entry.transmitterHardware)
            .put("observationHardware", entry.observationHardware)
            .put("verdict", entry.verdict)
            .put("trialsTotal", entry.trialsTotal)
            .put("trialsOk", entry.trialsOk)
            .put("firstFailure", entry.firstFailure ?: org.json.JSONObject.NULL)
            .put("trials", trialsArray)
            .put("timestampMillis", entry.timestampMillis)
        file.appendBytes((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
        java.io.FileOutputStream(file, true).use { it.fd.sync() }
    }

    @Synchronized
    fun all(): List<LedgerEntry> = entries.toList()
}

/**
 * Phase 26 — the ONLY gate between oracle runs and catalogue evidence.
 *
 * Promotion rules (fail-closed, mirror of the Final Truth Gate):
 * - verdict MUST be unanimous Confirmed, with at least
 *   [IROracleEngine.MIN_CONFIRMED_TRIALS] trials,
 * - the evidence is APPENDED (never updated) to the immutable
 *   catalogue [com.elysium.nexus.fabric.infrared.evidence.EvidenceStore]
 *   with status REAL_DEVICE_OBSERVED,
 * - every promoted item records its oracle event id so the physical
 *   evidence lineage is fully traceable.
 *
 * Anything else returns null — no evidence row is ever written for an
 * unconfirmed run.
 */
object OracleEvidencePromoter {

    fun promote(
        ledgerEntry: OracleEvidenceLedger.LedgerEntry,
        evidenceStore: com.elysium.nexus.fabric.infrared.evidence.EvidenceStore,
        deviceModelId: String,
        transmitterHardware: String,
        receiverHardware: String
    ): com.elysium.nexus.fabric.infrared.evidence.EvidenceStore.AppendResult? {
        if (ledgerEntry.verdict != "CONFIRMED") return null
        if (ledgerEntry.trialsOk != ledgerEntry.trialsTotal) return null
        if (ledgerEntry.trialsTotal < IROracleEngine.MIN_CONFIRMED_TRIALS) return null
        if (ledgerEntry.physicalSha256.isBlank()) return null

        if (evidenceStore.contains("oracle-${ledgerEntry.eventId}")) return null

        val evidence = com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence(
            id = "oracle-${ledgerEntry.eventId}",
            deviceModelId = deviceModelId,
            actionKey = ledgerEntry.actionKey,
            signalId = ledgerEntry.signalId,
            physicalSha256 = ledgerEntry.physicalSha256,
            measuredCarrierHz = ledgerEntry.carrierHz,
            transmitterHardware = transmitterHardware,
            receiverHardware = receiverHardware,
            status = com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED
        )
        return evidenceStore.append(evidence)
    }
}