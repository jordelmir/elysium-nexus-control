package com.elysium.nexus.ui.connect

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ElysiumNexus.IrProbe"

// ═══════════════════════════════════════════════════════════════════════════
// §23 Complete Probe State Machine
// ═══════════════════════════════════════════════════════════════════════════

sealed interface ProbeUiState {
    data object LoadingCatalog : ProbeUiState
    data class Ready(val probeEngine: IrProbeEngine) : ProbeUiState
    data object Exhausted : ProbeUiState
    data object NoCompatibleCandidates : ProbeUiState
    data class Transmitting(
        val candidateId: String,
        val codeSetId: String,
        val action: IrAction,
        val attemptId: String
    ) : ProbeUiState
    data class AwaitingConfirmation(
        val candidateId: String,
        val codeSetId: String,
        val attemptId: String,
        val action: IrAction,
        val result: IrTransmitResult
    ) : ProbeUiState
    data class VerifyingSecondaryAction(
        val candidateId: String,
        val codeSetId: String,
        val primaryAttemptId: String,
        val secondaryAction: IrAction,
        val verifiedActions: Set<IrAction>
    ) : ProbeUiState
    data class ChallengeConfirmation(
        val candidateId: String,
        val codeSetId: String,
        val attemptId: String,
        val action: IrAction
    ) : ProbeUiState
    data object SavingProfile : ProbeUiState
    data class Completed(val profile: InstalledIrProfile) : ProbeUiState
    data class Error(val message: String) : ProbeUiState
}

// ═══════════════════════════════════════════════════════════════════════════
// §23 Probe Attempt — authoritatively tracked
// ═══════════════════════════════════════════════════════════════════════════

data class ProbeAttempt(
    val attemptId: String = UUID.randomUUID().toString(),
    val candidateId: String,
    val codeSetId: String,
    val signalId: String,
    val action: IrAction,
    val transmittedAtMs: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════════════════════
// §4/§24 Multi-action verification actions
// ═══════════════════════════════════════════════════════════════════════════

private val VERIFICATION_ACTIONS = listOf(
    IrAction.VOLUME_UP,
    IrAction.VOLUME_DOWN,
    IrAction.MUTE
)

@Composable
fun IrConnectFlow(
    template: DeviceTemplate,
    onBack: () -> Unit,
    onProfileInstalled: (InstalledIrProfile) -> Unit,
    onTryOther: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasIrBlaster: Boolean,
    modifier: Modifier = Modifier
) {
    // P1-17: Use rememberSaveable for critical state that must survive process death
    var step by rememberSaveable { mutableStateOf(IrStep.ORIENT) }
    var showHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var probeUiState by remember { mutableStateOf<ProbeUiState>(ProbeUiState.LoadingCatalog) }
    var currentResult by remember { mutableStateOf<IrTransmitResult?>(null) }
    var currentAttempt by remember { mutableStateOf<ProbeAttempt?>(null) }
    var currentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var verifiedActions by rememberSaveable { mutableStateOf<Set<IrAction>>(emptySet()) }
    var autoScanJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isAutoScanning by rememberSaveable { mutableStateOf(false) }
    var lastProbedCandidate by remember { mutableStateOf<com.elysium.nexus.core.device.IrCodeSet?>(null) }

    // §6 Pass targetModel for real ranking
    val targetModel = template.model

    // "Control Universal" is the first card: sweep ALL production-approved
    // TV code sets in the catalog so every tap tests a new candidate.
    val isUniversalSweep = template.id == "tv-universal-generic"

    // Async SQLite Candidate Loading + P1-EVIDENCE: penalty/evidence data
    LaunchedEffect(template) {
        probeUiState = ProbeUiState.LoadingCatalog
        val repo = IrCatalogRepository.getInstance(context)
        val profileRepo = InstalledIrProfileRepository(context)
        val sqliteCandidates = if (isUniversalSweep) {
            repo.getAllCandidates(
                deviceType = "TV",
                action = IrAction.VOLUME_UP,
                limit = 400
            )
        } else {
            repo.getCandidatesForBrand(
                brand = template.brand,
                deviceType = "",
                action = IrAction.VOLUME_UP
            )
        }

        // P1-EVIDENCE: Load penalty and evidence data from Room
        val penaltyMap = mutableMapOf<String, Int>()
        val successMap = mutableMapOf<String, Int>()
        val failMap = mutableMapOf<String, Int>()
        try {
            val db = com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase.getInstance(context)
            // Load penalties
            val penalties = db.profileDao().getTopPenalties(100)
            for (p in penalties) {
                penaltyMap[p.codeSetId] = p.penaltyScore
            }
            // P1-11: Single GROUP BY query instead of N+1 per-candidate queries
            val evidenceCounts = db.profileDao().getEvidenceCountsByCodeSet("VOLUME_UP")
            for (row in evidenceCounts) {
                if (row.successCount > 0) successMap[row.codeSetId] = row.successCount
                if (row.failCount > 0) failMap[row.codeSetId] = row.failCount
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load penalty/evidence data: ${e.message}")
        }

        if (sqliteCandidates.isNotEmpty()) {
            val engine = IrProbeEngine(
                rawCandidates = sqliteCandidates,
                targetModel = targetModel,
                penaltyMap = penaltyMap,
                successMap = successMap,
                failMap = failMap
            )
            if (engine.totalCandidates > 0) {
                probeUiState = ProbeUiState.Ready(engine)
                Log.d(TAG, "Loaded ${engine.totalCandidates} candidates for brand=${template.brand} (universal=$isUniversalSweep), targetModel=$targetModel, penalties=${penaltyMap.size}, evidence=${successMap.size}")
            } else {
                probeUiState = ProbeUiState.NoCompatibleCandidates
                Log.w(TAG, "SQLite returned ${sqliteCandidates.size} code sets but none had VOLUME_UP after dedup")
            }
        } else {
            probeUiState = ProbeUiState.NoCompatibleCandidates
            Log.w(TAG, "No SQLite candidates found for brand=${template.brand} (universal=$isUniversalSweep). Zero fallbacks.")
        }
    }

    val activeEngine = (probeUiState as? ProbeUiState.Ready)?.probeEngine

    fun sendTestAction(candidate: com.elysium.nexus.core.device.IrCodeSet, action: IrAction) {
        // §24 Cancel any in-flight transmission before starting a new one
        currentJob?.cancel()
        currentResult = null

        val signal = candidate.commands[action] ?: return
        val encodeResult = IrProtocol.encode(signal)
        if (encodeResult is com.elysium.nexus.fabric.infrared.EncodeResult.Success) {
            val attempt = ProbeAttempt(
                candidateId = candidate.id,
                codeSetId = candidate.id,
                signalId = candidate.commandSignalIds[action]
                    ?: candidate.commandBindings.firstOrNull { it.action == action }?.signalId
                    ?: "",
                action = action
            )
            currentAttempt = attempt
            currentJob = scope.launch {
                com.elysium.nexus.fabric.infrared.FileLog.d("PROBE_TX candidate=${candidate.id} action=$action signalId=${attempt.signalId} carrierHz=${encodeResult.waveform.carrierHz}")
                val result = irTransmitter.transmit(encodeResult.waveform)
                // §24 Only accept result if attemptId still matches (race guard)
                if (currentAttempt?.attemptId == attempt.attemptId) {
                    currentResult = result
                }
            }
        }
    }

    // §38 Auto-sweep: transmit candidate N, pause, advance, repeat until the
    // user confirms or candidates are exhausted. Every stop leaves the engine
    // positioned on the LAST transmitted candidate (never a stuck state).
    fun startAutoScan(engine: IrProbeEngine) {
        if (isAutoScanning) return
        isAutoScanning = true
        currentResult = null
        autoScanJob?.cancel()
        autoScanJob = scope.launch {
            while (isActive) {
                val candidate = engine.currentCandidate() ?: break
                lastProbedCandidate = candidate
                sendTestAction(candidate, IrAction.VOLUME_UP)
                // Give the TV OSD time to react before the next candidate.
                delay(3_500)
                if (!engine.hasMore) break
                engine.nextCandidate()
            }
            isAutoScanning = false
            autoScanJob = null
        }
    }

    fun stopAutoScan() {
        isAutoScanning = false
        autoScanJob?.cancel()
        autoScanJob = null
    }

    // P1-EVIDENCE: Record when a candidate is rejected by the user
    fun recordCandidateRejection(candidate: com.elysium.nexus.core.device.IrCodeSet) {
        scope.launch {
            try {
                val profileRepo = InstalledIrProfileRepository(context)
                profileRepo.penalizeCandidate(
                    codeSetId = candidate.id,
                    reason = "user_rejected_vup"
                )
                profileRepo.recordCompatibilityEvidence(
                    codeSetId = candidate.id,
                    brand = candidate.brand,
                    deviceType = "TV",
                    actionKey = "VOLUME_UP",
                    success = false,
                    source = "local_probe_rejection"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record rejection evidence: ${e.message}")
            }
        }
    }

    // P1-EVIDENCE: Record when a candidate is confirmed by the user
    fun recordCandidateConfirmation(candidate: com.elysium.nexus.core.device.IrCodeSet) {
        scope.launch {
            try {
                val profileRepo = InstalledIrProfileRepository(context)
                profileRepo.recordCompatibilityEvidence(
                    codeSetId = candidate.id,
                    brand = candidate.brand,
                    deviceType = "TV",
                    actionKey = "VOLUME_UP",
                    success = true,
                    source = "local_probe_confirmation"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record confirmation evidence: ${e.message}")
            }
        }
    }

    fun buildAndPersistInstalledProfile(winnerCandidate: com.elysium.nexus.core.device.IrCodeSet, verifiedActions: Set<IrAction>): InstalledIrProfile? {
        val bindings = mutableMapOf<IrAction, IrCommandBinding>()

        for ((action, signal) in winnerCandidate.commands) {
            // §1 Use ONLY real signalIds from SQLite. Never fabricate.
            val realSignalId = winnerCandidate.commandSignalIds[action]
                ?: winnerCandidate.commandBindings.firstOrNull { it.action == action }?.signalId
                ?: continue

            val fp = IrProbeEngine.fingerprintSignal(signal)
            val sourceRevision = winnerCandidate.commandBindings
                .firstOrNull { it.action == action }
                ?.sourceRevisionId
                ?: winnerCandidate.provenance.commitSha
                ?: "catalog-legacy"
            bindings[action] = IrCommandBinding(
                signalId = realSignalId,
                physicalFingerprint = fp,
                sourceId = sourceRevision,
                action = action
            )
        }

        if (bindings.isEmpty()) {
            Log.e(TAG, "CRITICAL: Winner ${winnerCandidate.id} has ZERO real bindings. Refusing to save.")
            return null
        }

        val status = when {
            verifiedActions.size >= 3 -> VerificationStatus.SESSION_VERIFIED
            verifiedActions.size >= 2 -> VerificationStatus.PARTIALLY_VERIFIED
            verifiedActions.isNotEmpty() -> VerificationStatus.PARTIALLY_VERIFIED
            else -> VerificationStatus.UNVERIFIED
        }

        val profile = InstalledIrProfile(
            displayName = "${winnerCandidate.brand} Remote (${winnerCandidate.id.take(8)})",
            brand = winnerCandidate.brand,
            deviceType = template.category.name,
            model = winnerCandidate.modelPatterns.firstOrNull(),
            remoteModel = winnerCandidate.remoteModels.firstOrNull(),
            codeSetId = winnerCandidate.id,
            sourceRevision = winnerCandidate.commandBindings.firstOrNull()?.sourceRevisionId
                ?: winnerCandidate.provenance.commitSha
                ?: "catalog-legacy",
            commands = bindings,
            verifiedActions = verifiedActions,
            verificationStatus = status
        )

        val profileRepo = InstalledIrProfileRepository(context)
        val result = profileRepo.saveProfile(profile, verifiedActions)
        when (result) {
            is com.elysium.nexus.fabric.profile.SaveProfileResult.Saved -> {
                Log.d(TAG, "Installed profile ${profile.id} with ${bindings.size} bindings, verified=$verifiedActions")
                return profile
            }
            is com.elysium.nexus.fabric.profile.SaveProfileResult.ValidationFailure -> {
                Log.e(TAG, "Profile validation failed: ${result.reason}")
                return null
            }
            is com.elysium.nexus.fabric.profile.SaveProfileResult.StorageFailure -> {
                Log.e(TAG, "Profile storage failed: ${result.cause.message}")
                return null
            }
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Ayuda", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            NeonHeroCard(
                title = "${template.brand} ${template.model}",
                subtitle = template.blurbEs,
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(label = "Paso ${step.number} de ${IrStep.entries.size}", color = ElysiumColors.NeonOrange)
                    activeEngine?.let { engine ->
                        NeonStatusPill(label = "Probe ${engine.currentProbeNumber}/${engine.totalCandidates}", color = ElysiumColors.NeonPurple)
                    }
                }
            )

            StepIndicator(currentStep = step, modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 8.dp))

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding)) {
                when (val uiState = probeUiState) {
                    is ProbeUiState.LoadingCatalog -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ElysiumColors.NeonCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Cargando catálogo SQLite...", style = TextStyle(fontSize = 14.sp, color = ElysiumColors.OnSurfaceVariant))
                            }
                        }
                    }
                    is ProbeUiState.Ready -> {
                        val engine = uiState.probeEngine
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.95f)) },
                            label = "ir_step"
                        ) { currentStep ->
                            when (currentStep) {
                                IrStep.ORIENT -> OrientStep(
                                    onContinue = {
                                        step = IrStep.TEST
                                        val candidate = engine.currentCandidate() ?: return@OrientStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    },
                                    hasIrBlaster = hasIrBlaster
                                )
                                IrStep.TEST -> TestStep(
                                    template = template,
                                    probeEngine = engine,
                                    lastResult = currentResult,
                                    isAutoScanning = isAutoScanning,
                                    onSendTest = {
                                        if (isAutoScanning) {
                                            // During sweep the user only stops; taps are confirmations.
                                            return@TestStep
                                        }
                                        val candidate = engine.currentCandidate() ?: return@TestStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    },
                                    onDidWork = {
                                        // §38 P0-3 Challenge confirmation: re-transmit to prevent
                                        // wrong candidate selection from timing race.
                                        val winner = lastProbedCandidate ?: engine.currentCandidate()
                                        if (winner != null) {
                                            stopAutoScan()
                                            engine.selectById(winner.id)
                                            // Re-transmit VOLUME_UP as challenge before accepting
                                            step = IrStep.CHALLENGE
                                            sendTestAction(winner, IrAction.VOLUME_UP)
                                        }
                                    },
                                    onNextCandidate = {
                                        if (isAutoScanning) return@TestStep
                                        // P1-EVIDENCE: Record rejection before advancing
                                        val rejected = engine.currentCandidate()
                                        if (rejected != null) recordCandidateRejection(rejected)
                                        engine.nextCandidate()
                                        lastProbedCandidate = engine.currentCandidate()
                                        currentResult = null
                                        val candidate = engine.currentCandidate() ?: return@TestStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    },
                                    onStartAutoScan = { startAutoScan(engine) },
                                    onStopAutoScan = { stopAutoScan() },
                                    hasIrBlaster = hasIrBlaster
                                )
                                IrStep.CHALLENGE -> ChallengeStep(
                                    lastResult = currentResult,
                                    onDidWork = {
                                        // P0-1: Challenge confirmed — VOLUME_UP verified twice.
                                        // Record evidence, advance to VERIFY_SECONDARY (VOLUME_DOWN).
                                        val winner = engine.currentCandidate()
                                        if (winner != null) {
                                            verifiedActions = setOf(IrAction.VOLUME_UP)
                                            recordCandidateConfirmation(winner)
                                            if (IrAction.VOLUME_DOWN in winner.commands) {
                                                step = IrStep.VERIFY_SECONDARY
                                                sendTestAction(winner, IrAction.VOLUME_DOWN)
                                            } else {
                                                step = IrStep.SAVE
                                            }
                                        }
                                    },
                                    onNo = {
                                        // P1-EVIDENCE: Record rejection on challenge failure
                                        val rejected = engine.currentCandidate()
                                        if (rejected != null) recordCandidateRejection(rejected)
                                        // Challenge failed — the re-transmit didn't work. Try next.
                                        engine.nextCandidate()
                                        currentResult = null
                                        currentAttempt = null
                                        verifiedActions = emptySet()
                                        step = IrStep.TEST
                                        val candidate = engine.currentCandidate() ?: return@ChallengeStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    }
                                )
                                IrStep.CONFIRM -> ConfirmStep(
                                    onYes = {
                                        // §24 VOLUME_UP verified. Now verify VOLUME_DOWN.
                                        verifiedActions = setOf(IrAction.VOLUME_UP)
                                        val candidate = engine.currentCandidate()
                                        if (candidate != null && IrAction.VOLUME_DOWN in candidate.commands) {
                                            step = IrStep.VERIFY_SECONDARY
                                            sendTestAction(candidate, IrAction.VOLUME_DOWN)
                                        } else {
                                            // No VOLUME_DOWN available, skip to SAVE
                                            step = IrStep.SAVE
                                        }
                                    },
                                    onNo = {
                                        engine.nextCandidate()
                                        currentResult = null
                                        currentAttempt = null
                                        verifiedActions = emptySet()
                                        step = IrStep.TEST
                                        val candidate = engine.currentCandidate() ?: return@ConfirmStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    }
                                )
                                IrStep.VERIFY_SECONDARY -> VerifyActionStep(
                                    actionLabel = "VOLUME_DOWN",
                                    action = IrAction.VOLUME_DOWN,
                                    lastResult = currentResult,
                                    currentAttempt = currentAttempt,
                                    onSendTest = {
                                        val candidate = engine.currentCandidate() ?: return@VerifyActionStep
                                        sendTestAction(candidate, IrAction.VOLUME_DOWN)
                                    },
                                    onDidWork = {
                                        // §24 VOLUME_DOWN verified. Now verify MUTE.
                                        verifiedActions = verifiedActions + IrAction.VOLUME_DOWN
                                        val candidate = engine.currentCandidate()
                                        if (candidate != null && IrAction.MUTE in candidate.commands) {
                                            step = IrStep.VERIFY_TERTIARY
                                            sendTestAction(candidate, IrAction.MUTE)
                                        } else {
                                            step = IrStep.SAVE
                                        }
                                    },
                                    onSkip = {
                                        // §24 User says VOLUME_DOWN didn't work, skip to MUTE or SAVE
                                        val candidate = engine.currentCandidate()
                                        if (candidate != null && IrAction.MUTE in candidate.commands) {
                                            step = IrStep.VERIFY_TERTIARY
                                            sendTestAction(candidate, IrAction.MUTE)
                                        } else {
                                            step = IrStep.SAVE
                                        }
                                    }
                                )
                                IrStep.VERIFY_TERTIARY -> VerifyActionStep(
                                    actionLabel = "MUTE",
                                    action = IrAction.MUTE,
                                    lastResult = currentResult,
                                    currentAttempt = currentAttempt,
                                    onSendTest = {
                                        val candidate = engine.currentCandidate() ?: return@VerifyActionStep
                                        sendTestAction(candidate, IrAction.MUTE)
                                    },
                                    onDidWork = {
                                        // §24 All 3 actions verified
                                        verifiedActions = verifiedActions + IrAction.MUTE
                                        step = IrStep.SAVE
                                    },
                                    onSkip = {
                                        step = IrStep.SAVE
                                    }
                                )
                                IrStep.SAVE -> SaveStep(
                                    template = template,
                                    verifiedActions = verifiedActions,
                                    onSave = {
                                        val winner = engine.currentCandidate()
                                        if (winner != null) {
                                            // §24 Use actually verified actions, not assumed
                                            val profile = buildAndPersistInstalledProfile(winner, verifiedActions)
                                            if (profile != null) {
                                                probeUiState = ProbeUiState.Completed(profile)
                                                onProfileInstalled(profile)
                                            } else {
                                                onTryOther()
                                            }
                                        } else {
                                            onTryOther()
                                        }
                                    },
                                    onLearnInstead = onTryOther
                                )
                            }
                        }
                    }
                    is ProbeUiState.Exhausted -> {
                        NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonOrange, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Candidatos agotados", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
                                Text("Se probaron todos los candidatos. Puedes buscar otro modelo o marca.", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
                            }
                        }
                    }
                    is ProbeUiState.NoCompatibleCandidates -> {
                        NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonOrange, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Sin candidatos compatibles", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
                                Text("El catálogo no contiene códigos IR para ${template.brand}. Opciones:", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
                                NeonChip(label = "Buscar modelo exacto del TV", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Buscar modelo del control", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Importar perfil", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Aprender mediante receptor IR", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Volver y elegir otra marca", onClick = onBack, accent = ElysiumColors.NeonPurple, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    is ProbeUiState.Transmitting, is ProbeUiState.AwaitingConfirmation,
                    is ProbeUiState.VerifyingSecondaryAction, is ProbeUiState.ChallengeConfirmation,
                    is ProbeUiState.SavingProfile, is ProbeUiState.Completed -> {
                        // States handled by navigation, not rendered here
                    }
                    is ProbeUiState.Error -> {
                        Text("Error: ${uiState.message}", color = Color.Red)
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Probar ${template.brand}",
            whatIsThis = "Esta pantalla busca un perfil de control IR probando VolumeUp.",
            howToUse = listOf(
                "Paso 1: Asegúrate de que la TV esté encendida.",
                "Paso 2: Apunta el teléfono al sensor IR. La señal se envía automáticamente.",
                "Paso 3: Si aparece el indicador de volumen, toca 'Sí'. Si no, toca 'Probar siguiente'."
            ),
            tip = "El sistema probará candidatos distintos sin repetir señales fallidas. Se verificarán VOLUME_UP, VOLUME_DOWN y MUTE del mismo codeSet.",
            onDismiss = { showHelp = false }
        )
    }
}

private enum class IrStep(val number: Int, val labelEn: String, val labelEs: String) {
    ORIENT(1, "Aim", "Apuntar"),
    TEST(2, "Test", "Probar"),
    CHALLENGE(3, "Verify", "Re-verificar"),
    CONFIRM(4, "Confirm", "Confirmar"),
    VERIFY_SECONDARY(5, "Down", "Bajar"),
    VERIFY_TERTIARY(6, "Mute", "Mute"),
    SAVE(7, "Save", "Guardar")
}

@Composable
private fun StepIndicator(currentStep: IrStep, modifier: Modifier = Modifier) {
    val steps = IrStep.entries.toTypedArray()
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { idx, s ->
            val isActive = s == currentStep
            val isPassed = s.number < currentStep.number
            val color = when {
                isActive -> ElysiumColors.NeonCyan
                isPassed -> ElysiumColors.NeonGreen
                else -> ElysiumColors.OnSurfaceVariant.copy(alpha = 0.4f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("${s.number}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = color)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(s.labelEs, style = TextStyle(fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal), color = color)
            }
            if (idx < steps.size - 1) {
                Box(modifier = Modifier.weight(1f).height(2.dp).padding(horizontal = 4.dp).background(if (isPassed) ElysiumColors.NeonGreen else ElysiumColors.OnSurfaceVariant.copy(alpha = 0.2f)))
            }
        }
    }
}

@Composable
private fun OrientStep(onContinue: () -> Unit, hasIrBlaster: Boolean) {
    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonCyan, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preparación de Sondeo IR", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(
                if (hasIrBlaster) "El emisor IR enviará códigos de prueba. Mantén el teléfono apuntado al sensor IR."
                else "Este teléfono no tiene emisor IR. Puedes probar con un receptor externo USB.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(label = "Comenzar Prueba", onClick = onContinue, accent = ElysiumColors.NeonCyan, icon = { Icon(Icons.Filled.Bolt, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TestStep(
    template: DeviceTemplate,
    probeEngine: IrProbeEngine,
    lastResult: IrTransmitResult?,
    isAutoScanning: Boolean,
    onSendTest: () -> Unit,
    onDidWork: () -> Unit,
    onNextCandidate: () -> Unit,
    onStartAutoScan: () -> Unit,
    onStopAutoScan: () -> Unit,
    hasIrBlaster: Boolean
) {
    val currentCand = probeEngine.currentCandidate()
    val canConfirm = lastResult is IrTransmitResult.Success && currentCand != null

    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonPurple, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (isAutoScanning) "Barrido Automático Activo — Candidato ${probeEngine.currentProbeNumber} de ${probeEngine.totalCandidates}"
                else "Prueba de Volumen — Candidato ${probeEngine.currentProbeNumber} de ${probeEngine.totalCandidates}",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface
            )
            Text(
                "Perfil: ${currentCand?.brand ?: template.brand} (${currentCand?.id?.take(12) ?: "?"})\n" +
                "Acción: VOLUME_UP\n" +
                "Verificación: ${currentCand?.verification ?: VerificationStatus.UNVERIFIED}",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonFab(icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) }, onClick = onSendTest, accent = ElysiumColors.NeonCyan, fabSize = 80.dp)
            }
            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Transmitido: ${res.carrierHz} Hz"
                    is IrTransmitResult.NoEmitter -> "Sin emisor IR"
                    is IrTransmitResult.PermissionDenied -> "Permiso denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                NeonStatusPill(label = resultText, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isAutoScanning) {
                // §38 While sweeping: one big action — stop and confirm.
                NeonChip(label = "¡Funcionó! Detener barrido", onClick = onDidWork, accent = ElysiumColors.NeonGreen, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
                NeonChip(label = "Pausar barrido", onClick = onStopAutoScan, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Pause, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
            } else {
                NeonChip(label = "▶ Barrido automático (probar todas las marcas)", onClick = onStartAutoScan, accent = ElysiumColors.NeonCyan, icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip(label = "Sí, subió el volumen", onClick = { if (canConfirm) onDidWork() }, accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray, active = canConfirm, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                    if (probeEngine.hasMore) {
                        NeonChip(label = "No / Siguiente", onClick = onNextCandidate, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeStep(
    lastResult: IrTransmitResult?,
    onDidWork: () -> Unit,
    onNo: () -> Unit
) {
    val canConfirm = lastResult is IrTransmitResult.Success

    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonCyan, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Re-verificación de señal", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(
                "Se reenvió VOLUME_UP para confirmar que este candidato funciona. ¿La TV reaccionó?",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonFab(icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) }, onClick = {}, accent = ElysiumColors.NeonCyan, fabSize = 80.dp)
            }
            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Re-transmitido: ${res.carrierHz} Hz"
                    is IrTransmitResult.NoEmitter -> "Sin emisor IR"
                    is IrTransmitResult.PermissionDenied -> "Permiso denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                NeonStatusPill(label = resultText, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(label = "Sí, funcionó", onClick = { if (canConfirm) onDidWork() }, accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray, active = canConfirm, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                NeonChip(label = "No, siguiente", onClick = onNo, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfirmStep(onYes: () -> Unit, onNo: () -> Unit) {
    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonGreen, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("¿Reaccionó tu TV?", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text("Si viste el indicador de volumen subir, confirma para verificar más acciones del mismo control.", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(label = "Sí, funcionó", onClick = onYes, accent = ElysiumColors.NeonGreen, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                NeonChip(label = "No, probar otro", onClick = onNo, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * §24 Verification step for secondary/tertiary actions (VOLUME_DOWN, MUTE).
 * Transmits the action, shows result, asks user to confirm.
 */
@Composable
private fun VerifyActionStep(
    actionLabel: String,
    action: IrAction,
    lastResult: IrTransmitResult?,
    currentAttempt: ProbeAttempt?,
    onSendTest: () -> Unit,
    onDidWork: () -> Unit,
    onSkip: () -> Unit
) {
    val canConfirm = lastResult is IrTransmitResult.Success && currentAttempt != null && currentAttempt.action == action

    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonCyan, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verificar $actionLabel", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(
                "Acción: $actionLabel\n¿La TV reaccionó a esta acción?",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonFab(icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) }, onClick = onSendTest, accent = ElysiumColors.NeonCyan, fabSize = 80.dp)
            }
            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Transmitido: ${res.carrierHz} Hz"
                    is IrTransmitResult.NoEmitter -> "Sin emisor IR"
                    is IrTransmitResult.PermissionDenied -> "Permiso denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                NeonStatusPill(label = resultText, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(label = "Sí, funcionó", onClick = { if (canConfirm) onDidWork() }, accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray, active = canConfirm, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                NeonChip(label = "Saltar", onClick = onSkip, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SaveStep(template: DeviceTemplate, verifiedActions: Set<IrAction>, onSave: () -> Unit, onLearnInstead: () -> Unit) {
    val verifiedCount = verifiedActions.size
    val statusText = when {
        verifiedCount >= 3 -> "3 acciones verificadas (VOLUME_UP + VOLUME_DOWN + MUTE)"
        verifiedCount >= 2 -> "2 acciones verificadas"
        verifiedCount == 1 -> "1 acción verificada (VOLUME_UP)"
        else -> "Sin verificación"
    }
    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonGreen, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Perfil Encontrado", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(statusText, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
            Text("Se guardará el perfil completo con las acciones verificadas.", style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = ElysiumColors.OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(label = "Instalar Control Remoto", onClick = onSave, accent = ElysiumColors.NeonGreen, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
