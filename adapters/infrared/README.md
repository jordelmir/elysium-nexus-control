# infrared

**Protocol:** IR (Direct/Hub)
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Transmits IR commands via phone IR blaster or Nexus Hub IR transmitter. Uses existing IrLearner for learning, IR database for playback.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/infrared/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
