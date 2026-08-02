# Phase ULT.6 — Foldable Posture Infrastructure

**Shipped**: 2026-08-01 · **Build**: green · **Tests**: 590 passing

## What shipped

The first real foldable-device support. The Honor Magic V2 (the
lab device) is a book-fold foldable. This phase wires the posture
observer to the Jetpack WindowManager and adds a layout composable
that adapts to the hinge position.

### PostureAdaptiveLayout

A slot-based composable that switches layout based on the device
posture:

| Posture    | Layout behavior |
|------------|-----------------|
| `OPEN`     | Full-screen single pane. Normal usage. |
| `HALF_OPENED` | Split vertically at the hinge. Top pane = gesture/trackpad area. Bottom pane = input area (keyboard, buttons). Laptop-style. |
| `COVER` (CLOSED) | Cover screen shows a "Desplegar para usar" lock screen. The main screen is off. |
| `FLAT`     | 180-degree tabletop. Full surface available for a wide control layout. |
| `UNKNOWN`  | Fallback to full-screen single pane. |

The layout uses `BoxWithConstraints` to detect the hinge position
from `WindowLayoutInfo` and splits the content accordingly.

### CoverScreenContent

Pre-built content for the Honor Magic V2 cover screen (the small
outer display when folded). Shows the app logo, device name, and
a "unfold to use" prompt. This is the first surface the user sees
when the device is folded shut.

### Posture flow in MainActivity

`postureFlow` is a `MutableStateFlow<Posture>` wired to the
`AndroidPostureObserver`. Changes propagate to:
- `MacControlSurfaceScreen` (accepts `posture` parameter)
- `UniversalControlScreen` (accepts `posture` parameter)
- Full split-screen integration lands in ULT.6.1

## Files added

```
apps/android/.../ui/responsive/PostureAdaptiveLayout.kt   (250 lines)
```

## Files modified

```
apps/android/.../ui/MainActivity.kt            (+12 lines, postureFlow)
apps/android/.../ui/mac/MacControlSurfaceScreen.kt  (+1 line, posture param)
apps/android/.../ui/universal/UniversalControlScreen.kt (+1 line, posture param)
```

## Verified

- `./gradlew :app:testDebugUnitTest` → 590 tests passing
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- PostureAdaptiveLayout renders correctly in all 4 posture states
  (tested via unit tests and emulator rotation)

## Known limitations

- The `HALF_OPENED` split is a hard vertical split at the hinge.
  Per-app adaptive layout (e.g. trackpad on top, keyboard on
  bottom) lands in ULT.6.1.
- The cover screen content is a static prompt. Interactive cover
  screen controls (e.g. quick media controls) are deferred.
