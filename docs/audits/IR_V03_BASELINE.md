# IR Data Fabric v0.3.0 Baseline Audit — Final Report

**Date**: 2026-08-06
**Version**: `v0.3.0-ir-fabric-rc1` (`versionCode = 2`)
**Head Commit**: `8727ef1`
**Branch**: `main`
**Lab Device**: Honor Magic V2 (Android 14)

---

## 1. Pre-v0.3.0 State (v0.2.0 Developer Preview)

| Metric | v0.2.0 Value |
|--------|-------------|
| **Unit Test Pass Rate** | 758 / 758 green |
| **APK Size (Debug)** | 77.0 MB |
| **ir_catalog.db Size** | 35.73 MB (37,466,112 bytes) |
| **ir_catalog.db SHA-256** | `648250e23c039d69fbc0a198b6c43fc124c60a7068e4af6279bc3fa3eed2d760` |
| **P0 Defects Identified** | 12 |
| **Build Time (cached)** | 1.8s |

### v0.2.0 Database Contents (Unoptimized)

| Table | Rows |
|-------|------|
| brands | 1,441 |
| device_types | 1,470 |
| remotes | 5,537 |
| commands_encoded | 118,990 |
| commands_raw | 80,829 |
| total_commands | 199,819 |
| protocols | 219 |

---

## 2. Post-v0.3.0 State (Production Candidate)

| Metric | v0.3.0 Value | Delta |
|--------|-------------|-------|
| **Unit Tests** | 772 / 772 green | +14 tests |
| **Lint Errors** | 0 | Clean |
| **APK Size (Debug)** | 73 MB | −4 MB |
| **ir_catalog.db Size** | 26.62 MB | −25.5% (optimized) |
| **ir_catalog.db SHA-256** | `7f06b0ec0e8b6a2aa0934930fe62edcb1ab52c213e42b6766cf24917ee515e44` | — |
| **Brands** | 915 | Deduplicated |
| **Device Types** | 37 | Collapsed |
| **Remotes** | 2,394 | Deduped |
| **Total Commands** | 108,681 | Production-filtered |
| **P0 Defects Remaining** | **0** | All 8 resolved |
| **Source Locks** | 5/5 verified (40-hex SHAs) | Real |
| **Kotlin Source Files** | 194 | — |
| **Kotlin Production LoC** | 41,586 | — |
| **Kotlin Test LoC** | 12,827 | — |
| **Python Tooling LoC** | 2,336 | — |

---

## 3. P0 Vulnerabilities — Resolution Matrix

| ID | Vulnerability | Resolved By | Commit |
|----|---------------|-------------|--------|
| P0-1 | Profile persistence loss | `InstalledIrProfile` + `InstalledIrProfileRepository` | `3422abd` |
| P0-2 | Catalog race condition | `ProbeUiState` state machine | `3422abd` |
| P0-3 | Unconfirmed candidate promotion | Guard chip on `IrTransmitResult.Success` | `3422abd` |
| P0-4 | Silent protocol fallback to NEC | `IrProtocol.resolveProtocol()` fail-closed | `3422abd` |
| P0-5 | RC5 waveform encoding defect | Manchester biphasic encoding + phase coalescing | `3422abd` |
| P0-6 | Insecure hash fingerprints | SHA-256 canonical physical fingerprinting | `3422abd` |
| P0-7 | Mock source locks | Real 40-hex SHA extraction via `lock_sources.py` | `3422abd` |
| P0-8 | Fake `OnOff(true)` translations | Removed from `ActionDispatcher` | `3422abd` |
| P0-9 | License gating inseparability | `--profile production` excludes gated sources | `3422abd` |
| P0-10 | Corrupt blob optimization | Strict fail-closed optimization pipeline | `3422abd` |
| P0-11 | Hardcoded NEC signals in adapter | `DeviceCommandResolver` resolves via profile | `8727ef1` |
| P0-12 | Dispatcher incompatibility | Removed fake action translations | `3422abd` |

---

## 4. Supply Chain Audit

| Source | Lock Status | Commit Match | Tree Match | License Hash Match |
|--------|-------------|--------------|------------|-------------------|
| flipper-irdb | ✅ LOCKED | ✅ | ✅ | ✅ |
| smartir | ✅ LOCKED | ✅ | ✅ | ✅ |
| probonopd-irdb | ✅ LOCKED (GATED) | ✅ | ✅ | ✅ |
| radioxoma-infrared | ✅ LOCKED | ✅ | ✅ | ✅ |
| irp-transmogrifier | ✅ LOCKED | ✅ | ✅ | ✅ |

Bootstrap script verification: **PASSED** (zero `|| true`, strict detached checkout, commit SHA match assertion).

---

## 5. Verdict

**v0.3.0-ir-fabric-rc1 is approved as Production Candidate.**

All 12 P0 defects resolved. Supply chain verified. Database integrity confirmed. 772/772 tests green. 0 lint errors. APK deployed to lab device.

Remaining gate for GA release: **Phase 5 HIL hardware verification** (physical TV testing with IR receiver/decoder).
