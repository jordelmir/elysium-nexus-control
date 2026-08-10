# Elysium Nexus Universal Controller — Release Notes v0.5.0-engineering-preview

> **Devices verified**: Honor Magic V2 (`VER_N49`, Android 13 magia MagicOS) — lab device per MASTER_ORDER §0.
> **Build ID:** `a12e7d6` · CI: Android CI green (6m25s) · 775 JVM tests · 0 lint errors.

---

## Highlights

- **Kintech + Control Universal TV resolvable and verified on-device.** The on-brand
  candidate probe (`Prueba de Volumen — Candidato 1 de 1 — TRANSMITIDO: 38000 HZ`)
  runs end-to-end without crashes for Kintech, Universal TV and Control Universal
  TV. Regression that previously closed the APK is closed.
- **Catalog is no longer "orphaned JSON".** The curated multi-brand seed is now a
  real SQLite Schema v4 row-set with sources gated to schema approval state = 1,
  canonical actions, and NEC@38000 parametric signals.
- **Checksum-refreshed atomic catalog install.** The app reinstates `ir_catalog.db`
  when the asset SHA drifts; in-place `adb install -r` upgrades now correct a stale
  catalog. Install is atomic and fsync-durable.
- **Single shared SQLite connection.** `IrCatalogRepository` caches one read-only
  connection per process — eliminating the per-query open/close race that produced
  `SQLiteException: no such table … (OS error - 2)`.

## Verification evidence (lab)

| Check | Result |
|---|---|
| JVM unit tests | 775 green |
| Instrumented suite (Release Blocker + catalog + probe) | **green on device** |
| On-device visual flow — Kintech | Step 2/6 · "Prueba de Volumen — Candidato 1 de 1" · 38000 Hz |
| On-device visual flow — Control Universal TV | Step 2/6 · "Prueba de Volado — Candidato 1 de 1" · 38000 Hz |
| `ir_catalog.db` install (probe) | non-empty, integrity ok |
| CI `quick_check` / `foreign_key_check` / manifest hash | PASS |

## Changelog highlights

- `IrCatalogDatabaseManager` — atomic install (write→fsync→verify→replace), never
  deletes a valid DB before replacement; fixed 0-byte file caused by reopening the
  stream for `fd.sync()`.
- `IrCatalogRepository` — one cached connection per process; removed 8 `close()` of
  that shared connection.
- `IrCatalogRepositoryInstrumentedTest` — now asserts Kintech / Control Universal TV
  return non-empty candidates.
- `DbManagerProbeInstrumentedTest` — new diagnostic probe reporting on-device install
  state through the instrumented channel (the MagicOS log-lay is encrypted).
- `tools/ir-data/seed_curated_brands_v4.py` — idempotent deterministic seeder.

## Open items

- Logcat on the lab device is encrypted by a MagicOS HKS layer; diagnosis therefore
  relies on instrumented results, filesystem probes and the visual `uiautomator`
  flow instead of `adb logcat`.
- Desktop agents, Nexus Receiver firmware, and console backends remain on the
  track (Phases 3–4, vendor license–gated).

## Next steps (roadmap)

See `docs/architecture/MASTER_ORDER.md` §45. Immediate: Nivel / IR 2 (IR prefs +
smart sondecam) and Nivel / 3 (no-loop / desgaste guarantees), then the physical
Nexus Receiver BSP.

_Generated from the canonical phase changelogs; single source of truth remains the
latest `docs/changelogs/PHASE_ULT_10_KINTECH_ONDEVICE_VERIFIED.md`._