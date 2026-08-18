package com.elysium.nexus.fabric.infrared.legal

/**
 * Master Order v0.10 Phase 12 — Supply Chain Legal Evidence Ledger.
 *
 * Live legal status is a CONTROLLED machine with exactly five states:
 * UNREVIEWED -> REVIEW_REQUIRED -> DOCUMENTED -> SATISFIED  (or -> BLOCKED).
 *
 * - `UNREVIEWED`:   third-party material entered the repo, no legal review.
 * - `REVIEW_REQUIRED`: legal review explicitly requested (release blocker until resolved).
 * - `DOCUMENTED`:   review done; obligations + artifact hash recorded in this ledger.
 * - `SATISFIED`:    obligations fulfilled AND evidence recorded (pre-use
 *                   notification sent, hardware test copy available, attribution shipped).
 * - `BLOCKED`:      material is not cleared for commercial distribution.
 *
 * THIRD_PARTY_NOTICES.md MUST be generated from this ledger
 * (tools/legal/generate_third_party_notices.py). No prose-only compliance
 * claims are permitted: a notice table row without a ledger entry and artifact
 * hash is not a claim of compliance.
 */
enum class LegalEvidenceStatus {
    UNREVIEWED,
    REVIEW_REQUIRED,
    DOCUMENTED,
    SATISFIED,
    BLOCKED
}

data class LegalEvidenceEntry(
    val id: String,
    val title: String,
    val status: LegalEvidenceStatus,
    val artifactPath: String,
    val artifactSha256: String? = null,
    val obligations: List<String> = emptyList(),
    val notes: String = ""
)

/**
 * State machine guard. Returns null when the transition is legal, otherwise a
 * human-readable reason (release gates consume this result — a null means the
 * edit is rejected before it can ever persist).
 */
object LegalEvidenceLedger {

    private val ALLOWED = mapOf(
        LegalEvidenceStatus.UNREVIEWED to setOf(LegalEvidenceStatus.REVIEW_REQUIRED, LegalEvidenceStatus.BLOCKED),
        LegalEvidenceStatus.REVIEW_REQUIRED to setOf(LegalEvidenceStatus.DOCUMENTED, LegalEvidenceStatus.BLOCKED),
        LegalEvidenceStatus.DOCUMENTED to setOf(LegalEvidenceStatus.SATISFIED, LegalEvidenceStatus.REVIEW_REQUIRED, LegalEvidenceStatus.BLOCKED),
        LegalEvidenceStatus.SATISFIED to setOf(LegalEvidenceStatus.REVIEW_REQUIRED, LegalEvidenceStatus.BLOCKED),
        LegalEvidenceStatus.BLOCKED to emptySet()
    )

    fun canTransition(from: LegalEvidenceStatus, to: LegalEvidenceStatus): Boolean =
        to in ALLOWED.getValue(from)

    fun transitionGuard(from: LegalEvidenceStatus, to: LegalEvidenceStatus): String? {
        if (to !in ALLOWED.getValue(from)) {
            return "illegal ledger transition $from -> $to (allowed: ${ALLOWED.getValue(from)})"
        }
        return null
    }
}