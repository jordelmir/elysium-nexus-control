package com.elysium.nexus.ui.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.ProbeRestoreDecision
import com.elysium.nexus.fabric.infrared.ProbeRestoreResolver
import com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase
import com.elysium.nexus.fabric.profile.db.ProbeSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * P0.3: IrProbeViewModel — Survives process death via SavedStateHandle + Room.
 *
 * SavedStateHandle holds step/verifiedActions/isAutoScanning (lightweight, fast).
 * Room holds full probe session state (candidate index, signalId, attemptId, etc.).
 *
 * On restoration after process death:
 * 1. SavedStateHandle restores step + verifiedActions + isAutoScanning
 * 2. Room restores candidateIndex, currentCandidateId, lastSignalId, etc.
 * 3. Engine is reconstructed and repositioned to saved candidate index
 * 4. Candidate identity is verified (same codeSetId at same index)
 * 5. If verification fails → RECOVERY_REQUIRED (never silently select candidate 0)
 */
class IrProbeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // ═══════════════════════════════════════════════════════════════════
    // Persisted state via SavedStateHandle (survives process death)
    // ═══════════════════════════════════════════════════════════════════

    internal var step: IrStep
        get() = savedStateHandle["step"] ?: IrStep.ORIENT
        set(value) { savedStateHandle["step"] = value }

    val verifiedActions: Set<IrAction>
        get() = try {
            val json = savedStateHandle.get<String>("verifiedActions") ?: "[]"
            parseActionSet(json)
        } catch (e: Exception) { emptySet() }
    fun setVerifiedActions(value: Set<IrAction>) {
        savedStateHandle["verifiedActions"] = serializeActionSet(value)
    }

    var isAutoScanning: Boolean
        get() = savedStateHandle["isAutoScanning"] ?: false
        set(value) { savedStateHandle["isAutoScanning"] = value }

    // ═══════════════════════════════════════════════════════════════════
    // Transient state (reconstructed from Room after process death)
    // ═══════════════════════════════════════════════════════════════════

    private val _probeUiState = MutableStateFlow<ProbeUiState>(ProbeUiState.LoadingCatalog)
    val probeUiState: StateFlow<ProbeUiState> = _probeUiState.asStateFlow()

    private val _currentAttempt = MutableStateFlow<ProbeAttempt?>(null)
    val currentAttempt: StateFlow<ProbeAttempt?> = _currentAttempt.asStateFlow()

    private var probeEngine: IrProbeEngine? = null
    private var sessionId: String? = null
    private var lastPersistedIndex: Int = 0
    private var lastPersistedCandidateId: String? = null
    private var lastPersistedActionKey: String? = null
    private var lastPersistedSignalId: String? = null
    private var lastPersistedPhysicalSha256: String? = null
    private var lastPersistedAttemptId: String? = null

    // ═══════════════════════════════════════════════════════════════════
    // Room persistence
    // ═══════════════════════════════════════════════════════════════════

    private val db = ElysiumUserDatabase.getInstance(application)

    /**
     * Create or update probe session in Room.
     */
    fun persistSession(
        brand: String,
        deviceType: String,
        targetModel: String?,
        candidateIndex: Int,
        candidateId: String?,
        actionKey: String?,
        signalId: String?,
        physicalSha256: String?,
        attemptId: String?,
        catalogHash: String?
    ) {
        val sid = sessionId ?: UUID.randomUUID().toString().also { sessionId = it }
        lastPersistedIndex = candidateIndex
        lastPersistedCandidateId = candidateId
        lastPersistedActionKey = actionKey
        lastPersistedSignalId = signalId
        lastPersistedPhysicalSha256 = physicalSha256
        lastPersistedAttemptId = attemptId

        viewModelScope.launch {
            val entity = ProbeSessionEntity(
                sessionId = sid,
                brand = brand,
                deviceType = deviceType,
                targetModel = targetModel,
                startedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = null,
                status = step.name,
                winnerCodeSetId = null,
                currentCandidateIndex = candidateIndex,
                currentCandidateId = candidateId,
                currentActionKey = actionKey,
                lastSignalId = signalId,
                lastPhysicalSha256 = physicalSha256,
                lastAttemptId = attemptId,
                catalogHashAtStart = catalogHash,
                verifiedActionKeys = serializeActionSet(verifiedActions)
            )
            db.profileDao().insertProbeSession(entity)
        }
    }

    /**
     * Update just the transient probe state in Room (no full re-insert).
     */
    fun updateProbeState(
        candidateIndex: Int,
        candidateId: String?,
        actionKey: String?,
        signalId: String?,
        physicalSha256: String?,
        attemptId: String?
    ) {
        val sid = sessionId ?: return
        lastPersistedIndex = candidateIndex
        lastPersistedCandidateId = candidateId
        lastPersistedActionKey = actionKey
        lastPersistedSignalId = signalId
        lastPersistedPhysicalSha256 = physicalSha256
        lastPersistedAttemptId = attemptId

        viewModelScope.launch {
            db.profileDao().updateProbeSessionState(
                sessionId = sid,
                candidateIndex = candidateIndex,
                candidateId = candidateId,
                actionKey = actionKey,
                signalId = signalId,
                physicalSha256 = physicalSha256,
                attemptId = attemptId,
                verifiedActionKeys = serializeActionSet(verifiedActions)
            )
        }
    }

    /**
     * Restore probe session from Room after process death.
     * Returns the session if found, null otherwise.
     */
    suspend fun restoreSession(sessionId: String): ProbeSessionEntity? {
        val entity = db.profileDao().getProbeSession(sessionId) ?: return null
        this.sessionId = sessionId
        this.lastPersistedIndex = entity.currentCandidateIndex
        this.lastPersistedCandidateId = entity.currentCandidateId
        this.lastPersistedActionKey = entity.currentActionKey
        this.lastPersistedSignalId = entity.lastSignalId
        this.lastPersistedPhysicalSha256 = entity.lastPhysicalSha256
        this.lastPersistedAttemptId = entity.lastAttemptId

        // Restore step from session status
        val restoredStep = try {
            IrStep.valueOf(entity.status)
        } catch (e: Exception) {
            IrStep.ORIENT
        }
        step = restoredStep

        // Restore verified actions
        setVerifiedActions(parseActionSet(entity.verifiedActionKeys))

        return entity
    }

    /**
     * Initialize the probe engine with candidates, optionally restoring to a saved position.
     */
    fun initializeEngine(
        candidates: List<IrCodeSet>,
        targetModel: String?,
        penaltyMap: Map<String, Int>,
        successMap: Map<String, Int>,
        failMap: Map<String, Int>,
        restoreCandidateIndex: Int = 0,
        restoreCandidateId: String? = null
    ): Boolean {
        val engine = IrProbeEngine(
            rawCandidates = candidates,
            targetModel = targetModel,
            penaltyMap = penaltyMap,
            successMap = successMap,
            failMap = failMap
        )

        if (engine.totalCandidates == 0) {
            _probeUiState.value = ProbeUiState.NoCompatibleCandidates
            return false
        }

        // Reposition to saved candidate (process-death restore with identity guard)
        when (
            val decision = ProbeRestoreResolver.resolve(
                engine = engine,
                restoreCandidateIndex = restoreCandidateIndex,
                restoreCandidateId = restoreCandidateId
            )
        ) {
            is ProbeRestoreDecision.Ready -> { /* safe to resume at the resolved candidate */ }
            is ProbeRestoreDecision.RecoveryRequired -> {
                _probeUiState.value = ProbeUiState.RecoveryRequired(
                    reason = "Candidate identity mismatch after process death. " +
                        "Expected=${decision.expectedId}, Found=${decision.foundId}"
                )
                return false
            }
        }

        probeEngine = engine
        _probeUiState.value = ProbeUiState.Ready(engine)
        return true
    }

    fun currentCandidate(): IrCodeSet? = probeEngine?.currentCandidate()
    fun nextCandidate(): IrCodeSet? = probeEngine?.nextCandidate()
    fun selectById(id: String): Boolean = probeEngine?.selectById(id) ?: false

    fun recordSuccess(action: IrAction) {
        val current = verifiedActions.toMutableSet()
        current.add(action)
        setVerifiedActions(current)
    }

    fun isActionVerified(action: IrAction): Boolean = action in verifiedActions
    fun getCurrentAttempt(): ProbeAttempt? = _currentAttempt.value
    fun setCurrentAttempt(attempt: ProbeAttempt?) { _currentAttempt.value = attempt }
    fun setProbeUiState(state: ProbeUiState) { _probeUiState.value = state }
    fun hasRestorableState(): Boolean = savedStateHandle.get<String>("step") != null
    fun getSessionId(): String? = sessionId
    fun getCandidateIndex(): Int = lastPersistedIndex
    fun getCandidateId(): String? = lastPersistedCandidateId

    /**
     * Mark session complete in Room.
     */
    fun completeSession(winnerCodeSetId: String?) {
        val sid = sessionId ?: return
        viewModelScope.launch {
            db.profileDao().completeProbeSession(
                sessionId = sid,
                status = "COMPLETED",
                completedAtMs = System.currentTimeMillis(),
                winnerCodeSetId = winnerCodeSetId
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Serialization helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun serializeActionSet(actions: Set<IrAction>): String {
        return actions.joinToString(",") { it.name }
    }

    private fun parseActionSet(json: String): Set<IrAction> {
        if (json.isBlank() || json == "[]") return emptySet()
        return json.split(",").mapNotNull {
            try { IrAction.valueOf(it.trim()) } catch (e: Exception) { null }
        }.toSet()
    }
}
