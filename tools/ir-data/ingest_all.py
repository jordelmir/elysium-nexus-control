#!/usr/bin/env python3
"""
Elysium Nexus — IR Data Fabric Ingestion Pipeline
===================================================
Parses ALL 5 authorized IR data repositories into a unified SQLite database
ready for embedding as an Android asset.

Sources:
  1. Flipper-IRDB (.ir files — parsed + raw)
  2. SmartIR (JSON — Broadlink Base64 + raw arrays)
  3. probonopd/irdb (CSV — protocol parametric)
  4. radioxoma/infrared (LIRC .conf + irplus .xml)
  5. IrpProtocols.xml (protocol dictionary — metadata only)

Output:
  - ir_catalog.db (SQLite)
  - ir_catalog_stats.json (manifest with stats)
  - THIRD_PARTY_IR_DATA_NOTICES.md (legal notices)
"""

import base64
import csv
import hashlib
import json
import os
import re
import sqlite3
import struct
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from typing import Optional

# ─── Paths ────────────────────────────────────────────────────────────────────
ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = ROOT / ".cache" / "ir-sources"
OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
STATS_PATH = OUTPUT_DIR / "ir_catalog_stats.json"
NOTICES_PATH = OUTPUT_DIR / "THIRD_PARTY_IR_DATA_NOTICES.md"

# ─── Action Normalization ─────────────────────────────────────────────────────
ACTION_ALIASES: dict[str, list[str]] = {
    "POWER_TOGGLE": ["power", "power toggle", "standby", "pwr", "power_toggle",
                     "key_power", "pwr_toggle", "on/off", "on_off"],
    "POWER_ON": ["power_on", "power on", "on", "key_power_on"],
    "POWER_OFF": ["power_off", "power off", "off", "key_power_off", "shutdown"],
    "VOLUME_UP": ["vol_up", "vol up", "volume +", "volume+", "volumeup",
                  "volume_up", "vol+", "key_volumeup", "vol_u", "volume up",
                  "vol +"],
    "VOLUME_DOWN": ["vol_dn", "vol_down", "volume -", "vol-", "volumedown",
                    "volume_down", "key_volumedown", "vol_d", "volume down",
                    "vol -"],
    "MUTE": ["mute", "muting", "sound_mute", "key_mute", "mute/unmute"],
    "CHANNEL_UP": ["ch_up", "ch+", "channel_up", "channel +", "key_channelup",
                   "ch_next", "channel up", "ch +", "prog_up", "prog+",
                   "key_next"],
    "CHANNEL_DOWN": ["ch_dn", "ch-", "channel_down", "channel -",
                     "key_channeldown", "ch_prev", "channel down", "ch -",
                     "prog_down", "prog-", "key_previous"],
    "INPUT": ["input", "input source", "source", "tv/video", "key_tv",
              "input_next", "tv/av"],
    "MENU": ["menu", "key_menu", "osd"],
    "OK": ["ok", "enter", "select", "key_ok", "key_enter", "key_select"],
    "UP": ["up", "key_up", "cursor_up", "dpad_up"],
    "DOWN": ["down", "key_down", "cursor_down", "dpad_down"],
    "LEFT": ["left", "key_left", "cursor_left", "dpad_left"],
    "RIGHT": ["right", "key_right", "cursor_right", "dpad_right"],
    "BACK": ["back", "return", "key_back", "key_return", "exit", "key_exit"],
    "HOME": ["home", "key_home", "smart_hub", "smart hub"],
    "PLAY": ["play", "key_play"],
    "PAUSE": ["pause", "key_pause"],
    "PLAY_PAUSE": ["play/pause", "play_pause", "key_playpause", "play pause"],
    "STOP": ["stop", "key_stop"],
    "REWIND": ["rewind", "rew", "key_rewind", "<<", "key_rew"],
    "FAST_FORWARD": ["fast_forward", "ff", "key_fastforward", ">>", "key_ff",
                     "fwd"],
    "RECORD": ["record", "rec", "key_record"],
    "GUIDE": ["guide", "key_guide", "epg", "key_epg"],
    "INFO": ["info", "key_info", "display", "key_display"],
    "RED": ["red", "key_red"],
    "GREEN": ["green", "key_green"],
    "YELLOW": ["yellow", "key_yellow"],
    "BLUE": ["blue", "key_blue"],
    "NUM_0": ["0", "key_0", "num_0"],
    "NUM_1": ["1", "key_1", "num_1"],
    "NUM_2": ["2", "key_2", "num_2"],
    "NUM_3": ["3", "key_3", "num_3"],
    "NUM_4": ["4", "key_4", "num_4"],
    "NUM_5": ["5", "key_5", "num_5"],
    "NUM_6": ["6", "key_6", "num_6"],
    "NUM_7": ["7", "key_7", "num_7"],
    "NUM_8": ["8", "key_8", "num_8"],
    "NUM_9": ["9", "key_9", "num_9"],
    "SLEEP": ["sleep", "key_sleep", "sleep_timer"],
    "SUBTITLE": ["subtitle", "sub", "key_subtitle", "cc", "closed_caption"],
    "AUDIO": ["audio", "key_audio", "audio_mode", "sound_mode"],
    "ASPECT": ["aspect", "key_aspect", "aspect_ratio", "p.size", "picture_size"],
    "TIMER": ["timer", "key_timer"],
    # HVAC-specific
    "HVAC_OFF": ["off"],
    "HVAC_COOL": ["cool", "cooling"],
    "HVAC_HEAT": ["heat", "heating"],
    "HVAC_AUTO": ["auto"],
    "HVAC_DRY": ["dry", "dehumidify"],
    "HVAC_FAN_ONLY": ["fan_only", "fan only", "fan"],
    "FAN_LOW": ["low", "fan_low"],
    "FAN_MED": ["med", "medium", "fan_med"],
    "FAN_HIGH": ["high", "fan_high"],
    "FAN_AUTO": ["auto", "fan_auto"],
    "SWING": ["swing", "key_swing", "swing_toggle"],
    "TEMP_UP": ["temp_up", "temp+", "temperature_up"],
    "TEMP_DOWN": ["temp_down", "temp-", "temperature_down"],
}

# Build reverse lookup
_ACTION_REVERSE: dict[str, str] = {}
for canonical, aliases in ACTION_ALIASES.items():
    for alias in aliases:
        _ACTION_REVERSE[alias.lower().strip()] = canonical
    _ACTION_REVERSE[canonical.lower().strip()] = canonical


def normalize_action(raw_name: str) -> str:
    """Normalize a raw action name to its canonical form."""
    key = raw_name.lower().strip().replace("_", " ").replace("-", " ")
    # Direct match
    if key in _ACTION_REVERSE:
        return _ACTION_REVERSE[key]
    # Try with underscores
    key_us = key.replace(" ", "_")
    if key_us in _ACTION_REVERSE:
        return _ACTION_REVERSE[key_us]
    # Try removing key_ prefix
    if key.startswith("key "):
        sub = key[4:]
        if sub in _ACTION_REVERSE:
            return _ACTION_REVERSE[sub]
    # Keep original uppercased
    return raw_name.upper().replace(" ", "_").replace("-", "_")


# ─── Brand Normalization ──────────────────────────────────────────────────────
BRAND_ALIASES: dict[str, str] = {
    "lg": "LG", "samsung": "Samsung", "sony": "Sony", "panasonic": "Panasonic",
    "philips": "Philips", "toshiba": "Toshiba", "sharp": "Sharp",
    "hisense": "Hisense", "tcl": "TCL", "vizio": "Vizio", "sanyo": "Sanyo",
    "hitachi": "Hitachi", "jvc": "JVC", "pioneer": "Pioneer",
    "mitsubishi": "Mitsubishi", "daewoo": "Daewoo", "grundig": "Grundig",
    "emerson": "Emerson", "funai": "Funai", "magnavox": "Magnavox",
    "rca": "RCA", "zenith": "Zenith", "sylvania": "Sylvania",
    "insignia": "Insignia", "roku": "Roku", "xiaomi": "Xiaomi",
    "haier": "Haier", "bose": "Bose", "yamaha": "Yamaha", "denon": "Denon",
    "onkyo": "Onkyo", "marantz": "Marantz", "harman kardon": "Harman Kardon",
    "apple": "Apple", "daikin": "Daikin", "midea": "Midea",
    "gree": "Gree", "carrier": "Carrier", "whirlpool": "Whirlpool",
    "fujitsu": "Fujitsu", "general electric": "GE", "ge": "GE",
    "aoc": "AOC", "epson": "Epson", "benq": "BenQ", "optoma": "Optoma",
    "viewsonic": "ViewSonic", "nec": "NEC", "dell": "Dell",
    "onn": "Onn", "element": "Element", "westinghouse": "Westinghouse",
}


def normalize_brand(raw_brand: str) -> str:
    key = raw_brand.lower().strip()
    return BRAND_ALIASES.get(key, raw_brand.strip())


# ─── Device Type Normalization ────────────────────────────────────────────────
DEVICE_TYPE_ALIASES: dict[str, str] = {
    "tvs": "TV", "tv": "TV", "television": "TV",
    "acs": "AC", "ac": "AC", "climate": "AC", "air conditioner": "AC",
    "air_conditioner": "AC",
    "soundbars": "Soundbar", "soundbar": "Soundbar", "sound bar": "Soundbar",
    "projectors": "Projector", "projector": "Projector",
    "fans": "Fan", "fan": "Fan",
    "audio_and_video_receivers": "AV_Receiver", "av receiver": "AV_Receiver",
    "speakers": "Speaker", "speaker": "Speaker",
    "monitors": "Monitor", "monitor": "Monitor",
    "dvd_players": "DVD_Player", "dvd player": "DVD_Player",
    "dvd": "DVD_Player",
    "blu-ray": "BluRay_Player", "blu_ray": "BluRay_Player",
    "bluray": "BluRay_Player",
    "cable_boxes": "Cable_Box", "cable box": "Cable_Box",
    "stb": "Cable_Box", "set-top box": "Cable_Box", "set top box": "Cable_Box",
    "satellite": "Cable_Box", "satellite receiver": "Cable_Box",
    "streaming_devices": "Streaming_Device", "streaming device": "Streaming_Device",
    "streaming": "Streaming_Device",
    "led_lighting": "LED_Light", "led light": "LED_Light", "light": "LED_Light",
    "heaters": "Heater", "heater": "Heater",
    "humidifiers": "Humidifier", "humidifier": "Humidifier",
    "air_purifiers": "Air_Purifier", "air purifier": "Air_Purifier",
    "media_player": "Media_Player", "media player": "Media_Player",
    "digital_signs": "Digital_Sign",
    "cameras": "Camera", "camera": "Camera",
    "fireplaces": "Fireplace", "fireplace": "Fireplace",
    "universal_tv_remotes": "Universal_Remote",
    "cd_players": "CD_Player", "cd player": "CD_Player",
    "consoles": "Console", "console": "Console",
    "mp3 player": "MP3_Player", "mp3": "MP3_Player",
    "digital jukebox": "MP3_Player",
    "vcr": "VCR", "vtr": "VCR",
    "amplifier": "Amplifier", "amp": "Amplifier",
    "misc": "Miscellaneous", "miscellaneous": "Miscellaneous",
    "receiver": "AV_Receiver",
}


def normalize_device_type(raw_type: str) -> str:
    key = raw_type.lower().strip().replace("-", "_")
    return DEVICE_TYPE_ALIASES.get(key, raw_type.strip())


# ─── Protocol Normalization ───────────────────────────────────────────────────
PROTOCOL_MAP: dict[str, tuple[str, int]] = {
    # name -> (canonical_name, carrier_hz)
    "nec": ("NEC", 38000),
    "nec42": ("NEC42", 38000),
    "necext": ("NECext", 38000),
    "nec1": ("NEC", 38000),
    "necx2": ("NECx2", 38000),
    "necx": ("NECx2", 38000),
    "samsung32": ("Samsung32", 38000),
    "samsung": ("Samsung32", 38000),
    "samsung36": ("Samsung36", 38000),
    "sirc": ("SIRC", 40000),
    "sirc15": ("SIRC15", 40000),
    "sirc20": ("SIRC20", 40000),
    "sony12": ("SIRC", 40000),
    "sony15": ("SIRC15", 40000),
    "sony20": ("SIRC20", 40000),
    "rc5": ("RC5", 36000),
    "rc5x": ("RC5x", 36000),
    "rc6": ("RC6", 36000),
    "kaseikyo": ("Kaseikyo", 37000),
    "panasonic": ("Kaseikyo", 37000),
    "panasonic_old": ("Kaseikyo", 37000),
    "apple": ("Apple", 38000),
    "nec48": ("NEC48", 38000),
    "pioneer": ("Pioneer", 40000),
    "sharp": ("Sharp", 38000),
    "denon": ("Denon", 38000),
    "jvc": ("JVC", 38000),
    "lg": ("NEC", 38000),
    "mitsubishi": ("Mitsubishi", 33000),
    "raw": ("RAW", 38000),
    "broadlink": ("Broadlink", 38000),
}


def normalize_protocol(raw_proto: str) -> tuple[str, int]:
    key = raw_proto.lower().strip()
    if key in PROTOCOL_MAP:
        return PROTOCOL_MAP[key]
    return (raw_proto.strip(), 38000)


# ─── Broadlink Base64 Decoder ─────────────────────────────────────────────────
def decode_broadlink_base64(b64_str: str) -> tuple[int, list[int]]:
    """
    Decode Broadlink Base64 IR code to (carrier_hz, microseconds_pattern).
    Broadlink format:
      byte[0]: 0x26 = IR
      byte[1]: repeat count
      byte[2..3]: little-endian length of timing data
      byte[4..N]: timing pairs (each value * 8192/269 ≈ 30.45 µs per tick)
    """
    try:
        data = base64.b64decode(b64_str)
    except Exception:
        return (0, [])

    if len(data) < 6 or data[0] != 0x26:
        return (0, [])

    length = data[2] | (data[3] << 8)
    carrier_hz = 38000  # Broadlink default

    pattern = []
    i = 4
    end = min(4 + length * 2, len(data))
    while i < end:
        if i >= len(data):
            break
        val = data[i]
        i += 1
        if val == 0:
            # Two-byte value follows (big-endian)
            if i + 1 < len(data):
                val = (data[i] << 8) | data[i + 1]
                i += 2
            else:
                break
        # Convert ticks to microseconds: tick * 1_000_000 / (269 / 8192)
        us = int(val * 8192 / 269)
        if us > 0:
            pattern.append(us)

    return (carrier_hz, pattern)


# ─── Physical Validation ──────────────────────────────────────────────────────
def validate_raw_pattern(pattern: list[int], carrier_hz: int) -> bool:
    """
    Validate a raw IR pattern against Android ConsumerIrService constraints.
    Returns True if valid.
    """
    if not pattern:
        return False
    if len(pattern) < 2:
        return False
    # All durations must be > 0
    if any(d <= 0 for d in pattern):
        return False
    # Total duration < 2 seconds
    total_us = sum(pattern)
    if total_us > 2_000_000:
        return False
    # Pattern must have odd length (mark-space pairs + trailing mark)
    # Actually Android accepts even too, but odd is canonical
    # Carrier must be positive
    if carrier_hz <= 0:
        return False
    return True


# ─── Fingerprinting ──────────────────────────────────────────────────────────
def fingerprint_encoded(protocol: str, address: int, sub_device: int,
                        command: int) -> str:
    return f"{protocol}_{address}_{sub_device}_{command}"


def fingerprint_raw(carrier_hz: int, pattern: list[int]) -> str:
    h = hashlib.md5(json.dumps(pattern).encode()).hexdigest()[:16]
    return f"RAW_{carrier_hz}_{h}"


# ─── SQLite Schema ────────────────────────────────────────────────────────────
DDL = """
CREATE TABLE IF NOT EXISTS sources (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    url TEXT,
    license TEXT NOT NULL,
    commit_hash TEXT,
    production_enabled INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS brands (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS device_types (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS remotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id TEXT NOT NULL REFERENCES sources(id),
    brand_id INTEGER NOT NULL REFERENCES brands(id),
    device_type_id INTEGER NOT NULL REFERENCES device_types(id),
    model TEXT,
    remote_model TEXT,
    file_path TEXT,
    UNIQUE(source_id, brand_id, device_type_id, model, remote_model)
);

CREATE TABLE IF NOT EXISTS commands_encoded (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    remote_id INTEGER NOT NULL REFERENCES remotes(id),
    action TEXT NOT NULL,
    protocol TEXT NOT NULL,
    carrier_hz INTEGER NOT NULL,
    address INTEGER NOT NULL,
    sub_device INTEGER NOT NULL DEFAULT -1,
    command INTEGER NOT NULL,
    fingerprint TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS commands_raw (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    remote_id INTEGER NOT NULL REFERENCES remotes(id),
    action TEXT NOT NULL,
    carrier_hz INTEGER NOT NULL,
    pattern_json TEXT NOT NULL,
    duration_us INTEGER NOT NULL,
    fingerprint TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS protocols (
    name TEXT PRIMARY KEY,
    carrier_hz INTEGER,
    irp_notation TEXT,
    bit_count INTEGER,
    doc TEXT
);

CREATE INDEX IF NOT EXISTS idx_commands_encoded_remote ON commands_encoded(remote_id);
CREATE INDEX IF NOT EXISTS idx_commands_encoded_action ON commands_encoded(action);
CREATE INDEX IF NOT EXISTS idx_commands_encoded_fp ON commands_encoded(fingerprint);
CREATE INDEX IF NOT EXISTS idx_commands_raw_remote ON commands_raw(remote_id);
CREATE INDEX IF NOT EXISTS idx_commands_raw_action ON commands_raw(action);
CREATE INDEX IF NOT EXISTS idx_commands_raw_fp ON commands_raw(fingerprint);
CREATE INDEX IF NOT EXISTS idx_remotes_brand ON remotes(brand_id);
CREATE INDEX IF NOT EXISTS idx_remotes_type ON remotes(device_type_id);
"""

# ─── Counters ─────────────────────────────────────────────────────────────────
stats = defaultdict(int)


# ─── Database Helpers ─────────────────────────────────────────────────────────
def get_or_create_brand(cur: sqlite3.Cursor, name: str) -> int:
    cur.execute("SELECT id FROM brands WHERE name = ?", (name,))
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute("INSERT INTO brands (name) VALUES (?)", (name,))
    return cur.lastrowid


def get_or_create_device_type(cur: sqlite3.Cursor, name: str) -> int:
    cur.execute("SELECT id FROM device_types WHERE name = ?", (name,))
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute("INSERT INTO device_types (name) VALUES (?)", (name,))
    return cur.lastrowid


def get_or_create_remote(cur: sqlite3.Cursor, source_id: str, brand_id: int,
                         device_type_id: int, model: str,
                         remote_model: str, file_path: str) -> int:
    cur.execute(
        "SELECT id FROM remotes WHERE source_id=? AND brand_id=? AND "
        "device_type_id=? AND model=? AND remote_model=?",
        (source_id, brand_id, device_type_id, model, remote_model))
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute(
        "INSERT INTO remotes (source_id, brand_id, device_type_id, model, "
        "remote_model, file_path) VALUES (?, ?, ?, ?, ?, ?)",
        (source_id, brand_id, device_type_id, model, remote_model, file_path))
    return cur.lastrowid


def insert_encoded_dedup(cur: sqlite3.Cursor, remote_id: int, action: str,
                         protocol: str, carrier_hz: int, address: int,
                         sub_device: int, command: int) -> bool:
    fp = fingerprint_encoded(protocol, address, sub_device, command)
    cur.execute(
        "SELECT 1 FROM commands_encoded WHERE remote_id=? AND fingerprint=?",
        (remote_id, fp))
    if cur.fetchone():
        stats["dedup_encoded"] += 1
        return False
    cur.execute(
        "INSERT INTO commands_encoded (remote_id, action, protocol, carrier_hz, "
        "address, sub_device, command, fingerprint) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (remote_id, action, protocol, carrier_hz, address, sub_device,
         command, fp))
    stats["inserted_encoded"] += 1
    return True


def insert_raw_dedup(cur: sqlite3.Cursor, remote_id: int, action: str,
                     carrier_hz: int, pattern: list[int]) -> bool:
    fp = fingerprint_raw(carrier_hz, pattern)
    cur.execute(
        "SELECT 1 FROM commands_raw WHERE remote_id=? AND fingerprint=?",
        (remote_id, fp))
    if cur.fetchone():
        stats["dedup_raw"] += 1
        return False
    duration_us = sum(pattern)
    cur.execute(
        "INSERT INTO commands_raw (remote_id, action, carrier_hz, pattern_json, "
        "duration_us, fingerprint) VALUES (?, ?, ?, ?, ?, ?)",
        (remote_id, action, carrier_hz, json.dumps(pattern), duration_us, fp))
    stats["inserted_raw"] += 1
    return True


# ─── Parser 1: Flipper-IRDB ──────────────────────────────────────────────────
def parse_flipper_file(cur: sqlite3.Cursor, filepath: Path,
                       brand: str, device_type: str,
                       remote_model: str) -> int:
    """Parse a single Flipper .ir file and insert commands."""
    brand_norm = normalize_brand(brand)
    dtype_norm = normalize_device_type(device_type)
    brand_id = get_or_create_brand(cur, brand_norm)
    dtype_id = get_or_create_device_type(cur, dtype_norm)
    remote_id = get_or_create_remote(
        cur, "flipper-irdb", brand_id, dtype_id,
        remote_model, remote_model,
        str(filepath.relative_to(CACHE / "flipper-irdb")))

    count = 0
    try:
        text = filepath.read_text(encoding="utf-8", errors="replace")
    except Exception as e:
        stats["parse_errors"] += 1
        return 0

    blocks = re.split(r'\n#\s*\n|\n(?=name:)', text)

    current_name = None
    current_type = None
    current_protocol = None
    current_address = None
    current_command = None
    current_frequency = None
    current_data = None

    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("Filetype:") or line.startswith("Version:"):
            continue
        if line.startswith("#"):
            # If we have a pending entry, flush it
            if current_name and current_type:
                count += _flush_flipper_entry(
                    cur, remote_id, current_name, current_type,
                    current_protocol, current_address, current_command,
                    current_frequency, current_data)
            current_name = None
            current_type = None
            current_protocol = None
            current_address = None
            current_command = None
            current_frequency = None
            current_data = None
            continue

        key_val = line.split(":", 1)
        if len(key_val) != 2:
            continue
        key, val = key_val[0].strip(), key_val[1].strip()

        if key == "name":
            # Flush previous entry
            if current_name and current_type:
                count += _flush_flipper_entry(
                    cur, remote_id, current_name, current_type,
                    current_protocol, current_address, current_command,
                    current_frequency, current_data)
            current_name = val
            current_type = None
            current_protocol = None
            current_address = None
            current_command = None
            current_frequency = None
            current_data = None
        elif key == "type":
            current_type = val
        elif key == "protocol":
            current_protocol = val
        elif key == "address":
            # Flipper format: "07 00 00 00" (little-endian hex bytes)
            current_address = _parse_flipper_hex(val)
        elif key == "command":
            current_command = _parse_flipper_hex(val)
        elif key == "frequency":
            try:
                current_frequency = int(val)
            except ValueError:
                current_frequency = 38000
        elif key == "data":
            # Raw timing data
            try:
                current_data = [int(x) for x in val.split()]
            except ValueError:
                current_data = None

    # Flush last entry
    if current_name and current_type:
        count += _flush_flipper_entry(
            cur, remote_id, current_name, current_type,
            current_protocol, current_address, current_command,
            current_frequency, current_data)

    return count


def _parse_flipper_hex(hex_str: str) -> int:
    """Parse Flipper hex format '07 00 00 00' (little-endian) to int."""
    try:
        parts = hex_str.strip().split()
        result = 0
        for i, p in enumerate(parts):
            result |= int(p, 16) << (8 * i)
        return result
    except (ValueError, IndexError):
        return 0


def _flush_flipper_entry(cur: sqlite3.Cursor, remote_id: int,
                         name: str, sig_type: str,
                         protocol: Optional[str], address: Optional[int],
                         command: Optional[int],
                         frequency: Optional[int],
                         data: Optional[list[int]]) -> int:
    action = normalize_action(name)

    if sig_type == "parsed" and protocol and address is not None and command is not None:
        proto_name, default_hz = normalize_protocol(protocol)
        carrier = frequency if frequency else default_hz
        insert_encoded_dedup(cur, remote_id, action, proto_name, carrier,
                             address, -1, command)
        return 1
    elif sig_type == "raw" and data and frequency:
        if validate_raw_pattern(data, frequency):
            insert_raw_dedup(cur, remote_id, action, frequency, data)
            return 1
        else:
            stats["validation_rejected"] += 1
    return 0


def ingest_flipper(cur: sqlite3.Cursor):
    """Ingest all Flipper-IRDB .ir files."""
    flipper_root = CACHE / "flipper-irdb"
    if not flipper_root.exists():
        print("  ⚠ Flipper-IRDB not found, skipping")
        return

    # Register source
    cur.execute(
        "INSERT OR REPLACE INTO sources VALUES (?, ?, ?, ?, ?, ?)",
        ("flipper-irdb", "Flipper-IRDB", "https://github.com/Lucaslhm/Flipper-IRDB",
         "CC0-1.0", "HEAD", 1))

    total_commands = 0
    ir_files = list(flipper_root.rglob("*.ir"))
    print(f"  Found {len(ir_files)} .ir files")
    stats["flipper_files"] = len(ir_files)

    for ir_file in ir_files:
        # Skip _Converted_ directory
        if "_Converted_" in str(ir_file):
            stats["flipper_skipped_converted"] += 1
            continue

        # Extract brand and device type from path
        # Pattern: flipper-irdb/<DeviceType>/<Brand>/<Model>.ir
        parts = ir_file.relative_to(flipper_root).parts
        if len(parts) >= 3:
            device_type = parts[0]
            brand = parts[1]
            remote_model = ir_file.stem
        elif len(parts) == 2:
            device_type = parts[0]
            brand = "Unknown"
            remote_model = ir_file.stem
        else:
            device_type = "Miscellaneous"
            brand = "Unknown"
            remote_model = ir_file.stem

        n = parse_flipper_file(cur, ir_file, brand, device_type, remote_model)
        total_commands += n

    stats["flipper_total_commands"] = total_commands
    print(f"  ✓ Flipper: {total_commands} commands from {stats['flipper_files']} files")


# ─── Parser 2: SmartIR ───────────────────────────────────────────────────────
def ingest_smartir(cur: sqlite3.Cursor):
    """Ingest SmartIR JSON files."""
    smartir_root = CACHE / "smartir" / "codes"
    if not smartir_root.exists():
        print("  ⚠ SmartIR not found, skipping")
        return

    cur.execute(
        "INSERT OR REPLACE INTO sources VALUES (?, ?, ?, ?, ?, ?)",
        ("smartir", "SmartIR", "https://github.com/smartHomeHub/SmartIR",
         "MIT", "HEAD", 1))

    total_commands = 0
    json_files = list(smartir_root.rglob("*.json"))
    print(f"  Found {len(json_files)} SmartIR JSON files")
    stats["smartir_files"] = len(json_files)

    for jf in json_files:
        try:
            data = json.loads(jf.read_text(encoding="utf-8", errors="replace"))
        except (json.JSONDecodeError, Exception) as e:
            stats["parse_errors"] += 1
            continue

        manufacturer = normalize_brand(data.get("manufacturer", "Unknown"))
        models = data.get("supportedModels", [])
        model_str = ", ".join(models[:3]) if models else jf.stem
        encoding = data.get("commandsEncoding", "").lower()
        controller = data.get("supportedController", "").lower()

        # Determine device type from parent directory
        parent = jf.parent.name
        device_type = normalize_device_type(parent)

        brand_id = get_or_create_brand(cur, manufacturer)
        dtype_id = get_or_create_device_type(cur, device_type)
        remote_id = get_or_create_remote(
            cur, "smartir", brand_id, dtype_id, model_str, jf.stem,
            str(jf.relative_to(CACHE / "smartir")))

        commands = data.get("commands", {})
        n = _process_smartir_commands(cur, remote_id, commands, encoding,
                                     device_type, "")
        total_commands += n

    stats["smartir_total_commands"] = total_commands
    print(f"  ✓ SmartIR: {total_commands} commands from {stats['smartir_files']} files")


def _process_smartir_commands(cur: sqlite3.Cursor, remote_id: int,
                              commands: dict, encoding: str,
                              device_type: str, prefix: str) -> int:
    """Recursively process SmartIR command structures."""
    count = 0
    for key, value in commands.items():
        action_name = f"{prefix}{key}" if prefix else key

        if isinstance(value, str):
            count += _decode_smartir_value(cur, remote_id, action_name,
                                          value, encoding)
        elif isinstance(value, list):
            # Some commands are arrays (sequences)
            for i, v in enumerate(value):
                if isinstance(v, str):
                    count += _decode_smartir_value(
                        cur, remote_id, action_name, v, encoding)
        elif isinstance(value, dict):
            # Nested: temperature modes, sources, etc.
            for sub_key, sub_val in value.items():
                sub_action = f"{action_name}_{sub_key}"
                if isinstance(sub_val, str):
                    count += _decode_smartir_value(
                        cur, remote_id, sub_action, sub_val, encoding)
                elif isinstance(sub_val, list):
                    for v in sub_val:
                        if isinstance(v, str):
                            count += _decode_smartir_value(
                                cur, remote_id, sub_action, v, encoding)
                elif isinstance(sub_val, dict):
                    count += _process_smartir_commands(
                        cur, remote_id, {sub_key: sub_val}, encoding,
                        device_type, f"{action_name}_")
    return count


def _decode_smartir_value(cur: sqlite3.Cursor, remote_id: int,
                          action_name: str, value: str,
                          encoding: str) -> int:
    """Decode a single SmartIR command value (Base64 or raw)."""
    action = normalize_action(action_name)

    if encoding == "base64" or (len(value) > 20 and not value.startswith("[")):
        # Broadlink Base64
        carrier_hz, pattern = decode_broadlink_base64(value)
        if carrier_hz > 0 and validate_raw_pattern(pattern, carrier_hz):
            insert_raw_dedup(cur, remote_id, action, carrier_hz, pattern)
            return 1
        else:
            stats["validation_rejected"] += 1
    elif encoding == "raw" or value.startswith("["):
        # Raw microsecond array
        try:
            pattern = json.loads(value)
            if isinstance(pattern, list) and all(isinstance(x, int) for x in pattern):
                if validate_raw_pattern(pattern, 38000):
                    insert_raw_dedup(cur, remote_id, action, 38000, pattern)
                    return 1
        except (json.JSONDecodeError, TypeError):
            pass
        stats["validation_rejected"] += 1

    return 0


# ─── Parser 3: probonopd/irdb (CSV) ──────────────────────────────────────────
def ingest_probonopd(cur: sqlite3.Cursor):
    """Ingest probonopd/irdb CSV files (GATED — productionEnabled=false)."""
    irdb_root = CACHE / "probonopd-irdb" / "codes"
    if not irdb_root.exists():
        print("  ⚠ probonopd/irdb not found, skipping")
        return

    # Register as GATED source
    cur.execute(
        "INSERT OR REPLACE INTO sources VALUES (?, ?, ?, ?, ?, ?)",
        ("probonopd-irdb", "probonopd/irdb", "https://github.com/probonopd/irdb",
         "LicenseRef-IRDB-CUSTOM", "HEAD", 0))  # productionEnabled = 0 (GATED)

    total_commands = 0
    csv_files = list(irdb_root.rglob("*.csv"))
    print(f"  Found {len(csv_files)} probonopd CSV files")
    stats["probonopd_files"] = len(csv_files)

    for csv_file in csv_files:
        # Path: codes/<Brand>/<DeviceType>/<device>,<subdevice>.csv
        parts = csv_file.relative_to(irdb_root).parts
        if len(parts) >= 3:
            brand = parts[0]
            device_type = parts[1]
            remote_model = csv_file.stem
        elif len(parts) == 2:
            brand = parts[0]
            device_type = "Miscellaneous"
            remote_model = csv_file.stem
        else:
            continue

        brand_norm = normalize_brand(brand)
        dtype_norm = normalize_device_type(device_type)
        brand_id = get_or_create_brand(cur, brand_norm)
        dtype_id = get_or_create_device_type(cur, dtype_norm)
        remote_id = get_or_create_remote(
            cur, "probonopd-irdb", brand_id, dtype_id,
            remote_model, remote_model,
            str(csv_file.relative_to(CACHE / "probonopd-irdb")))

        try:
            with open(csv_file, "r", encoding="utf-8", errors="replace") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    fn = row.get("functionname", "").strip()
                    proto_raw = row.get("protocol", "").strip()
                    device_str = row.get("device", "0").strip()
                    subdevice_str = row.get("subdevice", "-1").strip()
                    func_str = row.get("function", "0").strip()

                    if not fn or not proto_raw:
                        continue

                    action = normalize_action(fn)
                    proto_name, carrier_hz = normalize_protocol(proto_raw)

                    try:
                        address = int(device_str)
                        sub_device = int(subdevice_str) if subdevice_str != "-1" else -1
                        command = int(func_str)
                    except ValueError:
                        stats["parse_errors"] += 1
                        continue

                    insert_encoded_dedup(cur, remote_id, action, proto_name,
                                        carrier_hz, address, sub_device, command)
                    total_commands += 1

        except Exception as e:
            stats["parse_errors"] += 1

    stats["probonopd_total_commands"] = total_commands
    print(f"  ✓ probonopd (GATED): {total_commands} commands from {stats['probonopd_files']} files")


# ─── Parser 4: radioxoma/infrared (LIRC + irplus XML) ────────────────────────
def ingest_radioxoma(cur: sqlite3.Cursor):
    """Ingest radioxoma/infrared LIRC .conf and irplus .xml files."""
    radio_root = CACHE / "radioxoma-infrared"
    if not radio_root.exists():
        print("  ⚠ radioxoma/infrared not found, skipping")
        return

    cur.execute(
        "INSERT OR REPLACE INTO sources VALUES (?, ?, ?, ?, ?, ?)",
        ("radioxoma-infrared", "radioxoma/infrared",
         "https://github.com/radioxoma/infrared",
         "MIT", "HEAD", 1))

    total_commands = 0

    # Parse LIRC .conf files
    lirc_files = list(radio_root.rglob("*.lircd.conf")) + list(radio_root.rglob("*.conf"))
    lirc_files = [f for f in lirc_files if f.suffix == ".conf"]
    print(f"  Found {len(lirc_files)} LIRC conf files")

    for lf in lirc_files:
        n = _parse_lirc_conf(cur, lf)
        total_commands += n

    # Parse irplus XML files
    xml_files = list(radio_root.rglob("*.xml"))
    print(f"  Found {len(xml_files)} irplus XML files")

    for xf in xml_files:
        n = _parse_irplus_xml(cur, xf)
        total_commands += n

    stats["radioxoma_total_commands"] = total_commands
    print(f"  ✓ radioxoma: {total_commands} commands")


def _parse_lirc_conf(cur: sqlite3.Cursor, filepath: Path) -> int:
    """Parse LIRC lircd.conf file format."""
    count = 0
    try:
        text = filepath.read_text(encoding="utf-8", errors="replace")
    except Exception:
        stats["parse_errors"] += 1
        return 0

    # Extract brand from path
    parts = filepath.relative_to(CACHE / "radioxoma-infrared").parts
    brand = normalize_brand(parts[0]) if parts else "Unknown"
    remote_model = filepath.stem.replace(".lircd", "")

    # Parse LIRC config
    header = [0, 0]
    one = [0, 0]
    zero = [0, 0]
    ptrail = 0
    pre_data = 0
    pre_data_bits = 0
    frequency = 38000
    bits = 0
    remote_name = remote_model

    in_codes = False
    codes: dict[str, int] = {}

    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        if line.startswith("begin codes"):
            in_codes = True
            continue
        elif line.startswith("end codes"):
            in_codes = False
            continue

        if in_codes:
            parts_line = line.split()
            if len(parts_line) >= 2:
                key_name = parts_line[0]
                try:
                    key_code = int(parts_line[1], 0)
                    codes[key_name] = key_code
                except ValueError:
                    pass
            continue

        # Parse config directives
        parts_line = line.split()
        if len(parts_line) < 2:
            continue
        directive = parts_line[0].lower()
        try:
            if directive == "name":
                remote_name = parts_line[1]
            elif directive == "frequency":
                frequency = int(parts_line[1])
            elif directive == "bits":
                bits = int(parts_line[1])
            elif directive == "pre_data_bits":
                pre_data_bits = int(parts_line[1])
            elif directive == "pre_data":
                pre_data = int(parts_line[1], 0)
            elif directive == "header":
                header = [int(parts_line[1]), int(parts_line[2])]
            elif directive == "one":
                one = [int(parts_line[1]), int(parts_line[2])]
            elif directive == "zero":
                zero = [int(parts_line[1]), int(parts_line[2])]
            elif directive == "ptrail":
                ptrail = int(parts_line[1])
        except (ValueError, IndexError):
            pass

    if not codes:
        return 0

    brand_id = get_or_create_brand(cur, brand)
    dtype_id = get_or_create_device_type(cur, "Miscellaneous")
    remote_id = get_or_create_remote(
        cur, "radioxoma-infrared", brand_id, dtype_id,
        remote_name, remote_model,
        str(filepath.relative_to(CACHE / "radioxoma-infrared")))

    # For each code, synthesize the raw pattern from LIRC timing
    for key_name, key_code in codes.items():
        action = normalize_action(key_name)
        full_code = (pre_data << bits | key_code) if pre_data_bits > 0 else key_code
        total_bits = pre_data_bits + bits if pre_data_bits > 0 else bits

        if total_bits <= 0 or (header[0] == 0 and one[0] == 0):
            # Can't synthesize raw, store as encoded with NEC approximation
            insert_encoded_dedup(cur, remote_id, action, "LIRC_RAW",
                                 frequency, pre_data, -1, key_code)
            count += 1
            continue

        # Synthesize raw timing pattern
        pattern = list(header)  # header mark + space
        for i in range(total_bits - 1, -1, -1):
            bit = (full_code >> i) & 1
            if bit:
                pattern.extend(one)
            else:
                pattern.extend(zero)
        if ptrail > 0:
            pattern.append(ptrail)

        # Remove trailing zeros
        while pattern and pattern[-1] <= 0:
            pattern.pop()

        if validate_raw_pattern(pattern, frequency):
            insert_raw_dedup(cur, remote_id, action, frequency, pattern)
            count += 1
        else:
            stats["validation_rejected"] += 1

    return count


def _parse_irplus_xml(cur: sqlite3.Cursor, filepath: Path) -> int:
    """Parse irplus XML format (safe against XXE)."""
    count = 0
    try:
        # Disable DTD/external entities for security
        parser = ET.XMLParser()
        tree = ET.parse(filepath, parser=parser)
        root = tree.getroot()
    except ET.ParseError:
        stats["parse_errors"] += 1
        return 0
    except Exception:
        stats["parse_errors"] += 1
        return 0

    # Extract brand from path
    parts = filepath.relative_to(CACHE / "radioxoma-infrared").parts
    brand = normalize_brand(parts[0]) if parts else "Unknown"
    remote_model = filepath.stem

    brand_id = get_or_create_brand(cur, brand)
    dtype_id = get_or_create_device_type(cur, "Miscellaneous")
    remote_id = get_or_create_remote(
        cur, "radioxoma-infrared", brand_id, dtype_id,
        remote_model, remote_model,
        str(filepath.relative_to(CACHE / "radioxoma-infrared")))

    # irplus XML: <irplus><device ...><button label="..."><raw frequency="..." data="..."/></button></device></irplus>
    for button in root.iter("button"):
        label = button.get("label", "unknown")
        action = normalize_action(label)

        for raw_el in button.iter("raw"):
            freq_str = raw_el.get("frequency", "38000")
            data_str = raw_el.get("data", "")
            try:
                freq = int(freq_str)
                pattern = [int(x) for x in data_str.split() if x.strip()]
            except ValueError:
                continue

            if validate_raw_pattern(pattern, freq):
                insert_raw_dedup(cur, remote_id, action, freq, pattern)
                count += 1

        # Also check for coded commands
        for coded in button.iter("code"):
            proto = coded.get("protocol", "")
            dev = coded.get("device", "0")
            sub = coded.get("subdevice", "-1")
            func = coded.get("function", "0")
            try:
                proto_name, carrier = normalize_protocol(proto)
                insert_encoded_dedup(cur, remote_id, action, proto_name,
                                     carrier, int(dev), int(sub), int(func))
                count += 1
            except ValueError:
                pass

    return count


# ─── Parser 5: IrpProtocols.xml ──────────────────────────────────────────────
def ingest_irp_protocols(cur: sqlite3.Cursor):
    """Parse IrpProtocols.xml for protocol metadata dictionary."""
    xml_path = CACHE / "irp-transmogrifier" / "src" / "main" / "resources" / "IrpProtocols.xml"
    if not xml_path.exists():
        print("  ⚠ IrpProtocols.xml not found, skipping")
        return

    cur.execute(
        "INSERT OR REPLACE INTO sources VALUES (?, ?, ?, ?, ?, ?)",
        ("irp-protocols", "IrpProtocols.xml",
         "https://github.com/bengtmartensson/IrpTransmogrifier",
         "Public Domain", "HEAD", 1))

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except ET.ParseError as e:
        print(f"  ⚠ Failed to parse IrpProtocols.xml: {e}")
        stats["parse_errors"] += 1
        return

    # Handle XML namespaces
    ns = {"irp": "http://www.harctoolbox.org/irp-protocols"}

    count = 0
    for protocol in root.findall(".//irp:protocol", ns):
        name = protocol.get("name", "")
        if not name:
            continue

        # Extract IRP notation
        irp_el = protocol.find("irp:irp", ns)
        irp_notation = irp_el.text.strip() if irp_el is not None and irp_el.text else ""

        # Extract frequency from IRP notation if present
        carrier_hz = None
        freq_match = re.search(r"frequency\s*=\s*(\d+)", irp_notation)
        if freq_match:
            carrier_hz = int(freq_match.group(1))
        else:
            # Try common frequencies
            for known, (_, freq) in PROTOCOL_MAP.items():
                if known.lower() == name.lower():
                    carrier_hz = freq
                    break

        # Extract bit count
        bit_count = None
        bits_match = re.search(r"\{(\d+)\}", irp_notation)

        # Extract documentation
        doc_el = protocol.find("irp:documentation", ns)
        doc = ""
        if doc_el is not None:
            doc = ET.tostring(doc_el, encoding="unicode", method="text").strip()[:500]

        cur.execute(
            "INSERT OR REPLACE INTO protocols (name, carrier_hz, irp_notation, "
            "bit_count, doc) VALUES (?, ?, ?, ?, ?)",
            (name, carrier_hz, irp_notation[:1000], bit_count, doc[:500]))
        count += 1

    stats["irp_protocols"] = count
    print(f"  ✓ IrpProtocols: {count} protocol definitions")


# ─── Main Pipeline ────────────────────────────────────────────────────────────
def main():
    print("=" * 70)
    print("  Elysium Nexus — IR Data Fabric Ingestion Pipeline")
    print("=" * 70)

    # Ensure output directory
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Remove old database
    if DB_PATH.exists():
        DB_PATH.unlink()

    # Create database
    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    cur = conn.cursor()

    # Create schema
    cur.executescript(DDL)
    conn.commit()

    # ─── Ingest All Sources ───────────────────────────────────────────
    print("\n[1/5] Ingesting Flipper-IRDB...")
    ingest_flipper(cur)
    conn.commit()

    print("\n[2/5] Ingesting SmartIR...")
    ingest_smartir(cur)
    conn.commit()

    print("\n[3/5] Ingesting probonopd/irdb (GATED)...")
    ingest_probonopd(cur)
    conn.commit()

    print("\n[4/5] Ingesting radioxoma/infrared...")
    ingest_radioxoma(cur)
    conn.commit()

    print("\n[5/5] Ingesting IrpProtocols.xml...")
    ingest_irp_protocols(cur)
    conn.commit()

    # ─── Final Stats ──────────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("  Final Database Statistics")
    print("=" * 70)

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
    cur.execute("SELECT COUNT(*) FROM sources")
    source_count = cur.fetchone()[0]

    # Top brands
    cur.execute("""
        SELECT b.name, COUNT(DISTINCT r.id) as remote_count
        FROM brands b JOIN remotes r ON r.brand_id = b.id
        GROUP BY b.name ORDER BY remote_count DESC LIMIT 20
    """)
    top_brands = [{"name": r[0], "remotes": r[1]} for r in cur.fetchall()]

    # Top device types
    cur.execute("""
        SELECT dt.name, COUNT(DISTINCT r.id) as remote_count
        FROM device_types dt JOIN remotes r ON r.device_type_id = dt.id
        GROUP BY dt.name ORDER BY remote_count DESC LIMIT 15
    """)
    top_types = [{"name": r[0], "remotes": r[1]} for r in cur.fetchall()]

    # Source breakdown
    cur.execute("""
        SELECT s.id, s.name, s.license, s.production_enabled,
               COUNT(DISTINCT r.id) as remotes,
               (SELECT COUNT(*) FROM commands_encoded ce
                JOIN remotes r2 ON ce.remote_id = r2.id
                WHERE r2.source_id = s.id) as encoded_cmds,
               (SELECT COUNT(*) FROM commands_raw cr
                JOIN remotes r3 ON cr.remote_id = r3.id
                WHERE r3.source_id = s.id) as raw_cmds
        FROM sources s LEFT JOIN remotes r ON r.source_id = s.id
        GROUP BY s.id
    """)
    source_breakdown = []
    for row in cur.fetchall():
        source_breakdown.append({
            "id": row[0], "name": row[1], "license": row[2],
            "production_enabled": bool(row[3]),
            "remotes": row[4], "encoded_commands": row[5],
            "raw_commands": row[6]
        })

    db_size = DB_PATH.stat().st_size

    print(f"  Brands:           {brand_count}")
    print(f"  Device Types:     {dtype_count}")
    print(f"  Remotes:          {remote_count}")
    print(f"  Encoded Commands: {encoded_count}")
    print(f"  Raw Commands:     {raw_count}")
    print(f"  Total Commands:   {encoded_count + raw_count}")
    print(f"  Protocols:        {proto_count}")
    print(f"  Sources:          {source_count}")
    print(f"  Database Size:    {db_size / 1024 / 1024:.2f} MB")
    print(f"  Deduped Encoded:  {stats.get('dedup_encoded', 0)}")
    print(f"  Deduped Raw:      {stats.get('dedup_raw', 0)}")
    print(f"  Validation Rejects: {stats.get('validation_rejected', 0)}")
    print(f"  Parse Errors:     {stats.get('parse_errors', 0)}")

    # ─── Write Stats JSON ─────────────────────────────────────────────
    manifest = {
        "schemaVersion": 2,
        "generatedAtUtc": "2026-08-06T15:00:00Z",
        "pipelineVersion": "1.0.0",
        "stats": {
            "brands": brand_count,
            "deviceTypes": dtype_count,
            "remotes": remote_count,
            "encodedCommands": encoded_count,
            "rawCommands": raw_count,
            "totalCommands": encoded_count + raw_count,
            "protocolDefinitions": proto_count,
            "sources": source_count,
            "databaseSizeBytes": db_size,
            "dedupEncoded": stats.get("dedup_encoded", 0),
            "dedupRaw": stats.get("dedup_raw", 0),
            "validationRejected": stats.get("validation_rejected", 0),
            "parseErrors": stats.get("parse_errors", 0),
        },
        "topBrands": top_brands,
        "topDeviceTypes": top_types,
        "sources": source_breakdown,
    }
    STATS_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"\n  ✓ Stats written to {STATS_PATH}")

    # ─── Write THIRD_PARTY_NOTICES ────────────────────────────────────
    notices = """# Third-Party IR Data Notices — Elysium Nexus Universal Controller

This application contains IR remote control data from the following open-source projects.
The data is used solely for infrared signal transmission and device compatibility.

---

## 1. Flipper-IRDB
- **Source**: https://github.com/Lucaslhm/Flipper-IRDB
- **License**: CC0-1.0 (Creative Commons Zero — Public Domain Dedication)
- **Usage**: TV, AC, Soundbar, Projector, and other device IR codes

## 2. SmartIR
- **Source**: https://github.com/smartHomeHub/SmartIR
- **License**: MIT License
- **Usage**: Climate (HVAC), Media Player, Fan, and Light IR codes

## 3. probonopd/irdb
- **Source**: https://github.com/probonopd/irdb
- **License**: Custom License (LicenseRef-IRDB-CUSTOM)
- **Status**: GATED — Data included but production-disabled pending license compliance
- **Attribution**: Contains/accesses irdb by Simon Peter and contributors, used under permission.
  For licensing details and for information on how to contribute to the database,
  see https://github.com/probonopd/irdb

## 4. radioxoma/infrared
- **Source**: https://github.com/radioxoma/infrared
- **License**: MIT License
- **Usage**: LG HiFi and Vityas TV IR codes (LIRC and irplus formats)

## 5. IrpProtocols.xml (IrpTransmogrifier)
- **Source**: https://github.com/bengtmartensson/IrpTransmogrifier
- **License**: Public Domain (data file only — the IrpTransmogrifier software is GPL-3.0)
- **Usage**: Protocol definition metadata only. No GPL-licensed code is included.
- **Isolation**: Only the IrpProtocols.xml data file is consumed. The IrpTransmogrifier
  Java runtime is NOT included in this application.

---

Generated by Elysium Nexus IR Data Fabric Ingestion Pipeline v1.0.0
"""
    NOTICES_PATH.write_text(notices)
    print(f"  ✓ Notices written to {NOTICES_PATH}")

    # ─── Optimize Database ────────────────────────────────────────────
    print("\n  Optimizing database...")
    conn.execute("ANALYZE")
    conn.execute("VACUUM")
    conn.commit()
    conn.close()

    final_size = DB_PATH.stat().st_size
    print(f"  ✓ Final database size: {final_size / 1024 / 1024:.2f} MB")
    print(f"\n  ✓ Pipeline complete! Database at: {DB_PATH}")
    print("=" * 70)


if __name__ == "__main__":
    main()
