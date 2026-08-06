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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.IrSignal
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
import kotlinx.coroutines.launch

private const val TAG = "ElysiumNexus.IrProbe"

sealed interface ProbeUiState {
    data object LoadingCatalog : ProbeUiState
    data class Ready(val probeEngine: IrProbeEngine) : ProbeUiState
    data object Exhausted : ProbeUiState
    data class Error(val message: String) : ProbeUiState
}

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
    var step by remember { mutableStateOf(IrStep.ORIENT) }
    var showHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var probeUiState by remember { mutableStateOf<ProbeUiState>(ProbeUiState.LoadingCatalog) }
    var currentResult by remember { mutableStateOf<IrTransmitResult?>(null) }

    // Async SQLite Candidate Loading (Eliminates P0 Race Condition with DeviceCatalog)
    LaunchedEffect(template) {
        probeUiState = ProbeUiState.LoadingCatalog
        val repo = IrCatalogRepository(context)
        val sqliteCandidates = repo.getCandidatesForBrand(
            brand = template.brand,
            deviceType = "TV",
            action = IrAction.VOLUME_UP
        )

        if (sqliteCandidates.isNotEmpty()) {
            val engine = IrProbeEngine(sqliteCandidates)
            probeUiState = ProbeUiState.Ready(engine)
            Log.d(TAG, "Loaded ${engine.totalCandidates} candidates directly from SQLite ir_catalog.db for brand=${template.brand}")
        } else {
            // Fallback for custom / non-catalog brands: build regional candidate set
            val fallbackCandidates = listOf(
                IrCodeSet(
                    id = "cand-fallback-nec-0x07",
                    brand = template.brand,
                    modelPatterns = setOf(template.model),
                    remoteModels = emptySet(),
                    commands = mapOf(
                        IrAction.VOLUME_UP to IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x07),
                        IrAction.VOLUME_DOWN to IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x06),
                        IrAction.MUTE to IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x08),
                        IrAction.POWER_TOGGLE to IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x02)
                    ),
                    provenance = CodeProvenance("Elysium Regional Fallback", "", "MIT"),
                    verification = VerificationStatus.UNVERIFIED
                ),
                IrCodeSet(
                    id = "cand-fallback-samsung-0x07",
                    brand = template.brand,
                    modelPatterns = setOf(template.model),
                    remoteModels = emptySet(),
                    commands = mapOf(
                        IrAction.VOLUME_UP to IrSignal.Encoded(38000, IrProtocol.Samsung, 0x07, null, 0x07),
                        IrAction.VOLUME_DOWN to IrSignal.Encoded(38000, IrProtocol.Samsung, 0x07, null, 0x0B),
                        IrAction.MUTE to IrSignal.Encoded(38000, IrProtocol.Samsung, 0x07, null, 0x0F),
                        IrAction.POWER_TOGGLE to IrSignal.Encoded(38000, IrProtocol.Samsung, 0x07, null, 0x02)
                    ),
                    provenance = CodeProvenance("Elysium Regional Fallback", "", "MIT"),
                    verification = VerificationStatus.UNVERIFIED
                )
            )
            val engine = IrProbeEngine(fallbackCandidates)
            probeUiState = ProbeUiState.Ready(engine)
        }
    }

    val activeEngine = (probeUiState as? ProbeUiState.Ready)?.probeEngine

    fun sendVolumeTestForCurrentCandidate() {
        val engine = activeEngine ?: return
        val candidate = engine.currentCandidate() ?: return
        val signal = candidate.commands[IrAction.VOLUME_UP] ?: return
        val sigDetails = IrProbeEngine.fingerprintSignal(signal)
        Log.d(TAG, "Transmitting Probe #${engine.currentProbeNumber}/${engine.totalCandidates}: ID=${candidate.id}, Brand=${candidate.brand}, Sig=$sigDetails")

        val encodeResult = IrProtocol.encode(signal)
        if (encodeResult is com.elysium.nexus.fabric.infrared.EncodeResult.Success) {
            scope.launch {
                val res = irTransmitter.transmit(encodeResult.waveform)
                Log.d(TAG, "Transmit result for Probe #${engine.currentProbeNumber}: $res")
                currentResult = res
            }
        } else {
            currentResult = IrTransmitResult.InvalidPattern("Unsupported protocol or invalid parameters")
        }
    }

    fun buildAndPersistInstalledProfile(winnerCandidate: IrCodeSet): InstalledIrProfile {
        val bindings = mutableMapOf<IrAction, IrCommandBinding>()
        
        for ((action, signal) in winnerCandidate.commands) {
            val fp = IrProbeEngine.fingerprintSignal(signal)
            bindings[action] = IrCommandBinding(
                signalId = "${winnerCandidate.id}_${action.name}",
                physicalFingerprint = fp,
                sourceId = winnerCandidate.provenance.sourceName,
                action = action
            )
        }

        val profile = InstalledIrProfile(
            displayName = "${winnerCandidate.brand} Remote (${winnerCandidate.id.take(8)})",
            brand = winnerCandidate.brand,
            deviceType = "TV",
            model = winnerCandidate.modelPatterns.firstOrNull(),
            remoteModel = winnerCandidate.remoteModels.firstOrNull(),
            codeSetId = winnerCandidate.id,
            sourceRevision = "v0.3.0",
            commands = bindings,
            verifiedActions = setOf(IrAction.VOLUME_UP),
            verificationStatus = VerificationStatus.PARTIALLY_VERIFIED
        )

        val profileRepo = InstalledIrProfileRepository(context)
        profileRepo.saveProfile(profile)
        Log.d(TAG, "Successfully installed and saved winner profile ID=${profile.id} with ${bindings.size} bindings to disk")
        return profile
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar
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
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Hero Card
            NeonHeroCard(
                title = "${template.brand} ${template.model}",
                subtitle = template.blurbEs,
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "Paso ${step.number} de 4",
                        color = ElysiumColors.NeonOrange
                    )
                    activeEngine?.let { engine ->
                        NeonStatusPill(
                            label = "Probe ${engine.currentProbeNumber}/${engine.totalCandidates}",
                            color = ElysiumColors.NeonPurple
                        )
                    }
                }
            )

            // Step Indicator
            StepIndicator(
                currentStep = step,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp)
            )

            // Step Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding)
            ) {
                when (val uiState = probeUiState) {
                    is ProbeUiState.LoadingCatalog -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ElysiumColors.NeonCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Cargando catálogo SQLite desde assets...",
                                    style = TextStyle(fontSize = 14.sp, color = ElysiumColors.OnSurfaceVariant)
                                )
                            }
                        }
                    }
                    is ProbeUiState.Ready -> {
                        val engine = uiState.probeEngine
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = {
                                (fadeIn() + scaleIn(initialScale = 0.95f))
                                    .togetherWith(fadeOut() + scaleOut(targetScale = 0.95f))
                            },
                            label = "ir_step"
                        ) { currentStep ->
                            when (currentStep) {
                                IrStep.ORIENT -> OrientStep(
                                    onContinue = {
                                        step = IrStep.TEST
                                        sendVolumeTestForCurrentCandidate()
                                    },
                                    hasIrBlaster = hasIrBlaster
                                )
                                IrStep.TEST -> TestStep(
                                    template = template,
                                    probeEngine = engine,
                                    lastResult = currentResult,
                                    onSendTest = { sendVolumeTestForCurrentCandidate() },
                                    onDidWork = { step = IrStep.CONFIRM },
                                    onNextCandidate = {
                                        engine.nextCandidate()
                                        currentResult = null
                                        sendVolumeTestForCurrentCandidate()
                                    },
                                    hasIrBlaster = hasIrBlaster
                                )
                                IrStep.CONFIRM -> ConfirmStep(
                                    onYes = {
                                        step = IrStep.SAVE
                                    },
                                    onNo = {
                                        engine.nextCandidate()
                                        currentResult = null
                                        step = IrStep.TEST
                                        sendVolumeTestForCurrentCandidate()
                                    }
                                )
                                IrStep.SAVE -> SaveStep(
                                    template = template,
                                    onSave = {
                                        val winner = engine.currentCandidate()
                                        if (winner != null) {
                                            val profile = buildAndPersistInstalledProfile(winner)
                                            onProfileInstalled(profile)
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
                        Text("No se encontraron más candidatos para esta marca.", color = ElysiumColors.NeonOrange)
                    }
                    is ProbeUiState.Error -> {
                        Text("Error de catálogo: ${uiState.message}", color = Color.Red)
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Probar ${template.brand}",
            whatIsThis = "Esta pantalla busca un perfil de control IR probando el comando de Subir Volumen (Volume Up).",
            howToUse = listOf(
                "Paso 1: Asegúrate de que la TV esté encendida con volumen perceptible.",
                "Paso 2: Apunta la parte superior del teléfono a la TV. La señal se transmite automáticamente al cambiar de código.",
                "Paso 3: Si aparece el aviso de volumen en pantalla, toca 'Sí'. Si no, toca 'Probar siguiente'."
            ),
            tip = "El sistema probará candidatos distintos automáticamente sin repetir señales fallidas.",
            onDismiss = { showHelp = false }
        )
    }
}

private enum class IrStep(val number: Int, val labelEn: String, val labelEs: String) {
    ORIENT(1, "Aim", "Apuntar"),
    TEST(2, "Test", "Probar"),
    CONFIRM(3, "Confirm", "Confirmar"),
    SAVE(4, "Save", "Guardar")
}

@Composable
private fun StepIndicator(
    currentStep: IrStep,
    modifier: Modifier = Modifier
) {
    val steps = IrStep.entries.toTypedArray()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { idx, s ->
            val isActive = s == currentStep
            val isPassed = s.number < currentStep.number
            val color = when {
                isActive -> ElysiumColors.NeonCyan
                isPassed -> ElysiumColors.NeonGreen
                else -> ElysiumColors.OnSurfaceVariant.copy(alpha = 0.4f)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${s.number}",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = color
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = s.labelEs,
                    style = TextStyle(fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal),
                    color = color
                )
            }

            if (idx < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .background(if (isPassed) ElysiumColors.NeonGreen else ElysiumColors.OnSurfaceVariant.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun OrientStep(
    onContinue: () -> Unit,
    hasIrBlaster: Boolean
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonCyan,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Preparación de Sondeo IR",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = if (hasIrBlaster)
                    "El emisor Infrarrojo de tu teléfono enviará códigos de prueba. Mantén el teléfono apuntado al sensor IR del equipo."
                else
                    "Atención: Este teléfono no reporta emisor IR de hardware. Puedes probar con un receptor external USB.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(
                label = "Comenzar Prueba",
                onClick = onContinue,
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TestStep(
    template: DeviceTemplate,
    probeEngine: IrProbeEngine,
    lastResult: IrTransmitResult?,
    onSendTest: () -> Unit,
    onDidWork: () -> Unit,
    onNextCandidate: () -> Unit,
    hasIrBlaster: Boolean
) {
    val currentCand = probeEngine.currentCandidate()

    // P0 Issue 3 Fix: Enable confirmation ONLY when transmission actually succeeded!
    val canConfirm = lastResult is IrTransmitResult.Success && currentCand != null

    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonPurple,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Prueba de Volumen — Candidato ${probeEngine.currentProbeNumber} de ${probeEngine.totalCandidates}",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Perfil: ${currentCand?.brand ?: template.brand} (${currentCand?.id ?: template.id})\n" +
                    "Acción: SUBIR VOLUMEN (VOLUME_UP)\n" +
                    "Estado Verificación: ${currentCand?.verification ?: VerificationStatus.UNVERIFIED}",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = ElysiumColors.OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                NeonFab(
                    icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) },
                    onClick = onSendTest,
                    accent = ElysiumColors.NeonCyan,
                    fabSize = 80.dp
                )
            }

            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Transmitido: ${res.carrierHz} Hz (${res.durationUs} µs)"
                    is IrTransmitResult.NoEmitter -> "No hay emisor IR disponible"
                    is IrTransmitResult.PermissionDenied -> "Permiso TRANSMIT_IR denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia ${res.requestedHz} Hz no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido: ${res.reason}"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android: ${res.cause.message}"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange

                NeonStatusPill(label = resultText, color = color)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonChip(
                    label = "Sí, subió el volumen",
                    onClick = { if (canConfirm) onDidWork() },
                    accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray,
                    active = canConfirm,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )

                if (probeEngine.hasMore) {
                    NeonChip(
                        label = "No / Probar siguiente",
                        onClick = onNextCandidate,
                        accent = ElysiumColors.NeonOrange,
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonGreen,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "¿Reaccionó tu TV?",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Si viste el indicador de volumen subir en pantalla, confirma para instalar el perfil de control completo.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(
                    label = "Sí, guardar perfil",
                    onClick = onYes,
                    accent = ElysiumColors.NeonGreen,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                NeonChip(
                    label = "No, probar otro",
                    onClick = onNo,
                    accent = ElysiumColors.NeonOrange,
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SaveStep(
    template: DeviceTemplate,
    onSave: () -> Unit,
    onLearnInstead: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonGreen,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Perfil Encontrado y Verificado",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "El mapa de comandos del código ganador se ha guardado de forma persistente. Todos los botones utilizarán las señales de este perfil.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(
                label = "Abrir Control Remoto",
                onClick = onSave,
                accent = ElysiumColors.NeonGreen,
                icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
