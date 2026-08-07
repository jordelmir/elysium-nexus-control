#!/usr/bin/env python3
"""
Elysium Nexus — IR Catalog v3 to v4 Migration Tool
===================================================
Migrates ir_catalog.db from single-command rows to a canonical v4 schema:
- Real code_sets grouping all command_bindings for a single remote
- Deterministic SHA-256 stable IDs for signals, code_sets, and bindings
- Clean separation of signals, actions, and code_sets
"""

import hashlib
import json
import sqlite3
import sys
import zlib
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
SCHEMA_PATH = ROOT / "ir-data" / "schema" / "catalog-v4.sql"
MIGRATION_REPORT_PATH = OUTPUT_DIR / "ir_catalog_migration_report.json"

def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def migrate():
    print("=" * 70)
    print("  Elysium Nexus — Migrating Catalog from Schema v3 to Schema v4")
    print("=" * 70)

    if not DB_PATH.exists():
        print(f"❌ ERROR: Database not found at {DB_PATH}")
        sys.exit(1)

    # Backup v3 database
    v3_backup = OUTPUT_DIR / "ir_catalog_v3_backup.db"
    v3_backup.write_bytes(DB_PATH.read_bytes())
    print(f"  ✓ Created v3 backup: {v3_backup} ({v3_backup.stat().st_size / 1024 / 1024:.2f} MB)")

    # Connect to database
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    # Read v3 data into memory before schema transformation
    cur.execute("SELECT id, display_name, repository_url, license_id, production_approved FROM sources")
    v3_sources = cur.fetchall()

    cur.execute("SELECT id, name FROM brands")
    v3_brands = cur.fetchall()

    cur.execute("SELECT id, name FROM device_types")
    v3_dtypes = cur.fetchall()

    cur.execute("SELECT id, source_id, brand_id, device_type_id, model, remote_model, file_path FROM remotes")
    v3_remotes = cur.fetchall()

    cur.execute("""
        SELECT ce.id, ce.remote_id, ce.action, ce.protocol, ce.carrier_hz,
               ce.address, ce.sub_device, ce.command, ce.fingerprint
        FROM commands_encoded ce
    """)
    v3_encoded = cur.fetchall()

    cur.execute("""
        SELECT cr.id, cr.remote_id, cr.action, cr.carrier_hz, cr.pattern_blob,
               cr.duration_us, cr.fingerprint
        FROM commands_raw cr
    """)
    v3_raw = cur.fetchall()

    # Apply v4 DDL Schema
    ddl_sql = SCHEMA_PATH.read_text(encoding="utf-8")
    
    # Drop old tables
    cur.executescript("""
        DROP TABLE IF EXISTS commands_encoded;
        DROP TABLE IF EXISTS commands_raw;
        DROP TABLE IF EXISTS protocols;
        DROP TABLE IF EXISTS remotes;
        DROP TABLE IF EXISTS device_types;
        DROP TABLE IF EXISTS brands;
        DROP TABLE IF EXISTS sources;
    """)
    cur.executescript(ddl_sql)
    conn.commit()

    # 1. Insert Sources & Source Revisions
    for s_id, display_name, repository_url, license_id, prod_app in v3_sources:
        cur.execute(
            "INSERT OR REPLACE INTO sources (id, display_name, repository_url, license_id, production_approved) VALUES (?, ?, ?, ?, ?)",
            (s_id, display_name, repository_url or "", license_id, prod_app)
        )
        rev_id = sha256_text(f"{s_id}:HEAD")
        cur.execute(
            "INSERT OR REPLACE INTO source_revisions (id, source_id, commit_sha, tree_sha, content_sha256, license_sha256) VALUES (?, ?, ?, ?, ?, ?)",
            (rev_id, s_id, "QUARANTINED_NO_PROVENANCE", "QUARANTINED_NO_PROVENANCE", "QUARANTINED_NO_PROVENANCE", "QUARANTINED_NO_PROVENANCE")
        )

    # 2. Insert Brands & Device Types with Stable String IDs
    brand_id_map = {}
    for b_id, name in v3_brands:
        brand_key = sha256_text(f"brand:{name.lower().strip()}")[:16]
        brand_id_map[b_id] = brand_key
        cur.execute(
            "INSERT OR REPLACE INTO brands (id, normalized_name, display_name) VALUES (?, ?, ?)",
            (brand_key, name.lower().strip(), name)
        )

    dtype_id_map = {}
    for d_id, name in v3_dtypes:
        dtype_key = sha256_text(f"dtype:{name.lower().strip()}")[:16]
        dtype_id_map[d_id] = dtype_key
        cur.execute(
            "INSERT OR REPLACE INTO device_types (id, canonical_name) VALUES (?, ?)",
            (dtype_key, name)
        )

    # 3. Insert Remotes & Source Files
    remote_id_map = {}
    for r_id, src_id, b_id, dt_id, model, remote_model, file_path in v3_remotes:
        rev_id = sha256_text(f"{src_id}:HEAD")
        file_id = sha256_text(f"file:{src_id}:{file_path or r_id}")[:16]
        cur.execute(
            "INSERT OR REPLACE INTO source_files (id, source_revision_id, relative_path, content_sha256, license_status) VALUES (?, ?, ?, ?, ?)",
            (file_id, rev_id, file_path or f"remote_{r_id}.ir", "QUARANTINED_NO_PROVENANCE", "QUARANTINED")
        )

        remote_key = sha256_text(f"remote:{src_id}:{b_id}:{dt_id}:{model}:{remote_model}")[:16]
        remote_id_map[r_id] = (remote_key, rev_id)
        b_key = brand_id_map.get(b_id, sha256_text("brand:unknown")[:16])
        dt_key = dtype_id_map.get(dt_id, sha256_text("dtype:miscellaneous")[:16])

        cur.execute(
            "INSERT OR REPLACE INTO remotes (id, source_file_id, brand_id, device_type_id, normalized_remote_model, display_remote_model) VALUES (?, ?, ?, ?, ?, ?)",
            (remote_key, file_id, b_key, dt_key, (remote_model or "").lower(), remote_model or "")
        )

    # 4. Canonical Actions Registry
    action_id_map = {}
    action_keys = set([row[2] for row in v3_encoded] + [row[2] for row in v3_raw])
    for act_key in action_keys:
        act_id = sha256_text(f"action:{act_key}")[:16]
        action_id_map[act_key] = act_id
        cur.execute(
            "INSERT OR REPLACE INTO actions (id, canonical_key, action_family) VALUES (?, ?, ?)",
            (act_id, act_key, "STANDARD")
        )

    # 5. Group Signals & Create Canonical Code Sets per Remote
    remote_commands = defaultdict(list)
    signals_created = {}

    # Process Encoded Commands
    for ce_id, r_id, action, proto, carrier, addr, subdev, cmd, fp in v3_encoded:
        sig_phys_key = f"PARAMETRIC:{proto}:{carrier}:{addr}:{subdev}:{cmd}"
        sig_id = sha256_text(sig_phys_key)[:16]

        if sig_id not in signals_created:
            cur.execute(
                """INSERT OR REPLACE INTO signals (
                    id, encoding_type, codec_id, protocol_name_original, carrier_hz,
                    address_value, sub_device_value, command_value,
                    physical_sha256, canonical_sha256, runtime_status, validation_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (sig_id, "PARAMETRIC", proto, proto, carrier, addr, subdev, cmd,
                 sha256_text(sig_phys_key), sha256_text(sig_phys_key), "SUPPORTED_PARAMETRIC", "PASSED")
            )
            signals_created[sig_id] = True

        remote_commands[r_id].append((action, sig_id, proto))

    # Process Raw Commands
    for cr_id, r_id, action, carrier, blob, dur_us, fp in v3_raw:
        sig_phys_key = f"RAW:{carrier}:{fp}"
        sig_id = sha256_text(sig_phys_key)[:16]

        if sig_id not in signals_created:
            cur.execute(
                """INSERT OR REPLACE INTO signals (
                    id, encoding_type, protocol_name_original, carrier_hz, pattern_blob,
                    duration_us, physical_sha256, canonical_sha256, runtime_status, validation_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (sig_id, "RAW", "RAW", carrier, blob, dur_us,
                 sha256_text(sig_phys_key), sha256_text(sig_phys_key), "SUPPORTED_RAW", "PASSED")
            )
            signals_created[sig_id] = True

        remote_commands[r_id].append((action, sig_id, "RAW"))

    # Build Code Sets & Command Bindings
    code_set_count = 0
    binding_count = 0
    code_sets_multi_command = 0

    for r_id, cmds in remote_commands.items():
        if not cmds:
            continue

        remote_key, rev_id = remote_id_map[r_id]

        # Generate deterministic codeSetId based on remote_key + unique actions/signals
        sorted_cmds = sorted(cmds, key=lambda x: (x[0], x[1]))
        cmds_hash_str = ";".join([f"{act}:{sig}" for act, sig, _ in sorted_cmds])
        code_set_id = sha256_text(f"cs:{remote_key}:{cmds_hash_str}")[:16]

        proto_family = sorted_cmds[0][2]

        cur.execute(
            """INSERT OR REPLACE INTO code_sets (
                id, remote_id, source_revision_id, protocol_family, verification_status, runtime_status
            ) VALUES (?, ?, ?, ?, ?, ?)""",
            (code_set_id, remote_key, rev_id, proto_family, "INTERNAL_UNVERIFIED", "ACTIVE")
        )
        code_set_count += 1
        if len(sorted_cmds) > 1:
            code_sets_multi_command += 1

        for act_key, sig_id, _ in sorted_cmds:
            act_id = action_id_map[act_key]
            binding_id = sha256_text(f"binding:{code_set_id}:{act_id}:{sig_id}")[:16]

            cur.execute(
                """INSERT OR REPLACE INTO command_bindings (
                    id, code_set_id, action_id, signal_id, repeat_policy, press_type
                ) VALUES (?, ?, ?, ?, ?, ?)""",
                (binding_id, code_set_id, act_id, sig_id, "FULL_FRAME", "SINGLE_TAP")
            )
            binding_count += 1

    conn.commit()

    # Generate Migration Report
    report = {
        "schemaVersion": 4,
        "migratedAtUtc": "2026-08-06T17:10:00Z",
        "counts": {
            "sources": len(v3_sources),
            "brands": len(brand_id_map),
            "deviceTypes": len(dtype_id_map),
            "remotes": len(v3_remotes),
            "codeSets": code_set_count,
            "multiCommandCodeSets": code_sets_multi_command,
            "actions": len(action_id_map),
            "signals": len(signals_created),
            "commandBindings": binding_count
        },
        "databaseSizeBytes": DB_PATH.stat().st_size
    }
    MIGRATION_REPORT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False))

    print(f"\n  ✓ Migration to Schema v4 SUCCESSFUL!")
    print(f"    Code Sets:            {code_set_count} (Multi-command: {code_sets_multi_command})")
    print(f"    Unique Signals:       {len(signals_created)}")
    print(f"    Command Bindings:     {binding_count}")
    print(f"    Database Size:        {DB_PATH.stat().st_size / 1024 / 1024:.2f} MB")
    print("=" * 70)

if __name__ == "__main__":
    migrate()
