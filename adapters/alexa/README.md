# alexa

**Protocol:** Amazon Alexa Smart Home
**Status:** STUB
**Maturity:** STUB (interface defined, implementation pending)

## Purpose

Bridges Alexa Smart Home API devices into the canonical model. Requires Alexa Skills Kit lambda + OAuth2.

## Implementation

Kotlin adapter lives in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/alexa/`

Implements `DeviceAdapter` interface from `fabric/adapter/DeviceAdapter.kt`.

## Dependencies

- Core: `fabric.canonical.*` (DeviceTwin, Capability, Protocol)
- Interface: `fabric.adapter.DeviceAdapter`

## Tests

`DeviceAdapterContractTest` verifies lifecycle and capability contract.
