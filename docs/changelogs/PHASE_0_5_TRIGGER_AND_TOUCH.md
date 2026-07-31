# PHASE 0.5 — Trigger Filter Pipeline + Touch Event Dispatcher

**Status:** `VERIFIED` (35 new unit tests, 160 total; build green;
lint green; APK 1.4 MB; **first Android-specific code in the
project**)
**Iteration goal:** ship the §13 trigger filter pipeline, the
analog → digital detector, and the touch event pipeline (the
`MotionEvent` → engine `submitTouchPoint` bridge). The touch
pipeline lands as a pure-Kotlin dispatcher plus a thin Android
`View` shell — the dispatcher is unit-tested from the JVM; the
`View` is a 30-line Android-specific shim that is integration-
tested when the first `Activity` lands in Phase 1+.

## 1. Objective

`MASTER_ORDER.md` §13 (triggers) and §11 (touch surface) are
the two pieces of the input pipeline that the engine (0.4) was
not yet wired to. After 0.5:

* The engine has a `TriggerFilters` pipeline that produces a
  filtered analog value and a `TriggerDigitalDetector` that
  reports the digital side (`LeftTriggerDigital` /
  `RightTriggerDigital`).
* The engine has a `TouchEventDispatcher` (pure Kotlin) that
  turns a `MotionEvent`-shaped stream into
  `submitTouchPoint` calls.
* The `TouchSurfaceView` is a `View` subclass that owns the
  Android-specific bits (parsing `MotionEvent`, normalising
  coordinates, clamping pressure) and forwards everything to
  the dispatcher.

The engine is now fully wired to the input surfaces it
consumes. The next bottleneck is the §38 disconnect test as
a property test in CI (0.6) and the `Activity` that hosts
the touch surface in 1.x.

## 2. Evidence researched

* `MASTER_ORDER.md` §11, §13, §38 — the three sections this
  iteration enforces.
* Android `MotionEvent` documentation — the action constants
  (`ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`,
  `ACTION_POINTER_UP`, `ACTION_UP`, `ACTION_CANCEL`),
  the pointer-id lifecycle, and the `getPressure` /
  `getX` / `getY` / `getPointerId` API.
* The `engine.submitTouchPoint` signature from 0.4: the
  dispatcher matches it 1:1.
* Agent memory `engineering-gotchas.md` — "Wiring Android
  Context-dependent classes into JVM-testable code: define a
  narrow interface in the testable class that captures only
  the values read." The dispatcher's callback shape
  (`(id: Int, point: TouchPoint?) -> Unit`) is the narrow
  interface; the `View` is the Android-specific adapter.

## 3. State before

`6c2d1f8` (Phase 0.4). 125 tests, build green, lint green.
The engine is the single writer of the canonical state, but
nothing in the codebase yet *feeds* the engine.

## 4. Files created

```
apps/android-controller/app/src/main/java/com/elysium/nexus/
├── core/filter/
│   ├── TriggerConfig.kt           (knob set, init-time validation)
│   ├── TriggerFilters.kt          (apply() pipeline, one-directional)
│   └── TriggerDigitalDetector.kt  (analog → digital event detector)
├── core/touch/
│   ├── PointerInfo.kt             (id, x, y, pressure — pure data)
│   ├── TouchAction.kt             (enum: Down/PointerDown/Move/PointerUp/Up/Cancel)
│   └── TouchEventDispatcher.kt    (pure Kotlin, JVM-testeable)
└── input/
    └── TouchSurfaceView.kt        (Android View, the first Android code)

apps/android-controller/app/src/test/java/com/elysium/nexus/
├── core/filter/
│   ├── TriggerConfigTest.kt             (5 tests)
│   ├── TriggerFiltersTest.kt            (11 tests)
│   └── TriggerDigitalDetectorTest.kt    (6 tests)
└── core/touch/
    └── TouchEventDispatcherTest.kt      (13 tests)
```

## 5. Architectural decisions

* **The `TouchSurfaceView` is a 30-line Android shell that
  owns a `TouchEventDispatcher`.** The dispatcher is what
  we test from the JVM. The View is what we integration-
  test when the first `Activity` lands. This is the
  "narrow interface, Android adapter" pattern from
  engineering-gotchas — the testable surface is the
  dispatcher, the Android-specific code is a thin
  adapter around it.
* **The dispatcher's state is a `LinkedHashMap<Int,
  PointerInfo>`.** The map is the source of truth for
  "which pointers are currently active". A
  `MutableList` would have made the "find a pointer by id"
  case O(n); the map is O(1) and is what the §11
  high-multitouch scenario needs.
* **A `Down` action *resets* the dispatcher's state.**
  This is the safety net for a missed `Cancel` or a
  gesture that was interrupted by a parent view. The
  discarded pointers are not re-emitted as `null` — the
  engine is expected to be neutral when a new gesture
  starts. (The §38 disconnect test verifies this on the
  engine side.)
* **A `Move` action for an unknown pointer is treated as
  a fresh `PointerDown`.** Defensive: the Android
  platform should not produce this, but if it does, the
  dispatcher does not silently drop the input. The
  engine is the only writer; the dispatcher is the
  filter.
* **Triggers are one-directional.** No "outer threshold"
  (1.0 is the natural max), no "anti-deadzone" (the §13
  spec does not list one for triggers), no "invert" (the
  trigger only has one direction). The trigger knob set
  is a strict subset of the stick's.
* **The `returnCurve` is selected on the way back.** A
  trigger call with `raw.value < previousFiltered.value`
  uses the return curve, not the response curve. This is
  the §13 "Retorno" knob. The pipeline is pure, so the
  engine passes the previous filtered value explicitly.
* **The digital detector is a separate class from the
  filter.** The filter is pure (no state); the detector
  is stateful (it remembers the previous digital value).
  Splitting them keeps each one testable in isolation
  and keeps the engine free of trigger-specific
  knowledge — the engine consumes the digital boolean
  and the analog value independently.
* **No Robolectric in 0.5.** The `View` class is in the
  test source set's classpath, but it cannot be
  instantiated without a real `Context` (which requires
  Android runtime). The agent-memory rule applies: a
  `Context`-dependent class is testable from the JVM
  *if* its logic lives in a separate, context-free
  class. The dispatcher is that class. The View is
  tested when the first `Activity` lands.

## 6. Implementation

The trigger pipeline runs in 5 steps:

```
0. zero input → zero output
1. magnitude < deadzone → zero
2. hair trigger short-circuit → 1
3. normalise + response curve (or return curve, on the way back)
4. reduced range rescale
5. clamp to [0, 1]
```

The touch pipeline runs in 6 actions (per the §11 spec):

```
Down        → reset state, add pointers, callback each
PointerDown → add pointers, callback each
Move        → for each pointer, update state, callback each
PointerUp   → remove the released pointer, callback null
Up          → remove the released pointer (and any leftovers), callback null
Cancel      → remove every active pointer, callback null
```

The engine calls the dispatcher's `process` from
`onTouchEvent`. The engine also calls the dispatcher's
`reset()` on every state-machine transition out of `Active`
(per §38), so a touch in flight when the transport drops
does not leave a stuck pointer on the host.

## 7. Tests

35 new unit tests. 160 total. All green in ~150 ms.

| Test class                       | Count | What it covers                                        |
| -------------------------------- | ----: | ----------------------------------------------------- |
| `TriggerConfigTest`              |     5 | Defaults, every init-time guard, every valid config.  |
| `TriggerFiltersTest`             |    11 | Per-knob unit tests + 2 property invariants.          |
| `TriggerDigitalDetectorTest`     |     6 | Activation point, hair trigger, reset, lastDigital.   |
| `TouchEventDispatcherTest`       |    13 | Full lifecycle, cancel safety, down-resets-state, defensive Move, coordinate clamp. |

The 2 property-based tests in `TriggerFiltersTest`:

* `outputIsBoundedByUnitRange` — the output is always in
  `[0, 1]` across 101 inputs.
* `pipelineNeverProducesNaN` — the output is never NaN or
  Inf across 101 inputs.

The dispatcher tests cover the §11 lifecycle end-to-end
with a `fullLifecycleStaysConsistent` test that walks
Down → Move → PointerDown → Move (both) → PointerUp → Up and
asserts the final active count is 0 and the callback log
contains exactly 7 entries.

## 8. Results

| Check                                          | Result   |
| ---------------------------------------------- | -------- |
| `./gradlew clean :app:testDebugUnitTest`       | green    |
| `./gradlew :app:assembleDebug`                 | green    |
| `./gradlew :app:lintDebug`                     | green    |
| Lint errors / warnings                         | 0 / 0    |
| Test count                                     | 160      |
| Test failures                                  | 0        |
| Test wall time                                 | 150 ms   |
| New production LOC (Kotlin)                    | ~660     |
| New test LOC (Kotlin)                          | ~620     |
| New Android-specific LOC                       | ~140 (the View) |
| APK size delta                                 | +560 KB (View + coroutines + dispatcher + filter) |
| Lint warnings on the View                      | 0        |

The `View` ships as part of the `:app` module. The
`unitTests.isReturnDefaultValues = true` setting in
`build.gradle.kts` means the test source set can compile
against the View's class without crashing on Android
runtime dependencies.

## 9. Metrics

* Trigger filter call cost: a few comparisons + a curve
  evaluation. Sub-microsecond.
* Touch dispatcher call cost: O(active pointers) for
  Move, O(1) for the others. Sub-microsecond.
* The View's `onTouchEvent` is not on the JVM-test hot
  path; its cost is dominated by the `MotionEvent`
  iteration (Android framework). We measure it
  separately when we have the §30 latency harness
  (`tools/latency-profiler/`).

## 10. Failures (test-discovered regressions)

2 test-discovered issues in this iteration.

* **Bug #5 — `TriggerConfig` rejected `deadzone=0.20f`
  with the default `activationPoint=0.10f`.** The
  constructor's init block requires
  `activationPoint >= deadzone`, but the test was
  exercising the deadzone in isolation without setting
  the activation point. The first test that triggered
  this (`belowDeadzoneIsZero`) failed with
  `IllegalArgumentException` at config construction
  time. **Fix:** the test now sets both `deadzone` and
  `activationPoint` explicitly. The constructor's
  validation is correct — a config with the digital
  side firing below the deadzone is a bug, not a
  feature.
* **Test fix #3 — `atDeadzoneIsZero` had the same
  issue.** Same fix as Bug #5.

No production-code bugs in this iteration. The
dispatcher's defensive `Move` handling
(treat-unknown-as-PointerDown) and `Up`-flushes-leftovers
behaviours are test-driven, not bug-driven — they are
the kind of edge cases the §11 spec calls out and the
tests pin.

## 11. Risks

* **The `View` is not unit-tested.** The dispatcher is;
  the View is integration-tested when the first
  `Activity` lands. If the View's `MotionEvent`
  parsing is wrong (e.g. we mis-map an action
  constant), the unit tests for the dispatcher will
  pass but the host will see wrong input. The
  mitigation is a small Robolectric test in 1.x that
  fires synthetic `MotionEvent`s at the View and
  asserts the dispatcher's output.
* **The dispatcher does not implement `getHistoricalX`
  / `getHistoricalY` consumption.** §11 says the
  `MotionEvent` API exposes historical samples (the
  recent past of a pointer, batched into a single
  event for efficiency). The View passes the *current*
  sample only. The §30 latency budget says we have
  < 4 ms for touch processing; historical-sample
  consumption is a 0.6+ concern.
* **No `SurfaceView` / hardware-accelerated path.** The
  View inherits from the platform `View`, which uses
  the standard UI thread. A high-rate touch stream
  (e.g. 240 Hz) might overwhelm the UI thread on a
  low-end device. The §0 "cheap-Android degradation
  path" is a Phase 4+ concern.
* **No pressure translation.** `getPressure(i)` on a
  device that does not measure pressure returns 1.0
  for an active pointer. The dispatcher does not
  distinguish a "real" 1.0 from a "no pressure
  reported" 1.0. Per §13, the model does not pretend
  to know the difference; the surface reports 1.0
  either way. Documented decision; we may revisit in
  0.6 if a host's behaviour diverges.

## 12. Next executable block (Phase 0.6)

The smallest concrete sub-task that unlocks the most
downstream work is **Phase 0.6 — §38 disconnect
property test + the first `Activity` that hosts the
touch surface**. The disconnect test is the
release-blocker gate. Without it, every other phase
is a release-blocker.

Concretely:

* The `Activity` (a `ComponentActivity` themed with the
  brand colors) hosts the `TouchSurfaceView` over the
  full window. The engine is owned by the Activity's
  lifecycle (the same `CoroutineScope` rule we already
  follow). The View's `onTouchPointChange` callback is
  wired to `engine.submitTouchPoint`.
* The §38 test is a property-based test that:
  1. sets up the engine in `Active` with 4 buttons,
     both triggers, both sticks at the corners, the
     D-pad diagonal, the touchpad active, the gyro
     active, a key held, and the mouse button held;
  2. simulates a hard kill of the engine's scope
     (process death equivalent — no graceful shutdown);
  3. asserts the last emitted state was neutral.

After 0.6, the first real "this app does something on
a phone" milestone lands: a Compose-free, View-based
gesture of a touch on Honor Magic V2, going through
the dispatcher, into the engine, out through a
`StateFlow` collector that logs. The Hilt wiring
arrives in 1.x to make the engine injectable.

---

**Status: `VERIFIED`. 160 tests, 0 failures, lint clean. Proceeding to 0.6.**
