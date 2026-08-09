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
  1,097-test suite).
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
| `Scene.kt` (Scene/ActionStep/MacroTransaction/StatePredicate/SceneDefinition DSL) | **UNIT_VERIFIED** | used by engine + registry tests | — | — |
| `InMemorySceneRegistry` | **UNIT_VERIFIED** | `InMemorySceneRegistryTest` (8 tests: CRUD, import/export roundtrip, tags) | shared `SceneDefinitionConverter` | in-memory only (tests/DSL) |
| `RoomSceneRegistry` + `SceneDao` + `SceneJsonCodec` | **UNIT_VERIFIED** | `SceneJsonCodecTest` (11), `RoomSceneRegistryTest` (7 with FakeSceneDao), `migrate7To8_createsScenesTable` (instrumented) | **durable via Room v8 (PHASE 8/9)**: UI wired (list/editor/run/delete) | real device UX not exercised |
| `ConcreteAutomationEngineService` | **UNIT_VERIFIED** | `ConcreteAutomationEngineServiceTest` (9 tests) | `executeScene`/`executeMacro`/`evaluateRules` | **Timeout-rollback bug fixed by test**; wired to scene Run button (PHASE 9), dispatcher honest (no adapters → not delivered) |
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
| `IrProbeViewModel` | **UNIT_VERIFIED** (wired) | `ProbeSessionPersistenceTest`, `IrStepTransitionTest` | PHASE 3 closing: `ensureSession` + `sessionId` in SavedStateHandle + attempts persisted + catalog-hash stamping; restore path reachable via identity-guarded `ProbeRestoreResolver` | kill+resume REAL proof still pending device |

### Profile / revalidation (PHASE 2)

| Class | Status | Evidence | Notes | Gaps |
|---|---|---|---|---|---|
| `ProfileRevalidationService` | **UNIT_VERIFIED** (wired) | `ProfileRevalidationTest` (9: KEEP/MIGRATE/NEEDS_REVALIDATION via seams) | PHASE 2 closing: startup revalidation pass wired in MainActivity — real manifest hash, applies migrations atomically, never destroys profiles | per-binding UI surfacing pending |
| `InstalledIrProfile` (fields) | UNIT_VERIFIED | tests assert `catalogCanonicalHashAtInstall` etc. | correct | binding-level `bindingId` gaps |

### V06-P9 Device Identity Graph (Identity Merge Policy + Durable Graph)

| Class | Status | Evidence | Wiring | Gaps |
|---|---|---|---|---|
| `IdentityMergeEngine` | **UNIT_VERIFIED** | `IdentityMergeEngineTest` (19: SAME/DIFFERENT/AMBIGUOUS, IP-null, kind-mismatch, contradiction-first mergeAll) | Used by `resolveIdentity` sites (future discovery merge) | no real discovery feeds observations yet |
| `PeerIdentity` / `PeerObservation` / `IdentityEvidenceKind` | **UNIT_VERIFIED** | exercised via engine + resolve tests; `PeerIdentity.composite` determinism asserted | composite fallback rooted in `Fingerprint.ofHex` (SHA-256) | — |
| `DeviceIdentityRepository` | IMPLEMENTED (persistence) | JSON round-trip exercised in engine tests; Room schema v9 + instrumented `migrate8To9` | `RoomIdentityDaoSeam` over `InstalledProfileDao` | no call site in production graph yet (repository instantiable, not invoked) |
| Room v9 (`device_identities`, `device_identity_history`) | **UNIT_VERIFIED** (schema) + instrumented | schema `9.json` exported; CASCADE FK + index in migration test | `MIGRATION_8_9` registered | instrumented test requires emulator |

### V06-P18 Mutation Semantics (single execution-policy classifier)

| Class | Status | Evidence | Wiring | Gaps |
|---|---|---|---|---|
| `MutationSemantics` | **UNIT_VERIFIED** | `MutationSemanticsTest` (7: all 25 actions classified once; destructive customs never hedge/repeat; legacy recording axis reproduced) | consumed by `HedgedExecutor` (gating) + `InputRecorder` (recording guard) | dispatcher does not invoke `executeWithHedge` end-to-end yet (PHASE 13/14) |
| `HedgedExecutor` | **UNIT_VERIFIED** | `HedgedExecutorTest` (5: non-idempotent/destructive never touch backup; late-ACK fallback; no-backup passthrough) | gating via `MutationSemantics.canHedge` | unwired upstream |

### V06-P13/14 Dispatcher wiring (scorer + breaker)

| Class | Status | Evidence | Wiring | Gaps |
|---|---|---|---|---|
| `ActionDispatcher` (scorer branch) | **INTEGRATION_VERIFIED (JVM)** | `ActionDispatcherResilienceTest` (5) | optional `routeScorer` injected → rank reranked, score-0 dropped | opt-in; production callers not yet constructing it |
| `ActionDispatcher` (breaker branch) | **INTEGRATION_VERIFIED (JVM)** | circuit open → skip+Fallback evidence → AllRoutesFailed("Circuit open"); success resets; failures open via real dispatch path | optional `circuitBreaker` injected; Ok→recordSuccess, Error→recordFailure | opt-in; cooldown/half-open E2E needs a device |

### V06-P19 Transaction semantics per automation step

| Class | Status | Evidence | Wiring | Gaps |
|---|---|---|---|---|
| `ConcreteAutomationEngineService.runSteps` | **INTEGRATION_VERIFIED (JVM)** | `ConcreteAutomationEngineServiceTest` (5 new: idempotent retries to 3rd attempt; VolumeUp retryCount=5 → exactly 1 dispatch; factory_reset → 1 dispatch; history recorded for scene; error recorded for failed macro) | single shared scene/macro path; `MutationSemantics`-gated retries; `recordExecution` wired | E2E on device still pending (physical) |

### V06-P22 FlightRecorder wired (full §57–§61 trace per dispatch)

| Class | Status | Evidence | Wiring | Gaps |
|---|---|---|---|---|
| `FlightRecorder` + `FlightBuilder` + `FlightEntry` | **UNIT_VERIFIED** | `FlightRecorderTest` (ring buffer, query, avg latency, failure rate) | **WIRED into `ActionDispatcher.dispatch`** — trace per attempt: routes evaluated (+scores +isSelected), resolve/send latency (IR), command payload (IR), winning route, TransportResult, error string, breaker-trip flag | recorder instance ownership/screen = UI-phase decision (not claimed); on-device latency proof pending device |
| `ActionDispatcher` (flight branch) | **INTEGRATION_VERIFIED (JVM)** | `ActionDispatcherFlightTest` (5: complete entry on success; NoRoute on no-target; PermissionDenied; CircuitBreakerOpen + trip flag; zero entries when no recorder) | optional `flightRecorder` injectable (default null → prior hot path untouched) | opt-in like scorer/breaker; production callers not yet constructing it |

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
HedgedExecutor, SelfHealing*, AppContextEngine, HostAgent.

**Rescued from `IMPLEMENTED_NOT_WIRED` by V06 work:** ActionRouteScorer + CircuitBreaker
(P13/14), FlightRecorder (P22), HedgedExecutor gating (P18). Outstanding list above
is the honest remainder.

---

## 4. Summary scores (engineering maturity, physical evidence not required)

| Subsistema | Score | Evidence |
|---|---|---|
| IR authoritative path | ~82 % | 1,097 tests incl. codec golden vectors; physicalSha verified per binding |
| LAN discovery | ~60 % | providers unit-verified; not on-device; no IPv6/dup-announce probe |
| TV adapter LG | ~35 % | honest unit tests; **zero physical** |
| Automation transactions | ~70 % | engine/registry unit-verified; durable via Room v8 (`scenes`) + full scene UI wired (PHASE 9); **PHASE 19: per-step transaction semantics unified, retries policy-gated, audit trail alive (1,119 tests)** |
| Identity/vault/trust | ~48 % | vault unit-verified; **identity graph pure-logic verified (merge policy + composite determinism, 19 tests), durable via Room v9 (schema exported + instrumented migration); peer-fingerprint enforcement still missing (§11)** |
| Routing/resilience | ~62 % | scorer/breaker **wired into the dispatcher** (optional injectables, integration-tested: open circuit blocks, success resets, failures open, rerank honored); single MutationSemantics classifier unit-proven; **FlightRecorder wired — full §57–§61 trace per dispatch, 5 more tests (1,124)** |
| Calibration (WiFi Oracle) | ~20 % | class exists; no E2E (§7, §16) |
| Pi_Release/CI | ~55 % | 3-gate CI: JVM + lint + assemble; **instrumented test = continue-on-error** (P0 §3,§18) |
| Physical truth | **<2 %** | nothing on hardware |

**Rule honored:** nothing above is claimed `REAL_DEVICE_VERIFIED` or `PRODUCTION_APPROVED`.
Physical gates pending: LG TV, HIL receiver, device matrix.

---

*Next:* V06-P9, P18, P13/14, P19 and P22 complete — 1,124 JVM green, lint + assemble green.
Then: PHASE 24 (error taxonomy UX) and the remaining software phases; physical-only
phases (8, 10, 16, 17-E2E, 25, 26, 32, 36, 38) remain blocked on hardware and are
documented, not claimed.