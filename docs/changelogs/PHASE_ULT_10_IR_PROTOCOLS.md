# PHASE ULT.10 — IR Protocol Encoders/Decoders + InfraredAdapter Wiring

**Date:** 2026-08-02
**Commit:** 6dfee6b
**Status:** VERIFIED (build + tests + lint green)

## What shipped

### 1. IR Protocol Encoders/Decoders (`IrWaveform.kt`)

Added five new encode/decode functions to the companion object:

| Protocol | Carrier | Encoding | Encode | Decode |
|----------|---------|----------|--------|--------|
| **Sony SIRC** | 40 kHz | Pulse-width | 1200/600 µs mark+space | ✅ |
| **Samsung** | 38 kHz | Pulse-distance | 4500/4500 header, 560 mark | ✅ |
| **NECx** | 38 kHz | Pulse-distance | ✅ (pre-existing) | ✅ **NEW** |
| **RC5** | 36 kHz | Manchester | ✅ (pre-existing) | ✅ **NEW** |
| **NEC** | 38 kHz | Pulse-distance | ✅ (pre-existing) | ✅ (pre-existing) |

**Sony SIRC frame format** (§6.4):
- Header: 2400 µs mark, 600 µs space
- 7 command bits + 5 address bits (LSB-first)
- Optional extended mode: 8 more address bits
- Bit encoding: 0 → 600/600, 1 → 1200/600

**Samsung frame format** (§6.4):
- Header: 4500 µs mark, 4500 µs space
- 8-bit address + 8-bit inverted address + 8-bit command + 8-bit inverted command
- Bit encoding: 0 → 560/560, 1 → 560/1690
- Trailing mark 560 µs + 0 space

### 2. Real Decoders Replace Placeholders (`IrLearner.kt`)

- **NECx decoder**: Replaced the placeholder with `IrWaveform.decodeNecExtended()` — validates 16-bit address, 8-bit command, 8-bit inverted command, and 68-entry pattern length.
- **RC5 decoder**: Replaced the placeholder with `IrWaveform.decodeRc5()` — Manchester bit extraction from 28-entry pattern, S1/S2 start bits, 5-bit address + 6-bit command.
- **SIRC decoder**: New — decodes 40 kHz pulse-width waveforms back to address + command.
- **Samsung decoder**: New — decodes 38 kHz pulse-distance waveforms with address/command inversion validation.

The `candidates` list in `decodeKnownProtocol()` now includes all five protocols: NEC, NECx, RC5, SIRC, Samsung.

The `confidenceFactor()` was updated to handle all five protocols in the `when` branch.

### 3. InfraredAdapter Wired to AndroidIrTransmitter (`InfraredAdapter.kt`)

Replaced the stub adapter with a fully wired implementation:

- **Constructor**: Accepts optional `Context?` (default null for testability).
- **`start()`**: Creates `AndroidIrTransmitter(context)` if context is available; logs emitter presence.
- **`scan()`**: Returns `ScanResult.Error` when no transmitter (stub mode); otherwise maps `DeviceCatalog.all` to `DeviceTwin` instances with correct `DeviceType` mapping.
- **`write()`**: Accepts `DeviceState.IrCommand`, encodes via `IrWaveform.encodeNec/encodeNecExtended/encodeRc5/encodeSonySirc/encodeSamsung` based on protocol name, and transmits via `AndroidIrTransmitter.transmit()`.
- **`read()`**: Returns `ReadResult.Error` (IR is transmit-only).
- **`subscribe()`**: Returns `AdapterResult.Error` (fire-and-forget).
- **`stop()`**: Clears transmitter reference, transitions to `Released`.

### 4. DeviceState.IrCommand (`DeviceTwin.kt`)

Added new sealed subclass:
```kotlin
data class IrCommand(
    val protocolName: String,
    val address: Int,
    val command: Int,
    val extras: Map<String, String> = emptyMap()
) : DeviceState()
```

This allows the automation engine and UI to dispatch IR commands through the canonical `DeviceState` interface without protocol-specific coupling.

## Files changed

| File | Lines | Change |
|------|-------|--------|
| `IrWaveform.kt` | +300 | SIRC/Samsung encoders + NECx/RC5/SIRC/Samsung decoders |
| `IrLearner.kt` | +104/-61 | Real decoders + SIRC/Samsung candidates |
| `InfraredAdapter.kt` | +151/-10 | Full rewrite: wired to transmitter |
| `DeviceTwin.kt` | +7 | `DeviceState.IrCommand` sealed subclass |

## Gate results

```
./gradlew :app:testDebugUnitTest   → BUILD SUCCESSFUL (635 tests, 0 failures)
./gradlew :app:assembleDebug       → BUILD SUCCESSFUL
./gradlew :app:lintDebug           → BUILD SUCCESSFUL (0 errors)
```

## What's next

- **Stateful AC/HVAC IR templates** (§6.5): Daikin, Gree, Midea, Mitsubishi AC protocols with temperature + mode stateful encoding.
- **ActionDispatcher**: Connect the automation engine's `Action` sealed class to the adapters (the `ActionDispatcher` in `AutomationEngine.kt` is currently a no-op).
- **IR Learner UI**: The learn flow needs a UI screen to capture IR signals and save them to the database.
