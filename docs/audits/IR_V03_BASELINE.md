# IR Data Fabric v0.3.0 Baseline Audit

**Date**: 2026-08-06  
**Branch**: `fix/ir-fabric-v0.3-production-candidate`  
**Base Commit SHA**: `5789174c1e021260e5d085d642e638e29fab7489`  

---

## 1. Initial State Measurements

| Metric | Value |
|--------|-------|
| **Base Commit SHA** | `5789174c1e021260e5d085d642e638e29fab7489` |
| **Unit Test Pass Rate** | 758 / 758 green (0 failures) |
| **APK Size (Debug)** | 77.0 MB |
| **ir_catalog.db Size** | 35.73 MB (37,466,112 bytes) |
| **ir_catalog.db SHA-256** | `648250e23c039d69fbc0a198b6c43fc124c60a7068e4af6279bc3fa3eed2d760` |
| **Build Time (cached)** | 1.8s |

### SQLite Database Content Audit (Pre-v0.3.0)

| Table | Row Count |
|-------|-----------|
| **brands** | 1,441 |
| **device_types** | 1,470 |
| **remotes** | 5,537 |
| **commands_encoded** | 118,990 |
| **commands_raw** | 80,829 |
| **total_commands** | 199,819 |
| **protocols** | 219 |

---

## 2. P0 Audit Vulnerabilities Identified (To Fix in v0.3.0)

1. **Profile Persistence Loss (P0)**: `IrConnectFlow` returns `DeviceTemplate` instead of an `InstalledIrProfile`. `TvControlScreen` falls back to hardcoded `DeviceCatalog` static codes.
2. **Catalog Race Condition (P0)**: `IrProbeEngine` initialized with static `DeviceCatalog` before async SQLite candidate loading finishes.
3. **Unconfirmed Candidate Promotion (P0)**: "Sí, subió el volumen" chip enabled even on failed transmissions (`NoEmitter`, `PermissionDenied`, `InvalidPattern`).
4. **Silent Protocol Fallback to NEC (P0)**: `mapProtocol()` converts unknown protocols into `NEC`, and `NECx` branch is unreachable.
5. **RC5 Waveform Encoding Defect (P0)**: Bit loop ignores bit value (`pattern.add(889)` twice regardless of bit).
6. **Insecure Hash Fingerprints (P0)**: `contentHashCode()` used for raw patterns instead of SHA-256 physical fingerprints.
7. **Mock Source Locks (P0)**: `sources.lock.json` and `ir_catalog.manifest.json` contain example SHA hashes (`b2c3d4e5...`, `e3b0c442...`).
8. **Permissive Bootstrap (P0)**: `bootstrap-sources.sh` ignores clone errors with `|| true` and lacks SHA verification.
9. **License Gating Inseparability (P0)**: Gated sources (`probonopd`) stored inside main DB instead of separate production vs. research packs.
10. **Corrupt Blob Optimization Risk (P0)**: `optimize_catalog.py` converts negative durations to zero and falls back to JSON text casting on blob error.
11. **Fabric Adapter Hardcoded NEC Signals (P0)**: `InfraredAdapter` contains hardcoded NEC bytes `0x44`, `0x45`, `0x46` instead of resolving signals through `InstalledIrProfile`.
12. **Dispatcher Incompatibility (P0)**: `ActionDispatcher` translates volume actions to `DeviceState.Level` and navigation to `OnOff(true)` which `InfraredAdapter` rejects.

---

## 3. Plan of Execution (Fases 1 — 5)

- **Fase 1**: Reclasificar v0.2.0 a Preview,Locks Reales, Bootstrap Estricto, Filtro Licencia, Schema V3.
- **Fase 2**: Implementar `InstalledIrProfile`, persisitir perfil ganador, conectar `TvControlScreen` a `InstalledIrProfile`.
- **Fase 3**: Eliminar fallbacks a NEC, corregir RC5 Manchester, SHA-256 physical fingerprints.
- **Fase 4**: Refactorizar `ActionDispatcher`, `InfraredAdapter` y `CommandResolver`.
- **Fase 5**: Suites de pruebas instrumentadas, golden vectors, HIL runner, y firma de release.
