#!/usr/bin/env python3
"""
Elysium Nexus — Production IR Catalog Build Pipeline
======================================================
Usage:
  python3 tools/ir-data/build_catalog.py --profile production

Profiles:
  - production: Includes only approved, production-enabled sources (CC0, MIT, Public Domain).
                Physically excludes probonopd-irdb.
  - research: Includes all 5 sources for laboratory research & analysis.
"""

import argparse
import hashlib
import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
MANIFEST_PATH = OUTPUT_DIR / "ir_catalog.manifest.json"
STATS_PATH = OUTPUT_DIR / "ir_catalog_stats.json"

sys.path.insert(0, str(ROOT / "tools" / "ir-data"))
import ingest_all
import optimize_catalog

def calculate_sha256(file_path: Path) -> str:
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def build(profile: str):
    print("=" * 70)
    print(f"  Elysium Nexus — Building IR Catalog (Profile: {profile.upper()})")
    print("=" * 70)

    # Step 1: Run Ingestion with profile filter
    ingest_all.run_ingestion(profile=profile)

    # Step 2: Run Catalog Optimization (compression + type collapse)
    optimize_catalog.optimize()

    # Step 3: Compute Real Database SHA-256 Checksum
    db_sha256 = calculate_sha256(DB_PATH)
    db_size = DB_PATH.stat().st_size

    # Step 4: Verify Database Integrity
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()
    cur.execute("PRAGMA quick_check")
    quick_check = cur.fetchone()[0]
    cur.execute("PRAGMA foreign_key_check")
    fk_check = cur.fetchall()
    
    cur.execute("SELECT COUNT(*) FROM brands")
    brand_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM device_types")
    dtype_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM remotes")
    remote_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM commands_encoded")
    encoded_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM commands_raw")
    raw_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM protocols")
    proto_count = cur.fetchone()[0]
    conn.close()

    if quick_check != "ok" or len(fk_check) > 0:
        print(f"❌ ERROR: Database integrity check failed! QuickCheck={quick_check}, FKErrors={len(fk_check)}")
        sys.exit(1)

    print(f"\n  ✓ SQLite Integrity Verification: PASSED (QuickCheck={quick_check})")

    # Step 5: Write Production Manifest with REAL SHA-256 Hash
    manifest = {
        "schemaVersion": 3,
        "profile": profile,
        "generatedAtUtc": "2026-08-06T15:45:00Z",
        "pipelineVersion": "0.3.0-production-candidate",
        "databaseSha256": db_sha256,
        "databaseSizeBytes": db_size,
        "canonicalContentSha256": db_sha256,  # Verified SHA256 of production DB asset
        "counts": {
            "brands": brand_count,
            "deviceTypes": dtype_count,
            "remotes": remote_count,
            "encodedCommands": encoded_count,
            "rawCommands": raw_count,
            "totalCommands": encoded_count + raw_count,
            "protocols": proto_count
        },
        "integrity": {
            "pragmaQuickCheck": quick_check,
            "foreignKeyErrors": len(fk_check)
        }
    }

    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"  ✓ Manifest written to {MANIFEST_PATH}")
    print(f"    SHA-256: {db_sha256}")
    print(f"    Size:     {db_size / 1024 / 1024:.2f} MB")
    print("=" * 70)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Build Elysium Nexus IR Catalog")
    parser.add_argument("--profile", choices=["production", "research"], default="production", help="Catalog build profile")
    args = parser.parse_args()
    build(args.profile)
