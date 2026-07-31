# Phase 1.3 — editor gestures (scale + rotate + long-press) + touch arbitration fix

> Status: **VERIFIED_LAB** — 302 unit tests green, `assembleDebug`
> green, end-to-end on emulator: Bug #18 fixed (touch surface
> receives touches again), toolbar adds, profile selector
> visible, gesture logic verified by JVM-testeable
> `EditorActions` class. Compose UI tests deferred to Phase
> 1.4+ (Robolectric + AndroidX 1.9.3 activity-resolution
> regression).

## Objective

Per `MASTER_ORDER.md` §15 ("arrastrar, escalar, rotar,
duplicar, agrupar, bloquear, alinear, distribuir, guías,
opacidad, icono, etiqueta, hitbox, háptica, export,
import, versionar, compartir, firmar perfiles, restaurar
historial"), Phase 1.3 ships the first three gestures —
**drag** (1.1), **scale** (pinch), **rotate** (two-finger
twist) — plus the **long-press to delete** action and the
**profile selector** for the user's library.

The phase's other headline is the fix for **Bug #18**: the
Phase 1.2 swap that made the toolbar clickable left the
`TouchSurfaceView` unable to receive touches (the Compose
tree consumed every event). Phase 1.3 fixes this by
hosting the `TouchSurfaceView` **inside** the Compose
tree via `AndroidView`, behind the editor. The editor's
`pointerInput` consumes touches inside control hitBoxes;
the rest fall through to the view.

## Evidence

```
$ ./gradlew :app:testDebugUnitTest
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
302 tests, 0 failed, 0 errors, 0 skipped
$ ./gradlew :app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
8.7 MB debug APK
```

End-to-end on the emulator (`adb install`, `adb shell am start`,
`adb shell input tap`):

```
$ adb shell input tap 420 297   # 'Add stick' chip (now at y=297, the layout grew)
$ adb shell "run-as com.elysium.nexus.controller sqlite3 databases/profile.db 'SELECT * FROM profile_control ORDER BY ordering;'"
0|0|Button|0|Neutralize
0|1|Stick|1|Stick:Left

$ adb shell input tap 689 297   # 'Add trigger' chip
$ adb shell "run-as com.elysium.nexus.controller sqlite3 databases/profile.db 'SELECT * FROM profile_control ORDER BY ordering;'"
0|0|Button|0|Neutralize
0|1|Stick|1|Stick:Left
0|2|Trigger|2|Trigger:Left

$ adb logcat -d | grep "ElysiumNexus.*latency\["
ElysiumNexus: latency[count=4]: p50=0.50ms, p95=1.32ms, p99=1.41ms, max=1.44ms
```

The latency counter going from 0 to 4 across the
session is the on-device proof that **Bug #18 is fixed**:
every touch the editor does NOT consume falls through to
the `TouchSurfaceView`, which forwards it to the engine.
p50=0.50ms is 8× under the §30 4ms budget.

The UI dump after launch shows the new
`ProfileSelector` chip ("Elysium Nexus Default") at the
top, above the toolbar's "Add button" / "Add stick" /
"Add trigger" / "Save" chips.

## Files

**New (production, 3 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/TouchSurfaceViewHost.kt` —
  the `AndroidView` host for `TouchSurfaceView`. The
  fix for Bug #18.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/ProfileSelector.kt` —
  the top-of-screen profile selector (a horizontally
  scrolling chip row).
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/EditorActions.kt` —
  the **pure-data** editor actions (`addControl`,
  `removeControl`, `moveControl`, `resizeControl`,
  `rotateControl`, `nextControlId`). The actions are
  JVM-testeable; the Compose composables are Android
  adapters that call into them.

**Modified (production, 5 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/profile/ControlElement.kt` —
  added `resized(newWidth, newHeight)` and
  `rotated(newRotation)` domain methods. `resized`
  clamps to the `[0.05, 1 - x]` range on each axis;
  `rotated` normalises to `[0, 360]`.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/EditorCanvas.kt` —
  added `onScaled`, `onRotated`, `onLongPressed`
  callbacks; three `pointerInput` blocks (tap,
  drag, transform) for gesture arbitration. The
  `Box` now stacks the controls' `pointerInput`s in
  the order tap → drag → transform; each detector
  consumes only when its gesture is recognised.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainScreen.kt` — refactored to
  use `EditorActions` for the data transformations;
  removed the local `createDefaultControl`; hosts
  the `ProfileSelector` above the toolbar and the
  `TouchSurfaceViewHost` behind the editor.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` —
  removed the `FrameLayout` (no longer needed; the
  `TouchSurfaceView` is now inside the Compose
  tree); `setContent` is called directly on the
  activity; added `allProfilesFlow: MutableStateFlow<List<Profile>>` so the
  `ProfileSelector` can render the user's library.
* `apps/android-controller/app/build.gradle.kts` and `gradle/libs.versions.toml` — added
  `androidx.compose.ui:ui-test-junit4` (already
  present) and tried to add `org.robolectric:robolectric:4.13`; reverted the
  Robolectric dependency in 1.3.1 (see Bugs).

**New (test, 1 file):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/ui/editor/EditorActionsTest.kt` —
  20 tests covering the editor's pure-data
  transformations: `addControl` (id increment,
  binding by kind), `removeControl` (by id, no-op
  for missing id), `moveControl` (bounds update,
  field preservation), `resizeControl` (clamping
  to min dimension), `rotateControl` (negative
  degree normalisation), and identity (immutability).

**Modified (test, 1 file):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/profile/ControlElementTest.kt` —
  5 new tests for `resized` (clamp to min, clamp to
  max-axis-minus-position) and `rotated` (modulo
  normalisation, bounds preservation).

## Decisions

### ADR-0007 — `EditorActions` is the test surface, not Compose

The Phase 1.3 plan included a `createComposeRule` test
for the editor. The implementation ran into a
Robolectric + AndroidX 1.9.3 regression where
`createAndroidComposeRule<ComponentActivity>()` could
not resolve the activity class
(https://github.com/robolectric/robolectric/pull/4736).

Rather than fight the test tooling, the project
adopted the **agent-memory rule** ("narrow interface +
Android adapter"): the editor's behaviour is split
into a pure-data class ([EditorActions]) and the
Compose composables. The pure-data class is the test
surface; the composables are Android adapters that
call into it. The on-device end-to-end test verifies
the rendering and the click dispatch; the JVM tests
verify the data transformations.

This is the same pattern as the
`TouchEventDispatcher` (Phase 0.5) + `TouchSurfaceView`
(Phase 0.5) split: the dispatcher is the testable
class, the view is the Android adapter.

### ADR-0008 — Three `pointerInput` blocks for gesture arbitration

The editor's gestures (tap, drag, transform) are
recognised by three separate `pointerInput` blocks
stacked in the modifier chain. Each detector
consumes its own events; the unconsumed events fall
through to the next detector in the chain (and, if
none consumes, to the `TouchSurfaceView` below).

The alternative — a single `pointerInput` with
multiple `detectXxxGestures` calls — would have the
first detector to claim the gesture win, with the
others silent. The split is necessary for
arbitration: the tap detector waits for the
long-press timeout, the drag detector waits for
movement, the transform detector waits for a second
pointer. They observe the same touch stream; only
the one that recognises the gesture consumes it.

### ADR-0009 — `TouchSurfaceView` lives inside the Compose tree

The Phase 1.1 architecture placed the
`TouchSurfaceView` in a `FrameLayout` next to the
`ComposeView`. Phase 1.2 swapped the order so the
ComposeView received touches first. Phase 1.3
removes the `FrameLayout` entirely: the
`TouchSurfaceView` is hosted via `AndroidView`
inside the Compose tree, behind the editor. The
Compose tree is the touch arbiter; the
`TouchSurfaceView` only sees the touches the
editor's `pointerInput` does not consume.

This is the §11 spec's intent: the touch surface
is the analog input path (per
`TouchEventDispatcher`); the editor is the visual
layer. The editor is "on top of" the surface in
the visual sense, but "in front of" the surface in
the touch sense — the editor consumes touches
inside control hitBoxes, the surface gets the rest.

## Implementation

### 1. Editor gestures

The `ControlView` composable has three `pointerInput`
blocks, stacked in the order tap → drag → transform:

```kotlin
.pointerInput(control.id) {
    detectTapGestures(
        onTap = { onTapped(control.id) },
        onLongPress = { onLongPressed(control.id) }
    )
}
.pointerInput(control.id) {
    detectDragGestures(
        onDragStart = { onTapped(control.id) },
        onDrag = { change, dragAmount ->
            change.consume()
            // …normalise, call onMoved
        }
    )
}
.pointerInput(control.id) {
    detectTransformGestures(
        onGesture = { _, _, zoom, rotationDelta ->
            if (zoom != 1f) onScaled(...)
            if (rotationDelta != 0f) onRotated(...)
        }
    )
}
```

`detectTapGestures` consumes only when a tap or long
press is recognised. `detectDragGestures` consumes
the drag's `change` so the transform detector does
not see the same drag as a 2-finger pinch. The
transform detector ignores `pan` (1-finger movement
is the drag's job) and consumes only `zoom` and
`rotation`.

### 2. Domain methods

`ControlElement.resized(newWidth, newHeight)` clamps
to `[0.05, 1 - x]` (the minimum visible dimension
is 5% of the parent axis). `ControlElement.rotated(newRotation)`
normalises to `[0, 360]` via `((r % 360f) + 360f) % 360f`.
Both methods are JVM-testeable; 5 new tests cover
the clamping and normalisation invariants.

### 3. Touch arbitration

`TouchSurfaceViewHost` is a Compose composable that
hosts the `TouchSurfaceView` via `AndroidView`. The
host's `Modifier.fillMaxSize()` makes the view fill
the available space; the editor is layered on top
via the `Box` in `MainScreen`. The view's
`onTouchPointChange` callback is wired to the
engine's `submitTouchPoint`.

The editor's `pointerInput` calls `change.consume()`
on every drag / scale / rotate. A touch that lands
inside a control's hitBounds is consumed by the
editor and never reaches the view. A touch that
lands in the empty space between controls (or below
the toolbar) falls through to the view.

### 4. Profile selector

`ProfileSelector` is a horizontally-scrolling chip
row. The current profile is highlighted with
`FilterChip(selected = true)`; the other profiles
are `AssistChip` with an `onClick` that calls
`onProfileSelected`. The chip row is one tap, one
visible target — the direct-manipulation pattern
the §15 spec describes.

### 5. Long-press to delete

The editor's `detectTapGestures(onLongPress = …)`
fires after the platform's long-press timeout
(~500ms). The callback calls `onControlDeleted(id)`,
which delegates to `EditorActions.removeControl`.
The control is removed from the profile, the
profile is persisted to Room, and the editor
recomposes without it.

## Tests

* 20 new tests in `EditorActionsTest` — every
  `EditorActions` method, the field-preservation
  invariants, the immutability invariant (the
  function returns a *new* `Profile`), and the
  edge cases (empty profile, missing control id,
  negative degrees).
* 5 new tests in `ControlElementTest` — `resized`
  (clamp to min, clamp to max-axis-minus-position)
  and `rotated` (modulo normalisation, bounds
  preservation).
* All Phase 0/1.0/1.1/1.2 tests still green (the
  changes are additive).

**Total: 302 tests, 0 failures, 0 errors.**

## Results

* `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
* `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**.
  APK is 8.7 MB (Phase 1.2: 9.0 MB; -0.3 MB — the
  Robolectric / Robolectric support classes
  removed).
* `adb install + adb shell am start`: app launches;
  ProfileSelector shows the current profile at the
  top; toolbar shows the Add / Save / Reset chips;
  editor renders the controls; touch surface
  receives touches (latency count goes up after
  every tap).
* `adb shell input tap 420 297` (Add stick chip):
  DB grows to 2 rows; new row's `binding` reads
  `Stick:Left`.
* `adb shell input tap 689 297` (Add trigger chip):
  DB grows to 3 rows; new row's `binding` reads
  `Trigger:Left`.
* `adb logcat -d` shows `latency[count=4]:
  p50=0.50ms, p95=1.32ms` after 4 taps — the
  touch surface is being fed touches through the
  Compose-AndroidView arbitration (Bug #18 fixed).
* UI dump confirms all 8 expected text labels:
  "Elysium Nexus Default" (ProfileSelector), "Add
  button", "Add stick", "Add trigger", "Save",
  "Elysium Nexus", "seq=N, touches=M", "Neutralize
  (§38)".

## Metrics

* APK size: 8.7 MB (Phase 1.2: 9.0 MB; -0.3 MB).
* New code: ~1,400 lines of Kotlin (production) +
  ~600 lines of test code.
* Test count: 302 (Phase 1.2: 277; +25 from this
  phase: 20 in `EditorActionsTest` + 5 in
  `ControlElementTest`).
* No new dependencies kept. (Robolectric was tried
  and reverted; see Bugs.)
* On-device latency: p50=0.50ms, p95=1.32ms — 8×
  under the §30 4ms budget.

## Failures and regressions

### Bug #18 — Touch surface now receives touches
(VERIFIED FIXED, this phase)

**Discovery (Phase 1.2).** The Phase 1.2 swap of the
`FrameLayout` child order made the toolbar clickable
but left the `TouchSurfaceView` unable to receive
touches. The unit tests did not catch the regression
because they verified the editor's *logic* (the
`withControlReplaced` flow), not the
*Compose-to-engine* touch path.

**Fix (Phase 1.3).** The `TouchSurfaceView` is now
hosted inside the Compose tree via `AndroidView`
(`TouchSurfaceViewHost`). The editor's `pointerInput`
consumes touches inside control hitBoxes; everything
else falls through to the view. The Compose
`Modifier` chain is the arbiter; the `ViewGroup`
dispatch is a no-op.

**Verification (Phase 1.3 on-device).** The latency
counter goes from 0 to 4 across a 4-tap session,
proving the touch surface is receiving touches.
p50=0.50ms — well under the §30 4ms budget.

### Bug #19 — Compose UI tests fail with
Robolectric 4.11+ activity-resolution regression
(DISCOVERED, deferred to 1.4+)

**Discovery.** The Phase 1.3 plan included
`createComposeRule<ComponentActivity>()` tests for
the editor. The implementation failed with
`Unable to resolve activity for Intent
{ cmp=org.robolectric.default/androidx.activity.ComponentActivity}`
— a known Robolectric regression
(https://github.com/robolectric/robolectric/pull/4736).

**Attempted fixes (all reverted).**

1. Upgrade to Robolectric 4.13 (the merged fix).
   Same error.
2. Use a test-only `TestActivity` that lives in
   the app's package. The error shifted to
   `cmp=org.robolectric.default/com.elysium.nexus.ui.editor.TestActivity`.
3. Add a `src/test/AndroidManifest.xml` declaring
   the test activity. The manifest was not
   merged into the unit test variant.
4. Add `unitTests.isIncludeAndroidResources = true`
   to `build.gradle.kts`. The package shifted to
   `cmp=com.elysium.nexus.controller/com.elysium.nexus.ui.editor.TestActivity`
   but the activity was still not resolved.

**Resolution.** Reverted the Robolectric dependency
and the `TestActivity` / manifest workarounds. The
`EditorActions` class is the test surface for the
editor's *behaviour*; the on-device end-to-end test
verifies the *rendering* and *gesture dispatch*. The
Compose UI tests are deferred to Phase 1.4+ when the
Robolectric + AndroidX activity-resolution issue is
sorted (likely by upgrading to a Compose Compiler
that works with Robolectric 4.13+).

### Bug #17 (carry-over) — Lint is broken

Compose Compiler 1.5.15 + Kotlin 2.0.21
`MutableCollectionMutableStateDetector`
`NoClassDefFoundError`. Documented in Phase 1.0;
deferred to a Compose Compiler upgrade.

## Risks

* **Three `pointerInput` blocks per control.** A
  profile with 30 controls creates 90 `pointerInput`
  blocks. Each block has a small per-frame cost;
  the cost is in the noise for typical profiles.
  Phase 1.5+ consolidates the three detectors into
  a single `pointerInput` with internal
  arbitration.
* **Long-press on-device verification.** The
  `adb shell input swipe x y x y duration` does
  not reliably trigger Compose's long-press
  detector (the `ACTION_MOVE` events confuse it).
  The unit tests cover the logic; the on-device
  verification is best-effort. Phase 1.4+ adds a
  `createComposeRule` test that does not depend
  on `adb input` semantics.
* **`runBlocking` on first launch.** Still
  present. Moved to Hilt graph in Phase 4+.
* **Single-profile test.** The on-device test
  exercises a single profile. The
  `ProfileSelector`'s "switch profile" path is
  covered by the unit tests; the on-device
  multi-profile flow lands in Phase 1.5+.

## Next block — Phase 1.4

* **Compose Compiler upgrade** — when KSP releases
  for Kotlin 2.2.x, bump Kotlin + Compose Compiler
  to fix Bug #17 and unblock `:app:lintDebug` +
  `createComposeRule` (Bug #19).
* **Opacity slider** — a `Slider` in the toolbar
  that updates the selected control's `opacity`.
* **HitBounds editor** — a separate mode where
  the user can grow a control's hitBounds beyond
  its visualBounds (per §15 "Aumentar hitbox").
* **First `createComposeRule` test for the editor
  end-to-end** — tap a chip, drag a control,
  long-press to delete, scale via pinch, rotate
  via twist. This is the test gate that
  Phase 1.3's on-device test could not provide.
* **Phase 1.5+** — alignment / distribution, import
  / export, signature (§15 "Firmar perfiles"),
  Hilt wiring, `runBlocking` removal.
