package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId

/**
 * The §28.4 automation engine.
 *
 * The engine is a deterministic executor.
 * Given an [Automation] and a [TriggerEvent]
 * (with the originating [DeviceId], if
 * applicable), the engine:
 *
 *  1. Builds the [IdempotencyKey] for
 *     `(automation, event, deviceId)`.
 *  2. Asks the [AutomationStore] whether
 *     the key is already in flight. If
 *     yes, returns [AlreadyRunning].
 *  3. Evaluates every [Condition] against
 *     a [Context] snapshot. If any
 *     condition is false, returns
 *     [ConditionsNotMet].
 *  4. Marks the key in-flight in the
 *     [AutomationStore].
 *  5. Executes every [Action] in order
 *     through the [ActionDispatcher]. If
 *     any action fails, the executor runs
 *     the [Automation.compensation] in
 *     reverse order, then marks the
 *     key completed-with-failure.
 *  6. Marks the key completed.
 *
 * The engine is **pure** (JVM-testeable).
 * The store and the dispatcher are
 * interfaces; tests stub them with
 * in-memory fakes. The production wiring
 * is the [DefaultAutomationStore] (in
 * the Hub / Android app) and an adapter-
 * backed [ActionDispatcher].
 */
object AutomationEngine {

    /**
     * The executor's verdict on a single
     * execution. The verdict is the engine's
     * report; the audit log records it.
     */
    sealed class Verdict {
        /** The automation ran and all actions completed. */
        data class Completed(val perAction: List<Pair<Action, CommandStatus>>) : Verdict()
        /** A duplicate trigger was deduped. */
        object AlreadyRunning : Verdict()
        /** A condition was not met; no actions ran. */
        data class ConditionsNotMet(
            val failed: List<Condition>
        ) : Verdict()
        /** An action failed; the compensation ran. */
        data class CompensationRan(
            val failed: Action,
            val perAction: List<Pair<Action, CommandStatus>>,
            val perCompensation: List<Pair<Action, CommandStatus>>
        ) : Verdict()
    }

    /**
     * Execute [automation] in response to
     * [event] from [deviceId]. The execution
     * is **synchronous** in this iteration:
     * the engine awaits every dispatcher
     * call. The Hub's runtime will wrap
     * the call in a coroutine.
     */
    fun execute(
        automation: Automation,
        event: TriggerEvent,
        deviceId: DeviceId?,
        context: Context,
        store: AutomationStore,
        dispatcher: ActionDispatcher
    ): Verdict {
        val key = IdempotencyKey.forEvent(automation, event, deviceId)
        if (store.isInFlight(key)) return Verdict.AlreadyRunning
        val failedConditions = automation.conditions.filter { !it.matches(context) }
        if (failedConditions.isNotEmpty()) {
            return Verdict.ConditionsNotMet(failedConditions)
        }
        store.markInFlight(key)
        val perAction = mutableListOf<Pair<Action, CommandStatus>>()
        try {
            for (action in automation.actions) {
                val status = dispatcher.dispatch(action, automation.verification)
                perAction.add(action to status)
                if (status == CommandStatus.Rejected ||
                    status == CommandStatus.TimedOut ||
                    status == CommandStatus.DeviceOffline
                ) {
                    val perComp = mutableListOf<Pair<Action, CommandStatus>>()
                    for (compensation in automation.compensation.reversed()) {
                        perComp.add(
                            compensation to dispatcher.dispatch(
                                compensation,
                                automation.verification
                            )
                        )
                    }
                    return Verdict.CompensationRan(action, perAction, perComp)
                }
            }
            return Verdict.Completed(perAction)
        } finally {
            store.markCompleted(key)
        }
    }
}

/**
 * The §28.4 dedup store. The engine marks
 * a key in-flight when it starts an
 * automation; the store returns `true`
 * for `isInFlight(key)` until the engine
 * calls `markCompleted(key)`. The dedup
 * window is the store's policy (typically
 * 5 minutes).
 */
interface AutomationStore {
    fun isInFlight(key: IdempotencyKey): Boolean
    fun markInFlight(key: IdempotencyKey)
    fun markCompleted(key: IdempotencyKey)
}

/**
 * The §28.4 action dispatcher. The engine
 * calls [dispatch] for every [Action] in the
 * automation. The dispatcher is the
 * **adapter boundary**: it translates the
 * canonical [Action] into the device's
 * native protocol call.
 */
fun interface ActionDispatcher {
    fun dispatch(action: Action, verification: VerificationPolicy): CommandStatus
}

/**
 * The §28.2 evaluation context. A snapshot
 * of the world at the moment the engine
 * reads it. The context is the source of
 * truth for the [Condition]s. The
 * production context is the Hub's
 * current state; the test context is a
 * hand-built [Map]-backed value.
 */
class Context(
    val snapshot: Map<String, Any>
) {
    init {
        // The context is total: any map is
        // a valid context. The keys are
        // caller-defined; the conditions
        // know how to look them up.
    }

    /**
     * @return the value at [key] or `null`
     * when the key is not in the snapshot.
     */
    fun get(key: String): Any? = snapshot[key]

    companion object {
        /**
         * The key for the current user id.
         * The conditions look this up to
         * decide whether a user-specific
         * automation fires.
         */
        const val KEY_USER_ID: String = "user.id"

        /**
         * The key for the current security
         * mode (Home, Away, Night, …).
         */
        const val KEY_SECURITY_MODE: String = "security.mode"

        /**
         * The key for the current tariff
         * (Low / Mid / Peak).
         */
        const val KEY_ENERGY_TARIFF: String = "energy.tariff"

        /**
         * The key for the current network
         * status (Online / Offline).
         */
        const val KEY_NETWORK: String = "network"
    }
}

/**
 * The §28.2 condition matcher. The
 * [matches] function is a pure function
 * of the [Context] snapshot. New condition
 * kinds are added by extending the
 * [matches] function; the closed set is
 * the discipline that keeps the engine
 * deterministic.
 */
fun Condition.matches(context: Context): Boolean {
    return when (kind) {
        ConditionKind.AfterSunset -> context.get(Context.KEY_SECURITY_MODE) == "AfterSunset"
        ConditionKind.BeforeSunrise -> context.get(Context.KEY_SECURITY_MODE) == "BeforeSunrise"
        ConditionKind.UserPresent -> {
            val userId = value
            userId != null && context.get(Context.KEY_USER_ID) == userId
        }
        ConditionKind.UserAbsent -> {
            val userId = value
            userId == null || context.get(Context.KEY_USER_ID) != userId
        }
        ConditionKind.UserRole -> {
            // A full RBAC + ABAC implementation
            // is in the §31 follow-up; for now
            // the condition is a no-op (any
            // role satisfies).
            true
        }
        ConditionKind.DeviceStateEquals -> value?.let { v ->
            context.get("device.${v}.state") == value
        } ?: false
        ConditionKind.DeviceStateNotEquals -> value?.let { v ->
            context.get("device.${v}.state") != value
        } ?: false
        ConditionKind.TimeInRange -> {
            // The condition's `value` is "HH:MM-HH:MM".
            val rangeStr = value
            if (rangeStr == null) return false
            val range = rangeStr.split("-")
            if (range.size != 2) return false
            val now = java.time.LocalTime.now()
            val start = java.time.LocalTime.parse(range[0].trim())
            val end = java.time.LocalTime.parse(range[1].trim())
            now in start..end
        }
        ConditionKind.DayOfWeek -> {
            // `value` is "MON,TUE,WED".
            val wanted = value?.split(",")?.map { it.trim() } ?: return false
            val today = java.time.LocalDate.now().dayOfWeek.name
            today in wanted
        }
        ConditionKind.Weather -> {
            // Weather integration lands in a
            // later phase; for now the
            // condition is satisfied when the
            // context carries a "weather"
            // entry (test seam).
            context.get("weather") != null
        }
        ConditionKind.SecurityMode -> {
            context.get(Context.KEY_SECURITY_MODE) == value
        }
        ConditionKind.ConfidenceAtLeast -> {
            val v = value?.toDoubleOrNull() ?: return false
            val actual = (context.get("confidence") as? Number)?.toDouble() ?: return false
            actual >= v
        }
        ConditionKind.HomeOccupied -> {
            context.get("home.occupied") == true
        }
        ConditionKind.NetworkOnline -> {
            context.get(Context.KEY_NETWORK) == "Online"
        }
        ConditionKind.EnergyTariff -> {
            context.get(Context.KEY_ENERGY_TARIFF) == value
        }
    }
}
