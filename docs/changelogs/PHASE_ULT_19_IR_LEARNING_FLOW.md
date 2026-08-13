# PHASE ULT.19 — Real IR Learning Flow (Whole-Store Readiness)

**Purpose — §5, §46:** a TV that is not in the catalog is no longer a dead
end. The user learns the original remote's signal through a real capture
bridge (Nexus Receiver / agent streams raw waveforms to the phone over
Wi-Fi or USB-C, the app decodes, stores, and transmits), and universal
probing now starts with the brands actually sold in Costa Rica.

## What shipped

### 1. `IrCaptureBridge` (new, production)
- `fabric/infrared/IrCaptureBridge.kt` — TCP server on **port 7879**
  (0.0.0.0, same loop architecture as the 7878 USB-C bridge).
- Frame format (one JSON object per line):
  `{"carrierHz":38000,"pattern":[9000,4500,560,1690,...]}`
- Accepts `"carrier"` as a legacy alias. Invalid frames are rejected
  with `DIAG LEARN_FRAME_INVALID` instead of crashing.
- Each frame goes through `IrLearner.learn()` (NEC, NECx, RC5, SIRC,
  Samsung) and the decode is surfaced on the main thread.
- Lifecycle logs: `DIAG LEARN_LISTEN/OK/CLIENT/FRAME/DECODED/STOPPED`.

### 2. UI access — two real entry points (was: none)
- **Hub card "APRENDER SEÑAL IR"** (`HubScreen`).
- **"Aprender señal IR" card inside Controles de TV** (`TvControlsSection`).
- Both navigate to `HubDestination.IrLearner`.

### 3. `MainActivity` wiring — real capture state
- `learnerResultState` holds the async `LearnResult`; the bridge starts
  when the learner screen appears and stops on back/save.
- `onRetry` now **re-listens** (bridge keeps running) instead of popping.
- `onSave` persists to Room (`learned_ir_command`) with `DIAG LEARN_SAVED`.
- **`onTransmit`** (new): fires the just-captured waveform through the
  phone's IR emitter — physical verification of the learned signal.

### 4. Learner screen
- Waiting state shows "RECEPTOR EN PUERTO 7879".
- Result state: protocol/address/command/carrier/confidence, raw
  waveform, "Guardar señal" and "Probar transmitir".

### 5. Probabilistic brand-first ordering (§35)
- `BrandRanking` (pure JVM): Samsung, LG, Hisense, TCL, Panasonic, Sony,
  Philips, Sharp, Toshiba, Konka, Telstar, AIWA, RCA, JVC, Xiaomi / Mi,
  Sankey, Kintech, Challenger, Kalley, Daewoo, Hyundai, Noblex, Akai,
  Sanyo, Funai, Magnavox, Sylvania, Westinghouse, CCE, Philco → `ORDER BY
  CASE ... ELSE 99 END, b.display_name, cs.id`.
- Applied to `getAllCandidates` (all three tiers) and both paged probes.
- Verified against the production catalog: the universal probe now starts
  with Samsung code sets (previously started with ADLER).

## Tests (all green)
- `IrCaptureBridgeTest` (5): JSON parse, garbage rejection, legacy key,
  NEC golden round-trip (addr 0x07 cmd 0x45 → 100% decode), NECx decode.
- `BrandRankOrderTest` (4): CR brands first, deterministic tail, 30 ranks,
  no injection surface.
- Full suite: **1,216 unit tests green** (1,209 before + 9 new − 2 old),
  `assembleDebug` and `lintDebug` green.

## Physical verification on Honor Magic V2 (connected adb)
- Bridge listening: `LISTEN *:7879` confirmed in device network state.
- **Real capture**: a golden NEC waveform (67 slices, 38 kHz) sent to the
  bridge over adb-forward → decoded **NEC 0x7 # 0x45 at 100%**.
- UI showed: Protocolo Nec, Dirección 0x7, Comando 0x45, 38 kHz, 100%.
- **Saved to Room**: `learned_ir_command` id=1, confidence 1.0, full
  raw pattern persisted (verified by dumping the DB).
- **Physical transmission**: "Probar transmitir" fired the learned
  waveform through the real emitter →
  `TX_OK carrier=38000Hz duration=67980us slices=67`.
- Screenshots: `docs/testing/evidence/ULT19/ULT19_01_hub.png` … `_06`.

## Current state
`VERIFIED_LAB`: capture → decode → save → transmit loop proven end to
end on lab hardware. The remaining hardware link (Nexus Receiver that
samples the physical remote) lands in Phase 4 firmware; the phone side
of the loop is complete and verified.

## Next (smallest)
- List learned commands under "Mis Controles" with one-tap transmit.
- Import the 4-digit Telstar/Kintech code families (eliztech) once a
  physical remote is borrowed.