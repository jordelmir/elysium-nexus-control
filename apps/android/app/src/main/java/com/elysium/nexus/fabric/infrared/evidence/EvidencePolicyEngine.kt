package com.elysium.nexus.fabric.infrared.evidence

import com.elysium.nexus.fabric.infrared.database.model.PhysicalTestEvidence
import com.elysium.nexus.fabric.infrared.promotion.ClaimPromotionEngine
import com.elysium.nexus.fabric.infrared.promotion.CoreActionPolicy
import org.json.JSONObject

/**
 * Master Order v0.10 Phase 3 — ONE Evidence Policy Engine.
 *
 * The single declarative authority is the versioned JSON policy document
 * `schemas/protocol/retail-core-policy-v1.json` (committed at repo root).
 * This engine parses that document (fail-closed: malformed or unknown version
 * => refusal) and exposes its declarations; every derivation delegates to
 * [ClaimPromotionEngine] and the parse-time cross-checks below GUARANTEE the
 * in-app policy constants cannot silently diverge from the committed policy.
 *
 * Cross-language compatibility contract (Phase 3/13):
 * - Kotlin: this engine reports [policyVersion]/[coreActionsFor]/[claimLadder].
 * - Python: `tools/ir-data/tv_claim_policy.py` loads the SAME JSON document.
 * - CI consistency check asserts JSON == Kotlin constants == Python readings,
 *   and that the schemas copy is byte-identical to the test-resource copy.
 */
class EvidencePolicyEngine private constructor(private val document: JSONObject) {

    val policyVersion: String = document.getString("policyVersion")

    private val coreActionsJson: JSONObject = document.getJSONObject("coreActions")
    private val claimLadderJson: List<String> =
        document.getJSONArray("claimLadder").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
    private val rulesJson: JSONObject = document.getJSONObject("rules")

    fun coreActionsFor(deviceType: String): Set<String> {
        val key = deviceType.uppercase()
        if (!coreActionsJson.has(key)) {
            throw IllegalStateException("policy $policyVersion has no coreActions for device type $key")
        }
        return coreActionsJson.getJSONArray(key).let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }
    }

    val claimLadder: List<String> get() = claimLadderJson

    fun rule(name: String): Boolean = rulesJson.optBoolean(name, false)

    fun deriveCoreMatrix(
        evidenceList: List<PhysicalTestEvidence>,
        deviceType: String = "TV"
    ): ClaimPromotionEngine.CoreMatrixResult =
        ClaimPromotionEngine.deriveCoreMatrix(evidenceList, coreActionsFor(deviceType))

    fun deriveClaimStatus(
        evidenceList: List<PhysicalTestEvidence>
    ): ClaimPromotionEngine.DerivationResult =
        ClaimPromotionEngine.deriveClaimStatus(evidenceList)

    /**
     * Fail-closed cross-check that the in-app policy constants match the
     * committed declarative policy. Throws on ANY divergence.
     */
    fun verifyAgainstInAppConstants() {
        require(policyVersion == CoreActionPolicy.POLICY_VERSION) {
            "policy version mismatch: policy=$policyVersion kotlin=${CoreActionPolicy.POLICY_VERSION}"
        }
        require(coreActionsFor("TV") == CoreActionPolicy.TV_CORE_ACTIONS) {
            "policy TV core actions mismatch vs Kotlin constants: ${coreActionsFor("TV")} != ${CoreActionPolicy.TV_CORE_ACTIONS}"
        }
        val ladderNames = ClaimPromotionEngine.CLAIM_LADDER.map { it.name }
        require(claimLadderJson == ladderNames) {
            "policy claim ladder mismatch vs Kotlin: $claimLadderJson != $ladderNames"
        }
    }

    companion object {
        /** Parses a policy document; malformed content throws (fail closed). */
        fun parse(policyJson: String): EvidencePolicyEngine {
            val doc = JSONObject(policyJson)
            require(doc.getString("policyVersion").isNotBlank()) { "policyVersion must not be blank" }
            val engine = EvidencePolicyEngine(doc)
            engine.verifyAgainstInAppConstants()
            return engine
        }
    }
}