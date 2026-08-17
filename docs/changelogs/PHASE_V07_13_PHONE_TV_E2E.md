# PHASE V07-13 — PHONE↔TV E2E IN THE CONTROLLER (Master Order v0.10 · Phase 21, audit action 3)

> Baseline: `fix/v0.10-truth-convergence` @ `a4def8b` (Phase 20: real bound-port listener).
> Goal: CLOSE the software-only phone↔TV Node E2E — the controller APK must
> be able to discover, pair with and command a real TV Node over the wire,
> with ONE source of truth for the protocol (audit P0-4).

## The problem (P0-4 revisited)

The controller app already shipped its OWN frame protocol (`MacProtocol`,
ChaCha20, Mac hosts) — an older wire truth. Adding a second TV wire would
have created exactly the "two truth engines" the audit forbids. Instead:

## `:tvlink` — one wire truth, compiled by both builds

- New Gradle module `apps/android/tvlink` (Android library) inside the
  controller build. Its `sourceSets` point at the tv-node build's
  ANDROID-INDEPENDENT wire packages: `canonical`, `protocol`, `channel`,
  `transport`, `pairing`, `credential`.
- The SAME physical files compile into both APKs — the controller and the
  TV Node can never drift: one source of truth for HELLO/HELLO_ACK/
  NONCE_ECHO_ACK/PAIR_CONFIRM/CHANNEL_READY/ACTION/RESPONSE bytes.
- Shared NSD service type: `TvLinkProtocol.NSD_SERVICE_TYPE` is now the one
  constant; the TV advertiser (`NexusTvDiscovery.SERVICE_TYPE`) references
  it, and the phone consumer reads it — the string cannot drift.
- Build wiring: `settings.gradle.kts` includes `:tvlink`; `:app` depends on
  it; root declares `android-library` apply-false; module mirrors the
  controller's Java/Kotlin target (17).

## Phone side

- `core/transport/tvnode/TvNodePhoneLink.kt` — the controller's thin seam
  over the shared `TvLinkClient`: `connect(host, port, confirm)` (TCP +
  X25519 handshake + PAIR_CONFIRM), `sendAction(UniversalAction)` (honest
  RESPONSE or null — never invented success), `close()`, full 64-hex
  server identity. Pure JVM.
- `core/transport/tvnode/TvNodeDiscovery.kt` — NSD consumer:
  `resolveFirst(timeout)` resolves `_elysium-tv._tcp` → host:port.
  Fail-closed: an advertised port outside `1..65535` is discarded as
  "not a real port" (P0-12 consumer side).

## E2E evidence (controller build)

| Gate | Result |
|---|---|
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL (incl. the shared `:tvlink` + the e6e2700 truth layer, first compile) |
| `:app:testDebugUnitTest --tests TvNodePhoneLinkE2eTest` | **2/2, 0 failures** |
| `:app:lintDebug` | 0 errors |
| TV Node suite (shared sources untouched semantics) | **99/99, 0 failures**, lint 0, assembleDebug green |

`TvNodePhoneLinkE2eTest` (the audit action 3 proof, NO mocks on the wire):

1. `phone pairs with and commands a real tv node over the wire` — real
   `ServerSocket`, real `TvLinkServer` + `CodeConfirmPairingGate` +
   `InMemoryTvCredentialVault` + a real `PairingSession` (its own generated
   code + QR nonce); the controller's `TvNodePhoneLink` phones
   `127.0.0.1:<port>`: handshake established, ACTION → EXECUTED, server
   finishes `Clean(1)`, and the phone's FULL 64-hex identity is durably
   pinned in the vault.
2. `phone is refused with a wrong pairing code` — the link NEVER
   establishes, the server reports the denial, nothing is pinned.

## Maturity

- Controller↔TV Node software E2E: `INTEGRATION_VERIFIED` (both builds'
   suites exercise the same wire end-to-end over real sockets).
- On-device pairing (TV + phone on the same LAN, QR UX) remains the next
   slice (Phase 22: QR + discovery UX).

## Commits

- Hash recorded in the follow-up doc commit.