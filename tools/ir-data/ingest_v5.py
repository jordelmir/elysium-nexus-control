#!/usr/bin/env python3
"""
Elysium Nexus — IR Data Fabric Ingestion Pipeline v5 (Schema v4 Native)
========================================================================
Parses ALL 5 authorized IR data repositories directly into Schema v4:
- Deterministic SHA-256 text PKs (no integer autoincrement)
- Groups commands into complete multi-command code_sets
- Validates raw microsecond patterns with strict fail-closed criteria
- Compresses RAW patterns into zlib level-9 pattern_blob
- Populates source_revisions and source_files for full provenance

Sources:
  1. Flipper-IRDB (.ir files — parsed + raw)
  2. SmartIR (JSON — Broadlink Base64 + raw arrays)
  3. probonopd/irdb (CSV — protocol parametric)
  4. radioxoma/infrared (LIRC .conf + irplus .xml)
  5. IrpProtocols.xml (protocol dictionary — metadata only)

Output:
  - ir_catalog.db (Schema v4 SQLite)
  - ir_catalog_stats.json (manifest with stats)
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
import zlib
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

# ─── Paths ────────────────────────────────────────────────────────────────────
ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = ROOT / ".cache" / "ir-sources"
# V0.6.2 Phase 1: clean-room builds redirect artifact output.
_DEFAULT_OUTPUT_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
OUTPUT_DIR = Path(os.environ.get("IR_CATALOG_OUTPUT_DIR") or _DEFAULT_OUTPUT_DIR)
DB_PATH = OUTPUT_DIR / "ir_catalog.db"
STATS_PATH = OUTPUT_DIR / "ir_catalog_stats.json"
REJECTIONS_PATH = OUTPUT_DIR / "ir_catalog_rejections.json"
SCHEMA_PATH = ROOT / "ir-data" / "schema" / "catalog-v5.sql"

sys.path.insert(0, str(ROOT / "tools" / "ir-data"))
import export_canonical_catalog


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def short(text: str) -> str:
    return sha256_text(text)[:16]


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

_ACTION_REVERSE: dict[str, str] = {}
for _canonical, _aliases in ACTION_ALIASES.items():
    for _alias in _aliases:
        _ACTION_REVERSE[_alias.lower().strip()] = _canonical
    _ACTION_REVERSE[_canonical.lower().strip()] = _canonical


def normalize_action(raw_name: str) -> str:
    key = raw_name.lower().strip().replace("_", " ").replace("-", " ")
    if key in _ACTION_REVERSE:
        return _ACTION_REVERSE[key]
    key_us = key.replace(" ", "_")
    if key_us in _ACTION_REVERSE:
        return _ACTION_REVERSE[key_us]
    if key.startswith("key "):
        sub = key[4:]
        if sub in _ACTION_REVERSE:
            return _ACTION_REVERSE[sub]
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
    "universal_tv_remotes": "TV",
    "cd_players": "CD_Player", "cd player": "CD_Player",
    "consoles": "Console", "console": "Console",
    "mp3 player": "MP3_Player", "mp3": "MP3_Player",
    "digital jukebox": "MP3_Player",
    "vcr": "VCR", "vtr": "VCR",
    "amplifier": "Amplifier", "amp": "Amplifier",
    "misc": "Miscellaneous", "miscellaneous": "Miscellaneous",
    "receiver": "AV_Receiver",
    # TV-family device types that must join the universal TV sweep pool.
    "unknown_tv": "TV", "unknown_dtv": "TV", "unknown_sonytv": "TV",
    "plasma": "TV", "plasma displays": "TV", "plasma_display": "TV",
    "led_tv": "TV", "rear projection dlp tv": "TV", "rear_projection_tv": "TV",
    "projection tv": "TV", "lcd tv": "TV",
}


def normalize_device_type(raw_type: str) -> str:
    key = raw_type.lower().strip().replace("-", "_")
    return DEVICE_TYPE_ALIASES.get(key, raw_type.strip())


# ─── Protocol Normalization ───────────────────────────────────────────────────
PROTOCOL_MAP: dict[str, tuple[str, int]] = {
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
    "aiwa": ("Aiwa", 38123),
    "raw": ("RAW", 38000),
    "broadlink": ("Broadlink", 38000),
}


def normalize_protocol(raw_proto: str) -> tuple[str | None, int]:
    """§7 (PTG-02): unknown protocols are REJECTED, never defaulted to 38 kHz.
    Known protocols with no explicit carrier fall back to their canonical
    carrier from PROTOCOL_MAP — that is normalization, not fabrication."""
    key = raw_proto.lower().strip()
    if key in PROTOCOL_MAP:
        return PROTOCOL_MAP[key]
    return (None, None)


# ─── PTG-02 §8: Provenance Lock Authority ────────────────────────────────────
SOURCES_LOCK_PATH = ROOT / "ir-data" / "sources.lock.json"


def load_source_lock() -> dict[str, dict]:
    """sources.lock.json is the provenance authority (§8). Production builds
    may only reference sources present in the lock; every revision inherits
    its identity (commit/tree/content/license hashes) from it."""
    if not SOURCES_LOCK_PATH.exists():
        return {}
    try:
        data = json.loads(SOURCES_LOCK_PATH.read_text(encoding="utf-8"))
        return {s["id"]: s for s in data.get("sources", [])}
    except Exception:
        return {}


class RejectionCollector:
    """§7: structured ingestion rejections. Every rejected unit is recorded
    with source, file, row, reason, detail, action and protocol. The rejection
    artifact joins the build identity (catalog.py hashes it into catalogBuildId)."""

    def __init__(self) -> None:
        self._by_row: list[dict] = []

    REJECTION_KIND = {
        "MALFORMED_ADDRESS": "MALFORMED_ADDRESS",
        "MALFORMED_COMMAND": "MALFORMED_COMMAND",
        "MISSING_CARRIER": "MISSING_CARRIER",
        "INVALID_CARRIER": "INVALID_CARRIER",
        "UNSUPPORTED_PROTOCOL": "UNSUPPORTED_PROTOCOL",
        "AMBIGUOUS_VARIANT": "AMBIGUOUS_VARIANT",
        "MALFORMED_RAW": "MALFORMED_RAW",
        "RAW_DURATION_TOO_LONG": "RAW_DURATION_TOO_LONG",
        "UNSUPPORTED_ENCODING": "UNSUPPORTED_ENCODING",
        "STRUCTURAL": "STRUCTURE",
    }

    def add(self, source_id: str, file: str, row: int, reason: str,
            detail: str = "", action: str = "", protocol: str = "",
            source_file_id: str | None = None) -> None:
        self._by_row.append({
            "source": source_id, "file": file, "row": row, "reason": reason,
            "detail": detail[:200], "action": action, "protocol": protocol,
            "source_file_id": source_file_id,
        })

    def write_rows(self, cur) -> None:
        """V0.6.1 §1/§4: rejections are part of the catalog truth — every
        rejected unit lands in catalog_rejections (deterministic epoch 0)."""
        for r in self._by_row:
            rid = sha256_text("|".join([
                "rej-v1", r["source"], str(r["file"]), str(r["row"]),
                r["reason"], r["detail"], r["action"], r["protocol"],
            ]))
            cur.execute(
                "INSERT OR IGNORE INTO catalog_rejections ("
                "id, source_file_id, reason, rejection_kind, rejection_epoch_ms"
                ") VALUES (?, ?, ?, ?, 0)",
                (rid, r.get("source_file_id"), r["reason"],
                 self.REJECTION_KIND.get(r["reason"], "OTHER")))

    def counts(self) -> dict[str, int]:
        counts: dict[str, int] = {}
        for r in self._by_row:
            counts[r["reason"]] = counts.get(r["reason"], 0) + 1
        return counts

    def write_manifest(self, path: Path, profile: str) -> None:
        manifest = {
            "schemaVersion": 5,
            "profile": profile,
            "generatedAtUtc": "2026-08-08T00:00:00Z",
            "totalRejections": len(self._by_row),
            "byReason": self.counts(),
            "rejections": self._by_row,
        }
        path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False,
                                   default=str) + "\n")


# ─── Broadlink Base64 Decoder ─────────────────────────────────────────────────
def decode_broadlink_base64(b64_str: str) -> tuple[int, list[int]]:
    try:
        data = base64.b64decode(b64_str)
    except Exception:
        return (0, [])
    if len(data) < 6 or data[0] != 0x26:
        return (0, [])
    length = data[2] | (data[3] << 8)
    carrier_hz = 38000
    pattern = []
    i = 4
    end = min(4 + length * 2, len(data))
    while i < end:
        if i >= len(data):
            break
        val = data[i]
        i += 1
        if val == 0:
            if i + 1 < len(data):
                val = (data[i] << 8) | data[i + 1]
                i += 2
            else:
                break
        us = int(val * 8192 / 269)
        if us > 0:
            pattern.append(us)
    return (carrier_hz, pattern)


# ─── Physical Validation ──────────────────────────────────────────────────────
def validate_raw_pattern(pattern: list[int], carrier_hz: int) -> bool:
    if not pattern or len(pattern) < 2:
        return False
    if any(d <= 0 for d in pattern):
        return False
    total_us = sum(pattern)
    if total_us > 2_000_000:
        return False
    if carrier_hz <= 0:
        return False
    return True


# ─── Counters ─────────────────────────────────────────────────────────────────
stats = defaultdict(int)


# ─── Database Schema (from catalog-v5.sql) ───────────────────────────────────
def init_database(conn: sqlite3.Connection):
    ddl_sql = SCHEMA_PATH.read_text(encoding="utf-8")
    conn.executescript(ddl_sql)
    conn.commit()


# ─── V5 §14: Protocol Definitions & Variants ─────────────────────────────────
# Seeded from PROTOCOL_MAP — the same single authority the codec gate uses.
#
# V06.3: Variant name normalization map. Catalog keys (lowercase, no underscores)
# must match runtime ProtocolCodecRegistry variant IDs (UPPERCASE with underscores).
VARIANT_NAME_MAP: dict[str, str] = {
    "sirc": "SIRC_12",
    "sirc15": "SIRC_15",
    "sirc20": "SIRC_20",
    "sony12": "SIRC_12",
    "sony15": "SIRC_15",
    "sony20": "SIRC_20",
    "nec": "NEC_32",
    "nec1": "NEC_32",
    "nec42": "NEC_42",
    "nec48": "NEC_48",
    "necext": "NECx_32",
    "necx": "NECx_32",
    "necx2": "NECx_32",
    "samsung32": "SAMSUNG_32",
    "samsung": "SAMSUNG_32",
    "samsung36": "SAMSUNG_36",
    "rc5": "RC5_14",
    "rc5x": "RC5X_16",
    "rc6": "RC6_16",
    "kaseikyo": "KASEIKYO_48",
    "panasonic": "KASEIKYO_48",
    "panasonic_old": "KASEIKYO_48",
    "aiwa": "AIWA_42",
}


def seed_protocol_definitions(conn: sqlite3.Connection):
    seen_variants = set()
    for key, (family_name, carrier_hz) in sorted(PROTOCOL_MAP.items()):
        proto_id = short(f"proto:{family_name}")
        conn.execute(
            "INSERT OR IGNORE INTO protocol_definitions "
            "(id, family_name, display_name, carrier_hz, encoding_kind) "
            "VALUES (?, ?, ?, ?, 'PARAMETRIC')",
            (proto_id, family_name, family_name, carrier_hz))
        # V06.3: variant identity derives from the NORMALIZED name so the
        # id matches the one computed by insert_signal_parametric().
        normalized_name = VARIANT_NAME_MAP.get(key, key)
        variant_id = short(f"variant:{family_name}:{normalized_name}")
        if (family_name, normalized_name) not in seen_variants:
            seen_variants.add((family_name, normalized_name))
            conn.execute(
                "INSERT OR IGNORE INTO protocol_variants "
                "(id, protocol_id, variant_name) VALUES (?, ?, ?)",
                (variant_id, proto_id, normalized_name))
    conn.commit()


# ─── V4 Entity Caches ────────────────────────────────────────────────────────
class EntityCache:
    def __init__(self, cur: sqlite3.Cursor, profile: str = "production",
                 lock: dict | None = None,
                 rejections: RejectionCollector | None = None):
        self.cur = cur
        self.profile = profile
        self.lock: dict[str, dict] = lock if lock is not None else {}
        self.rejections: RejectionCollector = rejections if rejections is not None else RejectionCollector()
        # V0.6.1 §5: signal eligibility floor tracked at insert time so code-set
        # eligibility derives from its own physical stock (never guessed later).
        self._signal_eligibility: dict[str, str] = {}
        self.brands: dict[str, str] = {}
        self.device_types: dict[str, str] = {}
        self.actions: dict[str, str] = {}
        self.signals: dict[str, str] = {}
        self._sources: dict[str, str] = {}
        self._revisions: dict[str, str] = {}
        self._files: dict[str, str] = {}

    def get_or_create_brand(self, name: str) -> str:
        b_norm = normalize_brand(name)
        b_key = b_norm.lower()
        if b_key not in self.brands:
            b_id = short(f"brand:{b_key}")
            self.cur.execute(
                "INSERT OR IGNORE INTO brands (id, normalized_name, display_name) "
                "VALUES (?, ?, ?)",
                (b_id, b_key, b_norm))
            self.brands[b_key] = b_id
        return self.brands[b_key]

    def get_or_create_device_type(self, name: str) -> str:
        dt_norm = normalize_device_type(name)
        dt_key = dt_norm.lower()
        if dt_key not in self.device_types:
            dt_id = short(f"dtype:{dt_key}")
            self.cur.execute(
                "INSERT OR IGNORE INTO device_types (id, canonical_name) VALUES (?, ?)",
                (dt_id, dt_norm))
            self.device_types[dt_key] = dt_id
        return self.device_types[dt_key]

    def get_or_create_action(self, canonical_key: str) -> str:
        if canonical_key not in self.actions:
            act_id = short(f"action:{canonical_key}")
            self.cur.execute(
                "INSERT OR IGNORE INTO actions (id, canonical_key, action_family) "
                "VALUES (?, ?, 'STANDARD')",
                (act_id, canonical_key))
            self.actions[canonical_key] = act_id
        return self.actions[canonical_key]

    def insert_signal_parametric(self, proto: str, carrier_hz: int,
                                  addr: int, sub_device: int = -1,
                                  cmd: int = -1, *,
                                  carrier_evidence: str = "UNKNOWN",
                                  source_file_id: str | None = None,
                                  eligibility: str = "PROBE_ELIGIBLE",
                                  protocol_variant_id: str | None = None) -> str:
        """V0.6.1 §3: full-SHA256 content-addressed physical identity. The
        identity is PHYSICAL: encoding version, codec, variant, carrier and
        parameters — not a storage representation. Changing any physical
        parameter changes the id; changing storage never does."""
        if protocol_variant_id is None:
            family_key = next(
                (k for k, (fam, _) in PROTOCOL_MAP.items()
                 if fam == proto and k == proto.lower()),
                next((k for k, (fam, _) in PROTOCOL_MAP.items()
                      if fam == proto), None))
            if family_key:
                # V06.3: use normalized variant name so variant_id matches
                # the one created by seed_protocol_definitions()
                normalized_key = VARIANT_NAME_MAP.get(family_key, family_key)
                protocol_variant_id = short(f"variant:{proto}:{normalized_key}")
        sig_key = "|".join([
            "pid-v1", proto, protocol_variant_id or "-",
            str(carrier_hz), str(addr), str(sub_device), str(cmd),
            "r0", "tg-",  # repeat policy / toggle semantics (not yet modeled)
        ])
        sig_id = sha256_text(sig_key)
        if sig_id not in self.signals:
            self.cur.execute(
                "INSERT OR IGNORE INTO signals ("
                "id, encoding_type, codec_id, protocol_name_original, "
                "carrier_hz, address_value, sub_device_value, command_value, "
                "repeat_count, physical_sha256, canonical_sha256, "
                "runtime_status, validation_status, protocol_definition_id, "
                "protocol_variant_id, carrier_evidence, evidence_level, "
                "eligibility_status"
                ") VALUES (?, 'PARAMETRIC', ?, ?, ?, ?, ?, ?, 0, ?, ?, "
                "'SUPPORTED_PARAMETRIC', 'PASSED', ?, ?, ?, "
                "'SOURCE_IMPORTED', ?)",
                (sig_id, proto, proto, carrier_hz, addr, sub_device, cmd,
                 sig_id, sig_id, short(f"proto:{proto}"),
                 protocol_variant_id, carrier_evidence, eligibility))
            self.signals[sig_id] = True
            self._signal_eligibility[sig_id] = eligibility
            if source_file_id:
                self.cur.execute(
                    "INSERT OR IGNORE INTO signal_sources "
                    "(signal_id, source_file_id) VALUES (?, ?)",
                    (sig_id, source_file_id))
        return sig_id

    def insert_signal_raw(self, carrier_hz: int, pattern: list[int], *,
                          carrier_evidence: str = "UNKNOWN",
                          source_file_id: str | None = None,
                          eligibility: str = "PROBE_ELIGIBLE") -> str:
        """V0.6.1 §3 (P0-10): RAW physical identity is storage-agnostic — it
        hashes the EXACT uncompressed duration sequence plus carrier. zlib or
        any future codec can change without touching physical identity."""
        packed = struct.pack(f"<{len(pattern)}I", *pattern)
        timing = "|".join(str(d) for d in pattern)
        sig_key = "|".join(["rpid-v1", str(carrier_hz), timing])
        sig_id = sha256_text(sig_key)
        if sig_id not in self.signals:
            compressed = zlib.compress(packed, 9)
            self.cur.execute(
                "INSERT OR IGNORE INTO signals ("
                "id, encoding_type, protocol_name_original, carrier_hz, "
                "pattern_blob, compression, slice_count, duration_us, "
                "uncompressed_bytes, physical_sha256, canonical_sha256, "
                "runtime_status, validation_status, carrier_evidence, "
                "evidence_level, eligibility_status"
                ") VALUES (?, 'RAW', 'RAW', ?, ?, 'zlib', ?, ?, ?, ?, ?, "
                "'SUPPORTED_RAW', 'PASSED', ?, 'SOURCE_IMPORTED', ?)",
                (sig_id, carrier_hz, compressed, len(pattern), sum(pattern),
                 len(packed), sig_id, sig_id, carrier_evidence, eligibility))
            self.signals[sig_id] = True
            self._signal_eligibility[sig_id] = eligibility
            if source_file_id:
                self.cur.execute(
                    "INSERT OR IGNORE INTO signal_sources "
                    "(signal_id, source_file_id) VALUES (?, ?)",
                    (sig_id, source_file_id))
        return sig_id

    def ensure_source(self, source_id: str, display_name: str,
                      repo_url: str, license_id: str,
                      production_enabled: bool) -> str:
        if source_id not in self._sources:
            prod = 1 if production_enabled else 0
            self.cur.execute(
                "INSERT OR IGNORE INTO sources (id, display_name, "
                "repository_url, license_id, production_approved) "
                "VALUES (?, ?, ?, ?, ?)",
                (source_id, display_name, repo_url, license_id, prod))
            self._sources[source_id] = source_id
        return source_id

    def ensure_revision(self, source_id: str, commit_sha: str,
                        tree_sha: str = "") -> str:
        """§8 (PTG-02) fail-closed: in production the lock is the sole source
        of revision identity. A source absent from sources.lock.json cannot
        produce a revision — the build stops instead of fabricating hashes."""
        entry = self.lock.get(source_id)
        if entry:
            resolved_commit = entry.get("resolvedCommit") or commit_sha
            resolved_tree = entry.get("resolvedTree") or tree_sha
            content_sha = entry.get("sourceContentSha256") or "0" * 64
            license_sha = entry.get("licenseFileSha256") or "0" * 64
        else:
            if self.profile == "production":
                raise RuntimeError(
                    f"PTG-02 §8: source '{source_id}' absent from "
                    f"sources.lock.json — fail-closed: no production revision "
                    "without lock provenance")
            resolved_commit, resolved_tree = commit_sha, tree_sha
            content_sha, license_sha = "0" * 64, "0" * 64
        rev_key = f"{source_id}:{resolved_commit}"
        if rev_key not in self._revisions:
            rev_id = short(rev_key)
            self.cur.execute(
                "INSERT OR IGNORE INTO source_revisions ("
                "id, source_id, commit_sha, tree_sha, "
                "content_sha256, license_sha256"
                ") VALUES (?, ?, ?, ?, ?, ?)",
                (rev_id, source_id, resolved_commit,
                 resolved_tree, content_sha, license_sha))
            self._revisions[rev_key] = rev_id
            # Also store as HEAD so callers can look up by HEAD
            self._revisions[f"{source_id}:HEAD"] = rev_id
        return self._revisions[rev_key]

    def ensure_file(self, source_id: str, rel_path: str,
                    content_sha: str = "") -> str:
        """§8 (PTG-02): license APPROVED is never asserted without evidence.
        Only a lock entry carrying licenseFileSha256 AND sourceContentSha256
        makes the file APPROVED; anything else stays AWAITING_EVIDENCE."""
        f_key = f"{source_id}:{rel_path}"
        if f_key not in self._files:
            rev_id = self._revisions.get(f"{source_id}:HEAD", "")
            file_id = short(f_key)
            entry = self.lock.get(source_id) or {}
            has_license_evidence = bool(
                entry.get("licenseFileSha256") and entry.get("sourceContentSha256"))
            license_status = "APPROVED" if has_license_evidence else "AWAITING_EVIDENCE"
            self.cur.execute(
                "INSERT OR IGNORE INTO source_files ("
                "id, source_revision_id, relative_path, "
                "content_sha256, license_status"
                ") VALUES (?, ?, ?, ?, ?)",
                (file_id, rev_id, rel_path,
                 content_sha or entry.get("sourceContentSha256") or "0" * 64,
                 license_status))
            self._files[f_key] = file_id
        return self._files[f_key]

    def create_remote(self, source_id: str, b_id: str, dt_id: str,
                      model: str, remote_model: str, file_id: str) -> str:
        remote_id = short(f"remote:{source_id}:{b_id}:{dt_id}:{remote_model}")
        self.cur.execute(
            "INSERT OR IGNORE INTO remotes ("
            "id, source_file_id, brand_id, device_type_id, "
            "normalized_remote_model, display_remote_model"
            ") VALUES (?, ?, ?, ?, ?, ?)",
            (remote_id, file_id, b_id, dt_id, model.lower(), remote_model))
        return remote_id

    def create_code_set(self, remote_id: str, rev_id: str,
                        protocol_family: str, num_commands: int,
                        binding_specs: list[tuple[str, str, str, str]] | None = None) -> str:
        """V0.6.1 §3 (P0-9): content-addressed code set. The identity covers
        schema version, source revision, remote identity and the SORTED set of
        authoritative bindings (action|signalPhysicalId|repeat|press). Any
        physical change to a binding produces a NEW code set id."""
        specs = binding_specs or [("?", "?", "?", "?")]
        material = "|".join([
            "csid-v1", rev_id, remote_id,
            *sorted(f"{a}|{s}|{r}|{p}" for a, s, r, p in specs),
        ])
        cs_id = sha256_text(material)
        el = "PROBE_ELIGIBLE"
        for _, sig_id, _, _ in specs:
            if self._signal_eligibility.get(sig_id, "RESEARCH_ONLY") != "PROBE_ELIGIBLE":
                el = "RESEARCH_ONLY"
        self.cur.execute(
            "INSERT OR IGNORE INTO code_sets ("
            "id, remote_id, source_revision_id, protocol_family, "
            "verification_status, runtime_status, eligibility_status, "
            "evidence_level"
            ") VALUES (?, ?, ?, ?, 'INTERNAL_UNVERIFIED', 'ACTIVE', ?, "
            "'SOURCE_IMPORTED')",
            (cs_id, remote_id, rev_id, protocol_family, el))
        return cs_id

    def create_binding(self, cs_id: str, act_id: str, sig_id: str,
                       repeat_policy: str = "FULL_FRAME",
                       press_type: str = "SINGLE_TAP") -> str:
        bnd_id = sha256_text("|".join(
            ["bnd-v1", cs_id, act_id, sig_id, repeat_policy, press_type]))
        self.cur.execute(
            "INSERT OR IGNORE INTO command_bindings ("
            "id, code_set_id, action_id, signal_id, "
            "repeat_policy, press_type"
            ") VALUES (?, ?, ?, ?, ?, ?)",
            (bnd_id, cs_id, act_id, sig_id, repeat_policy, press_type))
        return bnd_id


# ─── Parser 1: Flipper-IRDB ──────────────────────────────────────────────────
def ingest_flipper(conn: sqlite3.Connection, cache: EntityCache):
    flipper_root = CACHE / "flipper-irdb"
    if not flipper_root.exists():
        print("  ⚠ Flipper-IRDB not found, skipping")
        return

    source_id = "flipper-irdb"
    cache.ensure_source(source_id, "Flipper-IRDB",
                        "https://github.com/Lucaslhm/Flipper-IRDB", "CC0-1.0",
                        True)
    rev_id = cache.ensure_revision(source_id, "d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d")

    ir_files = list(flipper_root.rglob("*.ir"))
    print(f"  Found {len(ir_files)} .ir files")
    stats["flipper_files"] = len(ir_files)

    remote_commands: dict[str, list[tuple[str, str, str]]] = {}
    remote_meta: dict[str, tuple[str, str, str]] = {}

    for ir_file in ir_files:
        if "_Converted_" in str(ir_file):
            stats["flipper_skipped_converted"] += 1
            continue

        rel_path = str(ir_file.relative_to(flipper_root))
        parts = ir_file.relative_to(flipper_root).parts
        device_type = parts[0] if len(parts) >= 3 else "TV"
        brand = parts[1] if len(parts) >= 3 else (
            parts[0] if len(parts) == 2 else "Unknown")
        remote_model = ir_file.stem

        b_id = cache.get_or_create_brand(brand)
        dt_id = cache.get_or_create_device_type(device_type)
        file_id = cache.ensure_file(source_id, rel_path)
        remote_id = cache.create_remote(source_id, b_id, dt_id,
                                         remote_model.lower(), remote_model, file_id)
        remote_meta[remote_id] = (b_id, dt_id, remote_model)

        try:
            text = ir_file.read_text(encoding="utf-8", errors="replace")
        except Exception:
            stats["parse_errors"] += 1
            continue

        if remote_id not in remote_commands:
            remote_commands[remote_id] = []

        curr_name = None
        curr_type = None
        curr_proto = None
        curr_addr = None
        curr_cmd = None
        curr_freq = None
        curr_data = None

        def flush(row: int = 0):
            nonlocal curr_name, curr_type, curr_proto, curr_addr
            nonlocal curr_cmd, curr_freq, curr_data
            if curr_name and curr_type:
                action = normalize_action(curr_name)
                if curr_type == "parsed" and curr_proto:
                    if curr_addr is None:
                        cache.rejections.add(
                            source_id, rel_path, row, "MALFORMED_ADDRESS",
                            detail=f"non-hex address for '{curr_name}'",
                            action=action, protocol=curr_proto,
                            source_file_id=file_id)
                        stats["malformed_address_rejected"] += 1
                    if curr_cmd is None:
                        cache.rejections.add(
                            source_id, rel_path, row, "MALFORMED_COMMAND",
                            detail=f"non-hex command for '{curr_name}'",
                            action=action, protocol=curr_proto,
                            source_file_id=file_id)
                        stats["malformed_command_rejected"] += 1
                    proto_name, default_hz = normalize_protocol(curr_proto)
                    if proto_name is None:
                        cache.rejections.add(
                            source_id, rel_path, row, "UNSUPPORTED_PROTOCOL",
                            detail=f"unknown protocol '{curr_proto}'",
                            action=action, protocol=curr_proto,
                            source_file_id=file_id)
                        stats["unsupported_protocol_rejected"] += 1
                    elif curr_addr is not None and curr_cmd is not None:
                        if curr_freq is None and default_hz is not None:
                            stats["normative_carrier_default"] += 1
                        if curr_freq is None and default_hz is None:
                            cache.rejections.add(
                                source_id, rel_path, row, "MISSING_CARRIER",
                                detail=f"no frequency for '{curr_name}'",
                                action=action, protocol=proto_name,
                                source_file_id=file_id)
                            stats["missing_carrier_rejected"] += 1
                        else:
                            carrier = curr_freq if curr_freq else default_hz
                            sig_id = cache.insert_signal_parametric(
                                proto_name, carrier, curr_addr, -1, curr_cmd,
                                carrier_evidence=(
                                    "SOURCE_DECLARED" if curr_freq
                                    else "FORMAT_NORMATIVE"),
                                source_file_id=file_id)
                            remote_commands[remote_id].append(
                                (action, sig_id, proto_name,
                                 "FULL_FRAME", "SINGLE_TAP"))
                            stats["inserted_parametric"] += 1
                elif curr_type == "raw" and curr_data:
                    if curr_freq is None:
                        cache.rejections.add(
                            source_id, rel_path, row, "MISSING_CARRIER",
                            detail=f"raw '{curr_name}' without frequency",
                            action=action, protocol="RAW",
                            source_file_id=file_id)
                        stats["missing_carrier_rejected"] += 1
                    elif not validate_raw_pattern(curr_data, curr_freq):
                        kind = ("RAW_DURATION_TOO_LONG"
                                if max(curr_data) > 250_000
                                else "MALFORMED_RAW")
                        cache.rejections.add(
                            source_id, rel_path, row, kind,
                            detail=f"invalid raw '{curr_name}'",
                            action=action, protocol="RAW",
                            source_file_id=file_id)
                        stats["raw_rejected"] += 1
                    else:
                        sig_id = cache.insert_signal_raw(
                            curr_freq, curr_data,
                            carrier_evidence="SOURCE_DECLARED",
                            source_file_id=file_id)
                        remote_commands[remote_id].append(
                            (action, sig_id, "RAW", "FULL_FRAME", "SINGLE_TAP"))
                        stats["inserted_raw"] += 1
            curr_name = None
            curr_type = None
            curr_proto = None
            curr_addr = None
            curr_cmd = None
            curr_freq = None
            curr_data = None

        for line_no, line in enumerate(text.splitlines(), start=1):
            line = line.strip()
            if not line or line.startswith("Filetype:") or line.startswith("Version:"):
                continue
            if line.startswith("#"):
                flush(row=line_no)
                continue
            kv = line.split(":", 1)
            if len(kv) != 2:
                continue
            key, val = kv[0].strip(), kv[1].strip()
            if key == "name":
                flush(row=line_no)
                curr_name = val
            elif key == "type":
                curr_type = val
            elif key == "protocol":
                curr_proto = val
            elif key == "address":
                try:
                    curr_addr = int(val.strip().split()[0], 16)
                except (ValueError, IndexError):
                    curr_addr = None
            elif key == "command":
                try:
                    curr_cmd = int(val.strip().split()[0], 16)
                except (ValueError, IndexError):
                    curr_cmd = None
            elif key == "frequency":
                try:
                    curr_freq = int(val)
                except ValueError:
                    cache.rejections.add(
                        source_id, rel_path, line_no, "INVALID_CARRIER",
                        detail=f"non-integer frequency '{val}'",
                        source_file_id=file_id)
                    stats["invalid_carrier_rejected"] += 1
                    curr_freq = None
            elif key == "data":
                try:
                    curr_data = [int(x) for x in val.split()]
                except ValueError:
                    curr_data = None
        flush()

    total = 0
    for remote_id, cmds in remote_commands.items():
        if not cmds:
            continue
        b_id, dt_id, remote_model = remote_meta[remote_id]
        rev_id = cache._revisions.get(f"{source_id}:HEAD", short(f"{source_id}:HEAD"))
        proto_family = cmds[0][2] if cmds else "RAW"
        specs = [(act_key, sig_id, rp, pt) for act_key, sig_id, _, rp, pt in cmds]
        cs_id = cache.create_code_set(remote_id, rev_id, proto_family,
                                      len(cmds), binding_specs=specs)
        for act_key, sig_id, _, _, _ in cmds:
            act_id = cache.get_or_create_action(act_key)
            cache.create_binding(cs_id, act_id, sig_id)
            total += 1
    conn.commit()
    stats["flipper_total_commands"] = total
    print(f"  ✓ Flipper: {total} commands from {stats['flipper_files']} files")


# ─── Parser 2: SmartIR ───────────────────────────────────────────────────────
def ingest_smartir(conn: sqlite3.Connection, cache: EntityCache):
    smartir_root = CACHE / "smartir" / "codes"
    if not smartir_root.exists():
        print("  ⚠ SmartIR not found, skipping")
        return

    source_id = "smartir"
    cache.ensure_source(source_id, "SmartIR",
                        "https://github.com/smartHomeHub/SmartIR", "MIT", True)
    cache.ensure_revision(source_id, "HEAD")

    json_files = list(smartir_root.rglob("*.json"))
    print(f"  Found {len(json_files)} SmartIR JSON files")
    stats["smartir_files"] = len(json_files)

    total = 0
    for jf in json_files:
        try:
            data = json.loads(jf.read_text(encoding="utf-8", errors="replace"))
        except (json.JSONDecodeError, Exception):
            stats["parse_errors"] += 1
            continue

        manufacturer = normalize_brand(data.get("manufacturer", "Unknown"))
        models = data.get("supportedModels", [])
        model_str = ", ".join(models[:3]) if models else jf.stem
        encoding = data.get("commandsEncoding", "").lower()
        parent = jf.parent.name
        device_type = normalize_device_type(parent)

        b_id = cache.get_or_create_brand(manufacturer)
        dt_id = cache.get_or_create_device_type(device_type)
        rel_path = str(jf.relative_to(CACHE / "smartir"))
        file_id = cache.ensure_file(source_id, rel_path)
        remote_id = cache.create_remote(source_id, b_id, dt_id,
                                         jf.stem, model_str, file_id)

        remote_cmds: list[tuple[str, str, str, str, str]] = []
        commands = data.get("commands", {})
        n = _process_smartir_commands(cache, remote_cmds, commands, encoding,
                                      source_file_id=file_id, rel_path=rel_path)
        if remote_cmds:
            rev_id_r = cache._revisions.get(f"{source_id}:HEAD", short(f"{source_id}:HEAD"))
            specs = [(a, s, rp, pt) for a, s, _, rp, pt in remote_cmds]
            cs_id = cache.create_code_set(remote_id, rev_id_r, "RAW",
                                          len(remote_cmds), binding_specs=specs)
            for act_key, sig_id, _, _, _ in remote_cmds:
                act_id = cache.get_or_create_action(act_key)
                cache.create_binding(cs_id, act_id, sig_id)
        total += n
        conn.commit()

    stats["smartir_total_commands"] = total
    print(f"  ✓ SmartIR: {total} commands from {stats['smartir_files']} files")


def _process_smartir_commands(cache: EntityCache,
                              remote_cmds: list[tuple[str, str, str, str, str]],
                              commands: dict, encoding: str,
                              prefix: str = "",
                              source_file_id: str | None = None,
                              rel_path: str = "") -> int:
    count = 0
    for key, value in commands.items():
        action_name = f"{prefix}{key}" if prefix else key
        if isinstance(value, str):
            count += _decode_smartir_value(cache, remote_cmds, action_name,
                                           value, encoding, source_file_id,
                                           rel_path, 0)
        elif isinstance(value, list):
            for v in value:
                if isinstance(v, str):
                    count += _decode_smartir_value(cache, remote_cmds,
                                                   action_name, v, encoding,
                                                   source_file_id, rel_path, 0)
        elif isinstance(value, dict):
            for sub_key, sub_val in value.items():
                sub_action = f"{action_name}_{sub_key}"
                if isinstance(sub_val, str):
                    count += _decode_smartir_value(cache, remote_cmds,
                                                   sub_action, sub_val, encoding,
                                                   source_file_id, rel_path, 0)
                elif isinstance(sub_val, list):
                    for v in sub_val:
                        if isinstance(v, str):
                            count += _decode_smartir_value(cache, remote_cmds,
                                                           sub_action, v, encoding,
                                                           source_file_id, rel_path, 0)
                elif isinstance(sub_val, dict):
                    count += _process_smartir_commands(
                        cache, remote_cmds, {sub_key: sub_val}, encoding,
                        f"{action_name}_", source_file_id, rel_path)
    return count


def _decode_smartir_value(cache: EntityCache,
                          remote_cmds: list[tuple[str, str, str, str, str]],
                          action_name: str, value: str,
                          encoding: str, source_file_id: str | None,
                          rel_path: str, row: int) -> int:
    """V0.6.1 §4: SmartIR never assumes. Broadlink packets are 38 kHz by
    format (FORMAT_NORMATIVE); JSON raw lists are 38 kHz by SmartIR spec;
    undecodable values become typed rejections, not silent drops."""
    action = normalize_action(action_name)
    if encoding == "base64" or (len(value) > 20 and not value.startswith("[")):
        carrier_hz, pattern = decode_broadlink_base64(value)
        if carrier_hz > 0 and validate_raw_pattern(pattern, carrier_hz):
            sig_id = cache.insert_signal_raw(
                carrier_hz, pattern,
                carrier_evidence="FORMAT_NORMATIVE",
                source_file_id=source_file_id)
            remote_cmds.append((action, sig_id, "RAW", "FULL_FRAME", "SINGLE_TAP"))
            stats["inserted_raw"] += 1
            return 1
        cache.rejections.add(
            "smartir", rel_path, row, "MALFORMED_RAW",
            detail="undecodable broadlink value", action=action,
            protocol="RAW", source_file_id=source_file_id)
        stats["raw_rejected"] += 1
    elif encoding == "raw" or value.startswith("["):
        try:
            pattern = json.loads(value)
            if isinstance(pattern, list) and all(isinstance(x, int) for x in pattern):
                if not pattern:
                    cache.rejections.add(
                        "smartir", rel_path, row, "MALFORMED_RAW",
                        detail="empty raw pattern", action=action,
                        protocol="RAW", source_file_id=source_file_id)
                    stats["raw_rejected"] += 1
                    return 0
                if min(pattern) < 0:
                    cache.rejections.add(
                        "smartir", rel_path, row, "MALFORMED_RAW",
                        detail=f"negative duration {min(pattern)}µs",
                        action=action, protocol="RAW",
                        source_file_id=source_file_id)
                    stats["raw_rejected"] += 1
                    return 0
                if max(pattern) > 250_000:
                    cache.rejections.add(
                        "smartir", rel_path, row, "RAW_DURATION_TOO_LONG",
                        detail=f"max duration {max(pattern)}µs", action=action,
                        protocol="RAW", source_file_id=source_file_id)
                    stats["raw_rejected"] += 1
                    return 0
                # SmartIR spec: raw command lists are 38 kHz timings.
                sig_id = cache.insert_signal_raw(
                    38000, pattern,
                    carrier_evidence="FORMAT_NORMATIVE",
                    source_file_id=source_file_id)
                remote_cmds.append((action, sig_id, "RAW", "FULL_FRAME", "SINGLE_TAP"))
                stats["inserted_raw"] += 1
                return 1
        except (json.JSONDecodeError, TypeError):
            pass
        cache.rejections.add(
            "smartir", rel_path, row, "MALFORMED_RAW",
            detail="raw value is not an int list", action=action,
            protocol="RAW", source_file_id=source_file_id)
        stats["raw_rejected"] += 1
    return 0


# ─── Parser 3: probonopd/irdb (CSV) ──────────────────────────────────────────
# irdb labels remotes with unknown device type as "Unknown_<model>". The
# universal TV sweep must reach TVs regardless of that label, so remotes from
# brands known to sell TVs are classified as "Unknown_tv" (→ TV) instead of
# being stranded outside the sweep pool. Non-TV brands stay "Unknown".
IRDB_TV_BRANDS = {
    "samsung", "lg", "sony", "panasonic", "philips", "tcl", "hisense",
    "konka", "telstar", "aiwa", "rca", "jvc", "xiaomi", "daewoo", "sanyo",
    "sharp", "toshiba", "vizio", "hitachi", "mitsubishi", "skyworth", "cce",
    "philco", "semp", "gradiente", "aoc", "westinghouse", "polaroid",
    "emerson", "funai", "magnavox", "sylvania", "apex", "haier", "insignia",
    "element", "dynex", "proscan", "orion", "coby", "craig", "benq",
    "viewsonic", "ge", "zenith", "goldstar", "gibralter", "radioshack",
}


def classify_irdb_device_type(raw_type: str, brand: str) -> str:
    """irdb second-level directories are the device type; "Unknown_<model>"
    means the type is unknown. Brands that sell TVs keep their unknown remotes
    reachable by the universal TV sweep (as Unknown_tv → TV)."""
    key = raw_type.lower().strip()
    if key.startswith("unknown_"):
        if brand.lower().strip() in IRDB_TV_BRANDS:
            return "Unknown_tv"
        return "Unknown"
    return raw_type


def ingest_probonopd(conn: sqlite3.Connection, cache: EntityCache):
    irdb_root = CACHE / "probonopd-irdb" / "codes"
    if not irdb_root.exists():
        print("  ⚠ probonopd/irdb not found, skipping")
        return

    source_id = "probonopd-irdb"
    lock_entry = cache.lock.get(source_id) or {}
    prod_enabled = bool(lock_entry.get("productionEnabled", False))
    cache.ensure_source(source_id, "probonopd/irdb",
                        "https://github.com/probonopd/irdb",
                        "LicenseRef-IRDB-CUSTOM", prod_enabled)
    cache.ensure_revision(source_id, "HEAD")

    csv_files = list(irdb_root.rglob("*.csv"))
    print(f"  Found {len(csv_files)} probonopd CSV files")
    stats["probonopd_files"] = len(csv_files)

    total = 0
    for csv_file in csv_files:
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

        device_type = classify_irdb_device_type(device_type, brand)

        b_id = cache.get_or_create_brand(brand)
        dt_id = cache.get_or_create_device_type(device_type)
        rel_path = str(csv_file.relative_to(CACHE / "probonopd-irdb"))
        file_id = cache.ensure_file(source_id, rel_path)
        remote_id = cache.create_remote(source_id, b_id, dt_id,
                                         remote_model.lower(), remote_model, file_id)

        remote_cmds: list[tuple[str, str, str]] = []
        try:
            with open(csv_file, "r", encoding="utf-8", errors="replace") as f:
                reader = csv.DictReader(f)
                for row_no, row in enumerate(reader, start=1):
                    fn = row.get("functionname", "").strip()
                    proto_raw = row.get("protocol", "").strip()
                    device_str = row.get("device", "0").strip()
                    subdevice_str = row.get("subdevice", "-1").strip()
                    func_str = row.get("function", "0").strip()
                    if not fn or not proto_raw:
                        continue
                    action = normalize_action(fn)
                    proto_name, carrier_hz = normalize_protocol(proto_raw)
                    if proto_name is None:
                        cache.rejections.add(
                            "probonopd-irdb", csv_file, row_no, "UNSUPPORTED_PROTOCOL",
                            detail=f"unknown protocol '{proto_raw}'",
                            action=action, protocol=proto_raw)
                        stats["unsupported_protocol_rejected"] += 1
                        continue
                    try:
                        address = int(device_str)
                        sub_device = int(subdevice_str) if subdevice_str != "-1" else -1
                        command = int(func_str)
                    except ValueError:
                        cache.rejections.add(
                            "probonopd-irdb", rel_path, row_no, "MALFORMED_ADDRESS",
                            detail=f"non-hex device/function in '{fn}'",
                            action=action, protocol=proto_raw,
                            source_file_id=file_id)
                        stats["malformed_address_rejected"] += 1
                        continue
                    # CSV carries no frequency: the carrier comes from the
                    # protocol format itself (normative), never guessed.
                    sig_id = cache.insert_signal_parametric(
                        proto_name, carrier_hz, address, sub_device, command,
                        carrier_evidence="FORMAT_NORMATIVE",
                        source_file_id=file_id)
                    remote_cmds.append((action, sig_id, proto_name,
                                        "FULL_FRAME", "SINGLE_TAP"))
                    total += 1
        except Exception:
            stats["parse_errors"] += 1

        if remote_cmds:
            rev_id_r = cache._revisions.get(f"{source_id}:HEAD", short(f"{source_id}:HEAD"))
            specs = [(a, s, rp, pt) for a, s, _, rp, pt in remote_cmds]
            cs_id = cache.create_code_set(remote_id, rev_id_r, remote_cmds[0][2],
                                          len(remote_cmds), binding_specs=specs)
            for act_key, sig_id, _, _, _ in remote_cmds:
                act_id = cache.get_or_create_action(act_key)
                cache.create_binding(cs_id, act_id, sig_id)
        conn.commit()

    stats["probonopd_total_commands"] = total
    print(f"  ✓ probonopd (GATED): {total} commands from {stats['probonopd_files']} files")


# ─── Parser 4: radioxoma/infrared (LIRC + irplus XML) ────────────────────────
def ingest_radioxoma(conn: sqlite3.Connection, cache: EntityCache):
    radio_root = CACHE / "radioxoma-infrared"
    if not radio_root.exists():
        print("  ⚠ radioxoma/infrared not found, skipping")
        return

    source_id = "radioxoma-infrared"
    cache.ensure_source(source_id, "radioxoma/infrared",
                        "https://github.com/radioxoma/infrared", "MIT", True)
    cache.ensure_revision(source_id, "HEAD")

    total = 0

    # LIRC .conf files
    lirc_files = [f for f in radio_root.rglob("*.conf") if f.suffix == ".conf"]
    print(f"  Found {len(lirc_files)} LIRC conf files")

    remote_commands: dict[str, list[tuple[str, str, str]]] = {}
    remote_meta: dict[str, tuple[str, str, str]] = {}

    for lf in lirc_files:
        n = _parse_lirc_conf(cache, lf, remote_commands, remote_meta)
        total += n

    # irplus XML files
    xml_files = list(radio_root.rglob("*.xml"))
    print(f"  Found {len(xml_files)} irplus XML files")

    for xf in xml_files:
        n = _parse_irplus_xml(cache, xf, remote_commands, remote_meta)
        total += n

    # Commit grouped code sets
    rev_id_r = cache._revisions.get(f"{source_id}:HEAD", short(f"{source_id}:HEAD"))
    for remote_id, cmds in remote_commands.items():
        if not cmds:
            continue
        b_id, dt_id, remote_model = remote_meta[remote_id]
        proto_family = cmds[0][2] if cmds else "RAW"
        specs = [(a, s, rp, pt) for a, s, _, rp, pt in cmds]
        cs_id = cache.create_code_set(remote_id, rev_id_r, proto_family,
                                      len(cmds), binding_specs=specs)
        for act_key, sig_id, _, _, _ in cmds:
            act_id = cache.get_or_create_action(act_key)
            cache.create_binding(cs_id, act_id, sig_id)
    conn.commit()

    stats["radioxoma_total_commands"] = total
    print(f"  ✓ radioxoma: {total} commands")


def _parse_lirc_conf(cache: EntityCache, filepath: Path,
                     remote_commands: dict[str, list[tuple[str, str, str]]],
                     remote_meta: dict[str, tuple[str, str, str]]) -> int:
    count = 0
    try:
        text = filepath.read_text(encoding="utf-8", errors="replace")
    except Exception:
        stats["parse_errors"] += 1
        return 0

    parts = filepath.relative_to(CACHE / "radioxoma-infrared").parts
    brand = parts[0] if parts else "Unknown"
    remote_model = filepath.stem.replace(".lircd", "")

    header = [0, 0]
    one = [0, 0]
    zero = [0, 0]
    ptrail = 0
    pre_data = 0
    pre_data_bits = 0
    frequency = None
    bits = 0
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
                try:
                    codes[parts_line[0]] = int(parts_line[1], 0)
                except ValueError:
                    cache.rejections.add(
                        "radioxoma-infrared", str(filepath), 0, "MALFORMED_COMMAND",
                        detail=f"non-numeric code '{' '.join(parts_line)}'",
                        source_file_id=None)
                    stats["malformed_command_rejected"] += 1
            continue
        parts_line = line.split()
        if len(parts_line) < 2:
            continue
        directive = parts_line[0].lower()
        try:
            if directive == "frequency":
                try:
                    frequency = int(parts_line[1])
                except ValueError:
                    cache.rejections.add(
                        "radioxoma-infrared", str(filepath), 0, "INVALID_CARRIER",
                        detail=f"non-integer frequency '{parts_line[1]}'",
                        source_file_id=None)
                    stats["invalid_carrier_rejected"] += 1
                    frequency = None
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

    b_id = cache.get_or_create_brand(brand)
    dt_id = cache.get_or_create_device_type("Miscellaneous")
    rel_path = str(filepath.relative_to(CACHE / "radioxoma-infrared"))
    file_id = cache.ensure_file("radioxoma-infrared", rel_path)
    remote_id = cache.create_remote("radioxoma-infrared", b_id, dt_id,
                                     remote_model.lower(), remote_model, file_id)
    remote_meta[remote_id] = (b_id, dt_id, remote_model)
    if remote_id not in remote_commands:
        remote_commands[remote_id] = []

    for key_name, key_code in codes.items():
        action = normalize_action(key_name)
        full_code = (pre_data << bits | key_code) if pre_data_bits > 0 else key_code
        total_bits = pre_data_bits + bits if pre_data_bits > 0 else bits

        if total_bits <= 0 or (header[0] == 0 and one[0] == 0):
            # V0.6.1: raw LIRC files carry no bit-timing model — the physical
            # signal cannot be reconstructed. Typed rejection, not a fake sig.
            cache.rejections.add(
                "radioxoma-infrared", rel_path, 0, "UNSUPPORTED_ENCODING",
                detail=f"'{key_name}' has no bit timing model",
                action=action, protocol="LIRC_RAW", source_file_id=file_id)
            stats["unsupported_encoding_rejected"] += 1
            continue
        if frequency is None:
            cache.rejections.add(
                "radioxoma-infrared", rel_path, 0, "MISSING_CARRIER",
                detail=f"'{key_name}' without frequency directive",
                action=action, protocol="LIRC", source_file_id=file_id)
            stats["missing_carrier_rejected"] += 1
            continue

        pattern = list(header)
        for i in range(total_bits - 1, -1, -1):
            bit = (full_code >> i) & 1
            pattern.extend(one if bit else zero)
        if ptrail > 0:
            pattern.append(ptrail)
        while pattern and pattern[-1] <= 0:
            pattern.pop()

        if validate_raw_pattern(pattern, frequency):
            sig_id = cache.insert_signal_raw(
                frequency, pattern, carrier_evidence="SOURCE_DECLARED",
                source_file_id=file_id)
            remote_commands[remote_id].append(
                (action, sig_id, "RAW", "FULL_FRAME", "SINGLE_TAP"))
            count += 1
        else:
            cache.rejections.add(
                "radioxoma-infrared", rel_path, 0, "MALFORMED_RAW",
                detail=f"invalid raw for '{key_name}'",
                action=action, protocol="LIRC", source_file_id=file_id)
            stats["raw_rejected"] += 1

    return count


def _parse_irplus_xml(cache: EntityCache, filepath: Path,
                      remote_commands: dict[str, list[tuple[str, str, str]]],
                      remote_meta: dict[str, tuple[str, str, str]]) -> int:
    count = 0
    try:
        parser = ET.XMLParser()
        tree = ET.parse(filepath, parser=parser)
        root = tree.getroot()
    except (ET.ParseError, Exception):
        stats["parse_errors"] += 1
        return 0

    parts = filepath.relative_to(CACHE / "radioxoma-infrared").parts
    brand = parts[0] if parts else "Unknown"
    remote_model = filepath.stem

    b_id = cache.get_or_create_brand(brand)
    dt_id = cache.get_or_create_device_type("Miscellaneous")
    rel_path = str(filepath.relative_to(CACHE / "radioxoma-infrared"))
    file_id = cache.ensure_file("radioxoma-infrared", rel_path)
    remote_id = cache.create_remote("radioxoma-infrared", b_id, dt_id,
                                     remote_model.lower(), remote_model, file_id)
    remote_meta[remote_id] = (b_id, dt_id, remote_model)
    if remote_id not in remote_commands:
        remote_commands[remote_id] = []

    for button in root.iter("button"):
        label = button.get("label", "unknown")
        action = normalize_action(label)

        for raw_el in button.iter("raw"):
            declared = raw_el.get("frequency")
            freq_str = declared if declared else "38000"
            data_str = raw_el.get("data", "")
            try:
                freq = int(freq_str)
                pattern = [int(x) for x in data_str.split() if x.strip()]
            except ValueError:
                cache.rejections.add(
                    "radioxoma-infrared", rel_path, 0, "INVALID_CARRIER",
                    detail=f"non-integer frequency '{freq_str}' for '{label}'",
                    action=action, protocol="RAW", source_file_id=file_id)
                stats["invalid_carrier_rejected"] += 1
                continue
            if validate_raw_pattern(pattern, freq):
                # irplus XML: declared frequency = the transmitted carrier;
                # absent frequency = 38 kHz by the irplus format mandate.
                sig_id = cache.insert_signal_raw(
                    freq, pattern,
                    carrier_evidence=("SOURCE_DECLARED" if declared
                                      else "FORMAT_NORMATIVE"),
                    source_file_id=file_id)
                remote_commands[remote_id].append(
                    (action, sig_id, "RAW", "FULL_FRAME", "SINGLE_TAP"))
                count += 1
            else:
                cache.rejections.add(
                    "radioxoma-infrared", rel_path, 0, "MALFORMED_RAW",
                    detail=f"invalid raw for '{label}'", action=action,
                    protocol="RAW", source_file_id=file_id)
                stats["raw_rejected"] += 1

        for coded in button.iter("code"):
            proto = coded.get("protocol", "")
            dev = coded.get("device", "0")
            sub = coded.get("subdevice", "-1")
            func = coded.get("function", "0")
            proto_name, carrier = normalize_protocol(proto)
            if proto_name is None:
                cache.rejections.add(
                    "radioxoma-infrared", rel_path, 0, "UNSUPPORTED_PROTOCOL",
                    detail=f"unknown protocol '{proto}'",
                    action=action, protocol=proto)
                stats["unsupported_protocol_rejected"] += 1
                continue
            try:
                sig_id = cache.insert_signal_parametric(
                    proto_name, carrier, int(dev), int(sub), int(func),
                    carrier_evidence="FORMAT_NORMATIVE",
                    source_file_id=file_id)
                remote_commands[remote_id].append(
                    (action, sig_id, proto_name, "FULL_FRAME", "SINGLE_TAP"))
                count += 1
            except ValueError:
                cache.rejections.add(
                    "radioxoma-infrared", rel_path, 0, "MALFORMED_ADDRESS",
                    detail=f"non-numeric device/function for '{label}'",
                    action=action, protocol=proto, source_file_id=file_id)
                stats["malformed_address_rejected"] += 1

    return count


# ─── Parser 5: IrpProtocols.xml ──────────────────────────────────────────────
def ingest_irp_protocols(conn: sqlite3.Connection, cache: EntityCache):
    xml_path = CACHE / "irp-transmogrifier" / "src" / "main" / "resources" / "IrpProtocols.xml"
    if not xml_path.exists():
        print("  ⚠ IrpProtocols.xml not found, skipping")
        return

    source_id = "irp-protocols"
    cache.ensure_source(source_id, "IrpProtocols.xml",
                        "https://github.com/bengtmartensson/IrpTransmogrifier",
                        "Public Domain", True)
    cache.ensure_revision(source_id, "HEAD")

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except ET.ParseError as e:
        print(f"  ⚠ Failed to parse IrpProtocols.xml: {e}")
        stats["parse_errors"] += 1
        return

    ns = {"irp": "http://www.harctoolbox.org/irp-protocols"}
    count = 0
    for protocol in root.findall(".//irp:protocol", ns):
        name = protocol.get("name", "")
        if not name:
            continue
        irp_el = protocol.find("irp:irp", ns)
        irp_notation = irp_el.text.strip() if irp_el is not None and irp_el.text else ""
        carrier_hz = None
        freq_match = re.search(r"frequency\s*=\s*(\d+)", irp_notation)
        if freq_match:
            carrier_hz = int(freq_match.group(1))
        else:
            for known, (_, freq) in PROTOCOL_MAP.items():
                if known.lower() == name.lower():
                    carrier_hz = freq
                    break
        doc_el = protocol.find("irp:documentation", ns)
        doc = ""
        if doc_el is not None:
            doc = ET.tostring(doc_el, encoding="unicode", method="text").strip()[:500]

        conn.execute(
            "INSERT OR REPLACE INTO protocols (name, carrier_hz, irp_notation, "
            "bit_count, doc) VALUES (?, ?, ?, ?, ?)",
            (name, carrier_hz, irp_notation[:1000], None, doc[:500]))
        count += 1

    conn.commit()
    stats["irp_protocols"] = count
    print(f"  ✓ IrpProtocols: {count} protocol definitions")


# ─── Main Pipeline ────────────────────────────────────────────────────────────
def run_ingestion(profile: str = "production"):
    print("=" * 70)
    print(f"  Elysium Nexus — IR Data Fabric Ingestion v5 (Profile: {profile.upper()})")
    print("=" * 70)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    if DB_PATH.exists():
        DB_PATH.unlink()

    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")

    init_database(conn)
    seed_protocol_definitions(conn)

    collector = RejectionCollector()
    lock = load_source_lock()
    cache = EntityCache(conn.cursor(), profile=profile,
                        lock=lock, rejections=collector)

    print("\n[1/5] Ingesting Flipper-IRDB...")
    ingest_flipper(conn, cache)

    print("\n[2/5] Ingesting SmartIR...")
    ingest_smartir(conn, cache)

    if profile == "research":
        print("\n[3/5] Ingesting probonopd/irdb (RESEARCH ONLY)...")
        ingest_probonopd(conn, cache)
    elif (cache.lock.get("probonopd-irdb") or {}).get("productionEnabled"):
        print("\n[3/5] Ingesting probonopd/irdb (PRODUCTION — lockfile approved)...")
        ingest_probonopd(conn, cache)
    else:
        print("\n[3/5] Skipping probonopd/irdb for PRODUCTION profile (GATED)...")

    print("\n[4/5] Ingesting radioxoma/infrared...")
    ingest_radioxoma(conn, cache)

    # IrpProtocols.xml is metadata-only (no v4 table). Protocol data
    # lives in PROTOCOL_MAP normalization dict already.
    print("\n[5/5] IrpProtocols.xml (metadata — embedded in PROTOCOL_MAP, skipped)")

    # ─── Final Stats ──────────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("  Final Database Statistics")
    print("=" * 70)

    cur = conn.cursor()
    counts = {}
    for table in ["sources", "source_revisions", "source_files",
                   "brands", "device_types", "device_models", "remotes",
                   "code_sets", "actions", "signals", "command_bindings"]:
        cur.execute(f"SELECT COUNT(*) FROM {table}")
        counts[table] = cur.fetchone()[0]

    # Canonical hash
    canonical_hash, entity_counts = export_canonical_catalog.compute_canonical_hash(DB_PATH)

    db_size = DB_PATH.stat().st_size
    print(f"  Sources:          {counts.get('sources', 0)}")
    print(f"  Source Revisions: {counts.get('source_revisions', 0)}")
    print(f"  Source Files:     {counts.get('source_files', 0)}")
    print(f"  Brands:           {counts.get('brands', 0)}")
    print(f"  Device Types:     {counts.get('device_types', 0)}")
    print(f"  Remotes:          {counts.get('remotes', 0)}")
    print(f"  Code Sets:        {counts.get('code_sets', 0)}")
    print(f"  Actions:          {counts.get('actions', 0)}")
    print(f"  Signals:          {counts.get('signals', 0)}")
    print(f"  Command Bindings: {counts.get('command_bindings', 0)}")
    print(f"  Database Size:    {db_size / 1024 / 1024:.2f} MB")
    print(f"  Canonical SHA-256:{canonical_hash[:32]}...")
    print(f"  Deduped Parametric: {stats.get('inserted_parametric', 0)}")
    print(f"  Deduped Raw:      {stats.get('inserted_raw', 0)}")
    print(f"  Validation Rejects: {stats.get('validation_rejected', 0)}")
    print(f"  Protocol Rejects: {stats.get('unsupported_protocol_rejected', 0)}")
    print(f"  Parse Errors:     {stats.get('parse_errors', 0)}")

    # ─── Write Rejections Manifest + DB rows (§7/§4) ─────────────────
    collector.write_rows(conn)
    conn.commit()  # durability: row writes must not ride the closing rollback
    collector.write_manifest(REJECTIONS_PATH, profile)
    print(f"  ✓ Rejections manifest + {len(collector._by_row)} DB rows written")

    # ─── Write Stats JSON ─────────────────────────────────────────────
    manifest = {
        "schemaVersion": 5,
        "profile": profile,
        "generatedAtUtc": "2026-08-08T00:00:00Z",
        "pipelineVersion": "5.1.0-v5-native",
        "databaseSha256": hashlib.sha256(DB_PATH.read_bytes()).hexdigest(),
        "canonicalContentSha256": canonical_hash,
        "databaseSizeBytes": db_size,
        "counts": entity_counts,
        "stats": {
            "dedupParametric": stats.get("inserted_parametric", 0),
            "dedupRaw": stats.get("inserted_raw", 0),
            "validationRejected": stats.get("validation_rejected", 0),
            "unsupportedProtocolRejected": stats.get("unsupported_protocol_rejected", 0),
            "parseErrors": stats.get("parse_errors", 0),
        }
    }
    STATS_PATH.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"\n  ✓ Stats written to {STATS_PATH}")

    conn.close()


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(
        description="Elysium Nexus — IR Data Fabric Ingestion v5 (Schema v4 Native)")
    parser.add_argument("--profile", choices=["production", "research"],
                        default="production",
                        help="Build profile: production (approved sources) or "
                             "research (all sources)")
    args = parser.parse_args()
    run_ingestion(profile=args.profile)
