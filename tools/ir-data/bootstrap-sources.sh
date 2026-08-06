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

checkout_source "flipper-irdb" "https://github.com/Lucaslhm/Flipper-IRDB.git" "d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d"
checkout_source "smartir" "https://github.com/smartHomeHub/SmartIR.git" "e4df2957ad915536f41ffb39daa96886d7cfe040"
checkout_source "probonopd-irdb" "https://github.com/probonopd/irdb.git" "11aa5eb3ad9fec9e5c03f170c29c1467733d9f3e"
checkout_source "radioxoma-infrared" "https://github.com/radioxoma/infrared.git" "96179666ea236e33dc9ca9350d92c0ae69eec956"
checkout_source "irp-transmogrifier" "https://github.com/bengtmartensson/IrpTransmogrifier.git" "8636d20a5036c542a54fa815ee45415537011d45"

echo "==> Verifying source locks..."
python3 "$ROOT/tools/ir-data/verify_source_locks.py"

echo "==> Bootstrap completed successfully with strict lock verification."
