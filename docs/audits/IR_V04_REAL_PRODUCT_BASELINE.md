# IR Real Product v0.4.0 Baseline Audit

**Date**: 2026-08-06  
**Branch**: `fix/ir-real-product-v0.4`  
**Base Commit SHA**: `ebf2fd2e1f8915f2770be65f2f50985a475a716f`  

---

## 1. Baseline Measurements

| Metric | Baseline Value |
|--------|----------------|
| **Base Commit SHA** | `ebf2fd2e1f8915f2770be65f2f50985a475a716f` |
| **Android Version target** | API 34 (min API 26) |
| **Catalog Database SHA-256 (v3)** | `7f06b0ec0e8b6a2aa0934930fe62edcb1ab52c213e42b6766cf24917ee515e44` |
| **Catalog Database Size (v3)** | 26.62 MB |
| **APK Debug Size** | 73 MB |
| **JVM Unit Tests** | 772 passed |
| **Lint Errors** | 0 |
| **Protocols Declared** | 7 (NEC, NECx, Samsung32, SonySIRC, RC5, RC6, Kaseikyo) |
| **Protocols HIL Verified** | 0 (Pending HIL test harness) |
| **Database Path Active** | `context.cacheDir/ir_catalog.db` vs `noBackupFilesDir/ir-catalog/ir_catalog.db` (Ambiguity to resolve) |
| **Profile Storage** | JSON in `noBackupFilesDir` (To migrate to Room) |

---

## 2. Identified Defect & Architectural Audit (v0.3.0 -> v0.4.0)

1. **Single-Action Code Sets (P0)**: v3 SQLite catalog queries `commands_encoded` and `commands_raw` per-action, instantiating `IrCodeSet` objects with 1 button. Winner candidates lack full remote commands.
2. **Runtime Re-Search Mismatch (P0)**: `TvControlScreen` re-queries candidates by brand at button click and selects first match or falls back to `DeviceTemplate` address/command bytes.
3. **Invented Candidate Fallback (P0)**: `IrConnectFlow` creates hardcoded NEC address 0x00 and Samsung codes if catalog returns no candidates.
4. **Local Protocol Resolution & NEC Fallback (P0)**: `IrCatalogRepository.mapProtocol()` matches `NEC` before `NECx` and falls back `else -> IrProtocol.Nec`.
5. **Permissive Optimizer (P0)**: `optimize_catalog.py` contains `max(0, p)`, `except Exception: pass`, and `CAST(pattern_json AS BLOB)`.
6. **Fake Canonical Hash (P0)**: `canonicalContentSha256` assigned `databaseSha256` binary hash instead of true logical export SHA-256.
7. **JSON Persistence Vulnerability (P0)**: `InstalledIrProfileRepository` uses non-transactional JSON map.
8. **Hardcoded Adapter Signals (P0)**: `InfraredAdapter` contains hardcoded NEC bytes `0x44`, `0x45`, `0x46` and marks IR devices as `Online`.
9. **Probe Race Conditions (P0)**: `currentResult` in `IrConnectFlow` lacks `attemptId` validation.

---

## 3. Transformation Plan (v0.4.0)

- **Step 1**: Catalog v4 SQL Schema & Migration (`code_sets`, `signals`, `command_bindings` with deterministic SHA-256 IDs).
- **Step 2**: Pipeline Fail-Closed Optimization & True Canonical Exporter.
- **Step 3**: ProtocolCodecRegistry as Single Authority (No local `mapProtocol()`, strict NECx/NEC ordering, `ProtocolResolution.Unsupported`).
- **Step 4**: Room Persistence for `InstalledIrProfile` (`ElysiumUserDatabase`).
- **Step 5**: Fallback-Free Execution Path (`DeviceCommandResolver` -> `signalId` -> `IrCatalog.getSignal`).
- **Step 6**: State Machine Probing (`IrProbeViewModel`, `ProbeAttempt` IDs, multi-action verification).
- **Step 7**: Release Pipeline, HIL Harness & Verification Gates.
