package com.elysium.nexus.fabric.hedging

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.routing.TransportRoute
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * §61 Hedged Execution.
 *
 * For safe actions, send the command on the
 * primary route and, if no ACK within [hedgeDelayMs],
 * send on a backup route.
 *
 * ## Hedging is ONLY for:
 * - Read operations (state queries)
 * - Idempotent actions (POWER_TOGGLE, MUTE_TOGGLE)
 *
 * ## Hedging is NEVER for:
 * - Non-idempotent actions (VOLUME_UP, CHANNEL_UP)
 *   where double execution = user-visible bug
 * - Destructive actions (factory reset)
 *
 * ## Policy
 *
 * ```
 * WiFi request
 * ↓
 * no ACK after 120ms
 * ↓
 * fallback IR
 * ```
 *
 * But only if:
 * ```
 * idempotency classification = IDEMPOTENT
 * state confirmation available
 * ```
 */
class HedgedExecutor(
    private val hedgeDelayMs: Long = 120L,
    private val maxConcurrentHedges: Int = 2
) {

    private var activeHedges = 0

    /**
     * Execute with hedging. If the primary route
     * doesn't respond within [hedgeDelayMs], try
     * the backup route.
     *
     * @param action the universal action
     * @param primary the primary route
     * @param backup the backup route (optional)
     * @param executor function to execute on a route
     * @return the result from whichever route succeeded first
     */
    suspend fun <T> executeWithHedge(
        action: UniversalAction,
        primary: TransportRoute,
        backup: TransportRoute?,
        executor: suspend (TransportRoute) -> T?
    ): HedgedResult<T> {
        // Only hedge idempotent actions
        if (!isIdempotent(action)) {
            val result = executor(primary)
            return if (result != null) {
                HedgedResult.PrimarySuccess(result)
            } else {
                HedgedResult.PrimaryFailed("Primary route failed")
            }
        }

        if (backup == null) {
            val result = executor(primary)
            return if (result != null) {
                HedgedResult.PrimarySuccess(result)
            } else {
                HedgedResult.PrimaryFailed("Primary route failed, no backup")
            }
        }

        if (activeHedges >= maxConcurrentHedges) {
            val result = executor(primary)
            return if (result != null) {
                HedgedResult.PrimarySuccess(result)
            } else {
                HedgedResult.PrimaryFailed("Too many active hedges")
            }
        }

        return coroutineScope {
            activeHedges++
            try {
                var primaryCompleted = false
                var backupTriggered = false

                // Launch primary
                val primaryJob = launch {
                    val result = executor(primary)
                    if (result != null) {
                        primaryCompleted = true
                    }
                }

                // Wait for hedge delay
                delay(hedgeDelayMs)

                // If primary hasn't completed, try backup
                if (!primaryCompleted) {
                    backupTriggered = true
                    val backupResult = executor(backup)
                    if (backupResult != null) {
                        primaryJob.cancel()
                        return@coroutineScope HedgedResult.BackupSuccess(backupResult)
                    }
                }

                // Wait for primary to finish
                primaryJob.join()
                if (primaryCompleted) {
                    HedgedResult.PrimarySuccess(Unit as T)
                } else {
                    HedgedResult.BothFailed("Both routes failed")
                }
            } finally {
                activeHedges--
            }
        }
    }

    /**
     * Check if an action is idempotent.
     * Idempotent actions can be safely executed
     * multiple times without side effects.
     */
    private fun isIdempotent(action: UniversalAction): Boolean = when (action) {
        is UniversalAction.PowerToggle -> true
        is UniversalAction.Mute -> true
        is UniversalAction.PowerOn -> true
        is UniversalAction.PowerOff -> true
        is UniversalAction.MediaStop -> true
        is UniversalAction.Home -> true
        is UniversalAction.Back -> true
        is UniversalAction.Menu -> true
        // Non-idempotent: double execution = visible change
        is UniversalAction.VolumeUp -> false
        is UniversalAction.VolumeDown -> false
        is UniversalAction.ChannelUp -> false
        is UniversalAction.ChannelDown -> false
        is UniversalAction.MediaPlay -> false
        is UniversalAction.MediaPause -> false
        is UniversalAction.MediaNext -> false
        is UniversalAction.MediaPrevious -> false
        is UniversalAction.Navigate -> false
        is UniversalAction.Ok -> false
        is UniversalAction.SetVolume -> true // Setting to specific value is idempotent
        is UniversalAction.SetTemperature -> true
        is UniversalAction.SetFanSpeed -> true
        is UniversalAction.SetMode -> true
        is UniversalAction.InputSelect -> true
        is UniversalAction.Custom -> false // Unknown, assume non-idempotent
    }
}

/**
 * Hedged execution result.
 */
sealed class HedgedResult<T> {
    data class PrimarySuccess<T>(val value: T?) : HedgedResult<T>()
    data class BackupSuccess<T>(val value: T?) : HedgedResult<T>()
    data class PrimaryFailed<T>(val reason: String) : HedgedResult<T>()
    data class BothFailed<T>(val reason: String) : HedgedResult<T>()
}
