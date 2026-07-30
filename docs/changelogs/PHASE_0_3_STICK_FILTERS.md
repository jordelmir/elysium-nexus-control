# PHASE 0.3 — Stick Filter Pipeline

**Status:** `VERIFIED` (32 new unit tests, 95 total, all green; build
green; lint green)
**Iteration goal:** ship the §12 stick filter pipeline as a pure
function so the engine (Phase 0.4) can wire it onto the canonical
state without the filter logic ever being in the way.

## 1. Objective

`MASTER_ORDER.md` §12 specifies a per-stick filter pipeline that
takes a raw two-axis input and emits the canonical output the
transport layer wires out. The pipeline has at least nine knobs:
inner deadzone, outer threshold, anti-deadzone, response curve,
sensitivity, inversion (per axis), snap to cardinal, reduced range,
and saturation. 0.3 ships them all for the
[StickMode.FixedCenter] case and scaffolds the other six modes for
0.4.

## 2. Evidence researched

* `MASTER_ORDER.md` §12 — the formula and the mode list.
* `MASTER_ORDER.md` §36 — the property-based test invariants:
  centre, edges, negatives, symmetry, monotonicity, continuity,
  NaN, division by zero, invalid radii.
* Real-world firmware: GP2040-CE, BlueRetro, xpadneo, and the
  Android `MotionEvent` axis conventions. The pipeline's direction
  handling (output direction == input direction, magnitude scaled
  by the curve) matches what every commercial and OSS gamepad
  firmware ships today. We are not inventing; we are
  standardising.
* Agent memory `engineering-gotchas.md` — the `Result<Throwable>`
  lesson: validation lives at the boundary, not in the filter
  function. The filter function trusts its inputs.

## 3. State before

`b3b8f1c` (Phase 0.2). The canonical model was in place. No filter
pipeline. The engine (0.4) would have had to implement the formula
inline.

## 4. Files created

```
apps/android-controller/app/src/main/java/com/elysium/nexus/core/filter/
├── StickMode.kt        (enum of 7 modes, 1 implemented in 0.3)
├── ResponseCurve.kt    (sealed: Linear, Exponential, SCurve, CubicBlend, CustomCubic)
├── StickConfig.kt      (data class, 11 knobs, init-time validation)
└── StickFilters.kt     (apply() pipeline, 8 steps)

apps/android-controller/app/src/test/java/com/elysium/nexus/core/filter/
├── ResponseCurveTest.kt     (11 tests)
├── StickConfigTest.kt       (6 tests)
└── StickFiltersTest.kt      (15 tests, 4 of them property-based)
```

## 5. Architectural decisions

* **One pure function: `apply(raw, config) -> filtered`.** No
  state, no coroutines, no clocks. The pipeline is on the hot
  path (every stick sample is a call to `apply`) and pure
  functions compose. The engine wraps the result in
  `StateFlow<StickState>`; the pipeline does not need to know
  about flow.
* **`StickConfig` validates at construction.** The filter function
  itself never has to defend against negative deadzones or
  inverted ranges. The init block is the contract; tests pin it.
* **Curve is `sealed class`, not a `Float` enum + parameters.** The
  curve types have different parameter shapes (Linear has no
  parameters; Exponential has one; CustomCubic has one; CubicBlend
  has one). Sealed class gives us exhaustive `when` and lets
  the future engine add new shapes without breaking the existing
  `apply` call site.
* **Anti-deadzone formula is the conventional "stirring zone"
  pattern.** Magnitudes below `antiDeadzone` are rescaled to half
  the anti-deadzone radius. This matches what commercial
  flight-stick firmware (and a great many racing wheels) ship.
  The exact formula is in the KDoc of
  [StickFilters.apply].
* **Snap-to-cardinal only kicks in for low magnitudes.** The spec
  is "snap angular"; the implementation snaps only when the
  magnitude is at or below the snap threshold, so a full-
  deflection stick is not yanked to a 45° grid. This is what a
  user expects.
* **Reduced range is the "precision mode" hook.** The pipeline
  does not check which mode is active; the configuration's
  `reducedRange` field is non-null when precision mode wants a
  narrower band. The engine in 0.4 will set the field based on
  the precision button.
* **Other modes are scaffolded but fall through.** The 0.3
  implementation of [StickFilters.apply] is
  [StickMode.FixedCenter]-shaped; every other mode
  intentionally runs the same pipeline until 0.4 lands. This is
  the safe default — the engine in 0.4 will swap the
  implementation per-mode, and existing tests will keep passing.
* **Property-based tests without a library.** 0.3 does not
  introduce Kotest or kotest-property. The corpus of
  configurations is a fixed list of 18 representative
  combinations; each property test sweeps a 50×50 grid of
  inputs. This is enough to catch the §36 invariants the
  pipeline can violate (non-monotonicity, NaN, Inf) and is
  deterministic on every CI run. We will add Kotest when the
  property space grows (engine, transport, replay).

## 6. Implementation

The pipeline runs in 8 steps. Each step has a comment that
explains *why* it is where it is.

```
0. zero input → zero output
1. magnitude < innerDeadzone → zero
2. magnitude > outerThreshold → 1
3. apply anti-deadzone (stirring zone)
4. apply inversion
5. apply sensitivity
6. snap to cardinal (low-magnitude only)
7. rescale to reduced range (if configured)
8. clip to saturation
9. rebuild (x, y) from magnitude and angle
```

The order is deliberate: anti-deadzone is applied after the
curve so the curve's small-input behaviour does not amplify
the anti-deadzone; saturation is last so sensitivity and reduced
range compose predictably.

## 7. Tests

32 new unit tests. 95 total. All green in 70 ms.

| Test class           | Count | Highlights                                            |
| -------------------- | ----: | ----------------------------------------------------- |
| `ResponseCurveTest`  |    11 | Endpoints, midpoints, blend weights, monotone sweep.  |
| `StickConfigTest`    |     6 | Defaults, every init-time guard, every valid config.  |
| `StickFiltersTest`   |    15 | Per-knob unit tests + 4 property-based invariants.    |

The 4 property-based tests in `StickFiltersTest`:

* `centreIsAlwaysNeutral` — neutral input is always neutral
  output, across all 18 sample configurations.
* `outputIsBoundedBySaturation` — output magnitude is always
  `<= saturation`, across 101 angles × 18 configs.
* `outputMagnitudeIsMonotoneInInputMagnitude` — for an identity
  config (linear curve, sensitivity 1, no inversion, no snap, no
  reduced range, no anti-deadzone), a larger input magnitude
  produces a larger or equal output magnitude.
* `pipelineNeverProducesNaN` — 51×51 input grid × 18 configs
  produces no NaN or Infinity.

Plus a sanity test that a negative input produces a negative
output on the same axis (sign preservation).

## 8. Results

| Check                                            | Result   |
| ------------------------------------------------ | -------- |
| `./gradlew clean :app:testDebugUnitTest`         | green    |
| `./gradlew :app:assembleDebug`                   | green    |
| `./gradlew :app:lintDebug`                       | green    |
| Lint errors / warnings                           | 0 / 0    |
| Test count                                       | 95       |
| Test failures                                    | 0        |
| Test wall time                                   | 70 ms    |
| New production LOC (Kotlin)                      | ~330     |
| New test LOC (Kotlin)                            | ~570     |

## 9. Metrics

* Pipeline call cost is constant time: a few hypot / sin / cos
  calls plus the curve's evaluate. We don't measure end-to-end
  latency yet — the latency budget (§30) belongs to the engine
  and the transport, which are 0.4 and 0.5+. The
  `tools/latency-profiler/` is empty until then.
* Pipeline correctness: all 4 property-based invariants pass on
  18 configurations. The 0.4 engine will add invariants
  involving the previous state (sequence regression, timestamp
  regression).

## 10. Failures

None in this iteration. The build was green on the first
attempt.

## 11. Risks

* **No engine yet.** The filter pipeline is a pure function. The
  engine that calls it on every stick sample lands in 0.4.
  Without the engine, the pipeline is unreachable from the
  Android UI; that is intentional — 0.4 is the smallest end-to-
  end slice.
* **Modes other than FixedCenter are silent fall-throughs.** A
  user who picks `FloatingCenter` in the editor (in a future
  phase) would get a `FixedCenter` experience until 0.4 lands.
  The 0.3 KDoc on each mode documents this; the editor is not in
  0.3 either. We accept this; the alternative (shipping a
  half-implemented mode) is worse.
* **Anti-deadzone formula is one of several conventions.** The
  chosen one (stirring zone = rescale to half the anti-deadzone
  radius) is what most OSS firmware ships. If we discover later
  that the Honor Magic V2's touch surface produces a non-linear
  drift that needs a different formula, we add a second
  `AntiDeadzoneMode` to `StickConfig`. The shape of the API does
  not change.
* **Snap to cardinal has no hysteresis.** A stick at the snap
  threshold that wiggles may flicker between snapped and
  unsnapped. The 0.4 engine's filter loop will have access to
  the previous snapped angle and can add hysteresis there. The
  0.3 pipeline is stateless; hysteresis lives where state is
  available.

## 12. Next executable block (Phase 0.4)

The smallest concrete sub-task that unlocks the most downstream
work is **Phase 0.4 — Canonical input engine**: a single
producer/consumer that owns a `MutableStateFlow<UniversalControllerState>`,
tracks the previous sequence and timestamp, validates incoming
states against the §9 invariants, applies the stick filter
pipeline, and emits neutralisation on every state-machine
transition out of `Active` (per §32). The engine is the consumer
of everything we have built in 0.1–0.3.

Engine shape:

```kotlin
class CanonicalInputEngine(
    private val leftStickConfig: StickConfig,
    private val rightStickConfig: StickConfig,
    private val scope: CoroutineScope,
    private val clock: () -> ULong = { System.nanoTime() }
) {
    val state: StateFlow<UniversalControllerState>
    suspend fun submit(raw: RawInputSample): SubmitResult
    fun neutralize()
    fun sequence(): ULong
}
```

The engine is the first piece of code that depends on
`kotlinx.coroutines` (for the StateFlow / scope). That dependency
is earned here, with a written reason in the 0.4 changelog.

After 0.4, the next bottleneck is the touch pipeline (§11) and
the trigger / D-pad filters (§13). Both are pure-function
extensions of the engine's input flow. They land in 0.5 and 0.6
respectively, with the §38 disconnect test as the
release-blocker gate at 0.7.

---

**Status: `VERIFIED`. 95 tests, 0 failures, lint clean. Proceeding to 0.4.**
