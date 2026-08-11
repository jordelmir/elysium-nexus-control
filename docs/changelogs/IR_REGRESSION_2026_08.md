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
| Catalog→Runtime variant contract | **INTEGRATION REGRESSION** |
| AndroidIrTransmitter | IMPLEMENTED (not yet culpable) |
