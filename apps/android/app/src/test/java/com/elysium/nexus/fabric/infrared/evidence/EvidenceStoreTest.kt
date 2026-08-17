package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.promotion.EvidenceRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvidenceStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun runtimeEvidence(id: String, deviceModelId: String = "mod-test-1"): PhysicalTestEvidence =
        EvidenceRecorder.recordRuntime(
            id = id,
            deviceModelId = deviceModelId,
            actionKey = "POWER_TOGGLE",
            signalId = "sig-1",
            physicalSha256 = "a".repeat(64),
            measuredCarrierHz = 38000,
            transmitterHardware = "elem-bridge-test",
            receiverHardware = "elem-bridge-test"
        )

    @Test
    fun `append is append-only and durable across reload`() {
        val file = tmp.newFile("evidence.jsonl")
        val store = JsonLineEvidenceStore(file)
        val r1 = store.append(runtimeEvidence("ev-1"))
        val r2 = store.append(runtimeEvidence("ev-2"))
        assertEquals(1L, r1.seq)
        assertEquals(2L, r2.seq)

        val reloaded = JsonLineEvidenceStore(file)
        assertEquals(
            listOf("ev-1", "ev-2"),
            reloaded.all().map { it.id }
        )
        assertTrue(reloaded.contains("ev-1"))
    }

    @Test
    fun `append rejects duplicate ids`() {
        val store = JsonLineEvidenceStore(tmp.newFile("evidence.jsonl"))
        store.append(runtimeEvidence("ev-1"))
        assertThrows(IllegalArgumentException::class.java) {
            store.append(runtimeEvidence("ev-1"))
        }
    }

    @Test
    fun `supersede preserves history via tombstone`() {
        val store = JsonLineEvidenceStore(tmp.newFile("evidence.jsonl"))
        store.append(runtimeEvidence("ev-1"))
        val result = store.supersede("ev-1", runtimeEvidence("ev-1-rev2"))

        assertEquals(2L, result.seq)
        assertEquals(1L, result.supersedesSeq)
        assertEquals(listOf("ev-1", "ev-1-rev2"), store.all().map { it.id })
        assertEquals(mapOf("ev-1" to "ev-1-rev2"), store.tombstoneLinks())

        val reloaded = JsonLineEvidenceStore(tmp.newFile("evidence-reloaded.jsonl"))
        reloaded.append(runtimeEvidence("ev-a"))
        reloaded.supersede("ev-a", runtimeEvidence("ev-a-v2"))
        assertEquals(mapOf("ev-a" to "ev-a-v2"), reloaded.tombstoneLinks())
    }

    @Test
    fun `supersede rejects unknown or duplicate targets`() {
        val store = JsonLineEvidenceStore(tmp.newFile("evidence.jsonl"))
        assertThrows(IllegalArgumentException::class.java) {
            store.supersede("ghost", runtimeEvidence("ev-new"))
        }
        store.append(runtimeEvidence("ev-1"))
        assertThrows(IllegalArgumentException::class.java) {
            store.supersede("ev-1", runtimeEvidence("ev-1"))
        }
    }

    @Test
    fun `corrupted or rewritten store file fails closed on load`() {
        val file = tmp.newFile("evidence.jsonl")
        file.writeText(
            "{\"seq\":2,\"supersedesSeq\":null,\"id\":\"ev-x\",\"deviceModelId\":\"m\",\"actionKey\":\"A\"," +
                "\"signalId\":\"s\",\"physicalSha256\":\"${"a".repeat(64)}\",\"measuredCarrierHz\":38000," +
                "\"transmitterHardware\":\"t\",\"receiverHardware\":\"r\",\"verifiedAtTimestamp\":1," +
                "\"status\":\"RUNTIME_EXECUTABLE\"}\n"
        )
        // seq starts at 2 — contiguity demands 1
        assertThrows(IllegalStateException::class.java) {
            JsonLineEvidenceStore(file)
        }
    }

    @Test
    fun `status round-trips without default`() {
        val file = tmp.newFile("evidence.jsonl")
        val store = JsonLineEvidenceStore(file)
        val fail = EvidenceRecorder.recordFailure(
            id = "ev-f",
            deviceModelId = "mod-test-1",
            actionKey = "MUTE",
            signalId = "sig-1",
            physicalSha256 = "b".repeat(64),
            measuredCarrierHz = 38000,
            transmitterHardware = "t",
            receiverHardware = "r",
            status = PhysicalEvidenceStatus.REGRESSION
        )
        store.append(fail)
        assertEquals(PhysicalEvidenceStatus.REGRESSION, JsonLineEvidenceStore(file).all().first().status)
        assertFalse(store.all().any { it.status.isPass })
    }
}