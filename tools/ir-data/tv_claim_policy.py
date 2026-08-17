#!/usr/bin/env python3
"""
Master Order v0.10 Phase 3 — ONE Evidence Policy Engine (Python consumer).

Loads the SINGLE declarative policy document `schemas/protocol/retail-core-policy-v1.json`
(exactly as the Kotlin EvidencePolicyEngine does) and exposes the Python-side
claim gate. `--check` verifies the whole consistency net:

  1. schemas copy == Android unit-test resource copy (byte-identical),
  2. policy JSON == Kotlin constants in CoreActionPolicy.kt/ClaimPromotionEngine.kt
     (policyVersion, TV core actions, claim ladder — read from source, not re-typed),
  3. Python claim derivations are exercised against a fixture and must equal the
     semantica of the Kotlin engine (regression dominance, ladder derivation).

Usage:
  python3 tools/ir-data/tv_claim_policy.py --check     # CI consistency gate (exit 1 on drift)
  python3 tools/ir-data/tv_claim_policy.py --derive <evidence.json>
"""

import hashlib
import json
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
POLICY_PATH = os.path.join(REPO_ROOT, "schemas", "protocol", "retail-core-policy-v1.json")
TEST_RESOURCE_POLICY = os.path.join(
    REPO_ROOT,
    "apps/android/app/src/test/resources/policy/retail-core-policy-v1.json",
)
CORE_ACTION_POLICY_KT = os.path.join(
    REPO_ROOT,
    "apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/promotion/CoreActionPolicy.kt",
)
CLAIM_ENGINE_KT = os.path.join(
    REPO_ROOT,
    "apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/promotion/ClaimPromotionEngine.kt",
)

FAILURES: list[str] = []


def fail(msg: str) -> None:
    FAILURES.append(msg)
    print(f"  FAIL: {msg}")


def sha256_file(path: str) -> str:
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def load_policy() -> dict:
    with open(POLICY_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def extract_kotlin_core_actions() -> set[str]:
    with open(CORE_ACTION_POLICY_KT, "r", encoding="utf-8") as f:
        src = f.read()
    m = re.search(r"TV_CORE_ACTIONS: Set<String> = setOf\((.*?)\)", src, re.S)
    if not m:
        raise RuntimeError("TV_CORE_ACTIONS not found in CoreActionPolicy.kt")
    return {part.strip().strip('"') for part in m.group(1).split(",") if part.strip()}


def extract_kotlin_policy_version() -> str:
    with open(CORE_ACTION_POLICY_KT, "r", encoding="utf-8") as f:
        src = f.read()
    m = re.search(r'const val POLICY_VERSION = "([^"]+)"', src)
    if not m:
        raise RuntimeError("POLICY_VERSION not found in CoreActionPolicy.kt")
    return m.group(1)


def extract_kotlin_claim_ladder() -> list[str]:
    with open(CLAIM_ENGINE_KT, "r", encoding="utf-8") as f:
        src = f.read()
    m = re.search(r"val CLAIM_LADDER = listOf\((.*?)\)", src, re.S)
    if not m:
        raise RuntimeError("CLAIM_LADDER not found in ClaimPromotionEngine.kt")
    return [
        part.strip().replace("DerivedClaimStatus.", "")
        for part in m.group(1).split(",")
        if part.strip()
    ]


# ---------------------------------------------------------------------------
# Python claim gate (same semantics as Kotlin ClaimPromotionEngine)
# ---------------------------------------------------------------------------

def derive_claim_status(evidence: list[dict]) -> tuple[str, bool]:
    """Returns (derived_status, has_regression). Mirrors Kotlin derivation."""
    if not evidence:
        return "STRUCTURAL_VALID", False
    statuses = [e.get("status") for e in evidence]
    has_failure = any(s in ("REGRESSION", "FAILED") for s in statuses)
    if "HIL_VERIFIED" in statuses:
        return "HIL_VERIFIED", has_failure
    if "REAL_DEVICE_OBSERVED" in statuses:
        return "REAL_DEVICE_VERIFIED", has_failure
    if "INDEPENDENT_DECODE_VERIFIED" in statuses:
        return "INDEPENDENT_DECODE_VERIFIED", has_failure
    if "ON_DEVICE_TRANSMITTED" in statuses:
        return "OPTICAL_TX_VERIFIED", has_failure
    return "RUNTIME_EXECUTABLE", has_failure


def derive_core_matrix(evidence: list[dict], core_actions: set[str]) -> dict:
    """Per-action matrix: PASS/REGRESSION/PENDING. Regression dominates. Mirrors Kotlin."""
    by_action: dict[str, list[str]] = {}
    for e in evidence:
        by_action.setdefault(e["actionKey"], []).append(e["status"])
    results: dict[str, str] = {}
    has_regression = False
    for action in sorted(core_actions):
        statuses = by_action.get(action, [])
        if not statuses:
            results[action] = "PENDING"
        elif any(s in ("REGRESSION", "FAILED") for s in statuses):
            results[action] = "REGRESSION"
            has_regression = True
        elif any(s in (
            "RUNTIME_EXECUTABLE", "ON_DEVICE_TRANSMITTED", "REAL_DEVICE_OBSERVED",
            "INDEPENDENT_DECODE_VERIFIED", "HIL_VERIFIED",
        ) for s in statuses):
            results[action] = "PASS"
        else:
            results[action] = "PENDING"
    return {
        "actionResults": results,
        "isCoreComplete": all(v in ("PASS", "UNSUPPORTED", "NOT_APPLICABLE") for v in results.values()),
        "hasRegression": has_regression,
    }


def run_check() -> int:
    policy = load_policy()

    # 1. schemas copy == test resource copy
    if not os.path.exists(TEST_RESOURCE_POLICY):
        fail(f"missing Android test-resource policy copy at {TEST_RESOURCE_POLICY}")
    else:
        json_sha = sha256_file(POLICY_PATH)
        test_sha = sha256_file(TEST_RESOURCE_POLICY)
        if json_sha != test_sha:
            fail("schemas copy and Android test-resource copy differ")

    # 2. JSON == Kotlin constants
    try:
        if policy["policyVersion"] != extract_kotlin_policy_version():
            fail("policyVersion mismatch between JSON and CoreActionPolicy.kt")
        kotlin_actions = extract_kotlin_core_actions()
        json_actions = set(policy["coreActions"]["TV"])
        if kotlin_actions != json_actions:
            fail(f"TV core actions mismatch: kotlin={kotlin_actions} json={json_actions}")
        kotlin_ladder = extract_kotlin_claim_ladder()
        if policy["claimLadder"] != kotlin_ladder:
            fail(f"claim ladder mismatch: kotlin={kotlin_ladder} json={policy['claimLadder']}")
        if not policy.get("rules", {}).get("noDefaultStatus", False):
            fail("policy must declare noDefaultStatus=true")
        if not policy.get("rules", {}).get("regressionDominates", False):
            fail("policy must declare regressionDominates=true")
    except RuntimeError as exc:
        fail(str(exc))

    # 3. Python gate semantics exercised
    fixture = [
        {"actionKey": "VOLUME_UP", "status": "ON_DEVICE_TRANSMITTED"},
        {"actionKey": "VOLUME_UP", "status": "REGRESSION"},
    ]
    matrix = derive_core_matrix(fixture, set(policy["coreActions"]["TV"]))
    if not matrix["hasRegression"]:
        fail("regression must dominate a passing action in the same cell")
    if matrix["actionResults"]["VOLUME_UP"] != "REGRESSION":
        fail("VOLUME_UP must derive REGRESSION when a regression exists")
    empty = derive_claim_status([])
    if empty != ("STRUCTURAL_VALID", False):
        fail(f"empty evidence must derive STRUCTURAL_VALID, got {empty}")

    if FAILURES:
        print(f"tv_claim_policy --check: {len(FAILURES)} failure(s)")
        return 1
    print("tv_claim_policy --check: OK (JSON == Kotlin == Python semantics)")
    return 0


def run_derive(evidence_path: str) -> int:
    with open(evidence_path, "r", encoding="utf-8") as f:
        evidence = json.load(f)
    status, has_regression = derive_claim_status(evidence)
    print(json.dumps({"derivedStatus": status, "hasRegression": has_regression}, indent=2))
    return 0


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--check" in args:
        sys.exit(run_check())
    if "--derive" in args:
        idx = args.index("--derive")
        if idx + 1 >= len(args):
            print("usage: tv_claim_policy.py --derive <evidence.json>", file=sys.stderr)
            sys.exit(2)
        sys.exit(run_derive(args[idx + 1]))
    print(__doc__)
    sys.exit(2)
