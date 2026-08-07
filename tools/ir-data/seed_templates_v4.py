#!/usr/bin/env python3
"""
Elysium Nexus — Seed DeviceTemplate TV catalog into Schema v4
=============================================================
Idempotently plants every TV template from DeviceTemplate.kt
(Sankey x4, Control Universal, Kintech, Kalley, Challenger,
Daewoo, Hyundai, Samsung, LG, Sony, Panasonic, Philips, TCL,
Hisense) as a real parametric NEC code set in ir_catalog.db.

Why: the IR connect flow is SQLite-only (zero template fallback).
The four Sankey templates existed in code with distinct NEC
addresses (0x00/0x04/0x08/0x40) but the DB only carried one
flipper-irdb Sankey set (NEC 0x20). The user's TV answered a
template address, not the flipper one -> "Sankey dejó de servir".
Seeding the templates restores all variants as DB-backed
candidates with real signal IDs (persistable + resolvable).

Deterministic IDs identical in shape to build_v4_catalog.py and
seed_kintech_v4.py so clean rebuilds reproduce byte-identical DBs.
"""

import hashlib
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
DB_PATH = ROOT / "apps/android/app/src/main/assets/ir/ir_catalog.db"

SOURCE_ID = "elysium-nexus-curated"
SOURCE_REV = "c9b1f3e8a1d2c4f6b8a0e2d4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2"
SOURCE_COMMIT = "curated-templates-v1"

# ── Template table mirrored from DeviceTemplate.kt (TV category) ──
# (template_id, brand, display_model, protocol, device_address, command_address)
TEMPLATES = [
    ("tv-universal-generic", "Control Universal TV", "Multimarca (Todas las marcas)", "NEC", 0x00, 0x00),
    ("tv-kintech-generic", "Kintech", "Generic (Smart / LED)", "NEC", 0x00, 0x12),
    ("tv-sankey-generic", "Sankey", "Generic (Smart / LED TV)", "NEC", 0x00, 0x00),
    ("tv-sankey-smart", "Sankey", "Smart TV (Android OS Series)", "NEC", 0x04, 0x12),
    ("tv-sankey-uhd", "Sankey", "4K UHD Smart TV (Series C / S)", "NEC", 0x08, 0x02),
    ("tv-sankey-curved", "Sankey", "Curved & Frameless LED TV", "NEC", 0x40, 0x1A),
    ("tv-kalley-generic", "Kalley", "Generic (Smart / LED)", "NEC", 0x04, 0x08),
    ("tv-challenger-generic", "Challenger", "Generic (Smart / LED)", "NEC", 0x04, 0x12),
    ("tv-daewoo-generic", "Daewoo", "Generic (Smart / LED)", "NEC", 0x02, 0x10),
    ("tv-hyundai-generic", "Hyundai", "Generic (Smart / LED)", "NEC", 0x04, 0x14),
    ("tv-samsung-generic", "Samsung", "Generic (2010+)", "Samsung", 0x07, 0x02),
    ("tv-lg-generic", "LG", "Generic (Smart / LED)", "NEC", 0x04, 0x08),
    ("tv-sony-generic", "Sony", "Generic (2010+)", "SIRC", 0x01, 0x0A),
    ("tv-panasonic-generic", "Panasonic", "Generic (Smart / LED)", "Kaseikyo", 0x40, 0x01),
    ("tv-philips-generic", "Philips", "Generic (RC5)", "RC5", 0x05, 0x0C),
    ("tv-tcl-generic", "TCL", "Generic (Smart / LED)", "NEC", 0x04, 0x12),
    ("tv-hisense-generic", "Hisense", "Generic (Smart / LED)", "NEC", 0x04, 0xF2),
]

# ── TV_BUTTONS command codes (from DeviceTemplate.kt) ──
# id -> (canonical action key, command code)
BUTTONS = [
    ("power", "POWER_TOGGLE", 0x02),
    ("input", "INPUT", 0x0B),
    ("vol_up", "VOLUME_UP", 0x07),
    ("ch_up", "CHANNEL_UP", 0x12),
    ("mute", "MUTE", 0x09),
    ("up", "UP", 0x0E),
    ("ok", "OK", 0x0D),
    ("ch_down", "CHANNEL_DOWN", 0x10),
    ("menu", "MENU", 0x1A),
    ("left", "LEFT", 0x0F),
    ("right", "RIGHT", 0x11),
    ("down", "DOWN", 0x0C),
    ("back", "BACK", 0x1B),
    ("vol_down", "VOLUME_DOWN", 0x0A),
    ("info", "INFO", 0x1C),
    ("last", "LAST", 0x14),
    ("home", "HOME", 0x20),
]

# Protocols that the parametric encoder supports with a canonical carrier.
PROTO_CARRIER = {
    "NEC": 38000,
    "Samsung": 38000,
    "SIRC": 40000,
    "Kaseikyo": 38000,
    "RC5": 36000,
}


def sha256t(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def short_sha(s: str) -> str:
    return sha256t(s)[:16]


def main() -> int:
    if not DB_PATH.exists():
        print(f"ERROR: catalog db not found at {DB_PATH}")
        return 1

    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    # ── Source (template-derived, NOT production-approved) ──
    # Templates are protocol-knowledge-based guesses, not HIL-verified signals.
    # production_approved=0 keeps them out of the universal sweep until physical
    # capture证明 they actually work on real hardware.
    cur.execute(
        "INSERT OR IGNORE INTO sources (id, display_name, repository_url, license_id, production_approved) "
        "VALUES (?, ?, ?, ?, 0)",
        (SOURCE_ID, "Elysium Nexus Curated TV (Templates)", "", "MIT"))
    cur.execute(
        "INSERT OR IGNORE INTO source_revisions (id, source_id, commit_sha, tree_sha, content_sha256, license_sha256) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (SOURCE_REV, SOURCE_ID, SOURCE_COMMIT, SOURCE_COMMIT,
         sha256t("template-catalog-v1"), "0" * 64))

    # Device type — reuse canonical TV
    cur.execute("SELECT id FROM device_types WHERE canonical_name = 'TV' OR canonical_name = 'Tv' ORDER BY canonical_name LIMIT 1")
    t_row = cur.fetchone()
    if t_row is None:
        t_type_id = short_sha("dtype:tv")
        cur.execute("INSERT OR IGNORE INTO device_types (id, canonical_name) VALUES (?, ?)", (t_type_id, "Tv"))
    else:
        t_type_id = t_row[0]

    total_sets = 0
    total_signals = 0

    for template_id, brand, model, proto, device_addr, cmd_addr in TEMPLATES:
        norm = brand.strip().lower().replace(" ", "_")

        # Brand — reuse if a display-name match already exists (flipper Sankey)
        cur.execute("SELECT id FROM brands WHERE display_name = ? OR normalized_name = ? LIMIT 1", (brand, norm))
        b_row = cur.fetchone()
        if b_row is not None:
            b_id = b_row[0]
        else:
            b_id = short_sha(f"brand:{norm}")
            cur.execute("INSERT OR IGNORE INTO brands (id, normalized_name, display_name) VALUES (?, ?, ?)",
                        (b_id, norm, brand))

        # Source file
        file_id = short_sha(f"file:{SOURCE_ID}:{template_id}.ir")
        cur.execute(
            "INSERT OR IGNORE INTO source_files (id, source_revision_id, relative_path, blob_sha, content_sha256, introduced_commit, last_modified_commit, license_status, rejection_reason) "
            "VALUES (?, ?, ?, NULL, ?, ?, ?, 'APPROVED', NULL)",
            (file_id, SOURCE_REV, f"Templates/{template_id}.json",
             sha256t(f"{template_id}:{proto}:{device_addr:x}:{cmd_addr:x}"),
             SOURCE_COMMIT, SOURCE_COMMIT))

        # Remote
        r_id = short_sha(f"remote:{SOURCE_ID}:{b_id}:{t_type_id}:{template_id}")
        cur.execute(
            "INSERT OR IGNORE INTO remotes (id, source_file_id, brand_id, device_type_id, normalized_remote_model, display_remote_model, region) "
            "VALUES (?, ?, ?, ?, ?, ?, NULL)",
            (r_id, file_id, b_id, t_type_id, template_id, model))

        # Code set
        cs_id = short_sha(f"cs:{SOURCE_ID}:{r_id}:{proto}")
        cur.execute(
            "INSERT OR IGNORE INTO code_sets (id, remote_id, source_revision_id, protocol_family, protocol_variant, region, verification_status, runtime_status) "
            "VALUES (?, ?, ?, ?, ?, NULL, 'UNVERIFIED', 'ACTIVE')",
            (cs_id, r_id, SOURCE_REV, proto, f"{proto}_32"))

        carrier = PROTO_CARRIER.get(proto, 38000)
        bound_count = 0
        for _bid, canonical, cmd in BUTTONS:
            sig_key = f"PARAMETRIC:{proto}:{carrier}:{device_addr}:{cmd}"
            sig_id = short_sha(sig_key)
            signature = sha256t(sig_key)
            cur.execute(
                "INSERT OR IGNORE INTO signals (id, encoding_type, codec_id, protocol_name_original, protocol_variant, carrier_hz, address_value, sub_device_value, command_value, repeat_count, toggle_policy, pattern_blob, compression, slice_count, duration_us, uncompressed_bytes, physical_sha256, canonical_sha256, runtime_status, validation_status, rejection_reason) "
                "VALUES (?, 'PARAMETRIC', ?, ?, ?, ?, ?, NULL, ?, 0, NULL, NULL, NULL, NULL, NULL, NULL, ?, ?, 'SUPPORTED_PARAMETRIC', 'PASSED', NULL)",
                (sig_id, proto, proto, str(carrier), carrier, device_addr, cmd, signature, signature))

            act_id = short_sha(f"action:{canonical}")
            cur.execute("INSERT OR IGNORE INTO actions (id, canonical_key, action_family) VALUES (?, ?, 'STANDARD')", (act_id, canonical))
            bnd_id = short_sha(f"b:{cs_id}:{act_id}:{sig_id}")
            cur.execute(
                "INSERT OR IGNORE INTO command_bindings (id, code_set_id, action_id, signal_id, repeat_policy, press_type, source_priority) "
                "VALUES (?, ?, ?, ?, 'FULL_FRAME', 'SINGLE_TAP', 1)",
                (bnd_id, cs_id, act_id, sig_id))
            bound_count += 1

        total_sets += 1
        total_signals += bound_count

    conn.commit()
    conn.close()

    print(f"  templates seeded : {total_sets} code sets")
    print(f"  signals/bindings : {total_signals}")
    print("  ✓ DeviceTemplate TV catalog planted into Schema v4")
    return 0


if __name__ == "__main__":
    sys.exit(main())
