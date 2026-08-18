package com.elysium.nexus.tvnode.canonical

/**
 * TV Node — canonical contract twin.
 *
 * These types are byte/name-identical twins of the controller's
 * `com.elysium.nexus.fabric.canonical` package, which is the shared
 * wire contract between phone and TV node (JSON encoded). The twin
 * stands until a shared Kotlin module unifies both copies (changelog
 * PHASE_V07 — documented refactor, not silently divergent: changing
 * an enum name here breaks the wire contract on the controller side).
 */

/** Canonical device id (twin of controller DeviceId). Never a physical identity; only metadata join key. */
@JvmInline
value class DeviceId(val value: String) {
    companion object {
        val UNKNOWN: DeviceId = DeviceId("00000000-0000-0000-0000-000000000000")
    }
}

/** Transport protocol spoken by the TV (twin of controller Protocol, as far as TV Node needs it). */
enum class Protocol {
    ElysiumLink, DirectIr, HdmiCec, Ble, WiFi, Matter, Zigbee, ZWave, Mqtt, Onvif
}

/** Navigation direction (twin of controller Direction). */
enum class Direction {
    Up, Down, Left, Right
}

/**
 * The §31.4 action risk class (twin of controller ActionRisk,
 * minimal copy used by the TV Node's honest capability reporting).
 */
enum class ActionRisk {
    Informational, Low, Reversible, PhysicalMotion, PrivacySensitive, SecuritySensitive, HighPower, LifeSafety
}

/**
 * §4.3 canonical capability enum — twin of the controller's
 * closed capability set. The TV Node uses it to report which
 * capabilities it can ACTUALLY observe or execute; it never
 * advertises a capability it cannot back with evidence.
 */
enum class Capability(val defaultRisk: ActionRisk) {
    OnOff(ActionRisk.Low),
    Toggle(ActionRisk.Low),
    Level(ActionRisk.Low),
    Color(ActionRisk.Low),
    ColorTemperature(ActionRisk.Low),
    Temperature(ActionRisk.Low),
    TargetTemperature(ActionRisk.Reversible),
    FanSpeed(ActionRisk.Low),
    Swing(ActionRisk.Low),
    Mode(ActionRisk.Low),
    Timer(ActionRisk.Low),
    OpenClose(ActionRisk.PhysicalMotion),
    Position(ActionRisk.PhysicalMotion),
    Direction(ActionRisk.PhysicalMotion),
    LockUnlock(ActionRisk.SecuritySensitive),
    ArmDisarm(ActionRisk.SecuritySensitive),
    Doorbell(ActionRisk.PrivacySensitive),
    Presence(ActionRisk.PrivacySensitive),
    StartStop(ActionRisk.Low),
    PauseResume(ActionRisk.Low),
    MediaTransport(ActionRisk.Low),
    Volume(ActionRisk.Low),
    Channel(ActionRisk.Low),
    InputSource(ActionRisk.Low),
    Scene(ActionRisk.Low),
    EnergyRead(ActionRisk.Informational),
    EnergyControl(ActionRisk.HighPower),
    Charging(ActionRisk.HighPower),
    CameraStream(ActionRisk.PrivacySensitive),
    CameraPtz(ActionRisk.PrivacySensitive),
    CameraTalk(ActionRisk.PrivacySensitive),
    CameraRecord(ActionRisk.PrivacySensitive),
    MotionDetection(ActionRisk.Informational),
    ContactDetection(ActionRisk.Informational),
    SmokeDetection(ActionRisk.LifeSafety),
    CarbonMonoxideDetection(ActionRisk.LifeSafety),
    WaterLeakDetection(ActionRisk.Reversible),
    AirQuality(ActionRisk.Informational),
    Irrigation(ActionRisk.Reversible),
    Custom(ActionRisk.Reversible)
}

/**
 * §4.4 canonical universal action — full twin of the controller's
 * sealed hierarchy. Exhaustive-when discipline: the TV Node's
 * ActionExecutor must handle every action or declare it
 * unsupported; it cannot silently ignore new actions.
 */
sealed class UniversalAction {

    abstract val targetDeviceId: DeviceId
    abstract val timestampNs: Long
    abstract val correlationId: String

    data class PowerOn(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class PowerOff(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class PowerToggle(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class VolumeUp(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class VolumeDown(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Mute(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class SetVolume(
        override val targetDeviceId: DeviceId,
        val level: Float, // 0.0..1.0
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(level in 0f..1f) { "Volume level must be in [0, 1] (got $level)." }
        }
    }

    data class ChannelUp(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class ChannelDown(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class InputSelect(
        override val targetDeviceId: DeviceId,
        val inputId: String,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(inputId.isNotBlank()) { "inputId must be non-blank." }
        }
    }

    data class MediaPlay(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaPause(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaStop(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaNext(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class MediaPrevious(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Navigate(
        override val targetDeviceId: DeviceId,
        val direction: Direction,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Ok(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Back(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Home(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Menu(
        override val targetDeviceId: DeviceId,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class SetTemperature(
        override val targetDeviceId: DeviceId,
        val targetCelsius: Float,
        val mode: ClimateMode = ClimateMode.Auto,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(targetCelsius in -50f..150f) { "Temperature must be in [-50, 150] °C (got $targetCelsius)." }
        }
    }

    data class SetFanSpeed(
        override val targetDeviceId: DeviceId,
        val level: Float, // 0.0..1.0
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(level in 0f..1f) { "Fan speed level must be in [0, 1] (got $level)." }
        }
    }

    data class SetMode(
        override val targetDeviceId: DeviceId,
        val mode: ClimateMode,
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction()

    data class Custom(
        override val targetDeviceId: DeviceId,
        val key: String,
        val payload: Map<String, String> = emptyMap(),
        override val timestampNs: Long = System.nanoTime(),
        override val correlationId: String = java.util.UUID.randomUUID().toString()
    ) : UniversalAction() {
        init {
            require(key.isNotBlank()) { "Custom action key must be non-blank." }
        }
    }

    fun requiredCapability(): Capability = when (this) {
        is PowerOn, is PowerOff, is PowerToggle -> Capability.OnOff
        is VolumeUp, is VolumeDown, is Mute, is SetVolume -> Capability.Volume
        is ChannelUp, is ChannelDown -> Capability.Channel
        is InputSelect -> Capability.InputSource
        is MediaPlay, is MediaPause, is MediaStop,
        is MediaNext, is MediaPrevious -> Capability.MediaTransport
        is Navigate, is Ok, is Back, is Home, is Menu -> Capability.MediaTransport
        is SetTemperature -> Capability.TargetTemperature
        is SetFanSpeed -> Capability.FanSpeed
        is SetMode -> Capability.Mode
        is Custom -> Capability.Custom
    }
}

/** Climate mode (twin of controller ClimateMode). */
enum class ClimateMode {
    Auto, Cool, Heat, Dry, FanOnly
}

/**
 * ActionResult — the TV Node's execution verdict for a
 * UniversalAction. Honest taxonomy: Success only after the
 * effect is OBSERVED when the action is observable; otherwise
 * ExecutedUnverified (transmission fired, no proof) or
 * Unsupported (no honest route at all).
 */
sealed class ActionResult {
    /** The system executed the action AND the effect was observed. */
    data class Success(val detail: String) : ActionResult()
    /** The action fired but no observable proof exists on this TV. */
    data class ExecutedUnverified(val detail: String) : ActionResult()
    /** No honest route exists for this action on this TV. */
    data class Unsupported(val detail: String) : ActionResult()
    /** Execution refused by policy (risk gate, user consent). */
    data class Refused(val detail: String) : ActionResult()
    /** The action failed after the attempt. */
    data class Failed(val detail: String) : ActionResult()
}

/**
 * Phase 13 — ONE shared evidence authority for causality proofs.
 *
 * Produced by the software-only IR oracle (Phase 25) on the controller and
 * persisted via the controller's appendix ledger (Phase 26); consumed by the
 * catalogue promotion engine. Owning both sides of the wire with a single
 * type is what makes a cross-version evidence claim meaningful: the fields
 * here are the immutable, hash-locked record of a real before→change→reversal
 * experiment on a physical TV.
 */
data class EvidenceEvent(
    val eventId: String,
    val tvDeviceId: String,
    val actionKey: String,
    val signalId: String,
    val inverseSignalId: String,
    val physicalSha256: String,
    val carrierHz: Int,
    val catalogBuildId: String,
    val source: String,
    val trialsTotal: Int,
    val trialsOk: Int,
    val beforeRawVolume: Int,
    val afterRawVolume: Int,
    val restoredRawVolume: Int,
    val timestampMillis: Long
)