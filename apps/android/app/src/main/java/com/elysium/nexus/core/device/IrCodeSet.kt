package com.elysium.nexus.core.device

/**
 * Compatibility verification level for IR code sets per AGENTS.md Hard Rule #5.
 */
enum class VerificationStatus {
    IMPORTED_UNREVIEWED,
    STRUCTURALLY_VALID,
    PROTOCOL_VALIDATED,
    UNVERIFIED,
    PARTIALLY_VERIFIED,
    VERIFIED_COMMUNITY,
    VERIFIED_LAB,
    REGRESSION,
    BLOCKED
}

/**
 * Data provenance record tracking dataset origin and licensing compliance per ADR-002.
 */
data class CodeProvenance(
    val sourceName: String,
    val sourceUrl: String,
    val licenseSpdx: String,
    val commitSha: String? = null,
    val attributionText: String? = null
)

/**
 * Canonical IR CodeSet definition mapping semantic [IrAction]s to physical [IrSignal]s.
 */
data class IrCodeSet(
    val id: String,
    val brand: String,
    val modelPatterns: Set<String>,
    val remoteModels: Set<String>,
    val commands: Map<IrAction, IrSignal>,
    val provenance: CodeProvenance,
    val verification: VerificationStatus = VerificationStatus.UNVERIFIED
)
