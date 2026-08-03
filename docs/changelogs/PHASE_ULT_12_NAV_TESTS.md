# PHASE ULT.12 — Navigation Wiring + Comprehensive Encoder & Dispatcher Tests

## What shipped

### Navigation wiring (AcControl + IrLearner)
- Added `AcControl(template)` and `IrLearner(learnResult)` destinations to `HubDestination` sealed class
- Added navigation branches in `MainActivity.kt` for both new destinations
- AC category now routes directly to `AcControlScreen` via `HubDestination.AcControl` (bypasses generic connect flow)
- Both screens are now reachable from the Hub

### IR encoder unit tests (SIRC + Samsung + AC brands)
- **SIRC encode tests**: waveform shape (26 entries for 12-bit, 42 for extended 20-bit), header timing (2400/600), pulse-width encoding, address/command range validation
- **Samsung encode tests**: waveform shape (68 entries), header timing (4500/4500), address/command range validation, different commands produce different waveforms
- **Daikin encode tests**: 38 kHz carrier, 100-entry waveform, address/temperature/mode/fanSpeed range validation, different temperatures produce different waveforms
- **Gree encode tests**: 38 kHz carrier, 68-entry waveform, address/temperature range validation, different modes produce different waveforms
- **Midea encode tests**: 38 kHz carrier, 100-entry waveform, temperature/mode range validation
- **Mitsubishi encode tests**: 38 kHz carrier, 68-entry waveform, address/temperature/mode/fanSpeed range validation, header timing (3400/1700)
- **Cross-protocol test**: all four AC encoders produce different waveforms for identical inputs

### AdapterActionDispatcher tests
- Routes OnOff, Level, Color, ColorTemperature, Climate, Lock, Position, Media commands to the correct adapter
- Returns `Accepted` on successful write
- Returns `DeviceOffline`, `TimedOut`, `Rejected` for corresponding error codes
- Returns `Unsupported` for Noop command and missing adapter

### DefaultAutomationStore tests
- `markInFlight` / `markCompleted` lifecycle
- Key isolation (different keys are independent)
- Safe to call `markCompleted` on non-existent key
- Multiple keys can be in-flight simultaneously
- Idempotency key generation is stable and differs for different events/devices

## Verification
- `./gradlew :app:testDebugUnitTest` → **694 tests pass** (up from 635)
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- `./gradlew :app:lintDebug` → BUILD SUCCESSFUL

## Files changed
- `apps/android/app/src/main/java/com/elysium/nexus/ui/hub/HubNavigation.kt` — added `AcControl` + `IrLearner` destinations
- `apps/android/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` — added navigation branches for AC + IR Learner
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/infrared/IrWaveformTest.kt` — added 34 new encoder tests
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/automation/AdapterActionDispatcherTest.kt` — new test file (14 tests)
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/automation/DefaultAutomationStoreTest.kt` — new test file (12 tests)
