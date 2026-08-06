package com.elysium.nexus.core.device

import java.util.UUID

/**
 * IrCommandBinding associates a semantic [IrAction] with a physical signal ID,
 * SHA-256 fingerprint, source identifier, and verification status.
 */
data class IrCommandBinding(
    val signalId: String,
    val physicalFingerprint: String,
    val sourceId: String,
    val action: IrAction
)

/**
 * §9 InstalledIrProfile — The Authoritative Persistent IR Remote Profile.
 *
 * Created when candidate probing succeeds. Stores the winning codeSetId,
 * source revisions, verified actions, and full command bindings map.
 * UI controls (TvControlScreen) drive signals strictly from this profile.
 * DeviceTemplate defines visual layout ONLY and never determines physical codes.
 */
data class InstalledIrProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val brand: String,
    val deviceType: String = "TV",
    val model: String? = null,
    val remoteModel: String? = null,
    val codeSetId: String,
    val sourceRevision: String = "v0.3.0",
    val commands: Map<IrAction, IrCommandBinding>,
    val verifiedActions: Set<IrAction> = emptySet(),
    val verificationStatus: VerificationStatus = VerificationStatus.PARTIALLY_VERIFIED,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
