# Phase 1.16 — 1.21 — Share intent, settings UI, CI, docs

> Six iterations that close Phase 1: the share intent
> (§15 export), the settings UI (§15), the first CI
> workflow, and the project's top-level docs. Each
> iteration is a small, testable, end-to-end deliverable;
> this changelog groups them because the dependency
> graph is a chain and the diffs are intertwined.

## §1 — Phase 1.16 — TransportSelector visible in MainScreen

The §17 transport selector ships a chip row above the
editor toolbar. The chip row is one-tap; the user
switches transports without leaving the editor. The
default transport is `LocalEchoTransport` (test-friendly,
0ms latency, records every frame); the engine's state
flow forwards to the current transport via
`TransportBinding`.

### Files

- `core/transport/ControllerTransport.kt` — added
  `state: TransportState` (6 states: `IDLE`,
  `INITIALISING`, `PAIRED`, `CONNECTED`, `DISCONNECTED`,
  `ERROR`) and `capabilities: TransportCapabilities`
  (label).
- `ui/editor/TransportSelector.kt` — new chip row.
- `ui/MainScreen.kt` + `PostureAwareMainScreen.kt` —
  `transports`, `currentTransport`,
  `onTransportSelected` parameters added.
- `ui/MainActivity.kt` — instantiates the default
  transport + binding.

### Compile errors fixed

1. `MainActivity.kt:196:60` — `defaultTransport` was
   referenced in `setContent` but declared after. Moved
   the declaration before the `setContent` block.
2. `MainScreen.kt:184:5` / `196:5` — duplicate
   `onTransportSelected` parameter declaration. Removed
   the duplicate.
3. `MainScreen.kt:167:9` / `384:9` — `onProfileUpdated`
   was not wired to the inner `MainScreenContent`. The
   inner's default `{}` no-op'd align / distribute. Added
   the wiring.

## §2 — Phase 1.17 — Profile share intent

The §15 export story. The user taps "Share" in the
editor toolbar; the system share sheet opens with the
current profile as a JSON document. Other apps (e-mail,
drive, Bluetooth, …) can receive the file.

### Files

- `core/profile/ProfileShare.kt` — pure data class
  (filename, mimeType, content). JVM-testeable.
- `core/profile/ProfileShareBuilder.kt` — pure
  builder. `slugOf(name)` produces a portable ASCII
  slug; non-ASCII letters are dropped, runs of
  separators collapse, the result is capped at 32
  characters, an empty slug becomes `untitled`.
- `core/profile/AndroidProfileShareLauncher.kt` —
  Android adapter. Writes the JSON to `cache/shares/`
  and returns an `ACTION_SEND` chooser Intent via
  FileProvider.
- `res/xml/file_paths.xml` — new FileProvider paths
  (`cache-path` for `shares/`).
- `AndroidManifest.xml` — new `<provider>` with
  authority `${applicationId}.fileprovider`.
- `gradle/libs.versions.toml` — added
  `androidx-core-ktx:1.13.1` for `FileProvider`.
- `ui/editor/EditorToolbar.kt` — new "Share" chip
  in row 1.
- `ui/MainScreen.kt` / `PostureAwareMainScreen.kt` /
  `MainActivity.kt` — `onShareProfile` callback
  wired through.
- 12 new `ProfileShareBuilderTest` cases.

### Bug discovered + fixed

The slug function's first cut was wrong on non-ASCII
input. The bug: the ASCII-letter branch appended the
letter regardless of `lastWasDash`, so a non-ASCII
letter followed by an ASCII letter produced a
run of `[drop]-[drop]-[letter]`. The fix: any
non-ASCII letter (or any non-alphanumeric) is a
*separator*; only ASCII letters and digits appear
in the slug.

Test cases covered: pure ASCII, runs of separators,
leading / trailing whitespace, all-non-ASCII name
("untitled" fallback), all binding variants
(Neutralize / Button / Stick / Trigger), full
round-trip through `ProfileJson`.

### Decisions

- ADR-0027: vendor-specific MIME type
  `application/vnd.elysium.profile+json` (a JSON
  subtype; receivers can sniff the structure).
- ADR-0028: cache, not files dir. The OS wipes the
  cache on storage pressure; the user never sees the
  artifact again after the share target is done.
- ADR-0029: `FLAG_GRANT_READ_URI_PERMISSION`. The
  receiver reads; it does not write.
- ADR-0030: FileProvider is the only legal way to
  share a file with another app on API 24+ (raw
  `file://` throws `FileUriExposedException`).

## §3 — Phase 1.18 — Settings UI

The §15 user-tunable document. The settings dialog
is a modal `AlertDialog` with three sections:

- **Sticks** — left / right sensitivity sliders
  (`[0.5, 2.0]`, default `1.0`).
- **Axis inversion** — four switches (left X / Y,
  right X / Y).
- **Haptics** — one switch (default on).

The dialog is "live": every change persists immediately
via `AppSettingsStore.update()`. The `SettingsAwareHaptics`
decorator gates every `HapticEvent` against
`hapticsEnabled`; a settings change with haptics off is
a no-op except for value persistence.

### Files

- `core/settings/AppSettings.kt` — 8-field data class
  with `MIN_SENSITIVITY` / `MAX_SENSITIVITY` companion
  constants. Validated: sensitivity in `[0.5, 2.0]`.
- `core/settings/AppSettingsStore.kt` — interface +
  `InMemoryAppSettingsStore` (test-friendly, StateFlow).
- `core/settings/AndroidAppSettingsStore.kt` —
  SharedPreferences-backed. 8 namespaced keys
  (`settings.*`); load clamps out-of-range floats to
  the sensitivity range.
- `core/haptics/SettingsAwareHaptics.kt` — decorator.
  Reads `settingsFlow.value` on every event; a
  settings change is picked up on the next event.
- `ui/settings/SettingsDialog.kt` — modal `AlertDialog`.
- `ui/editor/EditorToolbar.kt` — new "Settings" chip
  in row 1.
- `ui/MainScreen.kt` / `PostureAwareMainScreen.kt` /
  `MainActivity.kt` — `settings` + `onSettingsChange`
  wired through; `SettingsAwareHaptics(AndroidHaptics(this),
  settingsFlow)` constructed in onCreate.
- 18 new tests (8 `AppSettings` + 6
  `InMemoryAppSettingsStore` + 4
  `SettingsAwareHaptics`).

### Decisions

- ADR-0031: flat schema. Persistence is one key per
  field in SharedPreferences; no JSON serialiser
  needed. The settings are a small bag of values.
- ADR-0032: settings is the *source of truth*. The
  engine's `StickConfig` is currently set at
  construction; a follow-up Phase 1.22+ will make
  the engine's config reactive. The settings are
  already stored; the wiring is a one-line change.
- ADR-0033: `SettingsAwareHaptics` is a decorator, not
  a flag on `AndroidHaptics`. The haptics spec (§27)
  and the settings spec (§15) are independent
  concerns; the decorator is the one-class seam.

## §4 — Phase 1.19 — CI workflow

The first CI workflow. Runs on every push to
`main` / `develop` and every pull request. The
job runs JVM unit tests, builds the debug APK,
and verifies the §18 HID descriptor self-check
(`runValidator`).

### Files

- `.github/workflows/android-ci.yml` — Ubuntu 22.04,
  JDK 17, Android SDK 34, build-tools 34.0.0.
  Caches the Gradle build. Uploads the `app-debug`
  APK as a build artifact.

### Decisions

- ADR-0034: skip lint as a gate. Bug #17 (Compose
  Compiler 1.5.15 + Kotlin 2.0.21 detector stack
  is broken with `MutableCollectionMutableStateDetector`)
  blocks `lintDebug`. The gate lands in Phase 2+
  when the Compose Compiler upgrade is complete.
- ADR-0035: `fetch-depth: 0`. The CI run can attach
  the change-set to a future release note; the full
  history is available.
- ADR-0036: do not cache the Android SDK. The
  cmdline-tools step is small enough to
  re-download on every run; the cache invalidation
  rules for the SDK are brittle.

## §5 — Phase 1.20 — Top-level docs

The project's first top-level docs.

### Files

- `README.md` — project introduction, quick start,
  repo layout, hard rules, phase status table
  (30 phases shipped, 410 tests), build matrix,
  explicit "what this is NOT" section.
- `docs/ARCHITECTURE.md` — bird's-eye view, module
  map (engine, filter, model, profile, transport,
  haptics, motion, posture, settings, databases,
  UI), source-of-truth ordering, iteration loop,
  explicit deferrals.

### Decisions

- ADR-0037: README is a *user-facing* document. The
  ARCHITECTURE is a *design* document. The two are
  not merged: a user does not need to read the
  module map, and a contributor does not need the
  quick-start. AGENTS.md is the *operating contract*
  — three documents, three audiences.

## §6 — Phase 1.21 — On-device verification

The §45 final-mile verification. The new APK is
installed on the `MEET_ATD_API35` emulator; the
activity launches; the engine state machine emits
(seq 5 → 6 → 7 → 8 → 21 during the verification
session); the new "Share" and "Settings" chips
are present in row 1 (verified via uiautomator);
the Settings dialog opens and shows the three
sections ("Sticks", "Axis inversion", "Haptics");
the latency p50 is 1.1ms (well under the §30
4ms budget).

### Verification log

```
$ adb install -r -t app/build/outputs/apk/debug/app-debug.apk
Success

$ adb shell am start -n com.elysium.nexus.controller/com.elysium.nexus.ui.MainActivity
Starting: Intent { cmp=com.elysium.nexus.controller/com.elysium.nexus.ui.MainActivity }

$ adb logcat -d
ElysiumNexus: MainActivity.onCreate — Phase 1.3 editor + AndroidView arbitration
ElysiumNexus: state[seq=5, ts=29767822555, Δt=150ms]: buttons=0, dpad=Center, L=(0.0, 0.0), R=(0.0, 0.0), LT=0.0, RT=0.0, touches=0, motion=false
ElysiumNexus: state[seq=6, ts=134626217980, Δt=104757ms]: buttons=0, dpad=Center, L=(0.0, 0.0), R=(0.0, 0.0), LT=0.0, RT=0.0, touches=1, motion=false
ElysiumNexus: latency[count=2]: p50=1.118125ms, p95=1.629775ms, p99=1.675255ms, max=1.686625ms

# uiautomator dump — toolbar row 1 (after scroll):
text="Reset"
text="New profile"
text="Delete"
text="Share"      ← Phase 1.17
text="Settings"  ← Phase 1.18

# Settings dialog (after tap):
text="Sticks"
text="Left stick sensitivity"
text="Right stick sensitivity"
text="Axis inversion"
text="Invert left X"
text="Invert left Y"
text="Invert right X"
text="Invert right Y"
text="Haptics"
text="Haptic feedback"
text="Reset"
text="Close"
```

## §7 — Test count and build status

| Metric                  | Before  | After  |
|-------------------------|---------|--------|
| Unit tests              | 380     | **410** |
| JVM tests passing       | 380     | 410    |
| `assembleDebug`         | green   | green  |
| `lintDebug`             | broken  | broken (Bug #17, deferred) |
| On-device install       | yes     | yes    |
| Engine emissions        | seq 5+  | seq 21 |
| Latency p50             | 0.05ms  | 1.1ms (emulator) |
| APK size                | 9.2 MB  | 9.4 MB |

## §8 — What this unlocks

Phase 1.22+ picks up the engine-config wiring
(§15 settings → `StickConfig`), the profile import
path (the symmetric story to Phase 1.17's export),
the Compose Compiler upgrade (closes Bugs #17 and
#19), and the §11 transport multiplexer UI.
