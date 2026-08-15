# MASTER ORDER — Elysium Nexus Universal Control OS

> **UNIVERSAL INTENT FABRIC** — Codename: `ELYSiUM NEXUS — UNIVERSAL INTENT FABRIC`
>
> This is the governing constitution for the entire project. It supersedes the
> original 46-section order from the first prompt. Every architectural decision
> must reference one or more of its sections. If code reality diverges, log the
> divergence in the next `docs/changelogs/PHASE_<N>_<NAME>.md`.
>
> **UPDATE (2026-08-15): the consolidated software-first master order
> (68 sections: phone + TV Node + IR + Bluetooth + LAN + evidence, software
> before hardware, retail truth) is the NEW governing order. It is recorded
> verbatim in `MASTER_ORDER_SOFTWARE_FIRST.md` and supersedes the sections
> below as implementation priority. The sections below remain valid as the
> long-horizon architecture; the consolidated order (§59 PR1–PR14 slicing)
> wins where they conflict. Reconciliation note: PR1–PR14 in the consolidated
> order is the execution plan; the identity/evidence/routing architecture of
> this file remains the design backbone.**
>
> `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` = THE ORDER (verbatim).
> This file = architecture detail + legacy sections.

## Fundamental Principle (§0)

```text
Elysium =
    Universal Intent
        +
    Universal Device Identity
        +
    Capability Graph
        +
    Protocol Concordance
        +
    Dynamic Routing
        +
    Verification
        +
    Self-Healing
        +
    Extensibility
```

The user expresses an intent. Elysium determines: which device, which capability,
which protocol, which endpoint, which credential, which command, which route,
which fallback, how to verify it. The UI never needs to know that complexity.

## Engineering Objective

Build the most capable generalist control platform demonstrable through **metrics,
not button count**.

## Product Constraints

- No camera-dependent features
- No advertising
- No paywalled core functionality
- Core must be local-first, offline-capable, functional without mandatory cloud

## Project Metadata

| Key                   | Value                                    |
| --------------------- | ---------------------------------------- |
| Internal codename     | Elysium Nexus Universal Control OS       |
| Architecture codename | Universal Intent Fabric                  |
| Repository root       | `/Users/jordelmirsdevhome/Downloads/celular/Control Universal` |
| Initial lab device    | Honor Magic V2                           |
| Package root          | `com.elysium.nexus`                      |
| Min Android SDK       | 33 (Android 13)                          |
| Target / compile SDK  | 34 (Android 14)                          |
| Native engine language| Rust (where the boundary earns it)       |
| JNI motivation        | Pending benchmark in Phase 0.2           |
| Backend gating        | `REQUIRES_VENDOR_LICENSE` build flag     |
| Commercial console    | Blocked until vendor license obtained    |

## Success Metrics (§1)

```text
Crash-free sessions             > 99.9 %
Wrong-device dispatch           = 0
Silent protocol fallback        = 0
Offline core availability       = 100 %

LAN discovery success           > 98 %
Known-device reconnect          > 99.5 %
LAN command p50                 < 50 ms
LAN command p95                 < 150 ms

IR first candidate              > 80 %
IR top-3                        > 95 %
IR top-5                        > 98 %

Profile migration loss          = 0
Known HIL regression            = 0
Unproven production signals     = 0

Median known-TV setup           < 20 s
```

These are acceptance aspirations, not current results.

## Architecture Sections (§2–§100)

### §2 Competitive Strategy

- **Home Assistant**: Superar en real-time control, gaming, HID, IR, PC interaction,
  dynamic UI, cross-transport routing, latency, verification. Adoptar integration
  architecture, local-first, capabilities, automation, community adapters.
- **Homey**: Elysium Adapter SDK + sandbox + signed manifests + capability
  declarations + compatibility matrix + automated validation.
- **SmartThings/OEM**: Universal Device Knowledge Graph for ALL manufacturers.
- **SwitchBot**: Nexus Receiver (IR TX/RX, USB, BT, BLE, Wi-Fi, CEC, Matter).
- **KDE Connect/Unified Remote**: Elysium Host Fabric (macOS/Windows/Linux).
- **Macro Deck/Stream Deck**: Context awareness + device routing + gamepad + IR
  + HID + multi-device + state verification.

### §3 Universal Intent Model

Protocol-independent semantic actions: `POWER_ON`, `VOLUME_UP`, `MUTE_TOGGLE`,
`NAVIGATE`, `TEXT_INPUT`, `LAUNCH_APP`, `SET_LEVEL`, `EXECUTE_SEMANTIC_COMMAND`.
Vendor adapters translate. No `LG_VOLUME_UP` or `SAMSUNG_POWER`.

### §4 Universal Device Identity

IP/MAC/Bluetooth address is NOT identity. `DeviceIdentity` requires: stable
identifiers (vendor UUID, UPnP UDN, Matter node ID, pairing certificate, hardware
serial, Bluetooth identity, Elysium pairing ID, USB descriptor), network endpoints,
protocol bindings, evidence, confidence score.

### §5 Device Knowledge Graph

```text
Manufacturer → Platform → Device Family → Exact Model → Firmware Family
    → Capabilities → Protocol Bindings → Commands → Compatibility Evidence
```

Each edge declares: source, evidence, confidence, createdAt, lastVerified,
firmwareRange, region. No invented associations.

### §6 Protocol Concordance Graph

For the same device, `VOLUME_UP` maps to: IR signal, webOS command, Sony IRCC,
HDMI CEC opcode, Bluetooth HID consumer usage, Elysium Link semantic action.
Enables: WiFi fails → IR → CEC → WiFi.

### §7 Dynamic Route Intelligence

`ActionRouteScorer` replaces rigid priorities. Inputs: action capability, device
state, route availability, pairing state, authentication, measured latency,
recent failures, historical success, state confirmation ability, wake capability,
security, energy impact, user preference.

### §8 Universal LAN Discovery

`fabric/discovery/` with: `DiscoveryOrchestrator`, MdnsDiscoveryProvider,
SsdpDiscoveryProvider, DialDiscoveryProvider, MatterDiscoveryProvider,
BluetoothDiscoveryProvider, UsbDiscoveryProvider, PreviouslyPairedDiscoveryProvider,
ElysiumLinkDiscoveryProvider. Fuses results into one `DeviceTwin`.

### §9 Universal TV LAN Fabric

`fabric/tv/` with `TvLanAdapter` interface: discover, identify, pair,
queryCapabilities, execute, readState, observeState. First vertical for
full architecture application.

### §10 TV Adapter Priorities

`LgWebOsAdapter`, `SonyBraviaAdapter`, `AndroidGoogleTvAdapter`,
`VizioSmartCastAdapter`, `SamsungTizenAdapter`, `HisenseVidaaAdapter`,
`PhilipsTvAdapter`, `PanasonicTvAdapter`. Never `GenericSmartTvAdapter`.

### §11 Cross-Transport IR Calibration

`CrossTransportCalibrationEngine`: read state via WiFi → send IR candidate →
read state again → confirm causal change. CalibrationExperiment model with
beforeState, afterState, restorationState, result.

### §12 Safe Calibration

Never auto-test: POWER_OFF, factory reset, input reset. Priority: volume +1/restore
-1, mute/restore, navigation in safe context. If cannot restore → ABORT.

### §13 IR Data Fabric v5

Pipeline: sources.lock → immutable source checkout → source parser →
CanonicalSignalDraft → CanonicalRemoteDraft → validation → license gate →
deduplication → Schema v5 → canonical hash → manifest. No v3 intermediate.

### §14 Schema v5 Entities

sources, source_revisions, source_files, brands, device_types, device_models,
device_families, remotes, code_sets, actions, signals, command_bindings,
signal_sources, code_set_models, protocol_definitions, protocol_variants,
compatibility_assertions, physical_test_evidence, catalog_rejections.

### §15 Evidence Model

Hierarchical: IMPORTED_UNREVIEWED → STRUCTURALLY_VALID → PROTOCOL_VALIDATED →
SESSION_VERIFIED → LOCAL_USER_VERIFIED → WIFI_IDENTITY_CONFIRMED →
WIFI_ORACLE_VERIFIED → COMMUNITY_CONFIRMED → HIL_VERIFIED →
LAB_MATRIX_VERIFIED → OEM_VERIFIED. Never auto-promote.

### §16 Confidence Per Action

Per-action confidence scores, not blanket "this remote works." Example:
POWER 99.8%, VOLUME_UP 99.9%, HOME 93.1%, NETFLIX 51.0%.

### §17 Self-Healing Profiles

Action fails → that action = REGRESSION, other actions stay active, recalibrate
only the failed action.

### §18 Firmware-Aware Compatibility

Store: deviceModel, firmware, codeSet, transport, result. Ranking learns from
firmware transitions.

### §19 Profile Auto-Revalidation

Separate catalogCanonicalHashAtInstall, sourceRevision, profileBindingHash.
On catalog update: revalidate every binding. signalId exists? fingerprint same?
→ keep. Not same? → find equivalent → migrate. Not found → needsRevalidation.

### §20–§23 Nexus Receiver

Platform design: MCU/SoC with IR RX/TX, USB, BT Classic, BLE, Wi-Fi, Ethernet
optional, HDMI-CEC, Matter optional, Thread optional. Security: secure boot,
signed OTA, device identity, encrypted pairing, anti-rollback. IR Learning pipeline.
Any Remote → Any Device. Universal Protocol Bridge.

### §24–§26 Elysium Host Fabric

Unified agents for macOS/Windows/Linux. Common `HostAgent` API: mouse, keyboard,
clipboard, files, notifications, media, app list, active application, screen
streaming, system commands, battery, audio, semantic commands. Semantic Desktop
Control. Live Context Surface.

### §27–§29 AI Control Surface

AI Control Surface Generator, Control Surface DSL (declarative YAML), Stream Deck
Mode with pages/folders/context/variables/conditional buttons/live states/macros.

### §30–§33 Universal Gamepad + Control Fusion

Universal Gamepad (sticks, triggers, D-pad, paddles, touch, gyro, accelerometer,
haptics, adaptive layouts). Control Fusion (multiple inputs → one controller state
with ownership). Co-Pilot (User A → sticks, User B → buttons). Accessibility
Control Compiler.

### §34–§38 Scenes + Automation

Multi-Device Scenes with preconditions/execute/confirmation/timeout/rollback.
Macro Transactions (ActionPlan, not delay chains). Scenes As Code (declarative
YAML). Universal Automation Engine. Local Rule Engine (works without cloud).

### §39–§42 Elysium Adapter SDK

Adapter SDK with identity, version, developer, supported platforms, permissions,
discovery, pairing, capabilities, commands, events, security model, compatibility.
Sandboxed plugins with capabilities (NETWORK_LOCAL, BLUETOOTH, etc.). Signed
adapter packages. Official/Community/OEM/Experimental/Blocked classifications.

### §43–§45 Universal Control API + Web Remote

Local authenticated REST API: GET /devices, GET /devices/{id}/capabilities,
POST /devices/{id}/actions, GET /devices/{id}/state. Web Remote with QR/PIN,
temporary URL, limited capabilities, expiration. Guest Capabilities.

### §46–§49 Device Rooms + Proximity + Receiver Mesh

Home → Room → Zone → Device hierarchy. "Device Near Me" without camera using
WiFi/BLE proximity/Nexus Receiver association/room selection/recent device.
Receiver Mesh with IR line-of-sight/network reachability/lowest latency/known
device association. Edge Automation on Receiver.

### §50–§53 Evidence Network + Learning

Compatibility Evidence Network (opt-in, privacy-preserving). Federated Learning.
Candidate Ranking v2 with P(candidate works | model, platform, region, firmware,
evidence). Active Learning (maximize probability of success + information gained
- user disruption).

### §54–§56 WiFi Oracle + HIL + Test Matrix

WiFi Oracle IR Autocalibration (identify TV via LAN → rank IR → read state →
send IR → read state → confirm causal change → WIFI_ORACLE_VERIFIED). HIL Lab
with IR receiver + MCU capture + independent decoder. Device Test Matrix
(stratified, not random).

### §57–§61 Reliability Engineering

Protocol Flight Recorder (every action logs intent → device → routes evaluated →
winning route → command → transport result → state observation → latency).
Professional Diagnostics screen. Self-Healing Routes. Circuit Breakers (5 failures
→ OPEN → cooldown → half-open probe). Hedged Execution with idempotency
classification + state confirmation + cancellation.

### §62–§67 Session + Recording + Simulation

App Context Engine (host agents report activeAppId/capabilities/context).
Profile Continuity across devices. Session Arbitration (owner/controllers/targets/
transport/lease/sequence). Input Recording (UniversalAction + timestamp + device
+ state). Replay Engine (simulation/dry-run/real). Simulator (virtual devices
marked SIMULATED).

### §68–§72 Digital Twin + Trust

Digital Twin History (state history ring, desired/reported/last confirmed state,
confidence). State Reconciliation (desired ≠ reported → retry/fallback/warning).
Credential Vault (Android Keystore, references in Room only). Trust Model
(UNPAIRED → USER_APPROVED → PAIRED → PEER_PINNED → ATTESTED →
MANUFACTURER_CERTIFIED). Zero Trust Local Network.

### §73–§74 Privacy

Core local-first. Cloud optional for community knowledge/sync/remote access.
No ads in core UX.

### §75–§78 Performance + Memory + Battery

Performance Budget: touch → canonical input < 2ms, canonical → route < 1ms,
LAN p50 < 50ms, p95 < 150ms. Battery Budget with states (ACTIVE_CONTROL,
PASSIVE_MONITOR, BACKGROUND, IDLE). Discovery Backoff. Memory Budget (SQLite
indexed queries, candidate paging, lazy signals, bounded cache).

### §79–§81 Reliability + Errors

All external operations: timeout, typed error, retry policy, cancellation,
telemetry. Error Taxonomy: DiscoveryError, IdentityError, PairingError, etc.
Zero Failure Without Explanation (show what happened and what Elysium did).

### §82–§86 Release + Security

Release Factory: lint → unit → property → fuzz → schema → catalog → license →
reproducibility → instrumentation → security → HIL → device matrix → sign → R8
→ SBOM → provenance attestation. Production Signing (no debug signing). Supply-
Chain Attestation. Security Fuzzing. Plugin Security (sandboxed, capability-
scoped).

### §87–§90 Ecosystem

Elysium Verified certification. OEM Program (manufacturer adapters). Community
Program (contributors with CI verification). Documentation as Product.

### §91 Implementation Roadmap

**Release A — Nexus Foundation 1.0**: IR correctness, Schema v5, profile
revalidation, state machine, process death, release signing, HIL.
**Gate: IR production trustworthy.**

**Release B — Universal TV Fabric**: LAN discovery, stable identity, LG, Sony,
Android/Google TV, Vizio, route scoring, WiFi remote.
**Gate: same TV controllable via WiFi + IR.**

**Release C — Cross-Transport Intelligence**: Protocol Concordance, WiFi Oracle,
automatic IR calibration, self healing, confidence per action.
**Gate: Elysium can automatically identify and validate IR.**

**Release D — Universal Host Fabric**: macOS, Windows, Linux, clipboard, files,
notifications, media, semantic commands, context surfaces.
**Gate: one Android replaces mouse + keyboard + trackpad + stream deck.**

**Release E — Automation OS**: scenes, transaction macros, rule engine, multi-
device, local automation.
**Gate: complex room control without vendor-specific logic.**

**Release F — Nexus Receiver**: IR RX/TX, USB, BT, WiFi, CEC, secure OTA.
**Gate: Elysium works even when phone lacks IR.**

**Release G — Elysium Ecosystem**: Adapter SDK, plugin sandbox, signed packages,
community evidence, manufacturer program, local API.
**Gate: ecosystem can scale beyond internal engineering team.**

### §92–§95 Product Experience

First opening discovers devices. User authorizes once. Elysium discovers WiFi/IR/CEC.
WiFi works → asks about IR backup → calibrates via cross-transport. User presses
volume → Elysium chooses WiFi. WiFi disconnects → Elysium uses IR. User doesn't
switch controls. Context-aware control surfaces (Photoshop → brush/eraser/undo,
Spotify → play/next/volume, game → gamepad layout).

### §96 Architecture Supreme Gate

1. Every physical device has one stable DeviceIdentity.
2. Every action is semantic.
3. Every transport is replaceable.
4. Every command has provenance.
5. Every successful control can produce evidence.
6. Every state mutation can be verified where possible.
7. Every route failure has fallback.
8. Every profile survives updates.
9. Every plugin is permission-scoped.
10. Core operates offline.
11. No silent fallback.
12. No fake verification.
13. No guessed production compatibility.
14. No debug signing.
15. No mandatory cloud.
16. No protocol exposed unnecessarily to normal users.
17. New devices can be integrated without modifying core.
18. New transports can be added without modifying UI.
19. New control surfaces can be added without modifying protocols.
20. HIL validates physical pathways.

### §97 Maturity States

Each feature must have exactly one: CONCEPT → DESIGNED → IMPLEMENTED →
UNIT_VERIFIED → INTEGRATION_VERIFIED → ON_DEVICE_VERIFIED →
REAL_DEVICE_VERIFIED → HIL_VERIFIED → DEVICE_MATRIX_VERIFIED →
PRODUCTION_APPROVED. Never skip stages.

### §98 Anti-Patterns

No pursuing: more screens, more buttons, more fake brands, more vanity metrics
before closing: identity, routing, evidence, reliability.

### §99 Competitive Advantage

Home Assistant has more integrations. Homey has more plugins. Samsung knows its
own products better. SwitchBot has more mature hardware. Unified Remote has more
years of PC control. Elysium builds something different: Universal Intent Layer
+ Device Identity Graph + Capability Knowledge Graph + Protocol Concordance Graph
+ Dynamic Routing + Verification + Learning + Self-Healing. An architecture
above all of them.

### §100 Mission

> **Elysium Nexus must transform any authorized control intent into the correct
> action on the correct device via the best available route, with minimal
> configuration, practical latency, execution evidence where possible, and
> automatic recovery from failures.**

When this works for TVs, computers, phones, IoT, multimedia, gaming, and
Elysium hardware under the same canonical model, we are not building a simple
universal app. We are building the layer that connects:

**person → intent → device → physical/digital action.**

## Previous Sections (Legacy Reference)

The original 46-section order from the first prompt is preserved in
`MASTER_ORDER_SECTIONS.md` for historical reference. The sections above
supersede them entirely.
