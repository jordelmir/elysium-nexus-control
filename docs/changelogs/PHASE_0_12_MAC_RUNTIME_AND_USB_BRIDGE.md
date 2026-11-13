# Phase 0.12 — Unified Mac launch and ADB reverse routing

## Objective

Make the already-combined Nexus, ScreenMirror, and Vanguard Fabric Mac target
launch from its `.app` bundle and establish USB direct routes per authorized
Android ADB device.

## Changes

- Added the bundle-local `@executable_path/../Frameworks` runpath so the app
  resolves the embedded `WebRTC.framework` after it is copied out of SwiftPM.
- Changed the Mac ADB bridge to parse authorized device serials, run
  `adb -s <serial> reverse tcp:7878 tcp:7878` per device, and retry failed
  routes. Multiple connected ADB devices no longer make the unqualified
  `adb reverse` command fail as ambiguous.
- Moved ADB process checks off the UI thread and made monitoring retry after
  app launch so a later USB-debugging authorization is picked up.

## Verification

- `./build.sh build`: passed; the bundled app signature and `Info.plist`
  validate.
- The bundled executable launches and listens on TCP `7878`.
- Android reached the Mac at `192.168.1.8:7878` over the current shared Wi-Fi.
- The automatic ADB reverse route was created for the ADB-visible Android
  transports; Android reached `127.0.0.1:7878` through that reverse route.

## Limits

- The ADB-visible Android connections in this run are Wi-Fi ADB transports;
  no wired USB serial appeared in `adb devices`. The USB-C cable path therefore
  still needs a physical USB-debugging authorization test.
- The Mac was locked during UI verification. Screen Recording consent,
  completed pairing, Mac-to-Android frames, Android-to-Mac WebRTC video, and
  mouse/trackpad delivery remain unverified on this device session.
- No Apple Development or Developer ID signing identity is installed. This
  local bundle is ad-hoc signed; durable TCC permission grants and notarized
  distribution cannot be verified here.
