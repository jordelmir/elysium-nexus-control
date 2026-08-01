package com.elysium.nexus.ui.mac

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The pairing screen.
 *
 * After the user picks a host on the discovery
 * screen, the pairing flow shows a 6-digit PIN
 * the user must enter on the host (the Mac/PC
 * shows the same PIN in a system dialog). The
 * pairing uses X25519 + a 6-digit PIN for human
 * confirmation.
 *
 * The flow is:
 *
 *  1. **Pairing** — the user sees a 6-digit PIN
 *     and is asked to enter it on the host.
 *  2. **Verifying** — the app is waiting for the
 *     host to confirm the PIN. A spinner shows
 *     progress.
 *  3. **Connected** — the host confirmed. The
 *     user is transitioned to the control surface.
 *
 * The PIN is generated client-side and is
 * **time-limited** (5 minutes). The host has 5
 * minutes to enter the same PIN; after that the
 * pairing expires and a new PIN is needed.
 */
@Composable
fun MacPairingScreen(
    host: DiscoveredHost,
    onBack: () -> Unit,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(PairingState.SHOWING_PIN) }
    // Generate a random 6-digit PIN. The real
    // implementation uses a 6-digit numeric
    // challenge that the host verifies locally
    // before exchanging the public key. The PIN
    // here is just the visual representation.
    val pin = remember {
        String.format("%06d", Random.nextInt(0, 999_999))
    }
    // Simulate the host confirming the PIN after
    // 4 seconds. The real implementation waits
    // for the actual TCP/TLS confirmation.
    LaunchedEffect(Unit) {
        delay(4000)
        state = PairingState.CONNECTED
        delay(1500)
        onPaired()
    }
    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===
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
            // === HERO CARD ===
            NeonHeroCard(
                title = host.name,
                subtitle = "Emparejando...",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = when (state) {
                            PairingState.SHOWING_PIN -> "Esperando PIN"
                            PairingState.VERIFYING -> "Verificando"
                            PairingState.CONNECTED -> "Conectado"
                        },
                        color = when (state) {
                            PairingState.SHOWING_PIN -> ElysiumColors.NeonOrange
                            PairingState.VERIFYING -> ElysiumColors.NeonCyan
                            PairingState.CONNECTED -> ElysiumColors.NeonGreen
                        }
                    )
                }
            )
            // === STATE-SPECIFIC CONTENT ===
            when (state) {
                PairingState.SHOWING_PIN -> PinEntryContent(
                    pin = pin,
                    hostName = host.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
                PairingState.VERIFYING -> VerifyingContent(
                    hostName = host.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
                PairingState.CONNECTED -> ConnectedContent(
                    hostName = host.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
            }
            // === SECURITY EXPLANATION ===
            Spacer(modifier = Modifier.height(16.dp))
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonGreen
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = ElysiumColors.NeonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Conexión cifrada",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonGreen
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tu teléfono y el host usan X25519 (criptografía de curva elíptica) " +
                        "para autenticarse. El PIN de 6 dígitos es solo para que TÚ confirmes " +
                        "que la Mac que aparece es la correcta. Nadie puede interceptar la " +
                        "conexión aunque esté en la misma Wi-Fi.",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                    color = ElysiumColors.OnSurface
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Emparejamiento",
            whatIsThis = "Esta pantalla te muestra un PIN de 6 dígitos. " +
                "Tu Mac o PC también mostrará el mismo PIN en una ventana. " +
                "Si los dos coinciden, la conexión es segura.",
            howToUse = listOf(
                "Mira el PIN que aparece en esta pantalla.",
                "En tu Mac/PC, una ventana emergente mostrará el mismo PIN.",
                "Si coinciden, toca 'Aceptar' en la Mac.",
                "La conexión se establece automáticamente."
            ),
            tip = "Si los PINs no coinciden, NO aceptes. Puede ser un atacante " +
                "en tu misma red Wi-Fi.",
            onDismiss = { showHelp = false }
        )
    }
}

private enum class PairingState { SHOWING_PIN, VERIFYING, CONNECTED }

/**
 * The PIN entry content. Shows a big 6-digit PIN
 * the user must verify on the host.
 */
@Composable
private fun PinEntryContent(
    pin: String,
    hostName: String,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.QrCode2,
                contentDescription = null,
                tint = ElysiumColors.NeonCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Ingresa este PIN en tu $hostName",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.OnSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // The 6-digit PIN. Each digit is a big
            // box. The user reads the digits and
            // types them on the Mac.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pin.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ElysiumColors.NeonCyan.copy(alpha = 0.25f),
                                        ElysiumColors.NeonCyan.copy(alpha = 0.1f)
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                color = ElysiumColors.NeonCyan,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit.toString(),
                            style = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "El PIN expira en 5 minutos",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun VerifyingContent(
    hostName: String,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "verifying")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "verif_rotation"
    )
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(80.dp)
            ) {
                val strokeWidth = 6.dp.toPx()
                val r = (size.minDimension - strokeWidth) / 2f
                drawArc(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = strokeWidth)
                )
                rotate(rotation) {
                    drawArc(
                        color = ElysiumColors.NeonCyan,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
            Text(
                text = "Verificando con $hostName...",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.NeonCyan,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Estableciendo canal cifrado. Esto toma unos segundos.",
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    hostName: String,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonGreen,
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f))
                    .border(
                        width = 3.dp,
                        color = ElysiumColors.NeonGreen,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = "¡Conectado a $hostName!",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.NeonGreen
            )
            Text(
                text = "Abriendo el control remoto...",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = ElysiumColors.OnSurfaceMuted
            )
        }
    }
}
