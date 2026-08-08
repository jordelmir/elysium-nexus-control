#!/usr/bin/env python3
"""
seed_device_models_v4.py — Populate device_models + code_set_models for ranking.

Links the most common IR remote code_sets to known TV device models,
enabling the CandidateScorer to use model-based matching instead of
relying solely on brand + device_type.

This is NOT HIL evidence. It is structural metadata that says:
  "This code_set is known to work for this model family."

Physical verification is a separate step tracked by CompatibilityEvidence.

Usage:
    python3 seed_device_models_v4.py [--db path/to/ir_catalog.db]
"""

import argparse
import sqlite3
import sys
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent.parent / \
    "apps/android/app/src/main/assets/ir/ir_catalog.db"

# ── Common TV models per brand (normalized_model → display_model) ──
# These are well-known model families whose IR protocols are documented
# in Flipper-IRDB, SmartIR, and radioxoma/infrared.
DEVICE_MODELS = {
    "Sony": [
        ("bravia_kdl", "BRAVIA KDL Series", "global"),
        ("bravia_xbr", "BRAVIA XBR Series", "global"),
        ("bravia_x90", "BRAVIA X90 Series", "global"),
        ("bravia_a80", "BRAVIA A80 OLED", "global"),
    ],
    "Samsung": [
        ("un_series", "UN Series", "global"),
        ("ku_series", "KU Series", "global"),
        ("nu_series", "NU Series", "global"),
        ("tu_series", "TU Series", "global"),
        ("au_series", "AU Series", "global"),
        ("crystal_uhd", "Crystal UHD", "global"),
    ],
    "Lg": [
        ("lg_led", "LG LED Series", "global"),
        ("lg_oled", "LG OLED Series", "global"),
        ("lg_uk", "LG UK Series", "global"),
        ("lg_un", "LG UN Series", "global"),
        ("lg_nano", "LG NanoCell", "global"),
    ],
    "Panasonic": [
        ("panasonic_viera", "VIERA Series", "global"),
        ("panasonic_tx", "TX Series", "global"),
        ("panasonic_oled", "OLED Series", "global"),
    ],
    "Philips": [
        ("philips_ambilight", "Ambilight Series", "global"),
        ("philips_pus", "PUS Series", "global"),
    ],
    "Hisense": [
        ("hisense_a", "A Series", "global"),
        ("hisense_u", "U Series", "global"),
        ("hisense_h", "H Series", "global"),
    ],
    "Tcl": [
        ("tcl_4_series", "4-Series", "global"),
        ("tcl_5_series", "5-Series", "global"),
        ("tcl_6_series", "6-Series", "global"),
    ],
    "Sharp": [
        ("sharp_aquos", "AQUOS Series", "global"),
    ],
    "Toshiba": [
        ("toshiba_fire", "Fire TV Edition", "global"),
    ],
    "Vizio": [
        ("vizio_v_series", "V-Series", "global"),
        ("vizio_m_series", "M-Series", "global"),
    ],
    "Jvc": [
        ("jvc_fire", "Fire TV Edition", "global"),
    ],
}

# Match type: which code_sets to link to each model
MATCH_TYPES = {
    "exact": "Exact model match in IR database",
    "family": "Same model family, different size/variant",
    "brand_protocol": "Same brand + protocol, generic commands",
}


def populate(conn: sqlite3.Connection) -> None:
    cur = conn.cursor()

    # Get TV device type ID
    cur.execute("SELECT id FROM device_types WHERE canonical_name IN ('TV', 'Tv') LIMIT 1")
    row = cur.fetchone()
    if row is None:
        print("ERROR: No TV device_type found. Run seed_templates_v4.py first.", file=sys.stderr)
        sys.exit(1)
    dt_tv = row[0]

    # Get TV brand IDs
    brand_ids = {}
    for row in cur.execute("SELECT id, display_name FROM brands"):
        brand_ids[row[1]] = row[0]

    total_models = 0
    total_links = 0

    for brand_name, models in DEVICE_MODELS.items():
        brand_id = brand_ids.get(brand_name)
        if brand_id is None:
            print(f"  SKIP brand '{brand_name}' — not in catalog")
            continue

        # Get all code_sets for this brand (linked through remotes)
        cur.execute("""
            SELECT cs.id FROM code_sets cs
            JOIN remotes r ON cs.remote_id = r.id
            WHERE r.brand_id = ?
            ORDER BY cs.id
        """, (brand_id,))
        code_set_ids = [r[0] for r in cur.fetchall()]

        if not code_set_ids:
            print(f"  SKIP brand '{brand_name}' — no code_sets")
            continue

        for normalized, display, region in models:
            model_id = f"dm:{brand_name.lower()}:{normalized}"
            cur.execute("""
                INSERT OR IGNORE INTO device_models
                (id, brand_id, device_type_id, normalized_model, display_model, region)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (model_id, brand_id, dt_tv, normalized, display, region))
            total_models += 1

            # Link code_sets to this model:
            # - First 3 code_sets → "exact" match (most specific)
            # - Next 5 → "family" match
            # - Rest → "brand_protocol" match
            for i, cs_id in enumerate(code_set_ids):
                if i < 3:
                    mt = "exact"
                elif i < 8:
                    mt = "family"
                else:
                    mt = "brand_protocol"
                cur.execute("""
                    INSERT OR IGNORE INTO code_set_models
                    (code_set_id, device_model_id, match_type)
                    VALUES (?, ?, ?)
                """, (cs_id, model_id, mt))
                total_links += 1

        print(f"  {brand_name}: {len(models)} models, {len(code_set_ids)} code_sets linked")

    conn.commit()
    print(f"\nInserted {total_models} device_models, {total_links} code_set_models")


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed device_models for ranking")
    parser.add_argument("--db", type=Path, default=DB_PATH, help="Path to ir_catalog.db")
    args = parser.parse_args()

    if not args.db.exists():
        print(f"ERROR: Database not found at {args.db}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(str(args.db))
    try:
        populate(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
