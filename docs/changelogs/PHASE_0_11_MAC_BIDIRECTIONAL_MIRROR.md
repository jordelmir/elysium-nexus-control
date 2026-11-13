# Phase 0.11 — Nexus Mac bidirectional screen integration

## Objective

Make Elysium Nexus the single Mac application entry point for the existing
Nexus Android-control channel and the JSM Android-to-Mac screen receiver.

## Integration boundary

- Nexus keeps port `7878`, encrypted pairing, Mac screen frames, and input
  injection as the Mac app's sole control endpoint.
- The ScreenMirror receiver uses Android's `_jsm_video._tcp` Bonjour endpoint
  for persistent TCP signaling and WebRTC for Android video to Mac. Its
  ScreenCaptureKit, compositor, audio mixer, and HEVC recorder are included in
  the same Nexus app bundle.
- The duplicate ScreenMirror Nexus server is deliberately excluded: binding a
  second server to port `7878` would conflict with the existing Nexus agent.
- Vanguard Fabric's distributed console/node services are not integrated by
  this phase. They require a separate service and UI migration into Nexus; this
  phase does not claim those capabilities are present.

## Changes

- Added the ScreenMirror runtime and UI sources to the Nexus Mac target.
- Added an entry in the Nexus Mac dashboard that opens the Android receiver and
  ScreenMirror studio as a window in the same app process.
- Changed Bonjour discovery to pass the discovered service endpoint directly to
  the persistent TCP signaling connection instead of probing it over UDP and
  closing it before the Android offer arrived.
- Made ScreenCapture permission reporting query the macOS permission state
  rather than return an unconditional success.
- Bundled the WebRTC framework in the Nexus `.app` and deep-signs embedded code.
- Declared `_elysium._tcp`, `_jsm_video._tcp`, and `_elysium-vanguard._tcp`
  for local network access.
- Vendored the Vanguard Fabric libraries and Console/Node panels into the Nexus
  Mac target, with entry points in the Nexus dashboard for remote desktop,
  nodes, jobs, resources, workspace, terminal, agents, security, and telemetry.
- Kept Vanguard Node's own service on its Fabric transport; its duplicate
  Nexus compatibility listener is not started because the Nexus Mac agent
  already owns port `7878`.

## Verification

- `swift build -c debug`: passed for the macOS 13 target.
- `./build.sh build`: passed after integrating Nexus, ScreenMirror, and Fabric;
  the `.app` contains WebRTC.framework and its
  nested code signature verifies. The local bundle is ad-hoc signed because
  this Mac has no Apple signing identity installed.
- `plutil -lint`, deep `codesign --verify`, and `otool -L`: passed; all three
  Bonjour service types are in the app plist and WebRTC resolves from the
  embedded Frameworks folder.
- Physical Android streaming, Mac screen delivery, pointer/trackpad events,
  recording output, and TCC persistence remain unverified.

## Remaining integration

Fabric panels and Node services are now available in the unified app, but their
pairing, remote desktop, terminal, file transfer, and job flows still require
integration testing against a second Mac Node. Android-side UI/service work
follows after Mac integration.

## Source provenance

- Elysium Vanguard Fabric: upstream `25f243ffffd108ec072868b616011af12d977eb0`;
  the existing working-copy Node UI changes were included, while its
  NexusCompatibility listener sources were excluded to prevent a second
  listener on port `7878`.
- JSM ScreenMirror: upstream `0ede5e4c1cdb0a70fe704cfca13352320c4c7aad`;
  the existing working-copy capture/runtime/dashboard changes were included.
- Nexus Mac agent and its existing local pairing/IP changes remain the host
  app and the source of truth for the Android control channel.
