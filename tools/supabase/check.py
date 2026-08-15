#!/usr/bin/env python3
"""Elysium Nexus — Supabase connectivity checker (READ-ONLY, no secrets in output).

Loads credentials from /.env (gitignored) and performs safe health checks
against the project. NEVER prints real credential values — every key is
masked. Runs only owner/local scripts; the service role key is used solely
for a read-only OpenAPI/root introspection (no data access).

Usage:
    python3 tools/supabase/check.py
Exit code 0 = all checks healthy; 1 = something failed.
"""
import json
import os
import re
import sys
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"


def load_env(path: Path) -> dict:
    env = {}
    if not path.exists():
        print(f"[warn] {path} missing — copy from .env.example", file=sys.stderr)
    for line in path.read_text().splitlines() if path.exists() else []:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        env[k.strip()] = v.strip()
    return env


def http_get(url: str, apikey: str | None = None) -> tuple[int, str]:
    req = urllib.request.Request(url)
    if apikey:
        req.add_header("apikey", apikey)
        req.add_header("Authorization", f"Bearer {apikey}")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")[:200]
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:200]
    except Exception as e:  # noqa: BLE001 — any transport error = unhealthy
        return 0, str(e)


def mask(s: str, env: dict) -> str:
    for v in env.values():
        if not v or len(v) < 8:
            continue
        s = s.replace(v, "[MASKED]")
    return re.sub(r"(eyJ[a-zA-Z0-9_.-]+\.[a-zA-Z0-9_.-]+\.[a-zA-Z0-9_.-]+)", "[JWT]", s)


def main() -> int:
    env = load_env(ENV_PATH)
    url = env.get("SUPABASE_URL", "").rstrip("/")
    anon = env.get("SUPABASE_ANON_KEY", "") or env.get("SUPABASE_PUBLISHABLE_KEY", "")
    svc = env.get("SUPABASE_SERVICE_ROLE_KEY", "")
    if not url:
        print("FATAL: SUPABASE_URL missing (check /.env or .env.example)", file=sys.stderr)
        return 1

    checks = [
        ("storage (anon)", f"{url}/storage/v1/bucket?limit=1", anon),
        ("postgrest root (service_role, RO)", f"{url}/rest/v1/", svc),
        ("auth health", f"{url}/auth/v1/health", None),
    ]
    ok = True
    for name, endpoint, key in checks:
        code, body = http_get(endpoint, key)
        safe = mask(body, env)
        note = ""
        if code == 200:
            note = "OK"
        elif code == 401:
            note = "401 expected (endpoint requires auth/role)"
        else:
            note = "FAIL"
            ok = False
        print(f"  {name:<42} HTTP {code:>3}  {note}")
        if code not in (200, 401):
            print(f"      body: {safe.strip()}")
    print(f"\nresult: {'ALL HEALTHY' if ok else 'UNHEALTHY — see above'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
