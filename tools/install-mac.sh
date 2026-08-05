#!/bin/bash
# =================================================================
# Elysium Nexus — Universal Mac Agent Installer
# 1-Click setup for ANY Mac (Apple Silicon M1/M2/M3/M4 & Intel)
# Enables Headless Auto-Start at Boot/Login + USB Auto-Bridge
# =================================================================

set -e

echo "🚀 Installing Elysium Nexus Mac Agent..."

# 1. Determine script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
AGENT_DIR="$PROJECT_ROOT/apps/mac-agent"

# 2. Build Swift binary
echo "📦 Compiling elysium-agent..."
cd "$AGENT_DIR"
swift build -c release

# 3. Create destination binary path
INSTALL_DIR="/usr/local/bin"
sudo mkdir -p "$INSTALL_DIR"
sudo cp "$AGENT_DIR/.build/release/elysium-agent" "$INSTALL_DIR/elysium-agent"
sudo chmod +x "$INSTALL_DIR/elysium-agent"

echo "✅ Installed binary to $INSTALL_DIR/elysium-agent"

# 4. Install LaunchAgent daemon for automatic boot & login startup
echo "⚙️ Registering LaunchAgent daemon for auto-start at boot/login..."
"$INSTALL_DIR/elysium-agent" --install-daemon

echo ""
echo "🎉 SUCCESS! Elysium Nexus Mac Agent is now installed & active on your Mac!"
echo "• Auto-starts at boot/login headlessly (no display required)."
echo "• Auto-bridges USB-C cable connection via ADB."
echo "• Ultra-low latency 60 FPS screen streaming."
echo ""
