package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrCodeSet

/**
 * V0.6.2 PR3 Phase 14 — Cursor over ranked IR probe candidates.
 *
 * Both engines ([IrProbeEngine] eager, [PagedIrProbeEngine] bounded-memory)
 * expose the same cursor contract, so the product probe flow can switch
 * between them without UI changes.
 *
 * Honesty contract:
 * - [nextCandidate] advances by ONE ranked candidate, never skipping.
 * - [selectById] re-positions by exact identity; a miss must NOT silently
 *   select candidate 0 (callers guard via [ProbeRestoreResolver]).
 */
interface ProbeCursor {
    /** Total candidate count (source-level). */
    val totalCandidates: Int

    /** 1-based probe number of the current candidate (for UI "X de Y"). */
    val currentProbeNumber: Int

    /** True while more candidates remain after the current one. */
    val hasMore: Boolean

    /** Currently selected candidate, or null when exhausted. */
    fun currentCandidate(): IrCodeSet?

    /** Advance to the next ranked candidate; null when exhausted. */
    suspend fun nextCandidate(): IrCodeSet?

    /** Re-position by exact candidate ID. Returns false when not found. */
    fun selectById(candidateId: String): Boolean

    /** Reset to the beginning of the sweep. */
    fun reset()
}