# Phase 1.22 — 1.24 — Import, settings wiring, duplicate, rename

> Three iterations that close the §15 editor
> surface: import from JSON, settings → engine
> wiring, and the missing duplicate / rename
> operations. Each is a small, end-to-end
> deliverable; the changelog groups them because
> the dependency graph is a chain and the diffs
> are intertwined.

## §1 — Phase 1.22 — Profile import

The §15 import path. The user pastes a profile's
JSON into a dialog; the importer validates,
parses, and (on success) upserts with a fresh
`id`. The symmetric counterpart to the Phase 1.17
share intent.

### Files

- `core/profile/ProfileImporter.kt` — pure,
  JVM-testeable. Wraps `ProfileJson.fromJson` and
  the `Profile.init` validation as a typed
  `ProfileImportResult` envelope (Success or
  Failure with reason + cause).
- `ui/editor/ProfileImportDialog.kt` — modal
  `AlertDialog` with a multiline `OutlinedTextField`.
  On failure, the dialog stays open with the
  reason inline; on success, the dialog dismisses
  and the activity has already upserted.
- `ui/editor/EditorToolbar.kt` — new "Import" chip
  in row 1.
- 10 new `ProfileImporterTest` cases.

### Decisions

- ADR-0038: typed envelope, not `Result<Throwable>`.
  The agent-memory rule applies: a `Failure(reason,
  cause)` is a stronger contract than
  `Result.failure(e)`.
- ADR-0039: importer does not assign an `id`. The
  repository is the id owner; the activity calls
  `repo.nextId()` and passes the result as the
  new id. The importer's responsibility ends at
  "validated profile".
- ADR-0040: importer stamps `createdAt` and
  `updatedAt` with the import time. The source's
  timestamps are the source's local clock; the
  receiver's "this profile was added" should be
  the receiver's local clock.

## §2 — Phase 1.23 — Engine reactive StickConfig

The §15 settings → engine wiring. The engine's
per-side `StickConfig` is now mutable, guarded by
a `ReentrantReadWriteLock`. The activity's
`onSettingsChange` builds a fresh `StickConfig`
per side (sensitivity + axis inversion) and calls
`engine.updateStickConfig`. The next
`engine.submitStick` uses the new config.

### Files

- `core/engine/CanonicalInputEngine.kt`:
  - `leftStickConfig` / `rightStickConfig`
    changed from `val` to `var`.
  - New `stickConfigLock` (`ReentrantReadWriteLock`):
    reads on every `submitStick`; writes are rare
    (a settings change).
  - New `currentStickConfig(side)`: hot read.
  - New `updateStickConfig(side, config)`: the
    `StickConfig.init` block validates; an
    out-of-range config throws
    `IllegalArgumentException` before the engine's
    state changes.
- 7 new `EngineStickConfigTest` cases.

### Decisions

- ADR-0041: `ReentrantReadWriteLock`, not
  `synchronized`. The filter pipeline reads on
  every `submitStick` (high frequency); a settings
  change writes once (low frequency). A read-write
  lock is the right shape: many concurrent reads
  + rare writes.
- ADR-0042: settings `sensitivity` and `invertX/Y`
  are the only knobs the user dials in the
  settings dialog. The other 8 `StickConfig` fields
  (deadzone, response curve, snap, …) are out of
  scope for the settings; they keep their defaults
  when the activity rebuilds a fresh
  `StickConfig`. The full per-knob editor is a
  follow-up; the settings surface is the "I own a
  Magic V2" minimal set.

## §3 — Phase 1.24 — Profile duplicate + rename

The two missing §15 operations: produce a fresh
copy of the current profile (new id, new
timestamps, "name (copy)" suffix); rename the
current profile.

### Files

- `core/profile/ProfileActions.kt` — pure,
  JVM-testeable. Two functions:
  - `duplicate(source, newId, now) → Profile`.
  - `rename(source, newName, now) → Profile`.
- `ui/editor/ProfileRenameDialog.kt` — single-line
  `OutlinedTextField` pre-filled with the
  current name. "Rename" disabled while blank or
  unchanged.
- `ui/editor/EditorToolbar.kt` — new "Duplicate"
  and "Rename" chips in row 1.
- 7 new `ProfileActionsTest` cases.

### Decisions

- ADR-0043: control ids are *not* shared between
  source and duplicate. A duplicate's control ids
  are shifted by `source.id * 1000`, so the
  duplicate's ids live in a different "namespace"
  from the source's. Editing the duplicate does
  not touch the source.
- ADR-0044: rename validation is delegated to
  `Profile.init`. A blank new name throws
  `IllegalArgumentException` from the data
  class's `init`; the activity catches and
  logs (no UI dialog for a blank-name error).

## §4 — Test count and build status

| Metric                  | Before  | After  |
|-------------------------|---------|--------|
| Unit tests              | 410     | **434** |
| JVM tests passing       | 410     | 434    |
| `assembleDebug`         | green   | green  |
| `lintDebug`             | broken  | broken (Bug #17, deferred) |

## §5 — Project status (as of Phase 1.24)

The Android module is feature-complete for the
§15 surface (editor + import + export + duplicate
+ rename + settings + transport + haptics +
motion + posture). The test count is 434 JVM
unit tests, all green, `assembleDebug` green.

### Open work

- **Phase 2+** — Real Bluetooth HID transport
  (requires hardware). The Phase 1.11 skeleton
  (`BluetoothHidTransport`) is ready; the
  `BluetoothAdapter` permissions and the actual
  `BluetoothHidDevice` registration are out of
  scope for the lab emulator.
- **Phase 2+** — Compose Compiler upgrade
  (closes Bugs #17 and #19). KSP does not yet
  have a release for Kotlin 2.2.x; the upgrade
  is blocked on a KSP release.
- **Phase 3** — Desktop agents
  (`apps/macos-agent/`, `windows-agent/`,
  `linux-agent/`). Out of scope for the Android
  module.
- **Phase 4** — Nexus Receiver firmware
  (`firmware/`). Out of scope; requires hardware.
- **Phase 2+** — Vendor-licensed console
  backends (PS4/5, Xbox One/Series, Switch/2).
  Gated behind vendor licensing; without a
  license the build is `REQUIRES_VENDOR_LICENSE`.

### Deferred polish

- **Settings → trigger / motion / haptics
  config**: the §15 settings surface is the 8
  fields the user dials today. Per-control
  trigger settings (hair-trigger, curve),
  per-control motion settings (yaw/pitch/roll
  blend), and per-event haptic strengths are
  follow-ups; the engine is ready to receive
  them via the same `updateXxx` pattern.
- **Profile signature verification on import**:
  Phase 1.6 ships `ProfileSignature`
  (HMAC-SHA256) and `KeystoreProfileSigner`;
  the JSON format does not yet include a
  signature field. The importer does not
  verify; an unsigned import is accepted.
  Adding the signature is a single field in
  `ProfileJson` plus a `KeystoreProfileSigner.verify`
  call in `ProfileImporter.import`.
- **Light theme**: `AppSettings.darkTheme` is
  persisted; the activity's theme is always
  `Theme.ElysiumNexus` (dark). The flag is the
  forward-compatible seam.
- **Profile history / undo**: the §15 "history"
  milestone is a per-session undo stack; the
  editor's "Reset" is a placeholder for the
  same-profile reset. The full milestone lands
  with the profile version migrations
  (Phase 2+).
