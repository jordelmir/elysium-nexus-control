package com.elysium.nexus.ui.control

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceButton
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.EncodeResult
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvControlScreen(
    template: DeviceTemplate,
    onBack: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasEmitter: Boolean,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var transmitStatusText by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier.fillMaxSize()
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
                if (transmitStatusText != null) {
                    NeonStatusPill(
                        label = transmitStatusText!!,
                        color = if (isStatusError) ElysiumColors.NeonOrange else ElysiumColors.NeonGreen
                    )
                }
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
                title = template.brand,
                subtitle = template.model,
                accent = ElysiumColors.NeonGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "Perfil Activo",
                        color = ElysiumColors.NeonCyan
                    )
                    if (hasEmitter) {
                        NeonStatusPill(
                            label = "IR listo",
                            color = ElysiumColors.NeonGreen
                        )
                    } else {
                        NeonStatusPill(
                            label = "Sin emisor",
                            color = ElysiumColors.NeonOrange
                        )
                    }
                }
            )

            // === TIP STRIP =========================================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonOrange,
                cornerRadius = 12.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = ElysiumColors.NeonOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Apunta el emisor superior al ${template.brand} para enviar cada comando.",
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                        color = ElysiumColors.OnSurface
                    )
                }
            }

            // === BUTTON GRID =======================================
            val columns = when (info.size) {
                com.elysium.nexus.ui.responsive.ScreenSize.Compact -> 4
                com.elysium.nexus.ui.responsive.ScreenSize.Medium -> 5
                com.elysium.nexus.ui.responsive.ScreenSize.Expanded -> 6
                com.elysium.nexus.ui.responsive.ScreenSize.Large -> 7
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
            ) {
                items(template.buttons) { button ->
                    ControlButton(
                        button = button,
                        onClick = {
                            scope.launch {
                                val result = sendButtonCommand(irTransmitter, template, button)
                                when (result) {
                                    is IrTransmitResult.Success -> {
                                        isStatusError = false
                                        transmitStatusText = "Transmitido: ${button.labelEs}"
                                    }
                                    is IrTransmitResult.NoEmitter -> {
                                        isStatusError = true
                                        transmitStatusText = "Sin emisor IR"
                                    }
                                    is IrTransmitResult.PermissionDenied -> {
                                        isStatusError = true
                                        transmitStatusText = "Permiso denegado"
                                    }
                                    is IrTransmitResult.UnsupportedCarrier -> {
                                        isStatusError = true
                                        transmitStatusText = "Frecuencia no soportada"
                                    }
                                    is IrTransmitResult.InvalidPattern -> {
                                        isStatusError = true
                                        transmitStatusText = "Patrón inválido: ${result.reason}"
                                    }
                                    is IrTransmitResult.Busy -> {
                                        isStatusError = true
                                        transmitStatusText = "Emisor ocupado"
                                    }
                                    is IrTransmitResult.PlatformFailure -> {
                                        isStatusError = true
                                        transmitStatusText = "Error hardware: ${result.cause.message}"
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(transmitStatusText) {
        if (transmitStatusText != null) {
            delay(2000)
            transmitStatusText = null
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Control de ${template.brand}",
            whatIsThis = "Superficie de control remoto IR. Cada botón emite la forma de onda del protocolo configurado.",
            howToUse = listOf(
                "Apunta la parte superior del teléfono al dispositivo.",
                "Toca cualquier botón para transmitir la señal.",
                "Verás el estado de la transmisión en la parte superior."
            ),
            tip = "Mantén una línea directa de visión a menos de 3 metros.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun ControlButton(
    button: DeviceButton,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "button_scale"
    )
    val accent = when (button.iconHint) {
        "power" -> ElysiumColors.NeonMagenta
        "vol_up", "ch_up" -> ElysiumColors.NeonGreen
        "vol_down", "ch_down" -> ElysiumColors.NeonOrange
        "mute" -> ElysiumColors.NeonOrange
        "ok" -> ElysiumColors.NeonCyan
        "up", "down", "left", "right" -> ElysiumColors.NeonPurple
        else -> ElysiumColors.NeonCyan
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatioForButton(button)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElysiumColors.SurfaceHigh,
                        ElysiumColors.Surface
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = iconForButton(button.iconHint),
                contentDescription = button.labelEs,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = button.labelEs,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.OnSurface,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(accent)
        )
    }
}

private fun Modifier.aspectRatioForButton(button: DeviceButton): Modifier {
    val ratio = if (button.layoutWeight >= 2) 1f else 1.4f
    return this.then(Modifier.aspectRatio(ratio))
}

private fun iconForButton(hint: String): ImageVector = when (hint) {
    "power" -> Icons.Filled.PowerSettingsNew
    "input" -> Icons.Filled.Settings
    "vol_up" -> Icons.Filled.VolumeUp
    "vol_down" -> Icons.Filled.VolumeDown
    "mute" -> Icons.Filled.VolumeOff
    "ch_up" -> Icons.Filled.ArrowUpward
    "ch_down" -> Icons.Filled.ArrowDownward
    "up" -> Icons.Filled.ArrowUpward
    "down" -> Icons.Filled.ArrowDownward
    "left" -> Icons.Filled.ChevronLeft
    "right" -> Icons.Filled.ChevronRight
    "ok" -> Icons.Filled.PlayArrow
    "menu" -> Icons.Filled.Menu
    "back" -> Icons.Filled.Close
    "info" -> Icons.Filled.Info
    "last" -> Icons.Filled.Refresh
    "home" -> Icons.Filled.Home
    "netflix", "youtube", "prime" -> Icons.Filled.LiveTv
    "play" -> Icons.Filled.PlayArrow
    "num_1", "num_2", "num_3", "num_4", "num_5" -> Icons.Filled.Numbers
    "num_6", "num_7", "num_8", "num_9", "num_0" -> Icons.Filled.Numbers
    "minus" -> Icons.Filled.FastRewind
    "plus" -> Icons.Filled.FastForward
    "cross" -> Icons.Filled.Close
    "circle" -> Icons.Filled.Refresh
    "square" -> Icons.Filled.Stop
    "triangle" -> Icons.Filled.PlayArrow
    "l1", "r1", "l2", "r2" -> Icons.Filled.Keyboard
    "select", "start" -> Icons.Filled.Menu
    else -> Icons.Filled.Keyboard
}

private suspend fun sendButtonCommand(
    transmitter: AndroidIrTransmitter,
    template: DeviceTemplate,
    button: DeviceButton
): IrTransmitResult {
    val signal = IrSignal.Encoded(
        carrierHz = template.protocol.carrierHz,
        protocol = template.protocol,
        address = template.deviceAddress,
        command = button.commandCode
    )
    val encodeResult = IrProtocol.encode(signal)
    return if (encodeResult is EncodeResult.Success) {
        transmitter.transmit(encodeResult.waveform)
    } else {
        IrTransmitResult.InvalidPattern("Failed to encode protocol ${template.protocol}")
    }
}
