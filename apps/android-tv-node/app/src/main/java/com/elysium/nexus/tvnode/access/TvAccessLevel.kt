package com.elysium.nexus.tvnode.access

import com.elysium.nexus.tvnode.identity.DeviceFacts

/**
 * Access levels — TV-FABRIC.2 taxonomy. A service that is not granted
 * cannot execute; the capability surface shrinks with the access level.
 */
enum class TvAccessLevel(val label: String) {
    /** Installed but nothing granted. Observe nothing, execute nothing. */
    STANDARD("level-0-standard"),

    /** User granted the two system permissions that require intent + consent:
     *  AccessibilityService (API 24+) and NotificationListenerService (API 24+).
     *  Enables TV observation + media control, and IME on API 24+. */
    ENHANCED_USER_GRANTED("level-1-enhanced-user-granted"),

    /** Phone-side contract (Bluetooth HID). The TV node itself never claims
     *  this level; it reports pairing partners and route health. */
    BLUETOOTH_HID("level-2-bluetooth-hid"),

    /** Lab-only engineering channel (ADB). Never presented to consumers. */
    ENGINEERING_ADB("level-3-engineering-adb")
}

/**
 * CapabilityManifest — dynamic honest capability surface of this TV,
 * derived from the API level and the platform features PRESENT at runtime.
 *
 * TV-FABRIC.3: a capability is only advertised when there is a real route:
 * - Volume is observable via AudioManager on every API we ship.
 * - Mute is observable via AudioManager on every API we ship.
 * - Global TV keys (HOME/BACK/DPAD) are `GLOBAL_ACTION_*` — only API 33+
 *   when the accessibility service is granted; below that they drop out.
 * - Media transport is observable only when a media session exists AND the
 *   NotificationListenerService is granted.
 * - Keys are observed (not consumed) via the accessibility service when
 *   canRequestFilterKeyEvents is true (API 33+).
 */
data class CapabilityManifest(
    val apiLevel: Int,
    val accessLevel: TvAccessLevel,
    val volumeObservable: Boolean,
    val muteObservable: Boolean,
    val mediaSessionPresent: Boolean,
    val notificationListenerGranted: Boolean,
    val globalTvActions: Boolean,
    val keyFilteringObservable: Boolean,
    val imeAvailable: Boolean
) {

    val summary: String
        get() = buildString {
            append("api=").append(apiLevel)
            append(" access=").append(accessLevel.label)
            if (volumeObservable) append(" volume")
            if (muteObservable) append(" mute")
            if (globalTvActions) append(" globalKeys")
            if (imeAvailable) append(" ime")
            if (notificationListenerGranted && mediaSessionPresent) append(" mediaSession")
        }
}

/**
 * Builds the manifest truthfully for a given facts snapshot + grants.
 * All booleans are derived from the platform, never assumed.
 */
object CapabilityManifestBuilder {

    fun build(
        facts: DeviceFacts,
        access: TvAccessLevel,
        isMediaSessionActive: Boolean = false,
        isNotificationListenerGranted: Boolean = false
    ): CapabilityManifest {
        val api = facts.apiLevel
        val enhanced = access >= TvAccessLevel.ENHANCED_USER_GRANTED
        return CapabilityManifest(
            apiLevel = api,
            accessLevel = access,
            volumeObservable = true, // AudioManager volume is available API 1+; the node installs on 24+
            muteObservable = !facts.volumeFixed, // a fixed-volume TV cannot be muted honestly
            mediaSessionPresent = isMediaSessionActive,
            notificationListenerGranted = isNotificationListenerGranted,
            globalTvActions = enhanced && api >= 33,
            keyFilteringObservable = enhanced && facts.canRequestFilterKeyEvents,
            imeAvailable = enhanced // IME is a normal InputMethodService
        )
    }
}