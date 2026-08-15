# PHASE V07-TV-04 — PR2 SECURE PAIRING, SLICE 3 (PHONE↔TV WIRE PROTOCOL)

> Date: 2026-08-15. Maturity BEFORE: `IMPLEMENTED` (authenticated channel,
> direct key binding, no wire) — the phone mirrored it by passing keys
> directly, nothing framed yet.
> Maturity AFTER: `IMPLEMENTED` (wire contract: framing + §11 envelope +
> §11 response states + semantic action codec + 4-step wire handshake with
> AEAD possession proof; tests written: 25 new in this slice — NOT executed,
> verification pending Jor's order per verify-on-request rule).
> Order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` §11 (Universal
> Protocol: envelope fields, response states, semantic actions) + §10
> (pairing/anti-replay) + §61 PR2 slicing. Runs directly off PR2 slice 2's
> NEXT BLOCKER ("wire messages + handshake framing").

## WHAT CHANGED

New package `com.elysium.nexus.tvnode.protocol` (pure JVM, no Android or
network deps — JVM-testable like the controller's `MacProtocol`):

`protocol/TvLinkProtocol.kt` — the §11 Universal Protocol surface:

- Length-prefixed framing **byte-identical to `MacProtocol`**
  (`u32 BE length | u8 type | payload`, length counts the type byte but not
  itself) so the phone mirror reuses ONE codec. Frame types on a private
  range (0x10..0x18) so the TV link can never collide with the MAC link's
  0x01..0x0F.
- §11 envelope with ALL ten required fields: `protocolVersion, messageId,
  connectionId, deviceId, action, timestamp, deadline, sequenceNumber,
  capabilityContext, authMetadata` — deterministic, versioned binary layout
  (byte 0 gates the whole layout; only v1 exists, v2 must be negotiated).
- §11 response states as a first-class enum — NEVER collapsed to boolean:
  `RECEIVED, ACCEPTED, EXECUTED, OBSERVED, FAILED, UNSUPPORTED,
  PERMISSION_REQUIRED` (stable distinct byte codes 0x01..0x07).
- Semantic action codec: the full `UniversalAction` sealed tree ↔ wire codes
  — a `UniversalAction`, never an Android keycode. Forward-compat codes
  (TEXT_COMMIT, SEARCH, OPEN_APP) decode to `null`, surfaced later as
  UNSUPPORTED, never as a silent success.

`protocol/TvLinkHandshake.kt` — the 4-step wire handshake:

1. PHONE → TV `HELLO` (connectionId + phone X25519 pubkey)
2. TV → PHONE `HELLO_ACK` (TV X25519 pubkey + random 32-byte challenge)
3. PHONE → TV `NONCE_ECHO_ACK` (challenge echoed through the SEALED channel)
4. TV → PHONE `CHANNEL_READY` (link established)

- The possession proof is real: the TV **authenticates and decrypts** the
  echo under its derived RX key before comparing the challenge. A peer that
  merely relays the code cannot produce a frame that passes AEAD.
- Fail-closed on every input: malformed HELLO, wrong-size key, unexpected
  frame type, echo before challenge, echo that authenticates but whose
  plaintext differs, repeated HELLO — all → `FAILED`, and channel keys are
  only readable after `ESTABLISHED`.
- Challenge comparison uses constant-time `MessageDigest.isEqual`.

## WHY

Slice 2 derived the channel keys but handed them between objects directly;
nothing framed or authenticated the handshake across a socket. This slice
defines the wire contract the phone mirror will implement byte-for-byte:
the §11 envelope, the seven response states that downstream evidence (IR
oracle, DeviceTwin, RoutePlanner fallback) will consume, and a handshake
whose possession proof (AEAD-verified nonce echo) is the trust root for the
first ACTION frame. The response-state taxonomy is deliberate: EXECUTED
≠ OBSERVED keeps the "test passed ≠ TV reacted" rule (§1) mechanical.

## FILES CHANGED

- `apps/android-tv-node/.../tvnode/protocol/TvLinkProtocol.kt` (new)
- `apps/android-tv-node/.../tvnode/protocol/TvLinkHandshake.kt` (new)
- `apps/android-tv-node/app/src/test/.../tvnode/protocol/TvLinkProtocolTest.kt` (new, 16 tests)
- `apps/android-tv-node/app/src/test/.../tvnode/protocol/TvLinkHandshakeTest.kt` (new, 9 tests)

## ARCHITECTURE IMPACT

The wire contract is now fixed and versioned on the TV side, which makes the
controller-side mirror implementable by specification alone (no crypto
redesign): same framing, same enum codes, same envelope layout, mirrored
`LinkSide.PHONE` in the channel. The handshake replaced `bindChannel(...)`
as the pairing glue in spirit (session still owns the code/QR pin; the
handshake carries the real keys over the wire). The next slice connects
this protocol object to a real TCP/NSD transport on the phone side.

## TESTS ADDED

`TvLinkProtocolTest` (16): frame length semantics (type byte included, 4
length bytes excluded); ACTION frame round-trips through the streaming
reader; partial frames report NeedMore; unknown frame type rejected; absurd
length rejected; consecutive frames parse cleanly from one buffer; §11
envelope round-trips all ten fields; unicode device/context round-trips;
trailing-garbage rejected; sub-minimum input rejected; v2 rejected until
negotiated; deterministic re-encode; all seven response states have distinct
stable codes + reverse lookup; response body round-trips state/messageId/
detail; unknown response state rejected; navigate codec survives all four
directions; full UniversalAction tree survives encode→decode→encode; forward-
compat codes decode to null; action byte codes all distinct.

`TvLinkHandshakeTest` (9): full 4-step handshake establishes byte-mirrored
channel keys (TV rx == phone tx, TV tx == phone rx); echo that authenticates
but carries the wrong challenge fails closed; raw un-sealed challenge fails
AEAD; malformed HELLO fails with nothing emitted; wrong-size key fails; echo
before challenge rejected; ACTION before ESTABLISHED rejected; repeated HELLO
rejected; TV pub key is real 32 bytes and its 8-hex fingerprint pins the QR.

## TEST RESULTS

NOT RUN — verify-on-request (Jor). Run with:
`cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest`.

## REAL-DEVICE TEST RESULT

None ordered this phase. The transport that carries these frames over a real
socket is the NEXT slice; after it lands, the on-screen pairing flow on
VER_N49 exercises this exact handshake end-to-end.

## KNOWN LIMITATIONS

- Transport not wired yet: this protocol is pure JVM data + state machine;
  no TCP/NSD socket streams frames yet. Phone mirror (LinkSide.PHONE + NSD
  resolve + Keystore vault + cross-side golden-vector parity tests) is the
  next PR2 slice.
- `authMetadata` is carried but currently empty; it will bind the attested
  channelAd/credential metadata when the Keystore vault lands.
- HELLO carries a raw X25519 pubkey protected by TLS/plain TCP framing;
  confidentiality of handshake material is not required (public keys are
  public), integrity/replay protection is the AEAD challenge echo.
- Session TTL / peer revocation from §10 still live in PairingSession /
  DESIGNED; the handshake assumes an in-date single-use session.

## SECURITY IMPACT

Fail-closed by construction + tests: malformed input never advances state;
channel keys are unreadable before ESTABLISHED; the possession proof is
actual AEAD authentication (a passive code-relayer cannot echo a valid
frame); replay/out-of-order/repeat frames are rejected; the §11 response
states forbid a boolean "success" from hiding a silent failure; constant-time
challenge comparison.

## EVIDENCE GENERATED

`TvLinkProtocol` / `TvLinkHandshake` ground-truth implementations; 25 new
unit tests (pending execution); this changelog.

## MATURITY

BEFORE: `IMPLEMENTED` (channel keys, no wire). AFTER: `IMPLEMENTED` (wire
contract complete and test-covered; not executed). No VERIFIED promotion —
the batch run, cross-side parity, then on-device decide.

## NEXT BLOCKER

PR2 slice 4: phone mirror over a real transport — NSD resolve on the phone,
a socket pipeline that reads `TvLinkProtocol` frames (Raw server → handshake
→ ACTION envelopes), the phone-side `LinkSide.PHONE` channel, Android
Keystore credential vault on both sides, and the cross-side golden-vector
parity tests proving the phone encodes the byte-identical envelope.
