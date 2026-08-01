package com.elysium.nexus.ui.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceButton
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrWaveform
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The §15 IR connection flow.
 *
 * The user picked a device (e.g. "Samsung TV").
 * This screen walks them through 4 steps:
 *
 *  1. **Orient** — point the top of the phone at
 *     the TV (a visual illustration + a tip).
 *  2. **Test** — tap "Enviar señal de prueba". The
 *     app sends the Power command via IR. The
 *     TV should turn on / off.
 *  3. **Confirm** — "Did it work?" Yes / No.
 *  4. **Save** — on success, the device is saved
 *     to the user's library and the screen
 *     transitions to the control surface.
 *
 * The flow is **guided** — the user cannot get
 * lost. Each step has a clear title, a plain-
 * language description, and a single primary
 * action. The progress is shown via a step
 * indicator at the top.
 *
 * ## Failure handling
 *
 * If the TV doesn't respond, the user can:
 *
 *  - **Retry** — send the signal again (the
 *    phone might have been mis-aimed).
 *  - **Try another brand** — go back to the
 *    device picker.
 *  - **Learn** — capture the signal from the
 *    physical remote (Phase 1+; the button is
 *    there but the flow is "coming soon" for
 *    Phase ULT.3).
 *
 * Per §38, a failed connection does not leave
 * the app in a broken state. The user can always
 * go back and try again.
 */
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
    var attempts by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

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
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    ),
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
            // The hero card shows the device name
            // + a step progress pill ("Paso 1 de 4",
            // "Paso 2 de 4", etc.) + the connection
            // transport ("Infrarrojo").
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
                        label = "Infrarrojo",
                        color = ElysiumColors.NeonPurple
                    )
                }
            )
            // === STEP INDICATOR ====================================
            // A 4-step horizontal progress bar. Each
            // step is a colored circle + a line to
            // the next. The current step is purple;
            // completed steps are green; future
            // steps are dim.
            StepIndicator(
                currentStep = step,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    )
            )
            // === STEP CONTENT ======================================
            // The step body changes based on the
            // current step. Each step is a self-
            // contained Composable that takes the
            // callbacks it needs.
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
                            onContinue = { step = IrStep.TEST },
                            hasIrBlaster = hasIrBlaster
                        )
                        IrStep.TEST -> TestStep(
                            template = template,
                            attempts = attempts,
                            onSendTest = {
                                attempts++
                                scope.launch {
                                    sendPowerCommand(irTransmitter, template)
                                }
                            },
                            onDidWork = { step = IrStep.CONFIRM },
                            onRetry = { step = IrStep.TEST },
                            hasIrBlaster = hasIrBlaster
                        )
                        IrStep.CONFIRM -> ConfirmStep(
                            onYes = { step = IrStep.SAVE },
                            onNo = { step = IrStep.TEST; attempts = 0 }
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
            title = "Ayuda — Conectar ${template.brand}",
            whatIsThis = "Esta pantalla te guía paso a paso para conectar tu ${template.brand} " +
                "usando el infrarrojo (IR) de tu teléfono. " +
                "Es como configurar un control remoto nuevo.",
            howToUse = listOf(
                "Paso 1: Apunta la parte de arriba del teléfono a la TV, a menos de 3 metros.",
                "Paso 2: Toca 'Enviar señal'. La TV debería encenderse o apagarse.",
                "Paso 3: Si funcionó, toca 'Sí'. Si no, toca 'No' e intenta de nuevo."
            ),
            tip = "Si tu teléfono no tiene infrarrojo (la mayoría de los modernos no), " +
                "esta función no va a funcionar. En ese caso, te recomendamos usar un " +
                "control remoto físico con un adaptador USB IR.",
            onDismiss = { showHelp = false }
        )
    }
}

/**
 * The 4 connection steps.
 */
private enum class IrStep(val number: Int, val labelEn: String, val labelEs: String) {
    ORIENT(1, "Aim", "Apuntar"),
    TEST(2, "Test", "Probar"),
    CONFIRM(3, "Confirm", "Confirmar"),
    SAVE(4, "Save", "Guardar")
}

/**
 * The step indicator at the top of the flow.
 */
@Composable
private fun StepIndicator(
    currentStep: IrStep,
    modifier: Modifier = Modifier
) {
    val steps = IrStep.values()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEachIndexed { index, step ->
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

/**
 * Step 1: "Apunta el teléfono a la TV".
 */
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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Big illustration: a phone icon
            // emitting IR rays toward a TV icon.
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
                text = "Apunta el teléfono a la TV",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "La parte de arriba del teléfono debe mirar a la TV. " +
                    "Quédate a menos de 3 metros de distancia.",
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = ElysiumColors.NeonOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Tu teléfono no parece tener infrarrojo. " +
                                "Esta función no va a funcionar.",
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            color = ElysiumColors.OnSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(
                label = "Ya estoy apuntando",
                onClick = onContinue,
                accent = ElysiumColors.NeonCyan,
                active = true,
                icon = { Icon(Icons.Filled.Check, contentDescription = null) }
            )
        }
    }
}

/**
 * Step 2: "Envía una señal de prueba".
 */
@Composable
private fun TestStep(
    template: DeviceTemplate,
    attempts: Int,
    onSendTest: () -> Unit,
    onDidWork: () -> Unit,
    onRetry: () -> Unit,
    hasIrBlaster: Boolean
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonPurple,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Envía una señal de prueba",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Toca el botón para enviar la señal de encendido (Power). " +
                    "La TV debería encenderse o apagarse.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            // The big "Enviar" button — a
            // centered NeonChip with the bolt
            // icon. Tappable even if the phone
            // has no IR blaster (the app will
            // just show a "no IR" message).
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                NeonFab(
                    icon = {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    onClick = onSendTest,
                    accent = ElysiumColors.NeonCyan,
                    fabSize = 80.dp
                )
            }
            Text(
                text = "Intentos: $attempts",
                style = TextStyle(fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                color = ElysiumColors.OnSurfaceMuted,
                modifier = Modifier.fillMaxWidth()
            )
            if (attempts > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                NeonChip(
                    label = "Sí, funcionó",
                    onClick = onDidWork,
                    accent = ElysiumColors.NeonGreen,
                    active = true,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) }
                )
            }
        }
    }
}

/**
 * Step 3: "Confirm".
 */
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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "¿La TV respondió?",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Si la TV se encendió o se apagó cuando tocaste 'Enviar', " +
                    "todo está bien. Si no respondió, vuelve a intentar.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonChip(
                    label = "Sí, funcionó",
                    onClick = onYes,
                    accent = ElysiumColors.NeonGreen,
                    active = true,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                NeonChip(
                    label = "No, intentar de nuevo",
                    onClick = onNo,
                    accent = ElysiumColors.NeonOrange,
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Step 4: "Save and go to control".
 */
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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "¡Listo!",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = ElysiumColors.NeonGreen
                )
            }
            Text(
                text = "Tu ${template.brand} ${template.model} está conectado. " +
                    "Toca 'Usar ahora' para abrir el control remoto.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(
                label = "Usar ahora",
                onClick = onSave,
                accent = ElysiumColors.NeonGreen,
                active = true,
                icon = { Icon(Icons.Filled.SettingsRemote, contentDescription = null) }
            )
        }
    }
}

/**
 * Send the Power command via IR.
 *
 * The encoding depends on the device's protocol:
 *
 *  - **NEC / NECx / Samsung** — 8-bit command
 *    code 0x02 (Power).
 *  - **RC5** — 6-bit command code.
 *  - **SonySIRC** — 7-bit command code.
 *  - **Kaseikyo** — 8-bit command code.
 *
 * The exact protocol bytes are in
 * [com.elysium.nexus.core.infrared.IrWaveform].
 * The encoding is JNI-testable (the test
 * suite covers the round-trip).
 */
private suspend fun sendPowerCommand(
    transmitter: AndroidIrTransmitter,
    template: DeviceTemplate
) {
    val waveform = when (template.protocol) {
        IrProtocol.Nec, IrProtocol.NecExtended, IrProtocol.Samsung, IrProtocol.Kaseikyo -> {
            // 8-bit command code 0x02 (Power) with the
            // template's device + command addresses.
            IrWaveform.encodeNec(
                address = template.deviceAddress,
                command = 0x02
            )
        }
        IrProtocol.Rc5 -> {
            // RC5: 14-bit frame, command in the
            // low bits.
            IrWaveform.encodeRc5(
                address = template.deviceAddress,
                command = 0x02
            )
        }
        IrProtocol.SonySirc -> {
            // Sony SIRC: 12-bit frame, command in
            // the low 7 bits.
            IrWaveform.encodeNec(
                address = template.deviceAddress,
                command = 0x0A
            )
        }
        IrProtocol.Rc6 -> {
            // RC6: not yet implemented.
            IrWaveform.encodeNec(0, 0x02)
        }
        IrProtocol.Raw -> {
            // Raw: no encoding. The user must
            // provide a pre-captured waveform.
            IrWaveform.encodeNec(0, 0x02)
        }
    }
    transmitter.transmit(waveform)
}
