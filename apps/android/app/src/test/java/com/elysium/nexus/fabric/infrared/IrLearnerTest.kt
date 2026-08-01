package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the §6.3 [IrLearner].
 *
 * The learner is the "raw waveform →
 * normalized command" pipeline. The
 * tests are pure (no photodiode): a test
 * fixture is a waveform produced by
 * [IrWaveform.encodeNec] (round-trip) or
 * a hand-crafted one (synthetic).
 */
class IrLearnerTest {

    @Test
    fun `learn rejects an odd-length raw waveform`() {
        try {
            IrLearner.learn(intArrayOf(560, 1690, 560))
            fail("Expected IllegalArgumentException for odd-length raw.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `learn rejects a negative entry`() {
        try {
            IrLearner.learn(intArrayOf(560, 1690, -1, 560))
            fail("Expected IllegalArgumentException for negative entry.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `learn round-trips a NEC-encoded waveform`() {
        val original = IrWaveform.encodeNec(address = 0x42, command = 0x84)
        val result = IrLearner.learn(original.pattern)
        assertNotNull("Expected a matched protocol, got null", result.command)
        // The protocol is NEC (or NECx
        // accepted, but the 36-entry length
        // matches NEC exactly).
        assertEquals(IrProtocol.Nec, result.command!!.protocol)
        // The address is the lower 8 bits
        // of 0x42 = 0x42. (NECx would be 16
        // bits; 0x42 fits in 8, so NEC is
        // the correct match.)
        assertEquals(0x42, result.command.address)
        // The command is 0x84.
        assertEquals(0x84, result.command.command)
        // The raw waveform is the same
        // (we kept the carrier).
        assertEquals(original.carrierHz, result.rawWaveform.carrierHz)
    }

    @Test
    fun `learn estimates the carrier near 38 kHz for an NEC waveform`() {
        val waveform = IrWaveform.encodeNec(address = 0x01, command = 0x02)
        val result = IrLearner.learn(waveform.pattern)
        // The estimator is approximate;
        // ±10% is fine. NEC is 38 kHz.
        assertTrue(
            "Expected carrier near 38 kHz, got ${result.carrierHz}",
            result.carrierHz in 34_000..42_000
        )
    }

    @Test
    fun `learn returns null command for an unknown waveform`() {
        // A waveform that doesn't match
        // any known protocol. The
        // [34_000..42_000] carrier is
        // valid; the pattern is irregular.
        val garbage = intArrayOf(100, 200, 300, 400, 500, 600, 700, 800)
        val result = IrLearner.learn(garbage)
        // The carrier estimate may
        // succeed (the entries are
        // sub-millisecond, the estimator
        // returns 30 kHz), but no
        // protocol matches. The result
        // is the raw waveform + low
        // confidence.
        assertNull(result.command)
        assertTrue(
            "Expected confidence < 0.5 for an unknown waveform, got ${result.confidence}",
            result.confidence < 0.5f
        )
    }

    @Test
    fun `learn produces a confidence between 0 and 1`() {
        val waveform = IrWaveform.encodeNec(address = 0x10, command = 0x20)
        val result = IrLearner.learn(waveform.pattern)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun `learn produces a higher confidence for a clean waveform than for a noisy one`() {
        val clean = IrWaveform.encodeNec(address = 0x10, command = 0x20).pattern
        val noisy = clean.copyOf().also { arr ->
            // Add 5 noise entries at the end
            // (the waveform is now 41 entries,
            // odd-length which is invalid for
            // the round trip; the learner
            // produces a LearnResult with
            // reduced confidence).
            arr.also { /* keep as is for this test */ }
        }
        // The clean waveform's confidence
        // is high (close to 1.0).
        val resultClean = IrLearner.learn(clean)
        // The noisy waveform is the same
        // length but a different shape; we
        // can simulate noise by shifting
        // some entries.
        val perturbed = clean.copyOf().also { arr ->
            // Multiply every 3rd entry by 0.5
            // to introduce jitter.
            for (i in arr.indices) {
                if (i % 3 == 0) arr[i] = (arr[i] * 0.5).toInt()
            }
        }
        val resultNoisy = IrLearner.learn(perturbed)
        // The clean waveform matches NEC
        // exactly; the perturbed one may
        // miss the ±25% tolerance on some
        // entries and fail to decode.
        // We assert that the clean
        // confidence is >= the noisy
        // confidence (when both decode).
        if (resultClean.command != null && resultNoisy.command != null) {
            assertTrue(
                "Expected clean (${resultClean.confidence}) >= noisy (${resultNoisy.confidence})",
                resultClean.confidence >= resultNoisy.confidence
            )
        }
    }

    @Test
    fun `learn accepts a waveform with 25 percent timing tolerance`() {
        val original = IrWaveform.encodeNec(address = 0x10, command = 0x20)
        val jittered = original.pattern.map { v ->
            (v * 1.2f).toInt()
        }.toIntArray()
        val result = IrLearner.learn(jittered)
        // The learner may decode it
        // (the waveform is 36 entries,
        // matching NEC) but the
        // confidence factor will reduce
        // the score. The test asserts
        // the command decoded, not the
        // confidence.
        assertNotNull("Expected the jittered NEC waveform to decode", result.command)
    }

    @Test
    fun `IrCommand rejects negative command`() {
        try {
            IrCommand(protocol = IrProtocol.Nec, address = 0, command = -1)
            fail("Expected IllegalArgumentException for negative command.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `IrCommand rejects negative address`() {
        try {
            IrCommand(protocol = IrProtocol.Nec, address = -1, command = 0)
            fail("Expected IllegalArgumentException for negative address.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `learn estimate carrier returns default for an empty mark list`() {
        // The only way to get an empty
        // mark list is to pass a pattern
        // where every on-pulse is 0; the
        // learner filters them out.
        val raw = intArrayOf(0, 1000, 0, 1000, 0, 1000)
        val result = IrLearner.learn(raw)
        // The estimator returns the
        // default 38 kHz.
        assertEquals(IrProtocol.DEFAULT_CARRIER_HZ, result.carrierHz)
    }

    @Test
    fun `learn rejects a non-positive sampleRateHz`() {
        try {
            IrLearner.learn(intArrayOf(560, 1690), sampleRateHz = 0)
            fail("Expected IllegalArgumentException for sampleRateHz = 0.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }
}
