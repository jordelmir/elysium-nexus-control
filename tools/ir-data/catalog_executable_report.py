#!/usr/bin/env python3
"""
Phase 4 — Runtime Executable Catalog Report Generator

Audits every signal in the packaged SQLite database (`ir_catalog.db`),
classifies runtime codec eligibility, and generates `runtime-executable-report.json`.

Commercial Gate: UNKNOWN = 0.
"""

import json
import os
import sqlite3
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
DB_PATH = os.path.join(REPO_ROOT, "apps/android/app/src/main/assets/ir/ir_catalog.db")
OUTPUT_REPORT_PATH = os.path.join(
    REPO_ROOT, "apps/android/app/src/main/assets/ir/runtime-executable-report.json"
)

# Codec classifications per Phase 5 & 8 Commercial Policy
EXPERIMENTAL_CODECS = {"RC5", "RC6", "KASEIKYO", "PANASONIC"}
PRODUCTION_CODECS = {
    "NEC",
    "NEC1",
    "NECEXT",
    "NEC_EXTENDED",
    "SAMSUNG",
    "SAMSUNG32",
    "SIRC",
    "SONY",
    "SIRC12",
    "SIRC15",
    "SIRC20",
    "AIWA",
    "AIWA_RC501",
}


def main():
    if not os.path.exists(DB_PATH):
        print(f"Error: Catalog DB not found at {DB_PATH}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    query = """
    SELECT
        s.id AS signal_id,
        cs.id AS code_set_id,
        b.display_name AS brand_name,
        dt.canonical_name AS device_type,
        src.id AS source_name,
        s.encoding_type,
        pd.family_name AS protocol_family,
        pv.variant_name AS protocol_variant,
        s.command_value
    FROM signals s
    JOIN command_bindings cb ON cb.signal_id = s.id
    JOIN code_sets cs ON cb.code_set_id = cs.id
    JOIN remotes r ON cs.remote_id = r.id
    JOIN brands b ON r.brand_id = b.id
    JOIN device_types dt ON r.device_type_id = dt.id
    JOIN source_revisions sr ON cs.source_revision_id = sr.id
    JOIN sources src ON sr.source_id = src.id
    LEFT JOIN protocol_variants pv ON s.protocol_variant_id = pv.id
    LEFT JOIN protocol_definitions pd ON pv.protocol_id = pd.id
    """

    cursor.execute(query)
    rows = cursor.fetchall()

    total_signals = len(rows)
    raw_executable = 0
    parametric_executable = 0
    experimental_lab_only = 0
    invalid_parameters = 0
    unsupported = 0

    brand_counts = {}
    protocol_counts = {}

    for row in rows:
        (
            sig_id,
            cs_id,
            brand,
            dev_type,
            source,
            enc_type,
            family,
            variant,
            cmd_val,
        ) = row

        proto_name = (family or "").upper()
        if not proto_name and variant:
            proto_name = variant.split("_")[0].upper()

        if enc_type == "RAW":
            raw_executable += 1
        elif proto_name in EXPERIMENTAL_CODECS:
            experimental_lab_only += 1
        elif proto_name in PRODUCTION_CODECS:
            if cmd_val is not None and cmd_val < 0:
                invalid_parameters += 1
            else:
                parametric_executable += 1
        else:
            unsupported += 1

        brand_counts[brand] = brand_counts.get(brand, 0) + 1
        protocol_counts[proto_name or "UNKNOWN"] = (
            protocol_counts.get(proto_name or "UNKNOWN", 0) + 1
        )

    report = {
        "version": "0.7.0-retail-truth",
        "catalogDbPath": os.path.relpath(DB_PATH, REPO_ROOT),
        "totalSignalsAudited": total_signals,
        "classification": {
            "RAW_EXECUTABLE": raw_executable,
            "PARAMETRIC_EXECUTABLE": parametric_executable,
            "EXPERIMENTAL_LAB_ONLY": experimental_lab_only,
            "INVALID_PARAMETERS": invalid_parameters,
            "UNSUPPORTED": unsupported,
        },
        "commercialGate": {"unknownCount": unsupported, "isPass": (unsupported == 0)},
        "brandCount": len(brand_counts),
        "protocolBreakdown": protocol_counts,
    }

    with open(OUTPUT_REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"✅ Runtime Executable Report generated: {OUTPUT_REPORT_PATH}")
    print(f"   Total Signals: {total_signals}")
    print(f"   Parametric Executable: {parametric_executable}")
    print(f"   Raw Executable: {raw_executable}")
    print(f"   Experimental (Lab Only): {experimental_lab_only}")
    print(f"   Unsupported: {unsupported}")
    print(f"   Gate Status: {'PASS' if unsupported == 0 else 'FAIL'}")


if __name__ == "__main__":
    main()
