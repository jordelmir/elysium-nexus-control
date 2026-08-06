# PHASE 0.4 REAL PRODUCT CHANGELOG — Elysium Nexus IR Real Product v0.4.0

**Tag:** `v0.4.0-ir-real-rc1`  
**Date:** 2026-08-06  
**Status:** PRODUCTION CANDIDATE / REAL PRODUCT READY  

---

## 🏆 Architectural Overhaul Summary

This release completes all 10 Gates of the **Master Order v0.4.0**, transforming the IR Data Fabric from an engineering preview into an authoritative, fail-closed, persistent real-product IR controller system.

### Key Solved Pain Points & Core Architecture

1. **Schema v4 Native Code Sets (`code_sets`, `command_bindings`, `signals`)**
   - Eliminated single-action code set fragmentation (`cat-enc-123`).
   - Grouped every remote control under a unified `codeSetId` linking all commands (`VOLUME_UP`, `VOLUME_DOWN`, `MUTE`, `POWER_TOGGLE`, navigation).
   - Produced 1,957 multi-command `code_sets`, 35,998 `command_bindings`, and 15,479 deduplicated physical `signals`.

2. **Authoritative Profile Signal Resolution**
   - Refactored `TvControlScreen` and `ActionDispatcher` to resolve signals strictly by exact `signalId` from SQLite/Room.
   - Removed all `DeviceTemplate` physical fallback attempts, brand re-querying, and hardcoded default NEC byte mappings.

3. **Protocol Codec Authority & Fail-Closed Resolution**
   - `ProtocolCodecRegistry` is the sole protocol authority.
   - Fixed protocol resolution so `NECx` is evaluated **BEFORE** `NEC`.
   - Unknown protocol names return `ProtocolResolution.Unsupported` with zero silent fallbacks to NEC.

4. **Room Database Persistence (`ElysiumUserDatabase`)**
   - Replaced JSON file maps with transactional Room database storage (`InstalledIrProfileEntity`, `InstalledIrCommandEntity`).
   - Profiles survive process termination, process death, and application restarts.

5. **Fail-Closed Catalog Ingestion & Canonical Hashing**
   - Removed `max(0, p)` clamping, swallowed exceptions, and JSON-as-blob fallbacks in python tooling.
   - Implemented `export_canonical_catalog.py` for logical canonical content SHA-256 calculation independent of binary page layout.

6. **Candidate Scoring & Ranking (`CandidateScorer`)**
   - Implemented state-machine candidate ranking in `IrProbeEngine` scoring exact model matches (+120), remote models (+110), OEM platforms (+90), brand matches (+70), and lab verification status (+50).

---

## 📊 Verification Metrics

- **JVM Unit Tests:** PASS (`./gradlew :app:testDebugUnitTest`)
- **Android Lint:** PASS (`./gradlew :app:lintDebug`)
- **Android APK Build:** PASS (`./gradlew :app:assembleDebug`)
- **Catalog Size:** 18.82 MB
- **Canonical SHA-256:** `8e75385dfc41e2a06944eb3a9397edea2db37f59016cdf1cc66cebeaf08dc936`
