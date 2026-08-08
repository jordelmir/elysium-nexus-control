#!/usr/bin/env python3
"""
Elysium Nexus — Seed Kintech into the native Schema v4 catalog
===============================================================
Idempotently plants the curated Kintech TV brand + remote + NEC
code set into ir_catalog.db, mirroring the exact pattern used by
existing brands (e.g. Sankey) so the authoritative resolution path
(§7 IrCatalogRepository / IrConnectFlow) can find it.

JSON source of truth: apps/android/app/src/main/assets/ir_codes_db.json
Uses the same deterministic ID derivation as build_v4_catalog.py so the
result is reproducible and stable across clean rebuilds.
"""

import hashlib
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
DB_PATH = ROOT / "apps/android/app/src/main/assets/ir/ir_catalog.db"
JSON_PATH = ROOT / "apps/android/app/src/main/assets/ir_codes_db.json"

BRAND = "Kintech"
BRAND_NORM = "kintech"
SOURCE_ID = "elysium-nexus-curated"
SOURCE_REV = "e28f0a7d8957c8e03f13c0756d10b6a9c6b2024f04b8a32f6c2e74da0011f9db8"
SOURCE_COMMIT = "curated-kintech-v1"

def sha256t(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()

def short_sha(s: str) -> str:
    return sha256t(s)[:16]

def parse_nec(hex_code: str):
    """Parse '0xHHLL' full NEC hex 0xHHLL to (address, command)."""
    v = int(hex_code.strip(), 16) & 0xFFFFFFFF
    address = (v >> 24) & 0xFF
    command = (v >> 8) & 0xFF
    return address, command

# ─── Map curated JSON command names to the canonical action keys used
#     by IrAction (the set the resolver will actually surface). Keys
#     that map to no canonical IrAction are skipped with a warning.
CANONICAL_MAP = {
    "POWER": "POWER_TOGGLE",
    "VOL_UP": "VOLUME_UP",
    "VOL_DOWN": "VOLUME_DOWN",
    "MUTE": "MUTE",
    "CH_UP": "CHANNEL_UP",
    "CH_DOWN": "CHANNEL_DOWN",
    "INPUT_SOURCE": "INPUT",
    "MENU": "MENU",
    "UP": "UP",
    "DOWN": "DOWN",
    "LEFT": "LEFT",
    "RIGHT": "RIGHT",
    "ENTER": "OK",
    "BACK": "BACK",
    "HOME": "HOME",
    "INFO": "INFO",
    "PLAY_PAUSE": "PLAY",
    "PLAY": "PLAY",
    "PAUSE": "PAUSE",
}

def main() -> int:
    if not DB_PATH.exists():
        print(f"ERROR: catalog db not found at {DB_PATH}")
        return 1

    # ── Load the curated JSON codeset for Kintech ──────────────────
    with open(JSON_PATH, encoding="utf-8") as fh:
        jdb = json.load(fh)
    kintech_brand = None
    for b in jdb.get("brands", []):
        if b.get("id", "").strip().lower() == BRAND_NORM:
            kintech_brand = b
            break
    if kintech_brand is None:
        print(f"ERROR: brand {BRAND_NORM} not found in {JSON_PATH.name}")
        return 1

    category = None
    for cat in kintech_brand.get("categories", []):
        if cat.get("type", "").upper() == "TV":
            category = cat
            break
    if category is None and kintech_brand.get("categories"):
        category = kintech_brand["categories"][0]
    if category is None or not category.get("codesets"):
        print(f"ERROR: no TV codeset for {BRAND}")
        return 1

    codeset = category["codesets"][0]
    commands = codeset.get("commands", {})
    carrier = codeset.get("frequency_hz", 38000)
    proto = codeset.get("protocol", "NEC").upper()

    mapped = {}
    skipped = []
    for raw_key, hex_code in commands.items():
        canonical = CANONICAL_MAP.get(raw_key)
        if canonical is None:
            skipped.append(raw_key)
            continue
        addr, cmd = parse_nec(hex_code)
        mapped[canonical] = (addr, cmd)

    if "VOLUME_UP" not in mapped or "MUTE" not in mapped:
        print("ERROR: Kintech set lacks required VOLUME_UP/MUTE binding")
        return 1
    print(f"  mapped       : {len(mapped)} canonical actions")
    print(f"  skipped      : {skipped} (no canonical IrAction)")

    # ── SQLite ─────────────────────────────────────────────────
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    # Source
    cur.execute(
        "INSERT OR IGNORE INTO sources (id, display_name, repository_url, license_id, production_approved) "
        "VALUES (?, ?, ?, ?, 1)",
        (SOURCE_ID, "Elysium Nexus Curated TV", "", "MIT"),)
    cur.execute(
        "INSERT OR IGNORE INTO source_revisions (id, source_id, commit_sha, tree_sha, content_sha256, license_sha256) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (SOURCE_REV, SOURCE_ID, SOURCE_COMMIT, SOURCE_COMMIT,
         sha256t(JSON_PATH.read_bytes().hex()), "0" * 64))

    # Source file row (page model used by remotes.source_file_id)
    file_id = short_sha(f"file:{SOURCE_ID}:kintech_smart_tv.ir")
    cur.execute(
        "INSERT OR IGNORE INTO source_files (id, source_revision_id, relative_path, blob_sha, content_sha256, introduced_commit, last_modified_commit, license_status, rejection_reason) "
        "VALUES (?, ?, ?, NULL, ?, ?, ?, 'APPROVED', NULL)",
        (file_id, SOURCE_REV, "Kintech/kintech_smart_tv.json",
         sha256t(json.dumps(commands, sort_keys=True)),
         SOURCE_COMMIT, SOURCE_COMMIT))

    # Brand
    b_id = short_sha(f"brand:{BRAND_NORM}")
    cur.execute(
        "INSERT OR IGNORE INTO brands (id, normalized_name, display_name) VALUES (?, ?, ?)",
        (b_id, BRAND_NORM, BRAND))

    # Device type — reuse existing canonical 'Tv' if present
    cur.execute("SELECT id FROM device_types WHERE canonical_name = 'TV'")
    t_row = cur.fetchone()
    if t_row is None:
        cur.execute("SELECT id FROM device_types WHERE canonical_name = 'Tv'")
        t_row = cur.fetchone()
    if t_row is None:
        t_type_id = short_sha("dtype:tv")
        cur.execute("INSERT OR IGNORE INTO device_types (id, canonical_name) VALUES (?, ?)", (t_type_id, "Tv"))
    else:
        t_type_id = t_row[0]

    # Remote
    r_id = short_sha(f"remote:{SOURCE_ID}:{b_id}:{t_type_id}:kintech_smart_tv")
    cur.execute(
        "INSERT OR IGNORE INTO remotes (id, source_file_id, brand_id, device_type_id, normalized_remote_model, display_remote_model, region) "
        "VALUES (?, ?, ?, ?, 'kintech_smart_tv', 'Kintech Smart / LED TV', NULL)",
        (r_id, file_id, b_id, t_type_id))

    # Code set
    cs_id = short_sha(f"cs:{SOURCE_ID}:{r_id}:{len(mapped) + 1}")
    cur.execute(
        "INSERT OR IGNORE INTO code_sets (id, remote_id, source_revision_id, protocol_family, protocol_variant, region, verification_status, runtime_status) "
        "VALUES (?, ?, ?, ?, ?, NULL, 'INTERNAL_UNVERIFIED', 'ACTIVE')",
        (cs_id, r_id, SOURCE_REV, proto, "NEC_32"))

    # Signals + bindings — deterministic ids identical to builder shape
    sig_ids = {}
    for canonical, (addr, cmd) in sorted(mapped.items()):
        sig_key = f"PARAMETRIC:{proto}:{carrier}:{addr}:{cmd}"
        sig_id = short_sha(sig_key)
        signature = sha256t(sig_key)
        try:
            cur.execute(
                "INSERT OR IGNORE INTO signals (id, encoding_type, codec_id, protocol_name_original, protocol_variant, carrier_hz, address_value, sub_device_value, command_value, repeat_count, toggle_policy, pattern_blob, compression, slice_count, duration_us, uncompressed_bytes, physical_sha256, canonical_sha256, runtime_status, validation_status, rejection_reason) "
                "VALUES (?, 'PARAMETRIC', 'NEC', ?, ?, ?, ?, NULL, ?, 0, NULL, NULL, NULL, NULL, NULL, NULL, ?, ?, 'SUPPORTED_PARAMETRIC', 'PASSED', NULL)",
                (sig_id, proto, proto, str(carrier),
                 addr, cmd, signature, signature))
        except sqlite3.Error as e:
            print(f"  signal insert error: {e}")
            conn.rollback()
            return 1
        sig_ids[canonical] = sig_id
        # Canonical action id
        act_id = short_sha(f"action:{canonical}")
        cur.execute("INSERT OR IGNORE INTO actions (id, canonical_key, action_family) VALUES (?, ?, 'STANDARD')", (act_id, canonical))
        bnd_id = short_sha(f"b:{cs_id}:{act_id}:{sig_id}")
        cur.execute(
            "INSERT OR IGNORE INTO command_bindings (id, code_set_id, action_id, signal_id, repeat_policy, press_type, source_priority) "
            "VALUES (?, ?, ?, ?, 'FULL_FRAME', 'SINGLE_TAP', 0)",
            (bnd_id, cs_id, act_id, sig_id))

    conn.commit()

    cur.execute("SELECT count(*) FROM command_bindings WHERE code_set_id=?", (cs_id,))
    bound = cur.fetchone()[0]
    conn.close()

    print(f"  brand        : {BRAND} (id={b_id})")
    print(f"  code_set     : {cs_id} ({proto} {carrier}Hz)")
    print(f"  signals      : {len(sig_ids)} inserted")
    print(f"  bindings     : {bound} bound to code set")
    print("  ✓ Kintech seeded into Schema v4 catalog")
    return 0

if __name__ == "__main__":
    sys.exit(main())
