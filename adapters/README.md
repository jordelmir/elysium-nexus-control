# Adapters

Protocol adapters bridge smart home / IoT APIs to the
Elysium Nexus canonical device model (`DeviceTwin`,
`DeviceState`, `Capability`, `Protocol`).

## Architecture

```
DeviceAdapter (interface)
  ├── HomeAssistantAdapter  ← VERIFIED (REST API)
  ├── MatterAdapter         ← STUB (CHIP SDK / Hub)
  ├── ZigbeeAdapter         ← STUB (coordinator HW)
  ├── ZWaveAdapter          ← STUB (controller HW)
  ├── MqttAdapter           ← STUB (Paho client)
  ├── OnvifAdapter          ← STUB (SOAP/WS)
  ├── InfraredAdapter       ← STUB (IR HW)
  ├── BleAdapter            ← STUB (GATT DB)
  ├── MediaAdapter          ← STUB (Cast/UPnP)
  ├── AlexaAdapter          ← STUB (ASK lambda)
  ├── GoogleHomeAdapter     ← STUB (Actions Console)
  ├── AppleHomeAdapter      ← STUB (MFi/ADK)
  └── VendorAdapter         ← STUB (vendor config)
```

## Interface

`DeviceAdapter` is defined in:
`apps/android/app/src/main/java/com/elysium/nexus/fabric/adapter/DeviceAdapter.kt`

Lifecycle: `Idle → start() → Active → scan/read/write → stop() → Released`

## Canonical types

- `DeviceTwin` — immutable device state snapshot
- `DeviceState` — sealed hierarchy (OnOff, Level, Color, Climate, Lock, Position, Media, …)
- `Capability` — closed enum of controllable dimensions
- `Protocol` — closed enum of protocol families

## Tests

`DeviceAdapterContractTest` verifies all adapters:
- Correct protocol and label
- Non-empty capabilities
- Lifecycle transitions (Idle → Active → Released)
- Error codes for stub operations
