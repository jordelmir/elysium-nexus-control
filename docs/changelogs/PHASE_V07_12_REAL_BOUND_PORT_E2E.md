# PHASE V07-12 — REAL BOUND-PORT CONTROL SURFACE (Master Order v0.10 · Phase 20, P0-12)

> Baseline: `fix/v0.10-truth-convergence` @ `12a6b96` (Phases 14/16/17/18).
> Goal: close audit P0-12 (a listener that advertises its REAL bound port —
> never a made-up `port=0`) and take the first step of audit action (3):
> the software-only phone↔TV E2E over a real TCP port.

## Verification evidence

| Gate | Result |
|---|---|
| `:app:testDebugUnitTest` | **99/99 tests, 0 failures** (was 97; +2 new) |
| `:app:lintDebug` | 0 errors |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

## TvLinkListener — the bound control surface

- New `transport/TvLinkListener.kt`: binds a real `ServerSocket(0)`,
  exposes `boundPort` (the OS-assigned port, `1..65535`), accepts sockets on
  a daemon thread and serves each with `TvLinkServer(dispatcher, gate)` on a
  per-link daemon thread.
- `start()` returns `State.Bound(port)` when bound; `stop()` closes the
  server socket (unblocks a pending `accept()` — fail-closed, no half-open
  accept loops) and returns to `State.Stopped`. Bind failure → `State.Failed`.
- The gate is provided per accepted socket (`() -> PairingGate`), so the
  pairing session opened on the TV screen can never contradict the gate at
  authorize time.

## App wiring (`TvNodeApp`, `SessionAwarePairingGate`)

- New `application/SessionAwarePairingGate.kt`: app-level gate that reads
  the CURRENT `PairingSession` at authorize time and delegates to
  `CodeConfirmPairingGate` semantics (pinned → reconnect; live session +
  QR nonce + code → pin + authorize; anything else → Denied).
- `TvNodeApp.startControlSurface()`: provisions the durable
  `AndroidKeyStoreTvCredentialVault`; if the Keystore vault cannot be built
  the control surface stays DOWN (fail-closed — no in-memory fallback that
  would hand out unpersisted pins). Listener starts first; discovery only
  advertises once `State.Bound` is real.
- `controlPort()` exposes the bound port for diagnostics.
- New `HonestUnsupportedDispatcher`: until the accessibility/volume executor
  lands, every action is answered `UNSUPPORTED` with a reason — never a
  fabricated `EXECUTED` (TV-FABRIC.4).

## NexusTvDiscovery — real port, never zero

- `start(advertisedPort: Int)` now REFUSES `port <= 0`: advertising a fake
  port would hand phones a door that does not exist. `NsdServiceInfo.port` =
  the listener's real bound port. `stop()` clears the remembered port.

## Tests

- `TvLinkListenerTest` (2 new):
  - Full E2E round trip over the real bound port: listener (allow-all test
    gate + counting dispatcher), `TvLinkClient` over `127.0.0.1:boundPort`,
    handshake established, ACTION → EXECUTED, clean close; the served count
    is asserted (never guessed); after `stop()` a new listener binds a fresh
    free port (no leaked handle).
  - Two listeners never share a port.
- Existing 97 tests unchanged and green.

## Next steps

- Controller-side E2E: `TvLinkClient` + NSD consumer wiring inside
  `apps/android` (audit action 3 completion), QR pairing payload surface,
  then the accessibility-mediated dispatcher.
- Wrap-key security level is API-31-gated (`UNKNOWN` below S) — an on-device
  TV pairing slice will measure it where possible.

## Commits

- Hash recorded in a follow-up doc commit (PHASE_V07_13 or amend note).