# PHASE V07-TV-05 — BATCH VERIFICATION + P0 FIXES (PR1+PR2 SLICES 1-3)

> Date: 2026-08-15. Maturity BEFORE: `IMPLEMENTED` (PR1 runner + PR2 slices
> 1-3 written, tests pending execution per verify-on-request rule).
> Maturity AFTER: `UNIT_VERIFIED` (73/73 JVM tests green, `assembleDebug`
> builds, `lintDebug` 0 errors — full gate executed once on Jor's order to
> "haz las pruebas" for the TV Node module).
> Order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` §62 (deliverable
> = code + tests + build + changelog + media) + §1 (TX_OK ≠ TV reacted) +
> + §19 (causal verify) + fitness/unit checks. Verify-on-request triggers
> the actual gradle run; test-discovered regressions are surfaced (Jor).

## WHAT CHANGED

First execution of the full TV Node gate surfaced live-found P0s — real
bugs, not lint cosmetics. Each was fixed at the true fault:

### P0 — Crypto would CRASH on API ≤ 32 despite "degradation" (lint NewApi x16 + real bug)
`channel/TvChannelCrypto.kt` relied on `catch (e: Exception)` around XEC
types (`NamedParameterSpec`, `XECPublicKey/Private`, `XEC*Spec`) that are
**API 33+**. On Android ≤ 32 a missing class raises `NoClassDefFoundError`
— an `Error`, NOT caught by `catch (e: Exception)` → runtime crash, the
exact opposite of the documented "node degrades, never invents".

Fixed honestly (minSdk 24 kept — cheap-Android degradation is a feature,
not a gate, unlike the controller which silenced NewApi by raising minSdk
to 33):
- `generateKeyPair()` / `computeSharedSecret()` now split by
  `Build.VERSION.SDK_INT >= 33`: modern path `@TargetApi(33)` uses XEC
  types; legacy path requests the same curve by algorithm name
  (`KeyGenerator`/`KeyFactory.getInstance("X25519")`/`"XDH"`) and rebuilds
  keys from standard PKCS8/X509 encoded forms — no API-33 class references.
- Both paths are `internal` + parity-tested so the JVM suite (where
  `SDK_INT = 0`) exercises BOTH routes.

### P0 — `AndroidTvObserver.adjustStreamVolume` compared void to 0
`AudioManager.adjustStreamVolume` is `void`; `== 0` could only ever mean
"dispatched", never "succeeded" — a silent claim bug against §1/§19.
Now returns `true` with a comment: only "dispatched"; real verification is
`observeVolume()` before/after via the causal verifier.

### P0 — `ime_config.xml` truncated input-method element
`<input-method ...` had no closing tag — malformed resource would break
the IME resource merge. Closed properly.

### P1 — `TvLinkProtocol` block-body returns
Kotlin functions with `return` inside a block body are non-local/illegal in
expression position; `decodeEnvelope`/`decodeResponseBody` reworked to a
single expression (compile error caught at first build).

### P1 — `TvNodeApp` compiled against the wrong BuildConfig
Imported `com.elysium.nexus.tvnode.BuildConfig` (the module's own).

### P1 — `PairingCode` data-class-like equality
`PairingCode` / `PairingNonce` gained `equals/hashCode/toString` — pairing
assertions compare values, not identity.

### P1 — test-name backticks with a colon + wrong-length fixture
`TvChannelCryptoTest` method name with `:` in backticks; `TvLinkProtocolTest`
"absurd frame length" now actually absurd (5 bytes).

### P1 — pre-existing engine test described non-existent behavior
`TvNodeCoreTest.executor fires once...` asserted a confirm-without-delta
the engine never did. Rewritten to reality: a `RaiserEffector` that mutates
the observed state, plus a new "stays unverified when no delta" test.

### Lint P0 (TV manifest)
`MissingTvBanner` (Leanback launcher requires a home-screen banner) and
`ImpliedTouchscreenHardware` (TVs are touchless — must be `required=false`).
Added `drawable/tv_banner.xml` (320x180, launcher look) + `android:banner`
+ the touchscreen uses-feature override.

## TEST RESULTS

`cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest :
app:assembleDebug :app:lintDebug` — **BUILD SUCCESSFUL**
- 73 tests, 0 failures (was 70 tests / 4 failures pre-fix; +3 = 2 new
  modern≡legacy parity tests + 1 rewritten-then-counted engine test).
- `assembleDebug` produces `app-debug.apk`.
- `lintDebug`: 0 errors (was 18). Remaining reported items are warnings
  (InlinedApi DPAD global actions — guarded for behavior, not errors).

## FILES CHANGED

- `channel/TvChannelCrypto.kt`: SDK-split X25519 (modern `@TargetApi(33)`
  + legacy encoded-forms path), both parity-exposed.
- `observe/AndroidTvObserver.kt`: void-adjust truthfulness (§1/§19).
- `protocol/TvLinkProtocol.kt`: block-body expression fix.
- `application/TvNodeApp.kt`: correct BuildConfig import.
- `pairing/PairingCode.kt`: value equality.
- `res/xml/ime_config.xml`: closed element.
- `res/drawable/tv_banner.xml`: new TV banner.
- `AndroidManifest.xml`: banner + touchscreen optional.
- Tests: TvChannelCryptoTest (+2 parity), TvLinkProtocolTest,
  TvLinkHandshakeTest, TvNodeCoreTest (reality rewrite + no-delta).

## MATURITY

BEFORE: `IMPLEMENTED`. AFTER: **`UNIT_VERIFIED`** — 73/73 JVM tests,
assembleDebug green, lintDebug 0 errors. Not INTEGRATION_VERIFIED yet:
cross-side byte-parity (phone vs TV) + on-device handshake are PR2 slice 4.

## NEXT BLOCKER

PR2 slice 4: the phone mirror over a real transport — NSD resolve on the
phone, socket frame pipeline (Raw server → handshake → ACTION envelopes),
phone-side `LinkSide.PHONE` channel, Android Keystore vault on both sides,
and the cross-side golden-vector parity tests. The ADB-wireless VER_N49
device is available for the on-device pairing flow after slicing.
