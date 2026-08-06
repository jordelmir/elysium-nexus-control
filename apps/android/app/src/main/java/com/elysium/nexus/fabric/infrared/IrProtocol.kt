package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrSignal

/**
 * Result of attempting to encode an [IrSignal] into an [IrWaveform].
 */
sealed interface EncodeResult {
    data class Success(val waveform: IrWaveform) : EncodeResult
    data class UnsupportedProtocol(val protocol: IrProtocol) : EncodeResult
    data class InvalidParameters(val reason: String) : EncodeResult
}

/**
 * Result of resolving a protocol string name from an IR catalog or source file.
 */
sealed interface ProtocolResolution {
    data class Supported(val protocol: IrProtocol) : ProtocolResolution
    data class RawPassthrough(val carrierHz: Int) : ProtocolResolution
    data class Unsupported(val originalName: String, val reason: String) : ProtocolResolution
}

/**
 * The §6.4 IR protocol catalog.
 */
enum class IrProtocol(
    /** Display name for the UI. */
    val displayName: String,
    /** Canonical carrier frequency in Hz. */
    val carrierHz: Int,
    /** The bit-encoding shape. */
    val encoding: IrEncoding
) {
    Nec("NEC", 38_000, IrEncoding.PulseDistance),
    NecExtended("NECx", 38_000, IrEncoding.PulseDistance),
    Rc5("RC5", 36_000, IrEncoding.Biphasic),
    Rc6("RC6", 36_000, IrEncoding.Biphasic),
    SonySirc("SIRC", 40_000, IrEncoding.PulseWidth),
    Samsung("Samsung", 38_000, IrEncoding.PulseDistance),
    Kaseikyo("Kaseikyo", 38_000, IrEncoding.PulseDistance),
    Raw("Raw waveform", 38_000, IrEncoding.Raw);

    companion object {
        const val DEFAULT_CARRIER_HZ: Int = 38_000

        /**
         * Resolves protocol string name cleanly.
         * Evaluates NECx BEFORE NEC.
         * Returns [ProtocolResolution.Unsupported] for unknown protocols. Zero silent NEC fallback.
         */
        fun resolveProtocol(name: String, carrierHz: Int = DEFAULT_CARRIER_HZ): ProtocolResolution {
            val clean = name.trim()
            if (clean.equals("RAW", ignoreCase = true)) {
                return ProtocolResolution.RawPassthrough(carrierHz)
            }
            return when {
                clean.startsWith("NECx", ignoreCase = true) || clean.startsWith("NECext", ignoreCase = true) || clean.startsWith("NECx2", ignoreCase = true) ->
                    ProtocolResolution.Supported(NecExtended)
                clean.startsWith("NEC", ignoreCase = true) ->
                    ProtocolResolution.Supported(Nec)
                clean.startsWith("Samsung", ignoreCase = true) ->
                    ProtocolResolution.Supported(Samsung)
                clean.startsWith("SIRC", ignoreCase = true) || clean.startsWith("Sony", ignoreCase = true) ->
                    ProtocolResolution.Supported(SonySirc)
                clean.startsWith("RC5", ignoreCase = true) ->
                    ProtocolResolution.Supported(Rc5)
                clean.startsWith("RC6", ignoreCase = true) ->
                    ProtocolResolution.Supported(Rc6)
                clean.startsWith("Kaseikyo", ignoreCase = true) || clean.startsWith("Panasonic", ignoreCase = true) ->
                    ProtocolResolution.Supported(Kaseikyo)
                else ->
                    ProtocolResolution.Unsupported(clean, "Protocol '$clean' is not registered in ProtocolCodecRegistry.")
            }
        }

        /**
         * Exhaustive encoder dispatching without silent fallback to NEC.
         * If a protocol is unsupported or parameters are invalid, returns explicit [EncodeResult].
         */
        fun encode(signal: IrSignal): EncodeResult {
            return when (signal) {
                is IrSignal.Raw -> {
                    if (signal.carrierHz !in 30_000..60_000) {
                        EncodeResult.InvalidParameters("Carrier frequency ${signal.carrierHz} Hz is out of valid IR range [30000, 60000] Hz")
                    } else {
                        try {
                            val waveform = IrWaveform(
                                carrierHz = signal.carrierHz,
                                pattern = signal.patternUs
                            )
                            EncodeResult.Success(waveform)
                        } catch (e: Exception) {
                            EncodeResult.InvalidParameters("Raw waveform invalid: ${e.message}")
                        }
                    }
                }
                is IrSignal.Encoded -> {
                    try {
                        when (signal.protocol) {
                            Nec -> EncodeResult.Success(
                                IrWaveform.encodeNec(signal.address, signal.command)
                            )
                            NecExtended -> EncodeResult.Success(
                                IrWaveform.encodeNecExtended(signal.address, signal.command)
                            )
                            Samsung -> EncodeResult.Success(
                                IrWaveform.encodeSamsung(signal.address, signal.command)
                            )
                            SonySirc -> EncodeResult.Success(
                                IrWaveform.encodeSonySirc(signal.address, signal.command)
                            )
                            Rc5 -> EncodeResult.Success(
                                IrWaveform.encodeRc5(signal.address, signal.command, signal.toggle)
                            )
                            Rc6 -> EncodeResult.Success(
                                IrWaveform.encodeRc6(signal.address, signal.command, signal.toggle)
                            )
                            Kaseikyo -> EncodeResult.Success(
                                IrWaveform.encodeKaseikyo(signal.address, signal.command)
                            )
                            Raw -> EncodeResult.InvalidParameters("Raw protocol must use IrSignal.Raw payload.")
                        }
                    } catch (e: Exception) {
                        EncodeResult.InvalidParameters("Encoder exception for ${signal.protocol}: ${e.message}")
                    }
                }
            }
        }
    }
}

/**
 * The bit-encoding shape.
 */
enum class IrEncoding {
    PulseDistance,
    PulseWidth,
    Biphasic,
    Raw
}
