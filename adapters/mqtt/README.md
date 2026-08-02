# mqtt

**Protocol:** MQTT 3.1.1/5.0
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Discovers and controls devices via MQTT topics. Supports Home Assistant MQTT discovery and custom topic trees.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/mqtt/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
