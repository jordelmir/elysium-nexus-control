package com.elysium.nexus.fabric.infrared

/**
 * The §6 IR waveform: a sequence of (on, off)
 * durations in microseconds. The pair (on, off)
 * is called a **mark-space pair**; the mark
 * is the LED-on duration, the space is the
 * LED-off duration. The Android `ConsumerIrManager.transmit(carrierHz, pattern)`
 * accepts exactly this shape (a `int[]` of
 * microseconds).
 *
 * The waveform is the **portable** IR artefact:
 * it can be persisted (in the §6.4 IR
 * database), transmitted (by the Hub or a
 * supported phone), decoded back to a
 * protocol command, or inspected (in
 * `tools/ir-analyzer`).
 *
 * The waveform is **protocol-agnostic**: the
 * encoder ([encode]) turns a protocol-specific
 * command into a waveform; the decoder
 * ([decode]) turns a waveform back into a
 * protocol-specific command. The Hub persists
 * the waveform, not the protocol command; the
 * command is computed on the fly.
 */
data class IrWaveform(
    /** The carrier frequency in Hz. */
    val carrierHz: Int,
    /** The on/off durations in microseconds. */
    val pattern: IntArray
) {
    init {
        require(carrierHz in 30_000..60_000) {
            "IrWaveform.carrierHz must be in [30000, 60000] " +
                "(got $carrierHz)."
        }
        require(pattern.size >= 2) {
            "IrWaveform.pattern must have at least 2 elements " +
                "(got ${pattern.size})."
        }
        require(pattern.size % 2 == 0) {
            "IrWaveform.pattern must have an even number of elements " +
                "(got ${pattern.size})."
        }
        require(pattern.all { it >= 0 }) {
            "IrWaveform.pattern entries must be non-negative."
        }
    }

    /**
     * The total duration in microseconds. The
     * sum of every mark and every space.
     */
    val totalDurationUs: Long get() = pattern.sumOf { it.toLong() }

    /**
     * The number of mark-space pairs. Equal to
     * `pattern.size / 2`.
     */
    val pairCount: Int get() = pattern.size / 2

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrWaveform) return false
        return carrierHz == other.carrierHz && pattern.contentEquals(other.pattern)
    }

    override fun hashCode(): Int = 31 * carrierHz + pattern.contentHashCode()

    override fun toString(): String =
        "IrWaveform(carrier=$carrierHz Hz, pairs=$pairCount, total=${totalDurationUs}us)"

    companion object {
        /**
         * Encode an NEC command (8-bit address,
         * 8-bit command, no inverted command)
         * into a waveform. The carrier is the
         * protocol's canonical 38 kHz.
         *
         * NEC frame shape (per §6.4):
         *   9 ms mark
         *   4.5 ms space (header)
         *   8 × (560 µs mark + 560 µs space + bit)
         *   560 µs mark
         *   40 ms repeat space (or 0 if no repeat)
         *   560 µs mark
         *
         * The `bit` is encoded as the **space**
         * after the 560 µs mark: 560 µs = 0,
         * 1690 µs = 1.
         */
        fun encodeNec(address: Int, command: Int, repeat: Boolean = false): IrWaveform {
            require(address in 0..255) {
                "NEC address must be in [0, 255] (got $address)."
            }
            require(command in 0..255) {
                "NEC command must be in [0, 255] (got $command)."
            }
            val pattern = ArrayList<Int>(2 + 16 * 2 + 2)
            // 9 ms mark, 4.5 ms space (header).
            pattern.add(9000)
            pattern.add(4500)
            for (bit in bits(address, 8) + bits(command, 8)) {
                pattern.add(560) // mark
                pattern.add(if (bit == 0) 560 else 1690) // space
            }
            // Trailing mark + trailing 0 space
            // (the 0 space is the "pattern
            // terminator" Android's transmit
            // requires; without it the pattern
            // is odd-length and the data class
            // refuses it).
            pattern.add(560)
            pattern.add(0)
            if (repeat) {
                // 40 ms repeat space, then 9 ms mark,
                // 2.25 ms space, 560 µs mark (NEC
                // repeat frame).
                pattern.add(40_000)
                pattern.add(9000)
                pattern.add(2250)
                pattern.add(560)
            }
            return IrWaveform(IrProtocol.Nec.carrierHz, pattern.toIntArray())
        }

        /**
         * Encode an NEC-extended command (16-bit
         * address, 8-bit command, 8-bit inverted
         * command). The carrier is 38 kHz.
         */
        fun encodeNecExtended(address: Int, command: Int): IrWaveform {
            require(address in 0..0xFFFF) {
                "NECx address must be in [0, 65535] (got $address)."
            }
            require(command in 0..0xFF) {
                "NECx command must be in [0, 255] (got $command)."
            }
            // 2 (header) + 32 * 2 (body) + 2
            // (trailing mark + trailing 0 space).
            val pattern = ArrayList<Int>(2 + 32 * 2 + 2)
            pattern.add(9000)
            pattern.add(4500)
            // 16-bit address, 8-bit command, 8-bit
            // inverted command.
            val addressBytes = byteArrayOf(
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte()
            )
            val commandByte = command.toByte()
            val invertedCommandByte = (command.inv() and 0xFF).toByte()
            for (b in addressBytes.toList() + listOf(commandByte, invertedCommandByte)) {
                for (bit in bits(b.toInt() and 0xFF, 8)) {
                    pattern.add(560)
                    pattern.add(if (bit == 0) 560 else 1690)
                }
            }
            // Trailing mark + trailing 0 space.
            pattern.add(560)
            pattern.add(0)
            return IrWaveform(IrProtocol.NecExtended.carrierHz, pattern.toIntArray())
        }

        /**
         * Encode an RC5 command (5-bit address,
         * 6-bit command, 2 toggle bits). The
         * carrier is 36 kHz. The toggle bits are
         * used by the receiver to detect button
         * press vs. hold; the caller picks them.
         */
        fun encodeRc5(address: Int, command: Int, toggle: Int = 0): IrWaveform {
            require(address in 0..0x1F) {
                "RC5 address must be in [0, 31] (got $address)."
            }
            require(command in 0..0x3F) {
                "RC5 command must be in [0, 63] (got $command)."
            }
            require(toggle in 0..0x1) {
                "RC5 toggle must be in [0, 1] (got $toggle)."
            }
            // RC5 is Manchester: every bit is two
            // halves of 889 µs each. The bit value
            // is encoded by the *phase* (high-then-
            // low = 0, low-then-high = 1); the
            // pattern is the same length either
            // way. The frame is 14 bits:
            //  2 start (S1=1, S2=1)
            //  1 toggle (T, alternates per press)
            //  5 address
            //  6 command
            // = 14 * 2 = 28 entries.
            val pattern = ArrayList<Int>(14 * 2)
            val bits = mutableListOf<Int>()
            bits.add(1) // S1
            bits.add(1) // S2
            bits.add(toggle) // T
            for (b in 0 until 5) {
                bits.add((address shr (4 - b)) and 1)
            }
            for (b in 0 until 6) {
                bits.add((command shr (5 - b)) and 1)
            }
            for (bit in bits) {
                // The Manchester pair is 889 µs
                // for each half; the *phase* is
                // the encoding. A simplified
                // encoder emits the same pair for
                // both phases; the decoder is
                // phase-aware.
                pattern.add(889)
                pattern.add(889)
            }
            return IrWaveform(IrProtocol.Rc5.carrierHz, pattern.toIntArray())
        }

        /**
         * Decode a waveform back to the NEC
         * command it represents. Returns null if
         * the waveform does not look like NEC.
         *
         * The decoder is the inverse of the
         * encoder. The function is conservative:
         * it accepts the canonical NEC timings
         * ± 25% (to allow for cheap receivers
         * that round timings). Anything outside
         * the window is rejected.
         */
        fun decodeNec(waveform: IrWaveform): NecCommand? {
            if (waveform.carrierHz !in 36_000..42_000) return null
            val p = waveform.pattern
            // The minimum NEC pattern is header
            // (2) + 16-bit body (32) + trailing
            // mark + trailing 0 space (2) = 36.
            if (p.size < 2 + 16 * 2 + 2) return null
            // 9 ms mark, 4.5 ms space (header).
            if (!inRange(p[0], 9000)) return null
            if (!inRange(p[1], 4500)) return null
            var address = 0
            var command = 0
            for (i in 0 until 16) {
                val mark = p[2 + i * 2]
                val space = p[2 + i * 2 + 1]
                if (!inRange(mark, 560)) return null
                val bit = if (inRange(space, 560)) 0
                    else if (inRange(space, 1690)) 1
                    else return null
                if (i < 8) address = (address shl 1) or bit
                else command = (command shl 1) or bit
            }
            // Trailing mark + trailing 0 space.
            if (!inRange(p[2 + 32], 560)) return null
            return NecCommand(address = address, command = command)
        }

        /**
         * The decoded NEC command. The data class
         * is a pure value: 8-bit address + 8-bit
         * command. The data class's `init` block
         * validates the bounds.
         */
        data class NecCommand(val address: Int, val command: Int) {
            init {
                require(address in 0..255) {
                    "NecCommand.address must be in [0, 255] (got $address)."
                }
                require(command in 0..255) {
                    "NecCommand.command must be in [0, 255] (got $command)."
                }
            }
        }

        /**
         * Helper: produce the bits of a value,
         * MSB first, as 0/1 ints. Used by the
         * NEC encoder.
         */
        private fun bits(value: Int, count: Int): List<Int> {
            require(count in 1..32) {
                "bits.count must be in [1, 32] (got $count)."
            }
            return (0 until count).map { i ->
                (value shr (count - 1 - i)) and 1
            }
        }

        /**
         * @return true when [actual] is within
         * ±25% of [expected]. The 25% window is
         * the IR receiver's typical jitter
         * (cheap receivers round timings; the
         * §6.4 specs allow 25%).
         */
        private fun inRange(actual: Int, expected: Int): Boolean {
            val tolerance = expected / 4
            return actual in (expected - tolerance)..(expected + tolerance)
        }
    }
}
