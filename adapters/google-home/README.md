# google-home

**Protocol:** Google Home Graph
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Bridges Google Home/Nest devices via Home Graph API. Requires Smart Home Action on Actions Console.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/googlehome/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
