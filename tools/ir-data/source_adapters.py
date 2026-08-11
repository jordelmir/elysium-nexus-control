#!/usr/bin/env python3
"""
ELYSIUM NEXUS — V0.6.1 Phase 1/2: One True Data Factory (SourceAdapters)
=======================================================================
Every Elysium-native dataset (curated TV, KINTECH, templates) enters the
catalog through the SAME fail-closed EntityCache + sources.lock authority
as external sources. Zero direct DB mutation — no seeders may touch the
final artifact.

- curated TV   → elysium-curated-observed  (PROBE_ELIGIBLE, production)
- KINTECH      → elysium-nexus-curated     (PROBE_ELIGIBLE, production)
- templates    → elysium-template-hypotheses (RESEARCH_ONLY, never production)

Artifacts live in data/curated-tv/<version>/ and data/ir-templates/<version>/
with per-file SHA-256 hashes anchored in sources.lock.json.
"""

import hashlib
import json
import sqlite3
import sys
from pathlib import Path

import ingest_v5
from ingest_v5 import (EntityCache, PROTOCOL_MAP)

ROOT = ingest_v5.ROOT
CURATED_DIR = ROOT / "ir-data" / "data" / "curated-tv" / "v1"
CURATED_JSON = CURATED_DIR / "ir_codes_db.json"
CURATED_LICENSE = CURATED_DIR / "LICENSE.txt"

TEMPLATE_DIR = ROOT / "ir-data" / "data" / "ir-templates" / "v1"
TEMPLATE_JSON = TEMPLATE_DIR / "templates.json"
TEMPLATE_LICENSE = TEMPLATE_DIR / "LICENSE.txt"

# Reused parsers/maps from the legacy seeder (kept as the single kernel
# implementation: parse_nec_32 / parse_samsung_32 / parse_sirc).
from seed_curated_brands_v4 import (  # noqa: E402
    COMMAND_ACTIONS, JSON_PROTOCOL_CODEC, PARSERS,
)

CURATED_ARTIFACTS = [
    ("ir_codes_db.json", CURATED_JSON),
    ("LICENSE.txt", CURATED_LICENSE),
]


def _file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _ensure_locked_artifacts(cache: EntityCache, source_id: str,
                             artifacts: list[tuple[str, Path]]) -> dict[str, str]:
    """Per-file provenance: exact file SHA-256, license status decided by the
    lock authority (fail-closed in production via ensure_revision)."""
    file_ids: dict[str, str] = {}
    cache.ensure_source(source_id,
                        f"Elysium Nexus {source_id}",
                        "https://github.com/jordelmir/elysium-nexus-control",
                        "LicenseRef-ELYSIUM-V1", True)
    cache.ensure_revision(source_id, "v1")
    for rel, path in artifacts:
        if not path.exists():
            raise RuntimeError(
                f"Phase 1/2 fail-closed: artifact {path} missing for "
                f"source '{source_id}' — no silent empty import")
        file_ids[rel] = cache.ensure_file(source_id, rel,
                                          content_sha=_file_sha256(path))
    return file_ids


def import_curated(cache: EntityCache, profile: str = "production",
                   brand_filter: str | None = None,
                   source_id: str = "elysium-curated-observed") -> dict:
    """Curated TV dataset through the factory. brand_filter restricts to a
    single brand id (used by elysium-nexus-curated / KINTECH)."""
    if profile == "production" and source_id not in cache.lock:
        raise RuntimeError(
            f"Phase 2 fail-closed: '{source_id}' must exist "
            "in sources.lock.json for production imports")
    files = _ensure_locked_artifacts(cache, source_id, CURATED_ARTIFACTS)

    jdb = json.loads(CURATED_JSON.read_text(encoding="utf-8"))
    rev_id = cache._revisions.get(f"{source_id}:v1") or \
        cache._revisions.get(f"{source_id}:HEAD", "")
    stats = {"brands": 0, "code_sets": 0, "signals": 0, "skipped_actions": 0}

    for brand in jdb.get("brands", []):
        brand_id = (brand.get("id") or "").strip()
        if brand_filter and brand_id.lower() != brand_filter.lower():
            continue
        brand_name = (brand.get("name") or brand_id or "Unknown").strip()
        if not brand_id:
            continue
        b_id = cache.get_or_create_brand(brand_name)
        dt_id = cache.get_or_create_device_type("Tv")

        category = None
        for cat in brand.get("categories", []):
            if str(cat.get("type", "")).upper() == "TV":
                category = cat
                break
        if category is None and brand.get("categories"):
            category = brand["categories"][0]
        if category is None or not category.get("codesets"):
            continue
        stats["brands"] += 1

        for codeset in category["codesets"]:
            cs_name = (codeset.get("name") or brand_name).strip()
            proto_orig = (codeset.get("protocol") or "NEC").strip()
            freq = codeset.get("frequency_hz")
            codec_id = JSON_PROTOCOL_CODEC.get(proto_orig)
            parser = PARSERS.get(proto_orig)
            if codec_id is None or parser is None:
                cache.rejections.add(
                    source_id, "ir_codes_db.json", 0, "UNSUPPORTED_PROTOCOL",
                    detail=f"unknown protocol '{proto_orig}' for {brand_id} {cs_name}",
                    protocol=proto_orig, source_file_id=files["ir_codes_db.json"])
                continue
            family = codec_id  # runtime codec id: NEC/SAMSUNG/SIRC
            # Schema V5 FK: signals.protocol_definition_id must reference a
            # seeded family in PROTOCOL_MAP (e.g. "SAMSUNG" → "Samsung32").
            family_def = {
                "NEC": "NEC", "SAMSUNG": "Samsung32", "SIRC": "SIRC",
            }.get(family, family)
            default_hz = PROTOCOL_MAP.get(
                next((k for k, (fam, _) in PROTOCOL_MAP.items()
                      if fam == family_def), family_def),
                (family_def, 38000))[1]
            carrier = int(freq) if freq else default_hz
            carrier_evidence = "SOURCE_DECLARED" if freq else "FORMAT_NORMATIVE"

            commands = codeset.get("commands", {})
            rem_cmds: list[tuple[str, str, str, str, str]] = []
            for raw_key, hex_code in commands.items():
                canonical = COMMAND_ACTIONS.get(raw_key)
                if canonical is None:
                    stats["skipped_actions"] += 1
                    continue
                try:
                    addr, cmd = parser(hex_code)
                except (ValueError, TypeError):
                    cache.rejections.add(
                        source_id, "ir_codes_db.json", 0, "MALFORMED_ADDRESS",
                        detail=f"unparseable hex {hex_code!r} ({raw_key}) "
                               f"{brand_id} {cs_name}",
                        action=canonical, protocol=proto_orig,
                        source_file_id=files["ir_codes_db.json"])
                    continue
                sig = cache.insert_signal_parametric(
                    family_def, carrier, addr, -1, cmd,
                    carrier_evidence=carrier_evidence,
                    source_file_id=files["ir_codes_db.json"])
                rem_cmds.append((canonical, sig, family, "FULL_FRAME", "SINGLE_TAP"))
            if not rem_cmds:
                continue

            remote_id = cache.create_remote(
                source_id, b_id, dt_id, cs_name.lower().replace(" ", "-"),
                cs_name, files["ir_codes_db.json"])
            specs = [(a, s, rp, pt) for a, s, _, rp, pt in rem_cmds]
            cs_id = cache.create_code_set(remote_id, rev_id, family,
                                          len(rem_cmds), binding_specs=specs)
            for act_key, sig_id, _, _, _ in rem_cmds:
                act_id = cache.get_or_create_action(act_key)
                cache.create_binding(cs_id, act_id, sig_id)
            stats["code_sets"] += 1
            stats["signals"] += len(rem_cmds)
    return stats


def import_kintech(cache: EntityCache, profile: str = "production") -> dict:
    """KINTECH subset under its own source identity (elysium-nexus-curated).
    Same kernel, same lock authority — no bespoke DB writes."""
    if profile == "production" and "elysium-nexus-curated" not in cache.lock:
        raise RuntimeError(
            "Phase 2 fail-closed: 'elysium-nexus-curated' must exist in "
            "sources.lock.json for production imports")
    _ensure_locked_artifacts(cache, "elysium-nexus-curated",
                             CURATED_ARTIFACTS)
    return import_curated(cache, profile=profile, brand_filter="KINTECH",
                          source_id="elysium-nexus-curated")


TEMPLATE_ARTIFACTS = [
    ("templates.json", TEMPLATE_JSON),
    ("LICENSE.txt", TEMPLATE_LICENSE),
]


def import_templates(cache: EntityCache, profile: str = "production") -> dict:
    """Hypothesis templates — RESEARCH_ONLY by construction. In production
    this import refuses to run: the dangerous data does not exist in the
    production artifact."""
    if profile == "production":
        raise RuntimeError(
            "Phase 1 fail-closed: elysium-template-hypotheses is "
            "RESEARCH_ONLY and never enters a production catalog build")
    files = _ensure_locked_artifacts(cache, "elysium-template-hypotheses",
                                     TEMPLATE_ARTIFACTS)
    art = json.loads(TEMPLATE_JSON.read_text(encoding="utf-8"))
    source_id = "elysium-template-hypotheses"
    rev_id = cache._revisions.get(f"{source_id}:v1") or \
        cache._revisions.get(f"{source_id}:HEAD", "")
    stats = {"code_sets": 0, "signals": 0}
    for i, t in enumerate(art["templates"]):
        proto_raw = t["protocol"]
        family, carrier = PROTOCOL_MAP.get(proto_raw.lower(), ("NEC", 38000))
        sig = cache.insert_signal_parametric(
            family, carrier, int(t["address"]), -1, int(t["command"]),
            carrier_evidence="FORMAT_NORMATIVE",
            source_file_id=files["templates.json"],
            eligibility="RESEARCH_ONLY")
        b_id = cache.get_or_create_brand(t["brand"])
        dt_id = cache.get_or_create_device_type("Tv")
        remote_id = cache.create_remote(
            source_id, b_id, dt_id, t["id"], t["display"],
            files["templates.json"])
        specs = [("POWER_TOGGLE", sig, "FULL_FRAME", "SINGLE_TAP")]
        cs_id = cache.create_code_set(remote_id, rev_id, family, 1, binding_specs=specs)
        act_id = cache.get_or_create_action("POWER_TOGGLE")
        cache.create_binding(cs_id, act_id, sig)
        stats["code_sets"] += 1
        stats["signals"] += 1
    return stats


def main() -> int:
    """CLI: --profile production|research. Builds the franchise DB through
    cargo ingestion + adapters (no legacy seeders)."""
    import argparse
    parser = argparse.ArgumentParser(description="Elysium Nexus SourceAdapters")
    parser.add_argument("--profile", choices=["production", "research"],
                        default="production")
    args = parser.parse_args()

    import ingest_v5 as ing
    db_path = ing.DB_PATH
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    ing.init_database(conn)
    ing.seed_protocol_definitions(conn)
    collector = ing.RejectionCollector()
    lock = ing.load_source_lock()
    cache = EntityCache(conn.cursor(), profile=args.profile,
                        lock=lock, rejections=collector)
    s = import_curated(cache, profile=args.profile)
    print(f"  ✓ curated: {s}")
    s2 = import_kintech(cache, profile=args.profile)
    print(f"  ✓ kintech: {s2}")
    if args.profile == "research":
        s3 = import_templates(cache, profile=args.profile)
        print(f"  ✓ templates: {s3}")
    collector.write_rows(conn)
    collector.write_manifest(ing.REJECTIONS_PATH, args.profile)
    conn.commit()
    conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
