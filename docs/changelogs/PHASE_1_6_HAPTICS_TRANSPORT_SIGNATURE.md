# Phase 1.6 — haptics, transport multiplexer, profile signature

> Status: **VERIFIED_LAB** — 363 unit tests green,
> `assembleDebug` green. Haptics, transport
> multiplexer interface, and profile signature
> shipped with full unit tests. Per-posture layout
> + on-device Bluetooth HID + Compose Compiler
> upgrade deferred to Phase 1.7+.

## Objective

Per `MASTER_ORDER.md` §17 (transport multiplexer),
§15 ("Firmar perfiles" / signing), and §27
(haptics locales), Phase 1.6 ships the three
foundations:

* **Transport multiplexer interface** —
  [`ControllerTransport`] + the closed set of
  result types (`TransportResult`, `PairingResult`,
  `ConnectionResult`, `SendResult`,
  `DisconnectResult`) and the closed set of
  reliable events (`ReliableInputEvent`).
  Phase 1.7+ ships the first real transport
  (Bluetooth HID, with the §18 `Elysium Nexus
  Gamepad` descriptor).
* **Profile signature** — HMAC-SHA256 keyed
  by a per-user secret, with a constant-time
  verify. The §15 "Firmar perfiles" feature.
* **Haptics** — the closed set of `HapticEvent`
  values (ButtonTap, ButtonLongPress, StickEdge,
  TriggerClick, Error, TransportConnected,
  TransportDisconnected, ProfileChanged,
  Recentered) + an Android adapter that maps
  each to a `VibrationEffect`.

## Evidence

```
$ ./gradlew :app:testDebugUnitTest
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
363 tests, 0 failed, 0 errors, 0 skipped
$ ./gradlew :app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
8.8 MB debug APK
```

## Files

**New (production, 4 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/transport/ControllerTransport.kt` —
  the §17 transport multiplexer interface +
  the result types + `ReliableInputEvent` + the
  `TransportCapabilities` data class.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/haptics/Haptics.kt` —
  the `Haptics` interface + the `HapticEvent`
  sealed class + `NullHaptics` + `FakeHaptics`.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/haptics/AndroidHaptics.kt` —
  the Android `Vibrator` adapter. Maps each
  `HapticEvent` to a `VibrationEffect.createOneShot`
  with a per-event duration and amplitude.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/profile/ProfileSignature.kt` —
  the HMAC-SHA256 signature / verify. Constant-
  time comparison.

**New (test, 3 files):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/transport/ControllerTransportTest.kt` —
  6 tests for the transport interface +
  `FakeTransport` (the test surface for the
  activity's transport tests in Phase 1.7+).
* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/profile/ProfileSignatureTest.kt` —
  11 tests for the signature (determinism,
  hex format, sensitivity to profile and
  secret, valid / invalid / tampered /
  truncated verification).
* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/haptics/HapticsTest.kt` —
  4 tests for the haptics abstraction (event
  hierarchy, NullHaptics, FakeHaptics).

## Decisions

### ADR-0017 — Transport is an interface, not a class

Per the agent-memory rule, the §17 transport is
a testable interface. The activity wires the
production implementation; the JVM tests use
`FakeTransport`. Phase 1.7+ ships the first
real transport (Bluetooth HID); the interface
is the seam.

### ADR-0018 — HMAC-SHA256, not RSA / ECDSA

The §15 spec describes the signature as a
"firma" (a signature), not as an asymmetric
public-key scheme. HMAC-SHA256 is sufficient
for the integrity check (proving the profile
was not tampered with after the author signed
it). If §15 ever calls for asymmetric signing
(e.g. for sharing profiles between users
without sharing the signing key), the
signature can be migrated to Ed25519 or
ECDSA-P256.

### ADR-0019 — Haptics is a sealed-class event API

The §27 spec describes haptics in terms of
*user-visible events* (button tap, stick edge,
trigger click, error, etc.), not low-level
parameters (duration, amplitude). The
`Haptics` interface is a semantic layer; the
Android adapter maps each event to a
`VibrationEffect` with a per-event duration
and amplitude. A future haptic device (e.g.
the Nexus Receiver's motors) can implement
`Haptics` without changing the editor.

## Implementation

### 1. Transport multiplexer

The `ControllerTransport` interface follows the
§17 spec verbatim:

```kotlin
interface ControllerTransport {
    val capabilities: TransportCapabilities
    val state: TransportState
    suspend fun start(): TransportResult
    suspend fun pair(): PairingResult
    suspend fun connect(): ConnectionResult
    suspend fun sendReliable(event: ReliableInputEvent): SendResult
    suspend fun sendRealtime(state: UniversalControllerState): SendResult
    suspend fun releaseAll(): SendResult
    suspend fun disconnect(): DisconnectResult
    suspend fun stop(): TransportResult
}
```

The result types are sealed classes with `Ok`
and `Error(reason)` variants. The
`ReliableInputEvent` is a sealed class with
`ReleaseAll`, `ButtonDown(button)`,
`ButtonUp(button)`, `ProfileChanged(id)`,
`PairingRequest(name)`, `Revocation(name)`.

The `FakeTransport` records every `sendRealtime`
and `sendReliable` call for later assertion. The
real transports (Phase 1.7+) live in their own
files.

### 2. Profile signature

`ProfileSignature.sign(profile, secret)` returns
a 64-character hex string (32-byte HMAC-SHA256
of the profile's JSON serialisation). The
`verify` function uses constant-time comparison
to avoid timing attacks.

```kotlin
val json = ProfileJson.toJson(profile)
val mac = Mac.getInstance("HmacSHA256")
mac.init(SecretKeySpec(secret, "HmacSHA256"))
val raw = mac.doFinal(json.toByteArray(Charsets.UTF_8))
return raw.toHex()
```

The production wiring (Phase 1.7+) stores the
per-user secret in the Android Keystore and
embeds the signature in the profile document.
The verify function is used by the host (or
any consumer) to confirm the document's
integrity.

### 3. Haptics

The `Haptics` interface has a single `fire(event)`
method. The `HapticEvent` is a sealed class with
9 variants. The `AndroidHaptics` adapter maps
each event to a `VibrationEffect.createOneShot`
with a per-event duration and amplitude:

```kotlin
private fun mapEvent(event: HapticEvent): Pair<Long, Int> = when (event) {
    HapticEvent.ButtonTap -> 20L to 128         // 20ms, 50% amplitude
    HapticEvent.ButtonLongPress -> 40L to 192 // 40ms, 75% amplitude
    HapticEvent.StickEdge -> 15L to 96
    HapticEvent.TriggerClick -> 25L to 192
    HapticEvent.Error -> 100L to 255          // error is the loudest
    // ... etc
}
```

The `NullHaptics` is a no-op for unit tests and
previews. The `FakeHaptics` records every
`fire` call for later assertion.

## Tests

* 6 new tests in `ControllerTransportTest` —
  the result types, `FakeTransport`'s capture
  semantics, the capabilities data class.
* 11 new tests in `ProfileSignatureTest` —
  determinism, hex format, sensitivity to
  profile and secret, valid / invalid /
  tampered / truncated verification.
* 4 new tests in `HapticsTest` — the event
  hierarchy, `NullHaptics`, `FakeHaptics`.
* All Phase 0/1.0-1.5 tests still green (the
  changes are additive).

**Total: 363 tests, 0 failures, 0 errors.**

## Results

* `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
* `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**.
  APK is 8.8 MB (Phase 1.5: 8.8 MB; no change).

## Metrics

* APK size: 8.8 MB.
* New code: ~1,000 lines of Kotlin (production)
  + ~600 lines of test code.
* Test count: 363 (Phase 1.5: 342; +21 from this
  phase: 6 in `ControllerTransportTest` + 11 in
  `ProfileSignatureTest` + 4 in `HapticsTest`).
* No new dependencies.

## Failures and regressions

No new failures or regressions.

## Risks

* **No real transport implementation.** The
  `ControllerTransport` interface is the seam;
  the first real implementation (Bluetooth HID
  with the §18 `Elysium Nexus Gamepad`
  descriptor) lands in Phase 1.7+. The
  emulator's virtual Bluetooth is unreliable;
  the on-device test will run on a real
  device or with the USB transport (which
  works in the emulator).
* **No Android Keystore wiring.** The profile
  signature uses a per-user secret. The
  production wiring (Phase 1.7+) stores the
  secret in the Android Keystore; for now the
  test surface is a `ByteArray` parameter.
* **No on-device haptics verification.** The
  emulator's virtual vibrator is unreliable;
  the on-device verification will run on a
  real device.

## Next block — Phase 1.7

* **Bluetooth HID transport** — the first real
  `ControllerTransport` implementation, using
  the §18 `Elysium Nexus Gamepad` descriptor
  (the `HidDescriptor` + `HidReportEncoder`
  already in the codebase).
* **Android Keystore wiring** — the per-user
  signing key is generated and stored on the
  device; the profile document includes the
  signature.
* **Compose Compiler upgrade** — when KSP
  releases for Kotlin 2.2.x, bump Kotlin +
  Compose Compiler to fix Bug #17 and unblock
  Bug #19.
* **Per-posture layout** — the editor's layout
  adapts to the `Posture` (e.g. tabletop uses
  the top half for the dashboard).
* **Phase 1.8+** — confirmation dialog for
  "Delete profile", alignment / distribution
  helpers, hitBounds editor, the `Elysium
  Link` protocol (§19).
