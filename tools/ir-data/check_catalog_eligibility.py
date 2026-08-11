#!/usr/bin/env python3
"""
Elysium Nexus — Catalog Eligibility Gate (V0.6.1, Phase 0.3/0.4)
================================================================
V0.6.1 conversion of the PTG-02 §3 gate:

- joins use the REAL Schema V5 foreign keys
  (signals.protocol_definition_id / protocol_variant_id), never
  sig.codec_id vs pd.id string matching (P0-2);
- eligibility is computed PER SIGNAL (eligibility_status)
  and code sets inherit the floor from their own bindings (P0-3/P0-5);
- RAW signals need NO parametric variant: their waveform IS the
  physical representation, validated by carrier + timing bounds;
- two SEPARATE surfaces:
    PROBE_ELIGIBLE   — may enter on-device probing (INTERNAL_UNVERIFIED OK)
    CLAIM_ELIGIBLE   — may feed product compatibility claims
                        (verification floor: not INTERNAL_UNVERIFIED/BLOCKED)
  CLAIM_ELIGIBLE == 0 ⇒ build is an Engineering Preview, not a
  Production Compatibility Build (--require-claims makes it fatal).

Exit code: 0 = gates pass; 1 = any mandatory gate fails.
Read-only: never mutates the catalog.
"""

import argparse
import sqlite3
import sys
from pathlib import Path

ACTIONS = [
    ("POWER", "POWER_TOGGLE"),
    ("VOLUME_UP", "VOLUME_UP"),
    ("VOLUME_DOWN", "VOLUME_DOWN"),
    ("MUTE", "MUTE"),
]

PHYSICAL_FLOOR = """
  (sig.encoding_type = 'RAW'
   OR (sig.protocol_definition_id IS NOT NULL
       AND sig.protocol_variant_id IS NOT NULL))
"""

PROBE_QUERY = f"""
SELECT COUNT(DISTINCT cs.id)
FROM code_sets cs
JOIN command_bindings cb ON cb.code_set_id = cs.id
JOIN actions a ON cb.action_id = a.id
JOIN signals sig ON cb.signal_id = sig.id
WHERE a.canonical_key = ?
  AND cs.runtime_status = 'ACTIVE'
  AND cs.verification_status != 'BLOCKED'
  AND sig.eligibility_status = 'PROBE_ELIGIBLE'
  AND cs.eligibility_status = 'PROBE_ELIGIBLE'
  AND ({PHYSICAL_FLOOR.strip()})
"""

CLAIM_QUERY = f"""
SELECT COUNT(DISTINCT cs.id)
FROM code_sets cs
JOIN command_bindings cb ON cb.code_set_id = cs.id
JOIN actions a ON cb.action_id = a.id
JOIN signals sig ON cb.signal_id = sig.id
JOIN source_revisions sr ON cs.source_revision_id = sr.id
JOIN sources s ON sr.source_id = s.id
WHERE a.canonical_key = ?
  AND s.production_approved = 1
  AND sr.license_sha256 NOT IN ('{"0" * 64}', '')
  AND cs.runtime_status = 'ACTIVE'
  AND cs.eligibility_status = 'PROBE_ELIGIBLE'
  AND sig.eligibility_status = 'PROBE_ELIGIBLE'
  AND cs.verification_status NOT IN ('INTERNAL_UNVERIFIED', 'BLOCKED')
  AND ({PHYSICAL_FLOOR.strip()})
"""


def run_gate(db_path: Path) -> tuple[bool, list[str], dict]:
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    failures: list[str] = []
    report: dict = {}
    try:
        probe_total = 0
        for gate_name, action_key in ACTIONS:
            count = conn.execute(PROBE_QUERY, (action_key,)).fetchone()[0]
            probe_total += count
            report[f"probe_{gate_name}"] = count
            status = "OK" if count > 0 else "FAIL"
            print(f"[gate] probe_{gate_name:<24} {action_key:<12} candidates={count:<6} {status}")
            if count <= 0:
                failures.append(f"probe_{gate_name}")

        claim_total = 0
        for gate_name, action_key in ACTIONS:
            count = conn.execute(CLAIM_QUERY, (action_key,)).fetchone()[0]
            claim_total += count
            report[f"claim_{gate_name}"] = count
            status = "OK" if count > 0 else "CLAIM_EMPTY"
            print(f"[gate] claim_{gate_name:<24} {action_key:<12} eligible={count:<6} {status}")

        report["claim_total"] = claim_total
        print(f"[gate] {'PROBE_SURFACE':<30} total={probe_total:<6} "
              f"{'OK' if probe_total > 0 else 'FAIL'}")
        if probe_total <= 0:
            failures.append("PROBE_SURFACE")
    finally:
        conn.close()
    return (not failures, failures, report)


def main() -> int:
    parser = argparse.ArgumentParser(description="Catalog eligibility gate (V0.6.1 §0.3/0.4)")
    default_db = (
        Path(__file__).resolve().parent.parent.parent
        / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir" / "ir_catalog.db"
    )
    parser.add_argument("--db", type=Path, default=default_db)
    parser.add_argument("--require-claims", action="store_true",
                        help="Fail when CLAIM_ELIGIBLE == 0 (release gate for "
                             "a Production Compatibility Build)")
    args = parser.parse_args()

    if not args.db.exists():
        print(f"[gate] FATAL: catalog not found at {args.db}")
        return 1

    ok, failures, report = run_gate(args.db)
    if args.require_claims and report.get("claim_total", 0) <= 0:
        print("[gate] REQUIRE_CLAIMS: claim surface empty — build is an "
              "Engineering Preview, NOT a Production Compatibility Build")
        if ok:
            failures.append("REQUIRE_CLAIMS")
            ok = False

    print(f"[gate] {'PASS — catalog is probe-usable' if ok else 'FAIL: ' + ', '.join(failures)}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
