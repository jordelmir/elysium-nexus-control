# matter

**Protocol:** Matter (Thread/Wi-Fi)
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Discovers and controls Matter-compliant smart home devices via the Android CHIP library or Nexus Hub's Matter controller. Supports OnOff, Level, Color, Temperature, Lock, Position, Scene capabilities.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/matter/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
