package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elysium.nexus.R

/**
 * The kind of alignment / distribution action
 * the toolbar's row 2 chips dispatch.
 */
enum class AlignmentAction {
    AlignLeft,
    AlignRight,
    AlignTop,
    AlignBottom,
    DistributeHorizontally,
    DistributeVertically
}

/**
 * The kind of control the toolbar's "Add" chip
 * creates.
 */
enum class ControlKind { Button, Stick, Trigger }

/**
 * The top toolbar of the editor.
 *
 * The toolbar is three rows:
 *  - Row 1: the action chips (Add, Save, Reset,
 *    New profile, Delete).
 *  - Row 2: the alignment / distribution chips
 *    (Phase 1.12).
 *  - Row 3: the opacity slider (Phase 1.4).
 *
 * Each row is horizontally scrollable; the
 * toolbar is wider than a 360dp screen on a
 * phone.
 */
@Composable
fun EditorToolbar(
    onAdd: (ControlKind) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onNewProfile: () -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    onShare: () -> Unit = { },
    onImport: () -> Unit = { },
    onSettings: () -> Unit = { },
    onAlign: (AlignmentAction) -> Unit = { },
    onOpacityChange: (Float) -> Unit = { },
    selectedOpacity: Float? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scrollState2 = rememberScrollState()
    Column(modifier = modifier) {
        // Row 1: the primary action chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12))
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { onAdd(ControlKind.Button) },
                label = { Text(stringResource(R.string.editor_add_button)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = { onAdd(ControlKind.Stick) },
                label = { Text(stringResource(R.string.editor_add_stick)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = { onAdd(ControlKind.Trigger) },
                label = { Text(stringResource(R.string.editor_add_trigger)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = false,
                onClick = onSave,
                label = { Text(stringResource(R.string.editor_save)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4),
                    selectedContainerColor = Color(0xFF1F6FEB),
                    selectedLabelColor = Color.White
                )
            )
            AssistChip(
                onClick = onReset,
                label = { Text(stringResource(R.string.editor_reset)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = onNewProfile,
                label = { Text(stringResource(R.string.editor_new_profile)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = onDeleteProfile,
                label = { Text(stringResource(R.string.editor_delete_profile)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFFB42318),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            // Phase 1.17: the §15 share chip. Tapping
            // it opens the system share sheet with
            // the current profile as a JSON document
            // (see [com.elysium.nexus.core.profile.AndroidProfileShareLauncher]).
            AssistChip(
                onClick = onShare,
                label = { Text(stringResource(R.string.editor_share_profile)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            // Phase 1.22: the §15 import chip.
            // Tapping it opens the import dialog
            // (see [com.elysium.nexus.ui.editor.ProfileImportDialog]).
            // The dialog accepts a JSON payload
            // (e.g. one received from a share
            // intent) and calls
            // [com.elysium.nexus.core.profile.ProfileImporter.import].
            AssistChip(
                onClick = onImport,
                label = { Text(stringResource(R.string.editor_import_profile)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            // Phase 1.18: the §15 settings chip.
            // Tapping it opens the settings dialog
            // (see [com.elysium.nexus.ui.settings.SettingsDialog]).
            AssistChip(
                onClick = onSettings,
                label = { Text(stringResource(R.string.editor_settings)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
        }
        // Row 2: alignment / distribution chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12))
                .horizontalScroll(scrollState2)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AssistChip(
                onClick = { onAlign(AlignmentAction.AlignLeft) },
                label = { Text(stringResource(R.string.editor_align_left)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = { onAlign(AlignmentAction.AlignRight) },
                label = { Text(stringResource(R.string.editor_align_right)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = { onAlign(AlignmentAction.AlignTop) },
                label = { Text(stringResource(R.string.editor_align_top)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = { onAlign(AlignmentAction.AlignBottom) },
                label = { Text(stringResource(R.string.editor_align_bottom)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = { onAlign(AlignmentAction.DistributeHorizontally) },
                label = { Text(stringResource(R.string.editor_distribute_horizontally)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
            AssistChip(
                onClick = { onAlign(AlignmentAction.DistributeVertically) },
                label = { Text(stringResource(R.string.editor_distribute_vertically)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1F),
                    labelColor = Color(0xFFF2F2F4)
                )
            )
        }
        // Row 3: opacity slider.
        if (selectedOpacity != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F12))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_opacity),
                    color = Color(0xFFF2F2F4),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = selectedOpacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1F6FEB),
                        activeTrackColor = Color(0xFF1F6FEB),
                        inactiveTrackColor = Color(0xFF1A1A1F)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
