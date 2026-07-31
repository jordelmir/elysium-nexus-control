# Elysium Nexus Universal Controller

> A platform that turns any Android phone (initial lab device:
> **Honor Magic V2**) into a **universal, professional, dynamic
> control surface** for desktop, mobile, console, and IoT hosts.

```
┌──────────────────────────────────────────────┐
│  Magic V2 touch surface                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Stick L  │  │ Buttons  │  │ Stick R  │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ LT  RT   │  │ D-pad    │  │ Touchpad │   │
│  └──────────┘  └──────────┘  └──────────┘   │
└──────────────────────────────────────────────┘
        │                                ▲
        │ Bluetooth HID                  │ Generic HID
        │ (BASIC_GAMEPAD_V1, VID/PID)    │ (over the air)
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

## Quick start

```bash
# Build + test
cd apps/android-controller
./gradlew :app:testDebugUnitTest :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug

# Run the hid-descriptor-validator (a §18 self-check)
./gradlew :app:runValidator
```

## Repository layout

```
elysium-nexus-controller/
├── apps/
│   ├── android-controller/      # the APK (this is where we ship first)
│   ├── macos-agent/             # Phase 3
│   ├── windows-agent/           # Phase 3
│   ├── linux-agent/             # Phase 3
│   └── profile-studio/          # editor host (later)
├── firmware/                    # Nexus Receiver (Phase 4)
├── crates/                      # shared Rust core
├── platform/                    # backend adapters per host
├── schemas/                     # versioned JSON / proto schemas
├── databases/                   # mappings, devices, games, compatibility
├── tools/                       # importers, profilers, testers
├── docs/                        # architecture, ADRs, changelogs
└── .github/workflows/           # CI
```

The whole order lives in `docs/architecture/MASTER_ORDER.md`
(§0–§46). Every architectural decision references one or more
of its sections.

## Hard rules (no exceptions)

These mirror `MASTER_ORDER.md` §2, §3, §4, §6, §30, §35, §38,
§41, §45. They are not negotiable.

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
7. **No device-hardcoding.** The Magic V2 is the *lab* device,
   not the *target*. Every capability comes from `WindowManager`
   + `InputDevice` + sensor introspection.

## Status

The project is in active Phase 1 (Android-first). See
`docs/changelogs/PHASE_<N>_*.md` for the iteration log.

| Phase | Description                                  | Status |
|-------|----------------------------------------------|--------|
| 0.1   | Gradle 9.3.1, AGP 8.7.3, Kotlin 2.0.21, JUnit | ✓      |
| 0.2   | Canonical input model (23 buttons)            | ✓      |
| 0.3   | Stick filter pipeline (5 curves)              | ✓      |
| 0.4   | Canonical input engine + §32 state machine    | ✓      |
| 0.5   | Trigger + touch pipeline                      | ✓      |
| 0.6   | §38 disconnect test (release blocker)         | ✓      |
| 0.7   | First MainActivity end-to-end                 | ✓      |
| 0.8   | §30 latency harness (p50 0.05ms)              | ✓      |
| 0.9   | Generic HID descriptor + compatibility DB     | ✓      |
| 1.0   | Room + Compose UI                             | ✓      |
| 1.1   | Profile data model + editor canvas            | ✓      |
| 1.2   | Profile Room persistence + editor toolbar     | ✓      |
| 1.3   | Editor gestures + touch arbitration fix        | ✓      |
| 1.4   | Motion / IMU + opacity slider + profile JSON  | ✓      |
| 1.5   | New / delete profile + foldable posture       | ✓      |
| 1.6   | Haptics + transport interface + signature     | ✓      |
| 1.7   | Editor align / distribute / hitBounds         | ✓      |
| 1.8   | Posture-aware main screen                      | ✓      |
| 1.9   | Keystore-backed profile signer                 | ✓      |
| 1.10  | USB + Elysium Link transport skeletons         | ✓      |
| 1.11  | Bluetooth HID transport skeleton               | ✓      |
| 1.12  | Editor alignment UI chips                      | ✓      |
| 1.13  | TransportBinding + LocalEchoTransport          | ✓      |
| 1.14  | Engine → transport end-to-end test (§45)       | ✓      |
| 1.15  | On-device verification                         | ✓      |
| 1.16  | TransportSelector visible in MainScreen        | ✓      |
| 1.17  | Profile share intent (export via share sheet)  | ✓      |
| 1.18  | Settings UI (sensitivity, axis, haptics)      | ✓      |
| 1.19  | GitHub Actions CI workflow                     | ✓      |

**Test count**: 410 unit tests, all green, `assembleDebug` green.

## Build matrix

| Component          | Min API | Target API | Status               |
|--------------------|---------|------------|----------------------|
| Android app        | 26      | 34         | green                |
| Desktop agents     | —       | —          | Phase 3 (out of scope) |
| Nexus Receiver FW  | —       | —          | Phase 4 (out of scope) |
| Console backends   | —       | —          | gated (§21–24)       |

## What this is NOT

- We do not claim Play Store readiness. The project is a personal /
  creative build, not a commercial one. No size pressure, no
  packaging pressure, no min-SDK dance for store compliance.
- We do not impersonate commercial controllers. The descriptor
  is ours; the brand is ours; the VID/PID are ours.
- We do not use Accessibility Service as a backdoor. We use
  Accessibility for accessibility.

## Contributing

Read [`AGENTS.md`](AGENTS.md) first. It is the project's
operating contract: hard rules, working contract with the
maintainer, iteration loop, build commands, source-of-truth
ordering. Every change references a section of
[`MASTER_ORDER.md`](docs/architecture/MASTER_ORDER.md); every
phase has a changelog under `docs/changelogs/PHASE_<N>_*.md`.

## License

The codebase is the property of the project owner. The
project is not open source at this time; permission to
mirror or fork is not granted by default.
