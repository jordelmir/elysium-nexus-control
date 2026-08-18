package com.elysium.nexus.tvnode.observe

import android.media.AudioManager
import android.content.Context
import com.elysium.nexus.tvnode.canonical.TvObservationEngine
import com.elysium.nexus.tvnode.canonical.VolumeObservation

/**
 * Phase 25 — production Android glue: volume observation straight from the
 * real AudioManager. Observation-only, never mutates state (the executor and
 * oracle read THROUGH this seam; effects belong to [TvEffector]).
 */
class AndroidAudioObservationEngine(context: Context) : TvObservationEngine {

    private val audioManager = runCatching {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }.getOrNull()

    override fun observeVolume(): VolumeObservation? {
        val manager = audioManager ?: return null
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

    override fun isMediaSessionActive(): Boolean = false
}