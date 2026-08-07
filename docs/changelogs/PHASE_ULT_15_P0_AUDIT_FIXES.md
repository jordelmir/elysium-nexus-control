# PHASE ULT.15 — P0 Audit Fixes (Post-Universal-Sweep Hardening)

> Commit: TBD (13 files, +279/-99 lines)
> Date: 2026-08-07
> Gates: 780 JVM tests ✅ · assembleDebug ✅ · lintDebug ✅ (0 errors)

## What shipped

Complete resolution of all 11 P0 blockers identified in the `b9d198a` audit.

## P0-1: Template-seeded signals removed from production sweep

**Files:** `tools/ir-data/seed_templates_v4.py`

Template-derived signals (Sankey ×4, Kintech, Kalley, etc.) are protocol-knowledge guesses, not HIL-verified. Changed `production_approved=1 → 0` so they no longer appear in the universal sweep until physical capture proves they work on real hardware. The DB still carries them as INTERNAL_UNVERIFIED for development use.

## P0-2: Silent NEC fallback eliminated in curated brands seeder

**Files:** `tools/ir-data/seed_curated_brands_v4.py`

Removed `DEFAULT_PARSER = parse_nec_32` and the fallback `PARSERS.get(proto_orig, DEFAULT_PARSER)`. Unknown protocols (RC5, RC6, Kaseikyo, etc.) are now explicitly rejected with a warning message instead of silently converting to NEC. Prevents phantom code sets that decode as NEC when the real protocol is different.

## P0-3: Auto-sweep challenge confirmation

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/ui/connect/IrConnectFlow.kt`

Added `ChallengeConfirmation` state and `ChallengeStep` composable. When the user taps "¡Funcionó! Detener barrido" during auto-sweep, the system now:
1. Re-transmits VOLUME_UP for the candidate (challenge)
2. Asks "¿La TV reaccionó?" with transmit result visible
3. Only proceeds to VERIFY_SECONDARY on explicit confirmation
4. Falls back to next candidate on rejection

Prevents wrong candidate acceptance from timing race between3.5s delay and user reaction.

## P0-4: Room verifiedActions derived from successCount

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/fabric/profile/InstalledIrProfileRepository.kt`

Changed `verifiedActions = commandsMap.keys` → `verifiedActions = commandEntities.filter { it.successCount > 0 }.mapNotNull { IrAction.valueOf(it.actionKey) }.toSet()`. Previously ALL command keys were marked as verified on profile reconstruction, even if the user never confirmed them.

## P0-5: IR device discovery uses installed profiles, not DeviceCatalog

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/infrared/InfraredAdapter.kt`

`scan()` now discovers installed IR profiles from Room via `InstalledIrProfileRepository.getAllProfilesSuspend()` and creates device twins with `deviceId = "ir-${profile.id}"` (UUID). Previously used `DeviceCatalog.all` with template IDs (`ir-tv-sankey-generic`) which don't match profile UUIDs, breaking `DeviceCommandResolver.resolve()`.

## P0-6: ActionDispatcher carries full IrSignal

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/fabric/canonical/DeviceTwin.kt`, `apps/android/app/src/main/java/com/elysium/nexus/fabric/dispatch/ActionDispatcher.kt`, `apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/infrared/InfraredAdapter.kt`

Added `irSignal: IrSignal? = null` field to `DeviceState.IrCommand`. ActionDispatcher now passes the full `resolution.signal` through instead of destructuring into `protocolName/address/command` (which loses carrier, subDevice, repeats, toggle, variant). InfraredAdapter.write() uses `IrProtocol.encode(state.irSignal)` when available, falling back to re-encoding from protocolName.

## P0-7: EXPERIMENTAL codecs blocked in production

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/ProtocolCodecRegistry.kt`, `apps/android/app/src/test/java/com/elysium/nexus/fabric/infrared/ProtocolCodecGoldenVectorTest.kt`

Changed `isCodecTransmittable()` to reject both `CODEC_BLOCKED` and `EXPERIMENTAL`. RC5, RC6, and Kaseikyo now return `EncodeResult.UnsupportedProtocol` at transmission time. Updated test to assert `assertFalse(isCodecTransmittable("RC5"))`.

## P0-8: Progressive universal sweep (brand-first heuristic)

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/database/IrCatalog.kt`, `apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/database/IrCatalogRepository.kt`

Replaced brute-force `LIMIT 400` with progressive brand-tier search:
- **Tier 1:** Samsung, LG, Sony, Panasonic, Philips (global leaders)
- **Tier 2:** Sankey, Kintech, Kalley, Challenger, Daewoo, Hyundai, Hisense, TCL, Noblex, RCA, Akai, Sanyo, Funai, Magnavox (regional/LatAm)
- **Tier 3:** All remaining brands (excluding already-seen)

Each tier queries only its brands, ordered by brand name. Reduces first-match latency from ~23 min (400 × 3.5s) to ~2-5 min for most users.

## P0-9: catalogCanonicalHash from manifest

**Files:** `apps/android/app/src/main/java/com/elysium/nexus/fabric/profile/InstalledIrProfileRepository.kt`

Changed `computeCatalogHash()` to read `canonicalContentSha256` from `ir_catalog.manifest.json` assets instead of computing a profile-level hash. Falls back to old computation only when manifest is unavailable.

## P0-10: Test naming corrected

**Files:** `apps/android/app/src/androidTest/java/com/elysium/nexus/fabric/dispatch/ReleaseBlockerInstrumentedTest.kt`

Updated Javadoc to clarify this is a Room resolution integration test, NOT a HIL (Hardware-In-the-Loop) test. Added note that actual HIL requires external IR receiver hardware.

## P0-11: seed_kintech_v4.py dead references fixed

**Files:** `tools/ir-data/seed_kintech_v4.py`

- `parse_ij(hex_code)` → `parse_nec(hex_code)` (was undefined)
- `SOURCE_TYPE` → `SOURCE_ID` (3 occurrences)
- `__carrier_to_str(carrier)` → `str(carrier)` (was undefined)
- `sig[canonical]` → `sig_ids[canonical]` (was undefined)
- Removed dead `__carrier_to_hig()` and `lastcarrier` at bottom

## Verification

| Gate | Result |
|------|--------|
| `./gradlew :app:testDebugUnitTest` | ✅ 780 tests (1 fixed for P0-7) |
| `./gradlew :app:assembleDebug` | ✅ APK built |
| `./gradlew :app:lintDebug` | ✅ 0 errors |
| Device install | ⏸️ Device VER-N49 disconnected — pending reconnection |

## Remaining (not in this commit)

- P1: Evidence store → ranking integration (closed-loop)
- P1: Room exportSchema=true + explicit migrations
- P1: Supply chain unification (5 Python scripts → 1)
- P1: HIL rig (physical IR receiver harness)
- P1: signal_sources table for traceability
- P1: Runtime manifest hash validation
- P1: Binding deterministic selection simplification
- P1: Atomic catalog installation
