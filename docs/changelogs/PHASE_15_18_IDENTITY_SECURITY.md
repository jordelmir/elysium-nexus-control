# PHASE_15_18_IDENTITY_SECURITY — PR 4: Identity & Security

**Status:** COMPLETE  
**Branch:** `fix/v0.6.2-truth-convergence`  
**Baseline:** `2378f0d7a822ba20e7922d1cb48a4e978542cbc3`  
**Build:** `assembleDebug` GREEN, `testDebugUnitTest` GREEN (1186 tests, 3 pre-existing unrelated failures)  
**Device:** Honor Magic V2 (VER_N49) — app launches, UI renders, all categories visible

## What shipped

### Phase 15 — Credential Vault (Room + Android Keystore)
- `AndroidKeystoreCredentialVault` (`CredentialVault.kt`) — AES-256-GCM encryption via Android Keystore; `encrypt()`/`decrypt()` with IV prepended to ciphertext
- `CredentialVaultStore` interface + `RoomCredentialVaultStore` adapter (`CredentialVaultStore.kt`)
- `CredentialVaultEntity` Room entity + `CredentialVaultDao` DAO
- `PairedDeviceDatabase` v2→v3 migration (`MIGRATION_2_3`): creates `credential_vault` table, scrubs plaintext `pairing_token`/`client_key` from `paired_devices`
- `ElysiumUserDatabase` v9→v10 migration for new probe session columns

### Phase 16 — Zero-Trust Policy (DeviceTrustRecord + TrustDao)
- `DeviceTrustRecordEntity` + `TrustDao` — Room persistence for device trust state transitions
- `TrustAuditLogEntity` + `TrustAuditDao` — append-only audit trail for all trust decisions
- `TrustRepository` — load/save/upgrade with transition validation (§72)
- `ZeroTrustPolicy` wired into `ActionDispatcher` — optional `trustResolver` + `trustAuditor` params; deny + audit on insufficient trust

### Phase 17 — Identity Engine Wired to Discovery
- `DiscoveryIdentityBridge` (`DiscoveryIdentityBridge.kt`) — converts `RawDiscoveryRecord` → `PeerObservation` with proper `PeerIdentityEvidence` extraction
- Bridge feeds observations through `IdentityMergeEngine` for cross-protocol dedup (§9: never produce different identities per protocol for the same device)
- `DiscoveryOrchestrator` accepts optional `identityBridge` param; processes batches after merge
- Structured logging (`DiscoveryIdentityBridge` TAG) for ADB observability
- Identity evidence priority: serial → UDN → MAC → Matter node → BT address → public key fingerprint

### Phase 18 — HedgedExecutor + Mac Secure Channel Adapter
- `PrimarySuccess` bug fixed: result now captured from primary job (was `Unit as T`, lost actual value)
- Extra closing brace removed from `HedgedExecutor.kt`
- `ActionDispatcher` accepts optional `hedgedExecutor` param; §61 hedged execution wired into `dispatchCore` for idempotent actions with multiple routes
- `MacSecureChannelAdapter` (`MacSecureChannelAdapter.kt`) — bridges `MacTransport` to `ControllerTransport` interface; state mapping for all `MacConnectionState` variants

## Files changed

| File | Change |
|------|--------|
| `DiscoveryIdentityBridge.kt` | **NEW** — RawDiscoveryRecord → PeerObservation bridge |
| `DiscoveryOrchestrator.kt` | `identityBridge` optional param; batch processing after merge |
| `MacSecureChannelAdapter.kt` | **NEW** — MacTransport → ControllerTransport adapter |
| `HedgedExecutor.kt` | PrimarySuccess result capture fix; extra brace removal |
| `ActionDispatcher.kt` | `hedgedExecutor` param; §61 hedged write in dispatchCore |
| `DeviceAdapter.kt` | (unchanged — ErrorCode import used by hedging) |
| `PeerIdentity.kt` | (unchanged — resolveIdentity extension used by bridge) |
| `CredentialVault.kt` | `AndroidKeystoreCredentialVault` — AES-256-GCM |
| `CredentialVaultStore.kt` | Room entity + DAO + adapter |
| `PairedDeviceDatabase.kt` | v3 migration; credential_vault table |
| `ElysiumUserDatabase.kt` | v10 migration; probe session columns |
| `TrustRepository.kt` | Trust persistence + audit log |
| `ZeroTrustPolicy.kt` | Trust authorization gate |

## Verification evidence

```
./gradlew :app:compileDebugKotlin        → BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest         → 1186 tests, 3 pre-existing failures
./gradlew :app:assembleDebug             → BUILD SUCCESSFUL
adb install -r app-debug.apk             → Success
adb shell am start -n ...ui.MainActivity → App launches, UI renders all categories
```

Device test on Honor Magic V2 (VER_N49):
- App launches without crashes
- UI renders: MAC/PC, USB-C CABLADO, UNIVERSAL REMOTE, CONTROLES DE TV, Mis Controles, AUTOMATIZACIONES
- Status: "SIN DISPOSITIVOS" (expected — no Mac Agent paired)
- No FATAL exceptions in logcat
