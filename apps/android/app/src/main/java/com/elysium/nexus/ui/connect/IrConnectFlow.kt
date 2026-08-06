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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrTransmitResult
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

@Composable
fun IrConnectFlow(
    template: DeviceTemplate,
    onBack: () -> Unit,
    onConnected: (DeviceTemplate) -> Unit,
    onTryOther: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasIrBlaster: Boolean,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(IrStep.ORIENT) }
    var showHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Mutable state for the active IrProbeEngine, populated directly from SQLite ir_catalog.db
    var probeEngine by remember(template) {
        val matchedTemplates = DeviceCatalog.all.filter {
            it.brand.equals(template.brand, ignoreCase = true) ||
            it.id == template.id ||
            template.brand.contains("Universal", ignoreCase = true)
        }

        val initialList = matchedTemplates.mapIndexed { idx, t ->
            IrCodeSet(
                id = "cand-${t.id}-$idx",
                brand = t.brand,
                modelPatterns = setOf(t.model),
                remoteModels = emptySet(),
                commands = mapOf(
                    IrAction.VOLUME_UP to IrSignal.Encoded(
                        carrierHz = t.protocol.carrierHz,
                        protocol = t.protocol,
                        address = t.deviceAddress,
                        command = if (t.commandAddress != 0) t.commandAddress else 0x07
                    )
                ),
                provenance = CodeProvenance(
                    sourceName = "Elysium Device Catalog",
                    sourceUrl = "https://github.com/jordelmir/elysium-nexus-control",
                    licenseSpdx = "MIT"
                ),
                verification = VerificationStatus.UNVERIFIED
            )
        }
        mutableStateOf(IrProbeEngine(initialList))
    }

    // Load candidates directly from SQLite ir_catalog.db on launch
    androidx.compose.runtime.LaunchedEffect(template) {
        val repo = com.elysium.nexus.fabric.infrared.database.IrCatalogRepository(context)
        val sqliteCandidates = repo.getCandidatesForBrand(
            brand = template.brand,
            deviceType = "TV",
            action = IrAction.VOLUME_UP
        )
        if (sqliteCandidates.isNotEmpty()) {
            val engineFromSqlite = IrProbeEngine(sqliteCandidates)
            probeEngine = engineFromSqlite
            Log.d(TAG, "Loaded ${engineFromSqlite.totalCandidates} candidates directly from SQLite ir_catalog.db for brand=${template.brand}")
        }
    }

    var currentResult by remember { mutableStateOf<IrTransmitResult?>(null) }
    var candidateIndex by remember { mutableStateOf(probeEngine.currentProbeNumber) }

    // Sync candidateIndex when probeEngine changes
    androidx.compose.runtime.LaunchedEffect(probeEngine) {
        candidateIndex = probeEngine.currentProbeNumber
    }

    // Helper to transmit the current candidate's VOLUME_UP signal automatically
    fun sendVolumeTestForCurrentCandidate() {
        val candidate = probeEngine.currentCandidate() ?: return
        val signal = candidate.commands[IrAction.VOLUME_UP] ?: return
        val sigDetails = IrProbeEngine.fingerprintSignal(signal)
        Log.d(TAG, "Transmitting Probe #${probeEngine.currentProbeNumber}/${probeEngine.totalCandidates}: ID=${candidate.id}, Brand=${candidate.brand}, Sig=$sigDetails")

        val encodeResult = IrProtocol.encode(signal)
        if (encodeResult is com.elysium.nexus.fabric.infrared.EncodeResult.Success) {
            scope.launch {
                val res = irTransmitter.transmit(encodeResult.waveform)
                Log.d(TAG, "Transmit result for Probe #${probeEngine.currentProbeNumber}: $res")
                currentResult = res
            }
        } else {
            currentResult = IrTransmitResult.InvalidPattern("Unsupported protocol or bad params")
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===========================================
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

            // === HERO CARD =========================================
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
                    NeonStatusPill(
                        label = "Probe ${candidateIndex}/${probeEngine.totalCandidates}",
                        color = ElysiumColors.NeonPurple
                    )
                }
            )

            // === STEP INDICATOR ====================================
            StepIndicator(
                currentStep = step,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp)
            )

            // === STEP CONTENT ======================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding)
            ) {
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
                            probeEngine = probeEngine,
                            lastResult = currentResult,
                            onSendTest = {
                                sendVolumeTestForCurrentCandidate()
                            },
                            onDidWork = { step = IrStep.CONFIRM },
                            onNextCandidate = {
                                val next = probeEngine.nextCandidate()
                                candidateIndex = probeEngine.currentProbeNumber
                                currentResult = null
                                Log.d(TAG, "Advanced to Candidate #${candidateIndex}: ID=${next?.id}, Brand=${next?.brand}")
                                sendVolumeTestForCurrentCandidate()
                            },
                            hasIrBlaster = hasIrBlaster
                        )
                        IrStep.CONFIRM -> ConfirmStep(
                            onYes = { step = IrStep.SAVE },
                            onNo = {
                                val next = probeEngine.nextCandidate()
                                candidateIndex = probeEngine.currentProbeNumber
                                currentResult = null
                                Log.d(TAG, "Advanced via ConfirmStep to Candidate #${candidateIndex}: ID=${next?.id}")
                                step = IrStep.TEST
                                sendVolumeTestForCurrentCandidate()
                            }
                        )
                        IrStep.SAVE -> SaveStep(
                            template = template,
                            onSave = { onConnected(template) },
                            onLearnInstead = onTryOther
                        )
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEach { step ->
            val color = when {
                step.ordinal < currentStep.ordinal -> ElysiumColors.NeonGreen
                step == currentStep -> ElysiumColors.NeonPurple
                else -> ElysiumColors.OnSurfaceMuted.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("📱", style = TextStyle(fontSize = 56.sp))
                Text("➤➤➤", style = TextStyle(fontSize = 24.sp), color = ElysiumColors.NeonCyan)
                Text("📺", style = TextStyle(fontSize = 56.sp))
            }
            Text(
                text = "Apunta el teléfono a la TV encendida",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Para probar códigos de forma confiable, la TV debe estar encendida. " +
                    "Probaremos la acción SUBIR VOLUMEN (Volume Up) para ver la confirmación OSD en pantalla.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            if (!hasIrBlaster) {
                Spacer(modifier = Modifier.height(8.dp))
                NeonCard(
                    accent = ElysiumColors.NeonOrange,
                    cornerRadius = 12.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = ElysiumColors.NeonOrange, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Hardware IR no detectado en este teléfono.",
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            color = ElysiumColors.OnSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(
                label = "Comenzar pruebas de volumen",
                onClick = onContinue,
                accent = ElysiumColors.NeonCyan,
                active = true,
                icon = { Icon(Icons.Filled.Check, contentDescription = null) }
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
                    onClick = onDidWork,
                    accent = ElysiumColors.NeonGreen,
                    active = true,
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
        accent = ElysiumColors.NeonCyan,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "¿Confirmar control remoto?",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Si la TV reaccionó al cambio de volumen, guarda este perfil. Si no, avanza al siguiente candidato.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonChip(
                    label = "Guardar Perfil",
                    onClick = onYes,
                    accent = ElysiumColors.NeonGreen,
                    active = true,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                NeonChip(
                    label = "Probar Otro",
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ElysiumColors.NeonGreen, modifier = Modifier.size(28.dp))
                Text(
                    text = "¡Perfil Seleccionado!",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                    color = ElysiumColors.NeonGreen
                )
            }
            Text(
                text = "El perfil para ${template.brand} ${template.model} ha sido verificado en esta sesión.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(
                label = "Abrir Control Remoto",
                onClick = onSave,
                accent = ElysiumColors.NeonGreen,
                active = true,
                icon = { Icon(Icons.Filled.SettingsRemote, contentDescription = null) }
            )
        }
    }
}
