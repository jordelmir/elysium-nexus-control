package com.elysium.nexus.fabric.infrared

/**
 * The §6.4 IR protocol catalog.
 *
 * The catalog is the set of protocols the
 * Elysium Nexus IR engine knows how to
 * encode and decode. A protocol is a
 * "shape" of an IR command: carrier
 * frequency, bit-encoding (pulse-distance
 * or pulse-width), header, address,
 * command, trailer, repeat behaviour.
 *
 * The catalog is **closed**: adding a new
 * protocol is an ADR + a new `encode /
 * decode` pair in [IrWaveform]. The §6
 * spec lists NEC, NECx, RC5, RC6, SIRC,
 * Samsung, Kaseikyo, Panasonic, JVC, Sharp,
 * Denon, Pioneer, LG, Mitsubishi, Daikin,
 * Gree, Fujitsu, Toshiba, Hitachi, Midea,
 * Whirlpool, and "raw waveform fallback".
 * This iteration ships the four most
 * common (NEC, NECx, RC5, SIRC) plus the
 * raw fallback; the rest follow in
 * subsequent phases.
 *
 * ## Why a closed enum and not plugin reflection
 *
 * The protocol plugin system (per §33) is
 * the right shape for vendor-specific
 * protocols. The protocols in this enum
 * are the *universal* ones every IR
 * receiver can decode; the vendor plugins
 * extend [IrWaveform] for proprietary
 * formats.
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
        /**
         * The default carrier for the [Raw] protocol.
         * The IR receiver will measure the actual
         * carrier during learning (per §6.3); the
         * default is the most common consumer IR
         * carrier.
         */
        const val DEFAULT_CARRIER_HZ: Int = 38_000
    }
}

/**
 * The bit-encoding shape. A protocol is one
 * of three shapes:
 *
 * - [PulseDistance] (NEC, NECx, Samsung,
 *   Kaseikyo): a marker pulse + a space; the
 *   space length encodes the bit (short = 0,
 *   long = 1).
 * - [PulseWidth] (SIRC): a marker pulse + a
 *   space + a pulse; the pulse length encodes
 *   the bit (short = 0, long = 1).
 * - [Biphasic] (RC5, RC6): every bit is two
 *   equal halves; the order of the halves
 *   encodes the bit (Manchester encoding).
 * - [Raw]: no encoding; the caller passes a
 *   list of (on, off) durations in microseconds.
 */
enum class IrEncoding {
    PulseDistance,
    PulseWidth,
    Biphasic,
    Raw
}
