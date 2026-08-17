#!/usr/bin/env python3
"""
Master Order v0.10 — FINAL COMMERCIAL TRUTH GATE (repo-wide scanner).

Repo-level counterpart of `FinalTruthGate.kt` (module-level facts). This scanner
searches the TRACKED tree for regressions of the 19-zero Final Commercial Truth
Gate that only exist at repository/document level:

  Z1  hardcoded HIL / fake verification status in committed code or docs
  Z2  hardcoded regressionCount literals
  Z3  "100%" coverage/compatibility claims without an evidence pointer
  Z4  hardcoded credentials / secret-looking literals in tracked files
  Z5  production-eligible research bootstrap feeds
  Z6  claim ladder/ordinal comparisons that bypass the declarative policy
  Z7  THIRD_PARTY_NOTICES.md stale relative to the legal ledger

Exit code 0 = gate holds; 1 = failures listed (CI blocks).

Usage: python3 tools/truth_engine/final_truth_gate.py [--repo-root PATH]
"""

import json
import os
import re
import sys

DEFAULT_REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))

SKIP_DIRS = {
    ".git", "build", ".gradle", "ir-data/canonical", "node_modules",
    "apps/android/app/src/main/assets/ir",  # binary/large data tree
    "databases",
}
SKIP_EXTS = {".png", ".jpg", ".jpeg", ".db", ".webp", ".apk", ".aab", ".keystore", ".der", ".pem", ".bin"}

FAILURES: list[str] = []


def tracked_files(repo_root: str) -> list[str]:
    """Approximation of git-tracked files via `git ls-files`; falls back to os.walk."""
    import subprocess

    try:
        out = subprocess.run(
            ["git", "ls-files"], cwd=repo_root, capture_output=True, text=True, timeout=30
        )
        if out.returncode == 0:
            return [os.path.join(repo_root, line) for line in out.stdout.splitlines() if line and os.path.exists(os.path.join(repo_root, line))]
    except Exception:
        pass
    result = []
    for dirpath, dirnames, filenames in os.walk(repo_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            full = os.path.join(dirpath, name)
            if os.path.splitext(name)[1] not in SKIP_EXTS:
                result.append(full)
    return result


def scan(repo_root: str) -> None:
    files = [
        f
        for f in tracked_files(repo_root)
        if any(not f.replace(repo_root, "").startswith(os.sep + d) for d in SKIP_DIRS)
        or f.endswith((".kt", ".py", ".md", ".json", ".yml", ".yaml", ".toml", ".gradle", ".kts"))
    ]
    scan_text_files = [
        f for f in files if f.endswith((".kt", ".py", ".md", ".json", ".yml", ".yaml", ".toml", ".gradle", ".kts"))
    ]

    # Z1: hardcoded HIL / fake status
    for f in scan_text_files:
        with open(f, "r", encoding="utf-8", errors="replace") as fh:
            content = fh.read()
        for m in re.finditer(r"(?m)^\s*status\s*=\s*(?:PhysicalEvidenceStatus\.)?HIL_VERIFIED", content):
            if "/evidence/" not in f.replace("\\", "/") and "test" not in f.replace("\\", "/").lower():
                FAILURES.append(f"Z1 hardcoded HIL_VERIFIED status at {os.path.relpath(f, repo_root)}:{m.start()}")
        for m in re.finditer(r"regressionCount\s*=\s*[\"']?[1-9][0-9]*[\"']?", content):
            FAILURES.append(f"Z2 hardcoded regressionCount at {os.path.relpath(f, repo_root)}:{m.start()}")

    # Z3: bare 100% claims without evidence pointers
    for f in scan_text_files:
        if not f.endswith(".md"):
            continue
        with open(f, "r", encoding="utf-8", errors="replace") as fh:
            content = fh.read()
        if "100%" in content and "evidence" not in content.lower() and "PRODUCTION_APPROVED" not in content:
            if os.path.basename(f) in ("THIRD_PARTY_NOTICES.md", "README.md", "AGENTS.md"):
                FAILURES.append(f"Z3 '100%' claim without evidence pointer at {os.path.relpath(f, repo_root)}")

    # Z4: secret-looking literals in tracked text files
    secret_patterns = [
        r"eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}",  # JWT
        r"sbp_[A-Za-z0-9]{20,}",  # Supabase
        r"service_role\s*[:=]\s*[\"'][^\"']+[\"']",
    ]
    for f in scan_text_files:
        if ".env.example" in f or os.path.basename(f) == ".gitleaks.toml":
            continue
        with open(f, "r", encoding="utf-8", errors="replace") as fh:
            content = fh.read()
        for pattern in secret_patterns:
            for m in re.finditer(pattern, content):
                FAILURES.append(f"Z4 possible secret literal at {os.path.relpath(f, repo_root)}:{m.start()}")
                break  # one failure per file/pattern is enough

    # Z5: production-eligible bootstrap feeds
    policy_path = os.path.join(repo_root, "schemas", "protocol", "retail-core-policy-v1.json")
    if os.path.exists(policy_path):
        with open(policy_path, "r", encoding="utf-8") as fh:
            policy = json.load(fh)
        if not policy.get("rules", {}).get("researchFeedsNotProductionEligible", False):
            FAILURES.append("Z5 policy must declare researchFeedsNotProductionEligible")

    # Z6: ordinal access comparisons on TvAccessLevel outside tv-node transport
    tv_node_files = [f for f in scan_text_files if "tvnode" in f.replace("\\", "/").lower()]
    for f in tv_node_files:
        with open(f, "r", encoding="utf-8", errors="replace") as fh:
            content = fh.read()
        for m in re.finditer(r"access(?:Level)?\s*[<>=]+\s*TvAccessLevel\.|TvAccessLevel\.[A-Z_]+\.ordinal", content):
            FAILURES.append(f"Z6 ordinal access comparison at {os.path.relpath(f, repo_root)}:{m.start()}")

    # Z7: notices freshness
    notices = os.path.join(repo_root, "THIRD_PARTY_NOTICES.md")
    ledger = os.path.join(repo_root, "legal-evidence", "ledger.json")
    if os.path.exists(notices) and os.path.exists(ledger):
        with open(notices, "r", encoding="utf-8") as fh:
            notices_content = fh.read()
        with open(ledger, "r", encoding="utf-8") as fh:
            ledger_data = json.load(fh)
        entry_ids = [e["id"] for e in ledger_data.get("entries", [])]
        missing = [eid for eid in entry_ids if eid not in notices_content]
        if missing:
            FAILURES.append(f"Z7 THIRD_PARTY_NOTICES.md stale: missing ledger entries {missing}")


def main() -> int:
    repo_root = DEFAULT_REPO_ROOT
    if "--repo-root" in sys.argv:
        idx = sys.argv.index("--repo-root")
        repo_root = os.path.abspath(sys.argv[idx + 1])
    scan(repo_root)
    if FAILURES:
        print("FINAL COMMERCIAL TRUTH GATE: FAILED")
        for failure in FAILURES:
            print(f"  - {failure}")
        return 1
    print("FINAL COMMERCIAL TRUTH GATE: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
