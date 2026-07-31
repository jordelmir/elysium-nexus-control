package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlElement
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import kotlin.math.roundToInt

/**
 * The first controls editor canvas.
 *
 * `MASTER_ORDER.md` §15 says the user can "create
 * controls from scratch" with "drag, scale, rotate,
 * duplicate, group, lock, align, distribute, opacity".
 * Phase 1.1 ships the smallest first slice:
 *
 *  - A `Box` that fills the parent.
 *  - The active profile's [ControlElement]s rendered
 *    as circles (for visibility during drag).
 *  - Each control is draggable via
 *    `pointerInput { detectDragGestures }`.
 *  - The drag is normalised to the parent size and
 *    persisted back into the profile via the
 *    `onMoved` callback.
 *
 * Phase 1.2 adds: scale (pinch), rotate (two-finger
 * twist), opacity slider, long-press to delete,
 * tap-to-select with handles for resize/rotate, the
 * toolbar ("Add button", "Add stick", "Add trigger",
 * "Save", "Reset"). Phase 1.3+ adds: profile selector,
 * import/export, signature.
 *
 * ## Why a `Box` with `Modifier.offset` instead of
 * `Modifier.layout` or `Canvas`
 *
 * The first slice's requirement is "render the
 * control at its bounds, allow drag". The
 * `Modifier.offset { x, y }` is the simplest Compose
 * primitive that does this. `Canvas` is for custom
 * drawing (Phase 1.2+). `Modifier.layout` is for
 * custom measure (overkill here).
 *
 * ## Why the `onMoved` callback, not a state hoisted in
 * the activity
 *
 * The editor is a *projection* of the profile. The
 * profile is the source of truth; the editor observes
 * the profile via a `StateFlow` (or a `MutableState`
 * for the 1.1 in-memory case). The `onMoved` callback
 * is the *write* path: the activity updates the
 * profile, the editor recomposes. This is the same
 * pattern as the engine: a single source of truth, a
 * unidirectional flow.
 */
@Composable
fun EditorCanvas(
    profile: Profile,
    onMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onTapped: (controlId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // The parent size, used to convert the control's
    // normalized bounds to pixel positions.
    val parentSize = remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12)) // brand_ink
            .onSizeChanged { parentSize.value = it }
    ) {
        profile.controls.sortedBy { it.zIndex }.forEach { control ->
            ControlView(
                control = control,
                parentSize = parentSize.value,
                onMoved = onMoved,
                onTapped = onTapped
            )
        }
    }
}

/**
 * Render a single [ControlElement] as a draggable
 * circle. The control's `visualBounds` is converted to
 * pixel positions using the parent's size; the drag
 * gesture updates the visual bounds and calls
 * [onMoved] to persist the change.
 */
@Composable
private fun ControlView(
    control: ControlElement,
    parentSize: IntSize,
    onMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onTapped: (controlId: Int) -> Unit
) {
    if (parentSize == IntSize.Zero) return

    val density = LocalDensity.current
    val pixelWidthPx = with(density) { control.visualBounds.width.toDp() }
    val pixelHeightPx = with(density) { control.visualBounds.height.toDp() }
    val pixelX = with(density) { control.visualBounds.x.toDp() }
    val pixelY = with(density) { control.visualBounds.y.toDp() }

    val label = when (val b = control.binding) {
        is CanonicalBinding.Button -> b.button.name
        is CanonicalBinding.Stick -> "Stick ${b.side.name}"
        is CanonicalBinding.Trigger -> "Trigger ${b.side.name}"
        CanonicalBinding.Neutralize -> "Neutralize"
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (parentSize.width * control.visualBounds.x).roundToInt(),
                    y = (parentSize.height * control.visualBounds.y).roundToInt()
                )
            }
            .size(width = pixelWidthPx, height = pixelHeightPx)
            .rotate(control.rotation)
            .alpha(control.opacity)
            .background(
                color = when (control.binding) {
                    is CanonicalBinding.Neutralize -> Color(0xFFB42318) // brand_danger
                    else -> Color(0xFF1F6FEB) // brand_accent
                },
                shape = CircleShape
            )
            .pointerInput(control.id) {
                detectDragGestures(
                    onDragStart = {
                        onTapped(control.id)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val parentW = parentSize.width.toFloat()
                        val parentH = parentSize.height.toFloat()
                        if (parentW <= 0f || parentH <= 0f) return@detectDragGestures
                        val dxNormalized = dragAmount.x / parentW
                        val dyNormalized = dragAmount.y / parentH
                        val moved = control.movedBy(dxNormalized, dyNormalized)
                        onMoved(control.id, moved.visualBounds)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}
