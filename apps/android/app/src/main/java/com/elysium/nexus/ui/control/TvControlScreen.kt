package com.elysium.nexus.ui.control

import android.util.Log
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceButton
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.EncodeResult
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
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ElysiumNexus.TvControlScreen"

/**
 * Production TvControlScreen driven strictly by [InstalledIrProfile].
 *
 * Resolves physical signals from the winner profile's command bindings map.
 * DeviceTemplate is used ONLY for visual layout buttons. Physical codes are NEVER
 * derived from DeviceTemplate.
 */
@Composable
fun TvControlScreen(
    template: DeviceTemplate,
    profile: InstalledIrProfile? = null,
    profileId: String? = null,
    onBack: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasEmitter: Boolean,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var transmitStatusText by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeProfile = remember(profile, profileId) {
        profile ?: profileId?.let { id -> InstalledIrProfileRepository(context).getProfile(id) }
    }

    // Singleton catalog repository — ONE instance, not per-button
    val catalogRepo = remember { IrCatalogRepository(context) }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier.fillMaxSize()
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
                title = activeProfile?.brand ?: template.brand,
                subtitle = activeProfile?.displayName ?: template.model,
                accent = ElysiumColors.NeonGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = if (activeProfile != null) "Perfil Instalado (Room DB)" else "Perfil Temporal",
                        color = if (activeProfile != null) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
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
                        text = "Apunta el emisor superior al ${activeProfile?.brand ?: template.brand} para enviar cada comando del perfil.",
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

            // Generate buttons from profile bindings when available,
            // fall back to template buttons for visual layout only
            val effectiveButtons = remember(activeProfile, template) {
                if (activeProfile != null && activeProfile.commands.isNotEmpty()) {
                    // Build buttons from the profile's real bindings
                    activeProfile.commands.keys.mapNotNull { action ->
                        template.buttons.firstOrNull { mapButtonToIrAction(it.id) == action }
                            ?: DeviceButton(
                                id = action.name.lowercase(),
                                labelEs = action.name.replace("_", " "),
                                labelEn = action.name.replace("_", " "),
                                iconHint = action.name.lowercase(),
                                commandCode = 0
                            )
                    }
                } else {
                    template.buttons
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
            ) {
                items(effectiveButtons) { button ->
                    ControlButton(
                        button = button,
                        onClick = {
                            scope.launch {
                                val result = sendProfileCommand(catalogRepo = catalogRepo, transmitter = irTransmitter, profile = activeProfile, template = template, button = button)
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

    if (showHelp) {
        HelpCard(
            title = "Control ${profile?.brand ?: template.brand}",
            whatIsThis = "Superficie de control Infrarroja optimizada driven por perfiles persistentes.",
            howToUse = listOf(
                "Apunta la parte superior del teléfono hacia el receptor IR del equipo.",
                "Toca cualquier botón para enviar la señal correspondiente.",
                "Las señales se ejecutan directamente desde el catálogo SQLite instalado."
            ),
            tip = "Mantén la vista directa sin obstáculos entre el emisor y el sensor del equipo.",
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
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "btn_scale"
    )

    val icon = buttonIcon(button.id)
    val color = when (button.iconHint) {
        "power" -> ElysiumColors.NeonOrange
        "vol_up", "vol_down", "mute" -> ElysiumColors.NeonCyan
        "ch_up", "ch_down" -> ElysiumColors.NeonGreen
        "nav" -> ElysiumColors.NeonPurple
        else -> ElysiumColors.NeonCyan
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.25f),
                        color.copy(alpha = 0.08f)
                    )
                )
            )
            .clickable {
                isPressed = true
                onClick()
                scope.launch {
                    delay(120)
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = button.labelEs,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = button.labelEs,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = ElysiumColors.OnSurface,
                maxLines = 1
            )
        }
    }
}

private fun buttonIcon(buttonId: String): ImageVector = when (buttonId) {
    "power" -> Icons.Filled.PowerSettingsNew
    "vol_up" -> Icons.Filled.VolumeUp
    "vol_down" -> Icons.Filled.VolumeDown
    "mute" -> Icons.Filled.VolumeOff
    "ch_up" -> Icons.Filled.ArrowUpward
    "ch_down" -> Icons.Filled.ArrowDownward
    "up" -> Icons.Filled.ArrowUpward
    "down" -> Icons.Filled.ArrowDownward
    "left" -> Icons.Filled.ChevronLeft
    "right" -> Icons.Filled.ChevronRight
    "ok" -> Icons.Filled.Check
    "menu" -> Icons.Filled.Menu
    "source", "input" -> Icons.Filled.Settings
    "back" -> Icons.Filled.ArrowBack
    "info" -> Icons.Filled.Info
    "guide" -> Icons.Filled.Menu
    "exit" -> Icons.Filled.Close
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

private fun mapButtonToIrAction(buttonId: String): IrAction? = when (buttonId) {
    "power" -> IrAction.POWER_TOGGLE
    "vol_up" -> IrAction.VOLUME_UP
    "vol_down" -> IrAction.VOLUME_DOWN
    "mute" -> IrAction.MUTE
    "ch_up" -> IrAction.CHANNEL_UP
    "ch_down" -> IrAction.CHANNEL_DOWN
    "up" -> IrAction.UP
    "down" -> IrAction.DOWN
    "left" -> IrAction.LEFT
    "right" -> IrAction.RIGHT
    "ok" -> IrAction.OK
    "menu" -> IrAction.MENU
    "home" -> IrAction.HOME
    "back" -> IrAction.BACK
    "play" -> IrAction.PLAY
    "pause" -> IrAction.PAUSE
    "stop" -> IrAction.STOP
    "source", "input" -> IrAction.INPUT
    else -> null
}

private suspend fun sendProfileCommand(
    catalogRepo: IrCatalogRepository,
    transmitter: AndroidIrTransmitter,
    profile: InstalledIrProfile?,
    template: DeviceTemplate,
    button: DeviceButton
): IrTransmitResult {
    val action = mapButtonToIrAction(button.id)
    if (profile == null || action == null) {
        return IrTransmitResult.InvalidPattern("Action ${button.labelEs} not configured in profile")
    }

    val binding = profile.commands[action]
        ?: return IrTransmitResult.InvalidPattern("Action $action not mapped for profile ${profile.id}")

    val signal = catalogRepo.getSignal(binding.signalId)
        ?: return IrTransmitResult.InvalidPattern("Signal ${binding.signalId} missing from SQLite catalog")

    // §21 Fingerprint verification: ensure the signal hasn't changed since installation
    val actualFingerprint = IrProbeEngine.fingerprintSignal(signal)
    if (actualFingerprint != binding.physicalFingerprint) {
        Log.e(TAG, "FINGERPRINT MISMATCH for action=$action, signalId=${binding.signalId}: " +
            "expected=${binding.physicalFingerprint}, actual=$actualFingerprint. " +
            "Catalog may have been updated. Refusing to transmit.")
        return IrTransmitResult.InvalidPattern(
            "Fingerprint mismatch for $action — profile may need reinstallation"
        )
    }

    val encodeResult = IrProtocol.encode(signal)
    return if (encodeResult is EncodeResult.Success) {
        Log.d(TAG, "Transmitting Authoritative Profile Signal action=$action, signalId=${binding.signalId}, " +
            "codeSetId=${profile.codeSetId}, fingerprint=${actualFingerprint.take(16)}...")
        transmitter.transmit(encodeResult.waveform)
    } else {
        IrTransmitResult.InvalidPattern("Failed to encode signal for action $action")
    }
}
