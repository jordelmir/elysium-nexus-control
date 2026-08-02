# Elysium Nexus Universal Controller

> A platform that turns any Android phone into a **universal,
> professional, dynamic control surface** for desktop, mobile,
> console, and IoT hosts.

```
┌──────────────────────────────────────────────┐
│  Phone touch surface                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Stick L  │  │ Buttons  │  │ Stick R  │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ LT  RT   │  │ D-pad    │  │ Touchpad │   │
│  └──────────┘  └──────────┘  └──────────┘   │
└──────────────────────────────────────────────┘
        │                                ▲
        │ Bluetooth HID                  │ Wi-Fi / Elysium Link
        │ (Elysium Nexus Gamepad)        │ (Mac agent + encryption)
        ▼                                │
   ┌──────────────────────────────────────┐
   │  Desktop / Steam / browsers / emus   │
   └──────────────────────────────────────┘
```

The project is a fully native Android app. The APK ships its own
device descriptor (`Elysium Nexus Gamepad`); we never impersonate
a DualSense, DualShock, Xbox, or Pro controller. The project
respects vendor licensing: PS4/5, Xbox One/Series, and Switch/2
backends compile **only** when a vendor license, an authorized
SDK, and provisioned secrets are present.

## What this project actually does (real, verified)

### Mac / PC control (Wi-Fi, Elysium Link)
- mDNS discovery of Mac agents on the local network
- X25519 + PIN pairing (encrypted channel)
- Multi-touch trackpad: 1-finger move/click, 2-finger
  scroll/pinch, 3-finger Mission Control/Swipe/Spaces
- Keyboard: uses Android system IME (user's dictionary,
  swipe-to-type, emoji)
- Modifier chips: ⌘ ⌥ ⌃ ⇧ Esc (toggleable, combinable)
- Media keys: Volume Up/Down, Mute, Play/Pause, Next/Previous
- Mac agent (Swift) runs on the host, receives encrypted
  input events, injects them via CoreGraphics

### Universal remote (Bluetooth HID)
- Registers as a Bluetooth HID Device (API 28+)
- Combo descriptor: keyboard + mouse + consumer control
- Works with any Bluetooth host: macOS, Windows, Linux,
  Android TV, Raspberry Pi, smart TVs
- Air mouse mode: gyroscope-driven cursor (no touch surface
  needed), with sensitivity/dead-zone/recenter controls
- 3-finger swipe → media keys (volume, track skip)

### TV control (IR)
- 30+ TV brands: Samsung, LG, Sony, Panasonic, Philips,
  TCL, Hisense, Vizio, Sharp, Toshiba, and 20+ more
- IR protocol support: NEC, NECx, RC5, RC6, Sony SIRC,
  Samsung, Kaseikyo, Raw
- Guided IR connect flow (4 steps)
- Real IR transmit (requires device with IR emitter;
  Honor Magic V2 does NOT have one)

### Controller editor
- Drag-and-drop control placement on a touch canvas
- Profile system: create, rename, duplicate, delete, import
  (paste JSON), export (share sheet)
- Keystore-backed profile signing
- Stick filters: Linear, Exponential, S-Curve, CubicBlend,
  Custom Cubic
- Trigger filters + digital detector
- Sensitivity, axis inversion, haptics toggle
- Transport selector (Local Echo, Bluetooth HID, Mac Link,
  USB, Elysium Link)

### Foldable support (Honor Magic V2)
- PostureObserver via Jetpack WindowManager
- PostureAdaptiveLayout: splits screen at hinge in
  half-opened posture (laptop-style)
- Cover screen prompt when device is folded shut

### Quick Connect
- Remembers the last connected device (Mac IP+port or
  Bluetooth address)
- One-tap reconnect from the Hub (no navigation through
  the full hierarchy)

### Other
- Haptics with settings-aware decorator
- Latency harness (p50 measured at 0.05ms)
- Section 38 disconnect test (release blocker: no stuck
  inputs after abrupt disconnect)
- GitHub Actions CI (test + assemble on push/PR)
- Bilingual UI (es + en)

## Quick start

```bash
# Build + test
cd apps/android
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug
```

## Build matrix

| Component          | Min API | Target API | Status               |
|--------------------|---------|------------|----------------------|
| Android app        | 26      | 34         | green (597 tests)    |
| Mac agent (Swift)  | —       | —          | functional           |
| Desktop agents     | —       | —          | Phase 3 (out of scope) |
| Nexus Receiver FW  | —       | —          | Phase 4 (out of scope) |
| Console backends   | —       | —          | gated (vendor license) |

## Test count

**597 unit tests**, all green. `assembleDebug` green.
`lintDebug` has a known issue (Bug #17: Compose Compiler
1.5.15 + Kotlin 2.0.21 detector stack). The lint gate
lands in Phase 2+ when the Compose Compiler upgrade is
complete.

## What this is NOT

- We do not claim Play Store readiness. The project is a
  personal / creative build, not a commercial one.
- We do not impersonate commercial controllers. The descriptor
  is ours; the brand is ours; the VID/PID are ours.
- We do not use Accessibility Service as a backdoor. We use
  Accessibility for accessibility.

## Repository layout

```
elysium-nexus-controller/
├── apps/
│   ├── android/                # the APK (this is where we ship first)
│   ├── macos-agent/            # Swift daemon for Mac (functional)
│   ├── windows-agent/          # Phase 3
│   ├── linux-agent/            # Phase 3
│   └── profile-studio/         # editor host (later)
├── firmware/                   # Nexus Receiver (Phase 4)
├── crates/                     # shared Rust core
├── platform/                   # backend adapters per host
├── schemas/                    # versioned JSON / proto schemas
├── databases/                  # mappings, devices, games, compatibility
├── tools/                      # importers, profilers, testers
├── docs/                       # architecture, ADRs, changelogs
└── .github/workflows/          # CI
```

## Hard rules (no exceptions)

1. **No impersonation of commercial devices.** We ship our own
   descriptor under our own VID/PID. We never present a fake
   Xbox- or PlayStation-branded identity.
2. **Licensed console backends are gated.** Direct PS4/5,
   Xbox One/Series, Switch/2 backends compile only when a
   vendor license is present.
3. **No Accessibility abuse for gamepad injection.** Accessibility
   Service is not a substitute for a real system-level gamepad.
4. **Disconnection must neutralize everything.** Test #38 is a
   release blocker.
5. **No silent claims.** Compatibility states are exactly:
   `VERIFIED_LAB`, `VERIFIED_COMMUNITY`, `PARTIALLY_VERIFIED`,
   `UNVERIFIED`, `REGRESSION`, `BLOCKED`.
6. **No GlobalScope in Kotlin.** Structured concurrency tied to
   lifecycle or service scope.
7. **No device-hardcoding.** The Honor Magic V2 is the *lab*
   device, not the *target*. Every capability comes from
   `WindowManager` + `InputDevice` + sensor introspection.

## Changelogs

All iteration logs live in `docs/changelogs/`:

| Phase | Description | Status |
|-------|-------------|--------|
| 0.1 | Gradle 9.3.1, AGP 8.7.3, Kotlin 2.0.21, JUnit | ✓ |
| 0.2 | Canonical input model (23 buttons) | ✓ |
| 0.3 | Stick filter pipeline (5 curves) | ✓ |
| 0.4 | Canonical input engine + §32 state machine | ✓ |
| 0.5 | Trigger + touch pipeline | ✓ |
| 0.6 | §38 disconnect test (release blocker) | ✓ |
| 0.7 | First MainActivity end-to-end | ✓ |
| 0.8 | §30 latency harness (p50 0.05ms) | ✓ |
| 0.9 | Generic HID descriptor + compatibility DB | ✓ |
| 1.0 | Room + Compose UI | ✓ |
| 1.1–1.12 | Profile editor, gestures, motion, haptics, transports | ✓ |
| 1.13–1.15 | TransportBinding + LocalEchoTransport + engine→transport | ✓ |
| 1.16–1.24 | Settings, share, import, CI, duplicate, rename | ✓ |
| ULT.0 | Fabric foundation (canonical model, IR POC, identity, automation) | ✓ |
| ULT.3 | Hierarchical navigation + Mac/PC trackpad + 30+ TV brands | ✓ |
| ULT.5 | Universal Bluetooth HID (combo descriptor: keyboard+mouse+consumer) | ✓ |
| ULT.6 | Foldable posture infrastructure (PostureAdaptiveLayout) | ✓ |
| ULT.7 | Media keys (Mac+BT) + Air mouse via IMU + HID retroactive tests | ✓ |
| ULT.8 | Quick Connect (one-tap reconnect from Hub) | ✓ |

## Contributing

Read [`AGENTS.md`](AGENTS.md) first. It is the project's
operating contract: hard rules, working contract with the
maintainer, iteration loop, build commands, source-of-truth
ordering.

## License

The codebase is the property of the project owner. The
project is not open source at this time; permission to
mirror or fork is not granted by default.
