# Phase 0.7 — UX Mega Upgrade: Drag, Scroll, Pan, Keyboard, Haptic

**Date**: 2026-08-04
**Status**: VERIFIED_LAB (assembleDebug GREEN)

## Summary

Five major UX features shipped in a single pass, all touching
`MacControlSurfaceScreen.kt` (Android) with zero Mac-agent changes needed.

## Features Delivered

### 1. Drag & Drop Support
- **Long-press to drag**: Hold finger on trackpad >350ms + move >15px → activates drag mode
- Mac agent already handles `.leftMouseDragged` events (lines 42-49, 70-77 of EventInjector.swift)
- `onDragStart` sends `mouseButton(LEFT, DOWN)`, subsequent moves are interpreted as drags
- `onDragEnd` on finger lift sends `mouseButton(LEFT, UP)`
- Strong haptic feedback (`LONG_PRESS`) signals drag activation
- Status pill shows "⟹ DRAG INICIO" / "⟹ DRAG FIN"
- Enables: file dragging, text selection, window resizing, slider manipulation

### 2. Scroll Acceleration (2.2×)
- 2-finger scroll deltas now multiplied by 2.2f acceleration factor
- Makes scrolling feel natural and responsive
- Applied to both pinch-disambiguated and direct scroll paths

### 3. Auto-Pan When Zoomed
- `panOffset` now tracks cursor position when `zoomScale > 1.05f`
- Formula: `panOffset = Offset(0.5f - cursorX, 0.5f - cursorY)`
- `graphicsLayer` calculates pixel translations with proper clamping:
  - `maxTx = size.width * (zoomScale - 1f) / 2f`
  - `translationX = (panOffset.x * size.width * zoomScale).coerceIn(-maxTx, maxTx)`
- Viewport automatically follows cursor — no more zooming into a dead corner
- Works in both Direct Touch and Trackpad Mouse modes

### 4. System Keyboard FAB (Android IME)
- Purple glowing FAB button at bottom-right corner
- Opens `AndroidKeyboardPanel` overlay with AnimatedVisibility (fade + scale)
- Full IME text input: each new character → `asciiToHidUsage()` → HID key down/up
- Supports backspace, space, and modifier keys
- Positioned above bottom bar to avoid overlap
- Enables: swipe typing, emoji input, autocomplete, voice typing

### 5. Haptic Feedback
- **Physical buttons**: `VIRTUAL_KEY` haptic on left/right click buttons
- **Mode toggle**: `VIRTUAL_KEY` haptic when switching Direct Touch ↔ Trackpad
- **Drag activation**: `LONG_PRESS` haptic when long-press drag engages
- **System keyboard FAB**: `VIRTUAL_KEY` haptic on toggle
- All use `view.performHapticFeedback()` — respects system haptic settings

## Files Modified

| File | Changes |
|------|---------|
| `MacControlSurfaceScreen.kt` | +2 imports, +6 state vars, +4 auto-pan lines, +11 drag callbacks, +3 haptic insertions, +76 FAB/keyboard overlay, +2 Trackpad params, +1 drag var, +6 drag-end guard, +8 long-press detection, +4 scroll accel, +4 graphicsLayer pan calc |

## What Did NOT Change (Mac Agent)

The `EventInjector.swift` already correctly:
- Tracks `isLeftMouseDown` / `isRightMouseDown` state
- Uses `.leftMouseDragged` / `.rightMouseDragged` when buttons are held
- No Mac-side changes needed — the protocol and event injection already support drag

## Test Matrix

| Test | Expected | Status |
|------|----------|--------|
| assembleDebug | GREEN | ✅ |
| 1-finger tap = left click | Click registered | PENDING_DEVICE |
| 2-finger tap = right click | Right click | PENDING_DEVICE |
| Long-press >350ms + drag = drag | File/window dragged | PENDING_DEVICE |
| Lift finger after drag = drop | Drag ends | PENDING_DEVICE |
| 2-finger scroll acceleration | Faster, smoother scroll | PENDING_DEVICE |
| Zoom + move cursor = auto-pan | View follows cursor | PENDING_DEVICE |
| FAB opens system keyboard | IME appears | PENDING_DEVICE |
| System keyboard text → Mac | Characters typed on Mac | PENDING_DEVICE |
| Haptic on physical buttons | Vibration feedback | PENDING_DEVICE |
| Haptic on mode toggle | Vibration feedback | PENDING_DEVICE |
| Haptic on drag start | Strong vibration | PENDING_DEVICE |

## Next Steps

- On-device validation of all 5 features
- Fine-tune scroll acceleration (2.2f may need adjustment)
- Fine-tune drag threshold (350ms / 15px may need adjustment)
- Consider adding haptic to KeyCap presses (virtual keyboard)
- Consider double-tap-to-drag alternative gesture
