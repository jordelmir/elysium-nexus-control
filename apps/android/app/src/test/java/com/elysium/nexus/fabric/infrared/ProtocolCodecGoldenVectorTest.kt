package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecGoldenVectorTest {

    @Test
    fun necGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeNec(address = 0x00, command = 0x07)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(9000, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun necExtendedGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeNecExtended(address = 0x0400, command = 0x07)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(9000, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun samsungGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeSamsung(address = 0x07, command = 0x02)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(4500, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun sonySircGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeSonySirc(address = 0x01, command = 0x12)
        assertEquals(40000, waveform.carrierHz)
        assertEquals(2400, waveform.pattern[0])
        assertEquals(600, waveform.pattern[1])
    }

    @Test
    fun rc5DifferentCommands_produceDifferentWaveforms() {
        val volumeUp = IrWaveform.encodeRc5(address = 0, command = 16, toggle = 0)
        val volumeDown = IrWaveform.encodeRc5(address = 0, command = 17, toggle = 0)

        assertFalse(
            "Different RC5 commands MUST produce different physical waveforms!",
            volumeUp.pattern.contentEquals(volumeDown.pattern)
        )
    }

    @Test
    fun protocolCodecRegistry_verifiesCodecsCorrectly() {
        val necSpec = ProtocolCodecRegistry.getCodec("NEC")
        assertNotNull(necSpec)
        // Honest state: unit-shape validated until decoder round-trip + HIL proof exist (dictamen P1).
        assertEquals(CodecVerificationStatus.UNIT_SHAPE_VALIDATED, necSpec?.status)
        assertTrue(ProtocolCodecRegistry.isCodecTransmittable("NEC"))
        assertTrue(ProtocolCodecRegistry.isCodecTransmittable("RC5"))
    }
}
