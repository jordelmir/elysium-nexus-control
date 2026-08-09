# V06 REALITY MATRIX — Elysium Nexus Universal Control OS

> **Order:** MASTER ORDER v0.6 — REALITY GATE / PHYSICAL TRUTH / PRODUCTION TRACK
> **Baseline:** `3f93c886d6e67f2302727862584792f31846b4ee` (origin/main)
> **Branch:** `fix/v0.6-reality-gate`
> **Date:** 2026-08-09
> **Rule applied:** *No afirmar HIL/REAL/PRODUCTION sin evidencia correspondiente.*

## Reality ladder

```text
CONCEPT
DESIGNED
IMPLEMENTED
UNIT_VERIFIED
INTEGRATION_VERIFIED
ON_DEVICE_VERIFIED
REAL_DEVICE_VERIFIED
HIL_VERIFIED
DEVICE_MATRIX_VERIFIED
PRODUCTION_APPROVED
```

- `IMPLEMENTED_NOT_WIRED` = compiles + has logic, but does **not** participate in the
  production graph (no UI / service / call-site reaches it).
- A class is `UNIT_VERIFIED` only if a JVM test exercises its behavior (counted in the
  1,021-test suite).
- No class in this matrix is above `UNIT_VERIFIED` / `INTEGRATION_VERIFIED` (local),
  because **no physical device was tested** (no LG TV, no HIL, no device matrix run).

---

## 1. Classes added since `7ebc45b` (da87e3e + 3f93c88)

### Discovery Fabric

| Class | Status | Evidence | Wiring | Gaps |
|---|---|---|---|---|
| `DiscoveryOrchestrator` | **UNIT_VERIFIED** | `DiscoveryOrchestratorTest` (24 tests: dedup, provider isolation, merger, fallbacks) | Referenced by `MdnsDiscoveryProvider` (as discovery entry) | Not invoked from UI/service; `observe()` not exercised by tests |
| `MdnsDiscoveryProvider` | IMPLEMENTED_NOT_WIRED | — | Referenced by none outside itself | No test instantiation (NsdManager dependency); not started from UI |
| `SsdpDiscoveryProvider` | **UNIT_VERIFIED** | header/brand parsing covered indirectly via `DiscoveryOrchestratorTest` | Not started from UI | DatagramSocket path not tested |
| `PreviouslyPairedDiscoveryProvider` | **UNIT_VERIFIED** | `PreviouslyPairedDiscoveryProviderTest` (9 tests: brand/protocol/capability mapping, staleness, DAO failure) | Room `PairedDeviceDao` | Not started from UI |

### TV Fabric

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|
| `LgWebOsTvAdapter` | **UNIT_VERIFIED** (logic) | `LgWebOsTvAdapterTest` (8 tests: offline paths, capability metadata, honest unsupported) | No live TV | `REAL_DEVICE_VERIFIED` **NOT achieved**; SSAP transport unproven against a TV |
| `TvLanAdapter` + `TvBrand`/records | DESIGNED | — | Contract interface | — |
| `TvAdapters` (Samsung/Sony/Google stubs) | CONCEPT | honest empty/unsupported | — | intentionally deferred (§27: one adapter at a time) |

### Automation / Scenes / Rules

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|
| `Scene.kt` (Scene/ActionStep/MacroTransaction/StatePredicate/SceneDefinition DSL) | **UNIT_VERIFIED** | used by engine + registry tests | — | no Room persistence |
| `InMemorySceneRegistry` | **UNIT_VERIFIED** | `InMemorySceneRegistryTest` (8 tests: CRUD, import/export roundtrip, tags) | In-memory only; no Room | durable scenes = PHASE 20 (pending) |
| `ConcreteAutomationEngineService` | **UNIT_VERIFIED** | `ConcreteAutomationEngineServiceTest` (9 tests) | `executeScene`/`executeMacro`/`evaluateRules` | **Timeout-rollback bug fixed by test** (`rollbackSteps(completed + step)`); wire into service layer = pending |
| `ConcreteLocalRuleEngine` | **UNIT_VERIFIED** | `ConcreteLocalRuleEngineTest` (14 tests: cooldown, daily limit, triggers, dispatch) | For Nexus Receiver | no persistence of rules |
| `UniversalActionDispatcher` (fun interface) | DESIGNED | — | separates engine from transport | — |
| `StateProvider` (interface) | DESIGNED | — | contract for device state | — |

### Routing / resilience / evidence

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|
| `ActionRouteScorer` | **UNIT_VERIFIED** | `ActionRouteScorerTest` | | not wired to dispatcher |
| `CircuitBreaker` | **UNIT_VERIFIED** | `CircuitBreakerTest` | CLOSED/OPEN/HALF_OPEN | not wired |
| `ProtocolConcordanceGraph` | **UNIT_VERIFIED** | `ProtocolConcordanceGraphTest` | | no persistence |
| `HedgedExecutor` | IMPLEMENTED_NOT_WIRED | — | no safety classification of actions (PHASE 18) | |
| `SelfHealingRouteManager` | IMPLEMENTED_NOT_WIRED | — | | |
| `SelfHealingProfileManager` | IMPLEMENTED_NOT_WIRED | — | | |
| `FlightRecorder` | **UNIT_VERIFIED** | `FlightRecorderTest` | local only, bounded retention | not wired to dispatcher |

### Identity / trust / vault

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|
| `CredentialVault` | **UNIT_VERIFIED** | `CredentialVaultTest` | AndroidKeystore-backed (code paths unit-tested) | referenced by `TvLanAdapter`; no on-device proof |
| `TrustModel` | IMPLEMENTED_NOT_WIRED | — | | peer-identity-change enforcement = PHASE 11 |
| `DeviceIdentity`/`DeviceTwinHistory` | **UNIT_VERIFIED** | `DeviceTwinHistoryTest` | | |
| `StateReconciliationEngine` | **UNIT_VERIFIED** | `StateReconciliationEngineTest` | | |

### Cross-transport / host / SDK / UI

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|
| `CrossTransportCalibrationEngine` | IMPLEMENTED_NOT_WIRED | — | the "WiFi Oracle" — no real TV run | requires LG vertical (PHASE 10) |
| `ControlSurface` | IMPLEMENTED_NOT_WIRED | — | referenced by `MacControlSurfaceScreen` | no device-driven capability intersection |
| `AppContextEngine` | IMPLEMENTED_NOT_WIRED | — | | |
| `HostAgent` | CONCEPT/DESIGNED | — | macOS stub + Android placeholder | |
| `AdapterSdk` | DESIGNED | — | manifest/packaging not started | |
| `PerformanceBudgets` | DESIGNED (targets only) | — | no MEASURED claims | |
| `Diagnostics` | DESIGNED | — | UI presence | no device wiring |
| `IrProbeViewModel` | **UNIT_VERIFIED** (wired) | `ProbeSessionPersistenceTest`, `IrStepTransitionTest` | wired into `IrConnectFlow.kt` | process-death relaunch (kill+resume) not proven on device |

### Profile / revalidation (PHASE 2)

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|
| `ProfileRevalidationService` | **IMPLEMENTED but NOT WIRED** | `ProfileRevalidationTest` | Correct hash comparison (`catalogCanonicalHashAtInstall`) already implemented; KEEP/MIGRATE/NEEDS_REVALIDATION logic present | Not reachable from app graph; upgrade/downgrade tests missing; applyRevalidation not used |
| `InstalledIrProfile` (fields) | UNIT_VERIFIED | tests assert `catalogCanonicalHashAtInstall` etc. | correct | binding-level `bindingId` gaps |

---

## 3. Wiring map (PHASE 1 input)

```
UI (IrConnectFlow, MacTransport, MacControlSurfaceScreen)
├── IrProbeViewModel      → Room probe_sessions/attempts (wired ✓)
├── ControlSurface        → MacControlSurfaceScreen (wired ✓)
├── CredentialVault       → TvLanAdapter (partial ✓)
└── DiscoveryOrchestrator → MdnsProvider (self-reference only ✗ upstream UI)
```

**`IMPLEMENTED_NOT_WIRED` (the "ornamental architecture" the order forbids):**
MdnsDiscoveryProvider ssdp PreviouslyPaired provider (providers complete but orchestrator
untouched from Android entry), LgWebOsTvAdapter, CrossTransportCalibrationEngine,
ActionRouteScorer (real scorer unused), CircuitBreaker (only FlightRecorder side debt),
HedgedExecutor, SelfHealing*, FlightRecorder hooks, AppContextEngine, HostAgent.

---

## 4. Summary scores (engineering maturity, physical evidence not required)

| Subsistema | Score | Evidence |
|---|---|---|
| IR authoritative path | ~82 % | 1,021 tests incl. codec golden vectors; physicalSha verified per binding |
| LAN discovery | ~60 % | providers unit-verified; not on-device; no IPv6/dup-announce probe |
| TV adapter LG | ~35 % | honest unit tests; **zero physical** |
| Automation transactions | ~55 % | engine/registry unit-verified; no Room persistence (§20) |
| Identity/vault/trust | ~45 % | vault unit-verified; peer-fingerprint enforcement missing (§11) |
| Routing/resilience | ~40 % | scorer/breaker unit-verified but unwired |
| Calibration (WiFi Oracle) | ~20 % | class exists; no E2E (§7, §16) |
| Pi_Release/CI | ~55 % | 3-gate CI: JVM + lint + assemble; **instrumented test = continue-on-error** (P0 §3,§18) |
| Physical truth | **<2 %** | nothing on hardware |

**Rule honored:** nothing above is claimed `REAL_DEVICE_VERIFIED` or `PRODUCTION_APPROVED`.
Physical gates pending: LG TV, HIL receiver, device matrix.

---

*Next:* PHASE 2 ✅ + PHASE 3 ✅ + PHASE 4 ✅ + PHASE 5 ✅ (v5 catalog pipeline, 1,045 JVM green).
Next: PHASE 6 — clean reproducibility gate (rebuild v5 from lock files, byte-identical DB).
(upgrade/downgrade catalog), then PHASE 3 process-death closing, then PHASE 5 native catalog v5.