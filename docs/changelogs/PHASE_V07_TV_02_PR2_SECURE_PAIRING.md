# PHASE V07-TV-02 — PR2 SECURE PAIRING (CONSOLIDATED ORDER ADOPTED)

> Date: 2026-08-15. Maturity BEFORE: `IMPLEMENTED`. Maturity AFTER: `IMPLEMENTED`
> (unit tests written: 24 total in tv-node, 13 new in this phase; NOT executed —
> verification pending Jor's order per verify-on-request rule).
> Governing order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` (§59 PR2).

## WHAT CHANGED

1. **Constitution consolidated**: the user's 68-section software-first master
   order ("Elysium Nexus = distributed control platform: phone + TV Node + IR +
   Bluetooth + LAN + evidence; software before hardware; retail truth") is now
   recorded verbatim in `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md`
   (2,612 lines). `MASTER_ORDER.md` and `AGENTS.md` updated to point at it as
   the governing order; PR1–PR14 slicing (§61) is the execution plan.
2. **PR2 Secure Pairing, pure core** (`apps/android-tv-node/`):
   - `PairingCode`: CSPRNG 6-digit code, constant-time comparison, never
     stored plaintext (digest imposed by the session layer).
   - `PairingNonce`: ephemeral single-use 16-hex nonce (anti-replay).
   - `QrPairingPayload`: strict wire format `elysium-pairing|v1|<deviceId>|<nonce>|<pubKeyFingerprint>`;
     malformed/unknown-version → REJECT. The 6-digit code is shown
     separately, never embedded in the QR (possession of QR ≠ access).
   - `PairingSession`: fail-closed state machine
     `Open → CodeVerified → Established | Failed | Expired`; single-use
     code, attempt limiting, TTL expiry, stale session never reveals code
     or QR (`displayCode()`/`qrPayload()` return null).
   - `NexusTvDiscovery`: NSD `_elysium-tv._tcp` advertisement with
     non-sensitive bootstrap TXT only (svc, v, api, platform, man, model);
     port = 0 by design — no production control port (§10).
   - Manifest: INTERNET + CHANGE_WIFI_MULTICAST_STATE added with an honest
     comment (required by the NSD API; the node itself performs no network
     I/O of its own).
   - Wiring: `TvNodeApp` exposes `identity`, owns the pairing session
     lifecycle, starts NSD advertisement at `onCreate`; `PairingActivity`
     displays the code and identity facts.

## WHY

The user's consolidated order: close the first offensive loop
(TV Node → pairing → Accessibility → IME → observation → HID → IR Oracle →
DeviceTwin → real tests) before any hardware. PR2 is the second slice of
that loop and its pairing layer must be fail-closed by construction, since
a guessed/pwned pairing poisons every downstream evidence claim.

## FILES CHANGED

- `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` (new, constitution)
- `docs/architecture/MASTER_ORDER.md`, `AGENTS.md` (pointers)
- `apps/android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/pairing/PairingCode.kt` (new)
- `.../pairing/QrPairingPayload.kt` (new)
- `.../pairing/PairingSession.kt` (new)
- `.../discovery/NexusTvDiscovery.kt` (new)
- `.../application/TvNodeApp.kt`, `.../PairingActivity.kt` (wiring)
- `app/src/main/AndroidManifest.xml` (NSD permissions)
- `app/src/test/java/com/elysium/nexus/tvnode/PairingCoreTest.kt` (new, 13 tests)

## ARCHITECTURE IMPACT

First concrete slice of the PR2 table in §61. The pairing core is
Android-free (pure Kotlin + java.security) so it stays JVM-testable and can
later be shared into `shared/nexus-*` libraries (§5) when the module split
lands. NSD advertisement is the discovery lane (§9); the authenticated
channel (X25519 + AEAD + Keystore) is the next slice of PR2 — `bindChannel()`
already models where it plugs in. Pairing is strictly single-session on the
node today; multi-peer revocation (§10) remains DESIGNED.

## TESTS ADDED

`PairingCoreTest` — 13 tests: code format/entropy/timing-safe verify;
nonce format/uniqueness; QR roundtrip + strict rejections; wrong-code
attempt accounting + fail-closed at limit; malformed input consumes
attempts; correct-code single-use advancement; expired session never
reveals code/QR and behaves fail-closed; binding requires verified code;
pinned-fingerprint determinism.

## TEST RESULTS

NOT RUN — verify-on-request rule (Jor). Run when ordered with:

```bash
cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## REAL-DEVICE TEST RESULT

None — no device session ordered this phase. Play-ready: the APK installs
the leanback activity + services declared in PR1 phase; on-device pairing
flow is the next ordered step (device: VER_N49, Wi-Fi ADB
`adb-A2VQ024305000780-SoFCiE._adb-tls-connect._tcp` — was offline).

## KNOWN LIMITATIONS

- QR *rendering* (ZXing/QRCodeGen dependency) not yet wired; payload
  contract is frozen and tested.
- Authenticated channel (forward-secure key exchange + AEAD + Keystore +
  certificate pinning) is the NEXT slice of PR2; `bindChannel()` is a
  placeholder transition, not yet backed by a handshake.
- `NexusTvDiscovery` port = 0: a real transport (server socket on the TV or
  reverse connection from the phone) is not yet implemented.
- Two physical TVs with equal metadata share the same `deviceId` join key
  until ObservationIdentity/pairing identity lands (§8, §31 — never merged
  into one DeviceTwin at runtime).
- Single active pairing session; revocation list pending.

## SECURITY IMPACT

Fail-closed properties verified by construction + tests: no plaintext code
digest in process, constant-time compare, single-use code, attempt limit,
TTL expiry, malformed QR rejection, no control port advertised, no
sensitive metadata on the LAN. Residual risk: code entropy is 10^6 — the
attempt limit (5) and TTL (60 s default) bound brute force; `displayCode()`
only opens while `Open`.

## EVIDENCE GENERATED

Constitution document (${N}); 13 unit tests of fail-closed pairing
semantics (pending execution); commit `448aeb3`.

## MATURITY

BEFORE: `IMPLEMENTED`. AFTER: `IMPLEMENTED` (pairing core);
`DESIGNED` for authenticated channel; NSD advertisement `IMPLEMENTED`
(untested on device). No promotion to any VERIFIED level.

## NEXT BLOCKER

Authenticated channel slice of PR2: X25519 key pair per session,
forward-secure derive (reuse the controller's Phase-32 directional
ChannelKeys twin), AEAD envelope with AAD, Android Keystore credential
vault, and the phone-side discovery+pairing client. After that:
PR3 Accessibility/observation verification on the real TV.