# PHASE V07-11 — TV NODE SECURITY BOUNDARY (Master Order v0.10 · Phases 14, 16, 17, 18)

> Baseline: `fix/v0.10-truth-convergence` @ `e6e2700` (Phases 0–11 + 19).
> Goal: close the three security-boundary P0s from the v0.10 audit before any
> further TV Node feature work: identity pinning at full entropy, a mandatory
> pairing gate (never nullable), and fail-closed credential vault handling.

## Verification evidence

| Gate | Result |
|---|---|
| `:app:testDebugUnitTest` | **97/97 tests, 0 failures** (was 95; +2 new) |
| `:app:lintDebug` | 0 errors |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

## Phase 18 — Wrap-key truth (v0.10 §18)

- `AndroidKeyStoreTvCredentialVault` now generates the AES wrapping key with
  `setKeySize(256)` and exposes `wrappingKeySecurityLevel()` which **measures**
  the runtime Android Keystore security level via
  `SecretKeyFactory.getKeySpec(..., KeyInfo)` instead of assuming it.
- `getSecurityLevel()` requires API 31 (S); below S the function reports
  `UNKNOWN` honestly — a measured `TRUSTED_ENVIRONMENT`/`STRONGBOX` claim is
  only ever produced on devices that can actually measure it.
- No hardware guarantee is implied by construction; only runtime measurement.

## Phase 17 — Fail-closed vault handling

- `CodeConfirmPairingGate.authorize` now switches on the vault result:
  `Stored` / `AlreadyPinned` → `Authorized`; `NotFound`, `Error`, or any
  exception → `Denied`. Previously the gate returned `Authorized` after
  attempting the pin regardless of the result (audit P0-16).

## Phase 16 — Mandatory pairing gate

- `TvLinkServer.pairingGate` is now **non-null**: the `= null` default and the
  `?: return Authorized` fail-open fallback are gone. Every server accepts a
  gate; a `null` gate is a compile error.
- `stepPairingConfirm` always calls `gate.authorize(handshake.peerIdentity, confirm)`.
- Test-only `AllowAllPairingGate` lives under `src/test/…/transport/` — an
  allow-all gate can never appear in production paths.

## Phase 14 — Identity at full entropy

- `TvChannelCrypto` now exposes `fullFingerprintOf(publicKeyBytes)` — the full
  32-byte SHA-256 as 64 lowercase hex. `fingerprintOf` remains for **display
  only** (8 hex chars) and is no longer used as a pin key.
- `TvLinkHandshake` carries `peerIdentity` (64-hex full fingerprint) alongside
  the legacy display `peerFingerprint` (8-hex).
- `TvCredentialVault` contract moved to full identity: `pinPeerIdentity`,
  `isPeerIdentityPinned`, `unpinPeer`, and `requireFullPeerIdentity` (throws
  unless the value is exactly 64 lowercase hex — 8-hex values are rejected).
- `InMemoryTvCredentialVault` and `AndroidKeyStoreTvCredentialVault` re-keyed
  to full identity (audit P0-13: 8-hex fingerprint = 32 bits of pinning).

## Phase 14 — Nonce unification at 128 bits

- `PairingNonce` is now **16 bytes** (32 hex chars); regex `^[0-9a-f]{32}$`.
- `PairingConfirm` nonce regex updated to 32 hex; `parse` accepts 1 + 6 + 32.
- `QrPairingPayload` LINE regex updated to 32-hex nonce (audit P0-14: one key
  domain for QR and handshake).

## Tests

- `TvLinkHandshakeTest`: new case — full peer identity is 64 hex and is the
  durable pin key; `PhoneMirror` gains `phonePublicKeyBytes`.
- `TvCredentialVaultTest`: 64-hex pins; 8-hex value must throw.
- `PairingGateTest` / `PairingCoreTest`: 32-hex nonces, full-identity pin
  assertions.
- `TvLinkTransportTest`: all four socket clients now send a valid
  `PAIR_CONFIRM` (dummy) — the server **always** demands one sealed frame, so
  mirror tests exercise the strict wire path.

## Commits

- `39f3c69` (to be created): `feat(tv-node): v0.10 phases 14/16/17/18 — full 64-hex peer identity, 128-bit nonces, mandatory pairing gate, fail-closed vault`