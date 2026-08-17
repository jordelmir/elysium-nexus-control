package com.elysium.nexus.tvnode.access

import com.elysium.nexus.tvnode.identity.DeviceFacts

/**
 * Capability grants — TV-FABRIC.2 V2 (Master Order v0.10 Phase 23).
 *
 * There is NO access-level ladder. Every capability carries its own
 * independent boolean grant, each derivable ONLY from platform facts +
 * explicit user consent. Nothing is "above" or "below" anything else:
 * a TV can honestly have media control granted while global-key actions
 * are absent, and vice versa.
 *
 * Ordinal comparisons on access levels are FORBIDDEN as policy: the
 * Final Truth Gate (Z6) fails the build if one reappears.
 */
data class TvCapabilityGrants(
    /** User granted AccessibilityService (API 24+ consent flow). */
    val accessibilityGranted: Boolean = false,
    /** User granted NotificationListenerService (API 24+ consent flow). */
    val notificationListenerGranted: Boolean = false,
    /** InputMethodService installed and enabled by the user. */
    val imeInstalledAndEnabled: Boolean = false,
    /** Global HOME action available (accessibility + API 33+). */
    val globalHomeAvailable: Boolean = false,
    /** Global BACK action available (accessibility + API 33+). */
    val globalBackAvailable: Boolean = false,
    /** Global DPAD actions available (accessibility + API 33+). */
    val globalDpadAvailable: Boolean = false,
    /** Media transport control granted (media session + notification listener). */
    val mediaTransportGranted: Boolean = false,
    /** Volume is observable on this device (AudioManager, API 1+). */
    val volumeObservable: Boolean = true,
    /** Volume execution granted — requires the user's TV-control consent. */
    val volumeExecutable: Boolean = false,
    /** Key events observable via accessibility key filtering (API 33+). */
    val keyFilteringObservable: Boolean = false,
    /** Fixed-volume device: mute is not honestly possible. */
    val volumeFixed: Boolean = false
) {

    /** Human summary for logs/UI. NOT a claim of any single level. */
    val summary: String
        get() = buildString {
            if (volumeObservable && volumeExecutable && !volumeFixed) append("volume ")
            if (mediaTransportGranted) append("mediaSession ")
            if (globalHomeAvailable && globalBackAvailable && globalDpadAvailable) append("globalKeys ")
            if (imeInstalledAndEnabled) append("ime ")
            if (keyFilteringObservable) append("keyFilter ")
            if (isBlank()) append("none")
        }

    val hasAnyGrant: Boolean
        get() = accessibilityGranted || notificationListenerGranted || imeInstalledAndEnabled ||
            globalHomeAvailable || globalBackAvailable || globalDpadAvailable ||
            mediaTransportGranted || volumeExecutable || keyFilteringObservable
}

/**
 * CapabilityManifest — dynamic honest capability surface of this TV,
 * derived from the API level and the platform features PRESENT at runtime.
 *
 * TV-FABRIC.3: a capability is only advertised when there is a real route:
 * - Volume is observable via AudioManager on every API we ship.
 * - Mute is observable via AudioManager on every API we ship (unless fixed).
 * - Global TV keys (HOME/BACK/DPAD) are `GLOBAL_ACTION_*` — only API 33+
 *   when the accessibility service is granted; below that they drop out.
 * - Media transport is observable only when a media session exists AND the
 *   NotificationListenerService is granted.
 * - Keys are observed (not consumed) via the accessibility service when
 *   canRequestFilterKeyEvents is true (API 33+).
 */
data class CapabilityManifest(
    val apiLevel: Int,
    val grants: TvCapabilityGrants,
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
            append(" grants=").append(grants.summary)
            if (volumeObservable) append(" volume")
            if (muteObservable) append(" mute")
            if (globalTvActions) append(" globalKeys")
            if (imeAvailable) append(" ime")
            if (notificationListenerGranted && mediaSessionPresent) append(" mediaSession")
        }
}

/**
 * Builds the manifest truthfully for a given facts snapshot + grants.
 * All booleans are derived from the platform facts and independent grants,
 * NEVER from an ordinal comparison.
 */
object CapabilityManifestBuilder {

    fun build(
        facts: DeviceFacts,
        grants: TvCapabilityGrants,
        isMediaSessionActive: Boolean = false,
        isNotificationListenerGranted: Boolean = false
    ): CapabilityManifest {
        val api = facts.apiLevel
        return CapabilityManifest(
            apiLevel = api,
            grants = grants,
            volumeObservable = grants.volumeObservable, // AudioManager volume is available API 1+; the node installs on 24+
            muteObservable = grants.volumeObservable && !facts.volumeFixed, // a fixed-volume TV cannot be muted honestly
            mediaSessionPresent = isMediaSessionActive,
            notificationListenerGranted = isNotificationListenerGranted,
            globalTvActions = grants.accessibilityGranted && api >= 33,
            keyFilteringObservable = grants.accessibilityGranted && facts.canRequestFilterKeyEvents,
            imeAvailable = grants.imeInstalledAndEnabled // IME is a normal InputMethodService
        )
    }
}