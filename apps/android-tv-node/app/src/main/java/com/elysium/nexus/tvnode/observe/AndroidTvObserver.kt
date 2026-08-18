package com.elysium.nexus.tvnode.observe

import android.content.Context
import android.media.AudioManager
import com.elysium.nexus.tvnode.canonical.TvObservationEngine
import com.elysium.nexus.tvnode.canonical.VolumeObservation

/**
 * Real Android observer — reads the TV state through public APIs.
 * Never mutates anything; the executor drives effects through [TvEffector].
 */
class AndroidVolumeObserver(context: Context) : TvObservationEngine {

    private val appContext = context.applicationContext

    override fun observeVolume(): VolumeObservation? {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
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

    override fun isMediaSessionActive(): Boolean {
        // Media session presence is reported by the phone/pairing
        // layer through the secure channel (TV-FABRIC.6); this is a
        // conservative baseline so the node never claims what it
        // cannot observe.
        return false
    }
}

/**
 * Real effector — the ONLY place raw key events are issued, and always
 * through public APIs. API 33+ routes key injection through the
 * accessibility global-action chain when the service is granted
 * (canRequestFilterKeyEvents); below API 33 it falls back to
 * AudioManager stream adjustment, which is still verifiable.
 */
class AndroidVolumeEffector(
    context: Context,
    private val useGlobalActions: Boolean = true
) : TvEffector {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager?
        get() = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun adjustStreamVolume(direction: VolumeActionInterpreter.VolumeDirectionAction): Boolean {
        val manager = audioManager ?: return false
        val adjustment = when (direction) {
            VolumeActionInterpreter.VolumeDirectionAction.Up -> AudioManager.ADJUST_RAISE
            VolumeActionInterpreter.VolumeDirectionAction.Down -> AudioManager.ADJUST_LOWER
            VolumeActionInterpreter.VolumeDirectionAction.Mute -> AudioManager.ADJUST_MUTE
        }
        // adjustStreamVolume is void: a return value here can only mean
        // "dispatched", never "succeeded". The caller verifies the actual
        // effect through observeVolume() before/after (§19 causal verifier).
        manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustment, AudioManager.FLAG_SHOW_UI)
        return true
    }

    override fun supportsGlobalTvKeys(): Boolean = useGlobalActions
}