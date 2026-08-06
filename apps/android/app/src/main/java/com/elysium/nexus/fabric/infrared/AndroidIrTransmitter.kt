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

        // Carrier frequency range validation
        val supportedRanges = carrierRanges()
        if (supportedRanges.isNotEmpty()) {
            val isSupported = supportedRanges.any { range -> waveform.carrierHz in range }
            if (!isSupported) {
                Log.w(tag, "Carrier ${waveform.carrierHz} Hz out of supported ranges $supportedRanges.")
                return@withContext IrTransmitResult.UnsupportedCarrier(
                    requestedHz = waveform.carrierHz,
                    supportedRanges = supportedRanges
                )
            }
        }

        // Validate pattern slices strictly > 0 and total duration < 2s
        if (waveform.pattern.isEmpty()) {
            return@withContext IrTransmitResult.InvalidPattern("Pattern is empty.")
        }
        if (waveform.pattern.any { it <= 0 }) {
            return@withContext IrTransmitResult.InvalidPattern("Pattern contains non-positive slice duration <= 0 us.")
        }
        if (waveform.totalDurationUs >= 2_000_000L) {
            return@withContext IrTransmitResult.InvalidPattern("Pattern total duration exceeds 2-second limit.")
        }

        if (!transmitMutex.tryLock()) {
            Log.w(tag, "IR transmit mutex locked; dropping concurrent request.")
            return@withContext IrTransmitResult.Busy
        }

        try {
            m.transmit(waveform.carrierHz, waveform.pattern)
            val hash = waveform.sha256Hash()
            Log.d(tag, "IR pattern successfully transmitted: ${waveform.carrierHz} Hz, ${waveform.totalDurationUs} us, hash=$hash")
            FileLog.d("TX_OK carrier=${waveform.carrierHz}Hz duration=${waveform.totalDurationUs}us hash=$hash slices=${waveform.pattern.size}")
            return@withContext IrTransmitResult.Success(
                carrierHz = waveform.carrierHz,
                durationUs = waveform.totalDurationUs,
                patternHash = hash
            )
        } catch (e: SecurityException) {
            FileLog.d("TX_PERMISSION_DENIED")
            Log.w(tag, "IR transmit failed: TRANSMIT_IR permission denied.", e)
            return@withContext IrTransmitResult.PermissionDenied
        } catch (e: Throwable) {
            FileLog.d("TX_EXCEPTION: ${e.message}")
            Log.e(tag, "IR transmit exception: ${e.message}", e)
            return@withContext IrTransmitResult.PlatformFailure(e)
        } finally {
            transmitMutex.unlock()
        }
    }

    companion object {
        /**
         * @return `true` when device announces system feature `FEATURE_CONSUMER_IR`.
         */
        fun deviceHasIr(context: Context): Boolean =
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CONSUMER_IR
            )
    }
}
