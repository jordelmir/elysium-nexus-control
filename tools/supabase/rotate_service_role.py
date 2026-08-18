#!/usr/bin/env python3
"""Supabase service-role rotation (P0-19) — fully automatic, fail-closed.

Requirements:
  - `.env` at repo root with SUPABASE_PROJECT_REF (or env var)
  - `SB_ACCESS_TOKEN` (Supabase Personal Access Token) in `.env` or env:
    dashboard → Account → Access Tokens → Generate new token

Flow (all via Management API, the ONLY way to rotate platform-issued keys):
  1. list API keys -> find the current service_role key id (revoked = false)
  2. rotate that key (old is revoked immediately; response contains the NEW key)
  3. update `.env` in place: SUPABASE_SERVICE_ROLE_KEY and SUPABASE_SECRET_KEY
     (alias of the same credential) -> new value
  4. append a rotation event WITHOUT the credential value to
     docs/security/SUPABASE_ROTATION_EVENTS.md

Fail-closed: any 401/403/404/malformed step aborts BEFORE touching .env.
Never prints a credential value; the new key is written only into .env.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / ".env"
EVENTS_FILE = ROOT / "docs" / "security" / "SUPABASE_ROTATION_EVENTS.md"
MGMT = "https://api.supabase.com/v1"


def load_env() -> dict:
    if not ENV_FILE.exists():
        sys.exit("FATAL: no .env at repo root")
    env = {}
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def api(path: str, token: str, method: str = "GET", payload: dict | None = None) -> dict:
    req = urllib.request.Request(
        f"{MGMT}{path}",
        method=method,
        headers={"Authorization": f"Bearer {token}",
                 "apikey": token,
                 "Content-Type": "application/json"},
        data=json.dumps(payload).encode() if payload is not None else None,
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:200]
        sys.exit(f"FATAL: Management API {method} {path} -> {e.code}: {detail}")


def rotate(env: dict, token: str) -> str:
    ref = env.get("SUPABASE_PROJECT_REF") or os.environ.get("SUPABASE_PROJECT_REF")
    if not ref:
        sys.exit("FATAL: SUPABASE_PROJECT_REF not set in .env")
    keys = api(f"/projects/{ref}/api-keys", token)
    service_key = next(
        (k for k in keys if k.get("name", "").lower().startswith("service_role") and not k.get("revoked")),
        None,
    )
    if service_key is None:
        service_key = next(
            (k for k in keys if k.get("name", "").lower() in ("service_role") and not k.get("revoked")),
            None,
        )
    if service_key is None:
        sys.exit("FATAL: active service_role key not found in project API keys")
    kid = service_key["id"]
    print(f"rotating service_role key id={kid} (revoked={service_key.get('revoked')})")
    rotated = api(f"/projects/{ref}/api-keys/{kid}/rotate", token, method="POST")
    new_key = rotated.get("api_key") or rotated.get("apikey") or rotated.get("key")
    if not new_key:
        sys.exit("FATAL: rotation response did not contain the new key; .env untouched")
    return new_key


def update_env(env: dict, new_key: str) -> None:
    lines = ENV_FILE.read_text().splitlines()
    updated = 0
    for i, line in enumerate(lines):
        m = re.match(r"^(SUPABASE_SERVICE_ROLE_KEY|SUPABASE_SECRET_KEY)=.*$", line)
        if m:
            lines[i] = f"{m.group(1)}={new_key}"
            updated += 1
    if updated == 0:
        sys.exit("FATAL: no SUPABASE_SERVICE_ROLE_KEY line found in .env; refusing to append")
    ENV_FILE.write_text("\n".join(lines) + "\n")
    print(f".env updated: {updated} credential line(s) replaced (values never printed)")


def record_event(env: dict) -> None:
    ref = env.get("SUPABASE_PROJECT_REF", "?")
    row = (
        f"| {date.today().isoformat()} | rotate service-role | `{ref}` | ✅ ROTADA vía "
        f"Management API (script tools/supabase/rotate_service_role.py); la anterior fue "
        f"revocada al rotar |\n"
    )
    if EVENTS_FILE.exists():
        content = EVENTS_FILE.read_text()
    else:
        content = (
            "# Supabase Rotation Events\n\n"
            "Cada fila documenta una rotación SIN contenido de credencial.\n\n"
            "| Evento | Fecha | Scope | Estado |\n| --- | --- | --- | --- |\n"
        )
    if "| 2026-08" in content and "ROTADA" in content:
        print("rotation event already recorded; skipping append")
        return
    EVENTS_FILE.write_text(content + row)
    print(f"rotation event appended: {EVENTS_FILE}")


def main() -> None:
    env = load_env()
    token = env.get("SB_ACCESS_TOKEN") or os.environ.get("SB_ACCESS_TOKEN")
    if not token:
        sys.exit(
            "BLOCKED: SB_ACCESS_TOKEN missing. Create one at device.supabase.com "
            "→ Account → Access Tokens → Generate new token, add SB_ACCESS_TOKEN= to "
            ".env (gitignored), then re-run this script. Nothing was rotated."
        )
    new_key = rotate(env, token)
    update_env(env, new_key)
    record_event(env)
    print("ROTATION COMPLETE: service_role rotated, .env updated, event recorded.")


if __name__ == "__main__":
    main()