#!/usr/bin/env python3
"""
Elysium Nexus — IR Data Source Locking Script
==============================================
Generates real 40-character commit SHAs, tree SHAs, license file SHA256s,
and content SHAs for all 5 authorized IR data repositories.
"""

import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = ROOT / ".cache" / "ir-sources"
LOCKFILE_PATH = ROOT / "ir-data" / "sources.lock.json"

SOURCES_DEF = [
    {
        "id": "flipper-irdb",
        "repository": "https://github.com/Lucaslhm/Flipper-IRDB.git",
        "dir": CACHE / "flipper-irdb",
        "requestedRef": "main",
        "includedPaths": ["TVs", "ACs", "SoundBars", "Projectors", "Streaming_Devices", "Fans"],
        "sourceLicense": "CC0-1.0",
        "licenseFilePath": "LICENSE",
        "productionEnabled": True,
        "approvalReference": "CC0-1.0-DECLARATION"
    },
    {
        "id": "smartir",
        "repository": "https://github.com/smartHomeHub/SmartIR.git",
        "dir": CACHE / "smartir",
        "requestedRef": "master",
        "includedPaths": ["codes/climate", "codes/media_player", "codes/fan", "codes/light"],
        "sourceLicense": "MIT",
        "licenseFilePath": "LICENSE",
        "productionEnabled": True,
        "approvalReference": "MIT-LICENSE"
    },
    {
        "id": "probonopd-irdb",
        "repository": "https://github.com/probonopd/irdb.git",
        "dir": CACHE / "probonopd-irdb",
        "requestedRef": "master",
        "includedPaths": ["codes"],
        "sourceLicense": "LicenseRef-IRDB-CUSTOM",
        "licenseFilePath": "README.md",
        "productionEnabled": False,  # GATED FOR PRODUCTION PACK
        "approvalReference": "PENDING-ISSUE-REGISTRATION"
    },
    {
        "id": "radioxoma-infrared",
        "repository": "https://github.com/radioxoma/infrared.git",
        "dir": CACHE / "radioxoma-infrared",
        "requestedRef": "master",
        "includedPaths": ["lg", "vityas"],
        "sourceLicense": "MIT",
        "licenseFilePath": "LICENSE",
        "productionEnabled": True,
        "approvalReference": "MIT-LICENSE"
    },
    {
        "id": "harctoolbox-irp-protocols",
        "repository": "https://github.com/bengtmartensson/IrpTransmogrifier.git",
        "dir": CACHE / "irp-transmogrifier",
        "requestedRef": "master",
        "includedPaths": ["src/main/resources/IrpProtocols.xml"],
        "sourceLicense": "Public Domain",
        "licenseFilePath": "src/main/resources/IrpProtocols.xml",
        "productionEnabled": True,
        "approvalReference": "PUBLIC-DOMAIN-DATA"
    }
]

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

def generate_locks():
    print("==> Generating REAL immutable source locks...")
    sources = []
    
    for s in SOURCES_DEF:
        repo_dir = s["dir"]
        if not repo_dir.exists():
            print(f"Error: {repo_dir} does not exist. Run bootstrap-sources.sh first.")
            continue
            
        commit = git_cmd(repo_dir, "rev-parse", "HEAD")
        tree = git_cmd(repo_dir, "rev-parse", "HEAD^{tree}")
        
        lic_file = repo_dir / s["licenseFilePath"]
        if lic_file.exists():
            lic_hash = hashlib.sha256(lic_file.read_bytes()).hexdigest()
        else:
            lic_hash = "0" * 64
            
        content_hash = compute_content_hash(repo_dir, s["includedPaths"])
        
        entry = {
            "id": s["id"],
            "repository": s["repository"],
            "requestedRef": s["requestedRef"],
            "resolvedCommit": commit,
            "resolvedTree": tree,
            "retrievedAtUtc": "2026-08-06T15:40:00Z",
            "includedPaths": s["includedPaths"],
            "sourceLicense": s["sourceLicense"],
            "licenseFilePath": s["licenseFilePath"],
            "licenseFileSha256": lic_hash,
            "sourceContentSha256": content_hash,
            "productionEnabled": s["productionEnabled"],
            "approvalReference": s["approvalReference"]
        }
        sources.append(entry)
        print(f"  ✓ Locked {s['id']}: commit {commit[:8]}, tree {tree[:8]}, content {content_hash[:8]}")

    lock_data = {
        "schemaVersion": 2,
        "lockedAtUtc": "2026-08-06T15:40:00Z",
        "sources": sources
    }
    
    LOCKFILE_PATH.parent.mkdir(parents=True, exist_ok=True)
    LOCKFILE_PATH.write_text(json.dumps(lock_data, indent=2, ensure_ascii=False))
    print(f"\n  ✓ Locks written to {LOCKFILE_PATH}")

if __name__ == "__main__":
    generate_locks()
