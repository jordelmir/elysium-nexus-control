#!/usr/bin/env python3
"""
Elysium Nexus — V0.6.2 Phase 0: Source Lock Schema Validator
============================================================
THE single authority for the STRUCTURE of sources.lock.json (P0-2).

It replaces every ad-hoc inline validator that used to live inside CI
workflows (which asserted `len(resolvedCommit)==40` for ALL sources and
therefore rejected first-party ARTIFACT sources whose commit identity is
a semantic tag like `curated-tv-v1`).

Rules (one implementation, used everywhere):
  * schemaVersion must be present and >= 2.
  * sources: non-empty, unique ids, at least one productionEnabled.
  * EVERY source, regardless of kind, must carry:
      id, repository, requestedRef, retrievedAtUtc,
      includedPaths (non-empty list of repo-relative paths),
      sourceLicense, licenseFilePath, licenseFileSha256 (64-hex),
      sourceContentSha256 (64-hex), productionEnabled (bool),
      approvalReference (non-empty).
  * kind == git (default when absent — legacy git checkouts):
      resolvedCommit  -> exactly 40 hex chars (git commit)
      resolvedTree    -> exactly 40 hex chars (git tree)
  * kind == artifact (first-party versioned datasets):
      resolvedCommit  -> semantic tag, NOT 40-hex (e.g. curated-tv-v1)
      resolvedTree    -> equals licenseFileSha256 (convention: the
                         artifact "tree identity" is its license blob)

Fail-closed: exit code 0 only when every rule passes.
This is a static schema check only. Byte-level content verification of
checkouts/artifacts is done by verify_source_locks.py (needs the local
resource cache) — this validator runs everywhere, including CI.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
LOCKFILE_PATH = ROOT / "ir-data" / "sources.lock.json"

_HEX64 = re.compile(r"^[0-9a-f]{64}$")
_HEX40 = re.compile(r"^[0-9a-f]{40}$")
_SEMANTIC_TAG = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]*$")

COMMON_REQUIRED = (
    "id", "repository", "requestedRef", "retrievedAtUtc",
    "includedPaths", "sourceLicense", "licenseFilePath",
    "licenseFileSha256", "sourceContentSha256",
    "productionEnabled", "approvalReference",
)


def validate() -> int:
    errors: list[str] = []

    if not LOCKFILE_PATH.exists():
        print(f"FATAL: lockfile missing at {LOCKFILE_PATH}", file=sys.stderr)
        return 1

    lock = json.loads(LOCKFILE_PATH.read_text(encoding="utf-8"))

    schema = lock.get("schemaVersion")
    if not isinstance(schema, int) or schema < 2:
        errors.append(f"schemaVersion must be an int >= 2, got {schema!r}")

    sources = lock.get("sources")
    if not isinstance(sources, list) or not sources:
        errors.append("sources must be a non-empty list")

    if isinstance(sources, list):
        ids = [s.get("id") for s in sources if isinstance(s, dict)]
        if len(ids) != len(set(ids)):
            errors.append("source ids must be unique")
        if not any(isinstance(s, dict) and s.get("productionEnabled") is True
                   for s in sources):
            errors.append("at least one source must be productionEnabled=True")

        for s in sources:
            if not isinstance(s, dict):
                errors.append("each source must be a JSON object")
                continue
            sid = s.get("id", "<unnamed>")
            for key in COMMON_REQUIRED:
                if key not in s:
                    errors.append(f"[{sid}] missing required field: {key}")
            if "includedPaths" in s and (not isinstance(s["includedPaths"], list)
                                         or not s["includedPaths"]):
                errors.append(f"[{sid}] includedPaths must be a non-empty list")
            if "productionEnabled" in s and not isinstance(s["productionEnabled"], bool):
                errors.append(f"[{sid}] productionEnabled must be a boolean")
            if "approvalReference" in s and not isinstance(s["approvalReference"], str) \
                    and s.get("approvalReference") is not None:
                errors.append(f"[{sid}] approvalReference must be a string")
            if "approvalReference" in s and isinstance(s.get("approvalReference"), str) \
                    and not s["approvalReference"].strip():
                errors.append(f"[{sid}] approvalReference must be non-empty")
            for key in ("licenseFileSha256", "sourceContentSha256"):
                val = s.get(key, "")
                if isinstance(val, str) and not _HEX64.match(val):
                    errors.append(f"[{sid}] {key} must be exactly 64 hex chars, got {val!r}")

            kind = s.get("kind", "git")
            if kind == "git":
                for key in ("resolvedCommit", "resolvedTree"):
                    val = s.get(key, "")
                    if isinstance(val, str) and not _HEX40.match(val):
                        errors.append(
                            f"[{sid}] kind=git {key} must be exactly 40 hex chars, "
                            f"got {val!r} (semantic tags only belong to kind=artifact)")
            elif kind == "artifact":
                rc = s.get("resolvedCommit", "")
                if isinstance(rc, str) and _HEX40.match(rc):
                    errors.append(
                        f"[{sid}] kind=artifact resolvedCommit must be a semantic tag "
                        f"(e.g. curated-tv-v1), not a 40-hex git SHA: {rc!r}")
                elif isinstance(rc, str) and not _SEMANTIC_TAG.match(rc):
                    errors.append(f"[{sid}] kind=artifact resolvedCommit has invalid tag: {rc!r}")
                rt = s.get("resolvedTree", "")
                lic = s.get("licenseFileSha256", "")
                if isinstance(rt, str) and isinstance(lic, str) and rt != lic:
                    errors.append(
                        f"[{sid}] kind=artifact resolvedTree must equal licenseFileSha256 "
                        f"(artifact tree identity = license blob), got {rt!r} vs {lic!r}")
            else:
                errors.append(f"[{sid}] unknown kind: {kind!r} (must be 'git' or 'artifact')")

    for err in errors:
        print(f"  ERROR: {err}", file=sys.stderr)

    if errors:
        print(f"\nFATAL: source lock schema validation FAILED with {len(errors)} errors.",
              file=sys.stderr)
        return 1

    kinds = [s.get("kind", "git") for s in sources]
    print(f"source lock schema: PASS "
          f"(schemaVersion={schema}, {len(sources)} sources, "
          f"{kinds.count('git')} git / {kinds.count('artifact')} artifact)")
    return 0


if __name__ == "__main__":
    sys.exit(validate())