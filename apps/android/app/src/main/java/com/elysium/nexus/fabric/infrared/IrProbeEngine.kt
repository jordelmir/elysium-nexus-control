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
        fun fingerprintSignal(signal: IrSignal): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            when (signal) {
                is IrSignal.Raw -> {
                    digest.update("RAW:v1".toByteArray(Charsets.UTF_8))
                    buffer.clear(); buffer.putInt(signal.carrierHz); digest.update(buffer.array())
                    for (us in signal.patternUs) {
                        buffer.clear(); buffer.putInt(us); digest.update(buffer.array())
                    }
                }
                is IrSignal.Encoded -> {
                    digest.update("ENCODED:v1".toByteArray(Charsets.UTF_8))
                    digest.update(signal.protocol.name.toByteArray(Charsets.UTF_8))
                    buffer.clear(); buffer.putInt(signal.carrierHz); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.address); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.subDevice ?: -1); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.command); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.repeats); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.toggle); digest.update(buffer.array())
                }
            }
            val hex = StringBuilder()
            for (b in digest.digest()) {
                hex.append(String.format("%02x", b))
            }
            return hex.toString()
        }
    }
}
