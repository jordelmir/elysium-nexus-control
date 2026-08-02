# onvif

**Protocol:** ONVIF Profile S/T/G
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Discovers and controls IP cameras via ONVIF standards. Translates ONVIF WS commands to camera capabilities.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/onvif/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
