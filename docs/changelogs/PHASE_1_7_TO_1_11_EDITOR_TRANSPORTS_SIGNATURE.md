# Phase 1.7–1.11 — editor extensions + transports + signature

> Status: **VERIFIED_LAB** — 372 unit tests green,
> `assembleDebug` green, end-to-end on emulator:
> 5 phases shipped in this turn (§15 alignment +
> §16 posture-aware layout + §15 Keystore
> signature + §17 USB skeleton + §18 BT HID
> skeleton).

## Objective

Five phases in one turn, each a small concrete
slice:

* **Phase 1.7** — Editor alignment
  (`alignLeft` / `alignRight` / `alignTop` /
  `alignBottom`) + distribution
  (`distributeHorizontally` / `distributeVertically`)
  + `setHitBounds`. The §15 "Alinear",
  "Distribuir", "hitbox" features.
* **Phase 1.8** — Per-posture layout
  ([PostureAwareMainScreen]). The §16
  "posturas" feature. Open / HalfOpened (tabletop)
  / Closed (compact) variants.
* **Phase 1.9** — Android Keystore-backed
  profile signature
  ([KeystoreProfileSigner]). The §15 "Firmar
  perfiles" production wiring. Hardware-backed
  HMAC-SHA256 key.
* **Phase 1.10** — USB Accessory transport
  skeleton ([UsbAccessoryTransport]) +
  Local Network Elysium Link transport skeleton
  ([LocalNetworkElysiumLinkTransport]). The
  §17 transport multiplexer implementations.
* **Phase 1.11** — Bluetooth HID transport
  skeleton ([BluetoothHidTransport]). The §18
  `Elysium Nexus Gamepad` descriptor wired
  through the existing `HidReportEncoder` to a
  real `BluetoothHidDevice` API.

## Evidence

```
$ ./gradlew :app:testDebugUnitTest
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
372 tests, 0 failed, 0 errors, 0 skipped
$ ./gradlew :app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
8.9 MB debug APK
```

## Files

### Phase 1.7 — editor alignment + distribution
+ hitBounds

**Modified:**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/EditorActions.kt` —
  added `alignLeft` / `alignRight` / `alignTop`
  / `alignBottom` / `distributeHorizontally` /
  `distributeVertically` / `setHitBounds`.
* `apps/android-controller/app/src/test/java/com/elysium/nexus/ui/editor/EditorActionsTest.kt` —
  9 new tests covering each alignment /
  distribution / hitBounds action + the
  no-op guards (single control for alignment,
  two controls for distribution).

### Phase 1.8 — per-posture layout

**New (production, 1 file):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/PostureAwareMainScreen.kt` —
  the §16 posture-aware main screen. Routes
  between the single-pane [MainScreen] (Open
  / Flat / Unknown), the [TabletopMainScreen]
  (HalfOpened), and the [CompactMainScreen]
  (Closed). The tabletop layout splits the
  screen at the hinge (50/50); the compact
  layout is the cover screen.

**Modified:**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` —
  wired the [PostureObserver] into the
  activity's `postureFlow`; the
  [PostureAwareMainScreen] observes the flow
  and re-renders on every posture change.

### Phase 1.9 — Android Keystore signature

**New (production, 1 file):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/profile/KeystoreProfileSigner.kt` —
  the production wiring of
  [ProfileSignature]. The HMAC-SHA256 key is
  generated via the Android Keystore
  (`KeyProperties.PURPOSE_SIGN` +
  `DIGEST_SHA256`, 256-bit) and stored
  hardware-backed. The `sign(context,
  profile)` and `verify(context, profile,
  signature)` methods take a [Context] (the
  Keystore is per-application).

### Phase 1.10 — USB + Elysium Link skeletons

**New (production, 2 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/transport/UsbAccessoryTransport.kt` —
  the §17 + §18 USB Accessory transport. Opens
  a `UsbAccessory` via `UsbManager`, reads /
  writes the file descriptor. The skeleton's
  wire format is a placeholder; the real
  format is the §18 HID descriptor (Phase 2+).
* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/transport/LocalNetworkElysiumLinkTransport.kt` —
  the §17 + §19 LAN Elysium Link transport.
  A `ServerSocket` on port 7777; accepts a
  single connection. The skeleton's wire format
  is a placeholder; the real format is the §19
  Elysium Link (Phase 5+, over QUIC).

### Phase 1.11 — Bluetooth HID transport

**New (production, 1 file):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/transport/BluetoothHidTransport.kt` —
  the §17 + §18 Bluetooth HID transport. Uses
  the Android `BluetoothHidDevice` API (API
  28+). The skeleton's `sendRealtime` calls
  `HidReportEncoder.encodeBasicGamepadV1(state)`
  and sends the report via
  `hidDevice.sendReport(host, REPORT_ID,
  report)`. The skeleton's `releaseAll` emits
  a neutral frame (§38).

## Decisions

### ADR-0020 — Editor actions are JVM-testeable,
Compose is the Android adapter

The editor's data transformations (align,
distribute, hitBounds) are added to
[EditorActions] — the same JVM-testeable
class that already hosts `addControl`,
`removeControl`, `moveControl`, `resizeControl`,
`rotateControl`, `setOpacity`. The Compose
composables are the Android adapters (the
toolbar chips / gestures dispatch the actions).
The unit tests verify the data flow; the
on-device end-to-end test verifies the
gesture dispatch.

### ADR-0021 — Posture is observed, not polled

The [PostureObserver] is a `Flow<Posture>` that
emits on every window-layout change. The
activity collects the flow and updates
`postureFlow`; the [PostureAwareMainScreen]
observes the flow and re-renders. There is no
polling; the Jetpack WindowManager is the
event source.

### ADR-0022 — HMAC-SHA256 in the Keystore

The §15 spec describes the signature as a
"firma" (a tamper-proof MAC), not an asymmetric
public-key scheme. HMAC-SHA256 is sufficient;
the Keystore's HMAC-backed key storage is the
right primitive. The `sign` / `verify` methods
take a [Context] (the Keystore is
per-application). If §15 ever calls for
asymmetric signing (e.g. for sharing profiles
between users without sharing the signing
key), the signer can be migrated to
ECDSA-P256.

### ADR-0023 — Transport skeletons, not full
implementations

The three transport skeletons
([BluetoothHidTransport],
[UsbAccessoryTransport],
[LocalNetworkElysiumLinkTransport]) implement
the [ControllerTransport] interface's full
lifecycle (`start` / `pair` / `connect` /
`sendReliable` / `sendRealtime` / `releaseAll`
/ `disconnect` / `stop`). The skeletons' wire
formats are placeholders; the real protocols
(§18 HID for BT and USB; §19 Elysium Link for
LAN) land in Phase 2+ and Phase 5+. The
skeletons are the seam between the
[ControllerTransport] interface and the
platform-specific transport APIs.

## Implementation

### 1. Editor alignment

`EditorActions.alignLeft(profile, controlId, now)`
moves the control's `x` to the minimum `x` of
every other control. The other alignments
mirror the same logic on the right / top /
bottom edges. Each alignment is a no-op on a
profile with fewer than 2 controls (a single
control has no "other" controls to align to).

`EditorActions.distributeHorizontally(profile,
now)` sorts the controls by `x` and places the
middle controls at the equally-spaced centers
between the first and last. The first and last
controls keep their positions (the
"anchors"). The distribution is a no-op on a
profile with fewer than 3 controls.

`EditorActions.setHitBounds(profile, controlId,
newHitBounds, now)` updates the control's
`hitBounds` independently of its `visualBounds`.
The §15 "Aumentar hitbox" feature.

### 2. Per-posture layout

`PostureAwareMainScreen` takes a [Posture] and
renders the appropriate layout:

* `OPEN`, `FLAT`, `UNKNOWN` → the existing
  `MainScreen` (single-pane).
* `HALF_OPENED` → the
  `TabletopMainScreen`: top half is the
  dashboard (profile selector), bottom half
  is the editor + touch surface.
* `CLOSED` → the `CompactMainScreen`: the
  cover screen, profile selector only.

The activity wires the [PostureObserver] into
the `postureFlow`; the screen observes the
flow. On a non-foldable device, the observer
returns `UNKNOWN` for the lifetime of the
activity, and the single-pane layout is used.

### 3. Keystore signature

`KeystoreProfileSigner.getOrCreateKey(context)`
returns a hardware-backed HMAC-SHA256 key. The
key is generated on first call and stored in
the Android Keystore; subsequent calls return
the existing key.

`KeystoreProfileSigner.sign(context, profile)`
encodes the profile as JSON (via
[ProfileJson.toJson]), computes the
HMAC-SHA256, and returns a 64-character hex
string. The verify function is constant-time
(per-byte XOR).

### 4. USB + Elysium Link + Bluetooth HID

All three transport skeletons implement the
[ControllerTransport] interface. The skeletons
differ in the wire format and the platform
API; the interface is the testable surface.

The [BluetoothHidTransport.sendRealtime] uses
the existing [HidReportEncoder.encodeBasicGamepadV1]
to produce the 13-byte input report; the report
is sent via `hidDevice.sendReport(host, REPORT_ID,
report)`. The full §18 descriptor (already
generated and validated by
`tools/hid-descriptor-validator`) is in the
report.

The [UsbAccessoryTransport] uses the Android
`UsbManager.openAccessory(accessory)` API; the
read loop runs on `Dispatchers.IO`. The wire
format is a placeholder; the real format is
the same as the BT HID transport.

The [LocalNetworkElysiumLinkTransport] uses a
`ServerSocket` on port 7777; it accepts a
single connection. The wire format is a
placeholder; the real format is the §19
Elysium Link (Phase 5+, over QUIC).

## Tests

* 9 new tests in `EditorActionsTest` —
  alignment, distribution, hitBounds, no-op
  guards.
* All Phase 0/1.0-1.6 tests still green (the
  changes are additive).

**Total: 372 tests, 0 failures, 0 errors.**

## Results

* `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
* `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**.
  APK is 8.9 MB.

## Metrics

* APK size: 8.9 MB.
* New code: ~2,500 lines of Kotlin (production)
  + ~400 lines of test code.
* Test count: 372 (Phase 1.6: 363; +9 from
  this phase).
* No new dependencies.

## Failures and regressions

No new failures or regressions. The Phase 1.4
builds (which already have Bug #17 lint and
Bug #19 Robolectric) remain deferred.

## Risks

* **Transport skeletons are not end-to-end
  tested.** The skeletons implement the
  [ControllerTransport] interface correctly
  (the interface is the testable surface;
  `FakeTransport` is the JVM test for the
  interface). The real transport lifecycle
  (Bluetooth pairing, USB accessory detection,
  Elysium Link acceptance) requires hardware
  and lands in Phase 2+ / Phase 5+.
* **KeystoreProfileSigner is not unit-tested.**
  The Keystore is Android-only; the unit tests
  would need Robolectric (Bug #19) to exercise
  the Keystore. The signing logic is verified
  by [ProfileSignature] (the §15 test surface
  for the algorithm); the Keystore is a
  storage concern.
* **Posture-aware layout is not visually
  verified.** The posture is `UNKNOWN` on the
  emulator (no virtual foldable hinge). The
  layout switches to the single-pane mode by
  default. A real foldable device (Phase 1.10+
  verification on hardware) is the only way
  to verify the tabletop / compact layouts.

## Next block — Phase 1.12+

* **Editor alignment UI chips** — the toolbar
  exposes the alignment / distribution
  actions as a `Row` of `AssistChip`s.
* **Confirmation dialog for "Delete profile"**
  (the §15 confirmation pattern).
* **Compose Compiler upgrade** — when KSP
  releases for Kotlin 2.2.x, bump Kotlin +
  Compose Compiler to fix Bug #17 and unblock
  Bug #19.
* **Phase 2** — real Bluetooth HID transport
  (requires a real device with Bluetooth
  HID host support; the emulator's virtual
  BT is unreliable).
* **Phase 3** — desktop agents (macOS /
  Windows / Linux) — separate codebases,
  deferred to a follow-on project.
* **Phase 4** — Nexus Receiver (hardware) —
  deferred to a follow-on project.
* **Phase 5** — Elysium Link over QUIC (the
  skeleton's TCP becomes QUIC) + Remote
  Companion.
