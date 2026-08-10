# PHASE V06-PTG-02 — Eligibility Gate + Strict Ingestion + Provenance Fail-Closed

> Order: MASTER ORDER v0.6 — PHYSICAL TRUTH / DEVICE IDENTITY / PRODUCTION GATE (§3, §7, §8)
> Branch: `fix/v0.6-physical-truth-and-identity-gate`
> Baseline: main `3f93c88` unchanged

## What shipped

### 1. Eligibility gate it's LIVE (§3) — `tools/ir-data/check_catalog_eligibility.py`
- Read-only SQL gate, exit 0/1, wired into **CI fast Gate 3b** and **android-ci Gate 2c**: a push cannot pass if the catalog's candidate queries return zero.
- Two surfaces, honestly separated:
  - **Probe surface** (what the runtime probe pipeline can work on): candidates per canonical action, excluding only BLOCKED. PASSES on the packaged catalog:
    POWER 1,432 · VOLUME_UP 964 · VOLUME_DOWN 812 · MUTE 891.
  - **Eligible surface** (what may be claimed/ranked): license evidence + runtime ACTIVE + verification status above `INTERNAL_UNVERIFIED` + codec registered (or RAW encoding) + variant-unambiguous. **RED by design**: all 2,350 code sets are `INTERNAL_UNVERIFIED` — the gate proves the evidence gap instead of faking it. PTG-05's evidence pipeline (structured per-binding verification) is what flips it green.

### 2. POWER probe dead-zone found and fixed (product-level bug)
- The catalog's canonical power action is **`POWER_TOGGLE`** (1,441 bindings); `POWER` has zero. The gate probed `POWER` → 0 → the runtime Power probe would have been dead on every fresh install. Gate now maps the POWER intent to canonical `POWER_TOGGLE` (universal-remote toggle law).

### 3. Runtime candidate surface fixed (§3) — `IrCatalogRepository.kt`
- Both candidate queries changed from `verification_status NOT IN ('INTERNAL_UNVERIFIED','BLOCKED')` → `!= 'BLOCKED'`: `INTERNAL_UNVERIFIED` code sets ARE part of the probe surface (probing exists precisely to verify them). Production-eligible ranking remains evidence-gated downstream.
- Gate SQL mirrors the same predicates; comments cross-reference PTG-02 §3.

### 4. Strict ingestion (§7) — `tools/ir-data/ingest_v5.py`
- `normalize_protocol` no longer invents 38 kHz for unknown protocols: unknown → `REJECTED` with a structured record (source, file, row, reason, detail, action, protocol) instead of a fabricated signal.
- `RejectionCollector` emits `ir_catalog_rejections.json` (real counts, profile, byReason) — the artifact catalog.py already hashes into `catalogBuildId`, so **rejections now participate in build identity**.
- Wire-up points: Flipper `.ir` flush (with line numbers), radioxoma irplus `<code>` and probonopd CSV rows.

### 5. Provenance fail-closed (§8) — same file
- `sources.lock.json` is now the **sole revision-identity authority**: production `ensure_revision` raises if a source is absent from the lock — zero-hash fabrication is impossible in production (research profile keeps legacy behavior).
- License `APPROVED` is never asserted without evidence: a source file is `APPROVED` only when the lock carries `licenseFileSha256` AND `sourceContentSha256`; anything else is `AWAITING_EVIDENCE`.

## Gate evidence (run on packaged catalog, read-only SQL)

```
[gate] POWER      POWER_TOGGLE  probe_candidates=1432  OK
[gate] VOLUME_UP  VOLUME_UP     probe_candidates=964   OK
[gate] VOLUME_DOWN VOLUME_DOWN  probe_candidates=812   OK
[gate] MUTE       MUTE          probe_candidates=891   OK
[gate] ELIGIBLE_* (all)         eligible=0             FAIL (status floor: 2,350 INTERNAL_UNVERIFIED)
```

Split diagnosis: license evidence present for all 2,350; `protocol_definitions` 22 / `protocol_variants` 31 present; 85,255 RAW signals are physical prototypes (RAW clause added to eligible query); the single blocking constraint is `verification_status` ⇒ PTG-05.

## Files
- `tools/ir-data/check_catalog_eligibility.py` (new)
- `tools/ir-data/ingest_v5.py` (normalize_protocol, RejectionCollector, lock authority, ensure_revision/ensure_file, tail manifest)
- `apps/android/.../IrCatalogRepository.kt` (both candidate queries: probe surface includes INTERNAL_UNVERIFIED)
- `.github/workflows/ci-fast.yml` Gate 3b + `.github/workflows/android-ci.yml` Gate 2c
- `docs/audits/V06_REALITY_LEDGER.md` (PTG-02 rows shipped/fixed, P0 table updated)

## Status
- Implemented + gate executed locally (read-only). **No gradle run** (verify-on-request contract).
- Pending batch (Jor's command): JVM critical compile path for `IrCatalogRepository` edits, `CatalogManifestTest`/`CatalogSchemaVersionGateTest`/probe suite, full `testDebugUnitTest` + `lintDebug` + `assembleDebug`, re-run of `check_catalog_eligibility.py` after a fresh build, packaged-artifact regeneration (`catalog.py`), CI runs.