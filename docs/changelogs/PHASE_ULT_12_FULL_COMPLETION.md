# PHASE ULT.12 — IR Database, AC State, Automation UI, Elysium Link

## What shipped

### IR Database Persistence
- `LearnedIrCommandEntity` — Room entity for captured IR signals (protocol, address, command, carrier, raw waveform, confidence, label, template link)
- `LearnedIrCommandDao` — DAO with CRUD: insert, byId, byTemplateId, all, count, deleteById, deleteByTemplateId, deleteAll
- `IrCommandDatabase` — Room database singleton (`ir_commands.db`), schema v1, `fallbackToDestructiveMigration`

### AC State Tracking
- `AcStateStore` — SharedPreferences-backed store persisting last temperature/mode/fan/power per device template
- `AcState` — Data class with serialize/parse round-trip (pipe-delimited format)
- `AcControlScreen` updated to read initial state from store and write state after every IR transmission
- `MainActivity` wired with `AcStateStore` instance

### Automation UI
- `AutomationListScreen` — Lists all automations with name, trigger, action count; supports create, edit, delete, manual run
- `AutomationEditorScreen` — Form to create/edit automations: name, trigger event selector, OnOff action config
- Navigation wired: Hub → AutomationList → AutomationEditor
- HubScreen card for "AUTOMATIZACIONES" added with §28 reference
- In-memory automation store for UI state; AutomationEngine execution on manual run

### Elysium Link Protocol
- Formal protocol spec document (`docs/protocol/ELYSIUM_LINK_SPEC.md`)
- `ElysiumLinkTransport` interface — abstraction over Wi-Fi, BLE, USB transports
- `TransportState` enum — lifecycle states (Idle, Discovering, Connecting, Pairing, Ready, etc.)

### Tests (703 total, up from 694)
- `AcStateTest` — serialize/parse round-trips, defaults, partial data
- `LearnedIrCommandEntityTest` — entity defaults, ID, extras

## Verification
- `./gradlew :app:testDebugUnitTest` → **703 tests pass**
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- `./gradlew :app:lintDebug` → BUILD SUCCESSFUL

## Files changed
- `databases/ir/LearnedIrCommandEntity.kt` — new
- `databases/ir/LearnedIrCommandDao.kt` — new
- `databases/ir/IrCommandDatabase.kt` — new
- `core/settings/AcStateStore.kt` — new
- `core/transport/elysium/ElysiumLinkTransport.kt` — new
- `ui/control/AcControlScreen.kt` — updated (state persistence)
- `ui/automation/AutomationListScreen.kt` — new
- `ui/automation/AutomationEditorScreen.kt` — new
- `ui/hub/HubNavigation.kt` — added AutomationList + AutomationEditor destinations
- `ui/hub/HubScreen.kt` — added AUTOMATIZACIONES card
- `ui/MainActivity.kt` — wired AC state, automation store, navigation branches
- `docs/protocol/ELYSIUM_LINK_SPEC.md` — new
- `test/.../AcStateTest.kt` — new
- `test/.../LearnedIrCommandEntityTest.kt` — new
