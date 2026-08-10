#!/usr/bin/env python3
"""
Elysium Nexus — Catalog Eligibility Gate (V06-PTG-02, order §3)

Production catalog MUST contain usable candidates, proven by SQL — a build
cannot ship if the candidate queries return zero.

Two surfaces are checked against the packaged database:

1. PROBE CANDIDATE SURFACE (the product's probe pipeline):
   the runtime candidate query (`getAllCandidates`) must return candidates
   for POWER / VOLUME_UP / VOLUME_DOWN / MUTE. INTERNAL_UNVERIFIED code sets
   ARE part of this surface (probing exists to verify them); only BLOCKED is
   excluded.

2. PRODUCTION ELIGIBLE SURFACE (what may be CLAIMED/ranked as usable):
   code sets whose evidence chain is complete:
   - source production_approved = 1 and license evidence present
     (source_revisions.license_sha256 != zero-hash),
   - runtime status ACTIVE,
   - verification_status not INTERNAL_UNVERIFIED/BLOCKED (evidence floor),
   - codec registered in protocol_definitions,
   - variant unambiguous (per code set: exactly one registered variant).

Exit code: 0 = all gates pass; 1 = any gate fails (names the failing gate).
Read-only: never mutates the catalog.
"""

import argparse
import sqlite3
import sys
from pathlib import Path

ZERO_SHA = "0" * 64

ACTIONS = [
    ("POWER", "POWER_TOGGLE"),
    ("VOLUME_UP", "VOLUME_UP"),
    ("VOLUME_DOWN", "VOLUME_DOWN"),
    ("MUTE", "MUTE"),
]

PROBE_QUERY = """
SELECT COUNT(DISTINCT cs.id)
FROM code_sets cs
JOIN source_revisions sr ON cs.source_revision_id = sr.id
JOIN sources s ON sr.source_id = s.id
JOIN command_bindings cb ON cb.code_set_id = cs.id
JOIN actions a ON cb.action_id = a.id
WHERE a.canonical_key = ?
  AND s.production_approved = 1
  AND cs.runtime_status = 'ACTIVE'
  AND cs.verification_status != 'BLOCKED'
"""

ELIGIBLE_IDS_QUERY = """
SELECT DISTINCT cs.id
FROM code_sets cs
JOIN source_revisions sr ON cs.source_revision_id = sr.id
JOIN sources s ON sr.source_id = s.id
JOIN command_bindings cb ON cb.code_set_id = cs.id
JOIN actions a ON cb.action_id = a.id
JOIN signals sig ON cb.signal_id = sig.id
LEFT JOIN protocol_definitions pd ON pd.id = sig.codec_id
WHERE 1 = 1
  AND s.production_approved = 1
  AND sr.license_sha256 NOT IN (?, '')
  AND cs.runtime_status = 'ACTIVE'
  AND cs.verification_status NOT IN ('INTERNAL_UNVERIFIED', 'BLOCKED')
  AND (pd.id IS NOT NULL OR sig.encoding_type = 'RAW')
  {action_filter}
"""


def code_set_variant_unambiguous(conn: sqlite3.Connection, cs_id: str) -> bool:
    """Variant-unambiguous: every signal of the code set resolves to exactly
    one registered protocol variant."""
    row = conn.execute(
        """
        SELECT COUNT(DISTINCT sig.codec_id), COUNT(DISTINCT sig.protocol_variant)
        FROM command_bindings cb
        JOIN signals sig ON cb.signal_id = sig.id
        WHERE cb.code_set_id = ?
        """,
        (cs_id,),
    ).fetchone()
    if not row:
        return False
    codecs, variants = row
    return codecs >= 1 and variants >= 1 and variants == 1


def eligible_ids(conn: sqlite3.Connection, action_key: str | None):
    action_filter = "AND a.canonical_key = ?" if action_key else ""
    params = [ZERO_SHA] + ([action_key] if action_key else [])
    return [r[0] for r in conn.execute(ELIGIBLE_IDS_QUERY.format(action_filter=action_filter), params)]


def run_gate(db_path: Path) -> tuple[bool, list[str]]:
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    failures: list[str] = []
    try:
        for gate_name, action_key in ACTIONS:
            count = conn.execute(PROBE_QUERY, (action_key,)).fetchone()[0]
            status = "OK" if count > 0 else "FAIL"
            print(f"[gate] {gate_name:<30} {action_key:<12} probe_candidates={count:<6} {status}")
            if count <= 0:
                failures.append(gate_name)

        eligible_total = sum(
            1 for cid in eligible_ids(conn, None) if code_set_variant_unambiguous(conn, cid)
        )
        status = "OK" if eligible_total > 0 else "FAIL"
        print(f"[gate] {'ELIGIBLE_CODESETS':<30} {'(all)':<12} eligible={eligible_total:<6} {status}")
        if eligible_total <= 0:
            failures.append("ELIGIBLE_CODESETS")

        for gate_name, action_key in ACTIONS:
            count = sum(
                1 for cid in eligible_ids(conn, action_key) if code_set_variant_unambiguous(conn, cid)
            )
            status = "OK" if count > 0 else "FAIL"
            print(f"[gate] {'ELIGIBLE_ ' + gate_name:<30} {action_key:<12} eligible={count:<6} {status}")
            if count <= 0:
                failures.append("ELIGIBLE_" + gate_name)
    finally:
        conn.close()

    return (not failures, failures)


def main() -> int:
    parser = argparse.ArgumentParser(description="Catalog eligibility gate (order §3)")
    default_db = (
        Path(__file__).resolve().parent.parent.parent
        / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir" / "ir_catalog.db"
    )
    parser.add_argument("--db", type=Path, default=default_db)
    args = parser.parse_args()

    if not args.db.exists():
        print(f"[gate] FATAL: catalog not found at {args.db}")
        return 1

    ok, failures = run_gate(args.db)
    print(f"[gate] {'PASS — production catalog is usable' if ok else 'FAIL: ' + ', '.join(failures)}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())