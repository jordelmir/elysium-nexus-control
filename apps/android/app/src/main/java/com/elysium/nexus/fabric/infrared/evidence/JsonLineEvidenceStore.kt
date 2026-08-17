package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Master Order v0.10 Phase 2 — append-only JSONL evidence store backed by durable media.
 *
 * - Every record is appended to the OPEN-ENDED file; the file is fsync'd before
 *   the append returns, so a confirmed append survives process death.
 * - Rewrites are structurally impossible: [FileOutputStream] opens in APPEND
 *   mode and the store refuses to load from a file that was rewritten
 *   (sequence numbers must be contiguous from 1).
 * - [supersede] writes a tombstone record that references the superseded id,
 *   without ever touching the superseded line.
 *
 * The serialization format is purposefully neutral (JSONL): it doubles as the
 * durable format for legal review and for the Python evidence ledger tooling.
 */
class JsonLineEvidenceStore(private val file: File) : EvidenceStore {

    private data class Line(
        val seq: Long,
        val supersedesSeq: Long?,
        val record: PhysicalTestEvidence
    )

    private val lines = mutableListOf<Line>()
    private val indexById = hashMapOf<String, Long>()
    private val links = linkedMapOf<Long, String>()

    init {
        if (file.exists()) load()
    }

    private fun load() {
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            val parsed = parseLine(line)
            if (parsed.seq != lines.size + 1L) {
                throw IllegalStateException(
                    "evidence store ${file.name} is not contiguous at seq ${parsed.seq} " +
                        "(expected ${lines.size + 1}) — file was rewritten or corrupted"
                )
            }
            lines.add(parsed)
            indexById[parsed.record.id] = parsed.seq
            parsed.supersedesSeq?.let { links[it] = parsed.record.id }
        }
    }

    private fun parseLine(raw: String): Line {
        val decoded = org.json.JSONObject(raw)
        val record = PhysicalTestEvidence(
            id = decoded.getString("id"),
            deviceModelId = decoded.getString("deviceModelId"),
            actionKey = decoded.getString("actionKey"),
            signalId = decoded.getString("signalId"),
            physicalSha256 = decoded.getString("physicalSha256"),
            measuredCarrierHz = decoded.getInt("measuredCarrierHz"),
            transmitterHardware = decoded.optString("transmitterHardware", ""),
            receiverHardware = decoded.optString("receiverHardware", ""),
            verifiedAtTimestamp = decoded.getLong("verifiedAtTimestamp"),
            status = PhysicalEvidenceStatusByName.require(decoded.getString("status"))
        )
        val supersedesSeq = if (decoded.isNull("supersedesSeq")) null else decoded.optLong("supersedesSeq")
        return Line(decoded.getLong("seq"), supersedesSeq, record)
    }

    private fun encode(line: Line): String {
        val payload = org.json.JSONObject()
            .put("seq", line.seq)
            .put("supersedesSeq", line.supersedesSeq ?: org.json.JSONObject.NULL)
            .put("id", line.record.id)
            .put("deviceModelId", line.record.deviceModelId)
            .put("actionKey", line.record.actionKey)
            .put("signalId", line.record.signalId)
            .put("physicalSha256", line.record.physicalSha256)
            .put("measuredCarrierHz", line.record.measuredCarrierHz)
            .put("transmitterHardware", line.record.transmitterHardware)
            .put("receiverHardware", line.record.receiverHardware)
            .put("verifiedAtTimestamp", line.record.verifiedAtTimestamp)
            .put("status", line.record.status.name)
        return payload.toString()
    }

    @Synchronized
    override fun append(record: PhysicalTestEvidence): EvidenceStore.AppendResult {
        require(record.id.isNotBlank()) { "evidence id must not be blank" }
        require(!indexById.containsKey(record.id)) { "evidence ${record.id} already exists; use supersede()" }
        val seq = lines.size + 1L
        val line = Line(seq = seq, supersedesSeq = null, record = record)
        writeLine(line)
        lines.add(line)
        indexById[record.id] = seq
        return EvidenceStore.AppendResult(record, seq, supersedesSeq = null)
    }

    @Synchronized
    override fun supersede(
        supersededId: String,
        replacement: PhysicalTestEvidence
    ): EvidenceStore.AppendResult {
        val supersededSeq = indexById[supersededId]
            ?: throw IllegalArgumentException("cannot supersede unknown evidence $supersededId")
        require(replacement.id != supersededId) { "replacement must have a fresh id" }
        require(!indexById.containsKey(replacement.id)) { "replacement ${replacement.id} already exists" }
        val seq = lines.size + 1L
        val line = Line(seq = seq, supersedesSeq = supersededSeq, record = replacement)
        writeLine(line)
        lines.add(line)
        indexById[replacement.id] = seq
        links[supersededSeq] = replacement.id
        return EvidenceStore.AppendResult(replacement, seq, supersedesSeq = supersededSeq)
    }

    private fun writeLine(line: Line) {
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            throw IllegalStateException("cannot create evidence store directory ${file.parentFile}")
        }
        FileOutputStream(file, true).use { fos ->
            fos.write(encode(line).toByteArray(StandardCharsets.UTF_8))
            fos.write('\n'.code)
            fos.fd.sync()
        }
    }

    override fun all(): List<PhysicalTestEvidence> = lines.map { it.record }

    override fun contains(id: String): Boolean = indexById.containsKey(id)

    override fun tombstoneLinks(): Map<String, String> {
        val bySeq = lines.associateBy { it.seq }
        return links.entries.associate { (supersededSeq, newId) ->
            (bySeq[supersededSeq]?.record?.id ?: supersededSeq.toString()) to newId
        }
    }
}

private object PhysicalEvidenceStatusByName {
    fun require(name: String): com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus {
        return com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
            .entries.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("unknown evidence status $name")
    }
}