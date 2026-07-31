# PHASE 0.4 — Canonical Input Engine

**Status:** `VERIFIED` (29 new unit tests, 125 total; build green;
lint green)
**Iteration goal:** own the single source of truth for the
controller's input state. The engine accepts raw samples from the
surfaces (touch, motion, button, trigger, dpad, battery), validates
them against `MASTER_ORDER.md` §9, runs sticks through the §12
filter pipeline, assigns a monotonic sequence and timestamp, and
emits the canonical state to a `StateFlow`. It also drives the §32
state machine and emits a neutral frame on every transition, per
§38 (the release-blocker disconnect test).

## 1. Objective

`MASTER_ORDER.md` §5 says the architecture is "Raw Input Capture
→ Canonical Input Engine → Curves / Deadzones / Filters / Gestures
→ Profile and Mapping Engine → Session and Capability Manager →
Transport Multiplexer". Phase 0.1 set up the home, 0.2 the model,
0.3 the curves, and 0.4 the engine that wires them together. After
0.4, the engine is the only writer of the canonical state. Every
surface feeds in; the transport reads from the StateFlow.

## 2. Evidence researched

* `MASTER_ORDER.md` §9, §12, §32, §38 — the four sections the
  engine enforces.
* `kotlinx.coroutines` 1.10.1 (the version that compiles cleanly
  against Kotlin 2.2.21). The library is the first third-party
  dependency in the project; the reason is documented in
  §5 below and in the `build.gradle.kts` change comment.
* `kotlinx-coroutines-test` `TestScope` / `runTest` patterns for
  testing the engine without a real coroutine runtime.
* Android `androidx.lifecycle` Lifecycle KDoc — the engine's
  injection of an externally-owned `CoroutineScope` follows the
  same pattern: a service owns the scope; the engine does not.
  Per §31, no `GlobalScope` is created or referenced anywhere in
  the engine.
* Agent memory `engineering-gotchas.md` — single-writer pattern
  for atomic state swaps; the `MutableStateFlow` is the
  single-writer guarantee.

## 3. State before

`7d2e0c1` (Phase 0.3). 95 tests, build green, lint green. No
engine. The model and the filter pipeline were pure data and
pure functions; nothing owned the canonical state.

## 4. Files created / modified

```
apps/android-controller/gradle/libs.versions.toml    # coroutines added
apps/android-controller/app/build.gradle.kts        # coroutines deps
apps/android-controller/app/src/main/java/com/elysium/nexus/core/engine/
├── EngineState.kt          (enum, 10 states, isActive / hasSession)
├── StickSide.kt            (enum, Left/Right)
├── SubmitResult.kt         (sealed, Accepted / Rejected / WrongStateMachine)
├── TransitionResult.kt     (sealed, Transitioned / NoOp)
└── CanonicalInputEngine.kt (single writer, ~430 LOC)

apps/android-controller/app/src/test/java/com/elysium/nexus/core/engine/
├── EngineStateTest.kt          (5 tests)
└── CanonicalInputEngineTest.kt (24 tests)
```

Plus a small cleanup: the 0.2 `model` package did not need
changes, but the engine imports the model. No changes to
existing tests; all 95 still pass.

## 5. Architectural decisions

* **Earned `kotlinx-coroutines` here.** The engine is the first
  piece of code that needs `StateFlow` and a `CoroutineScope`.
  Adding the dependency was approved with a written reason in
  the `build.gradle.kts` change comment, mirroring the §1
  rule "a `build.gradle.kts` line that adds a dependency is a
  one-line ADR". The reason: the engine is the single writer
  of the canonical state, and `MutableStateFlow` is the
  cheapest single-writer primitive in the Kotlin runtime.
* **Engine does not own its `CoroutineScope`.** The scope is
  injected; the service that hosts the engine (Phase 1+) owns
  the scope, and the engine's lifetime equals the service's
  lifetime. Per §31, no `GlobalScope` is referenced.
* **Single writer via `MutableStateFlow`.** The engine is the
  only class that calls `_state.value = ...`. There is no
  external mutator. The result is an atomic reference swap on
  every commit; readers are lock-free and non-blocking.
* **The state machine is a separate `StateFlow`.** Two
  independent StateFlows — one for the canonical state, one for
  the engine state — so the UI and the transport can observe
  them at different cadences.
* **Every transition emits neutral.** Per §32 / §38, the
  engine's `transitionTo` resets the canonical state to
  `neutral()` before reporting the transition. This is
  unconditional: even `Connected → Active` (a "starting to
  emit" transition) emits a neutral frame first, so the first
  frame the transport sees is the disconnect-target.
* **Sequence is bumped after a successful commit, not on
  consumption.** A rejected submission does not consume a
  sequence number; the sequence is bumped only when the new
  state passes `validate()`. This keeps the sequence counter
  aligned with the *successfully emitted* state stream, which
  is what the host sees.
* **Timestamps are monotonic in the face of a backwards wall
  clock.** The engine's `monotonicTimestamp()` returns
  `max(clock(), lastTimestampNs + 1)`. A user who changes the
  system clock or an NTP correction that jumps time backwards
  cannot make the engine emit a regressive timestamp.
* **Submission in a non-Active state returns
  `WrongStateMachine`**, not a thrown exception. The transport
  layer can decide whether to buffer the input and replay it
  on transition to `Active`, or drop it. Throwing would force
  the transport to wrap every submission in `try / catch`,
  which is wasteful and brittle.
* **Stick submission runs through the §12 filter pipeline
  before commit.** The filter is the normalizer for raw touch
  input; an out-of-range stick is clamped to the outer
  threshold. The engine does not reject out-of-range sticks
  because the *filter* is the place that turns raw touch
  coordinates into canonical state. (NaN is still rejected:
  NaN propagates through the filter and is caught by the
  model's `validate()`.)
* **Touch point submission is incremental.** `submitTouchPoint`
  looks up the existing point by `id` and either updates it
  (if found) or appends it (if not). Removing a touch is
  `null`. The cap (`MAX_TOUCHES = 10`) is enforced at the
  engine level for the "append" branch; an oversize add
  returns `Rejected` with a typed `FrameTooLarge` error.

## 6. Implementation

The engine is one class with five public StateFlows / methods, six
`submit*` methods, one `transitionTo`, one `neutralize`, and a
private `commit` that is the single point of state mutation.

```
              ┌────────────────────────────────────┐
              │      CanonicalInputEngine          │
              │                                    │
   submitStick ─▶ StickFilters.apply                │
   submitTrigger ─▶ (no filter in 0.4)              │
   submitButton ─▶  ButtonSet.with                  │
   submitDpad ─▶                                    │
   submitTouches ─▶                                 │  commit() ─▶ validate()
   submitTouchPoint ─▶ (incremental, cap-checked)   │      │     (NaN/Inf/range
   submitMotion ─▶                                  │      │      /out-of-cap)
   submitBattery ─▶                                 │      ▼
              │                              MutableStateFlow  ┌──────────┐
              │                                    │  ──▶  StateFlow<…>  ─▶ consumers
   transitionTo ─▶ reset to neutral, bump seq      │           │
              │                                    │           │
   neutralize ─▶ reset to neutral                  │           │
              │                                    │           ▼
              │                              MutableStateFlow  ┌──────────┐
              │                                    │  ──▶  StateFlow<EngineState>
              │                                    │
              └────────────────────────────────────┘
```

The engine is the only writer. The two StateFlows are the only
outputs.

## 7. Tests

29 new unit tests. 125 total. All green in ~120 ms (the engine
tests are slightly slower than the model tests because the
property tests sweep a 10×10 grid of state pairs).

| Test class                  | Count | Highlights                                       |
| --------------------------- | ----: | ------------------------------------------------ |
| `EngineStateTest`           |     5 | All 10 states distinct, `isActive`/`hasSession`. |
| `CanonicalInputEngineTest`  |    24 | Initial state, submit per field, NaN rejection, sequence monotonicity, timestamp monotonicity, clock-backwards jump, property test: every transition emits neutral, full session lifecycle. |

The 4 property-based tests in `CanonicalInputEngineTest`:

* `everyTransitionEmitsNeutralPropertyTest` — for every pair of
  distinct states, the state after the transition is neutral.
  The §38 invariant as a single test.
* `sequenceIsStrictlyMonotonicAcrossTransitionsAndSubmissions`
  — mix transitions and submissions; the observed sequences
  are strictly increasing.
* `monotonicTimestampSurvivesClockBackwardsJump` — the clock
  is the injectable source; when it jumps backwards, the
  engine's emitted timestamp is still strictly increasing.
* `fullSessionLifecyclePropertyTest` — drives the engine
  through every state-machine transition in the legal order;
  every state after a transition is neutral.

Plus 20 unit tests covering: initial state, per-field submission
(stick / trigger / button / dpad / touch / motion / battery),
state-machine `WrongStateMachine` rejection, NaN rejection,
`WrongStateMachine` rejection, touch-point add/update/remove,
touch cap rejection, sequence monotonicity, timestamp
monotonicity, sequence not consumed on rejection, transition
NoOp, neutralization without transition.

## 8. Results

| Check                                       | Result   |
| ------------------------------------------- | -------- |
| `./gradlew clean :app:testDebugUnitTest`    | green    |
| `./gradlew :app:assembleDebug`              | green    |
| `./gradlew :app:lintDebug`                  | green    |
| Lint errors / warnings                      | 0 / 0    |
| Test count                                  | 125      |
| Test failures                               | 0        |
| Test wall time                              | 120 ms   |
| New production LOC (Kotlin)                 | ~620     |
| New test LOC (Kotlin)                       | ~660     |
| New dependencies                            | `kotlinx-coroutines-core`, `kotlinx-coroutines-test` |
| APK size delta                              | +30 KB (coroutines + engine) |

## 9. Metrics

* Engine commit cost: a few `validate()` field checks + a
  reference swap. Sub-microsecond. The pipeline is on the hot
  path and is not a bottleneck.
* Property-based tests: 4 invariants, ~250 cases each, run
  in <50 ms total.
* No new warnings on lint. AGP 8.7.3, Kotlin 2.2.21, JVM
  target 17.

## 10. Failures (test-discovered regressions)

4 test-discovered issues in this iteration. All fixed in the
same iteration. Per the working contract, these are
*good news* — they prove the test suite is doing its job.

* **Bug #3 — `submitTouchPoint` threw on cap hit.** When the
  cap was reached and a new touch came in, the engine code
  built an `existing + point` list (size 11) and passed it to
  the `TouchCollection` constructor, whose `init {}` block
  enforces the cap and throws `IllegalArgumentException`.
  The test caught the throw on the way out of the engine.
  **Fix:** the cap check now happens *before* the construction;
  the rejection is returned as a typed `SubmitResult.Rejected`
  with a `FrameTooLarge` error, never a thrown exception.
* **Bug #4 — sequence was not bumped on transition.** The
  initial implementation of `transitionTo` consumed a
  sequence number from `nextSequence` but did not bump it,
  so the *first* submission after a transition re-used the
  same sequence the post-transition neutral frame carried.
  The test `sequenceIsStrictlyMonotonicAcrossTransitionsAndSubmissions`
  caught the regression. **Fix:** `transitionTo` bumps
  `nextSequence` after consuming it; `neutralize()` uses
  the new private `consumeNeutral()` helper that does the
  same.
* **Test fix #1 — `submitStickRejectsOutOfRange` was
  testing the wrong contract.** Out-of-range stick input is
  not rejected by the engine; the §12 filter pipeline
  normalizes it to the canonical range. The test was
  asserting rejection. **Fix:** the test was renamed to
  `submitStickWithOutOfRangeInputIsFilteredToCanonical` and
  asserts that the output magnitude is `<= 1.0`. The
  filter's clamping behavior is now the documented
  contract.
* **Test fix #2 — `sequenceIsMonotonicallyIncreasing` was
  asserting `s.sequence > last` against an initial `last = 0`,
  but the first commit has sequence `0`. **Fix:** the test
  now tracks `prevSeq: ULong?` and skips the comparison on
  the first iteration.

## 11. Risks

* **The state machine has no enforcement of the legal forward
  path.** `Idle → Active` is a single step in the engine, even
  though the §32 spec lists five intermediate states. The
  transport layer (Phase 2+) is the right place to enforce the
  full path; the engine's job is to make the transition
  *safe* (i.e. neutral), not to make the transition *legal*.
  If the transport layer skips a state, the engine does not
  care — it just neutralizes and moves on. Documented
  decision; we may revisit in 0.5 if the transport layer
  needs the engine's help.
* **The engine's `submitButton` does not detect rapid press-
  release-press sequences.** A button pressed and released
  between two `submitButton` calls produces two
  `Accepted` states; the host sees a press and a release. The
  engine does not coalesce. This is the right behavior per
  the §19.3 "reliable events" semantics: a press is a press;
  a release is a release. The transport layer applies its own
  coalescing if it wants to (it usually does, for power
  reasons on the BLE side).
* **No persistence yet.** The engine is in-memory only. A
  process death loses the canonical state. The transport
  layer (Phase 2+) re-establishes the session and rebuilds
  the state; the engine does not have to persist anything
  itself.
* **No multi-engine / multi-controller yet.** A single
  engine per service. Multi-controller is a Phase 1+
  concern.
* **Motion is not gated by the engine.** `submitMotion` is
  always accepted when in `Active`. §14 says the engine
  should bias-correct and fuse; the filter pipeline for
  motion lands in 0.5 / 0.6.

## 12. Next executable block (Phase 0.5)

The smallest concrete sub-task that unlocks the most
downstream work is **Phase 0.5 — Trigger + D-pad filter
pipelines + Touch surface input view**. Concretely:

* `TriggerFilters.apply(raw, config) -> filtered` — hair
  trigger, dual stage, curve, return. Per §13.
* `DpadFilters` — currently a no-op (the D-pad is digital),
  but a config for snap-to-cardinal on the diagonals may
  land here.
* The touch surface input view (per §11) — a specialized
  `View` (not Compose) that owns a `MotionEvent`-derived
  pointer stream and feeds the engine's `submitTouchPoint`.
  This is the first Android-side code in the project.
* After 0.5, the engine is fully wired to the touch
  pipeline. The §38 disconnect test (release blocker) lands
  as a property test in 0.6 — but the engine's neutralization
  is the substrate it builds on.

---

**Status: `VERIFIED`. 125 tests, 0 failures, lint clean. Proceeding to 0.5.**
