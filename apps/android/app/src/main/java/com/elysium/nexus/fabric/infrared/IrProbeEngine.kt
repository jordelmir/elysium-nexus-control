package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal

/**
 * Ranked IR Candidate Probe Engine for physical connection probing.
 *
 * Implements [IrAction.VOLUME_UP] probing to give immediate visual feedback on TV OSD,
 * deduplicates candidates by signal fingerprint, and ensures every probe attempt advances
 * to a distinct candidate code set.
 */
class IrProbeEngine(
    rawCandidates: List<IrCodeSet>
) {
    /**
     * Distinct candidate code sets containing a [IrAction.VOLUME_UP] command,
     * deduplicated by signal fingerprint.
     */
    val candidates: List<IrCodeSet> = rawCandidates
        .filter { IrAction.VOLUME_UP in it.commands }
        .distinctBy { candidate ->
            val signal = candidate.commands.getValue(IrAction.VOLUME_UP)
            fingerprintSignal(signal)
        }

    private var currentIndex = 0

    val totalCandidates: Int get() = candidates.size
    val currentProbeNumber: Int get() = (currentIndex + 1).coerceAtMost(totalCandidates)
    val hasMore: Boolean get() = currentIndex < candidates.size

    /** Get the currently selected candidate code set, or null if exhausted. */
    fun currentCandidate(): IrCodeSet? = candidates.getOrNull(currentIndex)

    /** Advance to the next candidate code set. Returns the candidate consumed or null if exhausted. */
    fun nextCandidate(): IrCodeSet? {
        if (currentIndex >= candidates.size) return null
        val current = candidates.getOrNull(currentIndex)
        currentIndex++
        return current
    }

    /** Reset state back to beginning. */
    fun reset() {
        currentIndex = 0
    }

    companion object {
        fun fingerprintSignal(signal: IrSignal): String = when (signal) {
            is IrSignal.Encoded -> "${signal.protocol}_${signal.address}_${signal.subDevice}_${signal.command}"
            is IrSignal.Raw -> "${signal.carrierHz}_${signal.patternUs.contentHashCode()}"
        }
    }
}
