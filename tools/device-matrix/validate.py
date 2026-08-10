#!/usr/bin/env python3
"""V06-P33 — Validate databases/devices-under-test.yaml (stratified matrix).

Schema + coverage enforcement, honest by construction:
  - device ids unique, lowercase kebab
  - stratum / transport / state / priority enums enforced
  - every `required: true` stratum must have >= 1 device (the matrix must
    not silently drop a reality axis)
  - duplicates rejected, owner required, gates optional

Usage:  python3 validate.py [path-to-yaml]        (default: repo matrix)
Exit:   0 = valid, 1 = invalid (prints violations).
"""
import re
import sys
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parent.parent.parent
DEFAULT = REPO / "databases" / "devices-under-test.yaml"

ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
STATES = {"available", "planned"}
PRIORITIES = {1, 2, 3, 4, 5}


def _fail(errors, msg):
    errors.append(msg)


def validate(path: Path) -> list:
    errors = []
    if not path.exists():
        return [f"matrix file not found: {path}"]
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        return [f"YAML parse error: {exc}"]

    if not isinstance(data, dict):
        return ["top-level must be a mapping"]

    strata = data.get("strata")
    if not isinstance(strata, list) or not strata:
        errors.append("'strata' must be a non-empty list")
        strata = []
    if not isinstance(data.get("transports"), list) or not data.get("transports"):
        errors.append("'transports' must be a non-empty list")

    stratum_ids = {}
    for s in strata:
        sid = s.get("id")
        if not isinstance(sid, str) or not ID_PATTERN.match(sid or ""):
            _fail(errors, f"stratum id invalid: {sid!r}")
            continue
        if sid in stratum_ids:
            _fail(errors, f"duplicate stratum id: {sid}")
        stratum_ids[sid] = False  # filled when a device references it

    allowed_strata = set(stratum_ids)
    allowed_transports = set(data.get("transports") or [])
    seen_ids = set()
    devices = data.get("devices")
    if not isinstance(devices, list) or not devices:
        errors.append("'devices' must be a non-empty list")
        devices = []

    for d in devices:
        if not isinstance(d, dict):
            _fail(errors, f"device entry is not a mapping: {d!r}")
            continue
        did = d.get("id")
        if not isinstance(did, str) or not ID_PATTERN.match(did):
            _fail(errors, f"device id invalid: {did!r}")
        elif did in seen_ids:
            _fail(errors, f"duplicate device id: {did}")
        else:
            seen_ids.add(did)

        if not d.get("brand") or not d.get("model"):
            _fail(errors, f"{did}: brand and model are required")
        stratum = d.get("stratum")
        if stratum not in allowed_strata:
            _fail(errors, f"{did}: stratum {stratum!r} not declared in strata")
        elif stratum in stratum_ids:
            stratum_ids[stratum] = True
        transports = d.get("transports")
        if not isinstance(transports, list) or not transports:
            _fail(errors, f"{did}: transports must be a non-empty list")
        else:
            for t in transports:
                if t not in allowed_transports:
                    _fail(errors, f"{did}: transport {t!r} not declared in transports")
        priority = d.get("priority")
        if priority not in PRIORITIES:
            _fail(errors, f"{did}: priority must be 1..5 (got {priority!r})")
        state = d.get("state")
        if state not in STATES:
            _fail(errors, f"{did}: state must be one of {sorted(STATES)} (got {state!r})")
        if not d.get("owner"):
            _fail(errors, f"{did}: owner is required (who must touch it)")

    # Stratification coverage: required strata must not be empty.
    if data.get("coverage_rule") == "required_strata_must_have_devices":
        for sid in allowed_strata:
            attrs = next((s for s in strata if s.get("id") == sid), {})
            if attrs.get("required") and not stratum_ids.get(sid):
                _fail(errors, f"required stratum '{sid}' has no devices — the matrix "
                              "silently dropped a reality axis")

    return errors


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT
    errors = validate(path)
    if errors:
        print(f"INVALID {path} ({len(errors)} violation(s)):")
        for e in errors:
            print(f"  - {e}")
        return 1
    print(f"VALID {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())