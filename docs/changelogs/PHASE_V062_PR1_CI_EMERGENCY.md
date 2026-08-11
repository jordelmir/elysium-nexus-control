# PHASE V06.2-PR1 — CI EMERGENCY + REPRODUCIBILITY GATE

> Commit range: PR 1 of `fix/v0.6.2-truth-convergence`
> Baseline: `main` @ `2378f0d7a822ba20e7922d1cb48a4e978542cbc3`

## What this fixes

The merge of `fix/v0.6-physical-truth-and-identity-gate` into `main` shipped a
Git LFS-tracked `ir_catalog.db` (182 MB blob, OID `be7aa15fd5...`) but neither
CI workflow declared `lfs: true` on checkout. The result: every CI run would
materialise a 132-byte LFS pointer instead of the real SQLite file, making
every Gate that touches the catalog FAIL.

Additionally, both workflows contained inline mini-validators for
`sources.lock.json` that asserted `len(resolvedCommit) == 40` for ALL
sources — but first-party artifact sources use semantic tags
(`curated-tv-v1`, `curated-kintech-v1`, `ir-templates-v1`) that are
intentionally not 40 hex. Those validators would reject the very data they
were supposed to protect.

Finally, `verify_reproducibility.py` still called the legacy seeders
(`seed_curated_brands_v4.py`, `seed_kintech_v4.py`, `seed_device_models_v4.py`)
and its `rewrite_manifest()` destroyed 5 manifest identity keys — running the
tool called "verify reproducibility" could corrupt the build artifacts.

## Files changed

### New
- `tools/ir-data/validate_source_lock_schema.py` — single authority for
  `sources.lock.json` structure (kind=git vs kind=artifact). Both workflows
  now call this one script; no inline YAML validators remain.

### Modified
- `.github/workflows/ci-fast.yml` — `lfs: true` on checkout; `git lfs fsck`
  step after checkout; Gate 1 upgraded (SQLite magic + size == manifest);
  Gate 3 replaced with `validate_source_lock_schema.py`.

- `.github/workflows/android-ci.yml` — same LFS + fsck + Gate 1 + Gate 2a
  fixes in both the build job and the instrumented job checkout.

- `tools/ir-data/catalog.py` — env override `IR_CATALOG_OUTPUT_DIR` allows
  clean-room builds into independent temp workspaces (Phase 1 requirement).

- `tools/ir-data/ingest_v5.py` — same env override so ingestion writes to
  the correct workspace during clean-room A/B rebuilds.

- `tools/ir-data/optimize_catalog.py` — same env override for the optimizer.

- `tools/ir-data/verify_reproducibility.py` — complete rewrite. Three modes:
  `--fast` (CI-safe, hash-only against shipped DB), default (clean-room
  Build A + Build B via `catalog.py`), `--mutation-test` (mutate 1 row,
  canonical MUST change). Legacy seeders are gone.

## Verification

```
validate_source_lock_schema.py  → PASS (8 sources, 5 git / 3 artifact)
verify_reproducibility.py --fast → PASS (canonical + counts + integrity)
```

## What this does NOT fix

This PR is the CI emergency gate. The following P0 items remain open for
subsequent PRs:

- P0-4/5: V5 FK runtime + catalog/runtime protocol compatibility contract
- P0-6/7/8: Variant governance (VariantUnsupported, SIRC explicit, NEC/Samsung)
- P0-9: PhysicalSignalIdentityV2 (repeat/toggle/press/state)
- P0-10/11/12: sr.version bug, catalog_signal_sources vs signal_verification_evidence
- P0-13/14/15: enum consistency, UNSUPPORTED_PROTOCOL in research, ensure_source_from_lock
- P0-16: CatalogManifest hash shape + policyVersion enforcement
- P0-17/18/19/20: Durable profile transaction, atomic selected command
- P0-21/22: Per-binding revalidation
- P0-23/24: Process-death recovery, attempt ledger lifecycle
- P0-25: Paging integrated into product flow
- P0-26/27/28/29/30/31: Identity V2, credential vault, Mac secure channel V2
- P0-32…45: LG vertical, automation, HIL, platform migration, etc.

## Reference

- Audit: V0.6.2 "TRUTH CONVERGENCE / PRODUCTION PROOF" master order
- P0-1 (LFS CI), P0-2 (source-lock CI), P0-3 (reproducibility)
