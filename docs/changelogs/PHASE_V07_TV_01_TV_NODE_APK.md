# PHASE V07-TV-01 — TV NODE APK (Software-Only TV Fabric)

> Iteration of phase V07 — RETAIL TRUTH OS. What shipped: the first
> installable slice of the TV Node (`apps/android-tv-node/`), the TV side
> of the Software-Only TV Fabric directive ("crea la app de tv").
> Date: 2026-08-15. Maturity: `IMPLEMENTED` (code written; unit tests
> written but NOT yet executed — verification batch pending Jor's order,
> per the verify-on-request rule).

## What shipped

New standalone Gradle project `apps/android-tv-node/` (mirrors `apps/android`
conventions; wrapper Gradle 9.3.1, AGP 8.7.3, Kotlin 2.0.21 — the exact
toolchain that passed the controller's full batch):

- **Canonical contract twin** — `canonical/TvCanonical.kt`: faithful twin of the
  controller's `com.elysium.nexus.fabric.canonical` wire contract, pinned to the
  same names so serialized payloads stay wire-compatible both directions:
  `DeviceId` (value class), `Protocol`, `Direction`, `ActionRisk`,
  `Capability` (25 entries with `defaultRisk`), `ClimateMode`, the sealed
  `UniversalAction` hierarchy (24 subtypes, `requiredCapability()` mapping) and
  the honest `ActionResult` taxonomy
  (`Success` / `ExecutedUnverified` / `Unsupported` / `Refused` / `Failed`).
  Documented as a twin in a changelog note until a shared Kotlin module
  unifies both copies (no silent divergence).
- **Access taxonomy** — `access/TvAccessLevel.kt`: the 4 levels
  (`STANDARD`, `ENHANCED_USER_GRANTED`, `BLUETOOTH_HID`, `ENGINEERING_ADB`)
  and a dynamic `CapabilityManifest` built from real facts (volume always
  observable; mute only when not fixed; global TV keys only API 33+; IME
  only when enhanced granted).
- **Identity provider** — `identity/NexusTvIdentityProvider.kt`: reports
  metadata join keys only (`manufacturer:model:device`, API level, platform,
  leanback, HDMI-CEC, volume-fixed, key-filter capability, manager); never a
  physical unique identity.
- **Observation engine + honest interpreter** — `observe/TvObservationEngine.kt`:
  pure `TvObservationEngine` (observations), `TvEffector` seam (the only place
  key/volume effects are issued), `VolumeActionInterpreter` — the evidence
  ladder: Confirmed ONLY when a real delta (up/down direction or mute toggle)
  is observed on the TV; Unverified otherwise; Unsupported on fixed-volume
  TVs; Refused below ENHANCED access. `TvActionExecutor` gates all execution
  through it. POWER/INPUT deliberately out of scope here (user-confirmation
  gate at pairing/phone layer).
- **Real Android glue** — `observe/AndroidTvObserver.kt` (AudioManager
  volume/effector), `accessibility/NexusAccessibilityService.kt`
  (observes key events without consuming; global HOME/BACK + DPAD only when
  granted, DPAD default-off), `ime/NexusTvIme.kt` (transport-only IME,
  secure-field flags, commit/delete/enter), `media/NotificationMediaObserver.kt`
  (media sessions only through user-granted notification access),
  `application/TvNodeApp.kt` (grants surface), `PairingActivity.kt`
  (leanback home showing identity facts).
- **Manifest/resources** — leanback launcher, AccessibilityService config
  (`canRequestFilterKeyEvents`, no window-content retrieval), IME config,
  notification listener; NO INTERNET permission; strings/es+en bilingual
  convention kept.
- **Unit tests (written, not yet run)** — `TvNodeCoreTest.kt` (11 tests:
  interpreter before/after ladders, executor gates, effector-failure honesty,
  no-observation honesty) and `TvNodeCanonicalTest.kt` (wire-twin coverage of
  all 24 actions, capability risk mapping, manifest degradation with API
  level, fixed-volume mute drop).

## Verification status

Per the working contract (verify-on-request): code+tests written, NOT compiled
or executed yet. The full gate
(`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`) runs
when Jor orders it ("haz las pruebas"). Expected: green once trivial
build-config kinks are surfaced.

## Verified facts / notes

- No hardware purchased (directive: deferred until 3→5→10 TVs validated).
- ADB remains engineering-only; the node advertises no IR emission of its own
  yet — the oracle is the next slice (snapshot → candidate → reversal), to be
  fed by the phone's IR catalog over the pairing channel.
- Twin-copy note recorded in `TvCanonical.kt` header: unifying both packages
  in a shared Kotlin module is a documented refactor, not a silent drift.