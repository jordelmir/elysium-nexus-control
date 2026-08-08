package com.elysium.nexus.fabric.automation

import android.util.Log
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * §36 Concrete Scene Registry Implementation.
 *
 * In-memory scene storage with import/export support.
 * Scenes are portable, serializable, and testable.
 *
 * ## Storage
 *
 * Scenes are stored in a [ConcurrentHashMap] for
 * thread-safe access. In production, this would be
 * backed by Room or file storage.
 *
 * ## Import/Export
 *
 * Scenes can be imported from declarative YAML/JSON
 * [SceneDefinition] and exported back.
 *
 * ## Tag-based Discovery
 *
 * Scenes are tagged for organized access:
 * - "movie" — movie mode scenes
 * - "morning" — morning routine scenes
 * - "night" — bedtime scenes
 * - "gaming" — gaming mode scenes
 */
class InMemorySceneRegistry : SceneRegistry {

    private val TAG = "SceneRegistry"
    private val scenes = ConcurrentHashMap<String, Scene>()
    private val scenesFlow = MutableStateFlow<List<Scene>>(emptyList())

    override suspend fun getScene(sceneId: String): Scene? {
        return scenes[sceneId]
    }

    override fun observeScenes(): Flow<List<Scene>> = scenesFlow.asStateFlow()

    override suspend fun saveScene(scene: Scene) {
        scenes[scene.id] = scene
        scenesFlow.value = scenes.values.toList()
        Log.d(TAG, "Scene saved: ${scene.name} (${scene.id})")
    }

    override suspend fun deleteScene(sceneId: String) {
        scenes.remove(sceneId)
        scenesFlow.value = scenes.values.toList()
        Log.d(TAG, "Scene deleted: $sceneId")
    }

    override suspend fun importScene(definition: SceneDefinition): Scene {
        val steps = definition.steps.map { stepDef ->
            ActionStep(
                targetDeviceId = DeviceId(stepDef.device),
                action = parseAction(stepDef.action, stepDef),
                precondition = stepDef.precondition?.let { parsePredicate(it) },
                successCondition = stepDef.successCondition?.let { parsePredicate(it) },
                timeoutMs = stepDef.timeout,
                rollbackAction = stepDef.rollback?.let { parseAction(it.action, it) },
                optional = stepDef.optional,
                retryCount = stepDef.retryCount,
                retryDelayMs = stepDef.retryDelayMs,
                description = stepDef.description
            )
        }

        val scene = Scene(
            name = definition.scene,
            description = definition.description,
            steps = steps,
            tags = definition.tags
        )

        saveScene(scene)
        return scene
    }

    override suspend fun exportScene(sceneId: String): SceneDefinition? {
        val scene = scenes[sceneId] ?: return null

        val stepDefinitions = scene.steps.map { step ->
            StepDefinition(
                device = step.targetDeviceId.value,
                action = step.action::class.simpleName ?: "Unknown",
                precondition = step.precondition?.let { exportPredicate(it) },
                successCondition = step.successCondition?.let { exportPredicate(it) },
                timeout = step.timeoutMs,
                rollback = step.rollbackAction?.let {
                    StepDefinition(
                        device = step.targetDeviceId.value,
                        action = it::class.simpleName ?: "Unknown"
                    )
                },
                optional = step.optional,
                retryCount = step.retryCount,
                retryDelayMs = step.retryDelayMs,
                description = step.description
            )
        }

        return SceneDefinition(
            scene = scene.name,
            description = scene.description,
            steps = stepDefinitions,
            tags = scene.tags
        )
    }

    override suspend fun getScenesByTag(tag: String): List<Scene> {
        return scenes.values.filter { tag in it.tags }
    }

    // ─── Action Parsing ─────────────────────────────────

    private fun parseAction(actionName: String, step: StepDefinition): UniversalAction {
        val targetId = DeviceId(step.device)
        return when (actionName.lowercase()) {
            "power_on", "poweron" -> UniversalAction.PowerOn(targetId)
            "power_off", "poweroff" -> UniversalAction.PowerOff(targetId)
            "volume_up", "volumeup" -> UniversalAction.VolumeUp(targetId)
            "volume_down", "volumedown" -> UniversalAction.VolumeDown(targetId)
            "mute" -> UniversalAction.Mute(targetId)
            "set_volume", "setvolume" -> {
                val level = step.level ?: 0.5f
                UniversalAction.SetVolume(targetId, level)
            }
            "channel_up", "channelup" -> UniversalAction.ChannelUp(targetId)
            "channel_down", "channeldown" -> UniversalAction.ChannelDown(targetId)
            "input_select", "inputselect" -> {
                UniversalAction.InputSelect(targetId, step.inputId ?: "HDMI_1")
            }
            "play" -> UniversalAction.MediaPlay(targetId)
            "pause" -> UniversalAction.MediaPause(targetId)
            "stop" -> UniversalAction.MediaStop(targetId)
            "next" -> UniversalAction.MediaNext(targetId)
            "previous" -> UniversalAction.MediaPrevious(targetId)
            "home" -> UniversalAction.Home(targetId)
            "back" -> UniversalAction.Back(targetId)
            "ok" -> UniversalAction.Ok(targetId)
            "navigate_up" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Up)
            "navigate_down" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Down)
            "navigate_left" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Left)
            "navigate_right" -> UniversalAction.Navigate(targetId, com.elysium.nexus.fabric.canonical.Direction.Right)
            else -> UniversalAction.Custom(targetId, actionName)
        }
    }

    // ─── Predicate Parsing ──────────────────────────────

    private fun parsePredicate(def: PredicateDefinition): StatePredicate? {
        return when (def.type.lowercase()) {
            "device_state" -> {
                if (def.device != null && def.state != null) {
                    StatePredicate.DeviceState(
                        deviceId = DeviceId(def.device),
                        expectedState = parseState(def.state)
                    )
                } else null
            }
            "capability_available" -> {
                if (def.device != null && def.capability != null) {
                    StatePredicate.CapabilityAvailable(
                        deviceId = DeviceId(def.device),
                        capability = def.capability
                    )
                } else null
            }
            "device_reachable" -> {
                if (def.device != null) {
                    StatePredicate.DeviceReachable(
                        deviceId = DeviceId(def.device),
                        maxAgeMs = def.maxAgeMs ?: 30_000L
                    )
                } else null
            }
            "all" -> {
                val predicates = def.predicates?.mapNotNull { parsePredicate(it) } ?: emptyList()
                if (predicates.isNotEmpty()) StatePredicate.All(predicates) else null
            }
            "any" -> {
                val predicates = def.predicates?.mapNotNull { parsePredicate(it) } ?: emptyList()
                if (predicates.isNotEmpty()) StatePredicate.Any(predicates) else null
            }
            else -> null
        }
    }

    private fun parseState(stateStr: String): com.elysium.nexus.fabric.canonical.DeviceState {
        return when {
            stateStr.equals("on", ignoreCase = true) -> com.elysium.nexus.fabric.canonical.DeviceState.OnOff(true)
            stateStr.equals("off", ignoreCase = true) -> com.elysium.nexus.fabric.canonical.DeviceState.OnOff(false)
            stateStr.startsWith("level:", ignoreCase = true) -> {
                val value = stateStr.substringAfter(":").toFloatOrNull() ?: 0.5f
                com.elysium.nexus.fabric.canonical.DeviceState.Level(value)
            }
            else -> com.elysium.nexus.fabric.canonical.DeviceState.Unknown
        }
    }

    private fun exportPredicate(predicate: StatePredicate): PredicateDefinition? {
        return when (predicate) {
            is StatePredicate.DeviceState -> PredicateDefinition(
                type = "device_state",
                device = predicate.deviceId.value,
                state = predicate.expectedState.toString()
            )
            is StatePredicate.CapabilityAvailable -> PredicateDefinition(
                type = "capability_available",
                device = predicate.deviceId.value,
                capability = predicate.capability
            )
            is StatePredicate.DeviceReachable -> PredicateDefinition(
                type = "device_reachable",
                device = predicate.deviceId.value,
                maxAgeMs = predicate.maxAgeMs
            )
            is StatePredicate.Custom -> PredicateDefinition(
                type = "custom",
                state = predicate.description
            )
            is StatePredicate.All -> PredicateDefinition(
                type = "all",
                predicates = predicate.predicates.mapNotNull { exportPredicate(it) }
            )
            is StatePredicate.Any -> PredicateDefinition(
                type = "any",
                predicates = predicate.predicates.mapNotNull { exportPredicate(it) }
            )
        }
    }
}
