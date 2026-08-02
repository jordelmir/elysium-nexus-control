package com.elysium.nexus.ui.usb

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard

/**
 * USB-C wired connection screen.
 *
 * Shows the connection status for the USB HID
 * transport. When a USB device is connected via
 * USB-C, the phone sends raw HID reports over
 * the bulk endpoint for near-zero latency.
 *
 * Phase ULT.9 — first iteration: connection
 * status display. The full control surface
 * (trackpad + keyboard + gamepad) will be
 * added when the transport is tested on a
 * real device.
 */
@Composable
fun UsbCConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElysiumColors.Background)
            .padding(16.dp)
    ) {
        // === TOP BAR ===========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Atrás",
                    tint = ElysiumColors.OnSurface
                )
            }
            Text(
                text = "USB-C CABLADO",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = ElysiumColors.NeonYellow
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === STATUS CARD =======================================
        NeonCard(
            modifier = Modifier.fillMaxWidth(),
            accent = ElysiumColors.NeonYellow,
            cornerRadius = 18.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElysiumColors.NeonYellow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Usb,
                            contentDescription = null,
                            tint = ElysiumColors.NeonYellow,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Conexión USB",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ElysiumColors.OnSurface
                        )
                        Text(
                            text = "Cable USB-C · HID directo",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = ElysiumColors.OnSurfaceVariant
                            )
                        )
                    }
                }

                // Connection status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = ElysiumColors.NeonYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Esperando conexión USB...",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ElysiumColors.NeonYellow
                    )
                }

                // Info
                Text(
                    text = "Conecta tu teléfono a un Mac o PC usando un cable USB-C. " +
                        "El dispositivo receptor ejecutará un daemon ligero que " +
                        "recibe los reportes HID y los inyecta como eventos nativos.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = ElysiumColors.OnSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === LATENCY CARD ======================================
        NeonCard(
            modifier = Modifier.fillMaxWidth(),
            accent = ElysiumColors.NeonGreen,
            cornerRadius = 16.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = ElysiumColors.NeonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "< 2ms de latencia",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonGreen
                    )
                    Text(
                        text = "USB full-speed: 1ms · USB high-speed: 0.125ms",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ElysiumColors.OnSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
