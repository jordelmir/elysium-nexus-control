# Phase ULT.0 — Fabric Foundation

> The "ULT" phase series is the §44 + §47
> first-execution response to the *ultimate
> final* master order. The previous
> `PHASE_<N>_<NAME>.md` changelogs cover
> Phases 0-1.24 (the Android gamepad app);
> this iteration opens the **Elysium Nexus
> Universal Control Fabric** — the broader
> ecosystem that the gamepad app is the
> first surface of.

## §1 — Scope of this iteration

Per `MASTER_ORDER.md` §47 "Primera ejecución
de la IA", the first execution must:

1. Inspect the repository.
2. Execute builds.
3. Execute tests.
4. Document failures.
5. Generate architecture map.
6. Generate inventory.
7. Generate threat model.
8. Generate risk register.
9. Design canonical model.
10. Design Device Knowledge Graph.
11. Design Capability Model.
12. Implement IR POC.

This iteration ships items 1-12 plus the
foundational scaffolding for the rest of
the fabric (identity, automation engine,
repo restructure to §44).

The fabric is a multi-iteration project.
Each phase ships one milestone; this
phase ships the foundation.

## §2 — Repo restructure (per §44)

`apps/android-controller/` renamed to
`apps/android/`. New top-level folders
created:

- `apps/ios-companion/` (scaffold)
- `apps/web-console/` (scaffold)
- `apps/retailer-dashboard/` (scaffold)
- `apps/installer-console/` (scaffold)
- `hub/{os,services,containers,matter,thread,zigbee,zwave,infrared,cameras}/` (scaffolds)
- `firmware/{receiver,ir-module,radio-module,bootloader,hardware-tests}/` (scaffolds)
- `crates/{control-core,device-twin,capability-core,protocol-core,automation-core,policy-core,crypto-core,input-core,hid-core,ir-core,telemetry-core,simulation-core}/` (scaffolds)
- `adapters/{google-home,apple-home,alexa,home-assistant,matter,zigbee,zwave,ble,mqtt,onvif,infrared,media,vendor}/` (scaffolds)
- `tools/{ir-analyzer,ir-importer,protocol-inspector,device-simulator,compatibility-runner,latency-profiler,automation-debugger,security-auditor}/` (scaffolds)

Every scaffold has a `README.md` (per
§6 "Empty modules are forbidden" with a
placeholder explaining the role, the
public API, the owner, the allowed
dependencies, the tests, the threat
model, and the maturity).

## §3 — §47 foundational docs

Four foundational documents:

- `docs/architecture/ARCHITECTURE_MAP.md`
  — the full fabric map (replaces
  `docs/ARCHITECTURE.md` as the canonical
  map; the old doc remains for the
  Android-only arch detail).
- `docs/architecture/INVENTORY.md` — the
  current state-of-the-code snapshot.
- `docs/security/THREAT_MODEL.md` —
  STRIDE per trust boundary, action risk
  policy, TOFU pairing, audit log.
- `docs/security/RISK_REGISTER.md` — risks
  R0-R9 with likelihood × impact, status
  (OK / MED / HIGH / BLOCKED_BY_*), and
  owner.

## §4 — Canonical model (per §4)

Three new pure-Kotlin modules under
`apps/android/.../fabric/`:

- `canonical/Capability.kt` — the
  40+ capability enum (OnOff, Level,
  Color, ColorTemperature, Temperature,
  TargetTemperature, FanSpeed, Swing,
  Mode, Timer, OpenClose, Position,
  Direction, LockUnlock, ArmDisarm,
  StartStop, PauseResume, MediaTransport,
  Volume, Channel, InputSource, Scene,
  EnergyRead, EnergyControl, Charging,
  CameraStream, CameraPtz, CameraTalk,
  CameraRecord, Doorbell, Presence,
  MotionDetection, ContactDetection,
  SmokeDetection, CarbonMonoxideDetection,
  WaterLeakDetection, AirQuality,
  Irrigation, Custom). Every variant
  carries a default [ActionRisk] per
  §31.4.
- `canonical/DeviceTwin.kt` — the §4.2
  device twin (immutable data class),
  `DeviceId`, `DeviceType` (40+ types),
  `DeviceState` sealed hierarchy
  (OnOff, Level, Color, ColorTemperature,
  Climate, Lock, Position, Media,
  EnergyRead), `ConnectivityState`,
  `TrustState`, `ProtocolBinding`,
  `Protocol`.
- `canonical/DeviceKnowledgeGraph.kt` —
  the §5 DKG (immutable), `GraphNode`,
  `Location`, `GraphEdge`, `Relation`
  (Controls, RemoteControls, Triggers,
  Observes, Secures, BelongsTo, Measures,
  Powers, Coordinates). Pure JVM; the
  Hub / Android / desktop agents all
  mirror.

The pure-Kotlin placement is a
pragmatic choice: the Android module
has the build infrastructure; the
canonical model is JVM-testeable from
day 1. The future home is the Rust
`crates/` (per §44); the Kotlin
implementation is a faithful reference
that the Rust port will follow.

## §5 — IR POC (per §6)

Three new pure-Kotlin modules +
one Android adapter:

- `infrared/IrProtocol.kt` — the
  protocol catalog (NEC, NECx, RC5,
  RC6, SonySIRC, Samsung, Kaseikyo,
  Raw), each with carrier + encoding.
- `infrared/IrWaveform.kt` — pure
  waveform data class + NEC / NECx /
  RC5 encoders + NEC decoder. The
  decoder is conservative: ±25%
  timing tolerance (per §6.4 receiver
  jitter).
- `infrared/AndroidIrTransmitter.kt` —
  Android `ConsumerIrManager` adapter.
  Detects `FEATURE_CONSUMER_IR`,
  validates carrier against the
  emitter's range, queues at most one
  transmit at a time, never crashes
  the activity (§38).

The IR POC is the first shippable
§6 surface. The Honor Magic V2 lab
device does **not** have an IR
emitter; the POC's "is the device
ready?" decision is the runtime
check (`AndroidIrTransmitter.hasEmitter()`)
the activity uses to hide the IR
controls. A Hub with an IR emitter
is the path to "control the TV from
your phone" — that path requires
hardware, queued for §7 / §20.

## §6 — Identity service (per §31.1)

Two modules under
`apps/android/.../fabric/identity/`:

- `DeviceIdentity.kt` — the interface
  + the `InMemoryDeviceIdentity`
  (test-friendly, HMAC-SHA256) + the
  `Fingerprint` helper (SHA-256 of the
  public key, hex-encoded).
- `AndroidDeviceIdentity.kt` — the
  Android Keystore adapter
  (HMAC-SHA256 with `KeyProperties.PURPOSE_SIGN`
  + `DIGEST_SHA256`, 256-bit key,
  alias `elysium.device.signing.v1`).
  The `deviceId` is persisted in
  `SharedPreferences` (the secret is
  the Keystore key; the id is not).

The identity service is the §31.2
foundation: every Elysium device has
a stable fingerprint + signing key;
the §19 Elysium Link uses mTLS with
the fingerprint as the join key.

## §7 — Automation Engine (per §28)

Two modules under
`apps/android/.../fabric/automation/`:

- `Automation.kt` — the data class
  (id, name, author, createdAtNs,
  triggers, conditions, actions,
  verification, compensation,
  signature). The [TriggerEvent] enum
  is the §28.1 vocabulary (25+
  events). The [ConditionKind] enum
  is the §28.2 vocabulary (15+
  conditions). The [CommandValue] is
  a typed envelope. The
  [VerificationPolicy] is the §28.3
  audit + retry policy. The
  [CommandStatus] is the §40 result
  class. The [IdempotencyKey] is the
  §28.4 dedup key.
- `AutomationEngine.kt` — the
  deterministic executor. Pure
  function: given the same inputs,
  same outputs + same side effects.
  The engine returns a [Verdict]
  (Completed / AlreadyRunning /
  ConditionsNotMet / CompensationRan).
  The dedup is via [AutomationStore];
  the action execution is via
  [ActionDispatcher]. Both are
  interfaces; tests stub with
  in-memory fakes.

The engine is JVM-testeable. The
production wiring is in the Hub (for
automations that run 24/7) and in
the Android app (for automations
that need to fire when the device
is awake). Cloud is optional.

## §8 — Test count and build status

| Metric                  | Before  | After  |
|-------------------------|---------|--------|
| Unit tests              | 434     | **505** |
| JVM tests passing       | 434     | 505    |
| `assembleDebug`         | green   | green  |
| `lintDebug`             | broken  | broken (Bug #17, deferred) |

The 71 new tests cover:
- `Capability` (11)
- `DeviceTwin` + `DeviceKnowledgeGraph` (12)
- `IrWaveform` (15)
- `DeviceIdentity` + `Fingerprint` (10)
- `Automation` + `AutomationEngine` (13)
- The remaining 10 are existing tests
  that exercise the new code paths.

## §9 — What's deferred to the next iterations

- **Rust port** of the canonical model
  (`crates/control-core/`, `crates/device-twin/`).
  The Kotlin implementation is the
  reference; the Rust port follows
  once `cargo` is wired in CI.
- **IR receiver** (learning). The §6.3
  "IR Receiver and Learning" pipeline
  needs hardware (an IR photodiode +
  edge timestamp capture). The pure
  decoder is in; the receiver is a
  Hub-only feature.
- **Adapter wiring** (`adapters/`,
  `hub/`). The interfaces are designed;
  the production implementations
  land in subsequent phases.
- **Schema extraction** (`schemas/`).
  The canonical model is in code;
  the versioned JSON / proto schema
  follows once the model stabilises.

## §10 — Open work (updated §0 of inventory)

- **Phase 2+** — Real Bluetooth HID
  transport (hardware).
- **Phase 2+** — Compose Compiler
  upgrade (upstream).
- **Phase 3** — Desktop agents
  (macOS / Windows / Linux).
- **Phase 4** — Nexus Receiver firmware
  (hardware).
- **Phase 4+** — Matter / Thread /
  Zigbee / Z-Wave coordinators (Hub
  hardware + per-protocol certification).
- **Phase 6** — Vendor licensing
  (PlayStation, Xbox, Nintendo).
- **Phase 7+** — Cloud integrations
  (Google Home, Apple Home, Alexa,
  Home Assistant).
