package com.elysium.nexus.fabric.infrared.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalTruthGateTest {

    @Test
    fun `module-level final truth gate holds`() {
        val failures = FinalTruthGate.failures()
        assertEquals(
            "Final Truth Gate failures: ${failures.joinToString("\n")}",
            emptyList<String>(),
            failures
        )
    }

    @Test
    fun `full 12-action core matrix is required for completeness`() {
        val policy = com.elysium.nexus.fabric.infrared.evidence.EvidencePolicyEngine.parse(
            javaClass.classLoader.getResourceAsStream("policy/retail-core-policy-v1.json")!!
                .bufferedReader().use { it.readText() }
        )
        assertTrue(policy.coreActionsFor("TV").size == 12)
    }
}