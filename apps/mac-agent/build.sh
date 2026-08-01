#!/bin/bash
# Elysium Nexus Mac Agent — build script.
#
# Builds the Swift package in release mode and
# produces a `.app` bundle that can be copied to
# /Applications and launched. The bundle includes
# the Info.plist with NSMicrophoneUsageDescription
# and other entitlements macOS needs.
#
# Usage:
#   ./build.sh           # build + create .app
#   ./build.sh run       # build + run the agent
#   ./build.sh clean     # remove build artifacts
#
# Requirements: macOS 13+, Xcode command-line tools
# (provides swiftc), and an active network on the
# local subnet so the phone can discover the Mac.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APP_NAME="Elysium Nexus Mac Agent"
BUNDLE_ID="com.elysium.nexus.agent"
BUILD_DIR="$SCRIPT_DIR/.build"
APP_BUNDLE="$BUILD_DIR/Elysium Nexus.app"

print_banner() {
    cat <<'EOF'

  ______   _                _   _           _      ___           _              _   _ 
 |  ____| | |          ___| \ | | ___   __| | ___|_ _|_ __  _ __ | \ | | ___
 | |__  | | |   ___    / _ \  \| |/ _ \ / _` |/ _ \| || '_ \| '_ \|  \| |/ _ \
 |  __| | | |  |___| |  __/ |\  | (_) | (_| |  __/| || |_) | |_) | |\  |  __/
 |____| |_| |_|       \___|_| \_|\___/ \__,_|\___|___| .__/| .__/|_| \_|\___|
                                                    |_|   |_|

EOF
}

build() {
    print_banner
    echo "==> Building Swift package (release)…"
    swift build -c release
    echo "==> Bundling .app…"
    mkdir -p "$APP_BUNDLE/Contents/MacOS"
    mkdir -p "$APP_BUNDLE/Contents/Resources"
    # Copy the binary.
    cp ".build/release/elysium-agent" "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent"
    # Info.plist. We declare the LSUIElement
    # (no dock icon) and a high-res friendly
    # bundle ID.
    cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleDisplayName</key><string>${APP_NAME}</string>
    <key>CFBundleExecutable</key><string>ElysiumNexusAgent</string>
    <key>CFBundleIdentifier</key><string>${BUNDLE_ID}</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleName</key><string>Elysium Nexus</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSMinimumSystemVersion</key><string>13.0</string>
    <key>LSUIElement</key><true/>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSPrincipalClass</key><string>NSApplication</string>
    <key>NSLocalNetworkUsageDescription</key>
    <string>Elysium Nexus necesita acceso a la red local para que tu teléfono Android pueda descubrir y conectarse a esta Mac.</string>
</dict>
</plist>
PLIST
    echo
    echo "==> Build complete."
    echo "  Bundle: $APP_BUNDLE"
    echo
    echo "Para usar:"
    echo "  open \"$APP_BUNDLE\""
    echo
    echo "Para permisos (la primera vez):"
    echo "  Ajustes del Sistema → Privacidad y Seguridad → Accesibilidad"
    echo "  → Agregar 'Elysium Nexus Mac Agent' y activarlo."
    echo
}

run() {
    print_banner
    echo "==> Running agent…"
    open "$APP_BUNDLE"
}

clean() {
    print_banner
    echo "==> Cleaning…"
    rm -rf "$BUILD_DIR"
    echo "Done."
}

case "${1:-build}" in
    build) build ;;
    run) build; run ;;
    clean) clean ;;
    *) echo "Usage: $0 {build|run|clean}"; exit 1 ;;
esac
