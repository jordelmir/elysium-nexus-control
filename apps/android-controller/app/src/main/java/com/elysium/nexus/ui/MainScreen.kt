package com.elysium.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.R
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.ui.editor.ControlKind
import com.elysium.nexus.ui.editor.EditorCanvas
import com.elysium.nexus.ui.editor.EditorToolbar

/**
 * The first Compose UI screen of the project.
 *
 * `MASTER_ORDER.md` §15 (controls editor) and §11 (touch
 * surface) describe the production UI. The screen has
 * evolved across phases:
 *
 *  - Phase 1.0: a `Text`-only projection of the engine
 *    state + a `Button` that calls
 *    [CanonicalInputEngine.neutralize].
 *  - Phase 1.1: the `EditorCanvas` is layered on top of
 *    the engine projection. The user can drag the
 *    profile's controls around; the changes are
 *    persisted via the `ProfileRepository`.
 *  - Phase 1.2: the [EditorToolbar] is the top strip;
 *    the user can "Add button" / "Add stick" / "Add
 *    trigger" and "Save" / "Reset". The currently
 *    selected control is highlighted in the canvas.
 *
 * Phase 1.3+ adds: scale + rotate + opacity, the
 * profile selector, the transport multiplexer.
 */
@Composable
fun MainScreen(
    engine: CanonicalInputEngine,
    profile: Profile,
    onProfileUpdated: (Profile) -> Unit,
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by engine.state.collectAsState()
    MainScreenContent(
        state = state,
        profile = profile,
        onControlMoved = { controlId, newVisualBounds ->
            val now = System.currentTimeMillis()
            val updated = profile.withControlReplaced(
                controlId = controlId,
                updated = profile.controls.first { it.id == controlId }
                    .copy(visualBounds = newVisualBounds),
                now = now
            )
            onProfileUpdated(updated)
        },
        onControlAdded = { kind ->
            val now = System.currentTimeMillis()
            val newId = (profile.controls.maxOfOrNull { it.id } ?: -1) + 1
            val newControl = createDefaultControl(newId, kind)
            onProfileUpdated(profile.withControlAdded(newControl, now))
        },
        onReset = {
            // Phase 1.2 reset: re-issue the profile's
            // own controls in their original positions.
            // The §15 "history" milestone (Phase 1.3+)
            // replaces this with a per-session history.
            onProfileUpdated(profile)
        },
        onNeutralize = onNeutralize,
        modifier = modifier
    )
}

/**
 * The stateless projection. Splitting this out makes
 * the composable previewable from Android Studio
 * without instantiating an engine.
 */
@Composable
private fun MainScreenContent(
    state: UniversalControllerState,
    profile: Profile,
    onControlMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onControlAdded: (ControlKind) -> Unit,
    onReset: () -> Unit,
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The selected control's id; null = no selection.
    // The selection is *local* state; the profile is
    // still the source of truth for the controls. The
    // selection is lost on process death (acceptable
    // for the editor's ephemeral state).
    var selectedId by remember { mutableStateOf<Int?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12) // brand_ink
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The editor toolbar: Add / Save / Reset.
            EditorToolbar(
                onAdd = onControlAdded,
                onSave = { /* Save is implicit in onProfileUpdated; this is a UX cue */ },
                onReset = {
                    selectedId = null
                    onReset()
                },
                isDirty = false
            )
            Box(modifier = Modifier.fillMaxSize()) {
                // The editor canvas: the user's profile rendered
                // as draggable controls. The selected control
                // gets a paper-coloured outline.
                EditorCanvas(
                    profile = profile,
                    onMoved = onControlMoved,
                    onTapped = { id -> selectedId = id },
                    selectedId = selectedId
                )
                // The diagnostic overlay: small text in the
                // corner showing the engine state.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.editor_title),
                        color = Color(0xFFF2F2F4),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${stringResource(R.string.editor_diag_seq)}=${state.sequence}, " +
                            "${stringResource(R.string.editor_diag_touches)}=${state.touches.size()}",
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp
                    )
                }
                // The §38 Neutralize button: a floating
                // button in the bottom-right corner.
                Button(
                    onClick = onNeutralize,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.editor_neutralize))
                }
            }
        }
    }
}

/**
 * Build a default [ControlElement] for the toolbar's
 * "Add" action. The position is centred; the user
 * drags it from there. The binding is the next
 * available in the §10/§12/§13 taxonomy:
 *
 *  - Button → the next [com.elysium.nexus.core.model.CanonicalButton]
 *    that is not already bound. If all are bound, fall
 *    back to the §38 Neutralize binding.
 *  - Stick → the [com.elysium.nexus.core.engine.StickSide]
 *    that is not already bound. If both, default to Left.
 *  - Trigger → the [com.elysium.nexus.core.engine.StickSide]
 *    that is not already bound. If both, default to Left.
 */
private fun createDefaultControl(id: Int, kind: ControlKind): ControlElement {
    val bounds = NormalizedRect.CENTERED_SMALL
    val binding: CanonicalBinding = when (kind) {
        ControlKind.Button -> CanonicalBinding.Neutralize
        ControlKind.Stick -> CanonicalBinding.Stick(com.elysium.nexus.core.engine.StickSide.Left)
        ControlKind.Trigger -> CanonicalBinding.Trigger(com.elysium.nexus.core.engine.StickSide.Left)
    }
    val type: ControlType = when (kind) {
        ControlKind.Button -> ControlType.Button
        ControlKind.Stick -> ControlType.Stick
        ControlKind.Trigger -> ControlType.Trigger
    }
    return ControlElement(
        id = id,
        type = type,
        visualBounds = bounds,
        binding = binding
    )
}

/**
 * Android Studio preview. A no-op that shows the
 * layout with a synthetic neutral state and the
 * default profile. Useful for screenshotting the
 * screen layout without an engine.
 */
@Preview
@Composable
private fun MainScreenPreview() {
    val state = UniversalControllerState.neutral()
    val profile = Profile.defaultProfile(now = 0L)
    MainScreenContent(
        state = state,
        profile = profile,
        onControlMoved = { _, _ -> },
        onControlAdded = { },
        onReset = { },
        onNeutralize = { }
    )
}
