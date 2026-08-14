package com.elysium.nexus.fabric.infrared

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android hardware adapter for IR pattern transmission wrapping [ConsumerIrManager].
 *
 * Implements strict capability probing (`hasIrEmitter()`), thread safety via [Mutex],
 * asynchronous execution on [Dispatchers.IO], and typed execution outputs using [IrTransmitResult].
 */
class AndroidIrTransmitter(context: Context) {

    private val tag = "ElysiumNexus.Ir"
    private val manager: ConsumerIrManager? = try {
        context.applicationContext
            .getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    } catch (e: Throwable) {
        Log.w(tag, "ConsumerIrManager service acquisition failed; operating in no-op mode.", e)
        null
    }

    private val hasEmitter: Boolean = try {
        manager?.hasIrEmitter() == true
    } catch (e: Throwable) {
        Log.w(tag, "ConsumerIrManager.hasIrEmitter() probe failed.", e)
        false
    }

    private val transmitMutex = Mutex()

    /**
     * @return `true` if the device has an operational IR emitter hardware component.
     */
    fun hasEmitter(): Boolean = hasEmitter

    /**
     * @return List of hardware-supported carrier frequency ranges in Hz, or `emptyList()`
     * when unavailable.
     */
    fun carrierRanges(): List<IntRange> {
        val m = manager ?: return emptyList()
        val ranges = try {
            m.carrierFrequencies
        } catch (e: SecurityException) {
            Log.w(tag, "getCarrierFrequencies permission denied.", e)
            return emptyList()
        } catch (e: Throwable) {
            Log.w(tag, "getCarrierFrequencies failed.", e)
            return emptyList()
        } ?: return emptyList()

        return ranges.map { it.minFrequency..it.maxFrequency }
    }

    /**
     * Transmit an IR waveform.
     * Executed asynchronously off the main thread on [Dispatchers.IO].
     *
     * @return [IrTransmitResult] detailing the typed result of the transmission attempt.
     */
    suspend fun transmit(waveform: IrWaveform): IrTransmitResult = withContext(Dispatchers.IO) {
        val m = manager ?: return@withContext IrTransmitResult.NoEmitter
        if (!hasEmitter) return@withContext IrTransmitResult.NoEmitter

        // Carrier frequency range validation.
        // V0.6.3 RC-9: instead of hard-failing on an unsupported carrier
        // (e.g. Kaseikyo 37kHz on Honor Magic V2), fall back to the nearest
        // supported carrier within tolerance (±2000 Hz). The IR pattern
        // timings are carrier-independent, so only the modulation frequency
        // shifts. Outside tolerance, fail closed with UnsupportedCarrier.
        val supportedRanges = carrierRanges()
        if (supportedRanges.isNotEmpty()) {
            val requestedHz = waveform.carrierHz
            val isSupported = supportedRanges.any { range -> requestedHz in range }
            if (!isSupported) {
                val nearestHz = supportedRanges
                    .flatMap { range -> listOf(range.first, range.last) }
                    .minBy { range -> kotlin.math.abs(range - requestedHz) }
                if (kotlin.math.abs(nearestHz - requestedHz) <= CARRIER_FALLBACK_TOLERANCE_HZ) {
                    FileLog.d("TX_CARRIER_FALLBACK requested=${requestedHz}Hz used=${nearestHz}Hz")
                    Log.w(tag, "Carrier $requestedHz Hz unsupported; falling back to $nearestHz Hz " +
                        "(supported $supportedRanges).")
                    return@withContext transmitLocked(m, waveform, nearestHz)
                }
                Log.w(tag, "Carrier $requestedHz Hz out of supported ranges $supportedRanges.")
                return@withContext IrTransmitResult.UnsupportedCarrier(
                    requestedHz = requestedHz,
                    supportedRanges = supportedRanges
                )
            }
        }

        return@withContext transmitLocked(m, waveform, waveform.carrierHz)
    }

    private suspend fun transmitLocked(
        m: ConsumerIrManager,
        waveform: IrWaveform,
        carrierHz: Int
    ): IrTransmitResult {
        // Validate pattern slices strictly > 0 and total duration < 2s
        if (waveform.pattern.isEmpty()) {
            return IrTransmitResult.InvalidPattern("Pattern is empty.")
        }
        if (waveform.pattern.any { it <= 0 }) {
            return IrTransmitResult.InvalidPattern("Pattern contains non-positive slice duration <= 0 us.")
        }
        if (waveform.totalDurationUs >= 2_000_000L) {
            return IrTransmitResult.InvalidPattern("Pattern total duration exceeds 2-second limit.")
        }

        if (!transmitMutex.tryLock()) {
            Log.w(tag, "IR transmit mutex locked; dropping concurrent request.")
            return IrTransmitResult.Busy
        }

        try {
            m.transmit(carrierHz, waveform.pattern)
            val hash = waveform.sha256Hash()
            Log.d(tag, "IR pattern successfully transmitted: $carrierHz Hz, ${waveform.totalDurationUs} us, hash=$hash")
            FileLog.d("TX_OK carrier=${carrierHz}Hz duration=${waveform.totalDurationUs}us hash=$hash slices=${waveform.pattern.size}")
            return IrTransmitResult.Success(
                carrierHz = carrierHz,
                durationUs = waveform.totalDurationUs,
                patternHash = hash
            )
        } catch (e: SecurityException) {
            FileLog.d("TX_PERMISSION_DENIED")
            Log.w(tag, "IR transmit failed: TRANSMIT_IR permission denied.", e)
            return IrTransmitResult.PermissionDenied
        } catch (e: Throwable) {
            FileLog.d("TX_EXCEPTION: ${e.message}")
            Log.e(tag, "IR transmit exception: ${e.message}", e)
            return IrTransmitResult.PlatformFailure(e)
        } finally {
            transmitMutex.unlock()
        }
    }

    companion object {
        /**
         * Maximum carrier deviation tolerated before failing closed.
         * IR receivers accept a ±2 kHz modulation drift (standard practice).
         */
        private const val CARRIER_FALLBACK_TOLERANCE_HZ = 2_000

        /**
         * @return `true` when device announces system feature `FEATURE_CONSUMER_IR`.
         */
        fun deviceHasIr(context: Context): Boolean =
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CONSUMER_IR
            )
    }
}
