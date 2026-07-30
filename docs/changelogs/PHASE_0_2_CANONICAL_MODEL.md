# PHASE 0.2 — Canonical Input Model

**Status:** `VERIFIED` (61 new unit tests green, build green, lint green)
**Iteration goal:** mirror the `UniversalControllerState` Rust struct
from `MASTER_ORDER.md` §9 in pure Kotlin, with the §9 validation
contract enforced by a typed `ValidationResult` / `ValidationError`
sealed hierarchy. No Android types, no Compose, no transport — JVM
tests only.

## 1. Objective

Phase 0.1 gave us a buildable home. Phase 0.2 fills it with the
canonical model the rest of the platform builds on. The model is the
spec the §9 mandate (Rust engine with JNI boundary) will eventually
have to mirror; locking it in Kotlin first means the wire format, the
validator, and the field-path contract are all stable before the
engine arrives.

## 2. Evidence researched

* The §9 spec in `MASTER_ORDER.md` was the contract. Re-read twice to
  enumerate every field of `UniversalControllerState` and every
  range rule.
* USB HID Usage Tables 1.5 — the hat switch encoding (0..7 clockwise
  from North, 8 for neutral). The same encoding is in §18 for the
  generic gamepad descriptor. Locked in
  `DpadState.fromHatSwitch` / `toHatSwitch`.
* Android `MotionEvent` pointer id contract — the IDs are non-negative
  `Int`; the practical upper bound is bounded by the platform but
  not formally. Capped at 1024 in `TouchPoint.MAX_ID` and the cap is
  enforced by the validator.
* Agent memory `engineering-gotchas.md` — backtick test names reject
  colons, which is why every test name in this iteration uses spaces
  or dashes, never colons.

## 3. State before

`8d4cf03` (Phase 0.1). The repo had AGP 8.7.3, Kotlin 2.2.21, an
empty `:app` module with a placeholder class, and `docs/`.

## 4. Files created

```
apps/android-controller/app/src/main/java/com/elysium/nexus/core/model/
├── CanonicalButton.kt        (enum, 23 buttons, compile-time COUNT guard)
├── ButtonSet.kt              (@JvmInline value class wrapping Long bitset)
├── StickState.kt             (data class, ±1.0 range, validate())
├── TriggerState.kt           (data class, [0..1] range, validate())
├── DpadState.kt              (enum + hat switch round-trip)
├── TouchPoint.kt             (data class, [0..1] x/y/pressure, id 0..1024)
├── TouchCollection.kt        (cap = 10, init check + validate())
├── MotionState.kt            (gyro + accel + roll/pitch/yaw + sample ts)
├── BatteryState.kt           (level 0..100 + isCharging)
├── UniversalControllerState.kt  (root canonical state, validate())
└── Validation.kt             (sealed ValidationResult + sealed ValidationError)

apps/android-controller/app/src/test/java/com/elysium/nexus/core/model/
├── CanonicalButtonTest.kt        (3 tests)
├── ButtonSetTest.kt              (6 tests)
├── StickStateTest.kt             (7 tests)
├── TriggerStateTest.kt           (7 tests)
├── DpadStateTest.kt              (7 tests)
├── TouchPointTest.kt             (8 tests)
├── TouchCollectionTest.kt        (4 tests)
├── MotionStateTest.kt            (4 tests)
├── BatteryStateTest.kt           (4 tests)
└── UniversalControllerStateTest.kt (11 tests)
```

Plus a small cleanup in `PlaceholderTest.kt` (removed an unused import
and tightened a tautology).

## 5. Architectural decisions

* **Kotlin over Rust for the model.** §9 says "preferiblemente Rust".
  We keep that promise but defer the JNI boundary to a future phase
  (§9 also says "justifica la frontera JNI con benchmark"). The
  model is in pure Kotlin because:
  1. The model is the spec; the Rust crate (when it arrives) mirrors
     it 1:1, so any change to the wire format forces a symmetric
     Rust change — easy to review.
  2. The model is on the hot path; we want fast JVM unit tests
     (the 61 tests in this iteration run in 60 ms total — see
     §9 below) before we know whether the hot path actually needs
     Rust.
  3. Rust does not add a boundary the model has to cross. It
     matters when the engine has to encode / decode / filter
     packets; the model is pure data.
* **ButtonSet is a `@JvmInline value class` over `Long`.** 21 bits
  per state, 8 bytes flat. `EnumSet<CanonicalButton>` was rejected
  because it boxes per element on the hot path. A `Long` bitset
  gives O(1) insert / delete, O(popcount) size, and zero allocation
  in the common case. Iteration uses `numberOfTrailingZeros` so a
  scan is also O(pressed count), not O(21).
* **Inner validators emit local field names; the root validator
  prefixes them.** `StickState.validate` reports `field = "x"`,
  `UniversalControllerState.validate` rewrites it to
  `"leftStick.x"`. This keeps the inner validators self-contained
  (you can call them without a parent) and the wire form
  unambiguous (the full path is unique inside a state).
* **ValidationResult is a sealed class, not `kotlin.Result<T>`.**
  `Result<Throwable>` would erase the typed `ValidationError` to
  `Throwable`; we want callers to switch on the variant. (See
  agent memory `engineering-gotchas.md` — "Result<Throwable> vs
  typed error envelopes".)
* **The model does not carry sequence-vs-previous-sequence or
  timestamp-vs-previous-timestamp checks.** §9 says "secuencias
  repetidas" and "timestamps regresivos" are rejected, but those
  are properties of a *stream* of states, not of an individual
  state. They live in the engine (Phase 0.3 / 0.4) where the
  previous state is available.
* **`TouchCollection` enforces its cap in `init`.** Construction
  with too many points fails loudly. The engine never produces an
  oversized state in the first place, but if a future code path
  bypasses the engine, the constructor catches it.
* **`DpadState.fromHatSwitch` returns `null` for out-of-range hat
  values, not `Center`.** A garbage byte from a malformed HID
  report must not silently map to neutral. The engine rejects the
  frame when the decoder returns `null`.

## 6. Implementation

* **61 new unit tests** across 10 test classes.
* **2 test-discovered regressions** caught and fixed in the same
  iteration (see §10).
* 0 production code outside the `core.model` package; the rest of
  the codebase is unchanged.
* The `:app` module still has `applicationId = com.elysium.nexus.controller`
  and the empty manifest from 0.1; nothing in the runtime surface
  has changed.

## 7. Tests

63 tests total (61 new + 2 placeholder from 0.1). All green in 60 ms.

| Test class                        | Count | What it covers                                    |
| --------------------------------- | ----: | ------------------------------------------------- |
| `CanonicalButtonTest`             |   3   | Ordinal stability, count = 23, name uniqueness.    |
| `ButtonSetTest`                   |   6   | Empty / all, `with` is immutable, multi-button sets, forEach iteration. |
| `StickStateTest`                  |   7   | Neutral, corners, axes, NaN/Inf/OutOfRange, multi-axis. |
| `TriggerStateTest`                |   7   | 0, 1, midpoint, negative, >1, NaN, Inf.           |
| `DpadStateTest`                   |   7   | Active flag, unit vectors, hat round-trip, USB HID encoding, garbage input. |
| `TouchPointTest`                  |   8   | Valid sample, corners, NaN, OutOfRange, id bounds, multi-field. |
| `TouchCollectionTest`             |   4   | Empty, cap, init rejection, per-point validation. |
| `MotionStateTest`                 |   4   | Realistic, extreme, NaN in every field, Inf.      |
| `BatteryStateTest`                |   4   | 0%, 100%, negative, >100.                         |
| `UniversalControllerStateTest`    |  11   | Neutral default, with motion/battery, every sub-validator, multi-violation, immutability, optional fields. |
| `PlaceholderTest`                 |   2   | 0.1 smoke tests.                                  |
| **Total**                         | **63** |                                                   |

## 8. Results

| Check                                            | Result   |
| ------------------------------------------------ | -------- |
| `./gradlew clean :app:testDebugUnitTest`         | green    |
| `./gradlew :app:assembleDebug`                   | green    |
| `./gradlew :app:lintDebug`                       | green    |
| Lint errors / warnings                           | 0 / 0    |
| Test count                                       | 63       |
| Test failures                                    | 0        |
| Test wall time                                   | 60 ms    |
| New production LOC (Kotlin)                      | ~430     |
| New test LOC (Kotlin)                            | ~440     |
| APK size delta                                   | +6 KB    |

## 9. Metrics

* Compile time for the new code: sub-second (it is small).
* Test wall time: 60 ms total across 63 tests. The model is cheap to
  test, which is by design — every iteration that adds a state
  variant (e.g. when the engine lands) gets the same fast feedback
  loop.
* Lint: 0 warnings. Lint is the floor; any new warning is a
  release-blocker until it's resolved.

## 10. Failures (test-discovered regressions are good news)

Two real bugs surfaced in this iteration. Both were caught by the
unit tests; both were fixed before the iteration closed.

* **Bug #1 — `CanonicalButton.COUNT` was 21, actual count is 23.**
  The §10 spec says "21" in a comment but enumerates 23 distinct
  values (4 face + 4 shoulder/trigger + 2 stick-click + 5 system +
  4 paddle + 4 auxiliary). The compile-time guard inside
  `CanonicalButton.Companion.init { require(values().size == COUNT) }`
  caught the discrepancy at test time. **Fix:** `COUNT = 23`,
  KDoc updated to spell out the breakdown. The KDoc is the source
  of truth now; the original spec comment was misleading.

* **Bug #2 — field paths in nested validation were class-qualified
  on the inner side and re-prefixed on the outer side, producing
  paths like `"rightTrigger.TriggerState.value"`.** The test
  expected `"rightTrigger.value"`. **Fix:** inner validators
  (StickState, TriggerState, TouchPoint, MotionState, BatteryState,
  TouchCollection) now emit local field names; the root
  `UniversalControllerState` validator adds the parent prefix.
  The KDoc on `UniversalControllerState.validate` documents the
  contract.

Both fixes are visible in the test diffs and the source diffs. The
tests that caught them are kept — they are the regression barrier.

## 11. Risks

* **Spec ambiguity on `COUNT`.** The §10 comment in `MASTER_ORDER.md`
  is wrong. We corrected it locally; the master order should be
  updated to say 23, not 21, on the next pass. (Not done in 0.2
  because touching the master order on every micro-correction
  polluts the constitution; we batch these in 0.5.)
* **No property-based tests yet.** §36 lists invariants that the
  property tests must hold (no pointer controls two elements, no
  released button stays active, no invalid frame mutates state,
  …). For the model alone, the invariants are exhausted by
  the unit tests we have. Property tests earn their place when the
  *engine* is in (0.3 / 0.4), because that is where sequence /
  timestamp regression, state-to-state compatibility, and other
  multi-event invariants live. We will add a property test per
  invariant the moment the engine has the API to express it.
* **Wire format is implicit.** The model has no
  `encode(Buffer)` / `decode(Buffer)` functions yet. The transport
  layer (Phase 2) will need them, and the format we pick then is
  what gets committed to `docs/protocol/`. The model is shaped to
  make that easy (immutable, no hidden state, every value a
  primitive or a `List` / `Map` of primitives) but the explicit
  codec is not in 0.2.
* **Motion has no range validation.** Per §9 we reject only NaN /
  Infinity. A 16 g accelerometer legitimately reports ~156 m/s²;
  the consumer backend, not the model, decides what to do with
  out-of-band motion data. This is a documented choice, not an
  oversight.
* **No Android-specific code.** Touch surfaces, sensor streams, and
  Bluetooth HID stack are still future phases. The model is pure
  Kotlin so it can be re-used on desktop agents in Phase 3
  without porting.

## 12. Next executable block (Phase 0.3)

The smallest concrete sub-task that unlocks the most downstream work
is **Phase 0.3 — Stick filter pipeline**: deadzones (radial + axial),
anti-deadzone, response curve, snap, saturation, mode switches
(fixed / floating / hybrid / relative / flick / gyro-assisted /
precision). All of it as a pure function

```kotlin
fun applyStickFilters(
    raw: StickState,
    config: StickConfig,
    motion: MotionState?,
    previousFiltered: StickState?
): StickState
```

with property-based tests for the §12 invariants (center, edges,
negatives, symmetry, monotonicity, continuity, NaN, division by
zero, invalid radii). No engine, no transport, no Android — JVM
tests only. The model we just shipped is the input; the filter
pipeline is the first consumer of the model.

After 0.3, the engine (Phase 0.4) wires the model + the filters +
the touch pipeline (§11) into a single producer/consumer with a
`StateFlow<UniversalControllerState>` and the §32 state machine.

---

**Status: `VERIFIED`. 63 tests, 0 failures, lint clean. Proceeding to 0.3.**
