package com.elysium.nexus.tvnode.observe

import android.media.AudioManager
import com.elysium.nexus.tvnode.access.TvCapabilityGrants

/**
 * TV observation engine — the honest source of "what the TV is really
 * doing" for the IR oracle (TV-FABRIC.3) and for action evidence.
 *
 * Every observation is timestamped and classified. The oracle's
 * challenge protocol (snapshot → candidate IR → re-observe → reversal)
 * only ever consumes observations produced here; a "confirmed"
 * code_set correlation is NEVER claimed from a snapshot alone.
 */
data class VolumeObservation(
    val rawVolume: Int,
    val maxVolume: Int,
    val level: Float,
    val isMuted: Boolean,
    val isVolumeFixed: Boolean,
    val timestampNs: Long = System.nanoTime()
) {
    val canMute: Boolean get() = !isVolumeFixed

    companion object {
        fun from(manager: AudioManager): VolumeObservation? {
            val max = runCatching { manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
            val raw = runCatching { manager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
            return VolumeObservation(
                rawVolume = raw,
                maxVolume = max,
                level = if (max > 0) raw.toFloat() / max.toFloat() else 0f,
                isMuted = runCatching { manager.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false),
                isVolumeFixed = manager.isVolumeFixed
            )
        }
    }
}

/** Key event observed on the TV (via AccessibilityService key filtering). */
data class KeyObservation(
    val keyCode: Int,
    val action: Int, // KeyEvent.ACTION_DOWN / ACTION_UP
    val source: String, // "remote" | "app" | "accessibility"
    val timestampNs: Long = System.nanoTime()
)

/** Foreground app change observed via accessibility events / leanback launcher. */
data class ForegroundAppObservation(
    val packageName: String?,
    val isLauncher: Boolean,
    val timestampNs: Long = System.nanoTime()
)

/**
 * Observation-only contract. Implementations must never mutate state;
 * the executor reads THROUGH this engine and demands before/after
 * evidence before ever returning a confirmed verdict.
 */
interface TvObservationEngine {
    fun observeVolume(): VolumeObservation?
    fun isMediaSessionActive(): Boolean
}

/**
 * Effector seam — the single place real key/touch effects are issued
 * against the public Android APIs (AudioManager, accessibility global
 * actions, IME commit). Kept separate so the observation engine stays
 * pure and fully unit-testable on the JVM without Robolectric.
 */
interface TvEffector {
    /** Adjust the music stream; returns true if the API call was issued. */
    fun adjustStreamVolume(direction: VolumeActionInterpreter.VolumeDirectionAction): Boolean
    /** True when global TV-key actions (HOME/BACK/DPAD) are available and granted. */
    fun supportsGlobalTvKeys(): Boolean
}

/**
 * Pure interpreter — maps the ACTION the user/phone wants onto the
 * honest evidence ladder:
 *
 *   VolumeUp/VolumeDown  → Success ONLY when a volume delta is observable
 *                          on the real TV (before/after comparison).
 *   Mute                 → Success ONLY when mute state changes; on a
 *                          fixed-volume TV this is Unsupported.
 *   everything else      → Unsupported until a dedicated observer exists
 *                          (ACTION_POWER needs explicit user confirmation).
 *
 * The ladder deliberately has NO "guessed it worked" rung: ExecutedUnverified
 * is only produced by the executor when transmission happened but no
 * observation exists yet — and it is never promoted to Success.
 */
object VolumeActionInterpreter {

    sealed class Verdict {
        /** Effect provably observed on the TV. */
        object Confirmed : Verdict()
        /** Transmission/command fired, but no observation proof yet. */
        object Unverified : Verdict()
        /** No honest route for this action on this TV (e.g. mute on fixed volume). */
        object Unsupported : Verdict()
        /** Policy refused execution (rare; used by the executor's risk gate). */
        object Refused : Verdict()
    }

    fun interpretVolumeChange(
        before: VolumeObservation?,
        after: VolumeObservation?,
        action: VolumeDirectionAction
    ): Verdict = when {
        before == null || after == null -> Verdict.Unverified
        before.isVolumeFixed -> Verdict.Unsupported
        action == VolumeDirectionAction.Mute && !before.canMute -> Verdict.Unsupported
        action == VolumeDirectionAction.Mute -> {
            if (before.isMuted == after.isMuted) Verdict.Unverified else Verdict.Confirmed
        }
        else -> {
            // Volume up/down: a REAL change must be observed in the right direction.
            val directionOk =
                if (action == VolumeDirectionAction.Up) after.rawVolume > before.rawVolume
                else after.rawVolume < before.rawVolume
            if (directionOk) Verdict.Confirmed else Verdict.Unverified
        }
    }

    enum class VolumeDirectionAction { Up, Down, Mute }
}

/**
 * Actual executor over public Android APIs only — no root, no OEM signing.
 * Executes ONLY what the observation engine can verify; refuses everything
 * that would require an unverifiable silent action (TV-FABRIC.4).
 */
class TvActionExecutor(
    private val observations: TvObservationEngine,
    private val effector: TvEffector,
    private val grants: TvCapabilityGrants
) {

    /**
     * Execute a volume direction action. Returns the honest verdict.
     * POWER/INPUT are out of scope here by design (explicit user
     * confirmation gate lives at the phone/pairing layer).
     *
     * The volume-execution gate is an INDEPENDENT grant
     * ([TvCapabilityGrants.volumeExecutable]) — there is no access ladder.
     */
    fun executeVolume(action: VolumeActionInterpreter.VolumeDirectionAction): VolumeActionInterpreter.Verdict {
        if (!grants.volumeExecutable) {
            return VolumeActionInterpreter.Verdict.Refused
        }
        val before = observations.observeVolume() ?: return VolumeActionInterpreter.Verdict.Unverified
        if (before.isVolumeFixed) return VolumeActionInterpreter.Verdict.Unsupported

        if (!effector.adjustStreamVolume(action)) return VolumeActionInterpreter.Verdict.Unverified

        val after = observations.observeVolume()
        return VolumeActionInterpreter.interpretVolumeChange(before, after, action)
    }

    /** Media transport: only honest when a media session is actually active. */
    fun supportsMediaTransport(): Boolean = observations.isMediaSessionActive()

    /** Global TV keys (HOME/BACK/DPAD) honest gate. */
    fun supportsGlobalTvKeys(): Boolean = effector.supportsGlobalTvKeys()
}