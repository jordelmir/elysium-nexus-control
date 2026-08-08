# PHASE ULT.17 — Comprehensive Audit Fixes (13 P0/P1)

**Commit:** `25ec52d`
**Audit baseline:** `b7321e5` (26 issues identified)
**Date:** 2026-08-07

## Summary

Fixed 13 of 26 audit issues from the comprehensive `b7321e5` review. All codeable P0 blockers resolved. Two P1 items deferred (need architectural decisions or keystore identity).

## P0 Fixes (10)

| # | Issue | Fix |
|---|-------|-----|
| P0-1 | CHALLENGE loop traps wizard | `IrConnectFlow` now advances to `VERIFY_SECONDARY` on confirmation |
| P0-2 | `catalog.py` calls nonexistent `seed()`, `export()` | Fixed to call `main()`, `compute_canonical_hash()`; lock verification now fail-closed |
| P0-3 | Shared source ID causes template pollution | Separated: `elysium-curated-observed` (production_approved=1) vs `elysium-template-hypotheses` (production_approved=0) |
| P0-4 | `INTERNAL_UNVERIFIED` reaches production probe | All 3 production SQL queries now filter `NOT IN ('INTERNAL_UNVERIFIED', 'BLOCKED')` |
| P0-5 | Progressive sweep SQL brand filter after GROUP BY | Brand `IN/NOT IN` moved inside WHERE clause |
| P0-6 | Fabricated `device_models=31`, `code_set_models=1773` | Deleted from catalog DB; schema preserved for future real data |
| P0-7 | Stale `canonicalContentSha256` in manifest | Recomputed from actual content via `export_canonical_catalog.py` |
| P0-9 | carrierHz overridden by protocol default | All 7 encoder functions now accept optional `carrierHz` param; `IrProtocol.encode()` passes `signal.carrierHz` |
| P0-10 | Template twins in `DeviceTwin` | Removed from `InfraredAdapter.scan()` — only Room profiles become DeviceTwins |
| P0-14 | `verification = UNVERIFIED` hardcoded | All 3 query sites now parse `cs.verification_status` via `parseVerificationStatus()` |

## P1 Fixes (3)

| # | Issue | Fix |
|---|-------|-----|
| P1-11 | N+1 evidence queries (400 Room queries per candidate load) | Single `GROUP BY codeSetId` query via `EvidenceCountRow` |
| P1-13 | CI manifest fail-open, missing bootstrap | Added `bootstrap-sources.sh --locked` step; manifest hash check fail-closed; canonical hash reproducibility gate |
| P1-19 | `runBlocking` in init blocks main thread; cache-before-DB | Init now lazy with `ensureLoaded()`; `saveProfile`/`deleteProfile` write Room BEFORE cache; accepts `CoroutineScope` param |

## Deferred (2)

| # | Issue | Reason |
|---|-------|--------|
| P1-17 | Process death test uses FakeBundle | Requires ViewModel + SavedStateHandle refactor (architectural decision) |
| P1-24 | Release signed with debug keystore | Requires keystore identity decision |

## Verification

- **814 JVM tests:** GREEN
- **assembleDebug:** GREEN
- **lintDebug:** GREEN
- **catalog.py --verify-only:** PASS
- **canonicalContentSha256:** `11c02ae03355c9ba98f5f7304b17c75a86ab1a9f23e3198e25a651ba9069d5fd` (matches manifest)
- **databaseSha256:** `7099ade2698fca85c416db67e719ea8656246314abb0f4793347e25c62ee795c` (matches file)

## Files Changed (14)

- `IrConnectFlow.kt` — CHALLENGE loop fix, N+1 evidence fix
- `IrCatalogRepository.kt` — SQL fixes, verification_status parsing
- `InfraredAdapter.kt` — Template twins removed
- `IrCatalogDatabaseManager.kt` — Manifest hash update
- `IrProtocol.kt` — carrierHz passthrough
- `IrWaveform.kt` — All 7 encode functions accept carrierHz param
- `InstalledIrProfileRepository.kt` — Lazy init, scope-based writes, cache-after-DB
- `ElysiumUserDatabase.kt` — `EvidenceCountRow` + batch query
- `ir_catalog.db` — Fabricated associations removed
- `ir_catalog.manifest.json` — canonical hash corrected
- `android-ci.yml` — Bootstrap, fail-closed, canonical reproducibility
- `catalog.py` — Fixed function calls, fail-closed
- `seed_curated_brands_v4.py` — Source ID separation
- `seed_templates_v4.py` — Source ID separation
