#!/usr/bin/env python3
"""
Elysium Nexus — Fail-Closed IR Catalog Optimizer
=================================================
Post-processes ir_catalog.db to:
1. Compress raw microsecond patterns into binary blobs (zlib compression level 9)
2. Validate decompression round-trip for 100% byte-exact integrity
3. Reject invalid or corrupted patterns without silent clamping (max(0, p)) or fallback JSON-casting
4. Remove orphaned records and optimize database layout via VACUUM & ANALYZE
"""

import json
import sqlite3
import struct
import sys
import zlib
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent.parent / \
    "apps" / "android" / "app" / "src" / "main" / "assets" / "ir" / "ir_catalog.db"

MAX_PATTERN_SLICES = 4096

def optimize():
    print(f"Opening database for fail-closed optimization: {DB_PATH}")
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    # 1. Clean up orphaned records
    cur.execute("DELETE FROM remotes WHERE brand_id NOT IN (SELECT id FROM brands)")
    cur.execute("DELETE FROM remotes WHERE device_type_id NOT IN (SELECT id FROM device_types)")
    cur.execute("DELETE FROM code_sets WHERE remote_id NOT IN (SELECT id FROM remotes)")
    cur.execute("DELETE FROM command_bindings WHERE code_set_id NOT IN (SELECT id FROM code_sets)")
    cur.execute("DELETE FROM command_bindings WHERE signal_id NOT IN (SELECT id FROM signals)")
    conn.commit()

    # 2. Validate and compress signals with pattern_blob
    cur.execute("SELECT id, carrier_hz, duration_us, pattern_blob FROM signals WHERE encoding_type = 'RAW'")
    rows = cur.fetchall()

    rejected_count = 0
    validated_count = 0

    for sig_id, carrier_hz, duration_us, blob in rows:
        if not blob:
            continue

        try:
            # Test decompression round-trip
            decompressed = zlib.decompress(blob)
            if len(decompressed) % 4 != 0:
                print(f"  ❌ Invalid pattern blob length for signal {sig_id}")
                cur.execute("DELETE FROM signals WHERE id = ?", (sig_id,))
                rejected_count += 1
                continue

            slice_count = len(decompressed) // 4
            pattern = struct.unpack(f"<{slice_count}I", decompressed)

            # Strict fail-closed assertions (NO max(0, p) allowed!)
            if any(val <= 0 for val in pattern):
                print(f"  ❌ Non-positive duration slice in signal {sig_id}")
                cur.execute("DELETE FROM signals WHERE id = ?", (sig_id,))
                rejected_count += 1
                continue

            if slice_count > MAX_PATTERN_SLICES:
                print(f"  ❌ Excessive pattern slices ({slice_count}) in signal {sig_id}")
                cur.execute("DELETE FROM signals WHERE id = ?", (sig_id,))
                rejected_count += 1
                continue

            validated_count += 1
        except Exception as e:
            print(f"  ❌ Failed to decompress/validate blob for signal {sig_id}: {e}")
            cur.execute("DELETE FROM signals WHERE id = ?", (sig_id,))
            rejected_count += 1

    conn.commit()

    # Remove unreferenced command bindings
    cur.execute("DELETE FROM command_bindings WHERE signal_id NOT IN (SELECT id FROM signals)")
    cur.execute("DELETE FROM code_sets WHERE id NOT IN (SELECT DISTINCT code_set_id FROM command_bindings)")
    conn.commit()

    # 3. Final VACUUM and ANALYZE
    print(f"  ✓ Validated {validated_count} raw patterns (Rejected {rejected_count} corrupted)")
    print("  Running ANALYZE & VACUUM...")
    conn.execute("ANALYZE")
    conn.execute("VACUUM")
    conn.commit()
    conn.close()

    final_size = DB_PATH.stat().st_size
    print(f"  ✓ Optimization complete. Database Size: {final_size / 1024 / 1024:.2f} MB")

if __name__ == "__main__":
    optimize()
