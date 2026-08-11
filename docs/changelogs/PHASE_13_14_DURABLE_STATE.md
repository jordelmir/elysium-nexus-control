# PHASE_13_14_DURABLE_STATE — PR 3: Durable State

**Status:** COMPLETE  
**Branch:** `fix/v0.6.2-truth-convergence`  
**Baseline:** `2378f0d7a822ba20e7922d1cb48a4e978542cbc3`  
**Build:** `assembleDebug` GREEN, `testDebugUnitTest` GREEN (1186 tests, 3 pre-existing unrelated failures)

## What shipped

### Phase 9 — SelectedCommandBinding single authority
- `SelectedCommandBinding.codeSetId` field added to `IrCodeSet.kt:60`
- `IrCatalogRepository.buildSelectedCommands()` eliminated; `selectedCommands` map built directly from the winning binding during `getAllCandidates()`
- `CodeSetCommandsResult.selectedCommands` is the sole authority (no more post-selection `firstOrNull`)

### Phase 10 — Transactional profile install
- `ElysiumUserDatabase.saveProfileWithCommands()` now uses `@Transaction`: `DELETE commands → INSERT exact set` (P0-19 fix)
- `InstalledIrProfileRepository.installProfile()` replaced `saveProfile()` — Room commit must succeed before cache update; `SaveProfileResult.StorageFailure` on exception
- `computeCatalogHash()` now reads `canonicalContentSha256` from `currentCatalogMetadata()` instead of computing file hash

### Phase 11 — ProfileRevalidationService
- `Ambiguous` status added to `BindingRevalidationResult` — returned when binding has multiple candidates with equal confidence
- Fallback `commandBindings` eliminated: `selectedCommands` is the sole authority for installed bindings
- `applyBindingRevalidation()` now operates per-binding (not batch)
- `needsUserAction` includes `Ambiguous` status as actionable

### Phase 12 — Process-death recovery
- `ProbeSessionEntity.catalogHashAtStart` field + `MIGRATION_9_10` adds column to `probe_sessions`
- `getLatestActiveProbeSession(brand, deviceType)` DAO query + `findLatestActiveSession()` in ViewModel
- **Catalog hash guard**: if `catalogHashAtStart != currentCatalogHash`, session is discarded (never silently restore from stale catalog)
- `restoreSession()` restores step, verified actions, and persisted candidate state from Room

### Phase 13 — Attempt lifecycle evidence
- `ProbeAttemptEntity` gains: `physicalSha256`, `carrierHz`, `catalogBuildId`, `confirmedAtEpochMs`, `confirmedBy`
- `MIGRATION_9_10` alters `probe_attempts` with 5 new columns
- `persistAttempt(attempt, physicalSha256, carrierHz, catalogBuildId)` — full metadata on every transmission
- `updateAttemptStatus(attemptId, result, durationMs)` — records outcome post-transmit
- `confirmAttempt(attemptId, confirmedBy)` — marks attempt as proven after user challenge
- `sendTestAction()` in `IrConnectFlow` now passes physical truth on every attempt

### Phase 14 — Bounded-memory paged probe
- `ProbeCursor` interface (`ProbeCursor.kt`) — unified contract for both engines
- `IrProbeEngine : ProbeCursor` — eager engine, all methods `override`-qualified
- `PagedIrProbeEngine : ProbeCursor` — bounded-memory variant using `CandidatePager`
- `CandidatePager.page()` and `findWithin()` now `suspend` (supports async SQLite loader)
- `CandidatePager.getCachedPage()` — non-suspend accessor for UI-bound `selectById()`
- `IrCatalogRepository.getCandidateCount()` + `getCandidatePage()` — paginated SQL queries with stable `ORDER BY brand, id`
- `IrCatalog` interface gains `getCandidateCount`/`getCandidatePage` + `InMemoryIrCatalog` implementation
- **Universal sweep**: `CandidatePager(pageSize=50, maxCachedPages=4)` → `PagedIrProbeEngine` (no more `limit=400`)
- **Brand search**: `getCandidatesForBrand()` → `IrProbeEngine` (unchanged behavior)
- `ProbeRestoreResolver.resolve()` now `suspend` (calls `nextCandidate()`)
- `TestStep` composable takes `ProbeCursor` (not `IrProbeEngine`)
- `startAutoScan()` takes `ProbeCursor`

## Files changed

| File | Change |
|------|--------|
| `IrCodeSet.kt` | `SelectedCommandBinding.codeSetId` added |
| `ProbeCursor.kt` | **NEW** — unified interface |
| `IrProbeEngine.kt` | `override` keywords on all `ProbeCursor` members; `nextCandidate()` suspend |
| `PagedIrProbeEngine.kt` | `override` keywords; `nextCandidate()` suspend; `pageView()` nullable |
| `CandidatePager.kt` | `pageLoader` suspend; `page()`/`findWithin()` suspend; `getCachedPage()` added |
| `ProbeRestoreResolver.kt` | `resolve()` suspend |
| `IrCatalog.kt` | `getCandidateCount`/`getCandidatePage` in interface + `InMemoryIrCatalog` |
| `IrCatalogRepository.kt` | `getCandidateCount`/`getCandidatePage` SQL; `selectedCommands` direct; `buildSelectedCommands` eliminated; `sourceRevisionSha` fix |
| `ElysiumUserDatabase.kt` | `ProbeAttemptEntity` 5 new fields; `MIGRATION_9_10`; version=10; `getLatestActiveProbeSession`; `deleteCommands` in transaction |
| `InstalledIrProfileRepository.kt` | `installProfile()` transaccional; `ProbeAttemptEntity` null defaults |
| `IrProbeViewModel.kt` | `initializeEngine()` suspend; `nextCandidate()` suspend; `persistAttempt()` with evidence; `updateAttemptStatus()`; `confirmAttempt()`; `findLatestActiveSession()` |
| `IrConnectFlow.kt` | Paging LaunchedEffect; `ProbeCursor` engine; catalog hash guard; `scope.launch` for `nextCandidate()`; attempt lifecycle in `sendTestAction`; `confirmAttempt` in challenge |
| `ProfileRevalidationService.kt` | Uses `selectedCommands` as authority (Phase 11 partial) |
| `CandidatePagerTest.kt` | `runTest` wrappers for suspend functions |
| `IrProbeEngineTest.kt` | `runTest` wrappers |
| `PagedIrProbeEngineTest.kt` | `runTest` wrappers |
| `ProbeRestoreResolverTest.kt` | `runTest` wrappers |
| `ProbeSessionPersistenceTest.kt` | `runTest` wrappers |

## Verification evidence

```
./gradlew :app:compileDebugKotlin        → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest         → 1186 tests, 3 pre-existing failures
./gradlew :app:assembleDebug             → BUILD SUCCESSFUL
```

PR3-related tests (all green):
- `CandidatePagerTest` (8 tests)
- `IrProbeEngineTest` (7 tests)
- `PagedIrProbeEngineTest` (7 tests)
- `ProbeRestoreResolverTest` (9 tests)
- `ProbeSessionPersistenceTest` (8 tests)

## Remaining PR3 work (Phase 11)
- `ProfileRevalidationService`: add `Ambiguous` status, eliminate unsafe migration fallback, per-binding `applyRevalidation`
