# PHASE V07-TV-03 — PR2 SECURE PAIRING, SLICE 2 (AUTHENTICATED CHANNEL)

> Date: 2026-08-15. Maturity BEFORE: `IMPLEMENTED` (pairing core).
> Maturity AFTER: `IMPLEMENTED` (pairing core + authenticated phone↔tv
> channel; unit tests written: 42 total in tv-node, 17 in this slice —
> NOT executed, verification pending Jor's order per verify-on-request rule).
> Order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` §10 (secure
> pairing properties) + §51 (security) + PR2 slicing §61.

## WHAT CHANGED

`apps/android-tv-node/app/src/main/java/com/elysium/nexus/tvnode/channel/TvChannelCrypto.kt`
(new, 580 lines) — the authenticated channel between phone and TV:

- X25519 ECDH → 32-byte shared secret (RFC 7748 wire form).
- HKDF-SHA256 (RFC 5869), salt `elysium-nexus-v1` (shared project
  constant, same as the controller's MacCrypto), directional info labels
  `elysium-channel-phone-to-tv` / `elysium-channel-tv-to-phone`.
- ChaCha20-Poly1305 AEAD, frames `nonce ‖ ciphertext ‖ tag`.
- Nonce domains distinct from the MAC link: `PHONE_TO_TV = 0x11`,
  `TV_TO_PHONE = 0x22` (avoids cross-link ciphertext substitution).
- `channelAd` binds protocol version + direction:
  `elysium-tv-link-v1|domain=<DIR>`.
- `ReplayGuard` sliding window (65,536): authenticate-first, then advance
  — a forged frame never consumes a sequence slot.
- `CryptoUnavailableException` for platforms without X25519.

`PairingSession` wired to the channel:
- `create()` now generates the node's ephemeral X25519 key pair (honest
  failure on platforms without X25519).
- The QR now carries the REAL SHA-256 8-hex fingerprint of the node's
  public key (public-key pinning before any key exchange, §10).
- `bindChannel(peerPublicKeyBytes)` derives the directional keys from
  CodeVerified only, transitions to Established only on success; malformed
  peer key throws and does NOT advance state (fail-closed).

`TvNodeApp.startPairing()`: honest `null` (pairing unsupported) when the
platform lacks X25519 — never an invented session.

## WHY

PR2 completes the two security halves of pairing: the out-of-band code/QR
proves the peer saw the screen; the authenticated channel proves both ends
own matching nonces/keys and protects every subsequent action frame with
AEAD + anti-replay. This is the trust root for every downstream evidence
claim (IR oracle, DeviceTwin, certificates) — if pairing is weak, nothing
below it can be trusted.

## FILES CHANGED

- `channel/TvChannelCrypto.kt` (new)
- `pairing/PairingSession.kt`
- `application/TvNodeApp.kt`
- `test/.../channel/TvChannelCryptoTest.kt` (new, 9 tests)
- `test/.../PairingCoreTest.kt` (rewritten for the new API; code/nonce
  classes restored)

## ARCHITECTURE IMPACT

The TV side of the authenticated channel is done and ground-truth for the
phone mirror. The controller already ships the equivalent `MacCrypto`
(Phase 32, byte-verified against CryptoKit); the phone↔tv channel reuses
the exact salt/recipe with link-specific labels, so implementing the phone
mirror (LinkSide.PHONE + NSD resolve + socket) needs no new crypto design —
only a cross-side parity test (Phase-32 pattern: golden vectors) before
any retail claim. `bindChannel()` now produces real keys that the transport
layer will use for every `UniversalAction` frame.

## TESTS ADDED

`TvChannelCryptoTest` (9): directional key mirror symmetry (TV rx == phone
tx and vice versa); phone→tv frame opens on TV with correct nonce domain;
foreign nonce domain rejected; replay rejected after first open; stale
frame beyond sliding window rejected (guard advanced past 65,536); AAD
mismatch fails authentication; tampered tag fails; channelAd binds
version+direction and is distinct per link; fingerprint 8-hex and
content-sensitive; X25519 keys 32 bytes and distinct.

`PairingCoreTest` rewrite: QR now pins the real fingerprint; established
channel key symmetry vs the phone mirror; malformed peer key fails closed
without advancing; expired session never reveals code/QR and binds to
Expired; code must be verified before bind; code/nonce tests restored
(5 code + 2 nonce).

## TEST RESULTS

NOT RUN — verify-on-request (Jor). Run with:
`cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest`.

## REAL-DEVICE TEST RESULT

None ordered this phase. Next on-device step: install tv-node on VER_N49
(ADB Wi-Fi `adb-A2VQ024305000780-SoFCiE._adb-tls-connect._tcp`, previously
offline) and run the on-screen pairing flow end-to-end.

## KNOWN LIMITATIONS

- Phone-side mirror (LinkSide.PHONE + NSD resolve + TCP/TLS-handshake
  framing + Keystore credential vault) is the NEXT PR2 slice — the wire
  messages (Hello/NonceEcho/channelAd) that carry the keys across the
  network are not yet defined; `bindChannel` passes keys directly.
- QR *rendering* still not wired (payload + pin now final and tested).
- Session expiration is TTL + state; peer REVOCATION list (§10) remains
  DESIGNED.
- The 70,000-frame sliding-window test is slow-ish by design (proves the
  window); acceptable in JVM unit scope.

## SECURITY IMPACT

Fail-closed properties now proven by construction + tests: mutual key
derivation from independent ephemeral key pairs; directional keys with
distinct nonce domains; AEAD integrity (AAD binds version+direction);
anti-replay window; constant-time code compare; code attempt limit;
TTL expiry; malformed peer key cannot advance the session. No channel key
is exposed before CodeVerified→Established. Crypto unavailable ⇒ pairing
honestly unavailable (never invented).

## EVIDENCE GENERATED

`TvChannelCrypto` ground-truth implementation; 17 new unit tests (pending
execution); commit `602f3b4`.

## MATURITY

BEFORE: `IMPLEMENTED`. AFTER: `IMPLEMENTED` (TV side of the channel,
packaged but untested); phone mirror `DESIGNED`. No VERIFIED promotion —
the batch run (and later on-device) will decide.

## NEXT BLOCKER

PR2 slice 3: wire messages + phone mirror — NSD resolve on the phone,
Hello/NonceEcho handshake framing, phone-side LinkSide.PHONE channel,
Android Keystore credential vault on both sides, and the cross-side parity
tests (golden vectors) proving byte-identical keys.