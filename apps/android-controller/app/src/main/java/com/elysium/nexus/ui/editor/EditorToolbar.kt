package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
 * undo, redo. Phase 1.2 ships the *first* slice:
 *
 *  - "Add button" (the most common action).
 *  - "Add stick".
 *  - "Add trigger".
 *  - "Save" (the explicit save action).
 *  - "Reset" (revert to the saved profile).
 *
 * The buttons emit `onAdd(ControlType)` and
 * `onSave()` / `onReset()`; the screen wires them to
 * the activity's profile-mutation callbacks.
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
 *
 * ## Why `Reset` reverts to a remembered baseline
 *
 * The screen hoists a `savedProfile` state — the
 * last profile that was loaded from the repository.
 * `Reset` copies the screen's current profile back
 * to that baseline. Undo / redo (Phase 1.3+) build
 * a per-session history on top of this baseline.
 */
@Composable
fun EditorToolbar(
    onAdd: (ControlKind) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    isDirty: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F12))
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
        // The Save chip is highlighted when the profile
        // is dirty. The state is hoisted from the screen.
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
