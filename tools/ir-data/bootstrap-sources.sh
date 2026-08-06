#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT="$(git rev-parse --show-toplevel)"
CACHE="$ROOT/.cache/ir-sources"
mkdir -p "$CACHE"

echo "==> Bootstrapping IR Data Sources into $CACHE..."

# 1. FLIPPER-IRDB
if [ ! -d "$CACHE/flipper-irdb" ]; then
    echo "Cloning Flipper-IRDB..."
    git clone --filter=blob:none --no-checkout https://github.com/Lucaslhm/Flipper-IRDB.git "$CACHE/flipper-irdb" || true
fi

# 2. SMARTIR
if [ ! -d "$CACHE/smartir" ]; then
    echo "Cloning SmartIR..."
    git clone --filter=blob:none --no-checkout https://github.com/smartHomeHub/SmartIR.git "$CACHE/smartir" || true
fi

# 3. PROBONOPD IRDB
if [ ! -d "$CACHE/probonopd-irdb" ]; then
    echo "Cloning probonopd/irdb..."
    git clone --filter=blob:none --no-checkout https://github.com/probonopd/irdb.git "$CACHE/probonopd-irdb" || true
fi

# 4. RADIOXOMA INFRARED
if [ ! -d "$CACHE/radioxoma-infrared" ]; then
    echo "Cloning radioxoma/infrared..."
    git clone --filter=blob:none https://github.com/radioxoma/infrared.git "$CACHE/radioxoma-infrared" || true
fi

# 5. IRP PROTOCOLS
if [ ! -d "$CACHE/irp-transmogrifier" ]; then
    echo "Cloning IrpTransmogrifier..."
    git clone --filter=blob:none --no-checkout https://github.com/bengtmartensson/IrpTransmogrifier.git "$CACHE/irp-transmogrifier" || true
fi

echo "==> Bootstrap completed successfully."
