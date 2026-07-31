# Architecture

> The architecture is the project. The code is the architecture,
> as it is at this moment. This document explains *why* the code
> is shaped the way it is; the code is the final word on *what*
> it is.

## Bird's-eye view

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android controller (APK)                      │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Editor UI  │  │ Touch surface│  │   Sensors    │          │
│  │   (Compose)  │  │  (View)      │  │ (IMU, hinge) │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                    │
│         ▼                 ▼                 ▼                    │
│  ┌──────────────────────────────────────────────────┐          │
│  │   EditorActions (pure)                           │          │
│  │   addControl / moveControl / resizeControl / …   │          │
│  └──────────────────────┬───────────────────────────┘          │
│                         │                                        │
│                         ▼                                        │
│  ┌──────────────────────────────────────────────────┐          │
│  │   Profile + ProfileRepository (Room)             │          │
│  │   per-control entity, draw order via ordering    │          │
│  └──────────────────────┬───────────────────────────┘          │
│                         │                                        │
│                         ▼                                        │
│  ┌──────────────────────────────────────────────────┐          │
│  │   Canonical Input Engine (§32 state machine)     │          │
│  │   Idle → Discovering → Pairing → … → Active      │          │
│  │   stick filter pipeline / trigger filters / …   │          │
│  └──────────────────────┬───────────────────────────┘          │
│                         │                                        │
│                         ▼                                        │
│  ┌──────────────────────────────────────────────────┐          │
│  │   TransportBinding + ControllerTransport         │          │
│  │   LocalEcho / BluetoothHID / USB / ElysiumLink   │          │
│  └──────────────────────┬───────────────────────────┘          │
│                         │                                        │
└─────────────────────────┼────────────────────────────────────────┘
                          │
                          ▼
              ┌──────────────────────┐
              │   Host (PC, console) │
              └──────────────────────┘
```

The engine is the centre of gravity. The editor and the
touch surface feed it; the transport binding drains it. The
engine itself is pure data (no Android imports); the
surrounding pieces are Android adapters.

## Module map

### `core.engine` — the canonical input engine

The engine is the project's heart. It is a `data class`
tree (`UniversalControllerState`) plus a state machine
(`EngineState`, 10 states) and a hot `StateFlow`. The
state machine's legal forward path is documented in
`MASTER_ORDER.md` §32; illegal transitions are
rejected (a `TransitionResult` is returned, never an
exception). The engine neutralizes on every transition
out of `Active` (§38 release blocker).

Key types:

- `CanonicalInputEngine` — the engine itself.
- `UniversalControllerState` — the canonical state.
  23 buttons, 2 sticks, 2 triggers, d-pad, touch,
  motion, sequence, timestamp.
- `EngineState` — the §32 state machine. 10 states,
  9 legal forward transitions.
- `StickSide` — `Left` / `Right` (the side discriminates
  the destination field for sticks + triggers).
- `TransportBinding` — the bridge between the engine and
  the transport. The engine is pure data; the transport
  is independent; the binding is the bridge.

### `core.filter` — the stick filter pipeline

Per `MASTER_ORDER.md` §12, every stick goes through a
filter pipeline:

1. Deadzone (`innerDeadzone`)
2. Anti-deadzone
3. Outer threshold
4. Response curve (`Linear`, `Exponential`, `SCurve`,
   `CubicBlend`, `CustomCubic`)
5. Sensitivity multiplier
6. Axis inversion (X / Y)
7. Snap-to-cardinal
8. Saturation
9. Reduced range (precision mode)

`StickConfig` is the immutable configuration; `StickFilters`
are pure functions. The pipeline is testable in isolation.

### `core.model` — the canonical model

The 23 canonical buttons (`South`, `East`, `West`, `North`,
`LeftBumper`, `RightBumper`, `LeftTriggerDigital`, `RightTriggerDigital`,
`LeftStickClick`, `RightStickClick`, `MenuPrimary`, `MenuSecondary`,
`System`, `Touchpad`, `Capture`, `Paddle1`–`Paddle4`, `Auxiliary1`–`Auxiliary4`).
The order is stable; `ButtonSet` (a 64-bit bitset) relies
on `ordinal`. **Do not reorder.**

### `core.profile` — the §15 profile document

A profile is a list of `ControlElement`s plus metadata.
Each element has a `ControlType` (Button, Stick, Trigger,
Dpad, Touchpad), a visual bounds, a hit bounds, a zIndex,
a rotation, an opacity, and a `CanonicalBinding` (what
the element does in the canonical world).

- `ProfileJson` — hand-written serialiser (§15 exchange format).
- `ProfileSignature` — HMAC-SHA256 sign / verify.
- `KeystoreProfileSigner` — AndroidKeyStore-backed signer.
- `ProfileShareBuilder` + `AndroidProfileShareLauncher` —
  the §15 export story (Phase 1.17).

### `core.transport` — the §17 transport multiplexer

A transport is the destination of the engine's emissions.
The interface is `ControllerTransport`; the implementations
are `LocalEchoTransport` (the test-friendly default),
`BluetoothHidTransport` (Phase 1.11's skeleton, uses the
§18 descriptor + `HidReportEncoder`), `UsbAccessoryTransport`
(Phase 1.10's skeleton), and `LocalNetworkElysiumLinkTransport`
(Phase 1.10's TCP skeleton on port 7777).

### `core.haptics` — the §27 haptics

Sealed-class event API (9 events: `ButtonTap`,
`ButtonLongPress`, `StickEdge`, `TriggerClick`, `Error`,
`TransportConnected`, `TransportDisconnected`,
`ProfileChanged`, `Recentered`). The Android adapter
maps each event to a `VibrationEffect`. `SettingsAwareHaptics`
(Phase 1.18) wraps the adapter to respect the
`hapticsEnabled` setting.

### `core.motion` — the §14 motion / IMU source

`MotionSensorSource` is a `Flow<MotionSample>`. The Android
adapter is `AndroidMotionSensorSource` (SensorManager). The
engine consumes the samples via `submitMotion`.

### `core.posture` — the §16 foldable posture

`PostureObserver` is a `Flow<Posture>`. The Android adapter
is `AndroidPostureObserver` (Jetpack WindowManager). The
editor's posture-aware layout switches between single-pane
(Open / Flat / Unknown), tabletop (HalfOpened), and compact
(Closed cover screen).

### `core.settings` — the §15 app settings

`AppSettings` is the user-tunable document (stick sensitivity,
axis inversion, haptics, dark theme). The store is
`AppSettingsStore`; the implementations are
`InMemoryAppSettingsStore` (test) and
`AndroidAppSettingsStore` (SharedPreferences).

### `databases.profile` — the profile Room database

`ProfileEntity` + `ProfileControlEntity` (one row per
control; ADR-0002 chose the two-table schema over a JSON
blob). `ProfileDao` exposes the queries. `RoomProfileRepository`
adapts the DAO to the `ProfileRepository` contract used
by the engine / editor.

### `databases.compatibility` — the §33 compatibility DB

A small Room database for the "this host works with this
controller" matrix. The status enum is
`CompatibilityStatus` (6 values).

### `ui` — the editor + activity

`MainActivity` is the host. It owns the engine, the
profile repository, the settings store, the transport
binding, and the activity scope. The Compose tree
(`MainScreen` → `PostureAwareMainScreen` → `MainScreen` /
`TabletopMainScreen` / `CompactMainScreen`) is the editor
+ touch surface + diagnostic overlay.

`MainScreen` hosts the `ProfileSelector` (chip row of
every profile in the DB), the `TransportSelector` (chip
row of every transport), the `EditorToolbar` (3 rows:
actions, alignment, opacity), the `EditorCanvas` (the
user's profile rendered as draggable / scalable /
rotatable controls), the `TouchSurfaceViewHost` (the
touch surface via `AndroidView`, the Bug #18 fix), and
the §38 Neutralize button. The `SettingsDialog` (Phase 1.18)
is a modal `AlertDialog` hosted over the editor.

## Source-of-truth ordering

When files disagree:

1. `docs/architecture/MASTER_ORDER.md` wins on intent.
2. The latest `docs/changelogs/PHASE_<N>_*.md` wins on current state.
3. `docs/adr/ADR-*.md` explains why a given decision was taken.
4. Code is the final word on what *is* shipped.

## Iteration loop

```
forever:
    read latest docs/changelogs/PHASE_<N>_*.md     # what we just shipped
    read docs/architecture/MASTER_ORDER.md §45     # where we're going
    read relevant docs/adr/ADR-*.md               # decisions already taken
    pick the smallest concrete sub-task that
        - is well-scoped (≤1 focused session)
        - has an obvious first commit
        - layers cleanly on existing code
        - unblocks the most downstream
    implement end-to-end:
        - code
        - unit tests
        - ./gradlew :app:testDebugUnitTest      # green
        - ./gradlew :app:assembleDebug         # green
        - docs/changelogs/PHASE_<NEW>_<NAME>.md
        - update this file or AGENTS.md if reusable
    next iteration
```

The loop halts only when:

1. A test fails in a way that needs an architectural decision
   the maintainer must sign.
2. Hardware the maintainer must touch (e.g. flashing the
   Nexus Receiver dev board for the first time).
3. A vendor license / SDK needs to be obtained (gate the
   build, write a `docs/licensing/STATUS.md`, continue
   with non-gated subsystems).

## What the project explicitly defers

- **Desktop agents** (`apps/macos-agent/`, `windows-agent/`,
  `linux-agent/`) are Phase 3. They will be a separate
  module each, with the Elysium Link protocol on the
  wire.
- **Nexus Receiver firmware** (`firmware/`) is Phase 4.
  The hardware is the Elysium Link endpoint; the
  firmware is BLE + Wi-Fi + USB.
- **Console backends** (PS4/5, Xbox One/Series, Switch/2)
  are gated behind vendor licensing. Without a license,
  the build is `REQUIRES_VENDOR_LICENSE` and the
  runtime surface is empty.
- **Light theme** is persisted as a `darkTheme` flag in
  `AppSettings` but the activity's theme is always
  `Theme.ElysiumNexus` (dark). The light theme is a
  follow-up.
- **Lint** is currently disabled (Bug #17: the Compose
  Compiler 1.5.15 + Kotlin 2.0.21 detector stack is
  broken with `MutableCollectionMutableStateDetector`).
  The gate lands in Phase 2+ when the Compose Compiler
  upgrade is complete.
