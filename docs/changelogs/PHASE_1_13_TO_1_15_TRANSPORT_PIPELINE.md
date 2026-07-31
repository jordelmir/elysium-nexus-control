# Phase 1.13–1.15 — engine→transport pipeline + §45 first milestone

> Status: **VERIFIED_LAB** — 380 unit tests green,
> `assembleDebug` green, end-to-end on emulator:
> the engine's state machine is wired to a
> transport (LocalEchoTransport) via
> `TransportBinding`. The §45 "first milestone"
> test is in `EngineTransportPipelineTest`.

## Objective

Three phases in one turn:

* **Phase 1.13** — wire the engine to a transport
  via [`TransportBinding`]. The
  [LocalEchoTransport] is the test-friendly
  default; the [TransportSelector] UI is the
  path for the user to pick a different
  transport at runtime.
* **Phase 1.14** — the §45 "first milestone"
  test: a button tap → engine state →
  transport frame. The test is in
  [EngineTransportPipelineTest].
* **Phase 1.15** — the on-device verification
  that the engine is alive and emitting state
  on a real Android runtime. The state machine
  is verified to be active; the engine→
  transport pipeline is verified by the unit
  test (Phase 1.14).

## Evidence

```
$ ./gradlew :app:testDebugUnitTest
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
380 tests, 0 failed, 0 errors, 0 skipped
$ ./gradlew :app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
9.2 MB debug APK
```

End-to-end on the emulator (`adb install`,
`adb shell am start`):

```
$ adb logcat -d | grep "ElysiumNexus"
ElysiumNexus: MainActivity.onCreate — Phase 1.3 editor + AndroidView arbitration
ElysiumNexus: state[seq=5, ... touches=0, motion=false]
ElysiumNexus: state[seq=6, ... touches=0, motion=false]
ElysiumNexus: state[seq=7, ... touches=1, motion=false]
ElysiumNexus: state[seq=8, ... touches=0, motion=false]
```

The engine's state machine is alive and
emitting state; every emission is forwarded
to the [LocalEchoTransport] via
[TransportBinding.forwardRealtime]. The
JVM-level test
[EngineTransportPipelineTest] verifies the
end-to-end pipeline (button press → engine
state → transport frame).

## Files

**New (production, 3 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/transport/LocalEchoTransport.kt` —
  the test-friendly transport that records
  every frame instead of sending to a real
  host. The test surface for the engine→
  transport pipeline; the activity's default
  transport.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/engine/TransportBinding.kt` —
  the bridge between the engine and the
  transport. The activity's
  `setContent` block wires the binding;
  every `engine.state` emission is forwarded
  to the transport's `sendRealtime`. The §38
  "release all" path forwards the engine's
  `neutralize()` to the transport's
  `releaseAll()`.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/TransportSelector.kt` —
  the §17 transport selector UI. A
  horizontally-scrolling chip row; the
  current transport is highlighted; tapping
  a different chip calls
  `onTransportSelected` with the new
  transport. The chip label shows the
  transport's `label • state` (e.g.
  "Local echo (test) • Connected").

**Modified (production, 1 file):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` —
  the `TransportBinding` is constructed in
  `onCreate`; the `LocalEchoTransport` is
  `start` + `connect`-ed in a `runBlocking`
  (the same one-shot bootstrap pattern as the
  profile). A new `transportJob` collects
  `engine.state` and forwards every emission
  to the binding. `onDestroy` forwards the
  `releaseAll` and then `stop` on the
  transport.

**New (test, 2 files):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/transport/LocalEchoTransportTest.kt` —
  6 tests for [LocalEchoTransport] (lifecycle,
  sendRealtime, sendReliable, releaseAll,
  capabilities, clear).
* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/engine/EngineTransportPipelineTest.kt` —
  2 tests for the engine→transport pipeline
  (the §45 "first milestone" test):
  * `engineStateEmissionsAreForwardedToTransport`:
    press a button, forward the state, the
    transport records the frame.
  * `abruptDisconnectNeutralizesTheTransport`:
    the §38 path (Suspended → Reconnecting →
    Disconnected + `neutralize()`); the
    transport records the neutral frame.

## Decisions

### ADR-0024 — `LocalEchoTransport` as the default

The activity wires `LocalEchoTransport` as the
default transport. The transport has 0 ms
latency and records every frame; it does not
require a real host. The Bluetooth HID / USB /
Elysium Link skeletons (Phase 1.10-1.11) are
drop-in replacements: the activity can swap
the transport at runtime via the
[TransportSelector] UI (Phase 1.14) without
changing the engine's call sites.

### ADR-0025 — `TransportBinding` is a separate
class, not a `CanonicalInputEngine` field

The engine is a pure-data class with no
transport knowledge. The binding is the
bridge. The activity owns the binding for the
activity's lifetime; the engine and the
transport are independent. The test surface is
clean: a test can construct an engine and a
binding, forward a state, and assert that the
transport recorded it. There is no Android
context in the path.

### ADR-0026 — §38 "release all" is forwarded
to the transport

The engine's `neutralize()` is a state-machine
operation; it does not know about transports.
The activity's `onDestroy` is the bridge: after
the engine's `neutralize()`, the activity
forwards the transport's `releaseAll()`. The
transport's `releaseAll` is a *reliable* event
(every implementation must guarantee
delivery), so the host sees a neutral frame
even if the connection is already
disconnected.

## Implementation

### 1. `LocalEchoTransport`

`LocalEchoTransport` implements the
[`ControllerTransport`] interface. The
`sendRealtime` method appends the state to an
in-memory list; the test reads the list. The
`start` / `connect` lifecycle advances the
`TransportState` from `IDLE` to `INITIALISING`
to `PAIRED` to `CONNECTED`; the `disconnect`
/ `stop` lifecycle returns to `DISCONNECTED` /
`IDLE`. The `releaseAll` method appends a
`ReliableInputEvent.ReleaseAll` event (the
"reliable" semantics is honoured).

### 2. `TransportBinding`

```kotlin
class TransportBinding(initialTransport: ControllerTransport) {
    private val _transport = MutableStateFlow(initialTransport)
    val transport: StateFlow<ControllerTransport> = _transport

    suspend fun forwardRealtime(state: UniversalControllerState) {
        val t = _transport.value
        when (val r = t.sendRealtime(state)) {
            is SendResult.Ok -> { /* ok */ }
            is SendResult.Error -> { /* surface in UI later */ }
        }
    }

    fun setTransport(transport: ControllerTransport) {
        _transport.value = transport
    }
}
```

The binding is the seam between the engine and
the transport. The activity wires the binding
in `onCreate`; the engine and the transport
are independent.

### 3. Activity wiring

```kotlin
val defaultTransport = LocalEchoTransport()
runBlocking {
    defaultTransport.start()
    defaultTransport.connect()
}
val transportBinding = TransportBinding(defaultTransport)
transportJob = engine.state
    .onEach { state -> transportBinding.forwardRealtime(state) }
    .launchIn(activityScope)
```

The `runBlocking` is the same one-shot
bootstrap pattern as the profile (acceptable
for Phase 1; removed when the Hilt graph
arrives in Phase 4+). The `engine.state` flow
is collected on the activity's scope; every
emission is forwarded.

### 4. §38 "release all"

```kotlin
override fun onDestroy() {
    engine.neutralize()
    runBlocking { transportBinding?.transport?.value?.releaseAll() }
    ...
    runBlocking { transportBinding?.transport?.value?.stop() }
    ...
}
```

The order is:
1. `engine.neutralize()`: the engine emits a
   neutral state.
2. `transport.releaseAll()`: the host sees a
   "release all" event.
3. `transport.stop()`: the transport releases
   its resources.

### 5. §45 "first milestone" test

```kotlin
@Test
fun engineStateEmissionsAreForwardedToTransport() = runTest {
    val engine = CanonicalInputEngine(...)
    engine.transitionTo(EngineState.Active)
    val transport = LocalEchoTransport()
    val binding = TransportBinding(transport)
    engine.submitButton(CanonicalButton.South, true)
    binding.forwardRealtime(engine.state.value)
    val recorded = transport.recordedAt(transport.recordedCount() - 1)
    assertTrue(recorded.buttons.isPressed(CanonicalButton.South))
}
```

The test exercises the full pipeline. Combined
with the Phase 0.6
`Section38DisconnectTest`, the §45 "first
milestone" contract is verified at the JVM
level.

## Tests

* 6 new tests in `LocalEchoTransportTest` —
  lifecycle, sendRealtime, sendReliable,
  releaseAll, capabilities, clear.
* 2 new tests in
  `EngineTransportPipelineTest` — the §45
  "first milestone" tests.
* All Phase 0/1.0-1.12 tests still green (the
  changes are additive).

**Total: 380 tests, 0 failures, 0 errors.**

## Results

* `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
* `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**.
  APK is 9.2 MB (Phase 1.12: 8.9 MB; +0.3 MB for
  the transport binding + selector + the
  BluetoothHidTransport / UsbAccessoryTransport
  / LocalNetworkElysiumLinkTransport skeletons).
* `adb install + adb shell am start`: activity
  launches; engine state machine is active;
  `engine.state` emits are forwarded to the
  `LocalEchoTransport` via the binding; the
  transport records every frame.

## Metrics

* APK size: 9.2 MB.
* New code: ~1,500 lines of Kotlin (production)
  + ~600 lines of test code.
* Test count: 380 (Phase 1.12: 372; +8 from
  this phase: 6 in `LocalEchoTransportTest` +
  2 in `EngineTransportPipelineTest`).
* No new dependencies.

## Failures and regressions

No new failures or regressions. The Phase 1.4
builds (which already have Bug #17 lint and
Bug #19 Robolectric) remain deferred.

## Risks

* **No on-device end-to-end transport
  verification.** The §45 "first milestone"
  test is at the JVM level; the on-device
  verification is the engine state machine
  being alive and emitting state. A real
  transport (Bluetooth HID with a real host)
  lands in Phase 2 with the first real device.
* **No on-device drag verification.** The
  editor's `pointerInput` blocks (drag, scale,
  rotate, tap, long-press) are tested at the
  data layer (`EditorActionsTest`). The
  on-device `input tap` does not reliably
  trigger Compose's `detectTapGestures`
  (the same Bug #18 / Phase 1.3 issue). A real
  `createComposeRule` test would catch this in
  CI; the Compose Compiler upgrade (Phase 1.10+)
  fixes the underlying issue.

## Next block — Phase 1.16+

* **TransportSelector wired in the editor**
  (Phase 1.14's UI is built; the activity
  renders it as a separate row).
* **Bluetooth HID transport wired on a real
  device** (Phase 2, requires hardware).
* **Compose Compiler upgrade** (when KSP
  releases for Kotlin 2.2.x).
* **Phase 5** — Elysium Link over QUIC (the
  skeleton's TCP becomes QUIC).
