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
    rm -rf "$APP_BUNDLE"
    mkdir -p "$APP_BUNDLE/Contents/MacOS"
    mkdir -p "$APP_BUNDLE/Contents/Resources"
    # Copy the binary.
    cp "$BUILD_DIR/release/elysium-agent" "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent"
    chmod +x "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent"
    # Copy icon if present
    if [ -f "/tmp/AppIcon.icns" ]; then
        cp "/tmp/AppIcon.icns" "$APP_BUNDLE/Contents/Resources/AppIcon.icns"
    fi
    if [ -f "/tmp/master_1024.png" ]; then
        cp "/tmp/master_1024.png" "$APP_BUNDLE/Contents/Resources/AppIcon.png"
    fi
    # Info.plist.
    cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleDisplayName</key><string>${APP_NAME}</string>
    <key>CFBundleExecutable</key><string>ElysiumNexusAgent</string>
    <key>CFBundleIconFile</key><string>AppIcon</string>
    <key>CFBundleIconName</key><string>AppIcon</string>
    <key>CFBundleIdentifier</key><string>${BUNDLE_ID}</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleName</key><string>Elysium Nexus</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.1.0</string>
    <key>CFBundleVersion</key><string>2</string>
    <key>LSMinimumSystemVersion</key><string>13.0</string>
    <key>LSUIElement</key><false/>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSPrincipalClass</key><string>NSApplication</string>
    <key>NSLocalNetworkUsageDescription</key>
    <string>Elysium Nexus necesita acceso a la red local para que tu teléfono Android pueda descubrir y conectarse a esta Mac.</string>
</dict>
</plist>
PLIST
    # Ad-hoc code sign the bundle
    echo "==> Signing app bundle with ad-hoc identity…"
    codesign --force --deep --sign - "$APP_BUNDLE"
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

dmg() {
    build
    echo "==> Creating DMG installer package…"
    DMG_DIR="$BUILD_DIR/dmg_staging"
    DMG_OUTPUT="$BUILD_DIR/Elysium-Nexus-Universal-Controller.dmg"
    rm -rf "$DMG_DIR" "$DMG_OUTPUT"
    mkdir -p "$DMG_DIR"
    cp -R "$APP_BUNDLE" "$DMG_DIR/"
    ln -s /Applications "$DMG_DIR/Applications"
    hdiutil create -volname "Elysium Nexus" -srcfolder "$DMG_DIR" -ov -format UDZO "$DMG_OUTPUT"
    echo "==> DMG created successfully at:"
    echo "    $DMG_OUTPUT"
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

install() {
    build
    echo "==> Installing to /Applications…"
    # Kill any running instance
    pkill -f ElysiumNexusAgent 2>/dev/null || true
    sleep 1
    # Remove old bundle
    rm -rf "/Applications/Elysium Nexus.app"
    # Copy fresh bundle
    cp -R "$APP_BUNDLE" "/Applications/Elysium Nexus.app"
    codesign --force --deep --sign - "/Applications/Elysium Nexus.app"
    echo "==> Installed & signed at /Applications/Elysium Nexus.app"
    echo "==> Launching…"
    open "/Applications/Elysium Nexus.app"
}

case "${1:-build}" in
    build) build ;;
    run) build; run ;;
    install) install ;;
    dmg) dmg ;;
    clean) clean ;;
    *) echo "Usage: $0 {build|run|install|dmg|clean}"; exit 1 ;;
esac
