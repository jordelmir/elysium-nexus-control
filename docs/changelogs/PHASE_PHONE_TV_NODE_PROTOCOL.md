# PHASE_PHONE_TV_NODE_PROTOCOL — Phone-Side TV Node Foundation

> **Date:** 2026-08-15
> **Commit:** (pending)
> **MASTER_ORDER references:** §7, §8, §9, §10, §11, §18, §19, §20, §21, §28

## What changed

### New files

| File | Purpose |
|---|---|
| `fabric/canonical/CapabilityManifest.kt` | §7 capability status enum (SUPPORTED → UNVERIFIED) + convenience TV capability sets |
| `fabric/tv/TvNodeProtocolClient.kt` | §11 protocol envelope (TvNodeCommand + 7 response states) + TvNodeObservation sealed hierarchy |
| `fabric/tv/TvNodeDiscoveryProvider.kt` | §9 NSD/DNS-SD discovery for `_elysium-tv._tcp` |
| `fabric/tv/TvNodePairingEngine.kt` | §10 secure pairing with X25519 key exchange, QR codes, human-readable codes |
| `fabric/calibration/CausalActionVerifier.kt` | §19 causal correlation engine with forward + inverse challenge trials |
| `fabric/calibration/IrOracleCalibrationEngine.kt` | §20 software-only IR oracle pipeline: probe → transmit → observe → verify |

### Modified files

| File | Change |
|---|---|
| `fabric/canonical/DeviceTwin.kt` | Added `Television` to DeviceType enum; added `TvNode` to Protocol enum |
| `fabric/routing/TransportRoute.kt` | Added TvNode route priority (8) and latency estimate (15ms) to RouteNegotiator |

## Architecture impact

- The phone now has a complete TV Node protocol client stack ready for when the TV Node APK exists
- Route planner can score TV_NODE as the preferred route (priority 8, faster than DirectIr at 10)
- Capability manifest enables UI generation from actual device capabilities (no fake buttons)
- Causal verification closes the loop: IR transmit → TV observe → confirm → evidence
- IR Oracle pipeline automates code set discovery without manual user testing

## Maturity

| Component | Maturity |
|---|---|
| UniversalAction | UNIT_VERIFIED (pre-existing) |
| DeviceTwin | UNIT_VERIFIED (pre-existing) |
| RouteNegotiator (TvNode) | IMPLEMENTED |
| CapabilityManifest | IMPLEMENTED |
| TvNodeProtocolClient | IMPLEMENTED |
| TvNodeDiscoveryProvider | IMPLEMENTED |
| TvNodePairingEngine | IMPLEMENTED |
| CausalActionVerifier | IMPLEMENTED |
| IrOracleCalibrationEngine | IMPLEMENTED |

## Known limitations

- TvNodePairingEngine uses fallback EC keys when X25519 is unavailable
- IrOracleCalibrationEngine needs VolumeDown waveform for full inverse challenge
- TV Node protocol client needs actual TCP/WebSocket transport (currently data models only)
- No integration tests until TV Node APK exists

## Next blocker

- Build TV Node (PR3) so phone ↔ TV Node protocol can be integration-tested
- Wire the protocol client to a real network transport
- Add TV Node routes to the UI device screen