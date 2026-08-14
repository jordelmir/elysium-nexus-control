#!/usr/bin/env python3
"""
Elysium Nexus — Unified IR Catalog Builder
============================================
Single entry point for all catalog operations. Replaces the fragmented
build_catalog.py + build_v4_catalog.py + individual seeders.

Usage:
  python3 tools/ir-data/catalog.py --profile production
  python3 tools/ir-data/catalog.py --profile research
  python3 tools/ir-data/catalog.py --verify-only
  python3 tools/ir-data/catalog.py --seed-templates
  python3 tools/ir-data/catalog.py --seed-brands
  python3 tools/ir-data/catalog.py --seed-kintech

Pipeline:
  1. Lock sources (or verify existing lock)
  2. Ingest from authorized sources
  3. Seed curated brands from ir_codes_db.json
  4. Seed DeviceTemplate-based TV brands
  5. Seed Kintech
  6. Optimize (orphan cleanup, VACUUM)
  7. Export canonical hash
  8. Verify manifest consistency
  9. Package for Android assets
"""

import argparse
import hashlib
import json
import os
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TOOLS_DIR = ROOT / "tools" / "ir-data"
# V0.6.2 Phase 1: clean-room builds redirect the artifact output via
# IR_CATALOG_OUTPUT_DIR (verify_reproducibility.py builds A/B into temp
# workspaces). Constantes por defecto siguen apuntando a los assets empaquetados.
_DEFAULT_OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
OUTPUT_DIR = Path(os.environ.get("IR_CATALOG_OUTPUT_DIR") or _DEFAULT_OUTPUT_DIR)
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
MANIFEST_PATH = OUTPUT_DIR / "ir_catalog.manifest.json"
REJECTIONS_PATH = OUTPUT_DIR / "ir_catalog_rejections.json"
STATS_PATH = OUTPUT_DIR / "ir_catalog_stats.json"
SOURCES_LOCK_PATH = ROOT / "ir-data" / "sources.lock.json"

# PTG-01 §9: policy version participates in catalogBuildId. Bump ONLY when the
# eligibility/identity policy changes (it invalidates every build identity).
BUILD_ID_POLICY_VERSION = "v0.6-ptg-1"
BUILD_ID_PREFIX = "ptg-v1"

sys.path.insert(0, str(TOOLS_DIR))


def calculate_file_sha256(file_path: Path) -> str:
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def compute_license_manifest_sha() -> str:
    """§9: deterministic digest of the license/provenance truth in the lockfile.

    Ordered by source id — stable across machines and rebuilds. Uses only
    identifier- and truth-bearing fields; timestamps are excluded.
    """
    lock = json.loads(SOURCES_LOCK_PATH.read_text())
    entries = sorted(
        (
            s.get("id", ""),
            s.get("licenseFileSha256", ""),
            s.get("sourceLicense", ""),
            bool(s.get("productionEnabled", False)),
        )
        for s in lock.get("sources", [])
    )
    canonical = "\n".join(f"{i}\t{f}\t{lic}\t{p}" for i, f, lic, p in entries) + "\n"
    return sha256_text(canonical)


def compute_catalog_build_id(schema_version: int, canonical_hash: str, source_lock_sha: str,
                             rejection_sha: str, license_sha: str) -> str:
    """§9: catalogBuildId = SHA256(prefix + schema + canonical + lock + rejections + licenses + policy)."""
    material = "|".join([
        BUILD_ID_PREFIX,
        str(schema_version),
        canonical_hash,
        source_lock_sha,
        rejection_sha,
        license_sha,
        BUILD_ID_POLICY_VERSION,
    ])
    return sha256_text(material)


def log(msg: str):
    print(f"[catalog] {msg}", flush=True)


def step_verify_locks():
    """Step 1: Verify source lockfile matches local checkouts."""
    log("Verifying source locks...")
    try:
        import verify_source_locks
        verify_source_locks.verify()
        log("Source locks verified.")
    except Exception as e:
        log(f"FATAL: Source lock verification failed: {e}")
        sys.exit(1)


def step_ingest(profile: str):
    """Step 2: Ingest from all authorized sources (Schema v4 native)."""
    log(f"Ingesting sources (profile={profile})...")
    import ingest_v5
    ingest_v5.run_ingestion(profile=profile)
    log("Ingestion complete.")


def step_import_curated(profile: str):
    """Step 3: V0.6.1 Phase 1/2 — curated TV dataset through the SourceAdapter
    (fail-closed EntityCache + sources.lock authority, real per-file SHA-256).
    NO legacy direct-DB seeder may touch the production artifact."""
    log(f"Importing curated TV dataset (profile={profile})...")
    import sqlite3 as _sql
    import ingest_v5
    import source_adapters
    conn = _sql.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    collector = ingest_v5.RejectionCollector()
    lock = ingest_v5.load_source_lock()
    cache = ingest_v5.EntityCache(conn.cursor(), profile=profile,
                                  lock=lock, rejections=collector)
    stats = source_adapters.import_curated(cache, profile=profile)
    collector.write_rows(conn)
    conn.commit()
    conn.close()
    log(f"Curated imported: {stats}")


def step_import_kintech(profile: str):
    """Step 4: V0.6.1 Phase 1/2 — KINTECH subset under its own locked source
    identity (elysium-nexus-curated)."""
    log(f"Importing KINTECH dataset (profile={profile})...")
    import sqlite3 as _sql
    import ingest_v5
    import source_adapters
    conn = _sql.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    collector = ingest_v5.RejectionCollector()
    lock = ingest_v5.load_source_lock()
    cache = ingest_v5.EntityCache(conn.cursor(), profile=profile,
                                  lock=lock, rejections=collector)
    stats = source_adapters.import_kintech(cache, profile=profile)
    collector.write_rows(conn)
    conn.commit()
    conn.close()
    log(f"KINTECH imported: {stats}")


def step_import_templates(profile: str):
    """Step 5 (RESEARCH ONLY): hypothesis templates enter ONLY research
    catalogs. import_templates raises under production by construction —
    the dangerous data does not exist in the production artifact."""
    if profile == "production":
        log("Templates skipped: elysium-template-hypotheses is RESEARCH_ONLY "
            "(Phase 1 fail-safe)")
        return
    log(f"Importing hypothesis templates (profile={profile})...")
    import sqlite3 as _sql
    import ingest_v5
    import source_adapters
    conn = _sql.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    collector = ingest_v5.RejectionCollector()
    lock = ingest_v5.load_source_lock()
    cache = ingest_v5.EntityCache(conn.cursor(), profile=profile,
                                  lock=lock, rejections=collector)
    stats = source_adapters.import_templates(cache, profile=profile)
    collector.write_rows(conn)
    conn.commit()
    conn.close()
    log(f"Templates imported: {stats}")


def step_optimize():
    """Step 6: Optimize catalog (orphan cleanup, RAW validation, VACUUM)."""
    log("Optimizing catalog...")
    import optimize_catalog
    optimize_catalog.optimize()
    log("Optimization complete.")


def step_export_hash() -> tuple[str, dict]:
    """Step 7: Export canonical hash and entity counts."""
    log("Computing canonical hash...")
    import export_canonical_catalog
    canonical_hash, counts = export_canonical_catalog.compute_canonical_hash(DB_PATH)
    log(f"Canonical hash computed: {canonical_hash[:16]}...")
    return canonical_hash, counts


def step_write_manifest(profile: str, db_sha256: str, canonical_hash: str, counts: dict):
    """Step 8: Write ir_catalog.manifest.json — manifest is the SINGLE catalog authority (PTG-01 §2/§9/§10).

    Carries the complete build identity: catalogBuildId + all provenance and
    gate hashes. ir_catalog_stats.json is rewritten in the SAME write with the
    SAME buildId — stale/divergent artifacts are a hard failure (order §10).
    """
    source_lock_sha = calculate_file_sha256(SOURCES_LOCK_PATH)
    license_sha = compute_license_manifest_sha()

    if REJECTIONS_PATH.exists():
        rejection_sha = calculate_file_sha256(REJECTIONS_PATH)
    else:
        # Fail-closed baseline: a build without a rejection artifact still
        # documents it explicitly (PTG-02 replaces this with real records).
        baseline = {
            "buildProfile": profile,
            "catalogBuildId": None,  # filled below after buildId computation
            "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "byReason": {},
            "totalRejections": 0,
            "note": "baseline — PTG-02 will record real rejection counts",
        }
        REJECTIONS_PATH.write_text(json.dumps(baseline, indent=2, ensure_ascii=False) + "\n")
        rejection_sha = calculate_file_sha256(REJECTIONS_PATH)

    schema_version = 5
    catalog_build_id = compute_catalog_build_id(
        schema_version, canonical_hash, source_lock_sha, rejection_sha, license_sha
    )

    manifest = {
        "catalogBuildId": catalog_build_id,
        "schemaVersion": schema_version,
        "profile": profile,
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "pipelineVersion": "0.5.1-v5-native",
        "databaseSha256": db_sha256,
        "canonicalContentSha256": canonical_hash,
        "sourceLockSha256": source_lock_sha,
        "rejectionManifestSha256": rejection_sha,
        "licenseManifestSha256": license_sha,
        "policyVersion": BUILD_ID_POLICY_VERSION,
        "databaseSizeBytes": DB_PATH.stat().st_size,
        "counts": counts
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    log(f"Manifest written: {MANIFEST_PATH} (buildId={catalog_build_id[:16]}...)")

    # §10: stats artifact gets the SAME identity — no second truth.
    stats = {
        "catalogBuildId": catalog_build_id,
        "schemaVersion": schema_version,
        "profile": profile,
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "pipelineVersion": "5.1.0-v5-native",
        "databaseSha256": db_sha256,
        "canonicalContentSha256": canonical_hash,
        "databaseSizeBytes": DB_PATH.stat().st_size,
        "counts": counts
    }
    STATS_PATH.write_text(json.dumps(stats, indent=2, ensure_ascii=False) + "\n")
    log(f"Stats rewritten with same buildId: {STATS_PATH.name}")


def step_verify_manifest():
    """Step 9: Verify installed DB matches manifest."""
    if not DB_PATH.exists():
        log("ERROR: No ir_catalog.db found.")
        return False
    if not MANIFEST_PATH.exists():
        log("ERROR: No manifest found.")
        return False

    manifest = json.loads(MANIFEST_PATH.read_text())
    expected_hash = manifest.get("databaseSha256", "")
    actual_hash = calculate_file_sha256(DB_PATH)

    if actual_hash != expected_hash:
        log(f"MANIFEST MISMATCH: expected={expected_hash}, actual={actual_hash}")
        return False

    # SQLite integrity check
    conn = sqlite3.connect(str(DB_PATH))
    try:
        fk = conn.execute("PRAGMA foreign_key_check").fetchall()
        if fk:
            log(f"INTEGRITY FAIL: foreign_key_check found {len(fk)} violations")
            return False
        qc = conn.execute("PRAGMA quick_check").fetchone()[0]
        if qc != "ok":
            log(f"INTEGRITY FAIL: quick_check returned {qc}")
            return False
    finally:
        conn.close()

    log(f"Manifest verified: SHA256={actual_hash}, integrity=ok")

    # §10: artifacts must share ONE build identity — any divergence fails.
    build_id = manifest.get("catalogBuildId", "")
    if not build_id:
        log("ERROR: manifest has no catalogBuildId (stale/pre-PTG-01 artifact).")
        return False
    if STATS_PATH.exists():
        stats = json.loads(STATS_PATH.read_text())
        if stats.get("catalogBuildId") != build_id:
            log(f"STALE BUILD TRUTH: stats.catalogBuildId={stats.get('catalogBuildId')} != manifest={build_id}")
            return False
        if stats.get("databaseSha256") != expected_hash:
            log("STALE BUILD TRUTH: stats.databaseSha256 differs from manifest")
            return False
        if stats.get("counts") != manifest.get("counts"):
            log("STALE BUILD TRUTH: counts differ between stats and manifest")
            return False
        log("Build identity unified: manifest ↔ stats carry the same catalogBuildId")
    else:
        log("WARNING: ir_catalog_stats.json absent (run full build to regenerate)")

    return True


def main():
    parser = argparse.ArgumentParser(description="Elysium Nexus — Unified IR Catalog Builder")
    parser.add_argument("--profile", choices=["production", "research"], default="production",
                        help="Build profile: production (approved sources) or research (all sources)")
    parser.add_argument("--verify-only", action="store_true",
                        help="Only verify existing catalog against manifest")
    parser.add_argument("--import-templates", action="store_true",
                        help="Source-import hypothesis templates (research only)")
    parser.add_argument("--import-curated", action="store_true",
                        help="Source-import curated TV dataset (fail-closed)")
    parser.add_argument("--import-kintech", action="store_true",
                        help="Source-import KINTECH dataset (fail-closed)")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Verify-only mode
    if args.verify_only:
        ok = step_verify_manifest()
        sys.exit(0 if ok else 1)

    # Individual source-import modes (each obliges a rebuild of build identity)
    if args.import_templates:
        step_import_templates(args.profile)
        return
    if args.import_curated:
        step_import_curated(args.profile)
        return
    if args.import_kintech:
        step_import_kintech(args.profile)
        return

    # Full build pipeline
    log(f"Starting full catalog build (profile={args.profile})")

    # Step 1: Verify source locks
    step_verify_locks()

    # Step 2: Ingest from sources
    step_ingest(args.profile)

    # Step 3-5: SourceAdapter imports (Phase 1 — no direct-DB seeders)
    step_import_curated(args.profile)
    step_import_kintech(args.profile)
    step_import_templates(args.profile)

    # Step 6: Optimize
    step_optimize()

    # Step 7: Compute canonical hash + counts (canonical content hash, not DB binary)
    canonical_hash, counts = step_export_hash()

    # Step 8: Write manifest
    db_sha256 = calculate_file_sha256(DB_PATH)
    step_write_manifest(args.profile, db_sha256, canonical_hash, counts)

    # Step 9: Verify
    ok = step_verify_manifest()
    if not ok:
        log("ERROR: Post-build verification failed!")
        sys.exit(1)

    # Step 10: V0.7 Phase 4 — Generate Runtime Executable Catalog Report
    try:
        import catalog_executable_report
        catalog_executable_report.main()
    except Exception as e:
        log(f"WARNING: Failed to generate executable report: {e}")

    log(f"Catalog build complete: {counts.get('code_sets', 0)} code sets, "
        f"{counts.get('signals', 0)} signals, {counts.get('command_bindings', 0)} bindings")


if __name__ == "__main__":
    main()
