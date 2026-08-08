# PHASE ULT.17 — Comprehensive Audit Fixes (All Codeable P0/P1)

**Commits:** `25ec52d`, `bc53d20`, `8a4dc03`, `7ff36c6`
**Audit baseline:** `b7321e5` (26 issues identified)
**Date:** 2026-08-07

## Summary

Fixed 18 of 26 audit issues from the comprehensive `b7321e5` review. All codeable P0 blockers resolved. Remaining items need user decisions (keystore) or architectural rewrites (schema v5, CandidateScorer connection).

## P0 Fixes (11)

| # | Issue | Fix | Commit |
|---|-------|-----|--------|
| P0-1 | CHALLENGE loop traps wizard | Advances to VERIFY_SECONDARY on confirm | `25ec52d` |
| P0-2 | catalog.py calls nonexistent functions | Fixed to `main()`, `compute_canonical_hash()`; fail-closed | `25ec52d` |
| P0-3 | Shared source ID causes template pollution | Separated: `elysium-curated-observed` vs `elysium-template-hypotheses` | `25ec52d` |
| P0-4 | INTERNAL_UNVERIFIED reaches production probe | All 3 SQL queries filter `NOT IN` | `25ec52d` |
| P0-5 | Progressive sweep SQL brand filter after GROUP BY | Moved inside WHERE clause | `25ec52d` |
| P0-6 | Fabricated device_models=31, code_set_models=1773 | Deleted from catalog DB | `25ec52d` |
| P0-7 | Stale canonicalContentSha256 | Recomputed from actual content | `25ec52d` |
| P0-8 | variantId not governing encoder | SQL reads protocol_name_original; resolveVariant() matches; SIRC uses addressBits | `bc53d20` |
| P0-9 | carrierHz overridden by protocol default | All 7 encoders accept carrierHz param | `25ec52d` |
| P0-10 | Template twins in DeviceTwin | Removed from InfraredAdapter.scan() | `25ec52d` |
| P0-14 | verification = UNVERIFIED hardcoded | Parsed from SQLite via parseVerificationStatus() | `25ec52d` |

## P1 Fixes (7)

| # | Issue | Fix | Commit |
|---|-------|-----|--------|
| P1-11 | N+1 evidence queries (400 per load) | Single GROUP BY via EvidenceCountRow | `25ec52d` |
| P1-12 | VERIFIED_COMMUNITY for single user | Added SESSION_VERIFIED status (LAB=6, COMMUNITY=5, SESSION=4) | `bc53d20` |
| P1-13 | CI manifest fail-open, missing bootstrap | Added bootstrap step; fail-closed; canonical hash reproducibility gate | `25ec52d` |
| P1-17 | Process death uses remember | step/verifiedActions/isAutoScanning → rememberSaveable | `7ff36c6` |
| P1-18 | No Room MigrationTestHelper tests | 3 tests: v2→v3→v4 migration, data survival, signal_sources creation | `bc53d20` |
| P1-19 | runBlocking in init, cache-before-DB | Lazy ensureLoaded(); Room-first writes; scope-based async | `25ec52d` |
| P1-20 | signal_sources in wrong DB | getSignalProvenance() queries user Room DB, not catalog DB | `8a4dc03` |
| P1-21 | needsRevalidation always false | Compares current catalog hash with stored hash | `bc53d20` |

## Deferred (4)

| # | Issue | Reason |
|---|-------|--------|
| P1-24 | Release signed with debug keystore | Needs keystore identity decision |
| — | Schema v5 native build | Requires ingest_all.py rewrite (architecture decision) |
| — | device_models → CandidateScorer | Requires JOIN integration (architecture decision) |
| — | HIL rig + device matrix | Physical hardware required |

## Verification

- **814 JVM tests:** GREEN
- **assembleDebug:** GREEN
- **lintDebug:** GREEN
- **catalog.py --verify-only:** PASS
- **canonicalContentSha256:** `11c02ae03355c9ba98f5f7304b17c75a86ab1a9f23e3198e25a651ba9069d5fd`
- **databaseSha256:** `7099ade2698fca85c416db67e719ea8656246314abb0f4793347e25c62ee795c`

## Files Changed (16)

- `IrConnectFlow.kt` — CHALLENGE loop, N+1 evidence, rememberSaveable
- `IrCatalogRepository.kt` — SQL fixes, verification_status parsing, variant resolution, signal_sources user DB
- `InfraredAdapter.kt` — Template twins removed
- `IrCatalogDatabaseManager.kt` — Manifest hash update
- `IrProtocol.kt` — carrierHz passthrough, SIRC variant dispatch
- `IrWaveform.kt` — All 7 encode functions accept carrierHz; SIRC addressBits
- `IrProbeEngine.kt` — SESSION_VERIFIED scoring
- `InstalledIrProfileRepository.kt` — Lazy init, scope-based writes, cache-after-DB, needsRevalidation
- `ElysiumUserDatabase.kt` — EvidenceCountRow, SESSION_VERIFIED, internal migrations
- `IrCodeSet.kt` — SESSION_VERIFIED enum value
- `ir_catalog.db` — Fabricated associations removed
- `ir_catalog.manifest.json` — Canonical hash corrected
- `android-ci.yml` — Bootstrap, fail-closed, canonical reproducibility
- `catalog.py` — Fixed function calls, fail-closed
- `RoomMigrationTest.kt` — New migration test suite
- `seed_curated_brands_v4.py`, `seed_templates_v4.py` — Source ID separation
