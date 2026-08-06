package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProbeEngineTest {

    private fun mockCodeSet(id: String, address: Int, command: Int): IrCodeSet = IrCodeSet(
        id = id,
        brand = "Sankey",
        modelPatterns = setOf("Generic"),
        remoteModels = emptySet(),
        commands = mapOf(
            IrAction.VOLUME_UP to IrSignal.Encoded(
                carrierHz = 38_000,
                protocol = IrProtocol.Nec,
                address = address,
                command = command
            )
        ),
        provenance = CodeProvenance("Test", "http://test", "MIT"),
        verification = VerificationStatus.UNVERIFIED
    )

    @Test
    fun `IrProbeEngine filters candidate code sets with VOLUME_UP action`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)
        val csNoVol = IrCodeSet(
            id = "csNoVol",
            brand = "Sankey",
            modelPatterns = setOf("NoVol"),
            remoteModels = emptySet(),
            commands = mapOf(
                IrAction.POWER_TOGGLE to IrSignal.Encoded(
                    carrierHz = 38_000,
                    protocol = IrProtocol.Nec,
                    address = 0,
                    command = 2
                )
            ),
            provenance = CodeProvenance("Test", "http://test", "MIT"),
            verification = VerificationStatus.UNVERIFIED
        )

        val engine = IrProbeEngine(listOf(cs1, csNoVol, cs2))
        assertEquals(2, engine.totalCandidates)
        assertEquals("cs1", engine.currentCandidate()?.id)
    }

    @Test
    fun `IrProbeEngine deduplicates candidate code sets with identical signal fingerprint`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs1Dup = mockCodeSet("cs1_dup", 0x00, 0x07) // Same address & command
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs1Dup, cs2))
        assertEquals(2, engine.totalCandidates) // cs1Dup deduplicated out!
    }

    @Test
    fun `IrProbeEngine advances through candidates on nextCandidate()`() {
        val cs1 = mockCodeSet("cs1", 0x00, 0x07)
        val cs2 = mockCodeSet("cs2", 0x04, 0x07)

        val engine = IrProbeEngine(listOf(cs1, cs2))
        assertTrue(engine.hasMore)
        assertEquals(1, engine.currentProbeNumber)

        val first = engine.nextCandidate()
        assertEquals("cs1", first?.id)

        val second = engine.nextCandidate()
        assertEquals("cs2", second?.id)

        assertFalse(engine.hasMore)
        assertNull(engine.nextCandidate())
    }
}
