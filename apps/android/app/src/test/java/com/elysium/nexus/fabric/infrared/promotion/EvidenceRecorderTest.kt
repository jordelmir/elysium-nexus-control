package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EvidenceRecorderTest {

    @Test
    fun `recordRealDevice produces explicit status`() {
        val evidence = EvidenceRecorder.recordRealDevice(
            id = "ev-1",
            deviceModelId = "mod-1",
            actionKey = "VOLUME_UP",
            signalId = "sig-1",
            physicalSha256 = "sha1",
            measuredCarrierHz = 38000,
            transmitterHardware = "NexusBridge",
            receiverHardware = "HIL-Station-1"
        )
        assertEquals(PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED, evidence.status)
    }

    @Test
    fun `recordRuntime produces RUNTIME_EXECUTABLE status`() {
        val evidence = EvidenceRecorder.recordRuntime(
            id = "ev-2",
            deviceModelId = "mod-1",
            actionKey = "VOLUME_UP",
            signalId = "sig-1",
            physicalSha256 = "sha1",
            measuredCarrierHz = 38000,
            transmitterHardware = "Phone",
            receiverHardware = "None"
        )
        assertEquals(PhysicalEvidenceStatus.RUNTIME_EXECUTABLE, evidence.status)
    }

    @Test
    fun `recorder rejects blank identity fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            EvidenceRecorder.recordRealDevice(
                id = "",
                deviceModelId = "mod-1",
                actionKey = "VOLUME_UP",
                signalId = "sig-1",
                physicalSha256 = "sha1",
                measuredCarrierHz = 38000,
                transmitterHardware = "X",
                receiverHardware = "Y"
            )
        }
    }

    @Test
    fun `failure recorder rejects passing statuses`() {
        assertThrows(IllegalArgumentException::class.java) {
            EvidenceRecorder.recordFailure(
                id = "ev-3",
                deviceModelId = "mod-1",
                actionKey = "VOLUME_UP",
                signalId = "sig-1",
                physicalSha256 = "sha1",
                measuredCarrierHz = 38000,
                transmitterHardware = "X",
                receiverHardware = "Y",
                status = PhysicalEvidenceStatus.HIL_VERIFIED
            )
        }
    }

    @Test
    fun `promoteToHil requires complete dual-path artifacts`() {
        val realEvidence = EvidenceRecorder.recordRealDevice(
            id = "ev-1",
            deviceModelId = "mod-1",
            actionKey = "VOLUME_UP",
            signalId = "sig-1",
            physicalSha256 = "sha1",
            measuredCarrierHz = 38000,
            transmitterHardware = "NexusBridge",
            receiverHardware = "TargetTV"
        )
        assertNull(EvidencePromotionService.promoteToHil(realEvidence, HilArtifacts(rawCaptureRef = "", independentDecoderRef = "dec-1")))
        assertNull(EvidencePromotionService.promoteToHil(realEvidence, HilArtifacts(rawCaptureRef = "cap-1", independentDecoderRef = "")))

        val promoted = EvidencePromotionService.promoteToHil(
            realEvidence,
            HilArtifacts(rawCaptureRef = "cap-1", independentDecoderRef = "dec-1")
        )
        assertEquals(PhysicalEvidenceStatus.HIL_VERIFIED, promoted!!.status)
    }

    @Test
    fun `promoteToHil refuses failing evidence`() {
        val failing = EvidenceRecorder.recordFailure(
            id = "ev-4",
            deviceModelId = "mod-1",
            actionKey = "VOLUME_UP",
            signalId = "sig-1",
            physicalSha256 = "sha1",
            measuredCarrierHz = 38000,
            transmitterHardware = "X",
            receiverHardware = "Y",
            status = PhysicalEvidenceStatus.FAILED
        )
        assertNull(EvidencePromotionService.promoteToHil(failing, HilArtifacts("cap-1", "dec-1")))
    }
}