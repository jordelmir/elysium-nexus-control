# ORDEN MAESTRA DE IMPLEMENTACIÓN
## ELYSIUM NEXUS — SOFTWARE-FIRST UNIVERSAL TV FABRIC + AUTONOMOUS IR ORACLE + RETAIL TRUTH

> **Governing constitution for Elysium Nexus.**
> Supersedes all prior versions.
>
> **Pivot fundamental:** Elysium Nexus deja de ser una APK con muchos controles
> y pasa a ser una **plataforma distribuida de control**: teléfono + TV Node + IR +
> Bluetooth + LAN + evidencia; primero 100% software con lo que tenemos hoy,
> después hardware propio únicamente donde aporte capacidades físicamente
> imposibles por software.
>
> **Commercial target:** Gollo Costa Rica → Tienda Monge → El Verdugo.
> Compatibilidad **ganada** modelo por modelo, acción por acción.

---

```text
══════════════════════════════════════════════════════════════════════════════
                         ELYSIUM NEXUS
                    MASTER IMPLEMENTATION ORDER

       SOFTWARE-FIRST UNIVERSAL TV FABRIC
     AUTONOMOUS IR ORACLE · RETAIL TRUTH · ZERO FAKE COMPATIBILITY
══════════════════════════════════════════════════════════════════════════════
```

---

## 0. ABSOLUTE PRODUCT CONSTITUTION

These rules are non-negotiable.

- NO MOCK AS PRODUCTION.
- NO FAKE DEVICE SUPPORT.
- NO TEMPLATE AS PHYSICAL TRUTH.
- NO GUESSED IR COMMAND.
- NO AI-GENERATED PRODUCTION IR SIGNAL.
- NO SILENT PROTOCOL FALLBACK.
- NO SILENT TRANSMISSION FAILURE.
- NO "ALL TVs" CLAIM WITHOUT EVIDENCE.
- NO ROOT ASSUMPTION.
- NO OEM PRIVILEGE ASSUMPTION.
- NO PRIVATE VENDOR API ASSUMPTION.
- NO ACCESSIBILITY ABUSE.
- NO ADB AS DEFAULT RETAIL CONTROL.
- NO HARDCODED RELEASE SECRETS.
- NO MANUFACTURER+MODEL AS UNIQUE PHYSICAL DEVICE ID.
- NO INTERNET REQUIREMENT FOR CORE REMOTE CONTROL.

An unknown value remains **UNKNOWN**.

UNKNOWN must never silently become: `0`, `38 kHz`, `NEC`, `Samsung`, `generic`,
`supported`, `verified`, or `compatible`.

---

## 1. REALITY / MATURITY MODEL

Every capability must have exactly one defensible maturity level:

```
CONCEPT
DESIGNED
IMPLEMENTED
UNIT_VERIFIED
INTEGRATION_VERIFIED
ON_DEVICE_VERIFIED
REAL_DEVICE_VERIFIED
HIL_VERIFIED
DEVICE_MATRIX_VERIFIED
RETAIL_MATRIX_VERIFIED
PRODUCTION_APPROVED
```

Never promote from `"test passed"` directly to `"works on TVs"`.

- **TX_OK** means the transmitting API accepted the waveform.
  TX_OK does NOT mean the intended TV reacted.
- **KeyEvent observed** ≠ raw IR captured.
- **Database entry** ≠ physically working signal.
- **User confirmation** ≠ retail-certified compatibility.

---

## 2. EXECUTION BASELINE

At the beginning of every implementation session:

1. Fetch the current main HEAD.
2. Read AGENTS.md.
3. Read current architecture docs.
4. Read current Reality Ledger.
5. Read latest IR regression/evidence reports.
6. Read current catalog manifest.
7. Read current source locks.
8. Inspect open PRs.
9. Inspect current CI status.
10. Never assume the previous audit HEAD is still current.

Create a feature branch. Never perform large experimental changes directly on main.

Before modifying architecture, record:

```
CurrentGitSha
CurrentCatalogBuildId
CurrentAppVersion
CurrentSchemaVersion
CurrentProtocolRegistryVersion
CurrentTestCount
CurrentKnownRegressions
```

---

## 3. FINAL PRODUCT MODEL

Elysium Nexus is **NOT** "a universal remote Android app."

Elysium Nexus **IS** a **Universal Intent Fabric** that converts an authorized
human intent into the correct physical or software action on the correct device
through the best verified route available.

**Canonical pipeline:**

```
User Intent
    ↓
UniversalAction
    ↓
Physical Device Identity
    ↓
DeviceTwin
    ↓
Capability Resolver
    ↓
Trust / Permission Policy
    ↓
Route Planner
    ↓
Best Verified Transport
    ↓
Execution
    ↓
Observation
    ↓
Evidence
    ↓
Learning
    ↓
Fallback / Self-Healing
```

One physical television has **ONE DeviceTwin**.

Example: `TV Samsung X`
```
├── TvNodeBinding
├── DirectIrBinding
├── BluetoothHidBinding
├── ConsumerLanBinding
├── CecBinding
└── future NexusBridgeBinding
```

Never create one pseudo-device per transport.

---

## 4. SOFTWARE-FIRST RULE

**PHASE A** must require ONLY:

- Android phone
- television capable of installing our APK where applicable
- existing Wi-Fi / Bluetooth
- built-in phone IR emitter when available

Do **NOT** block current development on:

- ESP32
- external IR receiver
- OEM partnership
- root
- privileged/system signing
- CEC hardware
- custom PCB

Hardware becomes **Phase B** only after software-only capabilities reach their
defined verification gates.

---

## 5. CREATE ELYSIUM NEXUS TV NODE

Create a separate Android TV application: `apps/android-tv-node/`

Do **not** make the mobile app and TV app one giant module.

Share stable domain libraries:

```
shared/
├── nexus-domain/
├── nexus-protocol/
├── nexus-crypto/
├── nexus-identity/
├── nexus-capabilities/
├── nexus-evidence/
└── nexus-serialization/
```

TV Node must support, where platform APIs allow:

- Android TV
- Google TV
- Fire OS / Fire TV through platform-specific adapter
- compatible AOSP TV devices

Never assume: `APK installable` = `all Android TV capabilities available`.

Build capability detection at runtime.

---

## 6. TV NODE ACCESS TIERS

Implement explicit privilege/capability tiers.

**TIER 0 — STANDARD CONSUMER**

Requires only normal app installation.

Capabilities may include:
- device metadata
- secure pairing
- local networking
- our own UI
- known app launch intents
- TV Node state
- IME registration
- our own notifications
- our own service lifecycle

**TIER 1 — USER-GRANTED ENHANCED MODE**

Explicit user activation.

Potential capabilities:
- AccessibilityService
- NotificationListenerService
- Nexus IME
- additional observable state

Never enable silently.

**TIER 2 — BLUETOOTH HID**

Phone becomes Bluetooth HID Device where the platform/TV accepts it.

**TIER 3 — ENGINEERING ADB**

Developer/lab only. Requires user-enabled debugging.
Never part of default retail UX.

**TIER 4 — OEM SYSTEM**

Future optional integration when an OEM explicitly grants system/private/driver
capabilities. The architecture MUST work even if TIER 4 never happens.

---

## 7. CAPABILITY MANIFEST

TV Node must never pretend capabilities exist. Create `CapabilityManifest`.

Each capability has one status:

```
SUPPORTED
USER_PERMISSION_REQUIRED
DEVELOPER_ONLY
OEM_ONLY
UNSUPPORTED
UNVERIFIED
```

Examples:

```
GLOBAL_HOME
GLOBAL_BACK
GLOBAL_DPAD
TEXT_INPUT
APP_LAUNCH
MEDIA_PLAYBACK_CONTROL
MEDIA_STATE
VOLUME_STATE
VOLUME_CONTROL
POWER_OFF
POWER_ON
INPUT_SOURCE
FOREGROUND_APP
IR_ORACLE
BLUETOOTH_HID
OEM_IR_CAPTURE
```

UI is generated from this manifest. If capability is unsupported: do not show
a functional-looking button.

---

## 8. TV IDENTITY PROVIDER

Implement `NexusTvIdentityProvider`.

Collect legitimate available metadata:

- manufacturer
- model
- device/product
- Android API
- OS/platform family
- build fingerprint where appropriate
- supported system features
- display capabilities
- network endpoint
- Bluetooth capability
- audio policy
- installed Nexus Node version

Important: `manufacturer + model` **IS NOT** stable unique physical identity.

Create `ObservationIdentity` until stronger identity evidence exists.

Identity status:

```
UNRESOLVED
RESOLVED
AMBIGUOUS
CONTRADICTED
```

Strong identity may later derive from:
- cryptographic TV Node identity
- paired certificate
- stable platform UUID
- OEM identity
- Nexus Bridge identity

---

## 9. PHONE ↔ TV DISCOVERY

Implement local discovery using Android NSD / DNS-SD.

Advertise: `_elysium-tv._tcp`

Discovery record must contain only non-sensitive bootstrap metadata.
Do not treat IP address as device identity.

Phone should display:
```
"TV Sala — TCL 55..."
"TV Dormitorio — Hisense..."
```
after verified pairing information exists.

Support:
- discovery
- manual IP fallback
- reconnect
- network changes
- router reboot
- TV reboot
- phone reboot

---

## 10. SECURE PAIRING

First launch TV:
- show QR
- short human-readable code

Pairing creates cryptographic identities. Use modern authenticated local channel.

Required properties:

- mutual authentication
- forward-secure session negotiation where practical
- unique device credentials
- Android Keystore
- certificate/public-key pinning
- connection IDs
- sequence numbers
- anti-replay
- session expiration
- peer revocation

- No permanent shared default password.
- No plaintext credential in Room.
- No unauthenticated 0.0.0.0 production control port.

```
Unknown peer:      REJECT
Malformed frame:   REJECT
Replay:            REJECT
EOF during auth:   FAIL CLOSED
```

---

## 11. UNIVERSAL PROTOCOL

Phone must never primarily send Android keycodes.

Send **semantic actions**.

Examples:
```
UniversalAction.Navigate(UP)
UniversalAction.Ok
UniversalAction.Back
UniversalAction.Home
UniversalAction.VolumeUp
UniversalAction.VolumeDown
UniversalAction.Mute
UniversalAction.MediaPlay
UniversalAction.MediaPause
UniversalAction.Search
UniversalAction.TextCommit
UniversalAction.OpenApp(package/capability)
UniversalAction.PowerToggle
```

Protocol envelope:

```
protocolVersion
messageId
connectionId
deviceId
action
timestamp
deadline
sequenceNumber
capabilityContext
auth metadata
```

TV response states:

```
RECEIVED
ACCEPTED
EXECUTED
OBSERVED
FAILED
UNSUPPORTED
PERMISSION_REQUIRED
```

Never collapse all of them into boolean success.

---

## 12. NEXUS ACCESSIBILITY ENHANCED MODE

Implement `NexusAccessibilityService`. Only user-enabled.

Responsibilities:
- observe applicable KeyEvents
- observe focus
- observe current package/context where permitted
- execute supported global actions
- generate semantic observations

On API levels supporting global D-pad actions, map:
```
Navigate.Up
Navigate.Down
Navigate.Left
Navigate.Right
Ok
```
to official global actions. Home/Back similarly where available.

Never use Accessibility to:
- circumvent lock screens
- approve security dialogs
- install software
- change security settings
- perform hidden autonomous behavior
- bypass user control

Every action originates from an explicit authorized Nexus command.

Provide prominent disclosure and explicit activation UX.

Core product must degrade gracefully when Accessibility is disabled.

---

## 13. OEM REMOTE SEMANTIC OBSERVER

When permitted, observe the original TV remote.

Store:
```
timestamp
keyCode
scanCode
InputDevice id
InputDevice descriptor
source
repeatCount
current app/context
```
but label these fields correctly.

- **scanCode** ≠ IR frame.
- **KeyEvent** ≠ physical IR waveform.

This subsystem learns **what action happened**, not **what photons were emitted**.

Create `SemanticRemoteObservation` and NEVER store it as `RawIrSignal`.

---

## 14. NEXUS TV IME

Implement `NexusTvIme : InputMethodService`.

User explicitly selects it. Phone can send:

```
TextCommit
Backspace
DeleteWord
Enter
CursorLeft
CursorRight
SelectAll
ClipboardPaste after explicit action
```

TV Node commits through InputConnection.

Security requirements:
- never log passwords
- never send secure field contents to analytics
- never persist sensitive typed text by default
- mark secure input sessions
- prevent background text injection from untrusted peers

Consumer feature: phone automatically presents a keyboard when the TV reports
an editable field and Nexus IME is active.

---

## 15. BLUETOOTH HID TRANSPORT

Implement mobile `BluetoothTvHidTransport` through supported Android Bluetooth
HID Device APIs.

Descriptor: Keyboard, Consumer Control, optional Mouse.

Start conservative. Test:

```
UP DOWN LEFT RIGHT
ENTER
ESC/BACK candidate
VOLUME_UP VOLUME_DOWN MUTE
PLAY_PAUSE
```

Do not label mappings **VERIFIED** until physical TV tests confirm them.

Build compatibility table:
```
manufacturer | model | OS | HID report | observed result
```

HID route is a transport, not a compatibility assumption.

---

## 16. MEDIA SESSION INTEGRATION

Where legitimate platform APIs and user permission expose active media, observe:

- package
- playing/paused
- metadata if available
- playback position if available
- supported media actions

Prefer semantic MediaController actions over simulated keypresses when the
target app exposes them.

Fallback: TV Node global action → Bluetooth HID → IR, depending on verified
availability.

---

## 17. APP LAUNCHER

Implement real TV application discovery/launch. Use Leanback launch intents
and explicit known packages.

Do NOT claim an app can launch unless an actual launch intent resolves.

Examples:
```
NETFLIX
YOUTUBE
PRIME_VIDEO
DISNEY
SPOTIFY
PLEX
```

Only expose installed/resolvable targets.

Avoid broad package enumeration unless genuinely required.

Do not claim complete Gollo coverage from magic pointer or any vendor
proprietary functionality. Do NOT promise universal `OEM_SPECIAL` equivalence.

---

## 18. TV OBSERVATION ENGINE

Create `TvObservationEngine`.

Adapters:
- `AccessibilityKeyObserver`
- `AccessibilityUiObserver`
- `ForegroundContextObserver`
- `AudioStateObserver`
- `MediaSessionObserver`
- `NexusNodeObserver`

Output: `ObservationEvent`

Examples:
```
KeyObserved(VOLUME_UP)
VolumeChanged(20,21)
ForegroundPackageChanged(...)
FocusChanged(...)
PlaybackChanged(...)
TvNodeReconnect(...)
AppLaunched(...)
```

Observations are facts. Do not infer causality yet.

---

## 19. CAUSAL CORRELATION ENGINE

Create `CausalActionVerifier`.

An action is not confirmed solely because something changed soon afterward.

Use:
- pre-state
- action
- bounded observation window
- expected direction
- reversal/challenge when safe
- repeat trials
- randomized small timing jitter
- exact candidate ID

Example:
```
A = volume 20
IR candidate X VolumeUp
B = volume 21
same codeSet VolumeDown
C = volume 20
```
If repeated consistently: `WIFI_ORACLE_CONFIRMED`.

Do not perform causal tests for dangerous/destructive actions automatically.

---

## 20. SOFTWARE-ONLY IR ORACLE

This is a **CORE** priority.

**Purpose:** use Phone IR + TV Node observation to discover which existing IR
signal actually controls the exact physical television.

**Pipeline:**
```
Exact TV identity metadata
    ↓
candidate narrowing
    ↓
candidate IR transmission
    ↓
TV Node observation
    ↓
causal challenge
    ↓
exact working code set
    ↓
persist evidence
```

Do **NOT** ask user to manually test hundreds of candidates whenever
machine-verifiable state exists.

Create `IrOracleCalibrationEngine`.

Input:
```
DeviceTwin
CandidateQuery
safe actions
TvObservationEngine
IrTransmitter
```

Output: `OracleCalibrationResult`
```
SUCCESS_EXACT
INCONCLUSIVE
NO_OBSERVABLE_STATE
EXHAUSTED
USER_CONFIRMATION_REQUIRED
```

---

## 21. SAFE IR PROBING

Initial autonomous safe set:

```
VOLUME_UP
VOLUME_DOWN
MUTE only if mute state can be observed/reversed reliably
```

**POWER:** never autonomous by default.
**INPUT:** never autonomous without explicit user participation.

For each trial:
- freeze candidate
- freeze action
- record signalId
- record physicalSha
- record catalogBuildId
- record waveformSha
- record carrier
- record transmit result
- record observation

No attribution after cursor advancement.

---

## 22. UNIFY UNIVERSAL + BRAND + MODEL PROBING

There must be **ONE** candidate engine.

`CandidateQuery`:
```
deviceType = TV
brand = optional
model = optional
platform = optional
actions = safe probe set
```

- **Universal:** brand = null, model = null
- **Brand:** brand = Samsung, model = null
- **Exact model:** brand = Samsung, model = UN...

No separate implementation. Never brand-query with `deviceType=""`.
Never arbitrary `LIMIT 200`. Use bounded-memory paging until candidate space
is exhausted.

---

## 23. PROBE CURSOR CONTRACT

Cursor states:
```
UNINITIALIZED
READY
EXHAUSTED
ERROR
```

`initialize()` must produce: `READY` + non-null candidate or `EXHAUSTED`.

`advance()` must atomically return the new current candidate.

Never: launch async mutation then synchronously read old cursor state.

Tests must cross every page boundary.

---

## 24. IR RUNTIME TRUTH

Maintain **ONE** canonical IR protocol registry shared/generated for:
- Python ingestion
- Kotlin runtime
- future firmware

No duplicated names.

Catalog:
- canonical family
- canonical variant

Source-original strings are provenance only.

Every production candidate must resolve:
```
catalog row → RuntimeProtocolBinding → exact codec → exact variant
→ encoder or RAW waveform → valid physical waveform
```

Generate `runtime-executable-report.json` for the EXACT packaged catalog.

Unknown runtime coverage: **0**.

---

## 25. RAW SIGNAL AUTHORITY

When a valid RAW waveform exists: RAW physical representation remains authoritative.

A protocol decode is a derived projection. Never require protocol recognition
for replay if RAW is validated.

Store:
```
RawCaptureSha
NormalizedPhysicalSha
carrier
timings
repeat semantics
toggle semantics
press semantics
```
where available.

---

## 26. CURRENT IR CODECS

Do not promote from unit-test success to commercial use.

Each protocol/variant gets status independently:
```
STRUCTURAL
GOLDEN_VECTOR
ROUNDTRIP
OPTICAL
REAL_DEVICE
HIL
```

**SIRC:** explicit `SIRC_12`, `SIRC_15`, `SIRC_20`. SIRC20 must separately
represent address, subdevice, command. No implicit 13-bit "address" approximation.

**AIWA:** required subdevice must fail if missing. No `subDevice ?: 0`.

**RC5 / RC6 / Kaseikyo:** remain laboratory-only until physical/independent
verification.

---

## 27. PHONE IR HARDWARE DIAGNOSTICS

Create engineering screen: `IR Hardware Diagnostics`

Display:
```
hasIrEmitter
available carrier ranges
requested carrier
actual carrier
waveform length
waveform SHA
transmit result
catalog signal ID
codeSet
protocol
variant
```

Never show `"worked"` from transmit result alone.

Use **TRANSMIT_ACCEPTED** until physical reaction is observed.

---

## 28. ROUTE PLANNER

Per `UniversalAction`, score routes independently.

Potential routes:
```
TV_NODE_NATIVE
TV_NODE_ACCESSIBILITY
TV_NODE_MEDIA_SESSION
BLUETOOTH_HID
DIRECT_IR
CONSUMER_LAN
NEXUS_BRIDGE
CEC
ADB_ENGINEERING
```

Score dimensions:
```
availability
verification level
expected latency
recent success
confidence
security/trust
battery/cost
state observability
```

Different actions may use different routes. Example:
```
Navigation:  TV_NODE
Volume:      IR
Text:        NEXUS_IME
Playback:    MEDIA_SESSION
Power:       IR
```

This is correct. Do not force one transport for entire device.

---

## 29. ADB RECLASSIFICATION

Keep ADB implementation. Rename product meaning: **ADB_ENGINEERING**.

It is an engineering/laboratory transport. Do not advertise it as zero-setup
consumer control.

Capabilities exposed by ADB adapter must be exact:

- Do not claim `HDMI_CEC` if no CEC implementation exists.
- Do not claim `InputSource` if `execute()` cannot perform InputSource.
- Do not claim `readable=true` when `readState()` returns null.
- `pair()` must perform or represent real pairing state, not unconditional success.

No internal fake capability declaration.

---

## 30. BACKGROUND / PROCESS DEATH

Design around Android lifecycle reality. Do not assume daemon permanence.

Persist durable state:
```
paired peers
DeviceTwin
capabilities
current profile
calibration session
last exact candidate
evidence
```

On restart: restore exact identities. Never restore by approximate index after
catalog changes.

State machine:
```
OFFLINE
DISCOVERABLE
PAIRING
CONNECTED
ACTIVE
DEGRADED
RECONNECTING
```

Process death is a required test.

---

## 31. DEVICE TWIN

Create authoritative `DeviceTwin`.

Fields:
```
physicalDeviceId
identityStatus
manufacturer
model
platform
firmwareFamily
room
capabilities
protocolBindings[]
lastObservedState
trust
evidence summary
```

ProtocolBinding examples:
```
TvNodeBinding
IrBinding
BluetoothHidBinding
LanBinding
BridgeBinding
CecBinding
```

Never merge devices based solely on `same manufacturer + same model`.
Two identical TVs in the same house remain distinct devices.

---

## 32. EVIDENCE LEDGER

All important physical/control events produce immutable evidence.

Evidence classes:
```
SOURCE_IMPORTED
STRUCTURALLY_VALID
RUNTIME_EXECUTABLE
ANDROID_TX_ACCEPTED
USER_CONFIRMED
TV_NODE_OBSERVED
WIFI_ORACLE_CONFIRMED
MULTI_SESSION_CONFIRMED
EXTERNAL_CAPTURE_VERIFIED
HIL_VERIFIED
REAL_DEVICE_VERIFIED
RETAIL_MATRIX_VERIFIED
```

Verification status is **derived** from evidence. Never manually set a stronger
state than the evidence supports.

---

## 33. SOFTWARE-ONLY SUCCESS GATE

Before buying/custom-building IR capture hardware, demonstrate with only
phone + TVs:

- TV Node installs
- Discovery works
- Pairing secure
- Reconnect works
- Nexus IME works
- Accessibility mode works where supported
- Bluetooth HID works on at least one real TV
- Phone IR works
- TvObservationEngine works
- IR Oracle automatically identifies at least one correct code set
- Exact profile survives process death
- Same DeviceTwin survives TV/router/phone reboot
- No silent failures

Then expand to: 3 TVs → 5 TVs → 10 TVs from different vendor/platform families.

Only then begin hardware acquisition phase.

---

## 34. PHASE B — NEXUS BRIDGE

Only after Phase A software gate passes.

**Purpose:**
- remove dependence on phone IR emitter
- gain physical IR reception
- gain better transmission
- eventually add CEC

Prototype hardware:
```
ESP32-S3 class MCU
IR demodulated receiver
wide-band/raw optical receiver
high-power IR LED driver
BLE
USB-C
optional Wi-Fi
local flash
```

Do **NOT** start with custom PCB. Start with development modules and breadboard.

---

## 35. PHYSICAL IR ACQUISITION

With Nexus Bridge:

```
Original Remote
    ↓
IR receivers
    ↓
measured carrier, raw timings, repeat structure, toggle behavior
    ↓
PhysicalSignalAcquisitionEngine
    ↓
semantic correlation with TV Node
    ↓
replay
    ↓
TV observation
    ↓
evidence
```

This replaces endless Internet searching. External IR databases become:

```
BOOTSTRAP
FALLBACK
REFERENCE
```

...not permanent source of truth.

---

## 36. CAPTURE PROTOCOL

Controller coordinates:

```
CaptureSession
CaptureToken
```

---

## 37. EVIDENCE-BASED PROTOCOL REGISTRY

When a raw capture is confirmed by causal TV Node observation and independent
replay: promote from `BOOTSTRAP` to `PHYSICALLY_CONFIRMED`.

The protocol registry has exactly three tiers:

```
BOOTSTRAP        — Imported from external DB, structurally valid, no physical evidence
PHYSICALLY_CONFIRMED — Captured from real remote, replayed, TV confirmed
HIL_CONFIRMED    — Dual-path independent verification
```

No promotion without evidence. A codec decode of a waveform does not change
the evidence tier.

---

## 38. WAVEFORM INTEGRITY

Store raw waveforms with:
- capture timestamp
- capture device
- measured carrier frequency
- raw timing array or modulated sample
- reference to original remote model where available
- reference to TV model
- TV Node observation bundle

On replay failure (TV does not react despite TX_OK):
- record failure
- do NOT silently delete or alter the stored waveform
- increment regression counter for that signal
- trigger recalibration

---

## 39. CATALOG BUILD

Catalog build produces:
```
runtime-executable-report.json
catalog-build-id
source-lock-SHA
protocol-registry-SHA
unknown-coverage = 0
```

Every build must verify:
- All source signals resolve to a protocol binding
- All protocol bindings resolve to a codec or RAW path
- No experimental codec in production path
- No unresolved carrier
- No duplicate canonical signal IDs

CI must enforce these checks. Catalog build fails if any check fails.

---

## 40. CATALOG VERSION UPGRADE

Schema upgrades must:
- verify all existing evidence rows migrate losslessly
- preserve existing verified bindings by canonical ID
- never re-assign canonical IDs
- produce a migration report
- flag any orphaned or unmapped evidence

Never break a verified TV profile during catalog upgrade.

---

## 41. REALITY LEDGER

Maintain a single file: `docs/architecture/REALITY_LEDGER.md`

Format per entry:
```
| Date       | Area       | Claim                | Evidence                     | Maturity Before | Maturity After | Verdict       |
```

Every session updates the ledger. Never say "worked on X" without updating
the ledger.

---

## 42. THIRD-PARTY IR DATA LICENSING

Every source in the catalog must have a recorded license status:

- file or DB row
- source URL or reference
- original author
- license type
- allowed use (research / personal / commercial / restricted)
- attribution requirements
- redistribution allowed?

Maintain: `ir-data/license-policy.yaml`.

If a source has no clear license: mark `UNCLEAR`. Do not distribute `UNCLEAR`
sources in a commercial build without legal review.

---

## 43. SOURCE LOCKS

Every external IR data ingestion freezes a `sources.lock.json`. Fields per source:

```
sourceId
url
importedAt
originalFileSha256
recordCount
protocols
catalogBuildId
licenseStatus
```

On catalog rebuild: verify lock hashes. Mismatch = build failure.

---

## 44. BRAND / MODEL ALIASES

Do not duplicate brand names.

Use aliases table:

```
canonical      alias
Samsung        Samsung Electronics, SAMSUNG, samsung, SUMSANG
LG             LG Electronics, L.G., 엘지
```

Model aliases follow same pattern. No silent canonical mismatch.

---

## 45. COMPATIBILITY CERTIFICATES

Create signed `RetailCompatibilityCertificate`.

Fields:
```
certificateId
retailer
retailerSku
GTIN
manufacturer
exactModel
firmwareFamily
originalRemoteModel
hardwareRevision
appVersion
catalogBuildId
firmwareBuild
perActionStatus
verificationMethods
evidenceHashes
verifiedAt
expiresAt
regressionState
signature
```

No boolean `works=true`. Compatibility is **action-level**.

---

## 46. RETAIL COVERAGE ENGINE

Calculate:
```
activeSkuCount
knownSkuCount
coreVerifiedCount
extendedVerifiedCount
fullVerifiedCount
pendingCount
regressionCount
```

Allow `100% CORE VERIFIED` only when:
```
coreVerifiedCount == activeSkuCount
AND pendingCount == 0
AND regressionCount == 0
```

No marketing override.

---

## 47. FIRST COMMERCIAL MATRIX

Pilot **one retailer first**.

Recommended sequence:
```
MONGE → EL VERDUGO → GOLLO
```

Reason: solve one closed inventory completely before expanding.

For every unique exact TV model:
1. install TV Node where possible
2. discover software capabilities
3. IR-oracle calibrate existing codes
4. capture physical IR later where necessary
5. verify CORE
6. store evidence
7. issue certificate

Reuse evidence between retailers only when exact hardware/region/firmware
compatibility is legitimately equivalent. Never reuse because: same brand,
same size, similar model name.

---

## 48. RETAIL EMPLOYEE UX

Create **Retail Setup Mode**.

```
Employee:
scan/select retailer SKU
    ↓
exact model found
    ↓
verified profile selected
    ↓
provision Nexus
    ↓
one physical validation
    ↓
ready
```

Target known-model setup: **<20 seconds** (only after measured).

---

## 49. CONSUMER UX

Customer should never see:
- codec
- carrier
- protocol IDs
- SQLite
- signal hashes

Normal flow:
```
Detect TV
    ↓
Pair
    ↓
Capabilities found
    ↓
Control ready
```

If IR calibration needed: `"Vamos a calibrar el control"` — not `"Test codeSet 183"`.

Advanced engineering diagnostics remain available separately.

---

## 50. SELF-HEALING

Failure of one binding does not invalidate whole device.

Example: `HOME` stops working. Mark `HOME = REGRESSION`. Keep `POWER`, `VOL`,
`MUTE`, `NAV` working. Recalibrate HOME only.

RoutePlanner may switch: `TV_NODE → HID → IR` without creating another
pseudo-device.

---

## 51. SECURITY

Zero-trust principles. Every command has:

- authenticated origin
- target identity
- required trust
- permission evaluation
- audit event

Credential storage: Android Keystore.
Encryption: modern AEAD.

- No hardcoded master production passwords.
- No debug production signing.
- No fail-open pairing.
- No unlimited unbounded transport queues.
- No silent peer merge.
- No unauthenticated capture listener.

---

## 52. PRIVACY

Local-first. Core remote must not require cloud.

- Sensitive telemetry off by default.
- Do not upload: typed passwords, private text, screen contents,
  Wi-Fi credentials, device identifiers, without explicit reason/consent.
- Community evidence upload: opt-in, minimized, pseudonymized where possible.
- Physical signal corpus does not require personal identity.

---

## 53. ANDROID RELEASE TOOLCHAIN

Maintain current supported target requirements. Use toolchain officially
supporting compile/target SDK.

Release build must fail if:
- keystore missing
- password missing
- alias missing
- signing configuration invalid

No fallback release password in source.

CI must install exact target SDK/build tools.

Generate:
```
APK/AAB
Git SHA
catalogBuildId
SBOM
artifact SHA-256
provenance report
```

---

## 54. TEST PYRAMID

```
UNIT:              protocol encoders, decoders, candidate ranking, identity,
                   capabilities, route scoring, crypto, serialization

INTEGRATION:       real packaged catalog, SQLite, TV Node protocol, IME,
                   Accessibility adapter, HID adapter, process recovery

ON DEVICE:         phone, TV Node, IR transmitter, Bluetooth

REAL DEVICE:       actual TV behavior

HIL:               independent physical receiver

MATRIX:            multiple brands/models/firmwares

RETAIL:            exact active SKUs
```

No level substitutes for the next.

---

## 55. SOFTWARE-ONLY DEVICE MATRIX

Begin immediately with real TVs available. Capture:
```
brand
exact model
OS
Android API
firmware
TV Node installability
Accessibility capability
IME
Bluetooth HID
phone IR
```

---

## 56. PR SEQUENCE

Do not combine all into one giant unreviewable PR.

**PR1 — Nexus Domain + Identity + Crypto**
- shared/nexus-domain/
- shared/nexus-identity/
- shared/nexus-crypto/
- core data model

**PR2 — Universal Protocol**
- shared/nexus-protocol/
- envelope, actions, responses
- serialization

**PR3 — TV Node Baseline**
- apps/android-tv-node/
- TIER 0 core
- capability manifest
- NSD discovery advertisement

**PR4 — Discovery + Pairing**
- phone NSD client
- manual IP fallback
- QR code pair
- cryptographic identity exchange
- reconnect/recovery

**PR5 — Nexus TV IME**
- IME service
- phone keyboard forwarding
- secure field handling

**PR6 — Accessibility Enhanced Mode**
- TIER 1 adapter
- semantic observation → UniversalAction

**PR7 — Bluetooth HID Transport**
- BluetoothTvHidTransport
- conservative report descriptor
- TV compatibility table

**PR8 — Software-Only IR Oracle**
- TvObservationEngine
- CausalActionVerifier
- IrOracleCalibrationEngine
- safe probing + cursor contract

**PR9 — Route Planner + DeviceTwin**
- RoutePlanner with multi-route scoring
- DeviceTwin persistence
- process death recovery

**PR10 — Retail Data Graph**
- retailers/SKUs/models/certificates

**PR11 — Nexus Bridge Prototype**
- only after software gate

**PR12 — Physical Acquisition**
- IR RX/replay/evidence

**PR13 — HIL**
- independent capture

**PR14 — Retail Matrix Pilot**

---

## 57. REALITY LEDGER MAINTENANCE

Every phase update must append to `REALITY_LEDGER.md`. Never overwrite.
Every entry must state the exact **maturity level before** and **after**,
and the **evidence** that justifies the promotion.

---

## 58. REGRESSION TRACKING

Every verified signal has a regression state:
```
STABLE
REGRESSION_SUSPECTED
REGRESSION_CONFIRMED
RECALIBRATED
```

A regression means the TV no longer reacts to a previously verified signal.
This is different from `TX_FAILED` (transmitter problem).

---

## 59. SUPPLY CHAIN NOTICES

Generate notices for every third-party IR source used in a build.
File: `THIRD_PARTY_IR_DATA_NOTICES.md` at repository root.

Content per source:
- source name and URL
- original author
- license
- modifications made
- inclusion in current catalog build

---

## 60. DEVICE MATRIX FORMAT

Standard format per device:

```yaml
manufacturer: Samsung
model: UN55CU7000FXZA
platform: Tizen
firmware: 2.5.1
year: 2024
retailer: Gollo
sku: GLL-12345
tvNode:
  installable: true
  tier: TIER_0
  capabilities: [GLOBAL_HOME, GLOBAL_BACK, VOLUME_CONTROL, APP_LAUNCH]
accessibility:
  available: true
  globalActions: [DPAD, HOME, BACK]
ime:
  available: true
bluetoothHid:
  available: true
  testedActions: [VOLUME_UP, VOLUME_DOWN, NAVIGATION]
phoneIr:
  available: true
  verifiedCodeSet: NEC_1
  verifiedSignals: [VOLUME_UP, VOLUME_DOWN, POWER, MUTE]
evidence:
  - type: TV_NODE_OBSERVED
    date: 2026-08-01
  - type: WIFI_ORACLE_CONFIRMED
    date: 2026-08-01
  - type: REAL_DEVICE_VERIFIED
    date: 2026-08-05
```

---

## 61. CRYPTO / IDENTITY SUMMARY

Summary table for the identity/crypto subsystem:

| Primitive             | Algorithm                   | Purpose                          |
|-----------------------|-----------------------------|----------------------------------|
| Ephemeral DH          | X25519                      | Session key agreement            |
| AEAD                  | AES-256-GCM (or XChaCha20) | Payload encryption               |
| Device key            | Ed25519                     | Device identity signing          |
| Cert chain            | X.509-like (compact)        | Peer identity verification       |
| Key storage           | Android Keystore            | Credential protection            |
| Session token         | 256-bit random + seq counter| Anti-replay                      |
| Pairing code          | 6 alphanumeric + QR         | Bootstrap trust in first meeting |

---

## 62. REQUIRED DELIVERABLE FOR EVERY PHASE

Every agent must finish a phase with:

```
WHAT CHANGED
WHY
FILES CHANGED
ARCHITECTURE IMPACT
TESTS ADDED
TEST RESULTS
REAL-DEVICE TEST RESULT
KNOWN LIMITATIONS
SECURITY IMPACT
EVIDENCE GENERATED
MATURITY BEFORE
MATURITY AFTER
NEXT BLOCKER
```

Never simply say: `"Implemented successfully."`

---

## 63. SOFTWARE-FIRST ACCEPTANCE TEST

Final Phase-A demonstration:

1. Fresh TV Node install.
2. Phone discovers TV.
3. TV displays pairing code.
4. Phone pairs securely.
5. Kill phone app. Restart. Reconnect exact same DeviceTwin.
6. TV remote key observed.
7. Phone D-pad controls TV through TV Node.
8. Phone keyboard types into TV through Nexus IME.
9. Bluetooth HID controls supported TV.
10. Phone IR sends VolumeUp candidate.
11. TV Node detects actual state/action change.
12. Oracle performs inverse challenge.
13. Exact IR signal is confirmed.
14. Persist: deviceId, codeSetId, signal IDs, physical SHAs, catalogBuildId,
    verification evidence.
15. Force-stop apps. Restart phone + TV. Profile remains.
16. Disconnect Wi-Fi. Verified IR still controls TV.
17. Restore Wi-Fi. Route planner automatically returns to preferred route.
18. No manual profile recreation.

This is the **software-only golden vertical**.

---

## 64. PHYSICAL GOLDEN VERTICAL — FUTURE

After Nexus Bridge exists:

```
OEM remote
→ Bridge capture
→ measured carrier
→ raw waveform
→ TV Node semantic observation
→ correlation
→ replay from Bridge
→ real TV reaction
→ independent receiver
→ evidence
→ exact profile
```

No Internet database involved. This becomes the ultimate IR acquisition loop.

---

## 65. COMMERCIAL GOLDEN VERTICAL

```
Retail SKU
→ exact model
→ exact TV identity
→ exact original remote
→ software capabilities
→ IR calibration/capture
→ per-action verification
→ evidence
→ signed certificate
→ retailer provisioning
→ customer setup
→ offline operation
→ update/revalidation
```

Only then: `RETAIL_MATRIX_VERIFIED`.

---

## 66. FINAL RELEASE TRUTH GATE

**NO PRODUCTION_APPROVED** until all applicable gates are true:

1. No silent control failures.
2. No fake capabilities.
3. No fake protocol fallback.
4. No experimental codec in commercial path.
5. Exact TV identity handled safely.
6. Multi-route DeviceTwin stable.
7. Secure pairing.
8. Replay protection.
9. Credentials protected.
10. Accessibility is explicit/user-authorized.
11. ADB is developer-only.
12. IME handles sensitive fields safely.
13. HID physically tested.
14. IR runtime contract clean.
15. IR oracle verified on multiple real TVs.
16. Profiles survive process death.
17. Catalog upgrades preserve verified bindings safely.
18. Release secrets absent from source.
19. Release signing permanent.
20. CI release gates green.
21. Real TV matrix exists.
22. Physical evidence exists where claimed.
23. Licensing audit complete.
24. Costa Rica regulatory requirements resolved where applicable.
25. Warranty/RMA/support defined.
26. Retail inventory snapshot authoritative.
27. Every claimed active SKU mapped.
28. Every CORE-compatible SKU physically verified.
29. Unknown retail SKU count = 0 for any 100% claim.
30. Open CORE regression count = 0.

---

## 67. ULTIMATE ARCHITECTURAL PRINCIPLE

The objective is **NOT**: "make one protocol control everything."

The objective is: **"make every authorized intent reach the correct physical
device through the strongest currently available verified route."**

Therefore:
- Software when software is strongest.
- IR when IR is strongest.
- Bluetooth when Bluetooth is strongest.
- LAN when LAN is legitimate and reliable.
- CEC when CEC hardware exists.
- Nexus Bridge when a physical endpoint is needed.
- OEM integration when legitimately available.

All routes converge on:
```
UniversalAction + DeviceTwin + Evidence
```

---

## 68. FINAL PRODUCT DEFINITION

Elysium Nexus shall become:

> A local-first distributed universal control fabric in which a phone,
> a cooperative TV Node and optional future Nexus hardware work together
> to identify the exact physical device, discover its capabilities,
> choose the best verified transport for each action, execute the action,
> observe the outcome, confirm the result, learn from it, and self-heal
> when routes degrade.

The fundamental cycle:

```
User Intent
    ↓
UniversalAction
    ↓
Best Verified Route
    ↓
TV Node
    ↓
confirms resultado
```

**NO CLAIM WITHOUT PROOF.**

**SKU exacto → TV exacto → funciones exactas → rutas exactas
→ evidencia exacta → certificado exacto.**

When we can do that over the complete, dated inventory of one retail chain,
we have a serious basis to say **"100% CORE VERIFIED"** without converting
marketing into an assumption.