package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the §6 [IrProtocol] +
 * [IrWaveform] POC. The tests are pure
 * (no Android `ConsumerIrManager`); the
 * Android adapter is exercised on-device
 * (Phase 6 has no HiL until a phone with
 * an IR emitter is in the lab).
 */
class IrWaveformTest {

    @Test
    fun `IrWaveform rejects a carrier outside 30-60 kHz`() {
        try {
            IrWaveform(carrierHz = 25_000, pattern = intArrayOf(1000, 1000))
            fail("Expected IllegalArgumentException for carrier < 30000.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `IrWaveform rejects an odd-length pattern`() {
        try {
            IrWaveform(carrierHz = 38_000, pattern = intArrayOf(1000, 1000, 1000))
            fail("Expected IllegalArgumentException for odd pattern length.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `IrWaveform total duration is the sum of all entries`() {
        val w = IrWaveform(
            carrierHz = 38_000,
            pattern = intArrayOf(9000, 4500, 560, 1690, 560, 0)
        )
        assertEquals(9000L + 4500L + 560L + 1690L + 560L + 0L, w.totalDurationUs)
        // 6 entries / 2 = 3 pairs.
        assertEquals(3, w.pairCount)
    }

    @Test
    fun `NEC encode produces the canonical 16-bit pattern shape`() {
        val w = IrWaveform.encodeNec(address = 0x01, command = 0x02)
        // The header is 9 ms mark + 4.5 ms space.
        assertEquals(9000, w.pattern[0])
        assertEquals(4500, w.pattern[1])
        // 16 bits of (mark, space) follow.
        // Total length: 2 (header) + 16 * 2 +
        // 2 (trailing mark + trailing 0 space)
        // = 36 entries.
        assertEquals(36, w.pattern.size)
        // The carrier is 38 kHz.
        assertEquals(38_000, w.carrierHz)
    }

    @Test
    fun `NEC encode with repeat adds a 9 ms 2_25 ms 560 us tail`() {
        val w = IrWaveform.encodeNec(address = 0x10, command = 0x20, repeat = true)
        // The tail is 40 ms (repeat space) + 9 ms
        // (mark) + 2.25 ms (space) + 560 us (mark)
        // = 4 extra entries (40_000, 9000, 2250,
        // 560). Total: 36 + 4 = 40.
        assertEquals(40, w.pattern.size)
        // The 4 tail entries are at indices 36..39.
        assertEquals(40_000, w.pattern[36])
        assertEquals(9000, w.pattern[37])
        assertEquals(2250, w.pattern[38])
        assertEquals(560, w.pattern[39])
    }

    @Test
    fun `NEC decode round-trips an encoded command`() {
        val original = IrWaveform.encodeNec(address = 0x42, command = 0x84)
        val decoded = IrWaveform.decodeNec(original)
        assertNotNull(decoded)
        assertEquals(0x42, decoded!!.address)
        assertEquals(0x84, decoded.command)
    }

    @Test
    fun `NEC decode returns null for an out-of-range carrier`() {
        // The constructor refuses out-of-range
        // carriers; the decoder never sees them.
        // The test confirms the constructor's
        // rejection is the boundary.
        try {
            IrWaveform(
                carrierHz = 25_000, // out of range
                pattern = intArrayOf(9000, 4500, 560, 1690, 560)
            )
            fail("Expected IllegalArgumentException for out-of-range carrier.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `NEC decode returns null for a too-short pattern`() {
        val w = IrWaveform(
            carrierHz = 38_000,
            pattern = intArrayOf(9000, 4500) // missing the 16-bit body
        )
        assertNull(IrWaveform.decodeNec(w))
    }

    @Test
    fun `NEC decode accepts a waveform with 25 percent timing tolerance`() {
        // The receiver's cheap hardware rounds
        // timings; the decoder's tolerance is
        // ±25% per §6.4.
        val raw = IrWaveform.encodeNec(address = 0x10, command = 0x20)
        val jittered = raw.pattern.map { v ->
            // 20% jitter — within tolerance.
            (v * 1.2f).toInt()
        }.toIntArray()
        val decoded = IrWaveform.decodeNec(
            IrWaveform(carrierHz = 38_000, pattern = jittered)
        )
        assertNotNull(decoded)
        assertEquals(0x10, decoded!!.address)
        assertEquals(0x20, decoded.command)
    }

    @Test
    fun `NEC extended encodes 32 bits of address+command+inverted-command`() {
        val w = IrWaveform.encodeNecExtended(address = 0x1234, command = 0x56)
        // Header (2) + 32 bits (64) + trailing
        // mark (1) + trailing 0 space (1) = 68
        // entries.
        assertEquals(2 + 32 * 2 + 2, w.pattern.size)
        // Carrier is 38 kHz.
        assertEquals(38_000, w.carrierHz)
    }

    @Test
    fun `NEC extended rejects an address above 16 bits`() {
        try {
            IrWaveform.encodeNecExtended(address = 0x10000, command = 0x56)
            fail("Expected IllegalArgumentException for address > 16 bits.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `NEC rejects an address above 8 bits`() {
        try {
            IrWaveform.encodeNec(address = 0x100, command = 0x01)
            fail("Expected IllegalArgumentException for address > 8 bits.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `RC5 encodes 14 bits (2 start + 1 toggle + 5 address + 6 command)`() {
        // The Manchester encoding is one pair
        // per bit (always 2 entries). 14 bits
        // = 28 entries.
        val w = IrWaveform.encodeRc5(address = 0x05, command = 0x0A, toggle = 0)
        assertEquals(28, w.pattern.size)
        // Carrier is 36 kHz.
        assertEquals(36_000, w.carrierHz)
    }

    @Test
    fun `RC5 rejects a 6-bit address`() {
        try {
            IrWaveform.encodeRc5(address = 0x20, command = 0x01)
            fail("Expected IllegalArgumentException for 6-bit address.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `RC5 rejects a 7-bit command`() {
        try {
            IrWaveform.encodeRc5(address = 0x01, command = 0x40)
            fail("Expected IllegalArgumentException for 7-bit command.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `IrProtocol catalog includes NEC, NECx, RC5, SonySIRC, Samsung, Kaseikyo, Raw`() {
        val expected = setOf("Nec", "NecExtended", "Rc5", "Rc6", "SonySirc", "Samsung", "Kaseikyo", "Raw")
        val actual = IrProtocol.values().map { it.name }.toSet()
        for (name in expected) {
            assertTrue("Expected IR protocol '$name' (per §6.4)", name in actual)
        }
    }

    @Test
    fun `every IR protocol carries a carrier and an encoding`() {
        IrProtocol.values().forEach { p ->
            assertTrue("Protocol ${p.name} has invalid carrier ${p.carrierHz}", p.carrierHz in 30_000..60_000)
            assertNotNull("Protocol ${p.name} has null encoding", p.encoding)
        }
    }

    @Test
    fun `NEC encode produces a waveform with a non-trivial byte sequence`() {
        val a = IrWaveform.encodeNec(address = 0x01, command = 0x02)
        val b = IrWaveform.encodeNec(address = 0x02, command = 0x01)
        assertNotEquals(a.pattern.toList(), b.pattern.toList())
    }

    // === SIRC ENCODE TESTS ================================

    @Test
    fun `SIRC encode produces 24 entries for 12-bit (7 cmd + 5 addr)`() {
        val w = IrWaveform.encodeSonySirc(address = 0x05, command = 0x0A)
        // Header (2) + 7 cmd bits (14) + 5 addr bits (10) = 26
        assertEquals(26, w.pattern.size)
        assertEquals(IrProtocol.SonySirc.carrierHz, w.carrierHz)
    }

    @Test
    fun `SIRC encode extended produces 42 entries for 20-bit address`() {
        val w = IrWaveform.encodeSonySirc(address = 0x123, command = 0x0A, extended = true)
        // Header (2) + 7 cmd bits (14) + 5 addr bits (10) + 8 ext addr bits (16) = 42
        assertEquals(42, w.pattern.size)
        assertEquals(IrProtocol.SonySirc.carrierHz, w.carrierHz)
    }

    @Test
    fun `SIRC encode rejects address above 511`() {
        try {
            IrWaveform.encodeSonySirc(address = 0x200, command = 0x01)
            fail("Expected IllegalArgumentException for address > 511.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `SIRC encode rejects command above 127`() {
        try {
            IrWaveform.encodeSonySirc(address = 0x01, command = 0x80)
            fail("Expected IllegalArgumentException for command > 127.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `SIRC header is 2400 mark, 600 space`() {
        val w = IrWaveform.encodeSonySirc(address = 0x01, command = 0x01)
        assertEquals(2400, w.pattern[0])
        assertEquals(600, w.pattern[1])
    }

    @Test
    fun `SIRC bit encoding uses pulse-width (600 or 1200 mark, 600 space)`() {
        val w = IrWaveform.encodeSonySirc(address = 0x00, command = 0x7F)
        // All command bits are 1 (1200 mark, 600 space)
        for (i in 2 until 16 step 2) {
            assertEquals(1200, w.pattern[i])
            assertEquals(600, w.pattern[i + 1])
        }
    }

    // === SAMSUNG ENCODE TESTS ==============================

    @Test
    fun `Samsung encode produces 68 entries (header + 32 bits + trailing)`() {
        val w = IrWaveform.encodeSamsung(address = 0x07, command = 0x02)
        // Header (2) + 8 addr (16) + 8 ~addr (16) + 8 cmd (16) + 8 ~cmd (16) + trailing (2) = 68
        assertEquals(68, w.pattern.size)
        assertEquals(IrProtocol.Samsung.carrierHz, w.carrierHz)
    }

    @Test
    fun `Samsung encode rejects address above 255`() {
        try {
            IrWaveform.encodeSamsung(address = 0x100, command = 0x01)
            fail("Expected IllegalArgumentException for address > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Samsung encode rejects command above 255`() {
        try {
            IrWaveform.encodeSamsung(address = 0x01, command = 0x100)
            fail("Expected IllegalArgumentException for command > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Samsung header is 4500 mark, 4500 space`() {
        val w = IrWaveform.encodeSamsung(address = 0x01, command = 0x01)
        assertEquals(4500, w.pattern[0])
        assertEquals(4500, w.pattern[1])
    }

    @Test
    fun `Samsung encode produces different waveforms for different commands`() {
        val a = IrWaveform.encodeSamsung(address = 0x07, command = 0x01)
        val b = IrWaveform.encodeSamsung(address = 0x07, command = 0x02)
        assertNotEquals(a.pattern.toList(), b.pattern.toList())
    }

    // === DAIKIN ENCODE TESTS ==============================

    @Test
    fun `Daikin encode produces a waveform at 38 kHz`() {
        val w = IrWaveform.encodeDaikin(
            address = 0x12, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 2
        )
        assertEquals(38_000, w.carrierHz)
        // Header (2) + 6 bytes encoded (6 × 16 = 96) + trailing (2) = 100
        assertEquals(100, w.pattern.size)
    }

    @Test
    fun `Daikin encode rejects address above 255`() {
        try {
            IrWaveform.encodeDaikin(
                address = 0x100, powerOn = true,
                temperatureCelsius = 24, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for address > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Daikin encode rejects temperature outside 16 to 32`() {
        try {
            IrWaveform.encodeDaikin(
                address = 0x01, powerOn = true,
                temperatureCelsius = 15, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature < 16.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
        try {
            IrWaveform.encodeDaikin(
                address = 0x01, powerOn = true,
                temperatureCelsius = 33, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature > 32.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Daikin encode rejects mode outside 0 to 4`() {
        try {
            IrWaveform.encodeDaikin(
                address = 0x01, powerOn = true,
                temperatureCelsius = 24, mode = 5, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for mode > 4.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Daikin encode rejects fanSpeed outside 0 to 3`() {
        try {
            IrWaveform.encodeDaikin(
                address = 0x01, powerOn = true,
                temperatureCelsius = 24, mode = 1, fanSpeed = 4
            )
            fail("Expected IllegalArgumentException for fanSpeed > 3.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Daikin encode different temperatures produce different waveforms`() {
        val a = IrWaveform.encodeDaikin(
            address = 0x01, powerOn = true,
            temperatureCelsius = 20, mode = 1, fanSpeed = 0
        )
        val b = IrWaveform.encodeDaikin(
            address = 0x01, powerOn = true,
            temperatureCelsius = 25, mode = 1, fanSpeed = 0
        )
        assertNotEquals(a.pattern.toList(), b.pattern.toList())
    }

    // === GREE ENCODE TESTS ================================

    @Test
    fun `Gree encode produces a waveform at 38 kHz`() {
        val w = IrWaveform.encodeGree(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 2
        )
        assertEquals(38_000, w.carrierHz)
        // Header (2) + 24 payload bits (48) + 8 CRC bits (16) + trailing (2) = 68
        assertEquals(68, w.pattern.size)
    }

    @Test
    fun `Gree encode rejects address above 15`() {
        try {
            IrWaveform.encodeGree(
                address = 0x10, powerOn = true,
                temperatureCelsius = 24, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for address > 15.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Gree encode rejects temperature outside 16 to 30`() {
        try {
            IrWaveform.encodeGree(
                address = 0x01, powerOn = true,
                temperatureCelsius = 15, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature < 16.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
        try {
            IrWaveform.encodeGree(
                address = 0x01, powerOn = true,
                temperatureCelsius = 31, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature > 30.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Gree encode different modes produce different waveforms`() {
        val a = IrWaveform.encodeGree(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 0
        )
        val b = IrWaveform.encodeGree(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 4, fanSpeed = 0
        )
        assertNotEquals(a.pattern.toList(), b.pattern.toList())
    }

    // === MIDEA ENCODE TESTS ==============================

    @Test
    fun `Midea encode produces a waveform at 38 kHz`() {
        val w = IrWaveform.encodeMidea(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 2
        )
        assertEquals(38_000, w.carrierHz)
        // Header (2) + 24 payload bits (48) + 24 inverted payload bits (48) + trailing (2) = 100
        assertEquals(100, w.pattern.size)
    }

    @Test
    fun `Midea encode rejects temperature outside 17 to 30`() {
        try {
            IrWaveform.encodeMidea(
                address = 0x01, powerOn = true,
                temperatureCelsius = 16, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature < 17.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
        try {
            IrWaveform.encodeMidea(
                address = 0x01, powerOn = true,
                temperatureCelsius = 31, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature > 30.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Midea encode rejects mode outside 0 to 4`() {
        try {
            IrWaveform.encodeMidea(
                address = 0x01, powerOn = true,
                temperatureCelsius = 24, mode = 5, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for mode > 4.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    // === MITSUBISHI ENCODE TESTS ==========================

    @Test
    fun `Mitsubishi encode produces a waveform at 38 kHz`() {
        val w = IrWaveform.encodeMitsubishi(
            address = 0x12, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 2
        )
        assertEquals(38_000, w.carrierHz)
        // Header (2) + 4 bytes (64) + trailing (2) = 68
        assertEquals(68, w.pattern.size)
    }

    @Test
    fun `Mitsubishi encode rejects address above 255`() {
        try {
            IrWaveform.encodeMitsubishi(
                address = 0x100, powerOn = true,
                temperatureCelsius = 24, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for address > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Mitsubishi encode rejects temperature outside 16 to 31`() {
        try {
            IrWaveform.encodeMitsubishi(
                address = 0x01, powerOn = true,
                temperatureCelsius = 15, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature < 16.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
        try {
            IrWaveform.encodeMitsubishi(
                address = 0x01, powerOn = true,
                temperatureCelsius = 32, mode = 1, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for temperature > 31.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Mitsubishi encode rejects mode outside 0 to 4`() {
        try {
            IrWaveform.encodeMitsubishi(
                address = 0x01, powerOn = true,
                temperatureCelsius = 24, mode = 5, fanSpeed = 0
            )
            fail("Expected IllegalArgumentException for mode > 4.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Mitsubishi encode rejects fanSpeed outside 0 to 4`() {
        try {
            IrWaveform.encodeMitsubishi(
                address = 0x01, powerOn = true,
                temperatureCelsius = 24, mode = 1, fanSpeed = 5
            )
            fail("Expected IllegalArgumentException for fanSpeed > 4.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Mitsubishi header is 3400 mark, 1700 space`() {
        val w = IrWaveform.encodeMitsubishi(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 0
        )
        assertEquals(3400, w.pattern[0])
        assertEquals(1700, w.pattern[1])
    }

    @Test
    fun `all AC encoders produce different waveforms`() {
        val daikin = IrWaveform.encodeDaikin(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 0
        )
        val gree = IrWaveform.encodeGree(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 0
        )
        val midea = IrWaveform.encodeMidea(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 0
        )
        val mitsubishi = IrWaveform.encodeMitsubishi(
            address = 0x01, powerOn = true,
            temperatureCelsius = 24, mode = 1, fanSpeed = 0
        )
        // All four are different protocols
        assertNotEquals(daikin.pattern.toList(), gree.pattern.toList())
        assertNotEquals(gree.pattern.toList(), midea.pattern.toList())
        assertNotEquals(midea.pattern.toList(), mitsubishi.pattern.toList())
        assertNotEquals(daikin.pattern.toList(), mitsubishi.pattern.toList())
    }

    // ── Kaseikyo (Panasonic) encoder tests ──────

    @Test
    fun `Kaseikyo header is 3456 mark, 1728 space`() {
        val w = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        assertEquals(3456, w.pattern[0])
        assertEquals(1728, w.pattern[1])
    }

    @Test
    fun `Kaseikyo carrier is 38 kHz`() {
        val w = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        assertEquals(38_000, w.carrierHz)
    }

    @Test
    fun `Kaseikyo frame is 50 entries (header 2 + 48 data bits + trailing 2)`() {
        val w = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        // 2 (header) + 48 bits * 2 (mark+space each) + 2 (trailing) = 100
        assertEquals(100, w.pattern.size)
    }

    @Test
    fun `Kaseikyo different addresses produce different waveforms`() {
        val w1 = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        val w2 = IrWaveform.encodeKaseikyo(address = 0x04, command = 0x01)
        assertNotEquals(w1.pattern.toList(), w2.pattern.toList())
    }

    @Test
    fun `Kaseikyo different commands produce different waveforms`() {
        val w1 = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        val w2 = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x02)
        assertNotEquals(w1.pattern.toList(), w2.pattern.toList())
    }

    @Test
    fun `Kaseikyo encode rejects address outside 0 to 255`() {
        try {
            IrWaveform.encodeKaseikyo(address = 256, command = 0x01)
            fail("Expected IllegalArgumentException for address > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Kaseikyo encode rejects command outside 0 to 255`() {
        try {
            IrWaveform.encodeKaseikyo(address = 0x40, command = 256)
            fail("Expected IllegalArgumentException for command > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Kaseikyo power command for Panasonic TV`() {
        val w = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        // Verify it's a valid waveform
        assertTrue(w.pattern.isNotEmpty())
        assertEquals(38_000, w.carrierHz)
    }

    // ── RC6 encoder tests ──────────────────────

    @Test
    fun `RC6 carrier is 36 kHz`() {
        val w = IrWaveform.encodeRc6(address = 0x00, command = 0x0C)
        assertEquals(36_000, w.carrierHz)
    }

    @Test
    fun `RC6 different addresses produce different waveforms`() {
        val w1 = IrWaveform.encodeRc6(address = 0x00, command = 0x0C)
        val w2 = IrWaveform.encodeRc6(address = 0x04, command = 0x0C)
        assertNotEquals(w1.pattern.toList(), w2.pattern.toList())
    }

    @Test
    fun `RC6 different commands produce different waveforms`() {
        val w1 = IrWaveform.encodeRc6(address = 0x00, command = 0x0C)
        val w2 = IrWaveform.encodeRc6(address = 0x00, command = 0x0D)
        assertNotEquals(w1.pattern.toList(), w2.pattern.toList())
    }

    @Test
    fun `RC6 toggle bit changes waveform`() {
        val w1 = IrWaveform.encodeRc6(address = 0x00, command = 0x0C, toggle = 0)
        val w2 = IrWaveform.encodeRc6(address = 0x00, command = 0x0C, toggle = 1)
        assertNotEquals(w1.pattern.toList(), w2.pattern.toList())
    }

    @Test
    fun `RC6 encode rejects address outside 0 to 15`() {
        try {
            IrWaveform.encodeRc6(address = 16, command = 0x0C)
            fail("Expected IllegalArgumentException for address > 15.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `RC6 encode rejects command outside 0 to 255`() {
        try {
            IrWaveform.encodeRc6(address = 0x00, command = 256)
            fail("Expected IllegalArgumentException for command > 255.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `RC6 encode rejects toggle outside 0 to 1`() {
        try {
            IrWaveform.encodeRc6(address = 0x00, command = 0x0C, toggle = 2)
            fail("Expected IllegalArgumentException for toggle > 1.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `RC6 pattern starts with leader mark`() {
        val w = IrWaveform.encodeRc6(address = 0x00, command = 0x0C)
        // Leader should be double-wide mark (1778 µs)
        assertEquals(1778, w.pattern[0])
    }

    // ── Cross-protocol uniqueness tests ────────

    @Test
    fun `all TV encoders produce unique waveforms for same address and command`() {
        val nec = IrWaveform.encodeNec(address = 0x04, command = 0x08)
        val samsung = IrWaveform.encodeSamsung(address = 0x04, command = 0x08)
        val rc5 = IrWaveform.encodeRc5(address = 0x04, command = 0x08)
        val rc6 = IrWaveform.encodeRc6(address = 0x04, command = 0x08)
        val sirc = IrWaveform.encodeSonySirc(address = 0x04, command = 0x08)
        val kaseikyo = IrWaveform.encodeKaseikyo(address = 0x04, command = 0x08)
        // All six are different protocols
        assertNotEquals(nec.pattern.toList(), samsung.pattern.toList())
        assertNotEquals(samsung.pattern.toList(), rc5.pattern.toList())
        assertNotEquals(rc5.pattern.toList(), rc6.pattern.toList())
        assertNotEquals(rc6.pattern.toList(), sirc.pattern.toList())
        assertNotEquals(sirc.pattern.toList(), kaseikyo.pattern.toList())
        assertNotEquals(nec.pattern.toList(), kaseikyo.pattern.toList())
    }
}
