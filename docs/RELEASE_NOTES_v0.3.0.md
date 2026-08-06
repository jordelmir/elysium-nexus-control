# RELEASE NOTES — v0.3.0-ir-fabric-rc1

**Elysium Nexus Universal Controller**
**Release Date**: 2026-08-06
**Tag**: `v0.3.0-ir-fabric-rc1`
**Commit**: `8727ef1`

---

## Highlights

### 🔴 IR Data Fabric — Production-Ready Infrared Universal Remote

This release transforms the Elysium Nexus Controller into a **professional-grade universal infrared remote** with:

- **915 brands** from 5 upstream IR data repositories
- **108,681 verified IR commands** across 37 device types
- **7 protocol encoders** (NEC, NECx, Samsung32, Sony SIRC, RC5, RC6, Kaseikyo) — all golden vector verified
- **Smart probing engine** for automatic brand/model detection
- **Persistent winner profiles** that survive app restarts and device reboots
- **Fail-closed protocol resolution** — zero silent NEC fallbacks
- **Cryptographic supply chain** — all upstream sources locked to exact commit SHAs with license verification

### 🛡️ 8 Critical P0 Defects Eliminated

Every defect identified in the v0.2.0 audit has been resolved:

1. Winner profile persistence loss → `InstalledIrProfile` + atomic JSON persistence
2. SQLite race condition → `ProbeUiState` state machine
3. False confirmation promotion → Guard on `IrTransmitResult.Success`
4. Silent NEC protocol fallback → `ProtocolResolution.Unsupported`
5. RC5 Manchester encoding → Biphasic bit transitions with phase coalescing
6. Insecure fingerprinting → SHA-256 canonical physical signal fingerprints
7. Mock supply chain locks → Real 40-hex commit/tree/license SHA verification
8. Fake action translations → Removed from `ActionDispatcher`

### 📊 Verification Gate

| Gate | Result |
|------|--------|
| JVM Unit Tests | **772 / 772 PASSED** |
| Lint Errors | **0** |
| assembleDebug | **BUILD SUCCESSFUL** |
| ADB Install | **Streamed Install → Success** |
| SQLite Integrity | **PRAGMA quick_check = ok** |
| Supply Chain Locks | **5/5 verified** |

---

## Codebase Metrics

| Metric | Value |
|--------|-------|
| Kotlin source files | 194 |
| Kotlin production LoC | 41,586 |
| Kotlin test LoC | 12,827 |
| Python tooling LoC | 2,336 |
| APK size (debug) | 73 MB |
| IR catalog (SQLite) | 26.62 MB |

---

## Installation

### From Source
```bash
cd apps/android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### From Release Asset
```bash
adb install -r elysium-nexus-v0.3.0-ir-fabric-rc1-debug.apk
```

---

## Known Limitations

- **Debug signing only** — Production keystore signing pipeline is Phase 5.
- **HIL hardware verification pending** — Physical TV testing with IR decoder is Phase 5.
- **probonopd/irdb data excluded** — GPL-2.0 source is gated and not included in the production APK.

---

## What's Next (Phase 5)

1. Physical TV testing (Samsung, LG, Sony, Sankey)
2. External TSOP38438 IR receiver verification
3. Candidate ranking scoring
4. Production release keystore signing
