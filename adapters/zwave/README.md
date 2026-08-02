# zwave

**Protocol:** Z-Wave (700/800)
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Discovers and controls Z-Wave devices via the Nexus Hub's Z-Wave controller. Translates Z-Wave command classes to canonical capabilities.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/zwave/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
