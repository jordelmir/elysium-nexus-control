# vendor

**Protocol:** Vendor REST/WebSocket
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Generic adapter for proprietary REST or WebSocket APIs. Configured with endpoint mappings per vendor.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/vendor/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
