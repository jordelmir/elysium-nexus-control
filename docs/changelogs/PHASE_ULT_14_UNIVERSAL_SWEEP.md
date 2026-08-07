# PHASE ULT.14 — Barrido Universal Automático + Sankey 5 Candidatos

## Problema resuelto (reportado por Jor)

1. **Sankey "ya no funcionaba"**: la DB solo tenía 1 code set Sankey (flipper-irdb, NEC 0x20), pero los 4 templates Sankey en `DeviceTemplate.kt` usan NEC 0x00/0x04/0x08/0x40. Como el flujo de conexión es **SQLite-only (zero template fallback)**, solo se probaba el candidato flipper — si el TV respondía a una dirección de template, "no servía".
2. **"Control Universal" era un solo candidato**: la primera tarjeta (multimarca) probaba 1 code set; el usuario pidió un **barrido automático de las bases de datos**: cada click = nueva prueba.

## Qué se implementó

### 1. Seed de templates en el catálogo (`tools/ir-data/seed_templates_v4.py`)
- 17 code sets sembrados (Sankey×4, Control Universal, Kintech, Kalley, Challenger, Daewoo, Hyundai, Samsung, LG, Sony, Panasonic, Philips, TCL, Hisense) como PARAMETRIC NEC con **signalIds reales** (persistibles + resolubles por `DeviceCommandResolver`).
- Idempotente, IDs deterministas (mismo patrón que `seed_kintech_v4.py`).
- **Sankey ahora tiene 5 candidatos** (flipper + 4 templates) con direcciones NEC 0x20/0x00/0x04/0x08/0x40.

### 2. Barrido universal en el repositorio
- `IrCatalog.getAllCandidates(deviceType, action, limit)` — devuelve **todos** los code sets TV aprobados de todas las marcas (LIKE 'Tv%' + `Universal_Tv_Remotes`, LIMIT 400).
- Implementado en `IrCatalogRepository` (SQLite) e `InMemoryIrCatalog`.

### 3. Barrido automático en `IrConnectFlow`
- `template.id == "tv-universal-generic"` → carga el pool universal completo.
- Botón **"▶ Barrido automático (probar todas las marcas)"** en el paso TEST: transmite candidato N, pausa 3.5 s (OSD de TV), avanza a N+1, repite.
- **"¡Funcionó! Detener barrido"** confirma el último candidato transmitido (`IrProbeEngine.selectById` reposiciona el engine — §38 nunca deja un estado "stuck").
- "Pausar barrido" detiene sin confirmar. Cada click manual sigue siendo una prueba nueva (auto-avance).

### 4. Gates
- **780 tests JVM verdes** (nuevos: `selectById`, sweep 400 candidatos, `UniversalCatalogSweepTest`).
- **7 tests instrumented verdes on-device** (VER-N49): incluidos `sankeyNowOffersMultipleCodeSetsForAutoSweep` (≥5, addresses 0x00/0x04/0x08/0x40) y `universalSweepReturnsCandidatesAcrossEveryBrand` (≥100, ≥5 marcas).
- `assembleDebug` + `lintDebug` (0 errores) verdes.
- Manifest del catálogo regenerado (sha + counts reales: 812 brands, 1976 code sets, 36317 bindings).

### 5. Instalación en el Android
- APK instalado en el dispositivo (VER-N49): `com.elysium.nexus.controller` — **Success**.
- App lanzada: PID vivo, sin crashes.

## Catálogo v0.5.0 (después del seed)
812 brands · 45 device_types · 1,976 remotes/code sets · 36,317 command_bindings · 15,550 signals · 17 codecs parametric

## Files changed
- `tools/ir-data/seed_templates_v4.py` — nuevo seed de templates TV
- `fabric/infrared/database/IrCatalog.kt` — `getAllCandidates` + impl in-memory
- `fabric/infrared/database/IrCatalogRepository.kt` — SQL del barrido universal
- `fabric/infrared/IrProbeEngine.kt` — `selectById(candidateId)`
- `ui/connect/IrConnectFlow.kt` — universal sweep + botón barrido automático
- `assets/ir/ir_catalog.db` + `ir_catalog.manifest.json` — catálogo sembrado
- Tests: `IrProbeEngineTest`, `UniversalCatalogSweepTest` (nuevo), `IrCatalogRepositoryInstrumentedTest` (2 casos nuevos)
