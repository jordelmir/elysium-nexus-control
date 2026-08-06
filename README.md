# Elysium Nexus Universal Controller

> **v0.3.0-ir-fabric-rc1** · A platform that turns any Android phone into a **universal, professional, dynamic control surface** for desktop, mobile, console, IoT, and infrared-controlled devices.

[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Tests](https://img.shields.io/badge/tests-772%20passed-brightgreen)]()
[![Lint](https://img.shields.io/badge/lint-0%20errors-brightgreen)]()
[![Version](https://img.shields.io/badge/version-0.3.0--ir--fabric--rc1-blue)]()
[![License](https://img.shields.io/badge/license-proprietary-lightgrey)]()

```
┌──────────────────────────────────────────────┐
│  Phone touch surface                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Stick L  │  │ Buttons  │  │ Stick R  │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ LT  RT   │  │ D-pad    │  │ Touchpad │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│  ┌──────────────────────────────────────┐    │
│  │         IR Remote Control            │    │
│  │   915 brands · 108,681 commands      │    │
│  └──────────────────────────────────────┘    │
└──────────────────────────────────────────────┘
        │              │                 ▲
        │ BT HID       │ IR Blaster      │ Wi-Fi / Elysium Link
        │ (Gamepad)     │ (38kHz)         │ (Mac agent + AES)
        ▼              ▼                 │
   ┌──────────────────────────────────────┐
   │  TV / AC / STB / Desktop / Steam     │
   └──────────────────────────────────────┘
```

---

## Features

### 🔴 Infrared Universal Remote — IR Data Fabric v0.3.0
- **915 brands**, **37 device types**, **2,394 code sets**, **108,681 verified IR commands** in a local-first SQLite catalog (26.62 MB).
- **7 supported protocols**: NEC, NECx, Samsung32, Sony SIRC (12/15/20), RC5, RC6, Kaseikyo/Panasonic — all with golden vector verified encoders.
- **Smart probing engine**: Automatic brand/model detection via sequential candidate testing with physical SHA-256 signal fingerprinting.
- **Persistent winner profiles**: Once a working code set is confirmed, it's saved locally and survives app restarts, updates, and device reboots.
- **Strict protocol resolution**: Fail-closed `ProtocolResolution.Unsupported` — zero silent NEC fallbacks.
- **Immutable supply chain**: 5 upstream IR repositories locked to exact commit SHAs with cryptographic license verification.

### 🖥️ USB-C Direct Mac Screen Replacement
- **60 FPS ultra-low latency** headless Mac control over USB-C cable (`127.0.0.1:7878`).
- **Zero-PIN auto-connection**: Instant pairing with no prompts.
- **Dual modes**: `100% Pantalla` (edge-to-edge monitor) and `Teclado Mac` (split display + keyboard).
- **Zero accumulative latency**: `conflate()` frame stream ensures <15ms glass-to-glass delay.

### 🎮 Universal Bluetooth HID Gamepad
- **Combo HID descriptor**: Keyboard + Mouse + Consumer Control + Gamepad in a single Bluetooth profile.
- **23-button canonical model** with 5 stick filter curves, trigger pipeline, and touch arbitration.
- **Foldable-aware**: `PostureAdaptiveLayout` via `WindowManager` for devices like Honor Magic V2.

### 📡 Multi-Transport Architecture
- Bluetooth HID (BLE / Classic)
- USB-C Direct (ADB + Screen Mirror)
- Wi-Fi (Elysium Link encrypted)
- Infrared (ConsumerIrManager)
- Future: Elysium Nexus Receiver (BLE/Wi-Fi/USB hardware dongle)

---

## Quick Start

### Build & Install
```bash
cd apps/android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Mac Setup (1-Click)
```bash
./tools/install-mac.sh
```

### IR Catalog Rebuild (Development)
```bash
# Bootstrap upstream IR data sources
bash tools/ir-data/bootstrap-sources.sh --locked

# Build production catalog (excludes gated sources)
python3 tools/ir-data/build_catalog.py --profile production

# Verify supply chain integrity
python3 tools/ir-data/verify_source_locks.py
```

---

## Build Matrix

| Component | Language | Min API | Target API | Tests | Status |
|-----------|----------|---------|------------|-------|--------|
| Android Controller | Kotlin 2.2.21 | 26 | 34 | 772 | ✅ Green |
| Mac Agent | Swift | macOS 12+ | — | — | ✅ Functional |
| IR Data Pipeline | Python 3 | — | — | — | ✅ Production |
| Desktop Agents | — | — | — | — | 🔲 Phase 3 |
| Nexus Receiver FW | — | — | — | — | 🔲 Phase 4 |
| Console Backends | — | — | — | — | 🔒 Vendor License Gated |

---

## Codebase Metrics

| Metric | Value |
|--------|-------|
| **Kotlin source files** | 194 |
| **Kotlin production LoC** | 41,586 |
| **Kotlin test LoC** | 12,827 |
| **Python tooling LoC** | 2,336 |
| **JVM unit tests** | 772 (100% green) |
| **Lint errors** | 0 |
| **APK size (debug)** | 73 MB |
| **IR catalog size** | 26.62 MB |
| **IR brands** | 915 |
| **IR commands** | 108,681 |

---

## Architecture

```
elysium-nexus-controller/
├── apps/
│   ├── android/                    # Main APK (Kotlin, Compose, Room)
│   │   └── app/src/main/
│   │       ├── java/com/elysium/nexus/
│   │       │   ├── core/           # Domain models (CanonicalInput, IrAction, InstalledIrProfile)
│   │       │   ├── fabric/         # Engine layer (IR, HID, Dispatch, Profile persistence)
│   │       │   │   ├── infrared/   # IrWaveform, IrProtocol, IrProbeEngine, ProtocolCodecRegistry
│   │       │   │   │   ├── database/   # IrCatalogRepository, IrCatalogDatabaseManager (SQLite)
│   │       │   │   │   └── ingestion/  # LicenseGate, parsers per upstream repo
│   │       │   │   ├── dispatch/   # ActionDispatcher, DeviceCommandResolver
│   │       │   │   ├── profile/    # InstalledIrProfileRepository
│   │       │   │   └── canonical/  # UniversalAction, CanonicalEngine
│   │       │   └── ui/            # Compose screens (Hub, Control, Connect, Settings)
│   │       └── assets/ir/         # ir_catalog.db + manifest + rejections
│   ├── macos-agent/               # Swift daemon (screen capture + ADB bridge)
│   └── profile-studio/            # Editor host (future)
├── ir-data/                       # sources.lock.json, ingestion configs
├── tools/ir-data/                 # bootstrap-sources.sh, build_catalog.py, lock/verify scripts
├── docs/
│   ├── architecture/              # MASTER_ORDER.md
│   ├── adr/                       # Architecture Decision Records
│   ├── changelogs/                # 48 phase changelogs
│   ├── audits/                    # IR_V03_BASELINE.md
│   ├── licensing/                 # License matrix
│   └── security/                  # Threat model
└── .github/workflows/             # CI
```

---

## IR Data Fabric — Supply Chain

| Source Repository | License | Commit Lock | Production |
|-------------------|---------|-------------|------------|
| [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) | CC0-1.0 | `d126fb1b` | ✅ Included |
| [SmartIR](https://github.com/smartHomeHub/SmartIR) | MIT | `e4df2957` | ✅ Included |
| [radioxoma/infrared](https://github.com/radioxoma/infrared) | CC0-1.0 | `96179666` | ✅ Included |
| [IrpTransmogrifier](https://github.com/bengtmartensson/IrpTransmogrifier) | GPL-2.0+ | `8636d20a` | ✅ Spec Reference |
| [probonopd/irdb](https://github.com/probonopd/irdb) | GPL-2.0 | `11aa5eb3` | 🔒 **GATED** (excluded from APK) |

All locks verified via `verify_source_locks.py` with 40-hex SHA commit, tree, and license file hashes.

---

## Supported IR Protocols

| Protocol | Carrier | Encoder Status | Golden Vectors |
|----------|---------|----------------|----------------|
| NEC | 38 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 12 |
| NECx (Extended) | 38 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 10 |
| Samsung32 | 38 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 8 |
| Sony SIRC (12/15/20) | 40 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 6 |
| RC5 (Manchester) | 36 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 5 |
| RC6 | 36 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 4 |
| Kaseikyo / Panasonic | 38 kHz | ✅ GOLDEN_VECTOR_VERIFIED | 4 |

---

## Hard Rules (No Exceptions)

1. **No impersonation of commercial devices.** We ship our own descriptor under our own VID/PID (`Elysium Nexus Gamepad`). We never present a fake Xbox- or PlayStation-branded identity.
2. **Licensed console backends are gated.** Direct PS4/5, Xbox One/Series, Switch/2 backends compile only when a vendor license, authorized SDK, and provisioned secrets are present.
3. **No Accessibility abuse for gamepad injection.** Accessibility Service is not a substitute for a real system-level gamepad.
4. **Disconnection must neutralize everything.** Test §38 is a release blocker. Zero stuck inputs after abrupt disconnect.
5. **No silent claims.** Compatibility states: `VERIFIED_LAB`, `VERIFIED_COMMUNITY`, `PARTIALLY_VERIFIED`, `UNVERIFIED`, `REGRESSION`, `BLOCKED`.
6. **No GlobalScope in Kotlin.** Structured concurrency tied to lifecycle or service scope.
7. **No `unwrap()` in production Rust.** Typed errors, no panics on external data.
8. **No device-hardcoding.** Honor Magic V2 is the *lab* device, not the *target*. Capabilities come from `WindowManager` + `InputDevice` + sensor introspection.
9. **No silent NEC fallbacks.** Protocol resolution is fail-closed with `ProtocolResolution.Unsupported`.
10. **No commercial / cloud gate for the core.** The APK works as a local controller without a network.

---

## Changelog Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 0.1 | Gradle 9.3.1, AGP 8.7.3, Kotlin 2.0.21, JUnit | ✅ |
| 0.2 | Canonical input model (23 buttons) | ✅ |
| 0.3 | Stick filter pipeline (5 curves) | ✅ |
| 0.4 | Canonical input engine + §32 state machine | ✅ |
| 0.5 | Trigger + touch pipeline | ✅ |
| 0.6 | §38 disconnect test (release blocker) | ✅ |
| 0.7 | First MainActivity end-to-end | ✅ |
| 0.8 | §30 latency harness (p50 0.05ms) | ✅ |
| 0.9 | Generic HID descriptor + compatibility DB | ✅ |
| 1.0 | Room + Compose UI | ✅ |
| 1.1–1.24 | Profile editor, gestures, motion, haptics, transports, settings, CI | ✅ |
| ULT.0–ULT.13 | Fabric foundation, hierarchy, BT HID, foldable, media keys, IR protocols | ✅ |
| IR Fabric | Local-first SQLite IR catalog (108,681 commands, 915 brands) | ✅ |
| **IR v0.3.0-rc1** | **8 P0 defects fixed, strict protocol resolution, supply chain locking** | ✅ |

All 48 phase changelogs are in [`docs/changelogs/`](docs/changelogs/).

---

## Contributing

Read [`AGENTS.md`](AGENTS.md) first. It is the project's operating contract: hard rules, working contract with the maintainer, iteration loop, build commands, and source-of-truth ordering.

---

## License

The codebase is the property of the project owner. The project is not open source at this time; permission to mirror or fork is not granted by default.
