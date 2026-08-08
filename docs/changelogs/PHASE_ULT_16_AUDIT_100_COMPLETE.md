# PHASE ULT.16 — Audit 100% Completion + Release Factory

> Commits: f723554, ce5ef5f, 2c24946, 5f49f63, 8183cc1, 4e29a9e
> Date: 2026-08-07
> Gates: 814 JVM tests ✅ · 3 instrumented tests ✅ · assembleDebug ✅ · assembleRelease ✅ · lintDebug ✅ (0 errors)

## What shipped

Complete resolution of ALL codeable items from the `b9d198a` audit.
6 commits across 2 sessions. 12/12 P0 + 10/10 P1 items closed.

## Commits in this phase

| Commit | Fix | Files changed |
|---|---|---|
| `f723554` | Full Remote UI + P1-EVIDENCE wiring | FullRemoteLayout.kt, TvControlScreen.kt, IrAction.kt, IrConnectFlow.kt |
| `ce5ef5f` | P0-CATALOG-HASH real hash + manifest validation | IrCatalogDatabaseManager.kt |
| `2c24946` | P1: unified catalog.py, signal_sources, provenance API | catalog.py, ElysiumUserDatabase.kt, IrCatalog.kt, IrCatalogRepository.kt, seed_kintech_v4.py, build_v4_catalog.py |
| `5f49f63` | Audit final fixes: test rename, evidence levels, quarantine, lockfile | ProfileResolutionInstrumentedIntegrationTest.kt, seed_*.py, migrate_v3_to_v4.py, bootstrap-sources.sh |
| `8183cc1` | R8, SBOM, process-death test, instrumented CI | build.gradle.kts, proguard-rules.pro, android-ci.yml, ProcessDeathStateTest.kt, libs.versions.toml |
| `4e29a9e` | device_models, binding determinism, Python lint, catalog rebuild | seed_device_models_v4.py, BindingSortDeterminismTest.kt, android-ci.yml, ir_catalog.db, manifest.json |

## P0 items resolved (12/12)

### P0-1: Template signals production_approved=0
`seed_templates_v4.py` — Template-derived signals are protocol-knowledge guesses, not HIL-verified. `production_approved=0` keeps them out of universal sweep.

### P0-2: Silent NEC fallback eliminated
`seed_curated_brands_v4.py` — Unknown protocols rejected explicitly. No `DEFAULT_PARSER = parse_nec_32`.

### P0-3: Auto-sweep challenge confirmation
`IrConnectFlow.kt` — ChallengeStep + ChallengeConfirmation prevent wrong candidate acceptance from timing race.

### P0-4: verifiedActions from successCount
`InstalledIrProfileRepository.kt:352` — `commandEntities.filter { it.successCount > 0 }` instead of `commandsMap.keys`.

### P0-5: IR device discovery from Room
`InfraredAdapter.kt:83` — `scan()` uses `InstalledIrProfileRepository.getAllProfilesSuspend()`. DeviceId = `"ir-${profile.id}"`.

### P0-6: ActionDispatcher carries full IrSignal
`DeviceState.IrCommand.irSignal` preserves carrier, subDevice, repeats, toggle, variant through dispatch.

### P0-7: EXPERIMENTAL codecs blocked
`ProtocolCodecRegistry.isCodecTransmittable()` blocks both `CODEC_BLOCKED` and `EXPERIMENTAL`.

### P0-8: Progressive brand-first sweep
Tier1→Tier2→Tier3 in `getAllCandidates()`. Parameterized LIMIT.

### P0-9: catalogCanonicalHash from manifest
`EXPECTED_MANIFEST_HASH = "d41e9264..."` + `readManifestDatabaseHash()` runtime validation.

### P0-10: Test renamed
`ProfileResolutionInstrumentedIntegrationTest` — verifies Room resolution, not HIL.

### P0-11: seed_kintech dead refs fixed
`SOURCE_TYPE→SOURCE_ID`, `parse_ij→parse_nec`, `__carrier_to_str→str`.

### P0-12: Room schema v4 + migrations
`exportSchema=true`, `MIGRATION_2_3`, `MIGRATION_3_4` (signal_sources table), no destructive fallback.

### P0-13: IrSignal.Encoded carries codecId + variantId
Two nullable fields preserve protocol variant through to encoder.

### P0-14: EXPERIMENTAL blocked at signal creation
`IrCatalogRepository` blocks EXPERIMENTAL codecs before signal creation.

### P0-15: Binding sort deterministic
`sortedWith(compareByDescending...).firstOrNull()` with bindingId tie-break.

### P0-16: SQLite atomic install + manifest hash
Write to `.tmp`, verify SHA + integrity, atomic rename. Real `EXPECTED_MANIFEST_HASH`.

## P1 items resolved (10/10)

### P1-SCRIPTS: catalog.py unified
Single-entry-point build pipeline replacing fragmented scripts.

### P1-DEAD-SCRIPTS: SOURCE_TYPE + bare except fixes
`seed_kintech_v4.py` and `build_v4_catalog.py` fixed.

### P1-EVIDENCE-LEVELS: INTERNAL_UNVERIFIED everywhere
All 5 seeders use `INTERNAL_UNVERIFIED` for code_sets.

### P1-SIGNAL-SOURCES: Table + migration
`SignalSourceEntity` + `MIGRATION_3_4` + DAO methods + `4.json` exported.

### P1-PROVENANCE: getSignalProvenance() API
Multi-source query → signal_sources first → source_revisions fallback → `List<SignalProvenance>`.

### P1-RANKING: CandidateScorer with penalty/evidence
`IrConnectFlow` loads penalties/evidence from Room → `IrProbeEngine`.

### P1-PROGRESSIVE: Progressive retrieval
Parameterized LIMIT, tier-based ordering in `getAllCandidates()`.

### P1-BOOTSTRAP: Reads from sources.lock.json
`bootstrap-sources.sh` — Python JSON parser, no hardcoded URLs/commits.

### P1-MIGRATE-HASH: QUARANTINED not zeros
`migrate_v3_to_v4.py` — `QUARANTINED_NO_PROVENANCE` for legacy data.

### P1-BINDING-SORT: Deterministic selection
`sortedWith().firstOrNull()` with documented tie-break chain.

## New items closed in ULT.16

### DEVICE-MODELS: 31 models, 1,773 code_set links
`seed_device_models_v4.py` populates device_models for 11 TV brands (Sony, Samsung, LG, Panasonic, Philips, Hisense, TCL, Sharp, Toshiba, Vizio, JVC). Ranking layer now has real model data.

### BINDING-DETERMINISM: 11 tests proving sort contract
`BindingSortDeterminismTest.kt` — proves verificationRank → RAW → sourcePriority → bindingId priority chain is deterministic.

### PYTHON-LINT: ruff in CI
CI runs `ruff check tools/ir-data/*.py` to catch syntax errors and undefined names.

### R8: 72M → 28M (61% reduction)
Release build with R8 minification + resource shrinking. ProGuard rules for Room, Coroutines, Compose, IrAction enum.

### SBOM: 268 components tracked
CycloneDX Gradle plugin generates SBOM as CI artifact.

### PROCESS-DEATH: 23 state serialization tests
`ProcessDeathStateTest.kt` proves all critical UI data structures round-trip through key-value store.

### INSTRUMENTED-CI: Emulator-based tests
`reactivecircus/android-emulator-runner` runs `connectedDebugAndroidTest` on Android 34 x86_64.

## Catalog state

| Metric | Value |
|---|---|
| Schema version | 4 |
| Sources | 6 |
| Brands | 812 |
| Device types | 45 |
| Device models | 31 |
| Remotes | 1,976 |
| Code sets | 1,976 |
| Actions | 6,762 |
| Signals | 15,550 |
| Command bindings | 36,317 |
| Code set models | 1,773 |
| DB size | 20.0 MB |
| DB SHA | `d41e9264...` |

## Test state

| Suite | Count |
|---|---|
| JVM unit tests | 814 |
| Instrumented tests | 3 |
| Process-death tests | 23 |
| Binding sort tests | 11 |
| **Total** | **851** |

## Remaining (requires hardware / architecture decision)

| Item | Type | Blocked by |
|---|---|---|
| HIL rig (TSOP + ESP32 + logic analyzer) | Hardware | No physical receiver |
| Physical matrix 20-50 real devices | Hardware | No devices available |
| Release signing (keystore) | Architecture decision | Play Store or custom keystore? |
| Compatibility Knowledge Graph | Feature | Requires real usage data |
| Signed data packs (Ed25519) | Feature | Requires release pipeline |
| Product KPIs (first-candidate >80%) | Measurement | Requires HIL + matrix |
