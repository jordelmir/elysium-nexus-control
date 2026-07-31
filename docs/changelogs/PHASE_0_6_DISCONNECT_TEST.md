# PHASE 0.6 — §38 Disconnect Test (Engine Half)

**Status:** `VERIFIED` (4 new tests, 164 total; build green; lint
green; the release-blocker gate is now a unit test)
**Iteration goal:** ship the engine's half of `MASTER_ORDER.md`
§38 as a unit test class. The transport half lands in Phase 2+
when the actual disconnect path is in the codebase; the engine
half is what we can verify today.

## 1. Objective

§38 is the *release-blocker* test. A single stuck control on
disconnect is grounds for rejection. The full spec has 15
steps. The engine contributes steps 1-6 (set the state) and
the first half of step 13 (engine-side neutralization on
transition out of `Active`). Steps 7-8 (keys, mouse) and
9-12 (Bluetooth / Wi-Fi / process kill / receiver reset) are
transport-layer concerns and have their own tests in Phase
2+. Step 14-15 (reconnect, first state is neutral) is the
*positive* case of the same property — the engine can verify
it now.

## 2. Evidence researched

* `MASTER_ORDER.md` §32 (state machine) and §38 (the
  release-blocker test).
* `CanonicalInputEngine.transitionTo` (Phase 0.4) — the
  contract that "every transition emits neutral".
* `CanonicalInputEngine.neutralize` (Phase 0.4) — the abrupt
  disconnect path.
* `kotlinx.coroutines.test.runTest` and `TestScope` for the
  collector / cancel-scope pattern.

## 3. State before

`<nuevo>` (Phase 0.5). 160 tests, build green, lint green. The
engine has a `transitionTo` and a `neutralize` that reset the
state; no test pins the §38 contract end-to-end.

## 4. Files created

```
apps/android-controller/app/src/test/java/com/elysium/nexus/core/engine/
└── Section38DisconnectTest.kt
    ├── section38HoldAllInputsThenDisconnectNeutralizes
    ├── section38NeutralizeWithoutTransitionResetsToZero
    ├── section38AllDisconnectedStatesAreNeutral
    └── section38ReconnectFirstStateIsNeutral
```

No production code changes. The test class is the deliverable.

## 5. Architectural decisions

* **The test class is named after the spec section.** The
  name `Section38DisconnectTest` makes the spec→test mapping
  grep-able from CI logs and from any test report. Future
  revisions of §38 update the class, not the class name.
* **The "kill" in step 11 is modelled as
  `transitionTo(Disconnected)`** in the test. The engine
  has no `kill` method; the engine's model of an abrupt
  disconnect is the transition to `Disconnected` (which the
  transport layer drives on a perceived drop). The
  transport layer's model — the host sees the device
  disappear — is tested in Phase 2+.
* **`neutralize()` clears motion.** This is the right
  interpretation of the spec: "motion recentered if
  required" is satisfied by a force-zero, because a stuck
  motion sample is just as bad as a stuck button. We
  document this in the test's KDoc. A future revision may
  add a `neutralizeKeepMotion()` variant if a host needs
  the last motion sample before the disconnect; the §38
  contract says "neutralize everything", not "recenter to
  the last known value".
* **Steps 7-8 (keys, mouse) and 9-12 (Bluetooth / Wi-Fi /
  process kill / receiver reset) are out of scope for the
  engine.** The test class documents the boundary so a
  future contributor does not silently under-test §38.
* **Steps 14-15 (reconnect, first state is neutral) ARE
  in scope.** The engine's `transitionTo(Active)` always
  emits a neutral frame first, so the reconnect path is
  verifiable in the test class today.

## 6. Implementation

The test class is a single file with four `@Test`s, each of
which drives a different shape of the §38 contract:

* `section38HoldAllInputsThenDisconnectNeutralizes` — the
  primary contract. Sets up Active with 4 buttons + both
  triggers + both sticks at the corners + dpad diagonal +
  two touches + motion, then `transitionTo(Disconnected)`
  and asserts every input is at neutral.
* `section38NeutralizeWithoutTransitionResetsToZero` —
  the abrupt-disconnect path (steps 9-10). Sets up the same
  non-neutral state, then `neutralize()` directly. The test
  asserts the result is fully neutral.
* `section38AllDisconnectedStatesAreNeutral` — a property
  test. For every non-emitting state (Idle, Discovering,
  Pairing, Authenticating, Negotiating, Disconnected), the
  engine's state after transitioning there is neutral.
* `section38ReconnectFirstStateIsNeutral` — steps 14-15.
  Drives a full session, disconnects, reconnects, and
  asserts the first state after `Active` is neutral.

The test setup uses an injected `CoroutineScope` and a
recording `MutableSharedFlow<Any>` to observe the engine's
emissions. The collector is launched in the test scope and
records every state the engine emits. After the
disconnect, the test inspects the engine's `state.value`
(which is the most recent emission) and asserts it is
neutral.

## 7. Tests

4 new unit tests, 164 total. All green in ~10 ms.

| Test class                  | Count | Highlights                                              |
| --------------------------- | ----: | ------------------------------------------------------- |
| `Section38DisconnectTest`   |     4 | Hold all inputs + disconnect, abrupt neutralize, all non-emitting states are neutral, reconnect first state is neutral. |

The 4 tests in `Section38DisconnectTest`:

* `section38HoldAllInputsThenDisconnectNeutralizes` —
  primary contract.
* `section38NeutralizeWithoutTransitionResetsToZero` —
  abrupt path.
* `section38AllDisconnectedStatesAreNeutral` — property
  test over the 6 non-emitting states.
* `section38ReconnectFirstStateIsNeutral` — reconnect
  first state.

## 8. Results

| Check                                          | Result   |
| ---------------------------------------------- | -------- |
| `./gradlew clean :app:testDebugUnitTest`       | green    |
| `./gradlew :app:assembleDebug`                 | green    |
| `./gradlew :app:lintDebug`                     | green    |
| Lint errors / warnings                         | 0 / 0    |
| Test count                                     | 164      |
| Test failures                                  | 0        |
| Test wall time                                 | 10 ms    |
| New production LOC                             | 0        |
| New test LOC                                   | ~290     |
| New dependencies                               | 0        |
| APK size delta                                 | 0        |

## 9. Metrics

* Test wall time: 10 ms for the §38 class. The test does
  not need real coroutines or wall-clock waits; `runTest`
  with `UnconfinedTestDispatcher` is fully synchronous.
* `neutralize()` is a single `MutableStateFlow.value =`
  swap, sub-microsecond. The §38 test runs it 5+ times
  per test invocation; total cost is in the noise.

## 10. Failures (test-discovered regressions)

1 test-discovered issue in this iteration.

* **Test fix #4 — `section38NeutralizeWithoutTransitionResetsToZero`
  assumed `motion` is preserved by `neutralize()`.** The
  test was wrong: the engine's `neutralize()` resets the
  state via `UniversalControllerState.neutral()`, which
  sets `motion = null`. The §38 contract says "neutralize
  everything"; a stuck motion sample is just as bad as a
  stuck button. **Fix:** the test now asserts `motion` is
  `null` after `neutralize()`, with a KDoc that documents
  the design choice and notes a future
  `neutralizeKeepMotion()` variant as a possible
  extension point. The engine's behavior is correct; the
  test was reflecting an aspirational API that does not
  match the §38 contract.

No production-code bugs in this iteration. The test
class passes on the first attempt after the test-fix
above.

## 11. Risks

* **The §38 spec has more steps than the engine can
  verify.** Steps 7-8 (keys, mouse) and 9-12 (transport-
  level disconnect) are out of scope for the engine.
  Phase 2+ will add transport-level tests that complete
  the spec. Until then, the engine's half of §38 is
  pinned, but the full §38 contract is not yet
  enforced end-to-end.
* **No integration test for the `TouchSurfaceView`'s
  `MotionEvent` parsing.** The dispatcher is unit-tested;
  the View is not. A malformed `MotionEvent` could
  survive unit testing but fail in production. The
  mitigation is a small Robolectric test in 1.x.
* **The test uses `UnconfinedTestDispatcher` for the
  engine's scope.** This makes the test synchronous, but
  it also means a real `CoroutineScope` (the one a service
  would inject) might behave differently. We accept this
  risk: the engine's `commit()` does not use the scope;
  the scope is reserved for future engine-internal jobs
  (Phase 0.5+). When those jobs land, the test will need
  to use `StandardTestDispatcher` and advance the virtual
  clock.
* **§38 is a contract test, not a performance test.** A
  real disconnect (Bluetooth link loss, process kill)
  can have arbitrary latency; the test only verifies the
  engine's *eventual* neutralization, not the timing. The
  §30 latency budget applies to the *reactive* path (the
  time between "engine receives `transitionTo(Disconnected)`"
  and "the consumer observes the neutral frame"). A
  separate property test in 0.7+ will measure that.

## 12. Next executable block (Phase 0.7)

The smallest concrete sub-task that unlocks the most
downstream work is **Phase 0.7 — First `MainActivity` +
manifest wiring + the runnable shell**. Concretely:

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt`
  — a `ComponentActivity` that hosts the `TouchSurfaceView`,
  owns the `CanonicalInputEngine` via a lifecycle-scoped
  `CoroutineScope`, and wires the view's callback to the
  engine.
* `AndroidManifest.xml` — register the activity as the
  launcher; the manifest now has a single `<activity>` tag.
* The §30 latency budget's first measurement: a small
  `tools/latency-profiler/` stub that captures the time
  between "view receives `MotionEvent`" and "engine's
  StateFlow emits".

After 0.7, the first real "this app does something on a
phone" milestone lands: a Compose-free, View-based touch
on Honor Magic V2, going through the dispatcher, into the
engine, out through a `StateFlow` collector that logs.
The Hilt wiring and DI module arrive in 1.x.

---

**Status: `VERIFIED`. 164 tests, 0 failures, lint clean. The §38 release-blocker test is now in CI. Proceeding to 0.7.**
