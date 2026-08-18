package com.elysium.nexus.tvnode.canonical

/**
 * Phase 13 / 25 — the ONE observation contract owned by the canonical
 * authority (compiled into both the controller and the TV Node via
 * `:tvlink`). The twin builds can never disagree about what a volume
 * snapshot is or what an observation engine must answer.
 *
 * Pure JVM on purpose: the software-only IR oracle consumes these types
 * on the PHONE side while the TV Node produces them on the AUDIO side.
 *
 * A "confirmed" code_set correlation is NEVER claimed from a snapshot
 * alone — snapshots feed the oracle's challenge protocol
 * (snapshot → candidate → re-observe → reversal).
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
}

/**
 * Observation-only contract. Implementations must never mutate state;
 * the executor and the oracle read THROUGH this engine and demand
 * before/after evidence before ever returning a confirmed verdict.
 */
interface TvObservationEngine {
    fun observeVolume(): VolumeObservation?
    fun isMediaSessionActive(): Boolean
}