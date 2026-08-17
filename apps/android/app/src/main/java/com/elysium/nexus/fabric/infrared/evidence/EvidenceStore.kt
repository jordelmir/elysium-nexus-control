package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence

/**
 * Master Order v0.10 Phase 2 — IMMUTABLE EVIDENCE STORE.
 *
 * Evidence rows are append-only. There is NO update and NO delete API on purpose:
 * a claim can only move forward by appending a new record that supersedes an
 * older one (tombstone), preserving full history for legal/supply-chain review
 * ([com.elysium.nexus.fabric.infrared.legal.LegalEvidenceLedger]).
 *
 * Implementations MUST guarantee:
 * - append() never rewrites or erases previously appended records,
 * - any store loaded from durable media reproduces the exact append sequence,
 * - supersede() adds a new record (tombstone link), never mutates the target.
 */
interface EvidenceStore {

    data class AppendResult(
        val record: PhysicalTestEvidence,
        val seq: Long,
        val supersedesSeq: Long?
    )

    /** Appends a record. Never mutates existing records. Returns the durable sequence number. */
    fun append(record: PhysicalTestEvidence): AppendResult

    /**
     * Appends a superseding record and links it to the superseded one via a
     * tombstone marker. The superseded record REMAINS in the store.
     */
    fun supersede(supersededId: String, replacement: PhysicalTestEvidence): AppendResult

    /** All records in append order. */
    fun all(): List<PhysicalTestEvidence>

    /** True when the store already holds a record with this id. */
    fun contains(id: String): Boolean

    /** Tombstone links: superseded recordId -> superseding recordId. */
    fun tombstoneLinks(): Map<String, String>
}