package com.elysium.nexus.fabric.infrared.promotion

import com.elysium.nexus.fabric.infrared.database.model.PhysicalEvidenceStatus
import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence

/**
 * Master Order v0.10 Phase 1/2 — explicit evidence creation.
 *
 * Evidence NEVER materializes with an implied strong status. [PhysicalTestEvidence]
 * has no default status; every row is created through the recorder of the
 * appropriate authority level. `HIL_VERIFIED` can only be reached through
 * [EvidencePromotionService.promoteToHil] with the required dual-path artifacts.
 */
object EvidenceRecorder {

    private fun validate(
        id: String,
        deviceModelId: String,
        actionKey: String,
        signalId: String,
        physicalSha256: String,
        measuredCarrierHz: Int
    ) {
        require(id.isNotBlank()) { "evidence id must not be blank" }
        require(deviceModelId.isNotBlank()) { "deviceModelId must not be blank" }
        require(actionKey.isNotBlank()) { "actionKey must not be blank" }
        require(signalId.isNotBlank()) { "signalId must not be blank" }
        require(physicalSha256.isNotBlank()) { "physicalSha256 must not be blank" }
        require(measuredCarrierHz > 0) { "measuredCarrierHz must be positive" }
    }

    /** Signal executes from catalog; no physical reaction observed yet. */
    fun recordRuntime(
        id: String,
        deviceModelId: String,
        actionKey: String,
        signalId: String,
        physicalSha256: String,
        measuredCarrierHz: Int,
        transmitterHardware: String,
        receiverHardware: String
    ): PhysicalTestEvidence {
        validate(id, deviceModelId, actionKey, signalId, physicalSha256, measuredCarrierHz)
        return PhysicalTestEvidence(
            id = id,
            deviceModelId = deviceModelId,
            actionKey = actionKey,
            signalId = signalId,
            physicalSha256 = physicalSha256,
            measuredCarrierHz = measuredCarrierHz,
            transmitterHardware = transmitterHardware,
            receiverHardware = receiverHardware,
            status = PhysicalEvidenceStatus.RUNTIME_EXECUTABLE
        )
    }

    /** Carrier emitted cleanly from the physical host (TX_OK). */
    fun recordOnDeviceTransmitted(
        id: String,
        deviceModelId: String,
        actionKey: String,
        signalId: String,
        physicalSha256: String,
        measuredCarrierHz: Int,
        transmitterHardware: String,
        receiverHardware: String
    ): PhysicalTestEvidence {
        validate(id, deviceModelId, actionKey, signalId, physicalSha256, measuredCarrierHz)
        return PhysicalTestEvidence(
            id = id,
            deviceModelId = deviceModelId,
            actionKey = actionKey,
            signalId = signalId,
            physicalSha256 = physicalSha256,
            measuredCarrierHz = measuredCarrierHz,
            transmitterHardware = transmitterHardware,
            receiverHardware = receiverHardware,
            status = PhysicalEvidenceStatus.ON_DEVICE_TRANSMITTED
        )
    }

    /** Physical reaction observed directly on the target device. */
    fun recordRealDevice(
        id: String,
        deviceModelId: String,
        actionKey: String,
        signalId: String,
        physicalSha256: String,
        measuredCarrierHz: Int,
        transmitterHardware: String,
        receiverHardware: String
    ): PhysicalTestEvidence {
        validate(id, deviceModelId, actionKey, signalId, physicalSha256, measuredCarrierHz)
        return PhysicalTestEvidence(
            id = id,
            deviceModelId = deviceModelId,
            actionKey = actionKey,
            signalId = signalId,
            physicalSha256 = physicalSha256,
            measuredCarrierHz = measuredCarrierHz,
            transmitterHardware = transmitterHardware,
            receiverHardware = receiverHardware,
            status = PhysicalEvidenceStatus.REAL_DEVICE_OBSERVED
        )
    }

    /** Records a failure or regression observed under test. */
    fun recordFailure(
        id: String,
        deviceModelId: String,
        actionKey: String,
        signalId: String,
        physicalSha256: String,
        measuredCarrierHz: Int,
        transmitterHardware: String,
        receiverHardware: String,
        status: PhysicalEvidenceStatus
    ): PhysicalTestEvidence {
        require(status.isFailure) { "failure recorder only accepts REGRESSION or FAILED, got $status" }
        validate(id, deviceModelId, actionKey, signalId, physicalSha256, measuredCarrierHz)
        return PhysicalTestEvidence(
            id = id,
            deviceModelId = deviceModelId,
            actionKey = actionKey,
            signalId = signalId,
            physicalSha256 = physicalSha256,
            measuredCarrierHz = measuredCarrierHz,
            transmitterHardware = transmitterHardware,
            receiverHardware = receiverHardware,
            status = status
        )
    }
}

/** Dual-path hardware-in-the-loop artifacts required before any HIL promotion. */
data class HilArtifacts(
    val rawCaptureRef: String,
    val independentDecoderRef: String
) {
    fun isComplete(): Boolean = rawCaptureRef.isNotBlank() && independentDecoderRef.isNotBlank()
}

/**
 * Master Order v0.10 Phase 6 — promotion is derived, never written.
 *
 * [promoteToHil] is the ONLY path to `HIL_VERIFIED` and it fails closed unless
 * the required dual-path artifacts are present.
 */
object EvidencePromotionService {

    fun promoteToHil(evidence: PhysicalTestEvidence, artifacts: HilArtifacts): PhysicalTestEvidence? {
        if (evidence.status.isFailure) return null
        if (!artifacts.isComplete()) return null
        return evidence.copy(status = PhysicalEvidenceStatus.HIL_VERIFIED)
    }
}