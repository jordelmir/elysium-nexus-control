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
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TOOLS_DIR = ROOT / "tools" / "ir-data"
OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
MANIFEST_PATH = OUTPUT_DIR / "ir_catalog.manifest.json"
REJECTIONS_PATH = OUTPUT_DIR / "ir_catalog_rejections.json"

sys.path.insert(0, str(TOOLS_DIR))


def calculate_file_sha256(file_path: Path) -> str:
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


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
        log(f"WARNING: Source lock verification failed: {e}")
        log("Continuing with existing checkouts (may produce inconsistent build).")


def step_ingest(profile: str):
    """Step 2: Ingest from all authorized sources."""
    log(f"Ingesting sources (profile={profile})...")
    import ingest_all
    ingest_all.run_ingestion(profile=profile)
    log("Ingestion complete.")


def step_seed_brands():
    """Step 3: Seed curated brands from ir_codes_db.json."""
    log("Seeding curated brands...")
    import seed_curated_brands_v4
    seed_curated_brands_v4.seed()
    log("Curated brands seeded.")


def step_seed_templates():
    """Step 4: Seed DeviceTemplate-based TV brands."""
    log("Seeding device templates...")
    import seed_templates_v4
    seed_templates_v4.seed()
    log("Device templates seeded.")


def step_seed_kintech():
    """Step 5: Seed Kintech brand."""
    log("Seeding Kintech...")
    import seed_kintech_v4
    seed_kintech_v4.seed()
    log("Kintech seeded.")


def step_optimize():
    """Step 6: Optimize catalog (orphan cleanup, RAW validation, VACUUM)."""
    log("Optimizing catalog...")
    import optimize_catalog
    optimize_catalog.optimize()
    log("Optimization complete.")


def step_export_hash():
    """Step 7: Export canonical hash and entity counts."""
    log("Computing canonical hash...")
    import export_canonical_catalog
    export_canonical_catalog.export()
    log("Canonical hash computed.")


def step_write_manifest(profile: str, db_sha256: str, canonical_hash: str, counts: dict):
    """Step 8: Write ir_catalog.manifest.json."""
    manifest = {
        "schemaVersion": 4,
        "profile": profile,
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "pipelineVersion": "0.5.0-unified-catalog",
        "databaseSha256": db_sha256,
        "canonicalContentSha256": canonical_hash,
        "databaseSizeBytes": DB_PATH.stat().st_size,
        "counts": counts
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    log(f"Manifest written: {MANIFEST_PATH}")


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
    return True


def step_count_entities() -> dict:
    """Count entities in the catalog for the manifest."""
    conn = sqlite3.connect(str(DB_PATH))
    try:
        counts = {}
        for table in ["sources", "source_revisions", "source_files", "brands",
                       "device_types", "device_models", "remotes", "code_sets",
                       "actions", "signals", "command_bindings", "code_set_models"]:
            try:
                counts[table] = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            except sqlite3.OperationalError:
                counts[table] = 0
        return counts
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(description="Elysium Nexus — Unified IR Catalog Builder")
    parser.add_argument("--profile", choices=["production", "research"], default="production",
                        help="Build profile: production (approved sources) or research (all sources)")
    parser.add_argument("--verify-only", action="store_true",
                        help="Only verify existing catalog against manifest")
    parser.add_argument("--seed-templates", action="store_true",
                        help="Only run DeviceTemplate seeder")
    parser.add_argument("--seed-brands", action="store_true",
                        help="Only run curated brands seeder")
    parser.add_argument("--seed-kintech", action="store_true",
                        help="Only run Kintech seeder")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Verify-only mode
    if args.verify_only:
        ok = step_verify_manifest()
        sys.exit(0 if ok else 1)

    # Individual seed modes
    if args.seed_templates:
        step_seed_templates()
        return
    if args.seed_brands:
        step_seed_brands()
        return
    if args.seed_kintech:
        step_seed_kintech()
        return

    # Full build pipeline
    log(f"Starting full catalog build (profile={args.profile})")

    # Step 1: Verify source locks
    step_verify_locks()

    # Step 2: Ingest from sources
    step_ingest(args.profile)

    # Step 3-5: Seed curated data
    step_seed_brands()
    step_seed_templates()
    step_seed_kintech()

    # Step 6: Optimize
    step_optimize()

    # Step 7: Compute canonical hash + counts
    counts = step_count_entities()

    # Step 8: Write manifest
    db_sha256 = calculate_file_sha256(DB_PATH)
    # Canonical hash is computed by export_canonical_catalog
    # For now we use the DB sha256 as a proxy
    step_write_manifest(args.profile, db_sha256, db_sha256, counts)

    # Step 9: Verify
    ok = step_verify_manifest()
    if not ok:
        log("ERROR: Post-build verification failed!")
        sys.exit(1)

    log(f"Catalog build complete: {counts.get('code_sets', 0)} code sets, "
        f"{counts.get('signals', 0)} signals, {counts.get('command_bindings', 0)} bindings")


if __name__ == "__main__":
    main()
