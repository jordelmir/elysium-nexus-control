package com.elysium.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.ui.editor.EditorCanvas

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
 *
 * Phase 1.2+ adds: the toolbar ("Add button", "Save",
 * "Reset"), scale + rotate + opacity, the profile
 * selector, the transport multiplexer.
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
    onControlMoved: (controlId: Int, newVisualBounds: com.elysium.nexus.core.profile.NormalizedRect) -> Unit,
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12) // brand_ink
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The editor canvas: the user's profile rendered
            // as draggable controls.
            EditorCanvas(
                profile = profile,
                onMoved = onControlMoved,
                onTapped = { /* Phase 1.2: select + show handles */ }
            )
            // The diagnostic overlay: small text in the
            // corner showing the engine state. This is
            // temporary; Phase 1.2 replaces it with a proper
            // diagnostic panel.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Elysium Nexus",
                    color = Color(0xFFF2F2F4),
                    fontSize = 14.sp
                )
                Text(
                    text = "seq=${state.sequence}, touches=${state.touches.size()}",
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
                Text("Neutralize (§38)")
            }
        }
    }
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
        onNeutralize = {}
    )
}
