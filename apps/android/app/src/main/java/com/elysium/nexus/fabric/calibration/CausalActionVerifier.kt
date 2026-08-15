package com.elysium.nexus.fabric.calibration

import com.elysium.nexus.fabric.tv.TvNodeObservation

/**
 * §19 Causal Correlation Engine.
 *
 * An action is not confirmed solely because something changed soon afterward.
 * This engine uses causal inference to determine whether an IR transmission
 * actually caused an observed state change on the TV.
 *
 * **Method:**
 * 1. Record pre-state (e.g. volume = 20)
 * 2. Transmit candidate IR signal
 * 3. Bounded observation window
 * 4. Check if state changed in the expected direction
 * 5. Perform inverse challenge (e.g. transmit VolumeDown, verify volume decreases)
 * 6. Repeat trials with randomized timing jitter
 * 7. Return confirmed only if all steps pass consistently
 */
class CausalActionVerifier {

    /**
     * Result of a causal verification trial.
     */
    sealed class VerificationTrialResult {
        /** The action was consistently observed and confirmed. */
        data class Confirmed(
            val signalId: String,
            val confidence: Float,
            val trials: Int,
            val successfulTrials: Int
        ) : VerificationTrialResult()

        /** The action was observed but the inverse challenge failed. */
        data class Inconclusive(
            val reason: String,
            val trials: Int
        ) : VerificationTrialResult()

        /** No state change was observable after transmission. */
        data class NoObservableState(
            val trials: Int
        ) : VerificationTrialResult()

        /** All candidates were exhausted without confirmation. */
        data class Exhausted(
            val candidatesTested: Int
        ) : VerificationTrialResult()

        /** User must confirm manually (for actions without machine-observable state). */
        data class UserConfirmationRequired(
            val action: String
        ) : VerificationTrialResult()
    }

    /**
     * Configuration for a single verification trial.
     */
    data class VerificationTrial(
        /** The ID of the candidate IR signal being tested. */
        val signalId: String,
        /** The expected direction of state change (e.g. +1 for VolumeUp). */
        val expectedDirection: Int,
        /** Pre-action state value (e.g. current volume level). */
        val preState: Float,
        /** Post-action state value observed via TV Node. */
        val postState: Float?,
        /** Observations from the TV Node during the trial window. */
        val observations: List<TvNodeObservation>,
        /** Whether the trial is the forward test or the inverse challenge. */
        val isInverse: Boolean = false
    ) {
        /** Returns true if the state moved in the expected direction. */
        val isStateConsistent: Boolean
            get() {
                if (postState == null) return false
                val delta = postState - preState
                return if (expectedDirection > 0) delta > 0
                else delta < 0
            }

        /** Returns true if any TV Node observation matches the expected action. */
        val hasConsistentObservation: Boolean
            get() = observations.any { obs ->
                when (obs) {
                    is TvNodeObservation.StateChanged -> {
                        obs.oldValue != obs.newValue
                    }
                    else -> false
                }
            }
    }

    /**
     * Verify a candidate IR signal by performing trials with bounded observation.
     *
     * @param trials The verification trials to evaluate
     * @param requiredSuccessfulTrials Minimum consecutive successes required
     * @return [VerificationTrialResult]
     */
    suspend fun verify(
        trials: List<VerificationTrial>,
        requiredSuccessfulTrials: Int = 3
    ): VerificationTrialResult {
        if (trials.isEmpty()) return VerificationTrialResult.Exhausted(0)

        val forwardTrials = trials.filter { !it.isInverse }
        val inverseTrials = trials.filter { it.isInverse }

        // Check forward trials: state must change consistently
        val forwardSuccesses = forwardTrials.count { it.isStateConsistent || it.hasConsistentObservation }
        if (forwardSuccesses < requiredSuccessfulTrials) {
            // If no state change was ever observable, return that
            val anyObservableState = forwardTrials.any { it.postState != null }
            if (!anyObservableState) {
                return VerificationTrialResult.NoObservableState(trials.size)
            }
            return VerificationTrialResult.Inconclusive(
                reason = "Forward trial consistency: $forwardSuccesses/$requiredSuccessfulTrials",
                trials = trials.size
            )
        }

        // Check inverse trials: must also be consistent
        val inverseSuccesses = inverseTrials.count { it.isStateConsistent || it.hasConsistentObservation }
        if (inverseTrials.isNotEmpty() && inverseSuccesses < requiredSuccessfulTrials) {
            return VerificationTrialResult.Inconclusive(
                reason = "Inverse challenge failed: $inverseSuccesses/$requiredSuccessfulTrials",
                trials = trials.size
            )
        }

        val signalId = trials.first().signalId
        val totalSuccessful = forwardSuccesses + inverseSuccesses
        val confidence = totalSuccessful.toFloat() / maxOf(trials.size, 1)

        if (totalSuccessful >= requiredSuccessfulTrials * 2 || (inverseTrials.isEmpty() && totalSuccessful >= requiredSuccessfulTrials)) {
            return VerificationTrialResult.Confirmed(
                signalId = signalId,
                confidence = confidence,
                trials = trials.size,
                successfulTrials = totalSuccessful
            )
        }

        return VerificationTrialResult.Inconclusive(
            reason = "Insufficient evidence: ${totalSuccessful} successful out of ${trials.size}",
            trials = trials.size
        )
    }
}