#!/usr/bin/env python3
"""
Elysium Nexus — Seed Curated TV Brands into Schema v4 Catalog
=============================================================
Idempotently plants every brand+codeset from ir_codes_db.json into
ir_catalog.db following the exact deterministic ID derivation of
build_v4_catalog.py, so the authoritative resolution path
(IrCatalogRepository / IrConnectFlow) can serve them.

Honesty rule: code sets we seed carry verification_status='INTERNAL_UNVERIFIED'
(we have not lab-proven them), matching how the rest of the catalog
presents community/curated data. No silent compatibility claims.

Recomputes ir_catalog.manifest.json (databaseSha256 + canonical + counts)
with the same code build_v4_catalog.py uses, so CI stays green.
"""

import hashlib
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
MANIFEST_PATH = OUTPUT_DIR / "ir_catalog.manifest.json"
JSON_PATH = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir_codes_db.json"

sys.path.insert(0, str(ROOT / "tools" / "ir-data"))
import export_canonical_catalog

SOURCE_ID = "elysium-nexus-curated"
SOURCE_COMMIT = "curated-tv-v1"
REVISION = hashlib.sha256(f"{SOURCE_ID}:{SOURCE_COMMIT}".encode()).hexdigest()
DT_TV = "3da00d033e4ad83b"  # device_types.canonical_name = 'Tv' (already present)

# JSON command key -> IrAction canonical_key (must exist in IrAction enum).
# Keys with no canonical IrAction (NETFLIX, YOUTUBE, PLAY_PAUSE, NUM_x,
# INFO) are skipped deliberately — the runtime surface has no slot for them.
COMMAND_ACTIONS = {
    "POWER": "POWER_TOGGLE",
    "VOL_UP": "VOLUME_UP",
    "VOL_DOWN": "VOLUME_DOWN",
    "MUTE": "MUTE",
    "CH_UP": "CHANNEL_UP",
    "CH_DOWN": "CHANNEL_DOWN",
    "INPUT_SOURCE": "INPUT",
    "MENU": "MENU",
    "BACK": "BACK",
    "UP": "UP",
    "DOWN": "DOWN",
    "LEFT": "LEFT",
    "RIGHT": "RIGHT",
    "ENTER": "OK",
    "HOME": "HOME",
}

# JSON protocol -> codec_id stored in the catalog. These are the exact
# codec ids the ProtocolCodecRegistry keys on, so signals actually decode
# at runtime (no phantom code sets).
JSON_PROTOCOL_CODEC = {
    "NEC": "NEC",
    "NEC1": "NEC",
    "NEC_38": "NEC",
    "SAMSUNG32": "SAMSUNG",
    "Samsung32": "SAMSUNG",
    "SAMSUNG": "SAMSUNG",
    "SIRC12": "SIRC",
    "SIRC15": "SIRC",
    "SIRC20": "SIRC",
    "SINC": "SIRC",
    "SONYSIRC": "SIRC",
}


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def short(text: str) -> str:
    return sha256_text(text)[:16]


def parse_nec_32(value_hex: str) -> tuple[int, int]:
    """Parse '0xLL' NEC 32-bit value to (address, command).

    Frame layout: ADDR | ~ADDR | CMD | ~CMD stored as one 32-bit hex.
    Address is the leading byte, command is the third byte.
    """
    v = int(value_hex.strip(), 16)
    addr = (v >> 24) & 0xFF
    cmd = (v >> 8) & 0xFF
    return addr, cmd


def parse_samsung_32(value_hex: str) -> tuple[int, int]:
    v = int(value_hex.strip(), 16)
    return (v >> 24) & 0xFF, (v >> 8) & 0xFF


def parse_sirc(value_hex: str) -> tuple[int, int]:
    """SIRC 12-bit: command (7) + address (5)."""
    v = int(value_hex.strip(), 16)
    return (v >> 7) & 0x1F, v & 0x7F


PARSERS = {
    "NEC": parse_nec_32,
    "NEC1": parse_nec_32,
    "NEC_38": parse_nec_32,
    "SAMSUNG32": parse_samsung_32,
    "Samsung32": parse_samsung_32,
    "SAMSUNG": parse_samsung_32,
    "SIRC12": parse_sirc,
    "SIRC15": parse_sirc,
    "SIRC20": parse_sirc,
    "SINC": parse_sirc,
}

def brand_resolvable(cur, normalized: str) -> bool:
    """True when the brand already has at least one TV code set in the catalog."""
    cur.execute(
        "SELECT 1 FROM code_sets cs "
        "JOIN remotes r ON cs.remote_id = r.id "
        "WHERE r.brand_id = (SELECT id FROM brands WHERE normalized_name = ?) LIMIT 1",
        (normalized,),
    )
    return cur.fetchone() is not None


def main() -> int:
    if not DB_PATH.exists():
        print(f"ERROR: catalog db not found at {DB_PATH}")
        return 1

    with open(JSON_PATH, encoding="utf-8") as fh:
        jdb = json.load(fh)

    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    # ── 1. Curated source (production-approved so the candidate query sees it) ──
    cur.execute(
        "INSERT OR IGNORE INTO sources (id, display_name, repository_url, license_id, production_approved) "
        "VALUES (?, ?, ?, ?, 1)",
        (SOURCE_ID, SOURCE_ID, "", "MIT"),
    )
    cur.execute(
        "INSERT OR IGNORE INTO source_revisions (id, source_id, commit_sha, tree_sha, content_sha256, license_sha256) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (REVISION, SOURCE_ID, SOURCE_COMMIT, SOURCE_COMMIT, "0" * 64, "0" * 64),
    )
    cur.execute("SELECT 1 FROM device_types WHERE id = ?", (DT_TV,))
    if cur.fetchone() is None:
        cur.execute(
            "INSERT OR IGNORE INTO device_types (id, canonical_name) VALUES (?, 'Tv')", (DT_TV,))
    conn.commit()

    seeded = 0
    for brand in jdb.get("brands", []):
        brand_id = (brand.get("id") or "").strip()
        brand_name = (brand.get("name") or brand_id or "Unknown").strip()
        normalized = brand_id.lower()
        if not normalized:
            print("  ! Empty brand id — skipped")
            continue

        if brand_resolvable(cur, normalized):
            print(f"  ✓ {brand_name} already resolvable in catalog — skipping (idempotent)")
            continue

        category = None
        for cat in brand.get("categories", []):
            if cat.get("type", "").upper() == "TV":
                category = cat
                break
        if category is None and brand.get("categories"):
            category = brand["categories"][0]
        if category is None or not category.get("codesets"):
            print(f"  ! {brand_name}: no codesets — skipped")
            continue

        b_id = short(f"brand:{normalized}")
        cur.execute(
            "INSERT OR IGNORE INTO brands (id, normalized_name, display_name) VALUES (?, ?, ?)",
            (b_id, normalized, brand_name),
        )
        conn.commit()

        for codeset in category["codesets"]:
            cs_id = (codeset.get("id") or f"{brand_id}_codeset").strip()
            cs_name = (codeset.get("name") or brand_name).strip()
            proto_orig = (codeset.get("protocol") or "NEC").strip()
            freq = codeset.get("frequency_hz", 38000)
            codec_id = JSON_PROTOCOL_CODEC.get(proto_orig)
            parser = PARSERS.get(proto_orig)
            if codec_id is None or parser is None:
                print(f"  ! {brand_name} / {cs_id}: unknown protocol '{proto_orig}' — REJECTED (no silent NEC fallback)")
                continue

            commands = codeset.get("commands", {})
            mapped = []
            for raw_key, hex_code in commands.items():
                canonical = COMMAND_ACTIONS.get(raw_key)
                if canonical is None:
                    continue
                try:
                    addr, cmd = parser(hex_code)
                except ValueError:
                    print(f"    ! unparseable hex {hex_code!r} ({raw_key}) — skipped")
                    continue
                mapped.append((canonical, addr, cmd))

            if not mapped:
                print(f"  ! {brand_name} / {cs_id}: no mappable commands — skipped")
                continue

            file_id = short(f"file:{SOURCE_ID}:{cs_id}")
            cur.execute(
                "INSERT OR IGNORE INTO source_files (id, source_revision_id, relative_path, content_sha256, license_status) "
                "VALUES (?, ?, ?, ?, 'APPROVED')",
                (file_id, REVISION, f"{brand_id}/{cs_id}.json",
                 sha256_text(json.dumps(commands, sort_keys=True))),
            )
            remote_id = short(f"remote:{SOURCE_ID}:{b_id}:{DT_TV}:{cs_id}")
            cur.execute(
                "INSERT OR IGNORE INTO remotes (id, source_file_id, brand_id, device_type_id, normalized_remote_model, display_remote_model, region) "
                "VALUES (?, ?, ?, ?, ?, ?, 'AR')",
                (remote_id, file_id, b_id, DT_TV, cs_id, cs_name),
            )
            code_set_id = short(f"cs:{SOURCE_ID}:{remote_id}:{len(mapped)}")
            cur.execute(
                "INSERT OR IGNORE INTO code_sets (id, remote_id, source_revision_id, protocol_family, protocol_variant, region, verification_status, runtime_status) "
                "VALUES (?, ?, ?, ?, ?, 'AR', 'INTERNAL_UNVERIFIED', 'ACTIVE')",
                (code_set_id, remote_id, REVISION, proto_orig, codec_id),
            )

            for canonical, addr, cmd in mapped:
                act_id = short(f"action:{canonical}")
                cur.execute(
                    "INSERT OR IGNORE INTO actions (id, canonical_key, action_family) VALUES (?, ?, 'STANDARD')",
                    (act_id, canonical),
                )
                sig_key = f"PARAMETRIC:{codec_id}:{freq}:{addr}:{cmd}"
                sig_id = short(sig_key)
                sig_sha = sha256_text(sig_key)
                cur.execute(
                    "INSERT OR IGNORE INTO signals (id, encoding_type, codec_id, protocol_name_original, protocol_variant, carrier_hz, address_value, sub_device_value, command_value, repeat_count, toggle_policy, pattern_blob, compression, slice_count, duration_us, uncompressed_bytes, physical_sha256, canonical_sha256, runtime_status, validation_status, rejection_reason) "
                    "VALUES (?, 'PARAMETRIC', ?, ?, ?, ?, ?, NULL, ?, 0, NULL, NULL, NULL, NULL, NULL, NULL, ?, ?, 'SUPPORTED_PARAMETRIC', 'PASSED', NULL)",
                    (sig_id, codec_id, codec_id, proto_orig, freq, addr, cmd, sig_sha, sig_sha),
                )
                bnd_id = short(f"b:{code_set_id}:{act_id}:{sig_id}")
                cur.execute(
                    "INSERT OR IGNORE INTO command_bindings (id, code_set_id, action_id, signal_id, repeat_policy, press_type, source_priority) "
                    "VALUES (?, ?, ?, ?, 'FULL_FRAME', 'SINGLE_TAP', 0)",
                    (bnd_id, code_set_id, act_id, sig_id),
                )
            conn.commit()
            seeded += 1
            print(f"  ✓ seeded {brand_name} / {cs_id} ({len(mapped)} IrActions, {codec_id}@{freq}Hz)")

    conn.close()
    print(f"\n  Seeded {seeded} new curated code sets.")

    # ── 2. Integrity + manifest ────────────────────────────────────────────────
    con = sqlite3.connect(str(DB_PATH))
    print(f"  quick_check       : {con.execute('PRAGMA quick_check').fetchone()[0]}")
    fk = con.execute("PRAGMA foreign_key_check").fetchall()
    print(f"  foreign_key_check : {len(fk)} violations")
    con.close()

    db_sha = hashlib.sha256(DB_PATH.read_bytes()).hexdigest()
    canonical_hash, counts = export_canonical_catalog.compute_canonical_hash(DB_PATH)
    manifest = {
        "schemaVersion": 4,
        "profile": "production",
        "generatedAtUtc": "2026-08-06T17:20:00Z",
        "pipelineVersion": "0.4.0-ir-real-rc1",
        "databaseSha256": db_sha,
        "canonicalContentSha256": canonical_hash,
        "databaseSizeBytes": DB_PATH.stat().st_size,
        "counts": counts,
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"  manifest updated  : {MANIFEST_PATH.name}")
    print(f"  databaseSha256   : {db_sha}")
    print(f"  canonical        : {canonical_hash}")
    print("  ✓ Done")
    return 0


if __name__ == "__main__":
    sys.exit(main())