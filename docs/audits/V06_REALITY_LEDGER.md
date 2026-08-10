# V06 REALITY LEDGER — Elysium Nexus Universal Control OS

> Order: MASTER ORDER v0.6 — PHYSICAL TRUTH / DEVICE IDENTITY / PRODUCTION GATE
> Branch: `fix/v0.6-physical-truth-and-identity-gate`
> Baseline: main `3f93c88` (unchanged — matches the external audit's audited SHA)
> Rule (§1): every relevant module gets EXACTLY ONE classification. Escalating
> a level by inference is forbidden. Evidence is a commit / test artifact /
> catalog build / device record — never prose.
>
> Ladder: DESIGNED → IMPLEMENTED → UNIT_VERIFIED → INTEGRATION_VERIFIED →
> ON_DEVICE_VERIFIED → REAL_DEVICE_VERIFIED → HIL_VERIFIED →
> DEVICE_MATRIX_VERIFIED → PRODUCTION_APPROVED

This ledger supersedes the prose classifications in older changelogs whenever
they disagree. The audit's §86 classifications were made against `main`; rows
marked ⚡ reflect state already earned on this branch (commits referenced).

---

## 1. Stop-the-line P0 positions (nothing merges until resolved)

| Position | Ledger today | Required before merge (§69) | Working phase |
|---|---|---|---|
| Catalog installer | ⚡ IMPLEMENTED (branch) — **hardcoded hash conflicts with manifest authority; no per-build layout** | UNIT_VERIFIED, fresh-install/tamper/kill instrumented gates | PTG-01 (this phase) |
| Catalog eligibility | ⚡ IMPLEMENTED (logic) — **structural risk: INTERNAL_UNVERIFIED candidates excluded by runtime query** | Eligible POWER/VOLUME_UP/VOLUME_DOWN/MUTE > 0 proven by SQL gate | PTG-02 |
| Production signing | IMPLEMENTED (debug-signed release) | **demo-only until production key exists** (§65, §69) | PTG-14 |
| Paired-device storage | IMPLEMENTED — **plaintext clientKey/pairingToken in Room** | Android Keystore vault, plaintext removed | PTG-07 |
| LG webOS vertical | DESIGNED/IMPLEMENTED SKELETON — HTTP POST framed as WebSocket; claim downgraded | REAL transport or honest SKELETON label (§32–§39) | PTG-09 |

---

## 2. Module ledger (one classification each)

| Module | Classification | Evidence | Gaps (what would raise the rung) |
|---|---|---|---|
| UniversalAction / domain model | UNIT_VERIFIED | domain tests (suite §1) | — |
| IR signal model (Encoded: codecId+variantId, params) | UNIT_VERIFIED | codec golden vectors in suite | RAW identity += canonical timing hash (PTG-04 §12) |
| IrProtocol codecs (NEC/NECx/Samsung/SIRC/RC5/RC6/Kaseikyo) | UNIT_VERIFIED | codec tests | SIRC variant fail-closed (PTG-05 §15/21), RC5/6/Kaseikyo remain EXPERIMENTAL until independent decoder + HIL (§16) |
| ProtocolCodecRegistry (resolve) | UNIT_VERIFIED | registry tests | no first-variant fallback (PTG-05 §20) |
| IrProbeEngine (eager, same-codeSet invariant) | UNIT_VERIFIED | probe tests incl. fingerprint | fingerprint MUST add codecId/variantId (§23, PTG-05) |
| PagedIrProbeEngine (bounded windows) | UNIT_VERIFIED | pager tests | — |
| CandidatePager | UNIT_VERIFIED | pager tests | — |
| Profile probe session durability (Room fields) | UNIT_VERIFIED | entity/migration tests | **NOT EARNED**: real process-death recovery via latest-active-session DAO (PTG-06 §32/§20) |
| Catalog installer | ⚡ IMPLEMENTED | branch commit `2f1855d`+ | PTG-01 completes UNIT_VERIFIED + instrumented gates |
| IR catalog schema | ⚡ INTEGRATION_VERIFIED (schema v5 assets in branch) | manifest schemaVersion=5, `catalog-rejections`/`signal_sources` tables present, `verify_reproducibility` | True native Schema V5 builder (PTG-03 §4) |
| Catalog eligibility policy | IMPLEMENTED (queries exist) | — | **SQL CI gate** (§3, PTG-02); candidates must be >0 |
| Data ingestion (ingest_v5) | ⚡ IMPLEMENTED | manifest stats (2,367 code sets; 85,392 signals) | unknown protocol → REJECT (not 38 kHz default), rejection collector with real counts (PTG-02 §7) |
| Evidence ontology (verification_status) | IMPLEMENTED | — | unit-status inflation mapper removed; per-binding evidence levels (§12, §19; PTG-05) |
| Canonical hash / provenance export | ⚡ IMPLEMENTED | canonical hash per manifest | MUST include `signal_sources` + protocol definitions + eligibility (§13, PTG-03) |
| Catalog build identity | ⚡ DESIGNED | this ledger | `catalogBuildId` in manifest + stats agreement (PTG-01 §10) |
| Content-addressed IDs (signalId/bindingId/codeSetId) | IMPLEMENTED (short hashes) | — | full-SHA-256 content-addressable identities (§6/§10–§12, PTG-04) |
| Profile save durability | IMPLEMENTED — **can report Saved on Room failure** | — | suspend transaction → COMMIT → cache → Saved; StorageFailure on failure (PTG-06 §17/§33/§34) |
| Profile metadata truth | IMPLEMENTED — **schemaVersion=5 hardcode + buildId "unknown" risk** | — | read CatalogMetadata (PTG-01/§18 — partially this phase) |
| Profile revalidation | UNIT_VERIFIED | revalidation tests | per-binding migration only on exact/unique fingerprint; no action-name equivalence (§35/§36, PTG-06) |
| IdentityMergeEngine (policy) | ⚡ UNIT_VERIFIED | `IdentityMergeEngineTest` (19) | wired to real discovery observations (PTG-08 §27–§29) |
| Room user DB (profiles) | ⚡ INTEGRATION_VERIFIED | exportSchema=true, migrations, instrumented migrate tests | FKs `command.profileId`/`attempt.sessionId` CASCADE + indexes (§78, PTG-06) |
| PairedDeviceDatabase | IMPLEMENTED — **destructive migration fallback** | — | explicit migrations + exportSchema + tests (PTG-07 §24/§37) |
| Android Keystore credential vault | **NOT IMPLEMENTED** (interface + in-memory only) | `CredentialVault.kt` | AES-GCM vault + plaintext removal (PTG-07 §38/§39/§25) |
| Trust model | IMPLEMENTED — **two ontologies** (`DeviceTwin` vs `fabric.identity.TrustModel`) | — | single state machine + projections (PTG-08 §26/§40) |
| LAN Discovery providers (SSDP/mDNS/paid) | ⚡ UNIT_VERIFIED | provider/orchestrator tests | USN→upnpUdn wiring, TXT parsing, stable identity feeding (PTG-08 §28/§29/§42/§43) |
| DefaultDiscoveryMerger | UNIT_VERIFIED | merger tests | never IP+hostname as identity; weak-evidence ⇒ no auto-merge (§41/§44) |
| Discovery capabilities | UNIT_VERIFIED | merger tests | UNKNOWN not fabricated OnOff (PTG-08 §31/§46) |
| Discovery Orchestrator | UNIT_VERIFIED | orchestrator tests | concurrent providers + continuous stream (PTG-08 §30/§45) |
| LgWebOsTvAdapter | DESIGNED / IMPLEMENTED SKELETON (HTTP ↔ SSAP gap) | offline unit tests only | real WebSocket SSAP state machine or honest downgrade (§32; PTG-09) |
| LG WoL | NOT IMPLEMENTED end-to-end (`getMacAddress() = null`) | — | MAC persistence + magic packet + recovery poll (PTG-09 §38/§50) |
| LG identity | IMPLEMENTED — `lg_webos_<IP>` | — | stable identity from UDN/serial/MAC (PTG-09 §33/§49) |
| LG state subscription | NOT IMPLEMENTED (`subscribable=true` is a claim) | — | real subscription path (§37/§51) |
| LG command semantics | IMPLEMENTED — **volume fallback 50, Toggle→Off, uniform D-pad** | — | typed semantics, StateUnavailable (PTG-09 §36/§52–§54) |
| CircuitBreaker | ⚡ UNIT_VERIFIED (wired in dispatcher) | breaker tests | scope key = deviceId+bindingId+action; atomic half-open; monotonic clock (PTG-10 §42/§61–§63) |
| ActionRouteScorer | ⚡ UNIT_VERIFIED (wired in dispatcher) | scorer tests | per-device/per-binding evidence; RouteCapability per action (PTG-10 §41/§64/§65) |
| ProtocolConcordanceGraph | IMPLEMENTED (in-memory) | — | persisted store with adapter version/evidence/latency (PTG-10 §43/§66) |
| CrossTransportCalibrationEngine (WiFi Oracle) | IMPLEMENTED (algorithm) | — | A-B-A trial, CALIBRATION_SAFE whitelist, maxRetries enforced, durable trials (PTG-11 §45–§47/§57–§60) |
| RouteChainer (IR→WiFi) | IMPLEMENTED | — | — |
| ActionDispatcher (flight + errorCode + mutation-gated) | ⚡ INTEGRATION_VERIFIED | dispatcher+flight tests (1,124 run green) | automation consumes typed result (PTG-12 §48/§70) |
| FlightRecorder | ⚡ INTEGRATION_VERIFIED (wired) | flight tests | — |
| KpiHarness (§1 metrics) | ⚡ UNIT_VERIFIED (written) | KpiHarnessTest (8, unrun) | batch gate (Jor's order) |
| Automation engine (scenes/macros/rules) | ⚡ UNIT_VERIFIED | engine tests (1,119 suite) | durable AutomationRun/counters; recordExecution in every run (PTG-12 §50/§71/§73) |
| MutationSemantics policy | ⚡ UNIT_VERIFIED | semantics tests | per-action declarations for reversible compensation (§49/§69) |
| Adapter SDK | IMPLEMENTED (schema/domain) | — | runtime: loader/verifier/permission broker/sandbox (PTG-13 §52/§53/§74/§75) |
| HostAgent | IMPLEMENTED (domain contract) | — | semantic typed commands before shell primitive (§54/§76) |
| AppContextEngine | IMPLEMENTED (in-memory) | — | capability/route intersection surface (§55/§77) |
| CI fast/full split | ⚡ IMPLEMENTED (branch) | `ci-fast.yml` + `android-ci.yml` | eligibility SQL gate, forbidden-fallback scanner, integration gate REQUIRED for RC (§3/§58–§60) |
| Claims audit | ⚡ UNIT_VERIFIED (tool) | `tools/claims-audit/audit.sh` PASS | — |
| Devices-under-test matrix | ⚡ DOCUMENTED (yaml+validator) | `device-matrix/validate.py` | physical stratification results (PTG-15 §63) |
| HIL | NOT EARNED (no bench: no receiver, no MCU, no LG TV) | — | §61/§62/§84 |
| Physical device matrix | NOT EARNED | — | §63/§85 |
| Production signing / release factory | NOT DONE (debug-signed release) | — | §65/§81/§82/§83, PTG-14 |
| Nexus Receiver | DESIGNED (spec only) | — | §68 — after core truth |

---

## 3. Honest global statement

96+ JVM tests ran green at the last gate (1,124; lint+assemble green) on the
previous branch; since then PHASES 31/33/34/35/37 added ~34 tests and
tooling that await Jor's verification batch. **Zero modules are above
INTEGRATION_VERIFIED**: no physical device, no HIL bench, no production
signing key exist. Nothing on this branch will claim otherwise.

## 4. Phase map (order §N)

| Phase | Order sections | Deliverable |
|---|---|---|
| PTG-01 | §1, §2, §10, §18(partial), §31(partial) | Reality ledger; catalog installer rebuild (manifest sole authority, builds/<buildId> layout, atomic promotion, rollback); buildId in manifest+stats |
| PTG-02 | §3, §7, §8 | Eligibility SQL gates; strict ingestion (unknown protocol/carrier REJECT); RejectionCollector; provenance fail-closed |
| PTG-03 | §4, §5, §9, §13, §11, §15 | Native Schema V5 builder; one authoritative pipeline; canonical hash covers signal_sources; reproducible factory A/B; seeders out of production path |
| PTG-04 | §6, §10–§12 | Content-addressed full-SHA256 identities; RAW canonical timing hash (zlib-independent) |
| PTG-05 | §12, §14–§16, §19–§23 | Evidence ontology; variant fail-closed; SIRC12/15/20 explicit + golden vectors; fingerprint += codecId/variantId |
| PTG-06 | §17–§20, §22, §23, §32–§36, §78 | suspend saveProfile durability; latest-active-session recovery; per-binding revalidation; Room FKs/indexes; signal_evidence rename |
| PTG-07 | §24, §25, §37–§39 | PairedDeviceDatabase migrations; Android Keystore vault; plaintext removal |
| PTG-08 | §26–§31, §40–§44, §46 | Unified trust; identity engine wired to SSDP/mDNS; orchestrator v2; NO capability fabrication |
| PTG-09 | §32–§39, §47–§55 | LG vertical rebuild (transport, identity, vault refs, semantics, subscription, WoL) |
| PTG-10 | §41–§44, §61–§66 | Route-scoped evidence; CircuitBreaker v2; concordance store; scorer v2 |
| PTG-11 | §45–§47, §57–§60 | Causal calibration trials (A-B-A); CALIBRATION_SAFE; durable evidence |
| PTG-12 | §48–§51, §56, §70–§73 | Automation result model; durable automation; scene hardening; recordExecution for every run |
| PTG-13 | §52–§55, §74–§77 | Adapter SDK runtime; scoped capabilities; host fabric semantic commands; contextual surface v2 |
| PTG-14 | §58–§60, §65, §66, §81–§83 | CI full: forbidden-fallback scanner, data factory A/B, Android integration REQUIRED, signing+vendor manifest |
| PTG-15 | §61–§64, §67, §84–§85 | HIL rig spec + physical matrix results once hardware lands |
| PTG-16 | §69, §70 | Branch exit criteria review; no tag/release until Physical Truth Gate |

*Updated:* 2026-08-09 — PTG-01 in progress.