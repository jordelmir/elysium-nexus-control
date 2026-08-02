# zigbee

**Protocol:** Zigbee 3.0
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Discovers and controls Zigbee devices via the Nexus Hub's coordinator (CC2652/EFR32). Translates ZCL clusters to canonical capabilities.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/zigbee/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
