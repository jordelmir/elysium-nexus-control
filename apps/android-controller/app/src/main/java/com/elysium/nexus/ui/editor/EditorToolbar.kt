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
 * The top toolbar of the editor.
 *
 * `MASTER_ORDER.md` §15 calls for a "toolbar" with the
 * primary editor actions: add a control, save, reset,
 * undo, redo, opacity. The toolbar has grown across
 * phases:
 *
 *  - Phase 1.2: Add button / stick / trigger, Save,
 *    Reset.
 *  - Phase 1.3: long-press to delete (via the editor
 *    canvas, not the toolbar).
 *  - Phase 1.4: opacity slider for the selected
 *    control. The slider is inline in the toolbar
 *    (a single Material 3 `Slider`) so the user can
 *    drag the opacity without opening a side panel.
 *
 * The toolbar is now two rows:
 *  - Row 1: the action chips (Add, Save, Reset).
 *  - Row 2: the opacity slider (Phase 1.4).
 *
 * The two rows are stacked in a `Column` by the
 * caller; the function emits a single `Row` (the
 * chips) and a single `Slider` (the opacity).
 *
 * ## Why chips, not buttons
 *
 * The toolbar is dense (5+ actions on a 360dp screen).
 * `AssistChip` / `FilterChip` are the Material 3
 * compact controls; they keep the bar at a single
 * 48dp height and stay legible at small font scales.
 * The brand uses `brand_accent` (`#1F6FEB`) for the
 * active variant and a transparent background for
 * the inactive variants.
 *
 * ## Why `Save` is a button, not an autosave
 *
 * The editor's mutation rate is *interactive* (every
 * drag emits a new profile). Autosaving on every
 * mutation would write the database on every frame
 * the user drags; the §30 latency budget forbids
 * I/O on the input thread. The `Save` button is
 * explicit: the user indicates the work is done.
 * The activity writes the current profile to Room
 * on every `onProfileUpdated` *and* on `Save` (the
 * two are equivalent for now; the `Save` button
 * surface in the toolbar is the "I'm done" cue for
 * the user).
 */
@Composable
fun EditorToolbar(
    onAdd: (ControlKind) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    isDirty: Boolean,
    onOpacityChange: (Float) -> Unit = { },
    selectedOpacity: Float? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
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
                selected = isDirty,
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
        }
        // Phase 1.4: the opacity slider. The slider
        // is enabled only when a control is selected
        // (i.e. `selectedOpacity != null`); the
        // screen wires the selected control's
        // opacity to the slider's value.
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

/**
 * The kind of control the toolbar's "Add" chip creates.
 *
 * The enum lives in the UI layer because the toolbar is
 * the only place that needs it. The activity maps the
 * kind to a [com.elysium.nexus.core.profile.ControlType]
 * and a [com.elysium.nexus.core.profile.CanonicalBinding].
 */
enum class ControlKind { Button, Stick, Trigger }

