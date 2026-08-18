package com.elysium.nexus.fabric.infrared.database.model

/**
 * Phase 13 — Device Model Graph & Phase 14 — Retail Data Model
 *
 * Provides authoritative models for exact device identity (MPN), retailer SKU links
 * (Monge, Gollo, Verdugo), physical test evidence, and signed compatibility certificates.
 *
 * Master Order v0.10 (TRUTH CONVERGENCE): NO DEFAULT STATUS ANYWHERE. Evidence level is a
 * typed, mandatory field. Claim promotion is derived, never written.
 */

data class DeviceModel(
    val id: String,
    val brandName: String,
    val modelName: String,
    val exactMpn: String,
    val deviceType: String = "TV",
    val hardwareFamily: String? = null,
    val originalRemoteModel: String? = null
)

enum class RetailerName {
    MONGE_CR,
    GOLLO_CR,
    VERDUGO_CR
}

data class RetailerSku(
    val id: String,
    val retailer: RetailerName,
    val skuCode: String,
    val gtin: String? = null,
    val mpn: String,
    val deviceModelId: String?,
    val isActive: Boolean = true,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Typed physical evidence statuses (Master Order v0.10 Phase 1).
 *
 * NO DEFAULT EXISTS. Every [PhysicalTestEvidence] row must declare its status
 * explicitly; evidence creation goes through [com.elysium.nexus.fabric.infrared.promotion.EvidenceRecorder]
 * from authorized paths only.
 */
enum class PhysicalEvidenceStatus {
    /** Signal executes from the catalog on the target runtime (no physical reaction observed yet). */
    RUNTIME_EXECUTABLE,

    /** Carrier was emitted cleanly from the physical host (e.g. ConsumerIrManager TX_OK). */
    ON_DEVICE_TRANSMITTED,

    /** Physical reaction observed directly on the target device. */
    REAL_DEVICE_OBSERVED,

    /** Independent decoder validated the waveform (raw capture + reference decode). */
    INDEPENDENT_DECODE_VERIFIED,

    /** Hardware-in-the-loop dual-path lab verification passed with artifacts. */
    HIL_VERIFIED,

    /** The action regressed: a previously passing test failed on re-run. */
    REGRESSION,

    /** The action failed under test. */
    FAILED;

    val isPass: Boolean
        get() = this == RUNTIME_EXECUTABLE || this == ON_DEVICE_TRANSMITTED ||
            this == REAL_DEVICE_OBSERVED || this == INDEPENDENT_DECODE_VERIFIED ||
            this == HIL_VERIFIED

    val isFailure: Boolean
        get() = this == REGRESSION || this == FAILED
}

data class PhysicalTestEvidence(
    val id: String,
    val deviceModelId: String,
    val actionKey: String,
    val signalId: String,
    val physicalSha256: String,
    val measuredCarrierHz: Int,
    val transmitterHardware: String,
    val receiverHardware: String,
    val verifiedAtTimestamp: Long = System.currentTimeMillis(),
    val status: PhysicalEvidenceStatus
)

data class RetailCompatibilityCertificate(
    val certificateId: String,
    val retailer: RetailerName,
    val skuCode: String,
    val exactMpn: String,
    val deviceModelId: String,
    val coreActionsVerified: Set<String>,
    val extendedActionsVerified: Set<String> = emptySet(),
    val physicalEvidenceShaList: List<String>,
    val evidenceIds: List<String>,
    val schemaVersion: Int,
    val policyVersion: String,
    val appCommit: String,
    val catalogBuildId: String,
    val verifiedAtTimestamp: Long,
    val validFromTimestamp: Long,
    val validUntilTimestamp: Long,
    val keyId: String,
    val signatureAlgorithm: String,
    val digitalSignature: String
)