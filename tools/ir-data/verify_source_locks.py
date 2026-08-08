#!/usr/bin/env python3
"""
Elysium Nexus — Source Lock Verification Script
================================================
Verifies that all local git checkouts strictly match the locked commits,
trees, license hashes, and content hashes in sources.lock.json.
Fail-closed: exits with non-zero if ANY mismatch occurs.
"""

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = ROOT / ".cache" / "ir-sources"
LOCKFILE_PATH = ROOT / "ir-data" / "sources.lock.json"

def git_cmd(repo_dir: Path, *args: str) -> str:
    res = subprocess.run(["git", "-C", str(repo_dir)] + list(args), capture_output=True, text=True, check=True)
    return res.stdout.strip()

def compute_content_hash(repo_dir: Path, included_paths: list[str]) -> str:
    h = hashlib.sha256()
    files_to_hash = []
    if not included_paths:
        for p in sorted(repo_dir.rglob("*")):
            if p.is_file() and not p.name.startswith(".git"):
                files_to_hash.append(p)
    else:
        for inc in included_paths:
            inc_path = repo_dir / inc
            if inc_path.is_file():
                files_to_hash.append(inc_path)
            elif inc_path.is_dir():
                for p in sorted(inc_path.rglob("*")):
                    if p.is_file() and not p.name.startswith(".git"):
                        files_to_hash.append(p)

    files_to_hash.sort()
    for f in files_to_hash:
        rel = f.relative_to(repo_dir).as_posix()
        h.update(rel.encode("utf-8"))
        h.update(f.read_bytes())

    return h.hexdigest()

def verify():
    print("==> Verifying IR Data Source Locks against sources.lock.json...")
    if not LOCKFILE_PATH.exists():
        print(f"ERROR: Lockfile missing at {LOCKFILE_PATH}")
        sys.exit(1)

    lock_data = json.loads(LOCKFILE_PATH.read_text(encoding="utf-8"))
    sources = lock_data.get("sources", [])

    errors = 0
    for s in sources:
        sid = s["id"]
        repo_dir = CACHE / sid
        if sid == "harctoolbox-irp-protocols":
            repo_dir = CACHE / "irp-transmogrifier"

        if not repo_dir.exists():
            print(f"  ❌ [{sid}] Repository directory missing: {repo_dir}")
            errors += 1
            continue

        actual_commit = git_cmd(repo_dir, "rev-parse", "HEAD")
        expected_commit = s["resolvedCommit"]
        if actual_commit != expected_commit:
            print(f"  ❌ [{sid}] Commit mismatch: expected {expected_commit}, got {actual_commit}")
            errors += 1
            continue

        actual_tree = git_cmd(repo_dir, "rev-parse", "HEAD^{tree}")
        expected_tree = s["resolvedTree"]
        if actual_tree != expected_tree:
            print(f"  ❌ [{sid}] Tree mismatch: expected {expected_tree}, got {actual_tree}")
            errors += 1
            continue

        lic_file = repo_dir / s["licenseFilePath"]
        if not lic_file.exists():
            print(f"  ❌ [{sid}] License file missing: {lic_file}")
            errors += 1
            continue

        actual_lic_hash = hashlib.sha256(lic_file.read_bytes()).hexdigest()
        expected_lic_hash = s["licenseFileSha256"]
        if actual_lic_hash != expected_lic_hash:
            print(f"  ❌ [{sid}] License SHA mismatch: expected {expected_lic_hash}, got {actual_lic_hash}")
            errors += 1
            continue

        actual_content_hash = compute_content_hash(repo_dir, s["includedPaths"])
        expected_content_hash = s["sourceContentSha256"]
        if actual_content_hash != expected_content_hash:
            print(f"  ❌ [{sid}] Content SHA mismatch: expected {expected_content_hash}, got {actual_content_hash}")
            errors += 1
            continue

        print(f"  ✓ [{sid}] Lock verified: commit={actual_commit[:8]}, tree={actual_tree[:8]}")

    if errors > 0:
        print(f"\n❌ Source lock verification FAILED with {errors} errors.")
        sys.exit(1)
    else:
        print(f"\n✅ All {len(sources)} source locks verified successfully.")

if __name__ == "__main__":
    verify()
