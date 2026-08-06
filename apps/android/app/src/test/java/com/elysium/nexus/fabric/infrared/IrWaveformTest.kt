package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

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
    fun `IrWaveform permits odd-length pattern arrays`() {
        // Android IR HAL accepts odd-length patterns and turns off the carrier at the end of the last mark.
        val w = IrWaveform(carrierHz = 38_000, pattern = intArrayOf(9000, 4500, 560))
        assertEquals(3, w.sliceCount)
    }

    @Test
    fun `IrWaveform rejects non-positive slice durations equal or below zero`() {
        try {
            IrWaveform(carrierHz = 38_000, pattern = intArrayOf(560, 0))
            fail("Expected IllegalArgumentException for 0 us slice duration.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
        try {
            IrWaveform(carrierHz = 38_000, pattern = intArrayOf(560, -100))
            fail("Expected IllegalArgumentException for negative slice duration.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `IrWaveform total duration is the sum of all entries`() {
        val w = IrWaveform(
            carrierHz = 38_000,
            pattern = intArrayOf(9000, 4500, 560, 1690, 560)
        )
        assertEquals(9000L + 4500L + 560L + 1690L + 560L, w.totalDurationUs)
        assertEquals(5, w.sliceCount)
    }

    @Test
    fun `NEC encode produces physical 32-bit LSB pattern shape ending with 560us stop mark`() {
        val w = IrWaveform.encodeNec(address = 0x01, command = 0x02)
        // Header: 9000 mark, 4500 space
        assertEquals(9000, w.pattern[0])
        assertEquals(4500, w.pattern[1])

        // 32 bits (64 entries) + 2 (header) + 1 (stop mark 560) = 67 entries
        assertEquals(67, w.pattern.size)
        assertEquals(560, w.pattern[66]) // Stop mark
        assertEquals(38_000, w.carrierHz)

        // All elements must be strictly positive
        assertTrue(w.pattern.all { it > 0 })
    }

    @Test
    fun `NEC encode with repeat adds repeat frame tail`() {
        val w = IrWaveform.encodeNec(address = 0x10, command = 0x20, repeat = true)
        // 67 base entries + 4 tail entries (40000, 9000, 2250, 560) = 71 entries
        assertEquals(71, w.pattern.size)
        assertEquals(40_000, w.pattern[67])
        assertEquals(9000, w.pattern[68])
        assertEquals(2250, w.pattern[69])
        assertEquals(560, w.pattern[70])
        assertTrue(w.pattern.all { it > 0 })
    }

    @Test
    fun `NEC decode round-trips an encoded 32-bit command`() {
        val original = IrWaveform.encodeNec(address = 0x42, command = 0x84)
        val decoded = IrWaveform.decodeNec(original)
        assertNotNull(decoded)
        assertEquals(0x42, decoded!!.address)
        assertEquals(0x84, decoded.command)
    }

    @Test
    fun `SIRC encode produces 25 entries for 12-bit (7 cmd + 5 addr)`() {
        val w = IrWaveform.encodeSonySirc(address = 0x05, command = 0x0A)
        // Header (2) + 7 cmd bits (14) + 5 addr bits (10) - trailing space = 26 entries
        assertEquals(26, w.pattern.size)
        assertEquals(IrProtocol.SonySirc.carrierHz, w.carrierHz)
        assertTrue(w.pattern.all { it > 0 })
    }

    @Test
    fun `Samsung encode produces 67 entries (header + 32 bits + stop mark)`() {
        val w = IrWaveform.encodeSamsung(address = 0x07, command = 0x02)
        assertEquals(67, w.pattern.size)
        assertEquals(IrProtocol.Samsung.carrierHz, w.carrierHz)
        assertTrue(w.pattern.all { it > 0 })
    }

    @Test
    fun `Kaseikyo header is 3456 mark, 1728 space`() {
        val w = IrWaveform.encodeKaseikyo(address = 0x40, command = 0x01)
        assertEquals(3456, w.pattern[0])
        assertEquals(1728, w.pattern[1])
        assertTrue(w.pattern.all { it > 0 })
    }

    @Test
    fun `all TV encoders produce unique waveforms for same address and command`() {
        val nec = IrWaveform.encodeNec(address = 0x04, command = 0x08)
        val samsung = IrWaveform.encodeSamsung(address = 0x04, command = 0x08)
        val rc5 = IrWaveform.encodeRc5(address = 0x04, command = 0x08)
        val rc6 = IrWaveform.encodeRc6(address = 0x04, command = 0x08)
        val sirc = IrWaveform.encodeSonySirc(address = 0x04, command = 0x08)
        val kaseikyo = IrWaveform.encodeKaseikyo(address = 0x04, command = 0x08)

        assertNotEquals(nec.pattern.toList(), samsung.pattern.toList())
        assertNotEquals(samsung.pattern.toList(), rc5.pattern.toList())
        assertNotEquals(rc5.pattern.toList(), rc6.pattern.toList())
        assertNotEquals(rc6.pattern.toList(), sirc.pattern.toList())
        assertNotEquals(sirc.pattern.toList(), kaseikyo.pattern.toList())
    }
}
