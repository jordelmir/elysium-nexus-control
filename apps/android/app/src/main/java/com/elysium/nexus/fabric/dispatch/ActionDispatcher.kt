package com.elysium.nexus.fabric.dispatch

import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.ControlEvent
import com.elysium.nexus.fabric.evidence.EventResult
import com.elysium.nexus.fabric.resilience.DisconnectNeutralizer
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.routing.TransportRoute
import com.elysium.nexus.fabric.session.ControlSession
import com.elysium.nexus.fabric.session.PermissionGate
import com.elysium.nexus.fabric.session.PermissionResult
import com.elysium.nexus.fabric.session.SessionManager
import com.elysium.nexus.fabric.session.SessionState
import java.util.UUID

/**
 * The §4.7 unified action dispatcher.
 *
 * Implements the 10-step pipeline from the technical
 * verdict:
 *
 * ```
 * 1. Parse intent → UniversalAction
 * 2. Resolve target → DeviceTwin from DKG
 * 3. Negotiate route → RouteNegotiator picks best
 * 4. Check permissions → PermissionGate
 * 5. Open/reuse session → SessionManager
 * 6. Translate & dispatch → DeviceAdapter.write()
 * 7. Handle disconnect → DisconnectNeutralizer
 * 8. Record evidence → EvidenceStore
 * 9. Fallback on failure → next route in ranked list
 * 10. Neutralize on terminate → release all inputs
 * ```
 *
 * The dispatcher is the **only** public API for
 * sending user intent to a device. UI components
 * and automation rules call [dispatch]; they never
 * call adapters directly.
 */
class ActionDispatcher(
    private val routeNegotiator: RouteNegotiator,
    private val sessionManager: SessionManager,
    private val neutralizer: DisconnectNeutralizer,
    private val evidenceStore: ControlEvidenceStore,
    /** Resolve device ID → twin. Supplied by the DKG layer. */
    private val twinResolver: (DeviceId) -> DeviceTwin?,
    /** Resolve granted permissions at runtime. */
    private val permissionResolver: () -> Set<String>,
    /** Translate a UniversalAction into DeviceState for a given adapter. */
    private val actionTranslator: ActionTranslator = DefaultActionTranslator,
    /** Max fallback retries per dispatch. */
    private val maxRetries: Int = 2
) {
    init {
        require(maxRetries in 0..5) { "maxRetries must be in [0, 5] (got $maxRetries)." }
    }

    /**
     * Dispatch a [UniversalAction] through the full
     * 10-step pipeline.
     *
     * @return [DispatchResult] summarizing the outcome.
     */
    suspend fun dispatch(action: UniversalAction): DispatchResult {
        val startNs = System.nanoTime()

        // ── Step 2: Resolve target twin ─────────────
        val twin = twinResolver(action.targetDeviceId)
            ?: return recordAndReturn(action, Protocol.Unknown, EventResult.NoRoute,
                DispatchResult.NoTarget(action.targetDeviceId), startNs)

        // ── Step 3: Negotiate routes ────────────────
        val routes = routeNegotiator.negotiate(action, twin)
        if (routes.isEmpty()) {
            return recordAndReturn(action, Protocol.Unknown, EventResult.NoRoute,
                DispatchResult.NoRoute(action, twin), startNs)
        }

        // ── Step 4: Check permissions for best route ─
        val grantedPermissions = permissionResolver()

        // ── Step 5: Open/reuse session ──────────────
        val existingSession = sessionManager.sessionFor(action.targetDeviceId)
        var activeSession: ControlSession = if (existingSession == null || existingSession.isTerminated) {
            sessionManager.createSession(
                sessionId = UUID.randomUUID().toString(),
                deviceId = action.targetDeviceId
            )
        } else {
            existingSession
        }

        // ── Steps 6, 9: Try routes with fallback ────
        var lastError: String? = null
        val availableRoutes = routes.filter { it.isAvailable }
        val routesToTry = availableRoutes.take(maxRetries + 1)

        for ((attemptIndex, route) in routesToTry.withIndex()) {
            // Permission check per route
            val permResult = PermissionGate.check(route.protocol, grantedPermissions)
            if (permResult !is PermissionResult.Granted) {
                val missing = when (permResult) {
                    is PermissionResult.Denied -> permResult.missing
                    is PermissionResult.RationaleRequired -> listOf(permResult.permission)
                    else -> emptyList()
                }
                // If this is the last route, return permission denied
                if (attemptIndex == routesToTry.lastIndex) {
                    return recordAndReturn(action, route.protocol, EventResult.PermissionDenied,
                        DispatchResult.PermissionDenied(action, missing), startNs)
                }
                // Otherwise try next route
                lastError = "Permission denied for ${route.protocol}: $missing"
                recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                continue
            }

            // Update session with route
            if (!activeSession.isActive) {
                activeSession = activeSession.transitionTo(SessionState.PermissionCheck)
                activeSession = activeSession.withRoute(route)
                activeSession = activeSession.transitionTo(SessionState.Active)
                sessionManager.updateSession(activeSession)
            }

            // ── Step 6: Translate & dispatch ────────
            val deviceState = actionTranslator.translate(action, route)
            if (deviceState == null) {
                lastError = "Cannot translate action ${action::class.simpleName} for ${route.protocol}"
                if (attemptIndex == routesToTry.lastIndex) {
                    return recordAndReturn(action, route.protocol, EventResult.AdapterError,
                        DispatchResult.TranslationFailed(action, route), startNs)
                }
                recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                continue
            }

            val writeResult = route.adapter.write(action.targetDeviceId, deviceState)

            when (writeResult) {
                is WriteResult.Ok -> {
                    // ── Step 7: Track for neutralization ─
                    neutralizer.trackAction(action)

                    // ── Step 8: Record evidence ─────────
                    activeSession = activeSession.recordActivity()
                    sessionManager.updateSession(activeSession)

                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
                    recordAttempt(action, route.protocol, EventResult.Success, null, startNs)

                    return DispatchResult.Success(
                        action = action,
                        route = route,
                        latencyMs = latencyMs,
                        reportedState = writeResult.reportedState
                    )
                }
                is WriteResult.Error -> {
                    lastError = "Adapter error: ${writeResult.code} - ${writeResult.message}"
                    if (attemptIndex == routesToTry.lastIndex) {
                        return recordAndReturn(action, route.protocol, EventResult.AdapterError,
                            DispatchResult.AdapterFailed(action, route, writeResult), startNs)
                    }
                    // ── Step 9: Fallback ────────────────
                    recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                }
            }
        }

        // All routes exhausted
        return recordAndReturn(action, Protocol.Unknown, EventResult.NoRoute,
            DispatchResult.AllRoutesFailed(action, lastError ?: "All routes exhausted"), startNs)
    }

    /**
     * Terminate the session for [deviceId] and neutralize
     * all inflight inputs. Called on disconnect.
     *
     * ── Step 10: Neutralize on terminate ──
     */
    suspend fun terminateSession(deviceId: DeviceId): List<UniversalAction> {
        val neutralActions = neutralizer.neutralize(deviceId)

        // Dispatch each neutral action (best-effort, no retry)
        for (action in neutralActions) {
            val twin = twinResolver(deviceId) ?: continue
            val routes = routeNegotiator.negotiate(action, twin)
            val route = routes.firstOrNull { it.isAvailable } ?: continue
            val state = actionTranslator.translate(action, route) ?: continue
            route.adapter.write(deviceId, state)

            evidenceStore.record(ControlEvent(
                timestampNs = System.nanoTime(),
                deviceIdHash = ControlEvidenceStore.hashDeviceId(deviceId),
                actionType = action::class.simpleName ?: "Unknown",
                correlationId = action.correlationId,
                protocol = route.protocol,
                result = EventResult.Neutralized
            ))
        }

        sessionManager.terminateSession(deviceId)
        return neutralActions
    }

    /**
     * Terminate all sessions and neutralize everything.
     */
    suspend fun terminateAll(): Map<DeviceId, List<UniversalAction>> {
        val result = mutableMapOf<DeviceId, List<UniversalAction>>()
        val sessions = sessionManager.activeSessions()
        for (session in sessions) {
            val actions = terminateSession(session.deviceId)
            if (actions.isNotEmpty()) {
                result[session.deviceId] = actions
            }
        }
        return result
    }

    // ── Private helpers ─────────────────────────────────

    private fun recordAttempt(
        action: UniversalAction,
        protocol: Protocol,
        result: EventResult,
        error: String?,
        startNs: Long
    ) {
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
        evidenceStore.record(ControlEvent(
            timestampNs = System.nanoTime(),
            deviceIdHash = ControlEvidenceStore.hashDeviceId(action.targetDeviceId),
            actionType = action::class.simpleName ?: "Unknown",
            correlationId = action.correlationId,
            protocol = protocol,
            result = result,
            latencyMs = latencyMs,
            errorMessage = error
        ))
    }

    private fun recordAndReturn(
        action: UniversalAction,
        protocol: Protocol,
        result: EventResult,
        dispatchResult: DispatchResult,
        startNs: Long
    ): DispatchResult {
        recordAttempt(action, protocol, result,
            (dispatchResult as? DispatchResult.AdapterFailed)?.writeError?.message, startNs)
        return dispatchResult
    }
}

/**
 * The result of dispatching a [UniversalAction].
 */
sealed class DispatchResult {
    data class Success(
        val action: UniversalAction,
        val route: TransportRoute,
        val latencyMs: Long,
        val reportedState: DeviceState?
    ) : DispatchResult()

    data class NoTarget(val deviceId: DeviceId) : DispatchResult()
    data class NoRoute(val action: UniversalAction, val target: DeviceTwin) : DispatchResult()
    data class PermissionDenied(val action: UniversalAction, val missing: List<String>) : DispatchResult()
    data class TranslationFailed(val action: UniversalAction, val route: TransportRoute) : DispatchResult()
    data class AdapterFailed(
        val action: UniversalAction,
        val route: TransportRoute,
        val writeError: WriteResult.Error
    ) : DispatchResult()
    data class AllRoutesFailed(val action: UniversalAction, val reason: String) : DispatchResult()
}

/**
 * Translates a [UniversalAction] into a [DeviceState]
 * for a specific route's adapter.
 */
fun interface ActionTranslator {
    fun translate(action: UniversalAction, route: TransportRoute): DeviceState?
}

/**
 * Default translator: maps canonical actions to
 * [DeviceState.IrCommand] for IR routes and
 * [DeviceState.OnOff] / [DeviceState.Level] /
 * [DeviceState.Media] for generic routes.
 */
object DefaultActionTranslator : ActionTranslator {
    override fun translate(action: UniversalAction, route: TransportRoute): DeviceState? {
        if (route.protocol == Protocol.DirectIr || route.protocol == Protocol.HubIr) {
            val cmdCode = when (action) {
                is UniversalAction.PowerOn, is UniversalAction.PowerToggle -> 0x02
                is UniversalAction.PowerOff -> 0x01
                is UniversalAction.VolumeUp -> 0x07
                is UniversalAction.VolumeDown -> 0x06
                is UniversalAction.Mute -> 0x08
                is UniversalAction.ChannelUp -> 0x09
                is UniversalAction.ChannelDown -> 0x0A
                is UniversalAction.Ok -> 0x0B
                is UniversalAction.Back -> 0x0C
                is UniversalAction.Home -> 0x0D
                is UniversalAction.Menu -> 0x0E
                is UniversalAction.MediaPlay -> 0x0F
                is UniversalAction.MediaPause -> 0x10
                is UniversalAction.MediaStop -> 0x11
                else -> null
            } ?: return null
            return DeviceState.IrCommand(
                protocolName = "NEC",
                address = 0,
                command = cmdCode,
                extras = mapOf("action" to action::class.simpleName.orEmpty())
            )
        }
        return when (action) {
            is UniversalAction.PowerOn -> DeviceState.OnOff(isOn = true)
            is UniversalAction.PowerOff -> DeviceState.OnOff(isOn = false)
            is UniversalAction.PowerToggle -> DeviceState.OnOff(isOn = true)
            is UniversalAction.VolumeUp -> DeviceState.Level(value = 0.6f)
            is UniversalAction.VolumeDown -> DeviceState.Level(value = 0.4f)
            is UniversalAction.SetVolume -> DeviceState.Level(value = action.level)
            is UniversalAction.Mute -> DeviceState.Level(value = 0f)
            is UniversalAction.MediaPlay -> DeviceState.Media(playing = true)
            is UniversalAction.MediaPause -> DeviceState.Media(playing = false)
            is UniversalAction.MediaStop -> DeviceState.Media(playing = false)
            is UniversalAction.MediaNext -> DeviceState.Media(playing = true, track = "next")
            is UniversalAction.MediaPrevious -> DeviceState.Media(playing = true, track = "previous")
            is UniversalAction.SetTemperature ->
                DeviceState.Climate(targetCelsius = action.targetCelsius, mode = action.mode)
            is UniversalAction.SetMode ->
                DeviceState.Climate(targetCelsius = 22f, mode = action.mode)
            is UniversalAction.SetFanSpeed -> DeviceState.Level(value = action.level)
            is UniversalAction.Navigate,
            is UniversalAction.Ok,
            is UniversalAction.Back,
            is UniversalAction.Home,
            is UniversalAction.Menu,
            is UniversalAction.ChannelUp,
            is UniversalAction.ChannelDown,
            is UniversalAction.InputSelect,
            is UniversalAction.Custom -> null
        }
    }
}
