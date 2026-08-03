# PHASE ULT.11 — AC/HVAC Stateful IR, ActionDispatcher, IR Learner UI

**Date:** 2026-08-02
**Commit:** d26198c
**Status:** VERIFIED (build + tests + lint green)

## What shipped

### 1. Stateful AC/HVAC IR Encoders (`IrWaveform.kt`)

Added four AC-family encoders that encode temperature + mode + fan speed into a single IR frame:

| Protocol | Carrier | Frame Size | Encode | Notes |
|----------|---------|------------|--------|-------|
| **Daikin** | 38 kHz | 48-bit | ✅ | 5800/2000 header, CRC8 |
| **Gree** | 38 kHz | 48-bit | ✅ | 9000/4500 header, CRC8 poly=0x31 |
| **Midea** | 38 kHz | 48-bit | ✅ | 4400/4400 header, inverted payload |
| **Mitsubishi** | 38 kHz | 32-bit | ✅ | 3400/1700 header, address+command |

Each encoder accepts: `address`, `powerOn`, `temperatureCelsius`, `mode` (0=auto,1=cool,2=dry,3=fan,4=heat), `fanSpeed` (0=auto,1=low,2=med,3=high).

### 2. AC Templates in DeviceCatalog

7 new AC templates added:

| ID | Brand | Model |
|----|-------|-------|
| `ac-daikin-generic` | Daikin | Generic (Inverter) |
| `ac-gree-generic` | Gree | Generic |
| `ac-midea-generic` | Midea | Generic |
| `ac-mitsubishi-generic` | Mitsubishi | Generic |
| `ac-samsung-generic` | Samsung | Generic WindFree |
| `ac-lg-generic` | LG | Generic Dual Inverter |
| `ac-carrier-generic` | Carrier | Generic |

New `AC_BUTTONS` layout: power, temp+/-, mode (auto/cool/dry/fan/heat), fan speed (auto/low/med/high).

New `DeviceCategory.AIR_CONDITIONER` added to the hub order.

### 3. AdapterActionDispatcher (`AdapterActionDispatcher.kt`)

Production [ActionDispatcher] that maps canonical [Action] to [DeviceAdapter].write() calls:

- **Protocol routing**: Looks up the adapter that supports the action's capability.
- **Command translation**: Converts [CommandValue] (OnOff, Level, Color, Climate, Lock, Position, Media) to [DeviceState].
- **Status mapping**: Translates adapter WriteResult errors to [CommandStatus] (DeviceOffline, TimedOut, Unsupported, Rejected).
- **Thread-safe**: Uses `runBlocking` for the adapter call (the Hub's runtime wraps in a coroutine in production).

### 4. DefaultAutomationStore (`DefaultAutomationStore.kt`)

In-memory [AutomationStore] with:

- 5-minute dedup window (`DEDUP_WINDOW_MS`)
- Maximum 1000 keys (`MAX_KEYS`) with LRU eviction
- Thread-safe via `ConcurrentHashMap`
- `isInFlight()` checks expiry before returning

### 5. AcControlScreen (`AcControlScreen.kt`)

Full stateful AC control UI:

- Large temperature display with +/- buttons (16-32°C range)
- Mode selector chips: Auto, Cool, Dry, Fan, Heat
- Fan speed selector chips: Auto, Low, Med, High
- Power toggle button
- Each adjustment sends a stateful IR command via the brand-specific encoder
- Auto-clearing "Enviado" pill after 2s
- Help card with usage instructions

### 6. IrLearnerScreen (`IrLearnerScreen.kt`)

IR capture result display:

- Hero card showing detected protocol + confidence score
- Protocol details panel (protocol, address, command, carrier, confidence)
- Extras display (toggle bit, repeat, sub-type)
- Collapsible raw waveform viewer
- Save button to persist to IR database
- Retry button for re-capture
- Animated waiting state when no result yet

### 7. Bug Fixes

- **TvControlScreen SIRC bug**: Was using `encodeNec()` for Sony SIRC; now uses `encodeSonySirc()`.
- **TvControlScreen Samsung**: Added proper Samsung protocol handling with `encodeSamsung()`.
- **InfraredAdapter scan()**: Now returns `ScanResult.Error` in stub mode (no transmitter).

## Files changed

| File | Lines | Change |
|------|-------|--------|
| `IrWaveform.kt` | +200 | Daikin/Gree/Midea/Mitsubishi AC encoders |
| `DeviceTemplate.kt` | +75 | 7 AC templates + AC_BUTTONS list |
| `DeviceCategory.kt` | +9 | AIR_CONDITIONER category |
| `AdapterActionDispatcher.kt` | +97 | New: production ActionDispatcher |
| `DefaultAutomationStore.kt` | +49 | New: in-memory dedup store |
| `AcControlScreen.kt` | +245 | New: stateful AC control UI |
| `IrLearnerScreen.kt` | +310 | New: IR capture result display |
| `TvControlScreen.kt` | +5/-4 | Fixed SIRC + Samsung encoding |
| `HubScreen.kt` | +1/-1 | AIR_CONDITIONER icon mapping |

## Gate results

```
./gradlew :app:testDebugUnitTest   → BUILD SUCCESSFUL (635+ tests, 0 failures)
./gradlew :app:assembleDebug       → BUILD SUCCESSFUL
./gradlew :app:lintDebug           → BUILD SUCCESSFUL (0 errors)
```

## What's next

- **IR Learner wiring**: Connect the IR receiver (photodiode on USB) to the learner and wire the save button to the Room database.
- **IR Database persistence**: Room DAO for learned IR commands, with CRUD operations.
- **AC state tracking**: Track last-sent AC state so the UI reflects current temperature/mode/fan.
- **Automation UI**: Screen to create/edit automations with triggers, conditions, and actions.
- **Elysium Link**: Real-time bidirectional communication protocol between phone and Hub.
