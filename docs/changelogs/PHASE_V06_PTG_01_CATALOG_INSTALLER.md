# PHASE V06-PTG-01 — Reality Ledger + Catalog Installer Stop-the-Line

> Order: MASTER ORDER v0.6 — PHYSICAL TRUTH / DEVICE IDENTITY / PRODUCTION GATE (§1, §2, §10, §18-partial, §31-partial)
> Branch: `fix/v0.6-physical-truth-and-identity-gate` (from `fix/v0.6-reality-gate` @ `1bfacea`)
> Baseline: main `3f93c88` confirmed unchanged (matches the external audit SHA)

## What shipped

### 1. Reality Ledger (§1)
- `docs/audits/V06_REALITY_LEDGER.md` — new authority doc: every relevant module
  gets EXACTLY ONE classification on the 9-rung ladder (DESIGNED →
  PRODUCTION_APPROVED), with evidence (commit/test/catalog) and the gap that
  would raise the rung. Rows the branch already earned are marked ⚡ with the
  earning commit. P0 stop-the-line table (installer, eligibility, signing,
  plaintext credentials, LG skeleton) + phase map PTG-01→PTG-16 mapping the
  audit's 70-section order.
- Zero modules claimed above INTEGRATION_VERIFIED; zero physical-device claims.
- `tools/claims-audit/audit.sh` allowlist += `V06_REALITY_LEDGER.md` (authority file).

### 2. Catalog installer rebuild — manifest is the SINGLE authority (§2)
- **REMOVED** `EXPECTED_MANIFEST_HASH` hardcoded constant (the audit's duplicated-truth P0).
- `NEW CatalogManifest.kt` — pure, JVM-testable identity module:
  - strict JSON-lite parser (no framework dependency, fail-closed),
  - `CatalogMetadata` with the 7 identity fields (§31):
    `catalogBuildId, schemaVersion, databaseSha256, canonicalContentSha256,
    sourceLockSha256, rejectionManifestSha256, licenseManifestSha256`,
  - parse fails (never partially installs) on any missing/blank field or
    non-JSON input,
  - `isSchemaVersionAccepted`: **null → REJECTED** (previously accepted "as
    last resort" because the hardcode was the real gate — the hardcode is gone,
    so fail-closed is now the only correct policy).
- `IrCatalogDatabaseManager` rewritten to the §2 layout:
  ```
  noBackupFilesDir/ir-catalog/
    builds/<catalogBuildId>/ir_catalog.db + manifest.json
    current                          ← pointer file (atomic swap)
  ```
  - extract → temp → `fd.sync()` → SHA-256 == manifest.databaseSha256
    (zero tolerance, manifest-derived — no code constant),
  - quick_check + foreign_key_check + v5-table gate (unchanged),
  - verified copy promoted into `builds/<buildId>/` (same-fs atomic rename),
    manifest copy fsynced into the build dir (build dirs are self-describing),
  - **atomic pointer swap** (`current.tmp` → `current`) AFTER full promotion;
    previous pointer captured before swap → **previous build kept for rollback**,
    others pruned; old database is NEVER deleted before the new one is verified
    (kill mid-install ⇒ old catalog remains valid — order §2 gate),
  - legacy adoption: a root-level `ir_catalog.db` from pre-PTG-01 APKs that
    exactly matches the manifest is adopted into `builds/` on next launch,
  - `currentCatalogMetadata()` / `catalogDatabaseHash()` now resolve from the
    **active build's** manifest copy.
- Fresh-install / tamper / kill guarantees are asserted by the instrumented
  probe (`DbManagerProbeInstrumentedTest` updated to the build layout:
  activeDb under builds/, pointer set, metadata resolves, tmp cleared) —
  runs in the integration gate, not locally.

### 3. Profile metadata truth (§18-partial, §31)
- `IrConnectFlow.readCatalogBuildId()` now reads the **verified build's
  metadata** (`catalogBuildId`); asset-manifest fallback reads the NEW key name.
- `catalogSchemaVersionAtInstall = 5` **hardcode removed** → schema version
  read from `CatalogMetadata`; only a missing manifest falls back to
  `INSTALLED_CATALOG_SCHEMA_UNKNOWN = 0` (documented fail-closed marker, never
  an invented version).

### 4. Catalog build identity unified across artifacts (§9, §10)
- `catalog.py step_write_manifest` now emits the full identity:
  - `sourceLockSha256` = SHA-256 of `ir-data/sources.lock.json` bytes,
  - `licenseManifestSha256` = deterministic digest of lock license/provenance
    truth (sorted source id, license SHA, license id, productionEnabled),
  - `rejectionManifestSha256` = SHA-256 of the rejection artifact (baseline
    artifact written by the builder if absent — real per-reason records land
    in PTG-02 §7),
  - `catalogBuildId` = SHA-256(prefix `ptg-v1` | schemaVersion | canonical |
    sourceLock | rejections | licenses | policy `v0.6-ptg-1`) — §9 formula,
    `policyVersion` recorded in the manifest,
  - **`ir_catalog_stats.json` rewritten in the same step with the SAME
    catalogBuildId/hashes/counts** — the packaged stale stats artifact
    (divergent `5e370c47…` vs manifest `00732dda…`) is eliminated (§10).
- `step_verify_manifest` fails hard on: missing catalogBuildId, stats buildId
  mismatch, stats databaseSha256 mismatch, stats counts mismatch.
- Packaged artifacts (manifest / stats / rejections) regenerated from the
  authoritative inputs (real DB hash, real lock) using the builder's own pure
  functions; full `--full` rebuild + CI runs remain in Jor's verification batch.

## Build Status (this update)

```
Kotlin: CatalogManifest (pure) + rewritten installer + probe update
Tests written (NOT run — verify-on-request): CatalogSchemaVersionGateTest (relabeled
  to CatalogManifest gate, null→false), CatalogManifestTest (11 cases)
Python: catalog.py identity math executed once to regenerate packaged artifacts
JVM/lint/assemble/py-suites: pending Jor's batch (verify-on-request)
```

## Next
PTG-02 (order §3/§7/§8): catalog eligibility SQL gates (POWER/VOL_UP/VOL_DOWN/MUTE
> 0), strict ingestion (unknown protocol/carrier → REJECT), RejectionCollector with
real counts, provenance fail-closed (no zero-SHA production sources).