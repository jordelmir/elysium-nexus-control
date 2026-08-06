package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus

data class CandidateScore(
    val codeSetId: String,
    val score: Int,
    val rationale: String
)

object CandidateScorer {
    fun scoreCandidate(candidate: IrCodeSet, targetModel: String? = null): CandidateScore {
        var score = 0
        val reasons = mutableListOf<String>()

        if (!targetModel.isNullOrBlank()) {
            if (candidate.modelPatterns.any { it.equals(targetModel, ignoreCase = true) }) {
                score += 120
                reasons.add("+120 exact model match ($targetModel)")
            } else if (candidate.remoteModels.any { it.equals(targetModel, ignoreCase = true) }) {
                score += 110
                reasons.add("+110 exact remote model match ($targetModel)")
            }
        }

        if (candidate.verification == VerificationStatus.VERIFIED_LAB) {
            score += 50
            reasons.add("+50 VERIFIED_LAB")
        } else if (candidate.verification == VerificationStatus.VERIFIED_COMMUNITY) {
            score += 35
            reasons.add("+35 VERIFIED_COMMUNITY")
        }

        if (candidate.commands.size >= 4) {
            score += 30
            reasons.add("+30 complete remote (${candidate.commands.size} bindings)")
        }

        return CandidateScore(candidate.id, score, reasons.joinToString(", "))
    }
}

/**
 * Ranked IR Candidate Probe Engine for physical connection probing.
 *
 * Implements [IrAction.VOLUME_UP] probing to give immediate visual feedback on TV OSD,
 * ranks candidates by [CandidateScorer] scoring, deduplicates candidates by signal fingerprint,
 * and ensures every probe attempt advances to a distinct candidate code set.
 */
class IrProbeEngine(
    rawCandidates: List<IrCodeSet>,
    targetModel: String? = null
) {
    /**
     * Distinct candidate code sets containing a [IrAction.VOLUME_UP] command,
     * deduplicated by signal fingerprint and ordered by [CandidateScorer] ranking score.
     */
    val candidates: List<IrCodeSet> = rawCandidates
        .filter { IrAction.VOLUME_UP in it.commands }
        .distinctBy { candidate ->
            val signal = candidate.commands.getValue(IrAction.VOLUME_UP)
            fingerprintSignal(signal)
        }
        .sortedByDescending { CandidateScorer.scoreCandidate(it, targetModel).score }

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
