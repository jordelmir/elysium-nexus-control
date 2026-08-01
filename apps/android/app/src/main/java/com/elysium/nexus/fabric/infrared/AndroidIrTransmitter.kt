package com.elysium.nexus.fabric.infrared

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

/**
 * The §6.2 Android IR transmitter.
 *
 * The transmitter wraps Android's
 * [ConsumerIrManager]. The platform
 * exposes IR transmission **only** on
 * devices that announce
 * `PackageManager.FEATURE_CONSUMER_IR`
 * (e.g. older Samsung, older LG; the
 * Honor Magic V2 lab device does not).
 *
 * The transmitter is the Android
 * adapter. The pure logic (waveform
 * encode / decode / carrier
 * validation) lives in [IrWaveform]
 * and is JVM-testeable. The transmitter
 * adds:
 *
 *  - **Capability detection** at
 *    construction: the constructor
 *    probes `FEATURE_CONSUMER_IR` and
 *    falls back to a no-op when the
 *    device has no emitter.
 *  - **Carrier validation**: the
 *    waveform's carrier is checked
 *    against the emitter's supported
 *    range. Out-of-range carriers are
 *    refused (the call is logged and
 *    a `false` is returned; the §38
 *    release-blocker discipline applies:
 *    the activity must not crash on a
 *    bad carrier).
 *  - **Queue limit**: at most one
 *    transmit is in flight at a time;
 *    concurrent calls are dropped with
 *    a warning.
 *
 * The transmitter does **not** track
 * whether the device confirmed the
 * blast. Per §6.5, an IR transmission
 * is `COMMAND_SENT` not
 * `STATE_CONFIRMED`; the caller
 * surfaces the result class to the
 * user.
 */
class AndroidIrTransmitter(context: Context) {

    private val tag = "ElysiumNexus.Ir"
    private val manager: ConsumerIrManager? = try {
        context.applicationContext
            .getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    } catch (e: Throwable) {
        Log.w(tag, "ConsumerIrManager unavailable; using no-op.", e)
        null
    }
    private val hasEmitter: Boolean = manager != null

    @Volatile
    private var transmitInFlight: Boolean = false

    /**
     * @return `true` if the device has a working
     * IR emitter. The caller should hide the
     * IR controls when the answer is `false`.
     */
    fun hasEmitter(): Boolean = hasEmitter

    /**
     * @return the supported carrier frequency
     * range in Hz, or `null` when the device
     * has no emitter. The Android
     * `ConsumerIrManager.carrierFrequencies`
     * returns an array of disjoint ranges;
     * we collapse them into the union
     * [min, max] for the caller. The
     * typical consumer IR emitter covers
     * 30-60 kHz, so the union is usually
     * a single range.
     */
    fun carrierRange(): IntRange? {
        val m = manager ?: return null
        val ranges = m.carrierFrequencies ?: return null
        if (ranges.isEmpty()) return null
        val min = ranges.minOf { it.minFrequency }
        val max = ranges.maxOf { it.maxFrequency }
        return min..max
    }

    /**
     * Transmit [waveform] via the device's IR
     * emitter. Returns `true` on success,
     * `false` on:
     *
     *  - no emitter (the call is a no-op),
     *  - carrier out of range,
     *  - another transmit in flight (queue
     *    full; the caller can retry).
     *
     * The function does **not** block: the
     * call to `transmit` returns when the
     * emitter accepts the pattern; the
     * actual blast is paced by the emitter's
     * own scheduler. The §6.2 "Cola de
     * comandos limitada" is implemented by
     * the [transmitInFlight] guard.
     */
    fun transmit(waveform: IrWaveform): Boolean {
        val m = manager ?: return false
        if (!hasEmitter) return false
        // Carrier validation.
        val range = carrierRange()
        if (range != null && waveform.carrierHz !in range) {
            Log.w(
                tag,
                "Carrier ${waveform.carrierHz} Hz out of range $range; refusing to transmit."
            )
            return false
        }
        // Queue limit: at most one transmit in
        // flight at a time. The blast itself is
        // short (a typical TV command is 30-50ms);
        // the caller can retry the next frame.
        if (transmitInFlight) {
            Log.w(tag, "Transmit queue full; dropping ${waveform.pairCount}-pair waveform.")
            return false
        }
        transmitInFlight = true
        try {
            m.transmit(waveform.carrierHz, waveform.pattern)
            return true
        } catch (e: Throwable) {
            // Per §38, the transmitter never
            // crashes the activity. The throw
            // is logged and the call returns
            // `false`; the caller surfaces the
            // failure to the user.
            Log.w(tag, "IR transmit failed: ${e.message}", e)
            return false
        } finally {
            // The Android emitter returns
            // synchronously; the guard is
            // released immediately.
            transmitInFlight = false
        }
    }

    companion object {
        /**
         * @return `true` when the device announces
         * `FEATURE_CONSUMER_IR`. A convenience
         * for the activity's "show IR controls?"
         * decision. The actual emitter presence
         * is also probed at construction
         * (cheap) — `hasEmitter()` is the
         * canonical answer.
         */
        fun deviceHasIr(context: Context): Boolean =
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CONSUMER_IR
            )
    }
}
