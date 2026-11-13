# Phase 0.10 — Mac Wi-Fi endpoint and pairing

## WHAT CHANGED

- The Mac dashboard now lists active private IPv4 addresses, prioritizes `en0`, refreshes them when the network path changes, and lets the user copy an address.
- The connection card explains the manual Android endpoint: the displayed Mac IP and port `7878`. The app advertises `_elysium._tcp` for clients that support discovery; the current Android workflow remains manual.
- Replaced the destructive app-install copy with a staged, signed, verified install and rollback backup.
- LAN membership no longer bypasses first-pairing confirmation. New network peers use the six-digit PIN shown by the Mac; only loopback development connections retain the local shortcut.
- Screen capture now requests permission only once per app launch and does not start the frame timer until macOS confirms Screen Recording access.
- Screen-frame JPEG payloads are encrypted with the paired channel key before they are framed and sent to Android; the Android transport already decrypts incoming frames.
- The build script prefers a stable Apple signing identity, refuses to replace the installed app with an ad-hoc build, and includes a notarization path for Developer ID releases.

## WHY

Routers or access points may block multicast discovery while allowing direct LAN connections. Showing the Mac's current address gives Android a dependable manual route. An address on the same Wi-Fi is not proof that a device is trusted, so first pairing continues to require the physical PIN confirmation.

## FILES CHANGED

- `apps/mac-agent/Sources/ElysiumAgent/LocalNetworkAddresses.swift`
- `apps/mac-agent/Sources/ElysiumAgent/main.swift`
- `apps/mac-agent/Sources/ElysiumAgent/ConnectionHandler.swift`
- `apps/mac-agent/build.sh`
- `docs/changelogs/PHASE_0_10_MAC_WIFI_PAIRING.md`

## ARCHITECTURE IMPACT

The existing TCP endpoint, encrypted channel, and Bonjour service remain in place. This change improves endpoint visibility and keeps local network discovery separate from device trust.

## TESTS ADDED

None. The deliverable is verified with a release build, bundle signature/plist checks, launch, and a phone-to-Mac TCP reachability check.

## TEST RESULTS

- `swift build -c release`: passed.
- `./build.sh build`: passed; ad-hoc bundle signature verified.
- `codesign --verify --deep --strict`: passed for `/Applications/Elysium Nexus.app`.
- `plutil -lint`: passed; `_elysium._tcp` is present in `NSBonjourServices`.

## REAL-DEVICE TEST RESULT

- Mac app launched from `/Applications`, showed `192.168.1.8` on `en0`, and listens on TCP `7878`.
- Connected Android reported `192.168.1.3/24`; `toybox nc` successfully reached `192.168.1.8:7878`.
- Android Bonjour discovery and the full six-digit pairing handshake were not exercised in this verification, so they are not claimed as verified.

## KNOWN LIMITATIONS

- The displayed IPv4 endpoint is for the same local network; router client isolation or a firewall can still prevent connection.
- This verifies TCP reachability, not completed control actions or a full pairing session.
- This Mac has zero valid Apple code-signing identities installed. The current installed bundle remains ad-hoc signed, so durable TCC permissions across rebuilt binaries and legal notarized public distribution are not yet available. Install the owner's Apple Development identity for local development and Developer ID Application identity plus a Keychain notary profile for release.
- The new stable-signing and notarization paths are prepared but were not exercised because the required identities are absent.
- Android's current user flow is manual IP entry; the Mac Bonjour advertisement does not prove Android discovery is working.

## SECURITY IMPACT

New LAN peers must confirm the Mac-generated PIN. A private IP or subnet match does not grant control.

## EVIDENCE GENERATED

Release build output, successful bundle verification, live dashboard accessibility tree, listening socket on `*:7878`, Android `wlan0` address, and successful Android TCP probe.

## MATURITY BEFORE / AFTER

- Mac app launch and endpoint display: `IMPLEMENTED` → `ON_DEVICE_VERIFIED` for this Mac and current LAN only.
- Bonjour discovery and end-to-end pairing: remains `IMPLEMENTED`; not verified in this run.

## NEXT BLOCKER

Install the owner's Apple Development identity and complete Android-to-Mac manual pairing with the displayed IP and six-digit PIN. Test notarization separately after provisioning a Developer ID certificate and stored notary profile.
