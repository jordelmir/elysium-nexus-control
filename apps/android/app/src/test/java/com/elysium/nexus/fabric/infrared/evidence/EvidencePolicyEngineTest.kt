package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.promotion.CoreActionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidencePolicyEngineTest {

    private fun policy(): EvidencePolicyEngine {
        val json = javaClass.classLoader
            .getResourceAsStream("policy/retail-core-policy-v1.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("policy test resource missing")
        return EvidencePolicyEngine.parse(json)
    }

    @Test
    fun `policy parses and matches in-app constants`() {
        val engine = policy()
        assertEquals("retail-core-policy-v1", engine.policyVersion)
        assertEquals(CoreActionPolicy.TV_CORE_ACTIONS, engine.coreActionsFor("TV"))
        assertEquals(CoreActionPolicy.TV_CORE_ACTIONS, engine.coreActionsFor("tv"))
        assertEquals(
            listOf(
                "SOURCE_IMPORTED", "STRUCTURAL_VALID", "RUNTIME_EXECUTABLE",
                "OPTICAL_TX_VERIFIED", "INDEPENDENT_DECODE_VERIFIED",
                "REAL_DEVICE_VERIFIED", "HIL_VERIFIED", "RETAIL_MATRIX_VERIFIED"
            ),
            engine.claimLadder
        )
        assertTrue(engine.rule("noDefaultStatus"))
        assertTrue(engine.rule("regressionDominates"))
    }

    @Test
    fun `policy with divergent core actions fails closed`() {
        val json = javaClass.classLoader
            .getResourceAsStream("policy/retail-core-policy-v1.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("policy test resource missing")
        val tampered = json.replace("\"POWER_TOGGLE\"", "\"POWER_TOGGLE_EXTRA\"")
        assertThrows(IllegalArgumentException::class.java) {
            EvidencePolicyEngine.parse(tampered)
        }
    }

    @Test
    fun `policy with wrong version fails closed`() {
        val json = javaClass.classLoader
            .getResourceAsStream("policy/retail-core-policy-v1.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("policy test resource missing")
        val tampered = json.replace("\"retail-core-policy-v1\"", "\"retail-core-policy-v2\"")
        assertThrows(IllegalArgumentException::class.java) {
            EvidencePolicyEngine.parse(tampered)
        }
    }
}