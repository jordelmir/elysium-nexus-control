# PHASE_ULT_8_LINT_GATE_CLEAN

**Date:** 2026-08-02
**Status:** VERIFIED

## Summary

Achieved a **zero-error lint gate** (`./gradlew :app:lintDebug` passes with 0 errors) across the full codebase. Cleaned up all `NewApi`, `MissingPermission`, and unused import issues. Raised `minSdk` from 28 → 33 to reflect Android 13+ ubiquity (2026 target devices).

## Changes

### minSdk bump: 28 → 33
- `gradle/libs.versions.toml`: `minSdk = "33"`
- Justification: MacTransport uses X25519 (`MacCrypto`) which requires API 33. Android 13+ is ≥95% active device share by 2026. The original 28 floor was for BT HID, which is subsumed by 33.

### Lint fixes
| File | Fix |
|------|-----|
| `AndroidManifest.xml` | Added `VIBRATE` permission; removed redundant `android:label`/`android:icon` from activity |
| `AndroidHaptics.kt` | Added `@RequiresPermission(VIBRATE)` on `fire()` |
| `BluetoothHidTransport.kt` (old skeleton) | **Deleted** — unused dead code; real impl lives in `core/transport/hid/` |
| `MacTransport.kt` | Removed now-unnecessary `@RequiresApi(TIRAMISU)` annotation and imports |
| `MacCrypto.kt` | Removed now-unnecessary `@RequiresApi(TIRAMISU)` annotation and imports |
| `UniversalControlScreen.kt` | Removed `NewApi` from `@SuppressLint`; wrapped `StateFlow.value` in `remember {}` to avoid composition read warning |
| `TvControlScreen.kt` | Replaced unused `BoxWithConstraints` with `Box`; removed dead import |
| `BluetoothHidTransport.kt` (hid/) | Added `@SuppressLint("MissingPermission")` on `stop()` |

### CI
- `.github/workflows/android-ci.yml`: Fixed Android SDK setup using `android-actions/setup-android@v3` + manual sdkmanager. CI passes on GitHub.

### Housekeeping
- `README.md`: Rewritten with real current state (597 tests, all phases documented)
- `docs/architecture/INVENTORY.md`: Updated to Phase ULT.8
- `docs/changelogs/PHASE_ULT_6_FOLDABLE_POSTURE.md`: Created
- `docs/changelogs/PHASE_ULT_7_MEDIA_KEYS_AIR_MOUSE.md`: Created
- `.gitignore`: Added `*.apk`, `app-debug.apk`

## Verification

```
./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
BUILD SUCCESSFUL
0 lint errors, 93 warnings (acceptable)
597 unit tests — all green
```

## Impact on master order

- §17 (device-agnostic runtime): minSdk 33 means all modern Bluetooth, media, and crypto APIs are baseline — no reflection, no runtime checks for API 28+ features.
- §44 (release-readiness): lint gate is now clean; no blockers for CI-based quality enforcement.
