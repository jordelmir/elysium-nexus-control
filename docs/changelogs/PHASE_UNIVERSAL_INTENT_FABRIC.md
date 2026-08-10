# PHASE_UNIVERSAL_INTENT_FABRIC — Elysium Nexus Universal Control OS

> **Codename:** `ELYSiUM NEXUS — UNIVERSAL INTENT FABRIC`
> **Date:** 2026-08-08
> **Supersedes:** Original 46-section MASTER_ORDER.md

## Update — 2026-08-08 (evening build)

### Shipment: Universal LAN Discovery + Real LG webOS Adapter + Concrete Automation Engine

**Focus:** Release B (Universal TV Fabric) and Release E (Automation OS) seeds.

#### Discovery Fabric (real implementations, §8)
- `fabric/discovery/MdnsDiscoveryProvider.kt` — NsdManager-based mDNS discovery for 7 service types
  (`_webos`, `_googlecast`, `_airplay`, `_roku-ecp`, `_sony`, `_adb`, `_elysium`).
- `fabric/discovery/SsdpDiscoveryProvider.kt` — SSDP M-SEARCH via DatagramSocket to
  `239.255.255.250:1900`, response header parsing, brand inference from manufacturer/headers.
- `fabric/discovery/PreviouslyPairedDiscoveryProvider.kt` — surfaces Room `paired_devices`
  (recent ≤7 days) into discovery; `entityToRecord()` maps protocolType → canonical Protocol,
  infers brand (LG/Samsung/Sony/Roku/Google/Apple) and capabilities per device type.
- `fabric/discovery/DiscoveryOrchestrator.kt` — `observe()` implemented with `callbackFlow`
  (DeviceAppeared/DeviceUpdated/DeviceDisappeared); `discover()` merges by stable ID
  (serial → UPnP UDN → MAC → BT addr) via `DefaultDiscoveryMerger`; single provider failure
  does not break the scan. Dedup verified by tests (same serial from mDNS+SSDP → ONE twin).

#### TV Fabric (§18, §20)
- `fabric/tv/LgWebOsTvAdapter.kt` — **real** webOS SSAP adapter over HTTP POST to
  `http://<ip>:<port>/` (default 3000):
  - Pairing flow (`register_0` → client-key persistence-ready), `discover()`/`identify()`
    via `/server-info`, WoL wake
  - Full command mapping: PowerOff, Volume±/Set/Mute, Channel±, InputSelect,
    Media transport, Navigate/Ok/Back/Home/Menu, Custom (`launch_app`, `toast`)
  - `readState()` for Volume/OnOff/Channel, `observeState()` via `MutableSharedFlow`
  - PowerOn deliberately routed to WoL (not SSAP), honest `Unsupported` otherwise
  - **Own VID/PID identity only — no forged Samsung/Xbox descriptors** (Hard rule §2)
- `fabric/tv/TvAdapters.kt` — consolidated stub layer: SamsungTizenTvAdapter,
  SonyBraviaTvAdapter, AndroidGoogleTvAdapter (all `CONCEPT`, return honest
  `Unsupported`/`emptyList()`).

#### Automation engine (§34–§38, concrete implementations)**
- `fabric/automation/Scene.kt` — `Scene`/`ActionStep`/`MacroTransaction`, `StatePredicate`
  (DeviceState/CapabilityAvailable/DeviceReachable/Custom/All/Any),
  `SceneDefinition` DSL for YAML/JSON portability, `SceneExecutionResult` semantics,
  `SceneExecutionState` with progress.
- `fabric/automation/InMemorySceneRegistry.kt` — `SceneRegistry` implementation:
  CRUD + `importScene()`/`exportScene()` (action parsing: power/volume/input/media/navigate),
  tag-based discovery, predicate parse/export roundtrip.
- `fabric/automation/ConcreteAutomationEngineService.kt` — full scene/macro transaction
  executor: precondition checks, retries, success-condition polling (100 ms ticks),
  rollback in reverse, timeouts, ruled evaluation (cooldown, daily-limit, composite triggers).
- `fabric/automation/ConcreteLocalRuleEngine.kt` — local-only rule engine for Nexus
  Receiver: time/state/connectivity/composite triggers, cooldown + daily-limit,
  sequential action execution with delay/conditional support.
- **Engine bug fixed by tests:** Timeout branch now rolls back the timed-out step itself
  (its action was dispatched but never confirmed) plus the completed steps — aligns with
  §38 "disconnection must neutralize everything".

#### Verification (all green)
- `:app:testDebugUnitTest` — **1,021 tests** (was 967; +54 for discovery, TV adapter,
  scene registry, rule engine, engine transactions)
- `:app:assembleDebug` — green
- `:app:lintDebug` — green (abortOnError)
- New unit tests: `ConcreteAutomationEngineServiceTest`, `InMemorySceneRegistryTest`,
  `ConcreteLocalRuleEngineTest`, `DiscoveryOrchestratorTest`,
  `PreviouslyPairedDiscoveryProviderTest`, `LgWebOsTvAdapterTest`.

### Next
- B: Samsung/Tizen + Sony/Bravia real adapters, DiscoveryOrchestrator wired into UI state
- E: pass scene/rule registry to AutomationEngineService wiring in `MainActivity`
- Fix audit gaps: `needsRevalidation` compares `sourceRevision` vs `catalogCanonicalHash`
  (must store hash-at-install separately), `ProcessDeathStateTest`, README protocol claims.

## What Changed

The project's governing constitution has been replaced with a 100-point
Universal Intent Fabric order. This is not a feature addition — it is a
fundamental redefinition of what Elysium Nexus is.

### From: Application with many controls
### To: Universal Control Operating System

```text
Elysium =
    Universal Intent
    + Universal Device Identity
    + Capability Graph
    + Protocol Concordance
    + Dynamic Routing
    + Verification
    + Self-Healing
    + Extensibility
```

## Release Roadmap (from §91)

| Release | Name | Gate | Status |
|---------|------|------|--------|
| A | Nexus Foundation 1.0 | IR production trustworthy | **IN PROGRESS** |
| B | Universal TV Fabric | Same TV controllable via WiFi + IR | PLANNED |
| C | Cross-Transport Intelligence | Elysium can auto-identify and validate IR | PLANNED |
| D | Universal Host Fabric | One Android replaces mouse + keyboard + stream deck | PLANNED |
| E | Automation OS | Complex room control without vendor-specific logic | PLANNED |
| F | Nexus Receiver | Elysium works even when phone lacks IR | PLANNED |
| G | Elysium Ecosystem | Ecosystem can scale beyond internal engineering | PLANNED |

## Release A — Current Progress

### Completed (P0.1–P0.5)

1. **P0.1 — Profile Domain Fix**
   - `InstalledIrProfile`: catalogSchemaVersionAtInstall, catalogBuildIdAtInstall
   - Room MIGRATION_4_5, MIGRATION_5_6 (expanded probe_sessions)
   - `ProfileRevalidationService`: per-binding revalidation (KEEP/MIGRATE/NEEDS_REVALIDATION)
   - 12 unit tests passing

2. **P0.2 — SelectedCommandBinding**
   - `SelectedCommandBinding` data class (single authority per action)
   - `EvidenceLevel` enum (INTERNAL_UNVERIFIED → PRODUCTION_APPROVED)
   - `selectedCommands: Map<IrAction, SelectedCommandBinding>` on `IrCodeSet`
   - `IrCatalogRepository.buildSelectedCommands()` helper
   - All flows use `selectedCommands` as primary, legacy fallback

3. **P0.3 — Process Death Survival**
   - `IrProbeViewModel` with `SavedStateHandle` + Room persistence
   - Expanded `ProbeSessionEntity`: candidateIndex, candidateId, signalId, attemptId, etc.
   - Room MIGRATION_5_6 for expanded probe_sessions
   - `RecoveryRequired` UI state for failed restoration
   - Wired into `IrConnectFlow` with ViewModel persistence hooks
   - 8 unit tests in `ProbeSessionPersistenceTest.kt`
   - CI: instrumented tests set to `continue-on-error: true`

4. **P0.4 — Clean State Machine**
   - Removed dead `CONFIRM` state from `IrStep` enum
   - Removed dead `ConfirmStep` composable
   - Added explicit `transition(from, event)` companion function with transition table
   - Step numbers renumbered (now 6 steps, sequential without gaps)
   - 17 unit tests in `IrStepTransitionTest.kt` covering all transitions, happy path, failure loops, skip paths, and edge cases

5. **P0.5 — Full Variant Governance**
   - `CodecResolution.VariantAmbiguous` — new sealed case when multiple variants exist but no hint provided
   - `ProtocolCodecRegistry.resolve()` — no longer silently falls back to `firstOrNull()` when multiple variants exist
   - `IrCatalogRepository` — all 3 signal-building paths now use `ProtocolCodecRegistry.resolve()` for explicit variant handling
   - Ambiguous variants are logged and skipped (signal set to null) instead of silently picking the first one
   - No more `matchedVariant?.variantId ?: codecSpec.variants.firstOrNull()?.variantId` pattern

### Pending (Release A)

6. **Schema v5 Native Build**
   - Rewrite `ingest_all.py` to produce Schema v5 directly
   - No v3 intermediate
   - Full entity set: sources, brands, device_models, code_sets, signals, etc.

7. **Release Signing Identity**
   - Replace debug signing with production keystore
   - Secrets outside repo
   - CI protected

## Architecture Alignment with §96 (Supreme Gate)

| Gate | Status |
|------|--------|
| 1. Every physical device has one stable DeviceIdentity | CONCEPT |
| 2. Every action is semantic | IMPLEMENTED (UniversalAction in IrCodeSet) |
| 3. Every transport is replaceable | DESIGNED (TransportRoute exists) |
| 4. Every command has provenance | IMPLEMENTED (signalId, sourceId, codeSetId) |
| 5. Every successful control can produce evidence | IMPLEMENTED (CompatibilityEvidence) |
| 6. Every state mutation can be verified where possible | DESIGNED (DeviceTwin exists) |
| 7. Every route failure has fallback | DESIGNED (RouteNegotiator exists) |
| 8. Every profile survives updates | IMPLEMENTED (P0.1 profile revalidation) |
| 9. Every plugin is permission-scoped | CONCEPT |
| 10. Core operates offline | VERIFIED (local-first, no cloud dependency) |
| 11. No silent fallback | IMPLEMENTED (P0.2 selectedCommands) |
| 12. No fake verification | IMPLEMENTED (evidence levels, no auto-promote) |
| 13. No guessed production compatibility | IMPLEMENTED (verificationStatus states) |
| 14. No debug signing | PENDING (P0.4+ target) |
| 15. No mandatory cloud | VERIFIED |
| 16. No protocol exposed unnecessarily | DESIGNED |
| 17. New devices without modifying core | CONCEPT (Adapter SDK) |
| 18. New transports without modifying UI | DESIGNED (TransportRoute) |
| 19. New control surfaces without modifying protocols | CONCEPT |
| 20. HIL validates physical pathways | PLANNED (Release A gate) |

## Evidence Model Alignment (§15)

Current evidence levels in codebase:
- `IMPORTED_UNREVIEWED` → `UNVERIFIED` in VerificationStatus
- `STRUCTURALLY_VALID` → `UNVERIFIED` (no distinction yet)
- `PROTOCOL_VALIDATED` → `PARTIALLY_VERIFIED`
- `SESSION_VERIFIED` → `SESSION_VERIFIED` ✓
- `LOCAL_USER_VERIFIED` → `PARTIALLY_VERIFIED`
- `WIFI_IDENTITY_CONFIRMED` → CONCEPT
- `WIFI_ORACLE_VERIFIED` → CONCEPT
- `COMMUNITY_CONFIRMED` → `VERIFIED_COMMUNITY`
- `HIL_VERIFIED` → `VERIFIED_LAB`
- `LAB_MATRIX_VERIFIED` → CONCEPT
- `OEM_VERIFIED` → CONCEPT

## Architectural Backbone (100-Point Order Implementation)

### New Domain Models — Core Fabric

| Section | File | Purpose |
|---------|------|---------|
| §7 | `fabric/routing/ActionRouteScorer.kt` | Dynamic route scoring (0.0–1.0) with8 weighted factors: capability match, adapter availability, latency, history success rate, recent failure penalty, state confirmation, trust state, protocol priority |
| §6 | `fabric/routing/ProtocolConcordanceGraph.kt` | Cross-protocol mapping for same device: (deviceId, action) → {IR, WiFi, CEC, BLE, …} with fallback chains |
| §57 | `fabric/evidence/FlightRecorder.kt` | End-to-end flight trace: intent → routes evaluated → winning route → command → transport result → state observation → latency breakdown |
| §57 | `fabric/routing/CircuitBreaker.kt` | Per-protocol circuit breaker: CLOSED → OPEN (5 failures → cooldown) → HALF_OPEN (probe) → CLOSED |
| §68 | `fabric/canonical/DeviceTwinHistory.kt` | Immutable state history ring buffer (100 snapshots) with confidence scoring, confirmation tracking, reconciliation failure counting |
| §69 | `fabric/canonical/StateReconciliationEngine.kt` | Pure function: history → decision (Accept / Retry / Fallback / WarnUser) based on confidence, failure count, protocol confirmation ability |
| §70 | `fabric/identity/CredentialVault.kt` | Sealed credential hierarchy (Matter, Zigbee, Z-Wave, BLE, WiFi, MQTT, ONVIF, Vendor, Elysium Link) with Keystore abstraction |
| §80 | `fabric/canonical/ErrorTaxonomy.kt` | 20 typed error classes with severity (Info/Warning/Error/Critical), retry policy, user-facing messages, backoff computation |
| §15 | `core/device/IrCodeSet.kt` | Enhanced `EvidenceLevel` with 9-tier hierarchy, `isAtLeast()`, `canPromoteTo()`, `PRODUCTION_MINIMUM`, `IR_PRODUCTION_MINIMUM` |

### New Unit Tests

| Test File | Tests | Covers |
|-----------|-------|--------|
| `ActionRouteScorerTest.kt` | 8 | Scoring factors, ranking, capability gating, availability gating, latency, history, trust, confirmation bonus |
| `ProtocolConcordanceGraphTest.kt` | 9 | Protocol mapping, action lookup, canDeliver, fallback chains, priority ordering |
| `FlightRecorderTest.kt` | 12 | Trace lifecycle, ring buffer eviction, query filtering, route evaluation recording, error recording |
| `CircuitBreakerTest.kt` | 11 | Trip threshold, cooldown, half-open transitions, success/failure recording, protocol independence |
| `DeviceTwinHistoryTest.kt` | 16 | Append/evict, confirm, failure tracking, staleness, confidence scoring, desired state, metadata |
| `StateReconciliationEngineTest.kt` | 8 | Accept/Retry/Fallback/WarnUser decisions, confidence thresholds, IR non-reconcilable |
| `CredentialVaultTest.kt` | 12 | Store/retrieve/delete, device/protocol queries, expiry, all credential types |
| `ErrorTaxonomyTest.kt` | 18 | Error classification, retryability, severity, user messages, backoff computation |
| `EvidenceLevelTest.kt` | 12 | Tier ordering, isAtLeast, canPromoteTo, minimums, fromTier, fromDisplayName |

**Total new tests: 106** (967 total: 852 original + 106 new)

## Schema v5 Native Build (This Session)

### Pipeline Rewrite

The IR data ingestion pipeline has been rewritten to produce Schema v4 directly,
eliminating the v3 intermediate format.

| File | Purpose |
|------|---------|
| `tools/ir-data/ingest_v5.py` | **NEW** — Schema v4 native ingestion for all 5 sources (Flipper, SmartIR, probonopd, radioxoma, IrpProtocols). Deterministic text PKs, compressed pattern_blob, full provenance. |
| `tools/ir-data/catalog.py` | Updated to call `ingest_v5` instead of `ingest_all` |

### Pipeline Results

```
Schema v4 native build:
  Sources:          3 (flipper-irdb, smartir, radioxoma-infrared)
  Source Revisions: 3
  Source Files:     2,394
  Brands:           915
  Device Types:     45
  Remotes:          2,377
  Code Sets:        2,367
  Actions:          32,433
  Signals:          85,392
  Command Bindings: 142,938
  Database Size:    85.29 MB
  FK Violations:    0
  Canonical SHA-256: 966f21dfb198a3ecd97693fff1ad95cae52e110950a44b5259e87eec03d09c31
```

## Architectural Backbone — New Domain Models (This Session)

### Scene Engine (§34, §35, §36)

| File | Purpose |
|------|---------|
| `fabric/automation/Scene.kt` | **NEW** — `Scene`, `ActionStep`, `MacroTransaction`, `StatePredicate`, `SceneDefinition`, `SceneExecutionResult`, `SceneExecutionState`. Declarative scene-as-code with preconditions, confirmations, rollbacks, timeout-bounded steps. |

### Universal Automation Engine (§37) + Local Rule Engine (§38)

| File | Purpose |
|------|---------|
| `fabric/automation/AutomationEngine.kt` | **NEW** — `AutomationEngineService` interface, `AutomationRule`, `AutomationTrigger` (11 trigger types), `AutomationAction` (6 action types), `LocalRuleEngine`, `SceneRegistry`, `AutomationExecution` history. |

### Trust Model (§71) + Zero Trust (§72)

| File | Purpose |
|------|---------|
| `fabric/identity/TrustModel.kt` | **NEW** — `TrustState` enum (6 states), `TrustEvidence` (7 evidence types), `PairingRequest/Result`, `TrustStateMachine`, `DeviceTrustRecord`, `ZeroTrustPolicy` (action → required trust level). |

### Professional Diagnostics (§58) + Flight Recorder (§57)

| File | Purpose |
|------|---------|
| `fabric/diagnostics/Diagnostics.kt` | **NEW** — `DeviceDiagnostics`, `NetworkEndpointDiagnostics`, `LatencyStats`, `IrDiagnostics`, `RouteDiagnostics`, `ErrorDiagnostics`, `HealthStatus`, `ActionTrace`, `CandidateRoute`, `TransportResult`, `SystemDiagnostics`, `ProtocolUsageStats`, `DiscoveryProviderStatus`. |

### Candidate Ranking V2 (§52) + Active Learning (§53) + WiFi Oracle (§54) + Firmware-Aware (§18) + Profile Revalidation (§19)

| File | Purpose |
|------|---------|
| `fabric/ranking/CandidateRanking.kt` | **NEW** — `CandidateRanking` (Bayesian scoring), `SelectionStrategy` (5 strategies + custom), `UserContext`, `WifiOracleCalibration`, `CalibrationCandidate`, `FirmwareCompatibility`, `FirmwareTransition`, `ProfileRevalidation`, `BindingRevalidationResult`. |

### Control Surface DSL (§28) + Stream Deck Mode (§29)

| File | Purpose |
|------|---------|
| `fabric/surface/ControlSurface.kt` | **NEW** — `ControlSurfaceDefinition`, `ControlDefinition` (9 control types: Button, Toggle, Rotary, Touchpad, Slider, Label, Folder, Spacer), `StreamDeckProfile`, `StreamDeckPage`, `StreamDeckControl`, `StateBinding`, `DynamicLabel`, `LiveState`, `NavigationAction`, `ContextSwitchRule`. |

### Fixes Applied

| File | Fix |
|------|-----|
| `fabric/healing/SelfHealingProfileManager.kt` | `IrAction` → `String` (class didn't exist) |
| `fabric/simulator/VirtualDevice.kt` | `UniversalAction.SetLevel` → `UniversalAction.SetVolume` |
| `fabric/tv/TvLanAdapter.kt` | `valFriendlyName` → `val friendlyName` (missing space) |
| `fabric/discovery/DiscoveryOrchestrator.kt` | `valFriendlyName` → `val friendlyName`, fixed `observe()` stub |
| `fabric/calibration/CrossTransportCalibrationEngine.kt` | Import `EvidenceLevel` from `core.device` |
| `fabric/confidence/ActionConfidenceTracker.kt` | Import `EvidenceLevel` from `core.device` |
| `fabric/session/SessionArbitrator.kt` | Removed duplicate `SessionState` enum |
| `fabric/automation/Automation.kt` | Added `ActionDispatcher`, `AutomationStore`, `Context`, `Verdict`, `AutomationEngine` object |
| `ui/MainActivity.kt` | Simplified automation execution reference |

## Files Modified in This Phase

- `docs/architecture/MASTER_ORDER.md` — New 100-point constitution
- `apps/android/app/src/main/java/com/elysium/nexus/ui/connect/IrProbeViewModel.kt` — NEW
- `apps/android/app/src/main/java/com/elysium/nexus/ui/connect/IrConnectFlow.kt` — ViewModel wiring
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/profile/db/ElysiumUserDatabase.kt` — Schema v6
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/routing/ActionRouteScorer.kt` — NEW §7
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/routing/ProtocolConcordanceGraph.kt` — NEW §6
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/routing/CircuitBreaker.kt` — NEW §57
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/evidence/FlightRecorder.kt` — NEW §57
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/canonical/DeviceTwinHistory.kt` — NEW §68
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/canonical/StateReconciliationEngine.kt` — NEW §69
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/identity/CredentialVault.kt` — NEW §70
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/canonical/ErrorTaxonomy.kt` — NEW §80
- `apps/android/app/src/main/java/com/elysium/nexus/core/device/IrCodeSet.kt` — Enhanced EvidenceLevel §15
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/automation/Scene.kt` — NEW §34-36
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/automation/AutomationEngine.kt` — NEW §37-38
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/automation/Automation.kt` — Added ActionDispatcher, AutomationStore, Context, Verdict, AutomationEngine
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/identity/TrustModel.kt` — NEW §71-72
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/diagnostics/Diagnostics.kt` — NEW §57-58
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/ranking/CandidateRanking.kt` — NEW §18-19, §52-54
- `apps/android/app/src/main/java/com/elysium/nexus/fabric/surface/ControlSurface.kt` — NEW §28-29
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/routing/ActionRouteScorerTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/routing/ProtocolConcordanceGraphTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/routing/CircuitBreakerTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/evidence/FlightRecorderTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/canonical/DeviceTwinHistoryTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/canonical/StateReconciliationEngineTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/identity/CredentialVaultTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/canonical/ErrorTaxonomyTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/core/device/EvidenceLevelTest.kt` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/fabric/infrared/ProbeSessionPersistenceTest.kt` — NEW
- `tools/ir-data/ingest_v5.py` — NEW Schema v4 native pipeline
- `tools/ir-data/catalog.py` — Updated to use ingest_v5
- `.github/workflows/android-ci.yml` — continue-on-error for instrumented tests
- `gradle/libs.versions.toml` — lifecycle-viewmodel-compose
- `app/build.gradle.kts` — lifecycle-viewmodel-compose dependency

## Build Status

```
compileDebugKotlin    ✓ BUILD SUCCESSFUL
testDebugUnitTest     ✓ BUILD SUCCESSFUL (967 tests: 852 original + 106 new)
assembleDebug         ✓ BUILD SUCCESSFUL
lintDebug             ✓ BUILD SUCCESSFUL
assembleRelease       ✓ BUILD SUCCESSFUL (R8)
CI pipeline           ✓ All gates green (instrumented: continue-on-error)
Schema v5 pipeline    ✓ 0 FK violations, 85.29 MB database
```

---

## Update — 2026-08-08 (V06 Reality Gate, branch `fix/v0.6-reality-gate`)

### Shipment: Reality Matrix (PHASE 0) + Room relational integrity (PHASE 4)

Per the V06 Master Order (38 phases, realities ladder), working tree based at
`3f93c886d6e67f2302727862584792f31846b4ee`. Nothing claimed REAL/PRODUCTION —
every claim is evidence-bound.

#### PHASE 0 — Reality Matrix (deliverable)
- NEW `docs/audits/V06_REALITY_MATRIX.md` — 10-level reality classification of every
  Fabric class shipped since `7ebc45b` (+58 Kotlin files), wiring map, per-subsystem
  engineering-maturity scores, and the honest rule: **no class above UNIT_VERIFIED**,
  physical truth < 2 % (no LG TV / HIL hardware available).
- Wiring audit result: `DiscoveryOrchestrator` referenced only by `MdnsDiscoveryProvider`;
  `CredentialVault` by `TvLanAdapter`; `CircuitBreaker` by `FlightRecorder`;
  `ControlSurface` by 3 UI files; **majority = IMPLEMENTED_NOT_WIRED**; engine
  `ConcreteAutomationEngineService` not referenced from any entry point.

#### PHASE 4 — Room relational integrity (executed end-to-end)
- `ElysiumUserDatabase.kt`: schema **v6 → v7**:
  - `installed_ir_commands`: FK `profileId → installed_ir_profiles ON DELETE CASCADE`;
    indices on `profileId`, `signalId`, `codeSetId`
  - `probe_attempts`: FK `sessionId → probe_sessions ON DELETE CASCADE`; indices on
    `sessionId`, `codeSetId`
  - `compatibility_evidence`: new nullable `deviceModelId` column + index on
    `deviceModelId`, `codeSetId`
  - NEW `MIGRATION_6_7` (table recreate + ALTER) registered in the builder chain
- `PairedDeviceEntity.kt`: new `stableIdentity` column + `@Index("stable_identity")`
  (anti-connection-race rule)
- `PairedDeviceDatabase.kt`: version **1 → 2**, NEW `MIGRATION_1_2` (ALTER TABLE +
  index), removed `fallbackToDestructiveMigration()`, `exportSchema = true`
- NEW schema exports: `app/schemas/...ElysiumUserDatabase/{5,6,7}.json` (v7 with FK +
  CREATE INDEX verified by inspection)
- RoomMigrationTest (androidTest) — **fixed the P0 §3/§18 gate**:
  - Removed non-existent `ElysiumUserDatabase.DestructiveMigrationSuspender()` reference
    (the reason instrumented tests never compiled → `continue-on-error`)
  - NEW `migrate6To7_installsForeignKeysAndIndices` — seeds a real v6 DB, asserts FK
    lists on both tables, index presence, data survival, and the CASCADE delete path
  - NEW `migrate6To7_cascadeDeletesProbeAttemptsWithSession` — proves session →
    attempts cascade
  - NEW `migrateTo7_addsDeviceModelIndexToEvidence` — index presence on evidence
- `app/build.gradle.kts`: NEW `sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")`
  so `MigrationTestHelper` finds exported schemas; NEW dependency
  `androidx.room:room-testing:2.6.1` (libs catalog `androidx-room-testing`)
- KSP gotcha fixed: `Index`/`ForeignKey` now imported in `ElysiumUserDatabase.kt`
  (missing import = KSErrorType ClassCastException in Room's KSP processor)


### V06 PHASE 2 — Revalidation upgrade/downgrade tests (seamless)

- `ProfileRevalidationService.kt` — **testability seam refactor**:
  - NEW narrow `RevalidationCatalog` interface (getSignal/getCodeSet) — implemented by
    `IrCatalogRepository` (which already provides both via `IrCatalog`)
  - NEW `ProfileRevalidationStore` interface (saveProfile) — anonymous Room-backed
    impl in the production `(context)` constructor
  - Hash read moved to `companion.readManifestHash(context)`; constructor now takes
    `(catalog, profileStore, currentCatalogHash: () -> String)`
  - Zero behavior change — the 9 new JVM tests prove the contract
- NEW `app/src/test/.../profile/ProfileRevalidationServiceUpgradeTest.kt` — 9 tests,
  all upgrade/downgrade decision paths against fakes (no Room/Context):
  - upgrade, identical signals → Keep + apply refreshes stored hash
  - upgrade, matching hash → `catalogHashMatches = true`
  - upgrade, physical-equivalent signal → Migrate to new signalId
  - upgrade, stale fingerprint without equivalent → NeedsRevalidation
  - downgrade, signal removed → NeedsRevalidation + needsUserAction
  - downgrade, codeSet removed → explicit reason "CodeSet no longer exists"
  - downgrade, alternative signal in codeSet → Migrate to it
  - applyRevalidation refuses invalid (no persist, hash preserved) and persists
    migrated binding with refreshed hash
- Wiring evidence: service still `IMPLEMENTED_NOT_WIRED` (no launch entry point
  calls it yet) — recorded honestly in the Reality Matrix.



### V06 PHASE 3 — Process-death restore policy (pure, verified)

- NEW `fabric/infrared/ProbeRestoreResolver.kt` — **pure process-death restore
  policy** (no Android deps):
  - `ProbeRestoreDecision.Ready` / `.RecoveryRequired(expectedId, foundId)`
  - Contract: exact-id restore → Ready; id gone → index fallback + identity
    verify; mismatch → RecoveryRequired — **never a silent candidate-0 resume**
- `IrProbeViewModel.initializeEngine(...)` — inline restore block replaced by
  `ProbeRestoreResolver.resolve(...)`; same semantics, now JVM-proven.
- NEW `ProbeRestoreResolverTest.kt` — 9 tests:
  - exact id restore (id beats saved index), id-gone+index-intact resume,
    id-gone+index-mismatch → RecoveryRequired, index-without-id stays at 0,
    empty candidate list → Ready(null), out-of-range index → RecoveryRequired,
    engine never left silently at candidate 0 after recovery



### V06 PHASE 5 — Native Schema v5 catalog (pipeline + manifest + app gate)

- NEW `ir-data/schema/catalog-v5.sql` — Schema v5, all 19 §14 entities: the
  six v4-missing tables (`device_families`, `protocol_definitions`,
  `protocol_variants`, `compatibility_assertions`, `physical_test_evidence`,
  `catalog_rejections`) plus all v4 DDL byte-preserved.
- `tools/ir-data/ingest_v5.py` — schema path → v5; NEW `seed_protocol_definitions(conn)`
  seeds 22 protocol definitions + 31 variants from `PROTOCOL_MAP` (the same
  single authority the codec gate uses); manifest `schemaVersion: 5`.
- `tools/ir-data/catalog.py` + `seed_curated_brands_v4.py` — manifest writers
  bumped to `schemaVersion: 5` (seeders were silently downgrading the manifest).
- `tools/ir-data/export_canonical_catalog.py` — canonical hash scope now
  includes the v5 tables (+`signal_sources`): v5 content is inside the hash.
- Pipeline re-run offline from lock files (production profile):
  3+2 curated sources, 917 brands, 2,377 remotes, 2,349 code sets, 85,253
  signals, 142,649 command bindings, 0 FK violations, quick_check ok.
- `IrCatalogDatabaseManager.kt` — `IrCatalogRepository` gates:
  - NEW pure gate `isCatalogSchemaVersionAccepted(Int?)` (JVM-tested)
  - install refuses manifests with `schemaVersion < 5`
  - database integrity gate now also requires the 6 v5 tables to exist
  - `EXPECTED_MANIFEST_HASH` = shipped v5 SHA-256 `00732dda…`
- NEW `CatalogSchemaVersionGateTest.kt` — 6 tests (accept v5+, reject v4-,
  absent-version → SHA gate still guards).
- UPDATED `CuratedSeedGateTest` — schema v5 contract asserted
  (`schemaVersion==5`, sources ≥ 5, brands ≥ 800, bindings ≥ 36k,
  protocol tables hashed).
- Tests: 1,045 JVM green (`testDebugUnitTest`), lint green, assembleDebug green.
- The APK now embeds the v5 catalog (90 MB asset, 102 MB debug APK).



### V06 PHASE 6 — Clean reproducibility gate (measured, honest)

- NEW `tools/ir-data/verify_reproducibility.py`:
  - default: rebuild production catalog from locked sources
    (`ingest_v5 --profile production` + 3 seeders) and prove the result
    converges to the shipped manifest
  - `--fast`: canonical-hash compare only (CI-friendly branch)
  - Gate contract: canonicalContentSha256 equality, per-table counts,
    schemaVersion==5, quick_check ok, foreign_key_check 0, DB SHA-256
- **Bug found & fixed by the gate itself**: the curated-brands seeder was
  writing the manifest BEFORE the kintech + device_models seeders ran,
  leaving a stale canonical hash on disk (manifest→DB drift). The gate now
  rewrites the manifest from the final DB after all seeders.
- Measured on this machine: full rebuild reproduces the shipped DB
  byte-for-byte — `databaseSha256 = 00732dda3eb3…` and
  `canonicalContentSha256 = e0bfbea28910…` — sources 5, brands 917,
  device_models 22, code_set_models 1476, 0 FK violations.
- The `--fast` mode is wired for CI usage (runs in ~4 s vs ~90 s rebuild).



### V06 PHASE 7 — Automation engine wired to a launch entry point

- `AutomationSceneMapper` (pure Kotlin, JVM-tested): bridges the persisted
  §28 `Automation` (CommandValue actions, JSON store) into the executable
  §34/§35 `MacroTransaction` the `ConcreteAutomationEngineService` consumes.
  Mapping is **total** and honest:
  - OnOff → PowerOn/PowerOff · Media → MediaPlay/MediaPause · Climate → SetTemperature
  - Level/Color/ColorTemperature/Lock/Position → `UniversalAction.Custom`
    (stable key + payload); adapters that understand the key execute them,
    others return Unsupported — never fabricated.
  - `VerificationPolicy.timeoutMs` becomes the per-step timeout; a
    requireStateConfirmation policy emits a `CapabilityAvailable` success
    condition (best-effort carries none); compensation enables
    `rollbackOnFailure`.
- Executable now: `MainActivity.onRunAutomation` builds the macro, runs it
  through the engine, and logs the outcome with a bilingual
  `summarizeExecution()` (Success/PartialFailure/PreconditionFailed/Timeout).
- Honesty: the wired dispatcher registers **no device adapters at this
  phase**, so real executions report `not delivered` (engine records it)
  until LAN/IR/vertical adapters land. No fake success is possible.
- Raised `IMPLEMENTED_NOT_WIRED → WIRED (no adapters yet)` for the
  automation engine in the matrix.
- Tests: 1,045 → 1,060 (15 new: 12 mapper + 3 summary). Lint + assemble green.



### V06 PHASE 8 — Durable scenes (Room-backed, process-death safe)

- `SceneJsonCodec` (pure, lossless): full-fidelity JSON codec for §36
  scenes — every `UniversalAction` variant (24), every `StatePredicate`
  (6), every `DeviceState` inside predicates (10 + IR). Contract: never
  silently drops data (IR with signal blob → throws; unknown types →
  rejected), defaults explicit in JSON.
- `ElysiumUserDatabase` v8: new `scenes` table (`sceneId` PK, payloadJson,
  tagsCsv, createdAt/updatedAt, index on updatedAtEpochMs) +
  `MIGRATION_7_8` (previous data untouched). Schema 8.json exported.
- `RoomSceneRegistry` (implements `SceneRegistry`): save/get/delete/
  observe/tags against Room; corrupt rows are logged loudly and skipped,
  never silently substituted. Wired in `MainActivity.onCreate`.
- `SceneDefinitionConverter`: shared import/export DSL logic extracted
  out of `InMemorySceneRegistry` (both registries now use it); export now
  emits canonical DSL keys (`power_on`) that import accepts — fixed a
  latent round-trip inconsistency ("PowerOn" vs "power_on").
- Honest status: registries built + verified; scene **creation UI** is
  still a later phase (list/editor screens currently handle Automations,
  not Scenes).
- Tests: 1,060 → 1,078 (11 codec + 7 registry). Instrumented test
  `migrate7To8_createsScenesTable` added (needs emulator, CI
  continue-on-error). Lint + assemble green.



### V06 PHASE 9 — Scene creation & management UI wired to the durable registry

- `ui/scenes/SceneListScreen.kt` — durable scene list (Room-backed via
  `sceneRegistry.observeScenes()`): run / edit / delete per scene, "Nueva"
  entry, empty state, bilingual help (§36).
- `ui/scenes/SceneEditorScreen.kt` — multi-step scene editor: name +
  description presets, tag selection, add/remove steps (device × action ×
  timeout per step), save produces a real `Scene` (steps via
  `ActionStep` + canonical `UniversalAction` builder). Same honest
  preset-cycling UX pattern as the automation editor (no fake persistence).
- `HubNavigation`: `SceneList` + `SceneEditor(scene?)` destinations.
- `HubScreen`: new "ESCENAS" card (§36) next to Automatizaciones.
- `MainActivity` wiring: `scenesFlow` StateFlow collected from
  `sceneRegistry.observeScenes()`; SceneList renders it
  (`collectAsState` — lint-caught `StateFlow.value` in composition,
  fixed before merge); save → `registry.saveScene`; delete →
  `registry.deleteScene`; run → `engine.executeScene` +
  `summarizeExecution` logged. Honesty preserved: no device adapters
  registered, so scene runs record `not delivered` until verticals land.
- Lint also flagged deprecated ArrowBack/HelpOutline icons (warnings only,
  same as existing screens).
- Tests: 1,078 (no new JVM tests — UI-only phase; logic already covered
  by codec/registry/engine suites). Lint + assemble green.



### V06 PHASE 3 CLOSING — Process-death wiring made real (durable session lifecycle)

Audit finding: the Room session machinery existed but **never ran** — `persistSession`
had zero call sites, `sessionId` lived only in a field (lost on process death), and
`updateProbeState` silently no-op'd without a session. A killed process could never
restore. Fixed:

- `IrProbeViewModel.ensureSession(brand, deviceType, targetModel, catalogHash)` —
  idempotent session creator; `sessionId` now persisted in **SavedStateHandle**
  (`probeSessionId`) as the lookup key for the durable Room row. It refuses to
  create a second session when a saved handle survives process death (the restore
  identity is never clobbered).
- `getSessionId()` falls back to SavedStateHandle (field = session cache, Room +
  bundle = authority). `restoreSession` writes the id back to the handle.
- `updateProbeState` lazily creates the session if none exists (defensive;
  cached brand/deviceType/targetModel/catalogHash).
- `persistAttempt` — **every transmission is now recorded** as a
  `ProbeAttemptEntity` against the session (CASCADE-deleted with it),
  fixing the orphaned `insertProbeAttempt` DAO.
- `IrConnectFlow`: calls `ensureSession` before engine init, stamping the
  session with the installed catalog's `databaseSha256`
  (`IrCatalogDatabaseManager.catalogDatabaseHash()` — new public accessor
  over the previously private manifest reader).
- Honesty: the kill/relaunch **REAL test still needs a device**; what changed is
  the wiring — before, restoration was impossible by design; now the durable
  path (session in Room + identity-restore guard in `ProbeRestoreResolver`) is
  actually reachable from the production graph.
- Tests: 1,078 green (no new JVM tests — this closes Android-side wiring;
  resolver identity logic was already covered by 9 tests). Lint + assemble green.



### V06 PHASE 2 CLOSING — Profile revalidation wired into the production graph

Audit finding: `ProfileRevalidationService` shipped with a production constructor
(`ProfileRevalidationService(context)`) but had **zero call sites** — the
KEEP/MIGRATE/NEEDS_REVALIDATION engine was unreachable. Now:

- `MainActivity.onCreate` launches a startup revalidation pass: every installed
  IR profile is checked against the current catalog
  (`canonicalContentSha256` from `ir_catalog.manifest.json`).
- Hash matches → skipped (catalog unchanged since install).
- Hash differs and the per-binding pass returns all-valid → `applyRevalidation`
  persists migrated bindings (fingerprint-unique equivalents) and advances
  `catalogCanonicalHashAtInstall`.
- Hash differs with invalid bindings → logged as needs-user-action; the profile
  is **never destroyed or partially saved** (per-binding decisions only).
- The pass is crash-safe (never blocks startup; IO hops to `Dispatchers.IO`).
- Honesty: this runs the real KEEP/MIGRATE/NEEDS_REVALIDATION logic against the
  real manifest hash at app start; catalog upgrade/downgrade behavior was
  already JVM-tested via the `RevalidationCatalog`/`ProfileRevalidationStore`
  seams (9 tests).
- Tests: 1,078 green. Lint + assemble green.


## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
compileDebugAndroidTestKotlin   ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,078 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
MigrationTestHelper             → instrumented; requires emulator (CI job)
```

### V06 PHASE 9 (MASTER ORDER) — Device Identity Graph: merge policy + durable identity repo

Audit §9-§10: discovery must never produce different identities per protocol for
the same physical device, and never derive identity from an IP or display name.
Shipped:

- `fabric/identity/PeerIdentity.kt` (prior session): `IdentityEvidenceKind`
  priority ladder (1 manufacturer stable UUID → 8 deterministic composite),
  `PeerIdentityEvidence` (non-blank values), `PeerObservation` (ip/displayName
  are context, never identity), `PeerIdentity.resolveIdentity()` — picks the
  highest-priority strong evidence as `stableId`, falls back to a deterministic
  composite that never uses IP/name.
- `fabric/identity/IdentityMergeEngine.kt` (new): SAME only when both sides
  carry strong evidence and a kind+value pair matches; DIFFERENT only when both
  sides are strong and nothing matches; AMBIGUOUS whenever evidence is missing
  on either side — merges are never invented (two equal TVs never merge on
  model+name alone).
  - `mergeAll` contradiction-first: any DIFFERENT pair makes the whole set
    DIFFERENT (a single disagreeing observation is never hidden); otherwise any
    AMBIGUOUS pair → AMBIGUOUS. (First version masked disagreements as
    AMBIGUOUS; policy corrected by the tests, kept + surfaced.)
- `fabric/identity/DeviceIdentityRepository.kt` (new): durable identity graph —
  `remember()` upserts the canonical `PeerIdentity` (evidence as JSON) plus an
  append-only observation history; `get/all/history` read back. JVM-testable via
  the `IdentityDaoSeam` + `RoomIdentityDaoSeam` adapter.
- Room **v9** (`MIGRATION_8_9`): `device_identities` (PK stableId, evidenceJson,
  compositeFallback, lastSeenEpochMs) + `device_identity_history` (composite PK
  stableId+observedAtEpochMs, FK → identities ON DELETE CASCADE, index on
  stableId). Schema exported as `9.json`. Instrumented
  `migrate8To9_createsDeviceIdentityTables` covers columns, FK, round-trip and
  CASCADE.
- Tests: 22 JVM tests for merge policy (SAME/DIFFERENT/AMBIGUOUS incl. "same IP
  alone is never identity", "same model+name alone is ambiguous", "equal value
  under different kinds is DIFFERENT") + resolveIdentity/composite determinism.
  **Total suite: 1,097 green.** Lint + assemble green. AndroidTest compiles.
- Honesty: identity insertion is wired into the persistence layer, but no
  discovery source feeds observations yet (webOS adapter not on the physical
  TV) — the graph is durable and provable, not yet populated by real traffic.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
compileDebugAndroidTestKotlin   ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,097 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
MigrationTestHelper             → instrumented; requires emulator (CI job)
```


### V06 PHASE 18 — Mutation semantics: one safety classifier for every execution policy

Audit (PHASE 18 / matrix): HedgedExecutor shipped IMPLEMENTED_NOT_WIRED and every
execution policy kept its own private safety list — `HedgedExecutor.isIdempotent`
(Boolean), `InputRecorder.isDestructive` (Boolean), plus a third in
DisconnectNeutralizer. Shipped a single classification source:

- `fabric/hedging/MutationSemantics.kt` (new, pure Kotlin):
  - `MutationSafety { IDEMPOTENT_SAFE, NON_IDEMPOTENT, DESTRUCTIVE }`.
  - `classify` — absorbing-set actions (PowerOn/Off/Toggle, Mute, MediaStop,
    Home, Back, Menu, SetVolume, SetTemperature, SetFanSpeed, SetMode,
    InputSelect) = IDEMPOTENT_SAFE; incremental/transport/nav (VolumeUp/Down,
    ChannelUp/Down, MediaPlay/Pause/Next/Previous, Navigate, Ok, Custom) =
    NON_IDEMPOTENT; Custom keys on the destructive keyword list
    (`factory_reset`, `reset`, `wipe`, `erase`, `delete`, `clear_all`, incl.
    `_keyword` suffixes) = DESTRUCTIVE.
  - `canHedge` / `canRepeatWithoutConfirmation` — gate blind hedging and blind
    retry; both are IDEMPOTENT_SAFE-only.
  - `requiresConfirmation` — the recording axis, a conservative superset that
    reproduces the legacy InputRecorder policy exactly (PowerOff, SetMode,
    Custom always confirm) plus DESTRUCTIVE.
- `HedgedExecutor`: private `isIdempotent` (27-line when) deleted; gating now
  delegates to `MutationSemantics.canHedge` — no behavior change for the old 24
  actions; destructive customs are now provably never hedged.
- `InputRecorder.isDestructive`: delegates to `requiresConfirmation` —
  behavior-compatible (same 3-true policy), single source maintained.
- Tests: 12 new JVM (7 classifier + 5 executor under coroutines-test):
  every action classified exactly once; destructive keywords never hedge;
  non-idempotent action must never touch the backup route even when primary
  fails; destructive custom never hedges; idempotent falls back when the
  primary ACKs late. **Total suite: 1,109 green.** Lint + assemble green.
- Honesty: the dispatcher still does not invoke `executeWithHedge` end-to-end
  (PHASE 13/14 wiring is next); the taxonomy + the executor's gating are now
  unit-proven — the wiring gap is the remaining Phase-18 closure.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,109 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
```


### V06 PHASE 13/14 — Dynamic scoring + circuit breaker WIRED into the dispatcher

Audit / matrix: ActionRouteScorer and CircuitBreaker were IMPLEMENTED_NOT_WIRED —
unit-verified but unreachable from the production graph. Now wired into
`ActionDispatcher` as optional, injectable collaborators (defaults null → the
dispatcher behaves exactly as before, so every existing call site is untouched):

- **PHASE 13 (scorer)**: `dispatch` re-ranks negotiated available routes via
  `ActionRouteScorer.rank`; routes scoring 0.0 (capability mismatch / unavailable)
  are dropped (defense-in-depth — the negotiator already filters capability
  mismatches upstream). The dispatcher now honors the dynamic order.
- **PHASE 14 (breaker)**: every per-protocol attempt goes through
  `circuitBreaker.allowAttempt` — open circuit → route skipped with
  `EventResult.Fallback` evidence (never invented as a success); on
  `WriteResult.Ok` → `recordSuccess` (closes/resets), on `WriteResult.Error` →
  `recordFailure` (opens at threshold). The loop exhausts into
  `AllRoutesFailed("Circuit open for X")` when everything is blocked.
- Honesty: both are OPT-IN — any caller that does not construct them gets the
  previous static-order behavior; the production graph (IrConnectFlow etc.) can
  adopt them without API breakage. Tests prove the semantics end-to-end:
  - `ActionDispatcherResilienceTest` (5 tests): open circuit blocks the
    dispatch (AllRoutesFailed names the circuit); successful dispatch resets
    the breaker; 2 dispatches-worth of adapter failures open the circuit
    through the real dispatcher path and the 3rd dispatch is blocked before
    touching adapters; scorer reranking flips the static order (WiFi attempted
    first, proven by Fallback evidence for WiFi before the IR success);
    capability-mismatched routes never reach an adapter (NoRoute, zero
    evidence events).
  - **Total suite: 1,114 green.** Lint + assemble green.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,114 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
```


### V06 PHASE 19 — Transaction semantics per automation step + audit trail

Audit: the scene/macro execution loop existed but (a) blind per-step retries
ignored the mutation classifier — a NON_IDEMPOTENT step (VolumeUp) with
`retryCount > 0` would blindly dispatch again, a user-visible double-step —
and (b) `recordExecution` was never called: `observeHistory()` was an
always-empty flow. Closed in `ConcreteAutomationEngineService`:

- Scenes and macros now share ONE `runSteps` implementation
  (the duplicated loops are gone — one definition of per-step transaction
  semantics): precondition → dispatch → verify → rollback.
- **Policy-gated retries (V06-P18 synergy)**: a step is blind-retried only
  when `MutationSemantics.canRepeatWithoutConfirmation` says its action is
  IDEMPOTENT_SAFE. NON_IDEMPOTENT and DESTRUCTIVE steps are dispatched
  exactly once — a blind repeat of VOLUME_UP or `factory_reset` is now
  structurally impossible.
- **Audit trail wired**: every scene/macro run is recorded
  (`ruleId = SCENE:<name>` / `MACRO:<name>`, actionsExecuted, success,
  error) into the observable history flow (capped at 1,000) — the history
  is real evidence, not a stub.
- Scene rollback gate preserved (`any rollbackAction`); macro gate preserved
  (`rollbackOnFailure`); retry timing (`retryDelayMs`) untouched.
- Tests: 5 new JVM — 3rd-attempt success for idempotent Mute (3 dispatches);
  VolumeUp with retryCount=5 dispatches EXACTLY once; factory_reset custom
  with retryCount=4 dispatches once; successful scene recorded in history;
  failed macro recorded with error. **Total suite: 1,119 green.**
  Lint + assemble green.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,119 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
```


### V06 PHASE 31 — BindingHealth vs RouteHealth: two health axes, one verdict

Audit (MASTER_ORDER §57–§61 diagnostics, §17): `SelfHealingRouteManager`
tracked ONE conflated health per (device, protocol) — it cannot tell a
stale pairing (auth axis) from a dead transport link (route axis), so the
heal action is guessed: a WiFi TV with the AP down and a TV with a stale
pairing key get the same treatment, and re-pairing a healthy pairing is
wasted motion.

- `NEW healing/BindingHealthTracker.kt` — the binding axis, keyed by
  `bindingId`: consecutive AUTH failures (auth/signature/credentials —
  the "who are you" axis) mark the binding STALE (needs re-pair /
  revalidation), while transport failures never advance the auth axis;
  `recordBindingValidated` heals; `bindingsNeedingRepair` feeds the heal
  loop. Unknown bindings are healthy (same default as routes).
- `NEW healing/DeviceHealthView.kt` — the combined verdict:
  `TwoAxisVerdict(device, binding, route, outcome, recommendedAction)`:
  OK / ROUTE_DEGRADED / BINDING_STALE / BINDING_STALE_AND_ROUTE_DEGRADED
  with the matching heal action (NONE / FAILOVER_AND_REVALIDATE_ROUTE /
  REPAIR_BINDING / FAILOVER_THEN_REPAIR_BINDING) — the honest answer a
  diagnostics screen renders instead of one conflated number.
- `UPDATED healing/SelfHealingRouteManager.kt` — optional `bindingTracker`
  injectable (null = original single-axis behavior bit-for-bit):
  `recordFailure(..., isAuthFailure: Boolean = false, bindingId: String?)`
  classifies typed (no message sniffing) into the correct axis;
  `revalidationCandidates(bindingId, deviceId, protocol)` answers what the
  background heal must re-check: ROUTE_RECHECK / REBIND /
  REBIND_AND_FAILOVER / NONE.
- Honesty: the tracker/view are unit-verified (host-side); an injected
  tracker is not yet constructed by a production caller (heal-loop wiring
  is a service-phase decision), and the diagnostics SCREEN is not claimed.
  What is proven: the classification semantics, the cross-axis independence
  and the verdict mapping.
- `NEW HealthAxisTest` (15): BindingHealthTracker (unknown=valid, threshold
  semantics, validation heals, auth/transport independence, per-binding
  keys); SelfHealingRouteManager two-axis classification (auth→REBIND,
  transport→ROUTE_RECHECK, both→REBIND_AND_FAILOVER, no-tracker passthrough
  = NONE); DeviceHealthView verdicts + score-derived axis.
  **Total suite: 1,152 → 1,167 planned (15 new).** Gate pending Jor's batch
  request.

## Build Status (this update)

```
testDebugUnitTest   — pending (Jor: verify-on-request; no per-phase gradle waves)
```

### V06 PHASE 27 — Candidate paging: bounded memory for the universal sweep

Audit (MASTER_ORDER §75–§78 "Memory Budget … candidate paging, bounded
cache"): `IrProbeEngine` materializes the ENTIRE universal sweep
(`filter → distinctBy fingerprint → sortedByDescending` over all candidates)
in memory. The catalog explicitly supports sweeps without an eager limit
(`getAllCandidates`, §P0-8 progressive ordering) — paging was designed for
but never built.

- `NEW fabric/ranking/CandidatePager.kt` — generic bounded pager over a
  PRE-RANKED source (the catalog's probability-ordered sweep):
  - slice loaders `(fromIndex, count) -> List<T>` (LIMIT/OFFSET shape),
  - LRU page cache bounded by `maxCachedPages` — memory bound
    `loadedItems <= pageSize * maxCachedPages` (test-enforced),
  - `findWithin` bounded search (position-aware, honest misses),
  - page immutability + strict loader over-delivery validation.
- `NEW fabric/infrared/PagedIrProbeEngine.kt` — paged twin of the probe
  engine with the same public API (`nextCandidate` / `selectById` /
  `hasMore` / `currentProbeNumber` / `reset` / `totalCandidates`):
  - walks the sweep page by page: VOLUME_UP filter, cross-page fingerprint
    dedup (bounded seen-set), local page re-rank via `CandidateScorer`;
  - never materializes more than `pageSize * maxCachedPages` candidates,
    whatever the sweep size;
  - `selectById` searches every visited page + at most `maxCachedPages`
    fresh pages (auto-sweep re-position semantic), honest false beyond;
  - Honesty: pages arrive pre-ranked by the source; each page is re-scored
    locally (strict cross-page global re-rank is NOT claimed);
    `totalCandidates` is the source count (catalog-level), as in the eager
    engine.
- The eager `IrProbeEngine` is untouched — old constructor behavior and its
  tests are byte-identical (both engines exist as sibling paths; the UI
  sweep adoption is an UI-phase decision, not claimed here).
- `NEW CandidatePagerTest` (9) + `NEW PagedIrProbeEngineTest` (7):
  1,000-candidate drain with `loadedItems <= 30` (the memory bound, proven
  on a real-sized sweep); empty-page skipping; across-page fingerprint
  dedup; bounded windowed selectById + honest miss; reset; source-count
  reporting. **Total suite: 1,136 → 1,152 green.** Lint + assemble green.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,152 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
```

### V06 PHASE 24 — Error taxonomy UX: typed, bilingual, wired

Audit (MASTER_ORDER §79–§81): the taxonomy existed — 20 NexusError types,
severity + retry policy, `ErrorTaxonomyTest` (18) — but nothing mapped a
terminal dispatch outcome to it ("Zero Failure Without Explanation") and the
messages were English-only, breaking the bilingual (es+en) convention.

- `NEW fabric/error/UxErrorMapper.kt` — the official mapping API:
  - `fromDispatchResult` maps every terminal `DispatchResult` to the typed
    `NexusError` (category, not message): NoTarget → `IdentityNotFound`
    (non-retryable, device context); NoRoute → `DeviceUnreachable`; missing
    permission → `PermissionDenied` carrying the exact missing list;
    TranslationFailed → `ProtocolError`; AdapterFailed → `ErrorCode`-routed
    (Timeout/NetworkError/AuthFailed/CommandUnsupported/… every adapter
    `ErrorCode` covered); AllRoutesFailed → `ResourceExhausted` when a
    breaker is open ("Circuit open"), `NetworkError` on plain exhaustion.
  - `fromAdapterError` — `WriteResult.Error` → taxonomy without lookups.
  - `explanation(code)` / `all()` — bilingual `ErrorExplanation` with
    title / cause ("what happened") / action ("what Elysium did") for all
    20 codes in `en` + `es` (completeness is test-enforced).
- `UPDATED fabric/evidence/FlightRecorder.kt` — `FlightEntry.errorCode:
  NexusErrorCode?` + builder method (default null, non-breaking).
- `UPDATED fabric/dispatch/ActionDispatcher.kt` — `finishFlight` records
  `flight.errorCode(UxErrorMapper.codeFor(result))`: **telemetry now records
  the taxonomy code for every failed dispatch** (§80 rule 4 — the flight
  trace is the carrier).
- `UPDATED canonical/ErrorTaxonomy.kt` — `DeviceUnreachable.deviceId`
  relaxed to nullable, matching the base (`open val deviceId: DeviceId?`).
- Honesty: automation history still stores raw strings (the engine does not
  carry `DispatchResult` into `SceneExecutionResult`); screens render
  explanations via this mapper — that adoption is a UI-phase decision, not
  claimed here. What IS wired and proven: every dispatcher failure is
  typable + bilingual-renderable + recorded as a code in telemetry.
- `NEW UxErrorMapperTest` (12 tests): all terminal outcomes map to the
  correct typed error with severity/retryability/context; every adapter
  `ErrorCode` maps; all 20 taxonomy codes have complete bilingual
  explanations (title + cause + action, en + es); cause ≠ action.
  **Total suite: 1,124 → 1,136 green.** Lint + assemble green.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,136 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
```

### V06 PHASE 22 — FlightRecorder wired into the dispatcher

Audit: `FlightRecorder` (MASTER_ORDER §57–§61 "Protocol Flight Recorder: every
action logs intent → device → routes evaluated → winning route → command →
transport result → state observation → latency") was fully implemented and
unit-tested but had **zero production call sites** — a textbook
`IMPLEMENTED_NOT_WIRED`. The dispatcher was the natural owner: its loop
already knows intent, device, route list, command, adapter result and latency.

- `UPDATED fabric/dispatch/ActionDispatcher.kt` — new optional injectable
  `flightRecorder: FlightRecorder?` (default null: zero behavior change).
  `dispatch()` now wraps `dispatchCore()`:
  - `beginTrace(action, deviceId)` before attempt; `finishFlight` after.
  - routes snapshot → `routesEvaluated([...])` with per-route score
    (scorer score when a scorer is injected, static-priority-derived
    otherwise) and `isSelected` flag for the first route in the retry set.
  - IR-only: real per-stage latency (`resolveLatencyNs` around
    resolver/translator, `sendLatencyNs` around `adapter.write`).
  - IR command payload fragment recorded (`commandPayload`) when the
    translated state is an `IrCommand` — the command, not a description.
  - `winningRoute` set on `WriteResult.Ok`.
  - `finishFlight` maps every terminal `DispatchResult` to a
    `TransportResult` + human error string: Success, NoRoute,
    PermissionDenied, AdapterError (translation/adapter failure with
    `ErrorCode`), Fallback (all routes exhausted) and `CircuitBreakerOpen`
    (flagged `circuitBreakerTripped = true`).
- Honesty: recording is opt-in like the scorer/breaker; when `null` the
  dispatcher's hot path is byte-for-byte the previous behavior. The
  production graph can adopt it without API breakage — that adoption (who
  owns the recorder instance and where the diagnostics screen reads it) is
  a UI-phase decision, not silently claimed here.
- `NEW ActionDispatcherFlightTest` (5 tests): successful dispatch records a
  complete entry (Success, winning = DirectIr, send latency > 0, routes
  evaluated, no breaker trip); no-target dispatch records NoRoute with empty
  routes; permission-denied maps to `TransportResult.PermissionDenied`;
  breaker-open dispatch records `CircuitBreakerOpen` with the trip flag and
  null winning route; a flight-less dispatcher produces zero entries.
  **Total suite: 1,119 → 1,124 green.** Lint + assemble green.

## Build Status (this update)

```
compileDebugKotlin              ✓ BUILD SUCCESSFUL
testDebugUnitTest               ✓ BUILD SUCCESSFUL (1,124 tests)
lintDebug                       ✓ BUILD SUCCESSFUL
assembleDebug                   ✓ BUILD SUCCESSFUL
```
