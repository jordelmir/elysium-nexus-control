package com.elysium.nexus.core.oracle

import com.elysium.nexus.tvnode.canonical.EvidenceEvent
import java.security.MessageDigest

/**
 * Master Order v0.10 Phase 25 — SOFTWARE-ONLY IR ORACLE.
 *
 * Turns a physical TV into a causality oracle WITHOUT any hardware receiver:
 * the TV Node's AudioManager is the observation lane, the phone's IR
 * transmitter is the effect lane, and only a FULL before → change →
 * reversal cycle counts as a confirmed trial.
 *
 * Hard rules (mirror of TV-FABRIC.3 / the v0.10 truth convergence):
 * - NEVER claim a code_set correlation from a snapshot alone.
 * - A trial is confirmed ONLY when the observed effect follows the IR burst
 *   AND the paired reversal signal restores the exact prior state.
 * - Verdicts are honest: [OracleVerdict.Confirmed] requires every trial to
 *   pass; anything less is [OracleVerdict.Unconfirmed] with the truthful count.
 *
 * Pure JVM: the transmitter/observer seams are interfaces, so the whole
 * challenge protocol is unit-testable without hardware.
 */
class IROracleEngine(
    private val transmitter: OracleTransmitter,
    private val observer: OracleObserver
) {

    /** A signal from the catalogue paired with its reversal (must exist to confirm). */
    data class OracleCandidate(
        val actionKey: String,
        val signalId: String,
        val inverseSignalId: String,
        val carrierHz: Int,
        val waveform: List<Int>
    ) {
        /** Canonical physical anchor: sha256(carrierHz + waveform bytes). */
        val physicalSha256: String by lazy {
            MessageDigest.getInstance("SHA-256")
                .digest(
                    (carrierHz.toString() + waveform.joinToString(",")).toByteArray(Charsets.UTF_8)
                )
                .joinToString("") { "%02x".format(it) }
        }
    }

    sealed class OracleVerdict {
        /** Every trial completed the full change + reversal cycle on the physical TV. */
        data class Confirmed(val trialsTotal: Int, val trialsOk: Int) : OracleVerdict()

        /** At least one trial failed — real evidence exists but is not unanimous. */
        data class Unconfirmed(val trialsTotal: Int, val trialsOk: Int, val firstFailure: String) : OracleVerdict()

        /** No observation lane, transmitter refused, or the candidate is not reversible. */
        data class Unsupported(val reason: String) : OracleVerdict()
    }

    /** One full change + reversal cycle. */
    data class TrialRecord(
        val trialIndex: Int,
        val beforeRawVolume: Int,
        val afterRawVolume: Int,
        val restoredRawVolume: Int,
        val beforeMuted: Boolean,
        val afterMuted: Boolean,
        val restoredMuted: Boolean,
        val changeOk: Boolean,
        val reversalOk: Boolean,
        val failReason: String?
    ) {
        val passed: Boolean get() = changeOk && reversalOk && failReason == null
    }

    data class OracleResult(
        val verdict: OracleVerdict,
        val trials: List<TrialRecord>
    )

    /**
     * Run the challenge protocol [trials] times against the physical TV.
     *
     * Confirmation semantics: the candidate is [OracleVerdict.Confirmed] only
     * when EVERY trial changed the observed state in the candidate's direction
     * AND the reversal restored it exactly. Any failed trial demotes the whole
     * run to Unconfirmed — the promoter (Phase 26) has no license to promote a
     * partial run.
     */
    fun run(candidate: OracleCandidate, trials: Int = 3): OracleResult {
        require(trials >= 1) { "at least one trial is required" }
        if (candidate.inverseSignalId.isBlank()) {
            return OracleResult(
                OracleVerdict.Unsupported("reversal signal required for confirmation"),
                emptyList()
            )
        }

        val trialRecords = mutableListOf<TrialRecord>()
        var firstFailure: String? = null

        repeat(trials) { index ->
            val before = observer.observe()
                ?: return OracleResult(
                    OracleVerdict.Unsupported("no observation lane (TV Node offline or unsupported)"),
                    trialRecords.toList()
                )
            val fired = transmitter.transmit(candidate.signalId, candidate.carrierHz, candidate.waveform)
            if (!fired) {
                return OracleResult(
                    OracleVerdict.Unsupported("transmitter refused the burst (hardware or license gate)"),
                    trialRecords.toList()
                )
            }
            val after = observer.observe()
                ?: return OracleResult(
                    OracleVerdict.Unconfirmed(
                        trialRecords.size, trialRecords.count { it.passed },
                        "observation lane dropped mid-trial"
                    ),
                    trialRecords.toList()
                )

            val changeOk = when (candidate.actionKey) {
                ACTION_VOLUME_UP -> after.rawVolume > before.rawVolume
                ACTION_VOLUME_DOWN -> after.rawVolume < before.rawVolume
                ACTION_MUTE -> after.isMuted != before.isMuted
                else -> false
            }

            var reversalOk = false
            var failReason: String? = null
            if (!changeOk) {
                failReason = "no observed change in direction of ${keySummary(candidate.actionKey)}"
            } else {
                val firedBack = transmitter.transmit(
                    candidate.inverseSignalId, candidate.carrierHz, candidate.waveform
                )
                if (!firedBack) {
                    failReason = "reversal burst refused by transmitter"
                } else {
                    val restored = observer.observe()
                    if (restored == null) {
                        failReason = "observation lane dropped during reversal"
                    } else {
                        reversalOk = restored.rawVolume == before.rawVolume &&
                            restored.isMuted == before.isMuted
                        if (!reversalOk) {
                            failReason = "reversal did not restore the exact prior state"
                        } else {
                            trialRecords += TrialRecord(
                                trialIndex = index,
                                beforeRawVolume = before.rawVolume,
                                afterRawVolume = after.rawVolume,
                                restoredRawVolume = restored.rawVolume,
                                beforeMuted = before.isMuted,
                                afterMuted = after.isMuted,
                                restoredMuted = restored.isMuted,
                                changeOk = true,
                                reversalOk = true,
                                failReason = null
                            )
                        }
                    }
                }
            }
            if (failReason != null) {
                trialRecords += TrialRecord(
                    trialIndex = index,
                    beforeRawVolume = before.rawVolume,
                    afterRawVolume = after.rawVolume,
                    restoredRawVolume = -1,
                    beforeMuted = before.isMuted,
                    afterMuted = after.isMuted,
                    restoredMuted = before.isMuted,
                    changeOk = changeOk,
                    reversalOk = reversalOk,
                    failReason = failReason
                )
                if (firstFailure == null) firstFailure = failReason
            }
        }

        val ok = trialRecords.count { it.passed }
        val verdict = if (ok == trialRecords.size) {
            OracleVerdict.Confirmed(ok, ok)
        } else {
            OracleVerdict.Unconfirmed(
                trialRecords.size, ok,
                firstFailure ?: "unknown trial failure"
            )
        }
        return OracleResult(verdict, trialRecords.toList())
    }

    /**
     * Phase 13 — maps a fully confirmed run onto the SHARED evidence event
     * consumed by the catalogue promotion engine ([EvidenceEvent]). Called by
     * the Phase 26 promoter ONLY on an unanimous Confirmed verdict.
     */
    fun toEvidenceEvent(
        candidate: OracleCandidate,
        result: OracleResult,
        tvDeviceId: String,
        catalogBuildId: String,
        eventId: String = "oracle-$tvDeviceId-${candidate.actionKey}-${System.currentTimeMillis()}"
    ): EvidenceEvent? {
        val confirmed = result.verdict as? OracleVerdict.Confirmed ?: return null
        val lastOk = result.trials.lastOrNull() ?: return null
        return EvidenceEvent(
            eventId = eventId,
            tvDeviceId = tvDeviceId,
            actionKey = candidate.actionKey,
            signalId = candidate.signalId,
            inverseSignalId = candidate.inverseSignalId,
            physicalSha256 = candidate.physicalSha256,
            carrierHz = candidate.carrierHz,
            catalogBuildId = catalogBuildId,
            source = "software-oracle",
            trialsTotal = confirmed.trialsTotal,
            trialsOk = confirmed.trialsOk,
            beforeRawVolume = lastOk.beforeRawVolume,
            afterRawVolume = lastOk.afterRawVolume,
            restoredRawVolume = lastOk.restoredRawVolume,
            timestampMillis = System.currentTimeMillis()
        )
    }

    private fun keySummary(key: String): String = when (key) {
        ACTION_VOLUME_UP -> "VOLUME_UP"
        ACTION_VOLUME_DOWN -> "VOLUME_DOWN"
        ACTION_MUTE -> "MUTE"
        else -> key
    }

    companion object {
        const val ACTION_VOLUME_UP = "VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "VOLUME_DOWN"
        const val ACTION_MUTE = "MUTE"

        /** Minimum unanimous trials the promoter accepts for REAL_DEVICE_OBSERVED. */
        const val MIN_CONFIRMED_TRIALS = 2
    }
}

/** Effect lane: emits a catalogue IR burst through the physical hardware. */
interface OracleTransmitter {
    fun transmit(signalId: String, carrierHz: Int, waveform: List<Int>): Boolean
}

/** Observation lane: real TV state via the wire (Phase 25 OBSERVE_VOLUME). */
interface OracleObserver {
    fun observe(): VolumeProbeSnapshot?
}

/** Snapshot of the observed TV volume state (from the wire probe). */
data class VolumeProbeSnapshot(
    val rawVolume: Int,
    val isMuted: Boolean
)