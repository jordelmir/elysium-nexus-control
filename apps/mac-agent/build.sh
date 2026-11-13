#!/bin/bash
# Elysium Nexus Control — Mac build script.
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

APP_NAME="Elysium Nexus Control"
BUNDLE_ID="com.elysium.nexus.agent"
BUILD_DIR="$SCRIPT_DIR/.build"
APP_BUNDLE="$BUILD_DIR/Elysium Nexus.app"
SIGNING_IDENTITY="${ELYSIUM_CODESIGN_IDENTITY:-}"
NEXUS_SUPABASE_URL="${ELYSIUM_NEXUS_SUPABASE_URL:-https://trccikkcmdqnutwfjrbf.supabase.co}"
NEXUS_PUBLISHABLE_KEY="${ELYSIUM_NEXUS_PUBLISHABLE_KEY:-}"

resolve_signing_identity() {
    if [ -n "$SIGNING_IDENTITY" ]; then
        printf '%s\n' "$SIGNING_IDENTITY"
        return
    fi

    local identities
    identities="$(security find-identity -v -p codesigning 2>/dev/null || true)"
    local developer_id
    developer_id="$(printf '%s\n' "$identities" | sed -n 's/.*"\(Developer ID Application:.*\)"/\1/p' | head -n 1)"
    if [ -n "$developer_id" ]; then
        printf '%s\n' "$developer_id"
        return
    fi

    local apple_development
    apple_development="$(printf '%s\n' "$identities" | sed -n 's/.*"\(Apple Development:.*\)"/\1/p' | head -n 1)"
    if [ -n "$apple_development" ]; then
        printf '%s\n' "$apple_development"
        return
    fi

    local local_identity
    local_identity="$(printf '%s\n' "$identities" | sed -n 's/.*"\(Elysium Nexus Local Code Signing\)"/\1/p' | head -n 1)"
    printf '%s\n' "$local_identity"
}

sign_bundle() {
    local app_path="$1"
    local identity
    identity="$(resolve_signing_identity)"

    local framework_dir="$app_path/Contents/Frameworks"
    local framework

    if [ -n "$identity" ]; then
        echo "==> Signing with stable identity: $identity"
        # Sign each embedded framework with its own identifier. Passing --deep
        # and --identifier here overwrites nested framework identities and still
        # cannot satisfy Hardened Runtime library validation for an ad-hoc WebRTC
        # binary that has no Apple Team ID.
        if [ -d "$framework_dir" ]; then
            while IFS= read -r -d '' framework; do
                if [[ "$identity" == Developer\ ID\ Application:* || "$identity" == Apple\ Development:* ]]; then
                    codesign --force --options runtime --timestamp --sign "$identity" "$framework"
                else
                    codesign --force --options runtime --sign "$identity" "$framework"
                fi
            done < <(find "$framework_dir" -mindepth 1 -maxdepth 1 -type d -name '*.framework' -print0)
        fi

        # Apple-issued identities share a Team ID with the framework we just
        # signed. A locally trusted self-signed identity has no Team ID, so it
        # needs the narrowly documented library-validation runtime exception.
        if [[ "$identity" == Developer\ ID\ Application:* || "$identity" == Apple\ Development:* ]]; then
            codesign --force --options runtime --timestamp --identifier "$BUNDLE_ID" --sign "$identity" "$app_path"
        else
            codesign --force --options runtime --identifier "$BUNDLE_ID" \
                --entitlements "$SCRIPT_DIR/ElysiumNexus.entitlements" --sign "$identity" "$app_path"
        fi
    else
        echo "WARNING: no Apple signing identity is installed; using ad-hoc signing."
        echo "Screen Recording/Accessibility grants may reset after rebuilds. Set ELYSIUM_CODESIGN_IDENTITY to an Apple Development or Developer ID Application identity."
        if [ -d "$framework_dir" ]; then
            while IFS= read -r -d '' framework; do
                codesign --force --sign - "$framework"
            done < <(find "$framework_dir" -mindepth 1 -maxdepth 1 -type d -name '*.framework' -print0)
        fi
        codesign --force --identifier "$BUNDLE_ID" --sign - "$app_path"
    fi
}

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
    mkdir -p "$APP_BUNDLE/Contents/Frameworks"
    # Copy the binary.
    cp "$BUILD_DIR/release/elysium-agent" "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent"
    chmod +x "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent"
    # WebRTC is a dynamic framework dependency and must ship inside the single Nexus app.
    if [ -d "$BUILD_DIR/release/WebRTC.framework" ]; then
        ditto "$BUILD_DIR/release/WebRTC.framework" "$APP_BUNDLE/Contents/Frameworks/WebRTC.framework"
    else
        echo "ERROR: WebRTC.framework was not produced by SwiftPM; refusing to create a non-launchable app bundle." >&2
        exit 1
    fi
    # The package executable resolves WebRTC from its SwiftPM build directory.
    # Add the app-local Frameworks folder so the copied bundle remains portable.
    if ! otool -l "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent" | grep -Fq '@executable_path/../Frameworks'; then
        install_name_tool -add_rpath '@executable_path/../Frameworks' "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent"
    fi
    if ! otool -L "$APP_BUNDLE/Contents/MacOS/ElysiumNexusAgent" | grep -Fq '@rpath/WebRTC.framework/WebRTC'; then
        echo "ERROR: Elysium Nexus executable does not declare its bundled WebRTC dependency." >&2
        exit 1
    fi
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
    <string>Elysium Nexus necesita acceso a la red local para Android, ScreenMirror y los nodos Vanguard.</string>
    <key>NSBonjourServices</key>
    <array>
        <string>_elysium._tcp</string>
        <string>_jsm_video._tcp</string>
        <string>_elysium-vanguard._tcp</string>
    </array>
    <key>ElysiumNexusSupabaseURL</key><string>${NEXUS_SUPABASE_URL}</string>
    <key>ElysiumNexusPublishableKey</key><string>${NEXUS_PUBLISHABLE_KEY}</string>
</dict>
</plist>
PLIST
    # Remove inherited quarantine/resource metadata before signing the bundle.
    xattr -cr "$APP_BUNDLE"
    # Sign using a stable Apple identity when available; ad-hoc is local-development only.
    echo "==> Signing app bundle…"
    sign_bundle "$APP_BUNDLE"
    codesign --verify --deep --strict --verbose=2 "$APP_BUNDLE"
    echo
    echo "==> Build complete."
    echo "  Bundle: $APP_BUNDLE"
    echo
    echo "Para usar:"
    echo "  open \"$APP_BUNDLE\""
    echo
    echo "Para permisos (la primera vez):"
    echo "  Ajustes del Sistema → Privacidad y Seguridad → Accesibilidad"
echo "  → Agregar 'Elysium Nexus Control' y activarlo."
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
    if [ -z "$(resolve_signing_identity)" ]; then
        echo "ERROR: refusing to replace the installed app with an ad-hoc build. Install an Apple Development or Developer ID Application certificate first." >&2
        exit 2
    fi
    echo "==> Installing to /Applications…"
    INSTALL_TARGET="/Applications/Elysium Nexus.app"
    STAGING="/Applications/.Elysium Nexus.installing.app"
    ROLLBACK="/Applications/.Elysium Nexus.rollback.app"

    # Stage and verify before replacing the currently installed app.
    rm -rf "$STAGING" "$ROLLBACK"
    ditto "$APP_BUNDLE" "$STAGING"
    xattr -cr "$STAGING"
    codesign --verify --deep --strict --verbose=2 "$STAGING"

    # Stop the running process only after the replacement is ready.
    pkill -f ElysiumNexusAgent 2>/dev/null || true
    sleep 1

    if [ -d "$INSTALL_TARGET" ]; then
        mv "$INSTALL_TARGET" "$ROLLBACK"
    fi
    if ! mv "$STAGING" "$INSTALL_TARGET"; then
        [ ! -d "$ROLLBACK" ] || mv "$ROLLBACK" "$INSTALL_TARGET"
        echo "ERROR: Could not install replacement; previous app restored." >&2
        exit 1
    fi
    if ! codesign --verify --deep --strict --verbose=2 "$INSTALL_TARGET"; then
        rm -rf "$INSTALL_TARGET"
        [ ! -d "$ROLLBACK" ] || mv "$ROLLBACK" "$INSTALL_TARGET"
        echo "ERROR: Installed bundle failed verification; previous app restored." >&2
        exit 1
    fi

    if [ -d "$ROLLBACK" ]; then
        BACKUP="$HOME/Applications/Elysium Nexus backup $(date +%Y%m%d-%H%M%S).app"
        mkdir -p "$HOME/Applications"
        mv "$ROLLBACK" "$BACKUP"
        echo "Previous app backup: $BACKUP"
    fi
    echo "==> Installed & signed at $INSTALL_TARGET"
    echo "==> Launching…"
    open "$INSTALL_TARGET"
}

case "${1:-build}" in
    build) build ;;
    run) build; run ;;
    install) install ;;
    dmg) dmg ;;
    notarize)
        build
        IDENTITY="$(resolve_signing_identity)"
        if [[ "$IDENTITY" != Developer\ ID\ Application:* ]]; then
            echo "ERROR: notarization requires an installed Developer ID Application certificate." >&2
            exit 2
        fi
        if [ -z "${ELYSIUM_NOTARY_PROFILE:-}" ]; then
            echo "ERROR: set ELYSIUM_NOTARY_PROFILE to a notarytool profile already stored in Keychain." >&2
            exit 2
        fi
        NOTARY_ZIP="$BUILD_DIR/Elysium-Nexus-notarization.zip"
        ditto -c -k --keepParent "$APP_BUNDLE" "$NOTARY_ZIP"
        xcrun notarytool submit "$NOTARY_ZIP" --keychain-profile "$ELYSIUM_NOTARY_PROFILE" --wait
        xcrun stapler staple "$APP_BUNDLE"
        spctl --assess --type execute --verbose "$APP_BUNDLE"
        ;;
    clean) clean ;;
    *) echo "Usage: $0 {build|run|install|dmg|notarize|clean}"; exit 1 ;;
esac
