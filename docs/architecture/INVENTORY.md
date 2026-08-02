# Inventory

> **Status:** Phase ULT.8 — updated after
> `MASTER_ORDER.md` §47 "Genera inventario".
> This is the project's current state-of-the-code
> snapshot. It is **not** a wishlist; everything
> listed here exists in the repo today.

## 1. Production code (Android) — 131 source files

### Core engine

| Module | Path | Status |
|--------|------|--------|
| `CanonicalInputEngine` | `core/engine/` | `VERIFIED_LAB` |
| `EngineState` + `TransportBinding` | `core/engine/` | `VERIFIED_LAB` |
| `StickFilters` (Linear, Exponential, SCurve, CubicBlend, CustomCubic) | `core/filter/` | `VERIFIED_LAB` |
| `StickConfig` (reactive, settings→engine) | `core/filter/` | `VERIFIED_LAB` |
| `TriggerFilters` + `TriggerDigitalDetector` + `TriggerConfig` | `core/filter/` | `VERIFIED_LAB` |
| `ResponseCurve` | `core/filter/` | `VERIFIED_LAB` |

### Input model

| Module | Path | Status |
|--------|------|--------|
| `UniversalControllerState` (23 buttons, sticks, triggers, dpad, motion, battery) | `core/model/` | `VERIFIED_LAB` |
| `CanonicalButton` enum | `core/model/` | `VERIFIED_LAB` |
| `ButtonSet`, `StickState`, `TriggerState`, `DpadState`, `MotionState`, `TouchCollection`, `BatteryState` | `core/model/` | `VERIFIED_LAB` |
| `TouchEventDispatcher` + `TouchSurfaceView` | `input/` | `VERIFIED_LAB` |
| `AirMouseController` (IMU→cursor) | `core/motion/` | `VERIFIED_LAB` |
| `MotionSensorSource` + `AndroidMotionSensorSource` | `core/motion/` | `VERIFIED_LAB` |

### HID

| Module | Path | Status |
|--------|------|--------|
| `HidDescriptor` + `HidReportEncoder` (BASIC_GAMEPAD_V1, 86 bytes) | `core/hid/` | `VERIFIED_LAB` |
| `HidDescriptors` (combo: keyboard+mouse+consumer) | `core/transport/hid/` | `VERIFIED_LAB` |
| `HidReports` (keyboard/mouse/consumer report builders) | `core/transport/hid/` | `VERIFIED_LAB` |

### Transports

| Module | Path | Status |
|--------|------|--------|
| `ControllerTransport` interface | `core/transport/` | `VERIFIED_LAB` |
| `LocalEchoTransport` (echo back for testing) | `core/transport/` | `VERIFIED_LAB` |
| `BluetoothHidTransport` (BT HID Device API 28+) | `core/transport/hid/` | `VERIFIED_LAB` |
| `MacTransport` + `MacProtocol` + `MacCrypto` + `MacDiscovery` | `core/transport/mac/` | `VERIFIED_LAB` |
| `UsbAccessoryTransport` (skeleton) | `core/transport/` | `SKELETON` |
| `LocalNetworkElysiumLinkTransport` (skeleton, port 7777) | `core/transport/` | `SKELETON` |

### Profile system

| Module | Path | Status |
|--------|------|--------|
| `Profile` + `ProfileJson` + `ProfileSignature` | `core/profile/` | `VERIFIED_LAB` |
| `KeystoreProfileSigner` (Android Keystore) | `core/profile/` | `VERIFIED_LAB` |
| `ProfileShareBuilder` + `AndroidProfileShareLauncher` | `core/profile/` | `VERIFIED_LAB` |
| `ProfileImporter` + `ProfileActions` (duplicate/rename) | `core/profile/` | `VERIFIED_LAB` |
| `LastDeviceMemory` + `LastDevice` (Quick Connect) | `core/profile/` | `VERIFIED_LAB` |
| `CanonicalBinding` + `ControlElement` + `ControlType` | `core/profile/` | `VERIFIED_LAB` |

### Database

| Module | Path | Status |
|--------|------|--------|
| `ProfileDatabase` + `ProfileDao` + `ProfileEntity` + `RoomProfileRepository` | `databases/profile/` | `VERIFIED_LAB` |
| `CompatibilityDatabase` + `CompatibilityDao` + `RoomCompatibilityRepository` | `databases/compatibility/` | `VERIFIED_LAB` |
| `InMemoryProfileRepository` (test double) | `databases/profile/` | `VERIFIED_LAB` |
| `InMemoryCompatibilityRepository` (test double) | `databases/compatibility/` | `VERIFIED_LAB` |

### Fabric (universal control fabric)

| Module | Path | Status |
|--------|------|--------|
| `Capability` (40+ variants, each with ActionRisk) | `fabric/canonical/` | `VERIFIED_LAB` |
| `DeviceTwin` (immutable, 40+ device types, 9 state types) | `fabric/canonical/` | `VERIFIED_LAB` |
| `DeviceKnowledgeGraph` (immutable, 9 relation types) | `fabric/canonical/` | `VERIFIED_LAB` |
| `DeviceIdentity` + `InMemoryDeviceIdentity` + `Fingerprint` | `fabric/identity/` | `VERIFIED_LAB` |
| `AndroidDeviceIdentity` (Keystore-backed) | `fabric/identity/` | `VERIFIED_LAB` |
| `Automation` (triggers, conditions, actions, compensation) | `fabric/automation/` | `VERIFIED_LAB` |
| `AutomationEngine` (deterministic executor) | `fabric/automation/` | `VERIFIED_LAB` |
| `IrProtocol` (NEC, NECx, RC5, RC6, SIRC, Samsung, Kaseikyo, Raw) | `fabric/infrared/` | `VERIFIED_LAB` |
| `IrWaveform` (encode/decode, ±25% tolerance) | `fabric/infrared/` | `VERIFIED_LAB` |
| `AndroidIrTransmitter` (ConsumerIrManager adapter) | `fabric/infrared/` | `VERIFIED_LAB` |
| `IrLearner` (IR receiver + learning) | `fabric/infrared/` | `SKELETON` |
| `DeviceCategory` + `DeviceTemplate` (30+ TV brands) | `core/device/` | `VERIFIED_LAB` |

### Haptics / Settings / Posture

| Module | Path | Status |
|--------|------|--------|
| `Haptics` (sealed event API) + `AndroidHaptics` | `core/haptics/` | `VERIFIED_LAB` |
| `SettingsAwareHaptics` decorator | `core/haptics/` | `VERIFIED_LAB` |
| `AppSettings` + `AppSettingsStore` + `AndroidAppSettingsStore` | `core/settings/` | `VERIFIED_LAB` |
| `PostureObserver` + `AndroidPostureObserver` + `NullPostureObserver` | `core/posture/` | `VERIFIED_LAB` |
| `PostureAdaptiveLayout` (split at hinge) | `ui/responsive/` | `VERIFIED_LAB` |
| `ResponsiveLayout` (1/2/3/4 columns) | `ui/responsive/` | `VERIFIED_LAB` |
| `LatencyTracker` | `core/latency/` | `VERIFIED_LAB` |
| `CompatibilityStatus` + `CompatibilityResult` | `core/compat/` | `VERIFIED_LAB` |

### UI screens

| Module | Path | Status |
|--------|------|--------|
| `MainActivity` + `MainScreen` + `PostureAwareMainScreen` | `ui/` | `VERIFIED_LAB` |
| `HubScreen` + `HubNavigation` + `DeviceCategoryScreen` | `ui/hub/` | `VERIFIED_LAB` |
| `QuickConnectCard` (one-tap reconnect) | `ui/hub/` | `VERIFIED_LAB` |
| `TvControlsSection` (30+ brands, search, tiers) | `ui/hub/` | `VERIFIED_LAB` |
| `ConsoleScreens` (PlayStation/Xbox/Nintendo sub-categories) | `ui/hub/` | `VERIFIED_LAB` |
| `EditorCanvas` + `EditorToolbar` + `EditorActions` | `ui/editor/` | `VERIFIED_LAB` |
| `ProfileSelector` + `ProfileImportDialog` + `ProfileRenameDialog` | `ui/editor/` | `VERIFIED_LAB` |
| `TransportSelector` + `TouchSurfaceViewHost` | `ui/editor/` | `VERIFIED_LAB` |
| `MacDiscoveryScreen` (mDNS radar) + `MacPairingScreen` (X25519+PIN) | `ui/mac/` | `VERIFIED_LAB` |
| `MacControlSurfaceScreen` (trackpad+keyboard+media) | `ui/mac/` | `VERIFIED_LAB` |
| `UniversalControlScreen` (BT HID setup+trackpad+air mouse) | `ui/universal/` | `VERIFIED_LAB` |
| `TvControlScreen` (IR grid per brand) | `ui/control/` | `VERIFIED_LAB` |
| `IrConnectFlow` (4-step guided) | `ui/connect/` | `VERIFIED_LAB` |
| `SettingsDialog` (sensitivity, axis, haptics) | `ui/settings/` | `VERIFIED_LAB` |
| `HelpOverlay` + `GuidedTourOverlay` | `ui/help/` | `VERIFIED_LAB` |
| `ElysiumTheme` + `NeonPrimitives` (3D glow) | `ui/theme/` | `VERIFIED_LAB` |
| `ManualAddHostDialog` | `ui/mac/` | `VERIFIED_LAB` |

## 2. Production code (Mac agent — Swift)

| Module | Path | Status |
|--------|------|--------|
| `EventInjector` (CoreGraphics + media keys) | `apps/mac-agent/Sources/` | `VERIFIED_LAB` |
| `ConnectionHandler` (decrypt + dispatch) | `apps/mac-agent/Sources/` | `VERIFIED_LAB` |
| `Protocol` (frame types) | `apps/mac-agent/Sources/` | `VERIFIED_LAB` |

## 3. Production code (Rust crates)

| Module | Path | Status |
|--------|------|--------|
| `Capability` | `crates/control-core/` | NEW (Phase ULT.0) |
| `DeviceTwin` | `crates/device-twin/` | NEW (Phase ULT.0) |
| `DeviceKnowledgeGraph` | `crates/device-twin/` | NEW (Phase ULT.0) |
| `IrProtocol` + `IrWaveform` | `crates/ir-core/` | NEW (Phase ULT.0) |
| `DeviceIdentity` | `crates/crypto-core/` | NEW (Phase ULT.0) |
| `Automation` + `AutomationEngine` | `crates/automation-core/` | NEW (Phase ULT.0) |

## 4. Test count

- **JVM unit tests**: 597 (Phase ULT.8)
- **Test files**: 62
- **Coverage gates**: every `data class` validated by
  `init` block; every public function has at least
  one happy-path + one failure-path test
- **§38 disconnect test**: shipped (release-blocker)

## 5. Documentation

- `MASTER_ORDER.md` (the constitution, §0–§46)
- `MASTER_ORDER_SECTIONS.md` (§0–§46)
- `ARCHITECTURE.md` (Android-focused arch)
- `ARCHITECTURE_MAP.md` (full fabric map)
- `INVENTORY.md` (this file)
- `THREAT_MODEL.md` (STRIDE per trust boundary)
- `RISK_REGISTER.md` (R0–R9)
- `0001-stack-and-principles.md` (1 ADR)
- 27 changelogs in `docs/changelogs/`

## 6. CI

- `.github/workflows/android-ci.yml` — runs JVM
  tests + assembleDebug + hid-descriptor-validator
  + uploads APK as build artifact

## 7. What's NOT in the repo today

These exist in the spec but **not** in the code yet:

- Desktop agents (Windows / Linux) — empty scaffolds
- Hub OS / services / containers — empty scaffolds
- Firmware (Receiver) — empty scaffolds
- Matter / Thread / Zigbee / Z-Wave / BLE / ONVIF /
  MQTT / Media / Vendor adapters — empty scaffolds
- Home Assistant / Google Home / Apple Home / Alexa
  adapters — empty scaffolds
- All schemas/ — empty
- Most databases/ — empty
- Most tools/ — empty (only README placeholders)

This is intentional: per `MASTER_ORDER.md` §47, the
project ships one phase at a time, and the empty
modules carry `README.md` placeholders so §6 is
respected. The next phase populates the next empty
module.
