# Changelog — Elysium Nexus IR Data Fabric v0.3.0 Production Candidate

**Date**: 2026-08-06  
**Target Version**: `v0.3.0-ir-fabric-rc1` (`versionCode = 2`)  
**Base Commit**: `5789174c1e021260e5d085d642e638e29fab7489`  
**Lab Device**: Honor Magic V2 (Android 14)  

---

## 1. Executive Summary & Defect Elimination Report

This iteration executes the **Master Order for IR Data Fabric v0.3.0 Production Candidate**. It eliminates all 8 critical P0 defects identified during the v0.2.0 audit and establishes an immutable, fail-closed supply chain and local profile persistence engine.

| Defect ID | Description | Root Cause | Resolution in v0.3.0 | Status |
|-----------|-------------|------------|-----------------------|--------|
| **P0-1** | Winning code set lost when opening remote screen | `TvControlScreen` took `DeviceTemplate` and re-encoded static codes | Introduced `InstalledIrProfile` persistent domain model. `TvControlScreen` resolves physical signals strictly from profile bindings. | **RESOLVED** |
| **P0-2** | Race condition between `DeviceCatalog` fallback & SQLite candidates | Initial state loaded static fallback before coroutine populated candidates | Created `ProbeUiState` state machine (`LoadingCatalog`, `Ready`, `Exhausted`). UI disabled until SQLite query resolves. | **RESOLVED** |
| **P0-3** | Confirmation chip enabled on failed transmissions | `TestStep` statically set `active = true` regardless of `IrTransmitResult` | "Sí, subió el volumen" chip active ONLY when `lastResult is IrTransmitResult.Success`. | **RESOLVED** |
| **P0-4** | Silence protocol resolution fallbacks | `proto.startsWith("NEC")` matched `NECx` prematurely + fallback to NEC for unknown | Created `IrProtocol.resolveProtocol()` with strict matching order and fail-closed `ProtocolResolution.Unsupported`. | **RESOLVED** |
| **P0-5** | Invalid RC5 Manchester encoding | `encodeRc5` emitted identical mark/space durations ignoring bit values | Implemented biphasic Manchester bit encoding with adjacent phase coalescing and start-bit alignment. | **RESOLVED** |
| **P0-6** | Non-cryptographic signal fingerprinting | `fingerprintSignal()` used non-portable `patternUs.contentHashCode()` | Implemented canonical SHA-256 physical signal fingerprinting (`RAW:v1` & `ENCODED:v1`). | **RESOLVED** |
| **P0-7** | Mock/placeholder commit SHAs in supply chain | `sources.lock.json` contained fake hashes | Rewrote `lock_sources.py` & `verify_source_locks.py` extracting real 40-hex SHAs, trees, and license hashes. | **RESOLVED** |
| **P0-8** | Fake `OnOff(true)` action translations | `DefaultActionTranslator` mapped navigation/channel actions to `OnOff(isOn=true)` | Removed fake translations; returns `null` for unsupported generic action conversions. | **RESOLVED** |

---

## 2. Supply Chain & Source Locking Metrics

All 5 IR repositories are locked to exact immutable commit SHAs in `sources.lock.json`:

* **`flipper-irdb`**: Commit `d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d` (CC0-1.0)
* **`smartir`**: Commit `e4df2957ad915536f41ffb39daa96886d7cfe040` (MIT)
* **`probonopd-irdb`**: Commit `11aa5eb3ad9fec9e5c03f170c29c1467733d9f3e` (**GATED — Excluded from production asset pack**)
* **`radioxoma-infrared`**: Commit `96179666ea236e33dc9ca9350d92c0ae69eec956` (CC0-1.0)
* **`harctoolbox-irp-protocols`**: Commit `8636d20a5036c542a54fa815ee45415537011d45` (GPL-2.0-or-later)

Verification script `python3 tools/ir-data/verify_source_locks.py` executes in fail-closed mode without `|| true` suppressions.

---

## 3. Production Asset Metrics (`ir_catalog.db`)

* **Database File**: `apps/android/app/src/main/assets/ir/ir_catalog.db`
* **Size**: **26.62 MB** (down from 162.79 MB raw SQLite, 83.6% reduction)
* **SHA-256 Checksum**: `7f06b0ec0e8b6a2aa0934930fe62edcb1ab52c213e42b6766cf24917ee515e44`
* **Brands**: 915
* **Device Types**: 37
* **Remotes / Code Sets**: 2,394
* **Total Encoded Commands**: 27,852
* **Total Raw Commands**: 80,829
* **Total Verified Commands**: 108,681
* **SQLite Integrity**: PASSED (`QuickCheck=ok`, `FKErrors=0`)

---

## 4. Test & Build Gate Results

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

* **JVM Unit Tests**: **PASSED** (includes `InstalledIrProfileRepositoryTest` and `IrProtocolCodecTest`)
* **Lint Check**: **PASSED** (0 errors)
* **Android Build**: `app-debug.apk` compiled and installed successfully on Honor Magic V2 via ADB Streamed Install.
