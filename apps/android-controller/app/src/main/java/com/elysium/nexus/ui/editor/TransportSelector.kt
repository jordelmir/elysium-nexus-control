package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elysium.nexus.R
import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.core.transport.TransportState

/**
 * The §17 transport selector.
 *
 * `MASTER_ORDER.md` §17 says the project shall
 * support multiple transports (Bluetooth HID,
 * USB Accessory, Elysium Link, etc.) via a
 * multiplexer. The selector is the UI for
 * choosing the active transport at runtime.
 *
 * The selector is a horizontally-scrolling chip
 * row. The current transport is highlighted
 * with a [FilterChip] (selected state). The
 * other transports are [AssistChip]s; tapping a
 * chip calls [onTransportSelected] with the new
 * transport's handle.
 *
 * The transport's [TransportState] is rendered
 * inline (e.g. "Connected", "Disconnected",
 * "Error"). The activity wires the chip's
 * selected state to the binding's
 * [TransportBinding.transportState].
 *
 * ## Why a chip row, not a `DropdownMenu`
 *
 * The chip row is one tap, one visible target.
 * A `DropdownMenu` requires two taps (open, then
 * pick) and obscures the editor. The chip row
 * is the direct-manipulation pattern the §17
 * spec describes.
 */
@Composable
fun TransportSelector(
    transports: List<ControllerTransport>,
    currentTransport: ControllerTransport,
    onTransportSelected: (ControllerTransport) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F12))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (transports.isEmpty()) {
            Text(
                text = "(no transports)",
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(8.dp)
            )
        } else {
            for (transport in transports) {
                val isSelected = transport === currentTransport
                if (isSelected) {
                    FilterChip(
                        selected = true,
                        onClick = { /* already current */ },
                        label = {
                            Text(
                                "${transport.capabilities.label} • ${stateLabel(transport.state)}"
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF1F6FEB),
                            labelColor = Color.White
                        )
                    )
                } else {
                    AssistChip(
                        onClick = { onTransportSelected(transport) },
                        label = {
                            Text(
                                "${transport.capabilities.label} • ${stateLabel(transport.state)}"
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1A1A1F),
                            labelColor = Color(0xFFF2F2F4)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Map a [TransportState] to a short label.
 */
private fun stateLabel(state: TransportState): String = when (state) {
    TransportState.IDLE -> "Idle"
    TransportState.INITIALISING -> "Init"
    TransportState.PAIRED -> "Paired"
    TransportState.CONNECTED -> "Connected"
    TransportState.DISCONNECTED -> "Disconnected"
    TransportState.ERROR -> "Error"
}
