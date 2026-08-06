#!/usr/bin/env python3
"""
Elysium Nexus — IR Catalog Optimizer
=====================================
Post-processes ir_catalog.db to:
1. Convert JSON pattern arrays to compact binary blobs (zlib-compressed)
2. Normalize device types (collapse rare types into canonical categories)
3. Remove orphaned records
4. VACUUM and ANALYZE for minimal size
"""

import json
import sqlite3
import struct
import zlib
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent.parent / \
    "apps" / "android" / "app" / "src" / "main" / "assets" / "ir" / "ir_catalog.db"

# ─── Device Type Collapsing ──────────────────────────────────────────────────
DEVICE_TYPE_COLLAPSE = {
    # Consolidate rare probonopd types into canonical categories
    "Pre-Amplifier": "Amplifier",
    "Surround Processor": "AV_Receiver",
    "Matrix Switcher": "AV_Receiver",
    "Tuner": "AV_Receiver",
    "DSS": "Cable_Box",
    "Satellite": "Cable_Box",
    "Video Projector": "Projector",
    "LCD Projector": "Projector",
    "DLP Projector": "Projector",
    "Toy": "Miscellaneous",
    "Toys": "Miscellaneous",
    "Misc": "Miscellaneous",
    "Misc.": "Miscellaneous",
    "PVR": "Cable_Box",
    "DVR": "Cable_Box",
    "Audio": "Speaker",
    "Audio Receiver": "AV_Receiver",
    "Audio System": "Speaker",
    "Mini System": "Speaker",
    "Micro System": "Speaker",
    "Compact System": "Speaker",
    "Sound Bar": "Soundbar",
    "Sound System": "Soundbar",
    "Disc Player": "DVD_Player",
    "Disc Changer": "DVD_Player",
    "DVD Recorder": "DVD_Player",
    "DVD/VCR": "DVD_Player",
    "VTR": "VCR",
    "Video Player": "VCR",
    "Video Recorder": "VCR",
    "Laser Disc": "DVD_Player",
    "Laserdisc": "DVD_Player",
    "MiniDisc": "CD_Player",
    "Blu-Ray Player": "BluRay_Player",
    "Blu-ray": "BluRay_Player",
    "Media Streamer": "Streaming_Device",
    "Streaming Box": "Streaming_Device",
    "Streaming Player": "Streaming_Device",
    "Apple TV": "Streaming_Device",
    "Roku": "Streaming_Device",
    "Fire TV": "Streaming_Device",
    "Chromecast": "Streaming_Device",
    "Digital Media": "Streaming_Device",
    "Network Player": "Streaming_Device",
    "LED Light": "LED_Light",
    "Light": "LED_Light",
    "Lighting": "LED_Light",
    "Air Conditioner": "AC",
    "Climate": "AC",
    "HVAC": "AC",
    "Heat Pump": "AC",
    "Dehumidifier": "AC",
    "Air Purifier": "Air_Purifier",
    "Home Theater": "AV_Receiver",
    "Karaoke": "Speaker",
    "Intercom": "Miscellaneous",
    "Vacuum": "Miscellaneous",
    "Robot Vacuum": "Miscellaneous",
    "Bidet": "Miscellaneous",
    "Digital Sign": "Digital_Sign",
    "Sign": "Digital_Sign",
    "KVM": "Miscellaneous",
    "CCTV": "Camera",
    "Security Camera": "Camera",
    "IP Camera": "Camera",
    "Game Console": "Console",
    "Console": "Console",
    "MP3 Player": "MP3_Player",
    "Digital Jukebox": "MP3_Player",
    "Car Multimedia": "Miscellaneous",
    "Head Unit": "Miscellaneous",
    "Handicap Ceiling Lift": "Miscellaneous",
    "Handicap Ceiling Lifts": "Miscellaneous",
    "Dust Collector": "Miscellaneous",
    "Dust Collectors": "Miscellaneous",
    "Computer": "Miscellaneous",
    "Computers": "Miscellaneous",
    "Converter": "Miscellaneous",
    "Converters": "Miscellaneous",
    "DVB-T": "Cable_Box",
    "Clock": "Miscellaneous",
    "Clocks": "Miscellaneous",
}


def optimize():
    print(f"Opening database: {DB_PATH}")
    conn = sqlite3.connect(str(DB_PATH))
    cur = conn.cursor()

    # ─── 1. Collapse Device Types ─────────────────────────────────────
    print("\n[1/4] Collapsing device types...")
    collapsed_count = 0
    for old_name, new_name in DEVICE_TYPE_COLLAPSE.items():
        # Get or create canonical type
        cur.execute("SELECT id FROM device_types WHERE name = ?", (new_name,))
        target_row = cur.fetchone()
        if not target_row:
            cur.execute("INSERT INTO device_types (name) VALUES (?)", (new_name,))
            target_id = cur.lastrowid
        else:
            target_id = target_row[0]

        # Find old type
        cur.execute("SELECT id FROM device_types WHERE name = ?", (old_name,))
        old_row = cur.fetchone()
        if not old_row:
            continue
        old_id = old_row[0]
        if old_id == target_id:
            continue

        # Get all remotes with the old device type
        cur.execute("SELECT id, source_id, brand_id, model, remote_model FROM remotes WHERE device_type_id = ?", (old_id,))
        old_remotes = cur.fetchall()

        for old_remote_id, src_id, brand_id, model, remote_model in old_remotes:
            # Check if a remote with the target device_type already exists
            cur.execute(
                "SELECT id FROM remotes WHERE source_id=? AND brand_id=? AND device_type_id=? AND model=? AND remote_model=?",
                (src_id, brand_id, target_id, model, remote_model))
            existing = cur.fetchone()

            if existing:
                # Merge: move commands from old remote to existing one
                existing_id = existing[0]
                cur.execute("UPDATE commands_encoded SET remote_id=? WHERE remote_id=?",
                            (existing_id, old_remote_id))
                cur.execute("UPDATE commands_raw SET remote_id=? WHERE remote_id=?",
                            (existing_id, old_remote_id))
                cur.execute("DELETE FROM remotes WHERE id=?", (old_remote_id,))
            else:
                # No collision, safe to reassign
                cur.execute("UPDATE remotes SET device_type_id=? WHERE id=?",
                            (target_id, old_remote_id))

            collapsed_count += 1

        # Remove old type if no more remotes reference it
        cur.execute("SELECT COUNT(*) FROM remotes WHERE device_type_id=?", (old_id,))
        if cur.fetchone()[0] == 0:
            cur.execute("DELETE FROM device_types WHERE id = ?", (old_id,))

    conn.commit()
    print(f"  ✓ Collapsed {collapsed_count} remote assignments")

    # ─── 2. Remove orphaned device types ──────────────────────────────
    print("\n[2/4] Removing orphaned device types...")
    cur.execute("""
        DELETE FROM device_types WHERE id NOT IN (
            SELECT DISTINCT device_type_id FROM remotes
        )
    """)
    orphaned = cur.rowcount
    conn.commit()
    print(f"  ✓ Removed {orphaned} orphaned device types")

    # ─── 3. Compress raw patterns ─────────────────────────────────────
    print("\n[3/4] Compressing raw patterns to binary blobs...")

    # Add blob column if not exists
    try:
        cur.execute("ALTER TABLE commands_raw ADD COLUMN pattern_blob BLOB")
    except sqlite3.OperationalError:
        pass  # Column already exists

    cur.execute("SELECT id, pattern_json FROM commands_raw")
    rows = cur.fetchall()
    compressed_count = 0
    total_json_bytes = 0
    total_blob_bytes = 0

    for row_id, pattern_json in rows:
        try:
            pattern = json.loads(pattern_json)
            # Pack as 32-bit unsigned integers
            packed = struct.pack(f"<{len(pattern)}I", *[max(0, p) for p in pattern])
            compressed = zlib.compress(packed, 9)
            cur.execute("UPDATE commands_raw SET pattern_blob = ? WHERE id = ?",
                        (compressed, row_id))
            total_json_bytes += len(pattern_json)
            total_blob_bytes += len(compressed)
            compressed_count += 1
        except Exception:
            pass

    conn.commit()

    if total_json_bytes > 0:
        ratio = (1 - total_blob_bytes / total_json_bytes) * 100
        print(f"  ✓ Compressed {compressed_count} patterns")
        print(f"    JSON: {total_json_bytes / 1024 / 1024:.1f} MB -> Blob: {total_blob_bytes / 1024 / 1024:.1f} MB ({ratio:.1f}% reduction)")

    # Now drop the JSON column by recreating the table
    print("  Migrating table to drop JSON column...")
    cur.executescript("""
        CREATE TABLE commands_raw_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            remote_id INTEGER NOT NULL REFERENCES remotes(id),
            action TEXT NOT NULL,
            carrier_hz INTEGER NOT NULL,
            pattern_blob BLOB NOT NULL,
            duration_us INTEGER NOT NULL,
            fingerprint TEXT NOT NULL
        );
        INSERT INTO commands_raw_new (id, remote_id, action, carrier_hz, pattern_blob, duration_us, fingerprint)
            SELECT id, remote_id, action, carrier_hz, COALESCE(pattern_blob, CAST(pattern_json AS BLOB)), duration_us, fingerprint
            FROM commands_raw;
        DROP TABLE commands_raw;
        ALTER TABLE commands_raw_new RENAME TO commands_raw;
        CREATE INDEX idx_commands_raw_remote ON commands_raw(remote_id);
        CREATE INDEX idx_commands_raw_action ON commands_raw(action);
        CREATE INDEX idx_commands_raw_fp ON commands_raw(fingerprint);
    """)
    conn.commit()
    print("  ✓ Table migrated successfully")

    # ─── 4. VACUUM & ANALYZE ──────────────────────────────────────────
    print("\n[4/4] Final optimization...")
    conn.execute("ANALYZE")
    conn.execute("VACUUM")
    conn.commit()

    # ─── Final Report ─────────────────────────────────────────────────
    cur.execute("SELECT COUNT(*) FROM brands")
    brands = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM device_types")
    dtypes = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM remotes")
    remotes = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM commands_encoded")
    encoded = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM commands_raw")
    raw = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM protocols")
    protos = cur.fetchone()[0]

    conn.close()

    final_size = DB_PATH.stat().st_size
    print(f"\n{'=' * 60}")
    print(f"  Optimized Database Report")
    print(f"{'=' * 60}")
    print(f"  Brands:           {brands}")
    print(f"  Device Types:     {dtypes}")
    print(f"  Remotes:          {remotes}")
    print(f"  Encoded Commands: {encoded}")
    print(f"  Raw Commands:     {raw}")
    print(f"  Total Commands:   {encoded + raw}")
    print(f"  Protocols:        {protos}")
    print(f"  Database Size:    {final_size / 1024 / 1024:.2f} MB")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    optimize()
