#!/usr/bin/env python3
"""
Elysium Nexus — Canonical IR Catalog Exporter & Hasher
======================================================
Exports all logical entities of ir_catalog.db in deterministic order
(NFKC normalized, locale-independent, rowid-independent) and computes
the true logical `canonicalContentSha256`.
"""

import hashlib
import json
import sqlite3
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = OUTPUT_DIR / "ir_catalog.db"

def compute_canonical_hash(db_path: Path) -> tuple[str, dict]:
    conn = sqlite3.connect(str(db_path))
    cur = conn.cursor()

    hasher = hashlib.sha256()

    tables = [
        "sources", "source_revisions", "source_files",
        "brands", "device_types", "device_models", "remotes",
        "code_sets", "actions", "signals", "command_bindings",
        "code_set_models", "device_families", "protocol_definitions",
        "protocol_variants", "compatibility_assertions",
        "physical_test_evidence", "catalog_rejections", "signal_sources"
    ]

    entity_counts = {}

    for table in tables:
        cur.execute(f"SELECT * FROM {table}")
        col_names = [description[0] for description in cur.description]
        rows = cur.fetchall()
        entity_counts[table] = len(rows)

        # Sort rows deterministically by primary key or first column
        sorted_rows = sorted(rows, key=lambda r: tuple(str(x) for x in r))

        for row in sorted_rows:
            row_dict = {
                k: (v.hex() if isinstance(v, bytes) else (unicodedata.normalize("NFKC", v) if isinstance(v, str) else v))
                for k, v in zip(col_names, row)
            }
            # Format JSON deterministically
            row_json = json.dumps(row_dict, sort_keys=True, ensure_ascii=False)
            hasher.update(row_json.encode("utf-8"))

    conn.close()
    canonical_hash = hasher.hexdigest()
    return canonical_hash, entity_counts

if __name__ == "__main__":
    if not DB_PATH.exists():
        print(f"ERROR: Database file not found at {DB_PATH}")
        sys.exit(1)

    c_hash, counts = compute_canonical_hash(DB_PATH)
    print(f"Canonical Content SHA-256: {c_hash}")
    print(f"Entity Counts: {json.dumps(counts, indent=2)}")
