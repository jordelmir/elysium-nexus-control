package com.elysium.nexus.ui

import androidx.compose.foundation.layout.Arrangement
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

/**
 * The first Compose UI screen of the project.
 *
 * `MASTER_ORDER.md` §15 (controls editor) and §11 (touch
 * surface) describe the production UI. The first screen
 * in Phase 1.0 is the simplest possible Compose surface:
 *
 *  - A `Text` showing the engine's current state
 *    (`buttons`, `dpad`, sticks, triggers, touches).
 *  - A `Button` that calls [CanonicalInputEngine.neutralize]
 *    — the §38 release-blocker trigger.
 *
 * The screen is intentionally minimal. Phase 1.1+ adds
 * the controls editor (the §15 deliverable). Phase 1.2+
 * adds the profile selector. Phase 1.3+ adds the
 * transport multiplexer. Each iteration adds one
 * component; the screen is a real (if small) UI
 * surface from day one.
 *
 * ## Why a Composable function, not a View
 *
 * Compose is the modern Android UI toolkit. The
 * production UI will be Compose; the first screen
 * should be Compose too. The touch surface (the
 * `TouchSurfaceView`) is a View subclass because
 * `MotionEvent` consumption is the §11 pipeline; the
 * `MainScreen` is a Composable because everything else
 * is declarative UI.
 *
 * ## Why a stateless composable
 *
 * The composable takes the engine as a parameter. The
 * engine's state is observed via `collectAsState()`. The
 * composable does not own the engine's lifecycle; the
 * `MainActivity` does. The composable is the projection
 * of the engine's state into pixels.
 */
@Composable
fun MainScreen(
    engine: CanonicalInputEngine,
    modifier: Modifier = Modifier
) {
    // `collectAsState` collects the StateFlow and
    // recomposes the composable on every emission. The
    // engine's state flow is the source of truth; the
    // composable is its projection.
    val state by engine.state.collectAsState()
    MainScreenContent(
        state = state,
        onNeutralize = { engine.neutralize() },
        modifier = modifier
    )
}

/**
 * The stateless projection. Splitting this out makes
 * the composable previewable from Android Studio
 * without instantiating an engine, which is the
 * conventional Compose pattern.
 */
@Composable
private fun MainScreenContent(
    state: UniversalControllerState,
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12) // brand_ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Elysium Nexus",
                color = Color(0xFFF2F2F4), // brand_paper
                fontSize = 28.sp,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            StateRow(label = "Buttons", value = state.buttons.size().toString())
            StateRow(label = "D-pad", value = state.dpad.toString())
            StateRow(
                label = "Left stick",
                value = "(${state.leftStick.x}, ${state.leftStick.y})"
            )
            StateRow(
                label = "Right stick",
                value = "(${state.rightStick.x}, ${state.rightStick.y})"
            )
            StateRow(label = "Left trigger", value = state.leftTrigger.value.toString())
            StateRow(label = "Right trigger", value = state.rightTrigger.value.toString())
            StateRow(label = "Touches", value = state.touches.size().toString())
            StateRow(label = "Motion", value = if (state.motion == null) "off" else "on")
            StateRow(label = "Sequence", value = state.sequence.toString())
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNeutralize) {
                Text("Neutralize (§38)")
            }
        }
    }
}

@Composable
private fun StateRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color(0xFFF2F2F4),
        fontSize = 16.sp
    )
}

/**
 * Android Studio preview. A no-op that shows the layout
 * with a synthetic neutral state. Useful for
 * screenshotting the screen layout without an engine.
 */
@Preview
@Composable
private fun MainScreenPreview() {
    MaterialTheme {
        MainScreenContent(
            state = UniversalControllerState.neutral(),
            onNeutralize = {}
        )
    }
}
