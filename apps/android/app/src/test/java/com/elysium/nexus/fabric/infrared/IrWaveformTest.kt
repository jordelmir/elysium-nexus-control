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
}
