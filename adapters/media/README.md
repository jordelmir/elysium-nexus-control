# media

**Protocol:** Chromecast/DLNA/UPnP
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Controls media playback via cast protocols. Discovers media renderers on the local network.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/media/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
