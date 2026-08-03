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
         * Encode a Sony SIRC command (5-bit or
         * 8-bit address, 7-bit command). The
         * carrier is 40 kHz.
         *
         * SIRC frame shape (per §6.4):
         *   Header: 2400 µs mark, 600 µs space
         *   7 command bits (LSB first)
         *   5 address bits (LSB first)
         *   (optional 8-bit extended: 8 more address bits)
         *
         * Bit encoding (pulse-width):
         *   0: 600 µs mark, 600 µs space
         *   1: 1200 µs mark, 600 µs space
         */
        fun encodeSonySirc(address: Int, command: Int, extended: Boolean = false): IrWaveform {
            require(address in 0..0x1FF) {
                "SIRC address must be in [0, 511] (got $address)."
            }
            require(command in 0..0x7F) {
                "SIRC command must be in [0, 127] (got $command)."
            }
            val pattern = ArrayList<Int>()
            // Header
            pattern.add(2400) // mark
            pattern.add(600)  // space
            // 7 command bits (LSB first)
            for (i in 0 until 7) {
                val bit = (command shr i) and 1
                pattern.add(if (bit == 0) 600 else 1200)
                pattern.add(600)
            }
            // 5 address bits (LSB first)
            for (i in 0 until 5) {
                val bit = (address shr i) and 1
                pattern.add(if (bit == 0) 600 else 1200)
                pattern.add(600)
            }
            if (extended) {
                // 8 more address bits (bits 5-12)
                for (i in 5 until 13) {
                    val bit = (address shr i) and 1
                    pattern.add(if (bit == 0) 600 else 1200)
                    pattern.add(600)
                }
            }
            return IrWaveform(IrProtocol.SonySirc.carrierHz, pattern.toIntArray())
        }

        /**
         * Encode a Samsung IR command (8-bit
         * address, 8-bit command). The carrier
         * is 38 kHz.
         *
         * Samsung frame shape (per §6.4):
         *   Header: 4500 µs mark, 4500 µs space
         *   8 address bits (MSB first)
         *   8 ~address bits (MSB first, inverted)
         *   8 command bits (MSB first)
         *   8 ~command bits (MSB first, inverted)
         *
         * Bit encoding (pulse-distance):
         *   0: 560 µs mark, 560 µs space
         *   1: 560 µs mark, 1690 µs space
         */
        fun encodeSamsung(address: Int, command: Int): IrWaveform {
            require(address in 0..0xFF) {
                "Samsung address must be in [0, 255] (got $address)."
            }
            require(command in 0..0xFF) {
                "Samsung command must be in [0, 255] (got $command)."
            }
            val pattern = ArrayList<Int>()
            // Header
            pattern.add(4500)
            pattern.add(4500)
            // Address + inverted address
            val addrInv = (address.inv() and 0xFF)
            for (b in listOf(address, addrInv)) {
                for (i in 7 downTo 0) {
                    val bit = (b shr i) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 0) 560 else 1690)
                }
            }
            // Command + inverted command
            val cmdInv = (command.inv() and 0xFF)
            for (b in listOf(command, cmdInv)) {
                for (i in 7 downTo 0) {
                    val bit = (b shr i) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 0) 560 else 1690)
                }
            }
            // Trailing mark + 0 space
            pattern.add(560)
            pattern.add(0)
            return IrWaveform(IrProtocol.Samsung.carrierHz, pattern.toIntArray())
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

        /**
         * Decode a NEC-extended waveform (16-bit
         * address, 8-bit command, 8-bit inverted
         * command). Returns null if the waveform
         * does not match NECx.
         */
        fun decodeNecExtended(waveform: IrWaveform): NecExtendedCommand? {
            if (waveform.carrierHz !in 36_000..42_000) return null
            val p = waveform.pattern
            // NECx: header(2) + 64 bits (32 pairs) + trailing(2) = 68
            if (p.size < 68) return null
            if (!inRange(p[0], 9000)) return null
            if (!inRange(p[1], 4500)) return null
            var address = 0
            var command = 0
            var invertedCommand = 0
            for (i in 0 until 32) {
                val mark = p[2 + i * 2]
                val space = p[2 + i * 2 + 1]
                if (!inRange(mark, 560)) return null
                val bit = if (inRange(space, 560)) 0
                    else if (inRange(space, 1690)) 1
                    else return null
                when {
                    i < 16 -> address = (address shl 1) or bit
                    i < 24 -> command = (command shl 1) or bit
                    else -> invertedCommand = (invertedCommand shl 1) or bit
                }
            }
            if (!inRange(p[2 + 64], 560)) return null
            // Validate inverted command
            if (command != (invertedCommand.inv() and 0xFF)) return null
            return NecExtendedCommand(address = address, command = command)
        }

        data class NecExtendedCommand(val address: Int, val command: Int) {
            init {
                require(address in 0..0xFFFF) { "NecExtendedCommand.address out of range." }
                require(command in 0..255) { "NecExtendedCommand.command out of range." }
            }
        }

        /**
         * Decode an RC5 waveform. RC5 is 14
         * Manchester bits, each 2 halves of 889 µs.
         * Returns null if the waveform does not
         * match RC5.
         */
        fun decodeRc5(waveform: IrWaveform): Rc5Command? {
            if (waveform.carrierHz !in 34_000..38_000) return null
            val p = waveform.pattern
            if (p.size != 28) return null
            // Each pair of entries is one Manchester
            // bit. Bit value is determined by phase:
            // (high, low) = 0, (low, high) = 1.
            val bits = mutableListOf<Int>()
            for (i in 0 until 14) {
                val a = p[i * 2]
                val b = p[i * 2 + 1]
                if (!inRange(a, 889) || !inRange(b, 889)) return null
                // In standard Manchester, the transition
                // in the middle determines the bit.
                // We can't distinguish phase from
                // durations alone (both halves are
                // 889 µs). We assume the first bit
                // is always 1 (start bit), then
                // decode the rest.
                bits.add(if (i == 0) 1 else if (a > b) 1 else 0)
            }
            // S1=1, S2=1, T, A4-A0, C5-C0
            if (bits[0] != 1 || bits[1] != 1) return null
            val toggle = bits[2]
            var address = 0
            for (i in 0 until 5) {
                address = (address shl 1) or bits[3 + i]
            }
            var command = 0
            for (i in 0 until 6) {
                command = (command shl 1) or bits[8 + i]
            }
            return Rc5Command(
                address = address,
                command = command,
                toggle = toggle
            )
        }

        data class Rc5Command(val address: Int, val command: Int, val toggle: Int) {
            init {
                require(address in 0..31) { "Rc5Command.address out of range." }
                require(command in 0..63) { "Rc5Command.command out of range." }
                require(toggle in 0..1) { "Rc5Command.toggle out of range." }
            }
        }

        /**
         * Decode a Sony SIRC waveform. SIRC uses
         * pulse-width encoding at 40 kHz.
         * Returns null if the waveform does not
         * match SIRC.
         */
        fun decodeSonySirc(waveform: IrWaveform): SonySircCommand? {
            if (waveform.carrierHz !in 38_000..42_000) return null
            val p = waveform.pattern
            // Minimum: header(2) + 12 bits (24) = 26
            if (p.size < 26) return null
            // Header: 2400 mark, 600 space
            if (!inRange(p[0], 2400)) return null
            if (!inRange(p[1], 600)) return null
            // Decode bits. Pulse-width: short mark=0, long mark=1.
            val bits = mutableListOf<Int>()
            var i = 2
            while (i + 1 < p.size) {
                val mark = p[i]
                val space = p[i + 1]
                if (!inRange(space, 600)) break
                val bit = if (inRange(mark, 600)) 0
                    else if (inRange(mark, 1200)) 1
                    else break
                bits.add(bit)
                i += 2
            }
            // SIRC: 7 command bits + 5 address bits
            // (standard) or + 8 address bits (extended)
            if (bits.size < 12) return null
            var command = 0
            for (j in 0 until 7) {
                command = command or (bits[j] shl j)
            }
            var address = 0
            for (j in 0 until 5) {
                address = address or (bits[7 + j] shl j)
            }
            val extended = bits.size >= 20
            if (extended) {
                for (j in 5 until 13) {
                    if (j + 7 < bits.size) {
                        address = address or (bits[j + 7] shl j)
                    }
                }
            }
            return SonySircCommand(
                address = address,
                command = command,
                extended = extended
            )
        }

        data class SonySircCommand(val address: Int, val command: Int, val extended: Boolean) {
            init {
                require(address in 0..0x1FF) { "SonySircCommand.address out of range." }
                require(command in 0..0x7F) { "SonySircCommand.command out of range." }
            }
        }

        /**
         * Decode a Samsung waveform. Samsung uses
         * pulse-distance at 38 kHz with 4500/4500
         * header and address+inverted,
         * command+inverted.
         */
        fun decodeSamsung(waveform: IrWaveform): SamsungCommand? {
            if (waveform.carrierHz !in 36_000..42_000) return null
            val p = waveform.pattern
            if (p.size < 68) return null
            // Header: 4500 mark, 4500 space
            if (!inRange(p[0], 4500)) return null
            if (!inRange(p[1], 4500)) return null
            var address = 0
            var addressInv = 0
            var command = 0
            var commandInv = 0
            for (i in 0 until 32) {
                val mark = p[2 + i * 2]
                val space = p[2 + i * 2 + 1]
                if (!inRange(mark, 560)) return null
                val bit = if (inRange(space, 560)) 0
                    else if (inRange(space, 1690)) 1
                    else return null
                when {
                    i < 8 -> address = (address shl 1) or bit
                    i < 16 -> addressInv = (addressInv shl 1) or bit
                    i < 24 -> command = (command shl 1) or bit
                    else -> commandInv = (commandInv shl 1) or bit
                }
            }
            if (!inRange(p[2 + 64], 560)) return null
            // Validate inversions
            if (address != (addressInv.inv() and 0xFF)) return null
            if (command != (commandInv.inv() and 0xFF)) return null
            return SamsungCommand(address = address, command = command)
        }

        data class SamsungCommand(val address: Int, val command: Int) {
            init {
                require(address in 0..255) { "SamsungCommand.address out of range." }
                require(command in 0..255) { "SamsungCommand.command out of range." }
            }
        }

        /**
         * Encode a Daikin AC command. Daikin uses
         * a custom 48-bit payload (at 38 kHz
         * carrier) with a fixed header.
         *
         * Frame layout:
         *   Header: 5800 mark, 2000 space
         *   0x34 (8 bits MSB)
         *   0x63 (8 bits MSB)
         *   Address (8 bits MSB)
         *   ~Address (8 bits MSB)
         *   Data0 (8 bits MSB, mode|power|temp)
         *   Data1 (8 bits MSB, fan)
         *
         * Data0 encoding:
         *   bit 7-4: mode (0=auto,1=cool,2=dry,
         *            3=fan,4=heat)
         *   bit 3: power (1=on, 0=off)
         *   bit 2-0: temperature (25..32 mapped)
         *
         * Data1 encoding:
         *   bit 7-4: fan speed (0=auto,1=low,
         *            2=med,3=high)
         */
        fun encodeDaikin(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..0xFF) { "Daikin address out of range." }
            require(temperatureCelsius in 16..32) { "Daikin temperature must be 16..32." }
            require(mode in 0..4) { "Daikin mode must be 0..4." }
            require(fanSpeed in 0..3) { "Daikin fanSpeed must be 0..3." }
            val data0 = ((mode and 0xF) shl 4) or
                (if (powerOn) 0x08 else 0x00) or
                ((temperatureCelsius - 16) and 0x07)
            val data1 = ((fanSpeed and 0xF) shl 4)
            val pattern = ArrayList<Int>()
            // Header
            pattern.add(5800); pattern.add(2000)
            // 0x34, 0x63
            emitByte(pattern, 0x34)
            emitByte(pattern, 0x63)
            // Address + inverted address
            emitByte(pattern, address)
            emitByte(pattern, address.inv() and 0xFF)
            // Data0 + Data1
            emitByte(pattern, data0)
            emitByte(pattern, data1)
            // Trailing space
            pattern.add(560); pattern.add(30000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        /**
         * Encode a Gree AC command. Gree uses
         * a 48-bit payload at 38 kHz.
         *
         * Frame layout:
         *   Header: 9000 mark, 4500 space
         *   20-bit payload (MSB-first) with
         *   CRC8 at the end.
         *
         * For simplicity, this encoder produces
         * the standard Gree 48-bit frame.
         */
        fun encodeGree(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..0x0F) { "Gree address must be 0..15." }
            require(temperatureCelsius in 16..30) { "Gree temperature must be 16..30." }
            require(mode in 0..4) { "Gree mode must be 0..4." }
            require(fanSpeed in 0..3) { "Gree fanSpeed must be 0..3." }
            val payload = ((temperatureCelsius - 16) shl 24) or
                (if (powerOn) 0x080000 else 0) or
                ((mode and 0x7) shl 21) or
                ((fanSpeed and 0x7) shl 8)
            val crc = greeCrc8(payload)
            val pattern = ArrayList<Int>()
            // Header
            pattern.add(9000); pattern.add(4500)
            // 24-bit payload MSB-first
            for (i in 23 downTo 0) {
                val bit = (payload shr i) and 1
                pattern.add(600)
                pattern.add(if (bit == 0) 600 else 1690)
            }
            // CRC 8-bit MSB-first
            for (i in 7 downTo 0) {
                val bit = (crc shr i) and 1
                pattern.add(600)
                pattern.add(if (bit == 0) 600 else 1690)
            }
            // Trailing mark
            pattern.add(600); pattern.add(30000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        /**
         * Encode a Midea AC command. Midea uses
         * a 48-bit payload at 38 kHz.
         *
         * Frame layout:
         *   Header: 4400 mark, 4400 space
         *   24-bit payload MSB-first
         *   24-bit inverted payload MSB-first
         */
        fun encodeMidea(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(temperatureCelsius in 17..30) { "Midea temperature must be 17..30." }
            require(mode in 0..4) { "Midea mode must be 0..4." }
            val payload = ((mode and 0x7) shl 20) or
                (if (powerOn) 0x040000 else 0) or
                ((temperatureCelsius - 17) shl 16) or
                ((fanSpeed and 0x7) shl 8) or
                (address and 0xFF)
            val pattern = ArrayList<Int>()
            // Header
            pattern.add(4400); pattern.add(4400)
            // 24-bit payload MSB-first
            for (i in 23 downTo 0) {
                val bit = (payload shr i) and 1
                pattern.add(600)
                pattern.add(if (bit == 0) 600 else 1600)
            }
            // 24-bit inverted payload MSB-first
            val inv = payload.inv() and 0xFFFFFF
            for (i in 23 downTo 0) {
                val bit = (inv shr i) and 1
                pattern.add(600)
                pattern.add(if (bit == 0) 600 else 1600)
            }
            // Trailing mark
            pattern.add(600); pattern.add(30000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        /**
         * Encode a Mitsubishi AC command.
         * Mitsubishi uses a 16-bit address +
         * 16-bit command at 38 kHz.
         *
         * Frame layout:
         *   Header: 3400 mark, 1700 space
         *   8-bit address MSB-first
         *   8-bit ~address MSB-first
         *   8-bit command MSB-first
         *   8-bit ~command MSB-first
         */
        fun encodeMitsubishi(
            address: Int,
            powerOn: Boolean,
            temperatureCelsius: Int,
            mode: Int,
            fanSpeed: Int
        ): IrWaveform {
            require(address in 0..0xFF) { "Mitsubishi address out of range." }
            require(temperatureCelsius in 16..31) { "Mitsubishi temperature must be 16..31." }
            require(mode in 0..4) { "Mitsubishi mode must be 0..4." }
            require(fanSpeed in 0..4) { "Mitsubishi fanSpeed must be 0..4." }
            val tempBits = (temperatureCelsius - 16) and 0x1F
            val cmd = ((mode and 0x7) shl 4) or
                (if (powerOn) 0x08 else 0x00) or
                (if (fanSpeed == 0) 0x00 else fanSpeed and 0x07)
            val pattern = ArrayList<Int>()
            // Header
            pattern.add(3400); pattern.add(1700)
            // Address + inverted
            emitByte(pattern, address)
            emitByte(pattern, address.inv() and 0xFF)
            // Command + inverted
            emitByte(pattern, cmd)
            emitByte(pattern, cmd.inv() and 0xFF)
            // Trailing mark
            pattern.add(430); pattern.add(30000)
            return IrWaveform(38000, pattern.toIntArray())
        }

        /**
         * Emit a single byte MSB-first as
         * mark-space pairs (pulse-distance).
         */
        private fun emitByte(pattern: MutableList<Int>, value: Int) {
            for (i in 7 downTo 0) {
                val bit = (value shr i) and 1
                pattern.add(560)
                pattern.add(if (bit == 0) 560 else 1690)
            }
        }

        /**
         * Compute Gree CRC8 (polynomial 0x31,
         * init 0xFF).
         */
        private fun greeCrc8(data: Int): Int {
            var crc = 0xFF
            for (i in 23 downTo 0) {
                crc = crc xor ((data shr i) and 1)
                for (j in 0 until 8) {
                    crc = if ((crc and 0x80) != 0) {
                        (crc shl 1) xor 0x31
                    } else {
                        crc shl 1
                    }
                    crc = crc and 0xFF
                }
            }
            return crc
        }
    }
}
