# Architecture Map

> **Status:** Phase ULT.0 — generated per
> `MASTER_ORDER.md` §47 "Inspecciona todo el
> repositorio … Genera mapa de arquitectura".
>
> This document is the single source of truth for
> *what is where*. It is a generated artefact: the
> next iteration's map will reflect the next phase's
> additions. The map is read top-down, deepest
> abstraction to shallowest surface.

## 0. Hierarchy of control (per `MASTER_ORDER.md` §2)

```
1. Control local directo.
2. Protocolo estándar local.
3. Integración oficial local.
4. Agente Elysium.
5. Elysium Nexus Hub.
6. Elysium Nexus Receiver.
7. Integración cloud-to-cloud.
8. Remote Play o streaming.
9. Backend certificado.
10. Modo de compatibilidad explícito.
```

The Android app, desktop agents, Hub, and Receiver
are siblings. The Android app is the **first** shipped
surface, not the **only** one. Every component must
speak the canonical model (`crates/control-core/`).

## 1. Top-level map

```
elysium-nexus/
├── apps/                    # user-facing entry points
│   ├── android/             # the APK (the first shippable surface)
│   ├── ios-companion/       # [Phase ULT.X] iOS app
│   ├── macos-agent/         # [Phase 3] macOS companion
│   ├── windows-agent/       # [Phase 3] Windows companion
│   ├── linux-agent/         # [Phase 3] Linux companion
│   ├── web-console/         # [Phase ULT.X] browser
│   ├── retailer-dashboard/  # [Phase 10] retail
│   └── installer-console/  # [Phase 10] installer
│
├── hub/                     # Elysium Nexus Home Hub
│   ├── os/                  # secure embedded Linux
│   ├── services/            # systemd-style service definitions
│   ├── containers/          # container manifests
│   ├── matter/              # Matter controller
│   ├── thread/              # Thread border router
│   ├── zigbee/              # Zigbee coordinator
│   ├── zwave/               # Z-Wave controller
│   ├── infrared/            # IR engine (transmit + receive)
│   └── cameras/             # ONVIF + media
│
├── firmware/                # Elysium Nexus Receiver
│   ├── receiver/            # main MCU firmware
│   ├── ir-module/           # IR add-on
│   ├── radio-module/         # sub-GHz add-on
│   ├── bootloader/          # secure boot
│   └── hardware-tests/      # HiL
│
├── crates/                  # shared Rust core (cross-platform)
│   ├── control-core/        # canonical control model
│   ├── device-twin/         # device twin + DKG
│   ├── capability-core/     # capability model
│   ├── protocol-core/       # Elysium Link
│   ├── automation-core/     # automation engine
│   ├── policy-core/         # RBAC + ABAC
│   ├── crypto-core/         # identity + crypto
│   ├── input-core/          # input capture (per-OS)
│   ├── hid-core/            # HID descriptor builder
│   ├── ir-core/             # IR protocol plugins
│   ├── telemetry-core/      # latency + perf
│   └── simulation-core/     # device simulators
│
├── adapters/                # protocol-specific implementations
│   ├── google-home/         # Google Home APIs
│   ├── apple-home/          # HomeKit + Matter multi-admin
│   ├── alexa/               # Alexa Smart Home Skill
│   ├── home-assistant/      # WebSocket + REST
│   ├── matter/              # Matter SDK adapter
│   ├── zigbee/              # zigpy / ZHA-style
│   ├── zwave/               # Z-Wave JS
│   ├── ble/                 # Bluetooth + BLE
│   ├── mqtt/                # MQTT 3.1.1 / 5
│   ├── onvif/               # ONVIF Profile T
│   ├── infrared/            # IR protocol plugins
│   ├── media/               # Cast / DLNA / AirPlay bridges
│   └── vendor/              # vendor cloud APIs
│
├── databases/               # versioned reference data
│   ├── devices/             # manufacturer / model / firmware
│   ├── infrared/            # IR code library
│   ├── mappings/            # control → capability mappings
│   ├── compatibility/       # VERIFIED_LAB / COMMUNITY
│   ├── quirks/              # per-firmware quirks
│   └── security/            # advisories
│
├── schemas/                 # versioned JSON / proto schemas
│   ├── devices/
│   ├── protocol/
│   ├── automation/
│   ├── profiles/
│   ├── permissions/
│   └── telemetry/
│
├── tools/                   # developer + ops tools
│   ├── ir-analyzer/         # waveform debug
│   ├── ir-importer/         # bulk IR import
│   ├── protocol-inspector/  # capture + decode
│   ├── device-simulator/    # mock devices
│   ├── compatibility-runner/
│   ├── latency-profiler/
│   ├── automation-debugger/
│   └── security-auditor/
│
├── docs/                    # docs (this file lives here)
│   ├── architecture/        # vision, master order, ADRs
│   ├── adr/                 # decision records
│   ├── research/            # OSINT, papers
│   ├── protocols/           # Elysium Link spec
│   ├── security/            # threat model, crypto notes
│   ├── privacy/             # privacy posture
│   ├── hardware/            # receiver selection, BOM
│   ├── compatibility/       # device matrix docs
│   ├── testing/             # test plans
│   └── business/            # licensing, partnerships
│
└── .github/workflows/       # CI
```

## 2. Cross-cutting modules

### `crates/control-core/` — the canonical model

Single source of truth for the data that flows
through the system. Per `MASTER_ORDER.md` §4:

- `UniversalControlState` — the headline state object
- `HumanInputState` — keyboard, mouse, trackpad,
  gamepads, touches, motion, voice, custom
- `TargetState` — what is being controlled right now
- `EnvironmentState` — ambient, posture, network
- `AutomationState` — pending + executing automations
- `Capability` enum — the 30+ capabilities from §4.3
- `DeviceTwin` — per §4.2
- `DeviceKnowledgeGraph` — per §5

Every other module is either a producer
(`apps/android/`, `adapters/ble/`, `adapters/matter/`),
a consumer (`apps/macos-agent/`, `hub/services/`),
or a transformer (`crates/automation-core/`).
None of them may extend the canonical model without
an ADR.

### `crates/protocol-core/` — Elysium Link

The wire format that ties Android, desktop agents,
Hub, Receiver, and cloud into one fabric. Per §19.
Versioned. Encryption + mutual auth + replay
protection. Lives in `schemas/protocol/`.

### `crates/automation-core/` — the brain

Per §28. Trigger + Conditions + Policy + Actions +
Verification + Compensation. Deterministic. Local
when possible. Cloud-assist optional.

### `crates/policy-core/` — RBAC + ABAC + risk

Per §31.3-31.4. Every action has a risk class
(Informational / Low / Reversible / PhysicalMotion
/ PrivacySensitive / SecuritySensitive / HighPower
/ LifeSafety) and a required authorization level.

### `crates/crypto-core/` — identity

Per §31.1-31.2. Per-device keys, mutual auth, AEAD,
replay protection, key rotation. Android Keystore
+ Hub TPM + Receiver secure element.

## 3. The shippable sequence

Per `MASTER_ORDER.md` §42 + §45, the milestones:

1. **Milestone 1 — Universal Input** — Android
   app + desktop agents: mouse / keyboard /
   trackpad / gamepad. **In flight (Phase 0-1).**
2. **Milestone 2 — Universal Infrared** — Direct
   IR on supported phones + Hub IR + learning +
   database. **This turn introduces the IR core**
   (pure protocol plugins + Android adapter).
3. **Milestone 3 — Smart Home** — Hub + Matter /
   Thread / Zigbee / Z-Wave / BLE. Lights, HVAC,
   lock, curtain, sensor. Local automation. Internet
   disconnected — system remains functional.
4. **Milestone 4 — Security** — Camera + doorbell +
   lock. Live stream + PTZ + two-way talk. Step-up
   authorization. Lock action. Confirmed state + audit.
5. **Milestone 5 — Intelligent Home** — Voice
   command → AI intent → visible plan → policy
   validation → deterministic execution → device
   confirmation → human-readable result.

## 4. Today (Phase ULT.0)

This iteration delivers:

- §44 repo restructure
- §47 four foundational docs:
  - `docs/architecture/ARCHITECTURE_MAP.md` (this file)
  - `docs/architecture/INVENTORY.md`
  - `docs/security/THREAT_MODEL.md`
  - `docs/security/RISK_REGISTER.md`
- §4 canonical model extensions:
  - `crates/control-core/Capability.kt`
  - `crates/device-twin/DeviceTwin.kt`
  - `crates/device-twin/DeviceKnowledgeGraph.kt`
- §6 IR POC:
  - `crates/ir-core/IrProtocol.kt` (pure protocol
    catalog)
  - `crates/ir-core/IrWaveform.kt` (pure waveform
    data + NEC encoder + decoder)
  - `adapters/infrared/AndroidIrTransmitter.kt`
    (Android `ConsumerIrManager` adapter)
- §31.1 Identity service skeleton
- §28 Automation Engine: trigger / conditions /
  actions / executor

## 5. What's NOT in this iteration (per §47 "no
declarar éxito sin hardware real")

The following ship as POCs in this iteration but
require **hardware-in-loop** validation per §39 + §42
before any of them can claim `VERIFIED_LAB`:

- IR transmitter on a real phone with FEATURE_CONSUMER_IR
  (Honor Magic V2 **does not** have an IR emitter;
  needs a Hub or a phone with IR for HiL).
- Matter devices (commissioning, multi-admin).
- Zigbee coordinator on real hardware.
- Z-Wave module (regional frequency, certification).
- BLE devices.
- ONVIF cameras.
- Smart locks (LIF, Level, Schlage, etc.).
- Garage controllers.

The Hub is a hardware project; the receiver is
hardware. Both require board selection, BOM,
and HiL rigs per `MASTER_ORDER.md` §20.
