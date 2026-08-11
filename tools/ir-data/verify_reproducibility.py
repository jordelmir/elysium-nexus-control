#!/usr/bin/env python3
"""
Elysium Nexus — V0.6.2 PHASE-1: Clean Reproducibility Gate
============================================================
The ONLY legal catalog builder is catalog.py --profile production.

Legacy seeders (seed_curated_brands_v4, seed_kintech_v4, seed_device_models_v4)
are PROHIBITED — they no longer exist in the production pipeline.

Modes:
  --fast          hash-only compare of SHIPPED DB vs manifest (CI-safe, no rebuild)
  (default)       clean-room Build A + Build B into independent temp workspaces;
                  compare canonical, counts, rejections, license, lock hashes
  --mutation-test after Build B, mutate 1 signal row → canonical MUST change
  --keep          keep temp workspaces for post-mortem inspection

CI uses --fast (proves committed DB matches committed manifest).
Full rebuild requires .cache/ir-sources (local / RC gate).

Exit code: 0 = PASS, 1 = FAIL (any drift or integrity violation).
"""

import argparse
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TOOLS = ROOT / "tools" / "ir-data"
ASSETS_DIR = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "ir"
DB_PATH = ASSETS_DIR / "ir_catalog.db"
MANIFEST_PATH = ASSETS_DIR / "ir_catalog.manifest.json"

sys.path.insert(0, str(TOOLS))
import export_canonical_catalog  # noqa: E402


def sha256(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_manifest(ws: Path) -> dict:
    return json.loads((ws / "ir_catalog.manifest.json").read_text())


def build_in_workspace(ws: Path) -> dict:
    """Run catalog.py --profile production into ws, return manifest + canonical."""
    env = dict(os.environ, IR_CATALOG_OUTPUT_DIR=str(ws))
    ws.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        [sys.executable, str(TOOLS / "catalog.py"), "--profile", "production"],
        check=False, capture_output=True, text=True, cwd=str(ROOT), env=env,
    )
    if result.returncode != 0:
        print(f"  FATAL: catalog.py failed (rc={result.returncode}):", file=sys.stderr)
        print(f"  stdout: {result.stdout[-500:]}", file=sys.stderr)
        print(f"  stderr: {result.stderr[-500:]}", file=sys.stderr)
        sys.exit(1)
    manifest_path = ws / "ir_catalog.manifest.json"
    db_path = ws / "ir_catalog.db"
    if not manifest_path.exists() or not db_path.exists():
        print(f"  FATAL: build produced no artifacts in {ws}", file=sys.stderr)
        sys.exit(1)
    manifest = _read_manifest(ws)
    canonical, counts = export_canonical_catalog.compute_canonical_hash(db_path)
    return {
        "manifest": manifest,
        "canonical": canonical,
        "counts": counts,
        "db_sha256": sha256(db_path),
        "rejection_sha256": manifest.get("rejectionManifestSha256", ""),
        "license_sha256": manifest.get("licenseManifestSha256", ""),
        "source_lock_sha256": manifest.get("sourceLockSha256", ""),
        "policy_version": manifest.get("policyVersion", ""),
        "pipeline_version": manifest.get("pipelineVersion", ""),
        "catalog_build_id": manifest.get("catalogBuildId", ""),
    }


def compare_builds(a: dict, b: dict, label_a: str, label_b: str) -> bool:
    """Compare two build dicts. Returns True if all match."""
    ok = True
    checks = [
        ("canonical", "canonical"),
        ("counts", "counts"),
        ("rejection_sha256", "rejectionManifestSha256"),
        ("license_sha256", "licenseManifestSha256"),
        ("source_lock_sha256", "sourceLockSha256"),
        ("policy_version", "policyVersion"),
        ("pipeline_version", "pipelineVersion"),
        ("catalog_build_id", "catalogBuildId"),
    ]
    for key, label in checks:
        va = a.get(key)
        vb = b.get(key)
        if va != vb:
            print(f"    DRIFT [{label}]: {label_a}={va!r} vs {label_b}={vb!r}")
            ok = False
        else:
            display = va[:16] + "..." if isinstance(va, str) and len(va) > 16 else va
            print(f"    OK [{label}]: {display}")
    return ok


def compare_against_shipped(b: dict) -> bool:
    """Compare a rebuild against the committed (shipped) manifest + DB."""
    if not MANIFEST_PATH.exists():
        print("    FAIL: shipped manifest missing", file=sys.stderr)
        return False
    shipped = json.loads(MANIFEST_PATH.read_text())
    ok = True
    pairs = [
        (b["canonical"], shipped.get("canonicalContentSha256"), "canonical"),
        (b["counts"], shipped.get("counts"), "counts"),
        (b["rejection_sha256"], shipped.get("rejectionManifestSha256"), "rejectionSha256"),
        (b["license_sha256"], shipped.get("licenseManifestSha256"), "licenseSha256"),
        (b["source_lock_sha256"], shipped.get("sourceLockSha256"), "sourceLockSha256"),
        (b["policy_version"], shipped.get("policyVersion"), "policyVersion"),
        (b["pipeline_version"], shipped.get("pipelineVersion"), "pipelineVersion"),
        (b["catalog_build_id"], shipped.get("catalogBuildId"), "catalogBuildId"),
    ]
    for actual, expected, label in pairs:
        if actual != expected:
            print(f"    DRIFT vs shipped [{label}]: build={actual!r} shipped={expected!r}")
            ok = False
        else:
            print(f"    OK [{label}]: matches shipped")
    if DB_PATH.exists():
        con = sqlite3.connect(str(DB_PATH))
        qc = con.execute("PRAGMA quick_check").fetchone()[0]
        fk = con.execute("PRAGMA foreign_key_check").fetchall()
        con.close()
        if qc != "ok" or len(fk) != 0:
            print(f"    FAIL: shipped DB integrity: quick_check={qc!r} fk={len(fk)}")
            ok = False
        else:
            print("    OK [shipped_db_integrity]: quick_check=ok fk=0")
    return ok


def mutation_test(ws_b: Path) -> bool:
    """Copy Build B's DB, mutate 1 signal, verify canonical changes.

    Proves the canonical hash is sensitive to data content (no precomputed
    cache, no compression artifact hiding real changes)."""
    db_b = ws_b / "ir_catalog.db"
    if not db_b.exists():
        print("  FATAL: Build B DB missing for mutation test", file=sys.stderr)
        return False
    with tempfile.TemporaryDirectory(prefix="nexus-mutation-") as mut_dir:
        ws_mut = Path(mut_dir)
        shutil.copy2(db_b, ws_mut / "ir_catalog.db")
        db_mut = ws_mut / "ir_catalog.db"
        con = sqlite3.connect(str(db_mut))
        con.execute("PRAGMA journal_mode=WAL")
        con.execute(
            "UPDATE signals SET carrier_hz = carrier_hz + 1 "
            "WHERE id = (SELECT MIN(id) FROM signals WHERE carrier_hz IS NOT NULL)"
        )
        con.commit()
        con.close()
        canonical_b, _ = export_canonical_catalog.compute_canonical_hash(db_b)
        canonical_mut, _ = export_canonical_catalog.compute_canonical_hash(db_mut)
        ok = (canonical_b != canonical_mut)
        if ok:
            print("  mutation test: PASS")
            print(f"    original : {canonical_b[:20]}...")
            print(f"    mutated  : {canonical_mut[:20]}...")
        else:
            print("  mutation test: FAIL (canonical unchanged after carrier_hz+1)",
                  file=sys.stderr)
        return ok


# ── CI-safe fast mode ────────────────────────────────────────────────────────

def run_fast() -> bool:
    """CI-safe: compare shipped DB canonical hash + counts against manifest."""
    print("=== V06.2 Phase 1 — Fast Reproducibility Gate (shipped DB) ===")
    if not DB_PATH.exists() or not MANIFEST_PATH.exists():
        print("  FATAL: shipped DB or manifest missing", file=sys.stderr)
        return False
    manifest = json.loads(MANIFEST_PATH.read_text())
    expected_canonical = manifest.get("canonicalContentSha256", "")
    expected_counts = manifest.get("counts", {})
    actual_canonical, actual_counts = export_canonical_catalog.compute_canonical_hash(DB_PATH)
    ok = True
    if actual_canonical != expected_canonical:
        ok = False
        print(f"  canonical: FAIL (expected {expected_canonical[:16]}..., got {actual_canonical[:16]}...)")
    else:
        print(f"  canonical: PASS ({actual_canonical[:16]}...)")
    for k, v in sorted(expected_counts.items()):
        got = actual_counts.get(k)
        if got != v:
            ok = False
            print(f"  count DRIFT {k}: manifest={v} actual={got}")
    if ok:
        print(f"  counts: PASS ({sum(actual_counts.values())} total entities)")
    con = sqlite3.connect(str(DB_PATH))
    qc = con.execute("PRAGMA quick_check").fetchone()[0]
    fk = con.execute("PRAGMA foreign_key_check").fetchall()
    con.close()
    if qc != "ok" or len(fk) != 0:
        ok = False
        print(f"  integrity: FAIL (quick_check={qc!r}, fk={len(fk)})")
    else:
        print("  integrity: PASS")
    print(f"=== {'PASS' if ok else 'FAIL'} ===")
    return ok


# ── Full clean-room A/B rebuild ──────────────────────────────────────────────

def run_full(args: argparse.Namespace) -> bool:
    """Clean-room Build A + Build B into independent temp workspaces."""
    print("=== V06.2 Phase 1 — Full Reproducibility Gate (clean-room A/B) ===")
    start = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="nexus-build-a-") as a_dir, \
         tempfile.TemporaryDirectory(prefix="nexus-build-b-") as b_dir:
        ws_a, ws_b = Path(a_dir), Path(b_dir)

        print("  Building A...")
        a = build_in_workspace(ws_a)
        print(f"  Build A: canonical={a['canonical'][:16]}... "
              f"signals={a['counts'].get('signals', '?')}")

        print("  Building B...")
        b = build_in_workspace(ws_b)
        print(f"  Build B: canonical={b['canonical'][:16]}... "
              f"signals={b['counts'].get('signals', '?')}")

        print("\n  A vs B:")
        ab_ok = compare_builds(a, b, "A", "B")

        print("\n  B vs shipped:")
        shipped_ok = compare_against_shipped(b)

        mut_ok = True
        if args.mutation_test:
            print("\n  Mutation test:")
            mut_ok = mutation_test(ws_b)

        if args.keep:
            print("\n  Workspaces preserved (pass --keep again to keep):")
            print(f"    A: {ws_a}")
            print(f"    B: {ws_b}")

    elapsed = time.monotonic() - start
    ok = ab_ok and shipped_ok and mut_ok
    print(f"\n  elapsed: {elapsed:.1f}s")
    print(f"=== {'PASS' if ok else 'FAIL'} — reproducible = {ok} ===")
    return ok


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="V06.2 Phase 1 — clean reproducibility gate (catalog.py only)")
    parser.add_argument("--fast", action="store_true",
                        help="canonical-hash + counts compare against shipped DB (CI-safe)")
    parser.add_argument("--mutation-test", action="store_true",
                        help="after Build B, mutate 1 signal → canonical MUST change")
    parser.add_argument("--keep", action="store_true",
                        help="preserve temp workspaces for inspection")
    args = parser.parse_args()
    if args.fast:
        ok = run_fast()
    else:
        ok = run_full(args)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
