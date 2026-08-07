#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT="$(git rev-parse --show-toplevel)"
CACHE="$ROOT/.cache/ir-sources"
LOCKFILE="$ROOT/ir-data/sources.lock.json"
mkdir -p "$CACHE"

MODE="${1:---locked}"

if [ ! -f "$LOCKFILE" ]; then
    echo "ERROR: Lockfile missing at $LOCKFILE" >&2
    exit 1
fi

if [ "$MODE" = "--refresh-locks" ]; then
    echo "==> Refreshing source locks from remote HEAD repositories..."
    python3 "$ROOT/tools/ir-data/lock_sources.py"
    echo "==> Source locks refreshed in $LOCKFILE"
    exit 0
fi

if [ "$MODE" = "--verify-only" ]; then
    echo "==> Verifying source locks only..."
    python3 "$ROOT/tools/ir-data/verify_source_locks.py"
    echo "==> Source locks verification completed."
    exit 0
fi

echo "==> Bootstrapping IR Data Sources ($MODE) into $CACHE..."

checkout_source() {
    local name="$1"
    local repo_url="$2"
    local commit="$3"
    local target_dir="$CACHE/$name"

    if [ ! -d "$target_dir/.git" ]; then
        echo "Cloning $name..."
        git clone --filter=blob:none "$repo_url" "$target_dir"
    fi

    echo "Checking out locked commit $commit for $name..."
    git -C "$target_dir" fetch --quiet origin "$commit"
    git -C "$target_dir" checkout --detach "$commit" --quiet

    local actual_commit
    actual_commit="$(git -C "$target_dir" rev-parse HEAD)"

    if [ "$actual_commit" != "$commit" ]; then
        echo "ERROR: [$name] Lock mismatch! Expected $commit, got $actual_commit" >&2
        exit 1
    fi
}

# P1-BOOTSTRAP: Read sources from sources.lock.json — single source of truth
# No hardcoded URLs or commits. The lockfile IS the authority.
python3 -c "
import json, sys
with open('$LOCKFILE') as f:
    lock = json.load(f)
for src in lock.get('sources', []):
    sid = src['id']
    url = src['repository']
    commit = src['resolvedCommit']
    # Map lockfile IDs to directory names
    alias = {
        'harctoolbox-irp-protocols': 'irp-transmogrifier'
    }.get(sid, sid)
    print(f'{alias}|{url}|{commit}')
" | while IFS='|' read -r name url commit; do
    checkout_source "$name" "$url" "$commit"
done

echo "==> Verifying source locks..."
python3 "$ROOT/tools/ir-data/verify_source_locks.py"

echo "==> Bootstrap completed successfully with strict lock verification."
