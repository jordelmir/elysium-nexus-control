# Inventory

> **Status:** Phase ULT.0 — generated per
> `MASTER_ORDER.md` §47 "Genera inventario".
> This is the project's current state-of-the-code
> snapshot. It is **not** a wishlist; everything
> listed here exists in the repo today.

## 1. Production code (Android)

| Module | Path | Status |
|--------|------|--------|
| `CanonicalInputEngine` | `apps/android/.../core/engine/` | `VERIFIED_LAB` (emulator) |
| `StickFilters` (Linear, Exponential, SCurve, CubicBlend, CustomCubic) | `apps/android/.../core/filter/` | `VERIFIED_LAB` |
| `TriggerFilters` + `TriggerDigitalDetector` | `apps/android/.../core/trigger/` | `VERIFIED_LAB` |
| `TouchEventDispatcher` + `TouchSurfaceView` | `apps/android/.../input/` | `VERIFIED_LAB` |
| `HidDescriptor` + `HidReportEncoder` (BASIC_GAMEPAD_V1, 86 bytes) | `apps/android/.../core/hid/` | `VERIFIED_LAB` (validator green) |
| `CompatibilityStatus` enum + Room DB | `apps/android/.../databases/compatibility/` | `VERIFIED_LAB` |
| `Profile` + `ProfileJson` + `ProfileSignature` | `apps/android/.../core/profile/` | `VERIFIED_LAB` |
| `KeystoreProfileSigner` (Android Keystore) | `apps/android/.../core/profile/` | `VERIFIED_LAB` |
| `ProfileShareBuilder` + `AndroidProfileShareLauncher` (FileProvider) | `apps/android/.../core/profile/` | `VERIFIED_LAB` |
| `ProfileImporter` + `ProfileActions` (duplicate / rename) | `apps/android/.../core/profile/` | `VERIFIED_LAB` |
| `ControllerTransport` + `LocalEchoTransport` | `apps/android/.../core/transport/` | `VERIFIED_LAB` |
| `BluetoothHidTransport` (skeleton) | `apps/android/.../core/transport/` | `SKELETON` (HiL pending) |
| `UsbAccessoryTransport` (skeleton) | `apps/android/.../core/transport/` | `SKELETON` (HiL pending) |
| `LocalNetworkElysiumLinkTransport` (skeleton, port 7777) | `apps/android/.../core/transport/` | `SKELETON` (HiL pending) |
| `TransportBinding` (engine → transport bridge) | `apps/android/.../core/engine/` | `VERIFIED_LAB` |
| `Haptics` (sealed event API) + `AndroidHaptics` (Vibrator) | `apps/android/.../core/haptics/` | `VERIFIED_LAB` |
| `SettingsAwareHaptics` decorator | `apps/android/.../core/haptics/` | `VERIFIED_LAB` |
| `MotionSensorSource` + `AndroidMotionSensorSource` | `apps/android/.../core/motion/` | `VERIFIED_LAB` |
| `PostureObserver` + `AndroidPostureObserver` (Jetpack WindowManager) | `apps/android/.../core/posture/` | `VERIFIED_LAB` |
| `AppSettings` + `AppSettingsStore` + `AndroidAppSettingsStore` (SharedPreferences) | `apps/android/.../core/settings/` | `VERIFIED_LAB` |
| `SettingsDialog` (3 sections) | `apps/android/.../ui/settings/` | `VERIFIED_LAB` |
| `EditorCanvas` + `EditorToolbar` + `EditorActions` | `apps/android/.../ui/editor/` | `VERIFIED_LAB` |
| `ProfileSelector` + `ProfileImportDialog` + `ProfileRenameDialog` + `TransportSelector` | `apps/android/.../ui/editor/` | `VERIFIED_LAB` |
| `MainActivity` + `MainScreen` + `PostureAwareMainScreen` | `apps/android/.../ui/` | `VERIFIED_LAB` (emulator) |

## 2. Production code (Rust crates)

| Module | Path | Status |
|--------|------|--------|
| `Capability` (30+ variants per §4.3) | `crates/control-core/` | NEW this iteration |
| `DeviceTwin` (per §4.2) | `crates/device-twin/` | NEW this iteration |
| `DeviceKnowledgeGraph` (per §5) | `crates/device-twin/` | NEW this iteration |
| `IrProtocol` (NEC, NECx, RC5, RC6, SIRC, Samsung, Kaseikyo, raw) | `crates/ir-core/` | NEW this iteration |
| `IrWaveform` (encode / decode / carrier estimation) | `crates/ir-core/` | NEW this iteration |
| `DeviceIdentity` (fingerprint + signing key) | `crates/crypto-core/` | NEW this iteration |
| `Automation` (trigger / conditions / actions / compensation) | `crates/automation-core/` | NEW this iteration |
| `AutomationEngine` (deterministic executor) | `crates/automation-core/` | NEW this iteration |

(Other crates per §44 are scaffolds with `README.md`
per §6 "Empty modules are forbidden".)

## 3. Production code (adapters)

| Module | Path | Status |
|--------|------|--------|
| `AndroidIrTransmitter` (ConsumerIrManager + carrier range) | `adapters/infrared/` | NEW this iteration |
| `AndroidDeviceIdentity` (Android Keystore-backed) | `adapters/android/` (new) | NEW this iteration |

(Other adapters per §44 are scaffolds with
`README.md`.)

## 4. Test count

- **JVM unit tests**: 434 (Phase 1.24) + new (Phase ULT.0)
- **Coverage gates**: every `data class` validated by
  `init` block; every public function has at least
  one happy-path + one failure-path test
- **§38 disconnect test**: shipped (release-blocker)

## 5. Documentation

- `MASTER_ORDER.md` (the constitution)
- `MASTER_ORDER_SECTIONS.md` (§0-§46)
- `ARCHITECTURE.md` (the Android-focused arch)
- `ARCHITECTURE_MAP.md` (this iteration — full fabric)
- `INVENTORY.md` (this file)
- `docs/security/THREAT_MODEL.md` (this iteration)
- `docs/security/RISK_REGISTER.md` (this iteration)
- `docs/adr/0001-stack-and-principles.md` (1 ADR so far)
- `docs/changelogs/PHASE_<N>_*.md` (24 changelogs)

## 6. CI

- `.github/workflows/android-ci.yml` — runs JVM
  tests + assembleDebug + hid-descriptor-validator
  + uploads APK as build artifact

## 7. What's NOT in the repo today

These exist in the spec but **not** in the code yet:

- Desktop agents (macOS / Windows / Linux) — empty
- Hub OS / services / containers — empty
- Firmware (Receiver) — empty
- Matter / Thread / Zigbee / Z-Wave / BLE / ONVIF /
  MQTT / Media / Vendor adapters — empty
- Home Assistant / Google Home / Apple Home / Alexa
  adapters — empty
- All schemas/ — empty
- Most databases/ — empty
- All tools/ — empty (only README placeholders)

This is intentional: per `MASTER_ORDER.md` §47, the
project ships one phase at a time, and the empty
modules carry `README.md` placeholders so §6 is
respected. The next phase populates the next empty
module.
