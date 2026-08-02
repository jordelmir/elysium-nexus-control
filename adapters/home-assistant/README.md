# home-assistant

**Protocol:** Home Assistant REST/WS
**Status:** VERIFIED
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Connects to Home Assistant via REST API + WebSocket. Translates entity states to DeviceTwin model. Polls for state changes; WebSocket push is Phase 2+.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/ha/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
