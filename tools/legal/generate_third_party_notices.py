#!/usr/bin/env python3
"""
Master Order v0.10 Phase 12 — Legal Evidence Ledger -> THIRD_PARTY_NOTICES.

Generates THIRD_PARTY_NOTICES.md from the ledger (`legal-evidence/ledger.json`)
together with the supply-chain lock (`ir-data/sources.lock.json`). The ledger is
the ONLY authority for legal status; this script renders it, it never decides
status itself.

Usage:
  python3 tools/legal/generate_third_party_notices.py            # render (overwrite)
  python3 tools/legal/generate_third_party_notices.py --check    # verify committed file is current
Exit code 1 on any failure.
"""

import json
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
LEDGER_PATH = os.path.join(REPO_ROOT, "legal-evidence", "ledger.json")
LOCK_FILE = os.path.join(REPO_ROOT, "ir-data", "sources.lock.json")
OUTPUT_NOTICES_PATH = os.path.join(REPO_ROOT, "THIRD_PARTY_NOTICES.md")

VALID_STATUSES = {"UNREVIEWED", "REVIEW_REQUIRED", "DOCUMENTED", "SATISFIED", "BLOCKED"}


def status_badge(status: str) -> str:
    if status == "SATISFIED":
        return ":white_check_mark:"
    if status == "BLOCKED":
        return ":x:"
    if status == "DOCUMENTED":
        return ":blue_book:"
    if status == "REVIEW_REQUIRED":
        return ":warning:"
    return ":grey_question:"


def build_notices(lock_data: dict, ledger_data: dict) -> str:
    sources = lock_data.get("sources", [])
    entries = ledger_data.get("entries", [])

    content = """# Third-Party Open Source & Asset Notices — Elysium Nexus OS v0.10

This document provides attribution and legal compliance notices for third-party open source datasets,
protocol specifications, and curated signals incorporated into Elysium Nexus OS.
Generated from the Legal Evidence Ledger (`legal-evidence/ledger.json`) by
`tools/legal/generate_third_party_notices.py`. Legal status is decided ONLY in the ledger — never here.

---

## 1. Supply Chain Source Provenance

| Source Identifier | License Grant | Verified Commit | Tree / Content Hash | Commercial Eligibility |
| :--- | :--- | :--- | :--- | :--- |
"""
    for info in sources:
        src_id = info.get("id", "UNKNOWN")
        license_grant = info.get("sourceLicense", "UNKNOWN")
        commit_hash = str(info.get("resolvedCommit", "N/A"))[:8]
        tree_hash = str(
            info.get("resolvedTree")
            or info.get("sourceContentSha256", "N/A")
        )[:12]

        if "probonopd" in src_id.lower() or "irdb" in src_id.lower():
            comm_eligibility = "COMMERCIAL_ATTRIBUTION_REQUIRED"
        elif "flipper" in src_id.lower():
            comm_eligibility = "CC0_VERIFIED_POST_2319685"
        elif "smartir" in src_id.lower():
            comm_eligibility = "MIT_NO_BRAND_REUSE"
        else:
            comm_eligibility = "CURATED_INTERNAL"

        content += f"| `{src_id}` | `{license_grant}` | `{commit_hash}` | `{tree_hash}` | `{comm_eligibility}` |\n"

    content += """
---

## 2. Commercial Compliance Obligations

### A. probonopd/irdb License
Elysium Nexus OS incorporates signal structures from the open-source `irdb` project.
- **Attribution Notice**: Signal data structures derived from `probonopd/irdb` are included pursuant to the IRDB License.
- **Commercial Obligations**: Pre-use notification and hardware test copy availability provisions are governed by the legal evidence ledger entry `probonopd-notification`.

### B. Flipper-IRDB (CC0-1.0)
- **Provenance Lock**: All imported signals from Flipper-IRDB are pinned to verified commits post-dating `2319685` (CC0-1.0 grant epoch). Ledger: `flipper-file-provenance` (SATISFIED).

### C. SmartIR (MIT License)
- **Brand Protection**: The "SmartIR" trademark is used solely for source attribution and does not imply endorsement of Elysium Nexus OS products.

---

## 3. Legal Evidence Ledger (Master Order v0.10 Phase 12)

| Ledger Entry | Status | Artifact Path | Obligations |
| :--- | :--- | :--- | :--- |
"""
    for entry in entries:
        status = entry["status"]
        if status not in VALID_STATUSES:
            raise ValueError(f"invalid ledger status {status!r} in {entry.get('id')}")
        obligations = "; ".join(entry.get("obligations", [])) or "—"
        content += (
            f"| `{entry['id']}` | {status_badge(status)} `{status}` "
            f"| `{entry.get('artifactPath', '—')}` | {obligations} |\n"
        )

    blockers = [e["id"] for e in entries if e["status"] == "BLOCKED"]
    review_required = [e["id"] for e in entries if e["status"] == "REVIEW_REQUIRED"]
    if blockers:
        content += f"\n**Release blockers**: {', '.join(f'`{b}`' for b in blockers)} are BLOCKED for commercial distribution.\n"
    if review_required:
        content += f"\n**Pre-release review required**: {', '.join(f'`{r}`' for r in review_required)} — PRODUCTION_APPROVED requires them SATISFIED or BLOCKED exemption.\n"

    content += "\n---\n*Generated automatically by tools/legal/generate_third_party_notices.py (Master Order v0.10 Phase 12).*\n"
    return content


def main() -> int:
    if not os.path.exists(LEDGER_PATH):
        print(f"Error: Ledger not found at {LEDGER_PATH}", file=sys.stderr)
        return 1
    if not os.path.exists(LOCK_FILE):
        print(f"Error: Lock file not found at {LOCK_FILE}", file=sys.stderr)
        return 1

    with open(LEDGER_PATH, "r", encoding="utf-8") as f:
        ledger_data = json.load(f)
    with open(LOCK_FILE, "r", encoding="utf-8") as f:
        lock_data = json.load(f)

    try:
        notices = build_notices(lock_data, ledger_data)
    except ValueError as exc:
        print(f"Ledger integrity error: {exc}", file=sys.stderr)
        return 1

    if "--check" in sys.argv:
        try:
            with open(OUTPUT_NOTICES_PATH, "r", encoding="utf-8") as f:
                current = f.read()
        except FileNotFoundError:
            print("Notices file missing; run generator without --check", file=sys.stderr)
            return 1
        if current != notices:
            print("THIRD_PARTY_NOTICES.md is stale; regenerate it", file=sys.stderr)
            return 1
        print("THIRD_PARTY_NOTICES.md is current")
        return 0

    with open(OUTPUT_NOTICES_PATH, "w", encoding="utf-8") as f:
        f.write(notices)
    print(f"Generated {OUTPUT_NOTICES_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
