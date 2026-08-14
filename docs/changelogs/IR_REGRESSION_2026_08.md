# IR REGRESSION — V0.6.3

**Date:** 2026-08-11
**Baseline:** `cd6d3769f607e5fcc4ba79cca8086ca69b12c446` (main after PR4)
**Branch:** `fix/v0.6.3-ir-runtime-regression`
**Device:** Honor Magic V2 (VER_N49), Android 15
**Catalog:** `be7aa15f...`, 181,846,016 bytes, 85,209 signals, 2,346 code_sets
**APK:** debug-signed, installed via ADB

## Observed behavior

1. **Universal sweep:** app opens, candidates loaded, but no IR transmission occurs. Auto-scan terminates instantly.
2. **Brand probe (Sankey):** same — no IR signal emitted.
3. **Hardware:** `ConsumerIrManager` present, `hasIrEmitter = true`.

## Root causes identified

### RC-1: PagedIrProbeEngine starts with no candidate (CRITICAL)
- `pageIndex = -1`, `pageItems = emptyList()`, `currentCandidate() = null`
- First page only loads inside `nextCandidate()`
- UI reads `currentCandidate()` before calling `nextCandidate()`
- Result: null candidate → no encode → no transmit

### RC-2: Race condition in `onNextCandidate`
- `scope.launch { engine.nextCandidate() }` (suspend, async)
- Immediately reads `engine.currentCandidate()` (sync, before coroutine executes)
- Result: stale candidate or null

### RC-3: Catalog variant names don't match runtime
- Python produces: `samsung32`, `sirc15`, `sirc20`
- Runtime expects: `SAMSUNG_32`, `SIRC_15`, `SIRC_20`
- `VariantUnsupported` returned → signal silently skipped

### RC-4: SIRC silent fallback to SIRC_12
- When `variantId` is null or unknown → defaults to 5-bit address (SIRC_12)
- Contradicts fail-closed philosophy

### RC-5: Silent encode failures
- `sendTestAction` only handles `EncodeResult.Success`
- All other outcomes → no user feedback

### RC-6: Process-death restore uses `also { return@also }`
- Does NOT null the variable → stale session used

### RC-7: Catalog pipeline cmd/sub_device parameter swap (CRITICAL — FIXED)
- `insert_signal_parametric(proto, carrier_hz, addr, cmd, sub_device=-1)` signature
- ALL callers passed `(proto, carrier, addr, -1, cmd)` — `-1` landed in `cmd`, real command in `sub_device`
- Result: ALL 7,860 parametric signals had `command_value=-1`, `sub_device_value=<actual command>`
- NEC/SIRC/Samsung encoders failed: "command must be in [0, 255] (got -1)"
- **Fix:** Swapped parameter names in function signature to match caller intent
- **Verified:** TX_OK on Honor Magic V2 after rebuild

## Classification

| Area | Status |
|------|--------|
| Universal sweep | **ON-DEVICE REGRESSION** |
| Brand IR probe | **ON-DEVICE REGRESSION** |
| Catalog→Runtime variant contract | **FIXED** |
| AndroidIrTransmitter | IMPLEMENTED (not yet culpable) |

---

## RC-8: Catalog variant names don't match runtime (FIXED — 2026-08-11)

### Problem
Catalog `protocol_variants.variant_name` stored lowercase keys from `PROTOCOL_MAP`:
`sirc`, `sirc15`, `sirc20`, `nec`, `necext`, `rc5`, `rc5x`, `rc6`, `kaseikyo`, `samsung32`

Runtime `ProtocolCodecRegistry` expects uppercase with underscores:
`SIRC_12`, `SIRC_15`, `SIRC_20`, `NEC_32`, `NECx_32`, `RC5_14`, `RC5X_16`, `RC6_16`, `KASEIKYO_48`, `SAMSUNG_32`

Result: `ProtocolCodecRegistry.resolve()` failed to match variants → `VariantUnsupported` → signals silently skipped. Only 1 ENCODE_FAILED in log (SIRC `sirc` → no match for `SIRC_12/SIRC_15/SIRC_20`).

### Fix
1. **In-place DB update:** Renamed all 22 surviving `protocol_variants.variant_name` entries to normalized names matching runtime variant IDs.
2. **Merged duplicates:** Consolidated `sirc/sony12` → `SIRC_12`, `sirc15/sony15` → `SIRC_15`, `sirc20/sony20` → `SIRC_20`, `samsung/samsung32` → `SAMSUNG_32`, `nec/lg/nec1` → `NEC_32`, `kaseikyo/panasonic/panasonic_old` → `KASEIKYO_48`, `necx/necx2` → `NECx_32`.
3. **DB integrity verified:** Zero orphan signals, 85,209 total signals, 7,860 valid parametric, 366 VOLUME_UP candidates.
4. **VACUUM** applied to reclaim space from deleted rows.

### Verification
- `CatalogSignalIntegrityTest.protocolVariantNamesAreNormalized()` — **PASSES**
- All 1,190 unit tests — **GREEN**
- `lintDebug` — **CLEAN**
- `assembleDebug` — **BUILD SUCCESSFUL**

---

## RC-9: Carrier fallback for unsupported frequencies (FIXED — 2026-08-12)

### Problem
Kaseikyo signals request 37,000 Hz. Honor Magic V2 IR hardware supports only
[30000, 33000, 36000, 38000, 40000, 56000]. Every Kaseikyo transmission failed:
`TX_FAILED UnsupportedCarrier(requestedHz=37000, ...)` — hard-blocking a whole
protocol family.

### Fix (`AndroidIrTransmitter.kt`)
- Refactored transmission into `transmitLocked(m, waveform, carrierHz)`.
- Before transmit, if `carrierHz` is not in the hardware-supported ranges, pick
  the NEAREST supported frequency within ±2000 Hz (`CARRIER_FALLBACK_TOLERANCE_HZ`).
- Emits `TX_CARRIER_FALLBACK requested=..Hz used=..Hz` diagnostic event.

### Verification (ON DEVICE — Honor Magic V2)
- Log: `TX_CARRIER_FALLBACK requested=37000Hz used=36000Hz` followed by `TX_OK`
  — Kaseikyo signal transmitted successfully via fallback.
- Full universal sweep: 0 TX_FAILED, 0 ENCODE_FAILED (see RC-11).

## RC-10: Start Over after failed session recovery stalls the sweep (FIXED — 2026-08-12)

### Problem
"Session Recovery Failed — candidate identity mismatch after process death"
→ user taps Start Over FAB → stuck on "Cargando catálogo SQLite..." forever.
Root cause: `LaunchedEffect` was keyed only on `template`; Start Over re-selected
the same template, so the effect never re-ran and the catalog never re-loaded.

### Fix (`IrConnectFlow.kt`)
- Added `sweepRestartToken: Int` (`mutableIntStateOf(0)`).
- `LaunchedEffect(template, sweepRestartToken)` re-runs the load.
- Start Over `onClick` increments the token.

### Verification (ON DEVICE)
- After identity-mismatch recovery failure, tapping Start Over now re-loads the
  catalog and the sweep starts ("PROBE 1/366").

## RC-11: Auto-scan stalls at the end of the first page (FIXED — 2026-08-12)

### Problem
Universal sweep stopped at "Candidato 27 de 366" with the button reverting to
"▶ BARRIDO AUTOMÁTICO" and no error. `currentCandidate()` returns null at the
END OF THE CURRENT PAGE (`itemIndex == pageItems.size`) — the loop broke there
instead of letting `nextCandidate()` load the following page.

### Fix (`IrConnectFlow.kt`)
- Auto-scan loop now drives from `nextCandidate()` (which returns the candidate
  it advanced past and transparently loads the next page; null means the sweep
  is truly exhausted) instead of gating on `currentCandidate()`.

### Verification (ON DEVICE — full sweep)
- Sweep advanced through all pages: `CANDIDATE_EXHAUSTED tested=101`
  (101 unique fingerprints across 366 catalog candidates after dedup).
- Protocol distribution (all TX_OK): NEC=25, NECExtended=29, Raw=31, RC5=6,
  RC6=1, Samsung=6, SonySIRC=3, Kaseikyo=1 (via carrier fallback).
- 0 TX_FAILED, 0 ENCODE_FAILED, 0 carrier fallbacks beyond the one Kaseikyo.
- 101 unique pattern hashes transmitted, carriers 36,000/38,000 Hz.

## RC-12: Multi-key universal sweep — VOLUME_UP ∪ MUTE ∪ POWER_TOGGLE (2026-08-12)

### Problem
The sweep probed each candidate with VOLUME_UP only and the pool was filtered
by VOLUME_UP too: TVs reachable ONLY via MUTE or POWER_TOGGLE never appeared
(18 TV code_sets have POWER but no VOLUME_UP/MUTE), and candidates sharing the
VOLUME_UP emission but differing in MUTE/POWER were wrongly collapsed by dedup
(101 unique fingerprints vs 396 real TV candidates).

### Fix
- `IrCatalog`: added `getCandidateCountForActions` / `getCandidatePageForActions`
  (SQL `a.canonical_key IN (?,?,?)`) — pool = UNION of VOLUME_UP, MUTE, POWER_TOGGLE.
- `PagedIrProbeEngine`: new `probeKeys` parameter (default `[VOLUME_UP]` keeps
  single-key behavior); dedup now uses the multi-key fingerprint — a candidate
  collapses ONLY when every probe key it exposes is physically identical.
- `IrConnectFlow`: universal sweep uses the 3-key pool; auto-scan transmits
  every key a candidate exposes (VOLUME_UP → MUTE → POWER_TOGGLE, 900ms gaps,
  1.5s slot tail); the challenge re-transmits the SAME key that worked;
  rejection/confirmation evidence records the actual action key.
- UI: TestStep shows the active action; ChallengeStep names the confirmed key;
  "Sí, funcionó" replaces the volume-only wording.

### Catalog numbers (TV, production-approved)
- Old pool (VOLUME_UP only): 366 — dedup collapsed to 101 real emissions.
- New pool (3 keys): 396 code_sets, 396 unique multi-key tuples (no collapse).
- 18 code_sets have POWER only — reachable only with the multi-key sweep.

### Tests
- Multi-key keeps candidates sharing VOLUME_UP but differing in MUTE (2 kept).
- Multi-key collapses only fully identical key tuples (1 kept).
- MUTE-only and POWER-only candidates stay in the sweep.
- RC-12 honest totals: dedup-adjusted `totalCandidates` (101-of-366 case),
  raw count with dedup off, honest even when the first page holds no uniques.

### Pending verification (hardware)
- Rebuild + reinstall; run the universal sweep on the lab TV: expect a 396
  candidate sweep (not 101), crossing every page boundary, ending with
  CANDIDATE_EXHAUSTED tested=396.
