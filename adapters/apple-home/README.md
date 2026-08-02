# apple-home

**Protocol:** Apple HomeKit (HAP)
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Bridges HomeKit accessories via HAP protocol over BLE/Wi-Fi. Requires MFi certification or HomeKit ADK.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/applehome/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
