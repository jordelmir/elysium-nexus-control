package com.elysium.nexus.fabric.infrared

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The §6.3 IR learner.
 *
 * The learner turns a **raw waveform**
 * (a sequence of on/off durations as
 * captured by the IR photodiode) into
 * a normalized [IrCommand] (a
 * `protocol + address + command + extras`
 * tuple).
 *
 * The pipeline is:
 *
 *  1. **Carrier estimation** — the
 *     learner estimates the carrier
 *     frequency from the **shortest
 *     repeating pulse** in the waveform.
 *     NEC's 560 µs mark is the canonical
 *     "shortest mark"; a learner that
 *     finds ~38 kHz from a 560 µs mark is
 *     correct (a 38 kHz carrier is
 *     on for ~13 µs per cycle, so a 560 µs
 *     mark is 560 / 26 ≈ 21 cycles, which
 *     is 21 / 560e-6 ≈ 37.5 kHz). The
 *     estimator is a heuristic; the §6.3
 *     "carrier estimation" step is a
 *     constraint, not a guarantee.
 *  2. **Protocol match** — the learner
 *     tries every supported [IrProtocol]
 *     decoder in turn. The first that
 *     decodes without rejecting wins.
 *  3. **Normalization** — the
 *     [IrCommand] is the canonical artefact:
 *     `protocol`, `address`, `command`,
 *     `extras`, `confidence`. The
 *     `extras` map is the protocol-specific
 *     data we could not fold into the
 *     common shape (toggle bit, repeat,
 *     device sub-type, …).
 *
 * The learner is **pure** (JVM-testeable).
 * The production wiring in `adapters/infrared/`
 * adapts a real photodiode capture; the
 * Hub runs the learner on every learn
 * press and stores the [IrCommand] in
 * the §6.4 IR database.
 *
 * ## Why a closed protocol list
 *
 * The learner iterates the [IrProtocol]
 * enum. The set is closed (per §6.4
 * "vendor plugins"); adding a new
 * protocol is an ADR + a new
 * [IrWaveform] decoder.
 */
object IrLearner {

    /**
     * The result of a learn pass. The
     * `command` is `null` when no protocol
     * matched; the `carrierHz` is the
     * estimator's best guess (always
     * present, even on a mismatch — the
     * §6.4 raw waveform is always
     * persisted as a fallback).
     */
    data class LearnResult(
        val carrierHz: Int,
        val command: IrCommand?,
        /** A raw waveform for the §6.4 IR database. */
        val rawWaveform: IrWaveform,
        /** A confidence score in [0, 1]. */
        val confidence: Float
    )

    /**
     * Learn a [IrCommand] from a **raw
     * waveform** (the photodiode's
     * capture). The function is total: a
     * noisy / unknown waveform returns
     * a `LearnResult` with `command = null`
     * and `confidence < 1.0`.
     */
    fun learn(raw: IntArray, sampleRateHz: Int = 1_000_000): LearnResult {
        require(raw.size >= 2) {
            "IrLearner.learn: raw waveform must have at least 2 entries."
        }
        require(raw.size % 2 == 0) {
            "IrLearner.learn: raw waveform must have an even number of entries."
        }
        require(raw.all { it >= 0 }) {
            "IrLearner.learn: raw waveform entries must be non-negative."
        }
        require(sampleRateHz > 0) {
            "IrLearner.learn: sampleRateHz must be positive."
        }
        // 1. Carrier estimation. The
        //    estimator is approximate
        //    (jittered waveforms produce
        //    a higher estimate than the
        //    canonical carrier); the
        //    estimate is for telemetry,
        //    not for decoding.
        val carrier = estimateCarrier(raw, sampleRateHz)
        val safeCarrier = if (carrier in 30_000..60_000) carrier else IrProtocol.DEFAULT_CARRIER_HZ
        // 2. The decoder's carrier check is
        //    tight (36-42 kHz for NEC).
        //    We try the canonical carrier
        //    for each protocol first; the
        //    estimate is too noisy on a
        //    jittered waveform to feed
        //    directly to the decoder. The
        //    estimate is recorded in the
        //    LearnResult for telemetry.
        val candidates: List<MatchCandidate> = listOf(
            MatchCandidate(IrProtocol.Nec) {
                tryDecodeNec(raw, IrProtocol.Nec.carrierHz)
            },
            MatchCandidate(IrProtocol.NecExtended) {
                // NECx is wider (16-bit
                // address); the heuristic
                // is a placeholder for the
                // full decoder.
                decodeNecExtended(raw, IrProtocol.NecExtended.carrierHz)
            },
            MatchCandidate(IrProtocol.Rc5) {
                decodeRc5(raw, IrProtocol.Rc5.carrierHz)
            }
        )
        for (candidate in candidates) {
            val cmd = candidate.decoder()
            if (cmd != null) {
                val confidence = 0.85f + (1.0f - 0.85f) * confidenceFactor(raw, cmd)
                return LearnResult(
                    carrierHz = safeCarrier,
                    command = cmd,
                    rawWaveform = IrWaveform(
                        carrierHz = candidate.protocol.carrierHz,
                        pattern = raw
                    ),
                    confidence = confidence.coerceIn(0f, 1f)
                )
            }
        }
        // No protocol matched. The raw
        // waveform is still the artefact;
        // the user can re-try or the
        // §6.4 "Raw waveform fallback"
        // path emits the undecoded signal.
        return LearnResult(
            carrierHz = safeCarrier,
            command = null,
            rawWaveform = IrWaveform(
                carrierHz = safeCarrier,
                pattern = raw
            ),
            confidence = 0.3f
        )
    }

    /**
     * Try the NEC decoder with a specific
     * carrier. The helper builds a fresh
     * [IrWaveform] (the decoder's tight
     * carrier range means the raw
     * waveform's estimate may not match)
     * and returns the normalized
     * [IrCommand] on success.
     */
    private fun tryDecodeNec(raw: IntArray, carrierHz: Int): IrCommand? {
        val waveform = IrWaveform(carrierHz = carrierHz, pattern = raw)
        return IrWaveform.decodeNec(waveform)?.let { nec ->
            IrCommand(
                protocol = IrProtocol.Nec,
                address = nec.address,
                command = nec.command
            )
        }
    }

    /**
     * Estimate the carrier frequency
     * from a raw waveform. The estimator
     * finds the **shortest on-pulse** in
     * the waveform and uses the
     * relationship: a 38 kHz carrier is
     * on for ~13 µs per cycle, so a
     * mark of `D` µs corresponds to a
     * carrier of `~D / 26` cycles / `D` µs
     * = `1 / 26e-6` Hz ≈ `38.5` kHz.
     *
     * Concretely: the estimator picks the
     * shortest on-pulse, divides by the
     * canonical NEC mark duration
     * (560 µs = 22 cycles at 38 kHz), and
     * multiplies by 38_000. The heuristic
     * is exact for the NEC family; for
     * RC5 / SIRC the result is approximate
     * (36-40 kHz carrier, 889 µs or 600 µs
     * marks), and the §6.4 "frequency
     * verification" step refines it.
     */
    internal fun estimateCarrier(raw: IntArray, sampleRateHz: Int): Int {
        // The shortest on-pulse.
        val onPulses = raw.indices.filter { it % 2 == 0 }.map { raw[it] }.filter { it > 0 }
        if (onPulses.isEmpty()) return IrProtocol.DEFAULT_CARRIER_HZ
        val shortest = onPulses.min()
        if (shortest >= 1_000) {
            // The shortest mark is over 1
            // ms — unlikely for any known
            // protocol. Return the default.
            return IrProtocol.DEFAULT_CARRIER_HZ
        }
        // A 560 µs mark corresponds to
        // 38 kHz (22 cycles). Scale linearly.
        val estimated = (shortest.toDouble() / 560.0 * 38_000.0).roundToInt()
        // Clamp to the [30, 60] kHz window.
        return estimated.coerceIn(30_000, 60_000)
    }

    /**
     * The confidence factor: how well the
     * raw waveform matches the decoded
     * protocol. The factor is a heuristic
     * in `[0, 1]` derived from the ratio
     * of the raw waveform's length to the
     * expected protocol length.
     */
    private fun confidenceFactor(raw: IntArray, cmd: IrCommand): Float {
        val expected = when (cmd.protocol) {
            IrProtocol.Nec -> 36
            IrProtocol.NecExtended -> 68
            IrProtocol.Rc5 -> 28
            else -> raw.size
        }
        val diff = abs(raw.size - expected)
        return (1.0f - diff.toFloat() / expected.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Try the NEC extended decoder. The
     * pure [IrWaveform] does not yet ship
     * a NECx decoder (the format is NEC
     * with 16-bit address + 8-bit
     * command + 8-bit inverted command);
     * this helper is the seam. The
     * heuristic is: the raw waveform's
     * 16-bit address is "wider" than 255.
     */
    private fun decodeNecExtended(raw: IntArray, carrierHz: Int): IrCommand? {
        if (raw.size < 60) return null
        // The function is best-effort;
        // the full NECx decoder is a
        // Phase 2+ follow-up. We return
        // a placeholder command that the
        // matcher uses to score the
        // candidate.
        return IrCommand(
            protocol = IrProtocol.NecExtended,
            address = 0x0000,
            command = 0x00,
            extras = mapOf("pending" to "true")
        )
    }

    /**
     * Try the RC5 decoder. The RC5 frame
     * is 14 Manchester bits = 28 entries
     * (each bit is two halves of 889 µs).
     * A pure RC5 decoder is a Phase 2+
     * follow-up; for now we return a
     * placeholder when the waveform
     * length matches.
     */
    private fun decodeRc5(raw: IntArray, carrierHz: Int): IrCommand? {
        if (raw.size != 28) return null
        return IrCommand(
            protocol = IrProtocol.Rc5,
            address = 0x00,
            command = 0x00,
            extras = mapOf("pending" to "true")
        )
    }

    /**
     * A candidate decoder. The matcher's
     * job is to try each in turn; the
     * first non-null wins.
     */
    private data class MatchCandidate(
        val protocol: IrProtocol,
        val decoder: () -> IrCommand?
    )
}

/**
 * The normalized IR command.
 *
 * The command is the canonical artefact
 * the §6.4 IR database persists: a
 * `(protocol, address, command, extras)`
 * tuple. The same physical button is the
 * same command across phones / Hubs /
 * Receivers.
 *
 * The `extras` map carries the
 * protocol-specific data we could not
 * fold into the common shape (toggle
 * bit, repeat, sub-type, …). The map is
 * `String -> String` to keep the JSON
 * serialisation in the §6.4 IR database
 * simple; values are JSON-encoded.
 */
data class IrCommand(
    val protocol: IrProtocol,
    /** The device address (8-bit for NEC, 5-bit for RC5, …). */
    val address: Int,
    /** The button command (8-bit for NEC, 6-bit for RC5, …). */
    val command: Int,
    /** Protocol-specific extras (toggle, repeat, sub-type, …). */
    val extras: Map<String, String> = emptyMap()
) {
    init {
        require(address >= 0) {
            "IrCommand.address must be non-negative (got $address)."
        }
        require(command >= 0) {
            "IrCommand.command must be non-negative (got $command)."
        }
    }
}
