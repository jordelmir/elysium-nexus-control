package com.elysium.nexus.fabric.infrared.promotion

/**
 * Master Order v0.10 Phase 4 — Per-Action CORE Matrix.
 *
 * A device NEVER becomes CORE_VERIFIED because a single action works. CORE is
 * defined by the complete applicable action set for the device type under policy
 * [retail-core-policy-v1].
 */
object CoreActionPolicy {

    const val POLICY_VERSION = "retail-core-policy-v1"

    /** Typical TV CORE actions: complete applicable set for a TV without extra capability info. */
    val TV_CORE_ACTIONS: Set<String> = setOf(
        "POWER_TOGGLE",
        "VOLUME_UP",
        "VOLUME_DOWN",
        "MUTE",
        "INPUT_SELECT",
        "UP",
        "DOWN",
        "LEFT",
        "RIGHT",
        "OK",
        "HOME",
        "BACK"
    )

    /** CORE action requirements per device type. */
    fun coreActionsFor(deviceType: String): Set<String> = when (deviceType.uppercase()) {
        "TV" -> TV_CORE_ACTIONS
        else -> TV_CORE_ACTIONS
    }
}

/**
 * Result of a single CORE action within a device matrix.
 */
enum class CoreActionResult {
    PASS,
    FAIL,
    UNSUPPORTED,
    NOT_APPLICABLE,
    REGRESSION,
    PENDING;

    val isSatisfied: Boolean
        get() = this == PASS || this == UNSUPPORTED || this == NOT_APPLICABLE
}