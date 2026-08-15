package com.elysium.nexus.fabric.tv

import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.canonical.DeviceId

/**
 * §11 Universal Protocol — TV Node protocol envelope.
 *
 * The phone sends semantic [UniversalAction]s (never Android keycodes) to
 * the TV Node over a local TCP connection. Each command is wrapped in a
 * [TvNodeCommand] envelope with auth metadata, anti-replay, and sequencing.
 */
data class TvNodeCommand(
    /** Protocol version for forward compatibility. */
    val protocolVersion: Int = 1,
    /** Unique message ID for idempotency and tracing. */
    val messageId: String,
    /** Connection/session identifier. */
    val connectionId: String,
    /** Target device identity. */
    val deviceId: DeviceId,
    /** The semantic action to execute. */
    val action: UniversalAction,
    /** Wall-clock nanos when the command was created. */
    val timestampNs: Long = System.nanoTime(),
    /** Max wall-clock nanos before the command should be discarded. */
    val deadlineNs: Long? = null,
    /** Monotonic sequence number for anti-replay. */
    val sequenceNumber: Long,
    /** Which capability context this command targets. */
    val capabilityContext: String? = null,
    /** Authentication token/hmac for the session. */
    val authToken: String? = null
)

/**
 * §11 TV response states — never collapse all into boolean success.
 */
sealed class TvNodeResponse {
    /** Command frame was received and is structurally valid. */
    data class Received(
        val messageId: String,
        val sequenceNumber: Long
    ) : TvNodeResponse()

    /** Command was accepted for execution. */
    data class Accepted(
        val messageId: String,
        val sequenceNumber: Long
    ) : TvNodeResponse()

    /** Command was executed (sent to the TV, but not yet observed). */
    data class Executed(
        val messageId: String,
        val sequenceNumber: Long
    ) : TvNodeResponse()

    /** TV Node observed the expected change on the TV. */
    data class Observed(
        val messageId: String,
        val sequenceNumber: Long,
        val observation: String? = null
    ) : TvNodeResponse()

    /** Command failed to execute. */
    data class Failed(
        val messageId: String,
        val sequenceNumber: Long,
        val reason: String
    ) : TvNodeResponse()

    /** The TV Node does not support this action. */
    data class Unsupported(
        val messageId: String,
        val sequenceNumber: Long
    ) : TvNodeResponse()

    /** The action requires a permission the phone has not yet been granted. */
    data class PermissionRequired(
        val messageId: String,
        val sequenceNumber: Long,
        val permission: String
    ) : TvNodeResponse()
}

/**
 * Observation event sent FROM the TV Node TO the phone.
 *
 * The TV Node monitors its own state (via AccessibilityService, IME,
 * MediaSession, etc.) and reports what it observed.
 */
sealed class TvNodeObservation {
    /** A key-like action was observed (e.g. volume button pressed on OEM remote). */
    data class KeyObserved(
        val key: String,
        val timestampNs: Long,
        val appPackage: String? = null
    ) : TvNodeObservation()

    /** A numerical state changed. */
    data class StateChanged(
        val property: String,
        val oldValue: String?,
        val newValue: String?,
        val timestampNs: Long
    ) : TvNodeObservation()

    /** The foreground app changed. */
    data class ForegroundAppChanged(
        val packageName: String,
        val timestampNs: Long
    ) : TvNodeObservation()

    /** Playback state changed. */
    data class PlaybackChanged(
        val isPlaying: Boolean,
        val timestampNs: Long
    ) : TvNodeObservation()

    /** TV Node connection state changed. */
    data class TvNodeReconnect(
        val timestampNs: Long
    ) : TvNodeObservation()

    /** An app was launched. */
    data class AppLaunched(
        val packageName: String,
        val timestampNs: Long
    ) : TvNodeObservation()
}