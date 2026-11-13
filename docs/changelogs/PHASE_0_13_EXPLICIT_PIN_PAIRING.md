# Phase 0.13 — Explicit PIN pairing on every transport

## Problem

The Mac treated any connection whose peer appeared as loopback as a trusted
development connection. The ADB bridge uses loopback for both USB and wireless
ADB, so those routes skipped the PIN window. That made a Wi-Fi ADB session look
connected without showing the PIN expected by Android.

Android's subnet fallback also accepted any open TCP 7878 endpoint, including
an address owned by the phone itself when ADB reverse exposed that port.

## Changes

- Require the displayed, user-confirmed six-digit PIN for network and ADB
  reverse sessions alike. The PIN remains redacted from logs.
- Make Android wait for the PIN on every route and remove the loopback-only
  automatic pairing branch.
- Exclude all local Android interface addresses and loopback from remembered,
  gateway, and subnet discovery candidates.
- Add structured discovery logs for skipped local candidates and pairing route.

## Verification

- Mac `swift test`: passed, 3 tests with no failures.
- Android `:integrations:nexus:testDebugUnitTest :app:assembleDebug`: passed;
  1,285 tests, no failures, errors, or skips.
- Installed APK `4.30.1` (`versionCode 65`) on the connected Honor VER-N49.
- Live Wi-Fi auto-discovery reached Android's PIN-entry screen. The Mac log
  recorded a peer at `192.168.1.3`, `HELLO`, and PIN generation; a macOS window
  query found the visible `Emparejar — Elysium Nexus` window.
- The changed Mac source compiles under `swift test`. The release bundle build
  is waiting at local signing because macOS is requesting keychain access for
  `Elysium Nexus Local Code Signing`; that updated bundle has not yet been
  installed. The live Wi-Fi route used the already-installed Mac app.

The current Mac log still reports Screen Recording and Accessibility as
disabled. Those permissions affect screen and mouse control, not PIN display.
