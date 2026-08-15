package com.elysium.nexus.fabric.calibration

import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.IrWaveform
import com.elysium.nexus.fabric.infrared.PagedIrProbeEngine
import com.elysium.nexus.fabric.infrared.ProbeCursor
import com.elysium.nexus.fabric.tv.TvNodeObservation

/**
 * §20 Software-Only IR Oracle.
 *
 * **Purpose:** Use Phone IR + TV Node observation to discover which existing
 * IR signal actually controls the exact physical television.
 *
 * This is a **CORE priority**. It replaces manual user testing of hundreds
 * of candidates whenever machine-verifiable state exists.
 *
 * **Pipeline:**
 * ```
 * Exact TV identity metadata
 *     ↓
 * candidate narrowing (via PagedIrProbeEngine)
 *     ↓
 * candidate IR transmission
 *     ↓
 * TV Node observation
 *     ↓
 * causal challenge (via CausalActionVerifier)
 *     ↓
 * exact working code set
 *     ↓
 * persist evidence
 * ```
 */
class IrOracleCalibrationEngine(
    private val probeEngine: ProbeCursor,
    private val causalVerifier: CausalActionVerifier,
    private val irTransmitter: suspend (IrWaveform) -> IrTransmitResult,
    private val observationSupplier: suspend () -> List<TvNodeObservation>,
    private val stateSupplier: suspend (property: String) -> Float?
) {

    /**
     * Result of an IR oracle calibration session.
     */
    sealed class OracleCalibrationResult {
        data class SuccessExact(
            val signalId: String,
            val codeSetId: String,
            val confidence: Float,
            val evidence: CalibrationEvidence
        ) : OracleCalibrationResult()

        data class Inconclusive(
            val reason: String,
            val lastTestedSignal: String?
        ) : OracleCalibrationResult()

        data class NoObservableState(
            val reason: String
        ) : OracleCalibrationResult()

        data class Exhausted(
            val candidatesTested: Int
        ) : OracleCalibrationResult()

        data class UserConfirmationRequired(
            val action: String
        ) : OracleCalibrationResult()
    }

    /**
     * Evidence collected during a calibration session.
     */
    data class CalibrationEvidence(
        val signalId: String,
        val catalogBuildId: String? = null,
        val waveformSha: String? = null,
        val carrier: Int? = null,
        val transmitResult: IrTransmitResult? = null,
        val observations: List<TvNodeObservation> = emptyList(),
        val verificationResult: CausalActionVerifier.VerificationTrialResult? = null,
        val deviceMetadata: String? = null
    )

    /**
     * Configure a calibration session.
     */
    data class CalibrationConfig(
        val deviceTwin: DeviceTwin,
        /** Safe actions to probe autonomously (§21). */
        val safeActions: List<String> = listOf("VOLUME_UP", "VOLUME_DOWN"),
        /** Whether to perform inverse challenges. */
        val enableInverseChallenge: Boolean = true,
        /** Minimum successful trials before declaring confirmation. */
        val requiredTrials: Int = 3,
        /** Max candidates to test before giving up. */
        val maxCandidates: Int = 200
    )

    /**
     * Run the calibration pipeline: narrow candidates, transmit, observe, verify.
     *
     * @param config calibration configuration
     * @return [OracleCalibrationResult]
     */
    suspend fun calibrate(config: CalibrationConfig): OracleCalibrationResult {
        // Initialize the probe cursor
        val initResult = probeEngine.initialize()
        if (probeEngine.state == com.elysium.nexus.fabric.infrared.CursorState.EXHAUSTED) {
            return OracleCalibrationResult.Exhausted(0)
        }

        var candidatesTested = 0

        // Iterate through candidates
        var candidate = probeEngine.currentCandidate()
        while (candidate != null && candidatesTested < config.maxCandidates) {
            val signal = candidate.commands.values.firstOrNull() ?: run {
                candidate = probeEngine.nextCandidate()
                continue
            }

            candidatesTested++

            // §21: For each trial: freeze candidate, freeze action, record metadata
            val waveform = IrWaveform(
                frequency = signal.frequency ?: 38000,
                pattern = signal.pattern,
                protocol = signal.protocol
            )

            // Record pre-state
            val preState = stateSupplier("volume")

            // Transmit
            val transmitResult = irTransmitter(waveform)

            if (transmitResult !is IrTransmitResult.Success) {
                candidate = probeEngine.nextCandidate()
                continue
            }

            // Collect observations from TV Node
            val observations = observationSupplier()

            // Build trial for causal verification
            val trial = CausalActionVerifier.VerificationTrial(
                signalId = candidate.id,
                expectedDirection = 1,
                preState = preState ?: 0f,
                postState = stateSupplier("volume"),
                observations = observations,
                isInverse = false
            )

            val inverseTrial = if (config.enableInverseChallenge) {
                // Transmit VolumeDown for inverse challenge
                // (simplified — real impl needs separate waveform for VolumeDown)
                null
            } else null

            val result = causalVerifier.verify(
                trials = listOfNotNull(trial, inverseTrial),
                requiredSuccessfulTrials = config.requiredTrials
            )

            when (result) {
                is CausalActionVerifier.VerificationTrialResult.Confirmed -> {
                    val evidence = CalibrationEvidence(
                        signalId = candidate.id,
                        transmitResult = transmitResult,
                        observations = observations,
                        verificationResult = result,
                        carrier = signal.frequency
                    )
                    return OracleCalibrationResult.SuccessExact(
                        signalId = candidate.id,
                        codeSetId = candidate.codeSetId,
                        confidence = result.confidence,
                        evidence = evidence
                    )
                }
                is CausalActionVerifier.VerificationTrialResult.NoObservableState -> {
                    return OracleCalibrationResult.NoObservableState(
                        reason = "TV Node reports no observable state for this action"
                    )
                }
                else -> {
                    // Continue to next candidate
                    candidate = probeEngine.nextCandidate()
                }
            }
        }

        return if (candidatesTested > 0) {
            OracleCalibrationResult.Exhausted(candidatesTested)
        } else {
            OracleCalibrationResult.NoObservableState("No probe candidates available")
        }
    }
}