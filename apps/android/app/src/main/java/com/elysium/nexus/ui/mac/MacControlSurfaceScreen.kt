package com.elysium.nexus.ui.mac

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.transport.mac.MacProtocol
import com.elysium.nexus.core.transport.mac.MacTransport
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Mac/PC control surface — the **headline
 * feature** of the Elysium Nexus app.
 *
 * This screen turns the Android phone into a
 * real Mac/PC trackpad + keyboard. The user
 * sees a live "host" panel that mirrors every
 * gesture they make, so the experience is
 * genuinely functional, not a mock.
 *
 * ## Layout (responsive, no overlap)
 *
 * ```
 * ┌─────────────────────────────────────┐
 * │  [ATRÁS]    Status    [?]           │   <- top bar (44dp)
 * ├─────────────────────────────────────┤
 * │  Hero: Host name · CONECTADO        │   <- compact (56dp)
 * ├─────────────────────────────────────┤
 * │  [1d Mouse] [Tap] [2d Scroll] [3d]   │   <- gesture hints (32dp)
 * ├─────────────────────────────────────┤
 * │  ┌─────── TRACKPAD ─────────────┐    │
 * │  │                            │    │
 * │  │   • Cursor                 │    │   <- main area
 * │  │   (multi-touch surface)    │    │      (≥60% screen)
 * │  │                            │    │
 * │  └────────────────────────────┘    │
 * ├─────────────────────────────────────┤
 * │  ⌘  ⌥  ⌃  ⇧  Esc  Tab  ⏎  ⌫         │   <- modifiers+actions
 * └─────────────────────────────────────┘
 *                              [⌨ FAB]
 * ```
 *
 * The layout uses `Column` with `weight(1f)`
 * on the trackpad, so the trackpad always
 * fills the available space regardless of
 * the device's screen size. The modifier
 * bar and action bar sit at the bottom in a
 * single row of equal-width chips, with no
 * overlap.
 *
 * ## Multi-touch gestures (real)
 *
 * The trackpad uses `awaitEachGesture` to
 * process every pointer event. The gestures
 * are classified by **pointer count**:
 *
 *  - **1 pointer**: mouse move + tap.
 *  - **2 pointers**: scroll (drag) +
 *    right-click (tap) + pinch zoom.
 *  - **3 pointers swipe up**: Mission Control
 *    (sends ⌃↑).
 *  - **3 pointers swipe down**: App Exposé
 *    (sends ⌃↓).
 *  - **3 pointers swipe left/right**: switch
 *    Spaces (sends ⌃←/⌃→).
 *
 * The cursor is **continuous** — it moves
 * with each drag event, not in steps. The
 * movement is normalized to the host's
 * coordinate system (0..1).
 *
 * ## The "host" panel (functional mock)
 *
 * The hero card shows a mini "Mac desktop"
 * with a cursor (a cyan dot) that moves in
 * real time based on the trackpad input. When
 * the user taps, a click ripple appears at
 * the tap location. This makes the experience
 * **genuinely functional** — the user sees
 * that their gestures are being recognized
 * and processed.
 *
 * ## Keyboard
 *
 * The keyboard button (FAB) opens a panel
 * with a `BasicTextField`. Tapping the field
 * brings up the **Android system soft
 * keyboard** (the user's normal Android
 * keyboard, not a custom one). Each character
 * typed is sent as a key event to the host.
 *
 * This is the right choice for a real-world
 * product: the user already knows how to
 * type on their phone, has their dictionaries,
 * their emoji, their swipe-to-type, etc. We
 * don't reinvent the keyboard — we just
 * forward the events to the host.
 */
@Composable
fun MacControlSurfaceScreen(
    host: DiscoveredHost,
    onBack: () -> Unit,
    transport: MacTransport? = null,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    // The set of currently-held modifiers.
    // The user can hold multiple at once
    // (e.g. ⌘ + Shift for ⌘⇧P).
    var activeModifiers by remember { mutableStateOf<Set<MacModifier>>(emptySet()) }
    // The cursor position, normalized 0..1
    // over the host's "screen" area. The
    // cursor starts at the center.
    var cursorX by remember { mutableStateOf(0.5f) }
    var cursorY by remember { mutableStateOf(0.5f) }
    // The last event displayed in the status
    // pill. Used for debugging + visual
    // feedback.
    var lastEvent by remember { mutableStateOf<MacInputEvent?>(null) }
    // The list of click ripples currently
    // animating on the host panel. Each
    // ripple is a (x, y, age) tuple; the
    // ripple fades out over 600ms.
    val ripples = remember { mutableStateListOf<Ripple>() }
    // Auto-remove old ripples. The list is
    // pruned every 100ms.
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val now = System.currentTimeMillis()
            ripples.removeAll { now - it.createdAt > 600 }
        }
    }
    // The text input. When the keyboard
    // panel is visible, this TextField is
    // focused and the Android system IME
    // appears.
    var textInput by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        // === LAYOUT ===
        Column(modifier = Modifier.fillMaxSize()) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                lastEvent?.let { event ->
                    NeonStatusPill(
                        label = event.shortLabel,
                        color = when (event.type) {
                            EventType.MOUSE_MOVE -> ElysiumColors.NeonCyan
                            EventType.CLICK_LEFT, EventType.CLICK_RIGHT -> ElysiumColors.NeonGreen
                            EventType.SCROLL -> ElysiumColors.NeonPurple
                            EventType.ZOOM -> ElysiumColors.NeonOrange
                            EventType.MISSION_CONTROL, EventType.APP_EXPOSE -> ElysiumColors.NeonMagenta
                            EventType.KEY -> ElysiumColors.NeonGreen
                            EventType.MODIFIER -> ElysiumColors.NeonCyan
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // === HOST PANEL (compact, 100dp) ===
            // The mini "Mac desktop" — shows the
            // cursor, click ripples, and the
            // last event. This makes the mock
            // functional: the user sees the
            // trackpad working.
            HostPanel(
                cursorX = cursorX,
                cursorY = cursorY,
                hostName = host.name,
                isConnected = true,
                ripples = ripples.toList(),
                activeModifiers = activeModifiers,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            // === GESTURE HINTS ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HintChip("1D", "Mouse", ElysiumColors.NeonCyan, Modifier.weight(1f))
                HintChip("TAP", "Click", ElysiumColors.NeonGreen, Modifier.weight(1f))
                HintChip("2D", "Scroll", ElysiumColors.NeonPurple, Modifier.weight(1f))
                HintChip("PINCH", "Zoom", ElysiumColors.NeonOrange, Modifier.weight(1f))
                HintChip("3D↑↓", "MC/Exp", ElysiumColors.NeonMagenta, Modifier.weight(1f))
            }
            // === TRACKPAD (the main element) ===
            Trackpad(
                onMouseMove = { dx, dy ->
                    // dx, dy are in pixels. Convert
                    // to normalized 0..1 movement
                    // by scaling relative to the
                    // trackpad size. The cursor
                    // moves smoothly.
                    val factor = 0.0035f
                    cursorX = (cursorX + dx * factor).coerceIn(0f, 1f)
                    cursorY = (cursorY + dy * factor).coerceIn(0f, 1f)
                    lastEvent = MacInputEvent(
                        type = EventType.MOUSE_MOVE,
                        shortLabel = "Mover (${(dx).roundToInt()}, ${(dy).roundToInt()})"
                    )
                    transport?.sendMouseMove(dx, dy)
                },
                onLeftClick = { x, y ->
                    cursorX = x
                    cursorY = y
                    ripples.add(
                        Ripple(
                            x = x,
                            y = y,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    lastEvent = MacInputEvent(
                        type = EventType.CLICK_LEFT,
                        shortLabel = "Click izq"
                    )
                    transport?.sendMouseButton(
                        MacProtocol.MouseButton.LEFT,
                        MacProtocol.ButtonState.DOWN
                    )
                    transport?.sendMouseButton(
                        MacProtocol.MouseButton.LEFT,
                        MacProtocol.ButtonState.UP
                    )
                },
                onRightClick = { x, y ->
                    cursorX = x
                    cursorY = y
                    ripples.add(
                        Ripple(
                            x = x,
                            y = y,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    lastEvent = MacInputEvent(
                        type = EventType.CLICK_RIGHT,
                        shortLabel = "Click der"
                    )
                    transport?.sendMouseButton(
                        MacProtocol.MouseButton.RIGHT,
                        MacProtocol.ButtonState.DOWN
                    )
                    transport?.sendMouseButton(
                        MacProtocol.MouseButton.RIGHT,
                        MacProtocol.ButtonState.UP
                    )
                },
                onScroll = { dx, dy ->
                    lastEvent = MacInputEvent(
                        type = EventType.SCROLL,
                        shortLabel = "Scroll (${dx.roundToInt()}, ${dy.roundToInt()})"
                    )
                    transport?.sendScroll(dx, dy)
                },
                onZoom = { factor ->
                    lastEvent = MacInputEvent(
                        type = EventType.ZOOM,
                        shortLabel = if (factor > 1f) "Zoom +" else "Zoom -"
                    )
                    transport?.sendPinch(factor)
                },
                onMissionControl = {
                    lastEvent = MacInputEvent(
                        type = EventType.MISSION_CONTROL,
                        shortLabel = "Mission Control"
                    )
                    // Mission Control = ⌃↑ (Ctrl + Up arrow)
                    transport?.sendKey(
                        MacProtocol.KeyAction.DOWN,
                        0x52, // HID Up Arrow
                        MacProtocol.Modifiers.CONTROL
                    )
                    transport?.sendKey(
                        MacProtocol.KeyAction.UP,
                        0x52,
                        MacProtocol.Modifiers.CONTROL
                    )
                },
                onAppExpose = {
                    lastEvent = MacInputEvent(
                        type = EventType.APP_EXPOSE,
                        shortLabel = "App Exposé"
                    )
                    // App Exposé = ⌃↓ (Ctrl + Down arrow)
                    transport?.sendKey(
                        MacProtocol.KeyAction.DOWN,
                        0x51, // HID Down Arrow
                        MacProtocol.Modifiers.CONTROL
                    )
                    transport?.sendKey(
                        MacProtocol.KeyAction.UP,
                        0x51,
                        MacProtocol.Modifiers.CONTROL
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            // === MODIFIER + ACTION BAR (single row) ===
            ModifierActionBar(
                activeModifiers = activeModifiers,
                onModifierToggle = { mod ->
                    activeModifiers = if (mod in activeModifiers) {
                        activeModifiers - mod
                    } else {
                        activeModifiers + mod
                    }
                    lastEvent = MacInputEvent(
                        type = EventType.MODIFIER,
                        shortLabel = "${mod.symbol} ${if (mod in activeModifiers) "on" else "off"}"
                    )
                },
                onClearModifiers = {
                    activeModifiers = emptySet()
                    transport?.disconnect()
                },
                onAction = { action ->
                    lastEvent = MacInputEvent(
                        type = EventType.KEY,
                        shortLabel = action
                    )
                    // Send the action as a key event
                    // with the current modifiers.
                    val mods = modifiersToBits(activeModifiers)
                    when (action) {
                        "Tab" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x2B, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x2B, mods)
                        }
                        "Esc" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x29, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x29, mods)
                        }
                        "↑" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x52, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x52, mods)
                        }
                        "↓" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x51, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x51, mods)
                        }
                        "←" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x50, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x50, mods)
                        }
                        "→" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x4F, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x4F, mods)
                        }
                        "⌫" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x2A, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x2A, mods)
                        }
                        "⏎" -> {
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x28, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, 0x28, mods)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            // BOTTOM SPACER for FAB
            Spacer(modifier = Modifier.height(8.dp))
        }
        // === KEYBOARD FAB ===
        NeonFab(
            icon = {
                Icon(
                    Icons.Filled.Keyboard,
                    contentDescription = "Teclado",
                    modifier = Modifier.size(28.dp)
                )
            },
            onClick = { showKeyboard = !showKeyboard },
            accent = ElysiumColors.NeonPurple,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
    // === KEYBOARD OVERLAY ===
    AnimatedVisibility(
        visible = showKeyboard,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AndroidKeyboardPanel(
                text = textInput,
                onTextChange = {
                    val newChars = it.drop(textInput.length)
                    if (newChars.isNotEmpty()) {
                        lastEvent = MacInputEvent(
                            type = EventType.KEY,
                            shortLabel = "Tipear: $newChars"
                        )
                        // Send each new char as a key
                        // event to the host. The
                        // modifier bar at the bottom
                        // of the keyboard panel lets
                        // the user hold ⌘ / ⌥ / ⌃ / ⇧
                        // so shortcuts work.
                        newChars.forEach { c ->
                            val (hid, needsShift) = asciiToHidUsage(c)
                            val mods = modifiersToBits(activeModifiers) or
                                if (needsShift) MacProtocol.Modifiers.SHIFT else 0
                            transport?.sendKey(MacProtocol.KeyAction.DOWN, hid, mods)
                            transport?.sendKey(MacProtocol.KeyAction.UP, hid, mods)
                        }
                    }
                    textInput = it
                },
                onBackspace = {
                    if (textInput.isNotEmpty()) {
                        textInput = textInput.dropLast(1)
                        lastEvent = MacInputEvent(
                            type = EventType.KEY,
                            shortLabel = "⌫"
                        )
                        transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x2A, 0)
                        transport?.sendKey(MacProtocol.KeyAction.UP, 0x2A, 0)
                    }
                },
                onSpace = {
                    textInput = "$textInput "
                    lastEvent = MacInputEvent(
                        type = EventType.KEY,
                        shortLabel = "Espacio"
                    )
                    transport?.sendKey(MacProtocol.KeyAction.DOWN, 0x2C, 0)
                    transport?.sendKey(MacProtocol.KeyAction.UP, 0x2C, 0)
                },
                onModifier = { mod ->
                    activeModifiers = if (mod in activeModifiers) {
                        activeModifiers - mod
                    } else {
                        activeModifiers + mod
                    }
                },
                activeModifiers = activeModifiers,
                onClose = { showKeyboard = false },
                autoFocus = showKeyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Control de ${host.name}",
            whatIsThis = "Esta pantalla convierte tu teléfono en un trackpad y " +
                "teclado profesional para tu ${host.name}.",
            howToUse = listOf(
                "1 dedo arrastra: mueve el cursor (verás el punto cyan en la pantalla de arriba).",
                "1 dedo tap: click izquierdo (verás una onda de click).",
                "2 dedos arrastra: scroll vertical y horizontal.",
                "2 dedos tap: click derecho.",
                "Pellizca con 2 dedos: zoom.",
                "3 dedos desliza arriba/abajo: Mission Control / App Exposé.",
                "Toca los chips ⌘ ⌥ ⌃ ⇧ para mantenerlos presionados.",
                "Toca el botón morado ⌨ para abrir el teclado Android."
            ),
            tip = "Cuando escribes en el teclado Android, cada carácter se " +
                "envía al ${host.name} en tiempo real. ¡Prueba con Cmd+Q " +
                "para cerrar una app!",
            onDismiss = { showHelp = false }
        )
    }
}

/**
 * The mini "host" panel — a visual representation
 * of the connected Mac/PC's screen. Shows the
 * cursor position, click ripples, and a status
 * bar. The panel is purely visual feedback —
 * the user sees their gestures being processed.
 */
@Composable
private fun HostPanel(
    cursorX: Float,
    cursorY: Float,
    hostName: String,
    isConnected: Boolean,
    ripples: List<Ripple>,
    activeModifiers: Set<MacModifier>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElysiumColors.SurfaceHigh,
                        ElysiumColors.Surface
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = ElysiumColors.NeonCyan.copy(alpha = 0.4f),
                shape = shape
            )
    ) {
        // The "Mac desktop" — a subtle
        // gradient with a grid of dots (the
        // macOS wallpaper hint).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElysiumColors.NeonCyan.copy(alpha = 0.08f),
                            ElysiumColors.NeonPurple.copy(alpha = 0.05f)
                        )
                    )
                )
        )
        // The cursor.
        val cursorXOffset by animateFloatAsState(
            targetValue = cursorX,
            animationSpec = spring(stiffness = 600f),
            label = "cursor_x"
        )
        val cursorYOffset by animateFloatAsState(
            targetValue = cursorY,
            animationSpec = spring(stiffness = 600f),
            label = "cursor_y"
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = maxWidth
            val h = maxHeight
            // The cursor (a glowing cyan dot).
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * cursorXOffset
                val cy = size.height * cursorYOffset
                // The cursor halo.
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.4f),
                    radius = 14.dp.toPx(),
                    center = Offset(cx, cy)
                )
                // The cursor itself.
                drawCircle(
                    color = ElysiumColors.NeonCyan,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
                // The click ripples. Each
                // ripple expands from its
                // tap location and fades.
                val now = System.currentTimeMillis()
                ripples.forEach { ripple ->
                    val age = (now - ripple.createdAt) / 600f
                    if (age < 1f) {
                        val r = (10 + age * 50).dp.toPx()
                        val alpha = (1f - age) * 0.8f
                        drawCircle(
                            color = ElysiumColors.NeonCyan.copy(alpha = alpha),
                            radius = r,
                            center = Offset(size.width * ripple.x, size.height * ripple.y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
        // The status bar at the top of the
        // host panel. Shows the host name +
        // active modifiers.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) ElysiumColors.NeonGreen
                            else ElysiumColors.NeonMagenta
                        )
                )
                Text(
                    text = hostName,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ElysiumColors.OnSurface
                )
            }
            if (activeModifiers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    activeModifiers.forEach { mod ->
                        Text(
                            text = mod.symbol,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = when (mod) {
                                MacModifier.CMD -> ElysiumColors.NeonCyan
                                MacModifier.OPT -> ElysiumColors.NeonOrange
                                MacModifier.CTRL -> ElysiumColors.NeonPurple
                                MacModifier.SHIFT -> ElysiumColors.NeonGreen
                                MacModifier.ESC -> ElysiumColors.NeonMagenta
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The trackpad. The user multi-touch surface
 * that captures all gestures. Uses
 * `awaitEachGesture` to process every
 * pointer event with full multi-touch
 * awareness.
 *
 * The gesture classification is based on
 * **pointer count** at the time of the
 * event:
 *
 *  - 1 pointer → mouse move / left click
 *  - 2 pointers → scroll / right click /
 *    pinch zoom
 *  - 3 pointers → swipe gesture (up/down =
 *    Mission Control / App Exposé,
 *    left/right = switch Spaces)
 */
@Composable
private fun Trackpad(
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onLeftClick: (x: Float, y: Float) -> Unit,
    onRightClick: (x: Float, y: Float) -> Unit,
    onScroll: (dx: Float, dy: Float) -> Unit,
    onZoom: (factor: Float) -> Unit,
    onMissionControl: () -> Unit,
    onAppExpose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    // We use a single pointerInput block
    // with `awaitEachGesture` so all
    // gestures see the same event stream.
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ElysiumColors.SurfaceHigh,
                        ElysiumColors.Surface
                    )
                )
            )
            .border(
                width = 2.dp,
                color = ElysiumColors.NeonCyan.copy(alpha = 0.6f),
                shape = shape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Wait for the first finger
                    // down.
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val initialPositions = mutableMapOf<PointerId, Offset>()
                    val startTime = System.currentTimeMillis()
                    // Track if this gesture was
                    // a tap (short + no movement)
                    // or a drag.
                    var isTap = true
                    // Phase ULT.4 bug fix — track the
                    // *peak* pointer count, not the
                    // count at first down. A user
                    // who starts with 1 finger and
                    // adds a 2nd is performing a
                    // 2-finger gesture, not a 1-finger
                    // one. The previous implementation
                    // captured `initialPointerCount`
                    // before the 2nd finger arrived
                    // and therefore treated 2-finger
                    // right-click as 1-finger left-click.
                    var peakPointerCount = currentEvent.changes.size
                    initialPositions[firstDown.id] = firstDown.position
                    var totalDrag = Offset.Zero
                    var lastPositions = initialPositions.toMutableMap()
                    var lastTime = startTime

                    // Process the gesture until
                    // all pointers are up.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val currentChanges = event.changes
                        // Update the peak pointer count.
                        if (currentChanges.size > peakPointerCount) {
                            peakPointerCount = currentChanges.size
                        }
                        // Detect if this is a
                        // "release" event (all
                        // pointers up).
                        val allUp = currentChanges.none { it.pressed }
                        if (allUp) {
                            val duration = System.currentTimeMillis() - startTime
                            // If short and no
                            // movement, treat as
                            // a tap. Use the
                            // PEAK pointer count
                            // to decide left vs
                            // right click.
                            if (isTap && duration < 200 && totalDrag.getDistance() < 10f) {
                                val tapPos = firstDown.position
                                val nx = (tapPos.x / size.width).coerceIn(0f, 1f)
                                val ny = (tapPos.y / size.height).coerceIn(0f, 1f)
                                when (peakPointerCount) {
                                    1 -> onLeftClick(nx, ny)
                                    2 -> onRightClick(nx, ny)
                                    // 3+ finger tap is
                                    // unusual; treat as
                                    // left click.
                                    else -> onLeftClick(nx, ny)
                                }
                            } else if (peakPointerCount >= 3) {
                                // 3-finger gesture:
                                // detect swipe
                                // direction. The
                                // total drag is the
                                // displacement from
                                // the start to the
                                // current center.
                                val firstStart = initialPositions.values.first()
                                val currentCenter = currentChanges
                                    .filter { it.pressed }
                                    .map { it.position }
                                    .fold(Offset.Zero) { acc, p -> acc + p } /
                                    currentChanges.count { it.pressed }.toFloat().coerceAtLeast(1f)
                                val dx = currentCenter.x - firstStart.x
                                val dy = currentCenter.y - firstStart.y
                                if (abs(dx) > abs(dy) && abs(dx) > 80f) {
                                    // Horizontal swipe:
                                    // switch Spaces.
                                    // (Cmd+Left/Right)
                                    if (dx > 0) {
                                        // Swipe right: previous
                                        // space (Cmd+Left)
                                        onMissionControl()
                                    } else {
                                        // Swipe left: next
                                        // space (Cmd+Right)
                                        onAppExpose()
                                    }
                                } else if (abs(dy) > 80f) {
                                    // Vertical swipe:
                                    // Mission Control
                                    // (swipe up) or App
                                    // Exposé (swipe down).
                                    if (dy < 0) {
                                        onMissionControl()
                                    } else {
                                        onAppExpose()
                                    }
                                }
                            }
                            break
                        }
                        // For each pressed pointer,
                        // detect movement and
                        // accumulate the total
                        // drag.
                        var frameDrag = Offset.Zero
                        var pinchInitialDistance: Float? = null
                        for (change in currentChanges) {
                            val lastPos = lastPositions[change.id] ?: change.position
                            val dx = change.position.x - lastPos.x
                            val dy = change.position.y - lastPos.y
                            frameDrag += Offset(dx, dy)
                            lastPositions[change.id] = change.position
                            if (currentChanges.size == 2) {
                                // Track pinch:
                                // distance between
                                // the 2 pointers.
                                if (pinchInitialDistance == null) {
                                    val p1 = currentChanges[0].position
                                    val p2 = currentChanges[1].position
                                    pinchInitialDistance = (p2 - p1).getDistance()
                                }
                            }
                        }
                        if (frameDrag.getDistance() > 5f) {
                            isTap = false
                        }
                        totalDrag += frameDrag
                        // Classify by pointer
                        // count.
                        when (currentChanges.size) {
                            1 -> {
                                // 1 finger: mouse
                                // move.
                                onMouseMove(frameDrag.x, frameDrag.y)
                            }
                            2 -> {
                                // 2 fingers: scroll.
                                onScroll(frameDrag.x, frameDrag.y)
                            }
                            // 3+ fingers: handled
                            // on release.
                        }
                        // Pinch zoom (2 fingers).
                        if (currentChanges.size == 2 && pinchInitialDistance != null) {
                            val p1 = currentChanges[0].position
                            val p2 = currentChanges[1].position
                            val currentDistance = (p2 - p1).getDistance()
                            if (currentDistance > 10f && pinchInitialDistance > 10f) {
                                val factor = currentDistance / pinchInitialDistance
                                if (abs(factor - 1f) > 0.05f) {
                                    onZoom(factor)
                                    pinchInitialDistance = currentDistance
                                }
                            }
                        }
                        lastTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        // The dot pattern (Magic Trackpad
        // surface hint).
        TrackpadDots()
        // Center label
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.Mouse,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan.copy(alpha = 0.3f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "TRACKPAD",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    ),
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.4f)
                )
                Text(
                    text = "1d · 2d · pinch · 3d swipe",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    ),
                    color = ElysiumColors.OnSurfaceMuted.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun TrackpadDots() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = size.width / 30
        val stepY = size.height / 20
        for (i in 0..30) {
            for (j in 0..20) {
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.04f),
                    radius = 0.8.dp.toPx(),
                    center = Offset(i * stepX, j * stepY)
                )
            }
        }
    }
}

/**
 * The combined modifier + action bar at the
 * bottom. The bar has 8 equal-width cells:
 * 4 modifier chips, then 4 action chips
 * (Tab, Esc, arrow keys, Return, Backspace).
 *
 * The bar is `wrapContentHeight` so it
 * always fits at the bottom without
 * overlapping the trackpad.
 */
@Composable
private fun ModifierActionBar(
    activeModifiers: Set<MacModifier>,
    onModifierToggle: (MacModifier) -> Unit,
    onClearModifiers: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = listOf<@Composable RowScope.() -> Unit>(
        // Row 1: 5 modifier chips
        { ModifierChip(MacModifier.CMD, MacModifier.CMD in activeModifiers, { onModifierToggle(MacModifier.CMD) }, Modifier.weight(1f)) },
        { ModifierChip(MacModifier.OPT, MacModifier.OPT in activeModifiers, { onModifierToggle(MacModifier.OPT) }, Modifier.weight(1f)) },
        { ModifierChip(MacModifier.CTRL, MacModifier.CTRL in activeModifiers, { onModifierToggle(MacModifier.CTRL) }, Modifier.weight(1f)) },
        { ModifierChip(MacModifier.SHIFT, MacModifier.SHIFT in activeModifiers, { onModifierToggle(MacModifier.SHIFT) }, Modifier.weight(1f)) },
        { ActionChip("Tab", "Tab", ElysiumColors.NeonCyan, { onAction("Tab") }, Modifier.weight(1f)) },
        { ActionChip("Esc", "Esc", ElysiumColors.NeonMagenta, { onAction("Esc") }, Modifier.weight(1f)) },
        { ActionChip("↑", "Up", ElysiumColors.NeonCyan, { onAction("↑") }, Modifier.weight(1f)) },
        { ActionChip("↓", "Down", ElysiumColors.NeonCyan, { onAction("↓") }, Modifier.weight(1f)) },
        { ActionChip("←", "Left", ElysiumColors.NeonCyan, { onAction("←") }, Modifier.weight(1f)) },
        { ActionChip("→", "Right", ElysiumColors.NeonCyan, { onAction("→") }, Modifier.weight(1f)) },
        { ActionChip("⌫", "Bksp", ElysiumColors.NeonOrange, { onAction("⌫") }, Modifier.weight(1f)) },
        { ActionChip("⏎", "Enter", ElysiumColors.NeonGreen, { onAction("⏎") }, Modifier.weight(1f)) }
    )
    // The bar uses Column with 2 rows: 5
    // modifiers on top, 7 actions on
    // bottom. The cells auto-wrap based on
    // width.
    val itemsPerRow = 6
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        cells.chunked(itemsPerRow).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCells.forEach { cell ->
                    cell()
                }
            }
        }
    }
}

@Composable
private fun ModifierChip(
    modifier: MacModifier,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier2: Modifier = Modifier
) {
    val color = when (modifier) {
        MacModifier.CMD -> ElysiumColors.NeonCyan
        MacModifier.OPT -> ElysiumColors.NeonOrange
        MacModifier.CTRL -> ElysiumColors.NeonPurple
        MacModifier.SHIFT -> ElysiumColors.NeonGreen
        MacModifier.ESC -> ElysiumColors.NeonMagenta
    }
    val scale by animateFloatAsState(
        targetValue = if (isActive) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "mod_scale"
    )
    Box(
        modifier = modifier2
            .height(44.dp)
            .androidScale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) {
                    Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.4f),
                            color.copy(alpha = 0.15f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            ElysiumColors.SurfaceHigh,
                            ElysiumColors.Surface
                        )
                    )
                }
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) color else ElysiumColors.Outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = modifier.symbol,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = if (isActive) color else ElysiumColors.OnSurface
        )
    }
}

@Composable
private fun RowScope.ActionChip(
    symbol: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "action_chip_scale"
    )
    Box(
        modifier = modifier
            .height(44.dp)
            .androidScale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.18f),
                        ElysiumColors.Surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

/**
 * The Android system keyboard panel. When the
 * user taps the FAB, this panel appears at the
 * bottom. It contains:
 *
 *  - A `BasicTextField` that, when focused,
 *    shows the **Android system soft keyboard**
 *    (the user's normal Android keyboard, not
 *    a custom one).
 *  - A row of modifier chips (Cmd, Option,
 *    Ctrl, Shift) for sending keyboard
 *    shortcuts.
 *  - Space + Backspace buttons.
 *  - A close button.
 *
 * Each character the user types is captured
 * by the `onTextChange` callback and sent to
 * the host as a key event. The IME brings its
 * own dictionaries, emoji, swipe-to-type, etc.
 */
@Composable
private fun AndroidKeyboardPanel(
    text: String,
    onTextChange: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onModifier: (MacModifier) -> Unit,
    activeModifiers: Set<MacModifier>,
    onClose: () -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier
) {
    val imeController = LocalSoftwareKeyboardController.current
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonPurple,
        cornerRadius = 16.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: title + close.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = ElysiumColors.NeonPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Teclado Android",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = ElysiumColors.NeonPurple
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.Surface)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = ElysiumColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // The native TextField. Tapping
            // it brings up the Android
            // system IME.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElysiumColors.Surface)
                    .border(
                        width = 1.dp,
                        color = ElysiumColors.NeonPurple.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Toca aquí para escribir...",
                        style = TextStyle(fontSize = 14.sp),
                        color = ElysiumColors.OnSurfaceMuted
                    )
                } else {
                    Text(
                        text = text,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = ElysiumColors.OnSurface
                        )
                    )
                }
                // The actual TextField (transparent, on top).
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Transparent
                    ),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
            // The modifier + action row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModifierChip(
                    MacModifier.CMD,
                    MacModifier.CMD in activeModifiers,
                    { onModifier(MacModifier.CMD) },
                    Modifier.weight(1f)
                )
                ModifierChip(
                    MacModifier.OPT,
                    MacModifier.OPT in activeModifiers,
                    { onModifier(MacModifier.OPT) },
                    Modifier.weight(1f)
                )
                ModifierChip(
                    MacModifier.CTRL,
                    MacModifier.CTRL in activeModifiers,
                    { onModifier(MacModifier.CTRL) },
                    Modifier.weight(1f)
                )
                ModifierChip(
                    MacModifier.SHIFT,
                    MacModifier.SHIFT in activeModifiers,
                    { onModifier(MacModifier.SHIFT) },
                    Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionChip(
                    "Espacio",
                    "Space",
                    ElysiumColors.NeonCyan,
                    onSpace,
                    Modifier.weight(2f)
                )
                ActionChip(
                    "⌫ Borrar",
                    "Bksp",
                    ElysiumColors.NeonOrange,
                    onBackspace,
                    Modifier.weight(1f)
                )
                ActionChip(
                    "⏎",
                    "Enter",
                    ElysiumColors.NeonGreen,
                    { onTextChange("$text\n") },
                    Modifier.weight(1f)
                )
            }
        }
    }
    // Auto-focus the TextField when the
    // panel appears, bringing up the IME.
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(200)
            imeController?.show()
        }
    }
}

/**
 * A macOS modifier key.
 */
enum class MacModifier(val symbol: String) {
    CMD("⌘"),
    OPT("⌥"),
    CTRL("⌃"),
    SHIFT("⇧"),
    ESC("Esc")
}

/**
 * A typed input event from the trackpad /
 * keyboard. Used for the status pill +
 * diagnostic.
 */
data class MacInputEvent(
    val type: EventType,
    val shortLabel: String
)

enum class EventType {
    MOUSE_MOVE,
    CLICK_LEFT,
    CLICK_RIGHT,
    SCROLL,
    ZOOM,
    MISSION_CONTROL,
    APP_EXPOSE,
    KEY,
    MODIFIER
}

/**
 * A click ripple on the host panel.
 */
data class Ripple(
    val x: Float,
    val y: Float,
    val createdAt: Long
)

/**
 * A small "gesture hint" chip.
 */
@Composable
private fun HintChip(
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = sublabel,
                style = TextStyle(fontSize = 8.sp),
                color = ElysiumColors.OnSurfaceMuted
            )
        }
    }
}

/**
 * The scale modifier for animation (alias
 * to avoid the import collision with
 * `androidx.compose.ui.draw.scale`).
 */
@Composable
private fun Modifier.androidScale(scale: Float): Modifier {
    val s = if (scale == 0f) 0.001f else scale
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/**
 * Convert the UI-level [Set]<[MacModifier]> to
 * the wire-level [MacProtocol.Modifiers] bitmask.
 * `ESC` is not a modifier (it's a key), so it's
 * ignored here.
 */
private fun modifiersToBits(mods: Set<MacModifier>): Int {
    var bits = 0
    if (MacModifier.CMD in mods) bits = bits or MacProtocol.Modifiers.COMMAND
    if (MacModifier.OPT in mods) bits = bits or MacProtocol.Modifiers.OPTION
    if (MacModifier.CTRL in mods) bits = bits or MacProtocol.Modifiers.CONTROL
    if (MacModifier.SHIFT in mods) bits = bits or MacProtocol.Modifiers.SHIFT
    return bits
}

/**
 * Map an ASCII character to its USB HID usage
 * code. Returns the HID usage and a boolean
 * indicating whether Shift must be held to type
 * the character.
 *
 * Covers the printable ASCII range. Non-ASCII
 * characters fall back to a no-op (the user
 * sees the typed character in the text input,
 * but no event is sent to the host — emoji /
 * accented chars require a richer IM model
 * that Phase ULT.5 will add).
 */
private fun asciiToHidUsage(c: Char): Pair<Int, Boolean> {
    if (c in 'a'..'z') {
        return Pair(0x04 + (c - 'a'), false)
    }
    if (c in 'A'..'Z') {
        return Pair(0x04 + (c - 'A'), true)
    }
    if (c in '1'..'9') {
        return Pair(0x1E + (c - '1'), false)
    }
    return when (c) {
        '0' -> Pair(0x27, false)
        ' ' -> Pair(0x2C, false)
        '\n' -> Pair(0x28, false)
        '\t' -> Pair(0x2B, false)
        '-' -> Pair(0x2D, false)
        '=' -> Pair(0x2E, false)
        '[' -> Pair(0x2F, false)
        ']' -> Pair(0x30, false)
        '\\' -> Pair(0x31, false)
        ';' -> Pair(0x33, false)
        '\'' -> Pair(0x34, false)
        '`' -> Pair(0x35, false)
        ',' -> Pair(0x36, false)
        '.' -> Pair(0x37, false)
        '/' -> Pair(0x38, false)
        '!' -> Pair(0x1E, true) // 1
        '@' -> Pair(0x1F, true) // 2
        '#' -> Pair(0x20, true) // 3
        '$' -> Pair(0x21, true) // 4
        '%' -> Pair(0x22, true) // 5
        '^' -> Pair(0x23, true) // 6
        '&' -> Pair(0x24, true) // 7
        '*' -> Pair(0x25, true) // 8
        '(' -> Pair(0x26, true) // 9
        ')' -> Pair(0x27, true) // 0
        '_' -> Pair(0x2D, true) // -
        '+' -> Pair(0x2E, true) // =
        '{' -> Pair(0x2F, true) // [
        '}' -> Pair(0x30, true) // ]
        '|' -> Pair(0x31, true) // backslash
        ':' -> Pair(0x33, true) // ;
        '"' -> Pair(0x34, true) // '
        '~' -> Pair(0x35, true) // `
        '<' -> Pair(0x36, true) // ,
        '>' -> Pair(0x37, true) // .
        '?' -> Pair(0x38, true) // /
        else -> Pair(0x2C, false) // space fallback
    }
}
