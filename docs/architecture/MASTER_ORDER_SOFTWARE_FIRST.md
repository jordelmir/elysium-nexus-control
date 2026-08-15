# MASTER ORDER — SOFTWARE-FIRST UNIVERSAL TV FABRIC (Consolidated)

> **This document is the governing implementation order for Elysium Nexus.**
> It supersedes the previous MASTER_ORDER.md sections (kept below as legacy
> reference) per the consolidated directive of 2026-08-15.
>
> Codename: **ELYSiUM NEXUS — SOFTWARE-FIRST UNIVERSAL TV FABRIC /
> AUTONOMOUS IR ORACLE / RETAIL TRUTH / ZERO FAKE COMPATIBILITY**
>
> Reconciliation note: the transformed product model is —
> *Elysium Nexus is no longer "an APK with many controls"; it is a
> distributed control platform: phone + TV Node + IR + Bluetooth + LAN +
> evidence; first 100% software with what exists today, then proprietary
> hardware ONLY where it solves physically impossible software gaps.*

```text
══════════════════════════════════════════════════════════════════════════════
                         ELYSIUM NEXUS
                    MASTER IMPLEMENTATION ORDER

       SOFTWARE-FIRST UNIVERSAL TV FABRIC
     AUTONOMOUS IR ORACLE · RETAIL TRUTH · ZERO FAKE COMPATIBILITY
══════════════════════════════════════════════════════════════════════════════

ROLE

Act as principal software architect, Android platform engineer,
embedded-systems architect, distributed-systems engineer,
security engineer, protocol engineer, QA/HIL architect,
data engineer and release engineer for Elysium Nexus.

Do not optimize for feature count.

Optimize for:

1. real physical usefulness;
2. deterministic behavior;
3. compatibility evidence;
4. minimum user setup;
5. local-first operation;
6. low latency;
7. security;
8. resilience;
9. explainability;
10. commercial readiness.

The system is intended ultimately to become a commercially sellable
universal-control platform for retailers such as:

- Gollo Costa Rica
- Tienda Monge
- El Verdugo

but compatibility claims MUST be earned model-by-model and action-by-action.

══════════════════════════════════════════════════════════════════════════════
0. ABSOLUTE PRODUCT CONSTITUTION
══════════════════════════════════════════════════════════════════════════════

These rules are non-negotiable.

NO MOCK AS PRODUCTION.
NO FAKE DEVICE SUPPORT.
NO TEMPLATE AS PHYSICAL TRUTH.
NO GUESSED IR COMMAND.
NO AI-GENERATED PRODUCTION IR SIGNAL.
NO SILENT PROTOCOL FALLBACK.
NO SILENT TRANSMISSION FAILURE.
NO "ALL TVs" CLAIM WITHOUT EVIDENCE.
NO ROOT ASSUMPTION.
NO OEM PRIVILEGE ASSUMPTION.
NO PRIVATE VENDOR API ASSUMPTION.
NO ACCESSIBILITY ABUSE.
NO ADB AS DEFAULT RETAIL CONTROL.
NO HARDCODED RELEASE SECRETS.
NO MANUFACTURER+MODEL AS UNIQUE PHYSICAL DEVICE ID.
NO INTERNET REQUIREMENT FOR CORE REMOTE CONTROL.

An unknown value remains UNKNOWN.

UNKNOWN must never silently become:

0
38 kHz
NEC
Samsung
generic
supported
verified
or compatible.

══════════════════════════════════════════════════════════════════════════════
1. REALITY / MATURITY MODEL
══════════════════════════════════════════════════════════════════════════════

Every capability must have exactly one defensible maturity level:

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

Never promote from:

"test passed"

directly to:

"works on TVs".

TX_OK means:

the transmitting API accepted the waveform.

TX_OK does NOT mean:

the intended TV reacted.

Likewise:

KeyEvent observed
≠
raw IR captured.

Database entry
≠
physically working signal.

User confirmation
≠
retail-certified compatibility.

══════════════════════════════════════════════════════════════════════════════
2. EXECUTION BASELINE
══════════════════════════════════════════════════════════════════════════════

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

Create a feature branch.

Never perform large experimental changes directly on main.

Before modifying architecture, record:

CurrentGitSha
CurrentCatalogBuildId
CurrentAppVersion
CurrentSchemaVersion
CurrentProtocolRegistryVersion
CurrentTestCount
CurrentKnownRegressions

══════════════════════════════════════════════════════════════════════════════
3. FINAL PRODUCT MODEL
══════════════════════════════════════════════════════════════════════════════

Elysium Nexus is NOT:

"a universal remote Android app."

Elysium Nexus IS:

a Universal Intent Fabric that converts an authorized human intent
into the correct physical or software action on the correct device
through the best verified route available.

Canonical pipeline:

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

One physical television has ONE DeviceTwin.

Example:

TV Samsung X
├── TvNodeBinding
├── DirectIrBinding
├── BluetoothHidBinding
├── ConsumerLanBinding
├── CecBinding
└── future NexusBridgeBinding

Never create one pseudo-device per transport.

══════════════════════════════════════════════════════════════════════════════
4. SOFTWARE-FIRST RULE
══════════════════════════════════════════════════════════════════════════════

PHASE A must require ONLY:

- Android phone;
- television capable of installing our APK where applicable;
- existing Wi-Fi/Bluetooth;
- built-in phone IR emitter when available.

Do NOT block current development on:

- ESP32;
- external IR receiver;
- OEM partnership;
- root;
- privileged/system signing;
- CEC hardware;
- custom PCB.

Hardware becomes Phase B only after software-only capabilities
reach their defined verification gates.

══════════════════════════════════════════════════════════════════════════════
5. CREATE ELYSIUM NEXUS TV NODE
══════════════════════════════════════════════════════════════════════════════

Create a separate Android TV application:

apps/android-tv-node/

Do not make the mobile app and TV app one giant module.

Share stable domain libraries:

shared/
├── nexus-domain/
├── nexus-protocol/
├── nexus-crypto/
├── nexus-identity/
├── nexus-capabilities/
├── nexus-evidence/
└── nexus-serialization/

TV Node must support, where platform APIs allow:

Android TV
Google TV
Fire OS / Fire TV through platform-specific adapter
compatible AOSP TV devices.

Never assume:

APK installable
=
all Android TV capabilities available.

Build capability detection at runtime.

══════════════════════════════════════════════════════════════════════════════
6. TV NODE ACCESS TIERS
══════════════════════════════════════════════════════════════════════════════

Implement explicit privilege/capability tiers.

TIER 0 — STANDARD CONSUMER

Requires only normal app installation.

Capabilities may include:

device metadata
secure pairing
local networking
our own UI
known app launch intents
TV Node state
IME registration
our own notifications
our own service lifecycle.

TIER 1 — USER-GRANTED ENHANCED MODE

Explicit user activation.

Potential capabilities:

AccessibilityService
NotificationListenerService
Nexus IME
additional observable state.

Never enable silently.

TIER 2 — BLUETOOTH HID

Phone becomes Bluetooth HID Device where the platform/TV accepts it.

TIER 3 — ENGINEERING ADB

Developer/lab only.

Requires user-enabled debugging.

Never part of default retail UX.

TIER 4 — OEM SYSTEM

Future optional integration when an OEM explicitly grants
system/private/driver capabilities.

The architecture MUST work even if TIER 4 never happens.

══════════════════════════════════════════════════════════════════════════════
7. CAPABILITY MANIFEST
══════════════════════════════════════════════════════════════════════════════

TV Node must never pretend capabilities exist.

Create:

CapabilityManifest

Each capability:

SUPPORTED
USER_PERMISSION_REQUIRED
DEVELOPER_ONLY
OEM_ONLY
UNSUPPORTED
UNVERIFIED

Examples:

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

UI is generated from this manifest.

If capability is unsupported:

do not show a functional-looking button.

══════════════════════════════════════════════════════════════════════════════
8. TV IDENTITY PROVIDER
══════════════════════════════════════════════════════════════════════════════

Implement:

NexusTvIdentityProvider

Collect legitimate available metadata:

manufacturer
model
device/product
Android API
OS/platform family
build fingerprint where appropriate
supported system features
display capabilities
network endpoint
Bluetooth capability
audio policy
installed Nexus Node version.

Important:

manufacturer + model
IS NOT
stable unique physical identity.

Create:

ObservationIdentity

until stronger identity evidence exists.

IdentityStatus:

UNRESOLVED
RESOLVED
AMBIGUOUS
CONTRADICTED

Strong identity may later derive from:

cryptographic TV Node identity
paired certificate
stable platform UUID
OEM identity
Nexus Bridge identity.

══════════════════════════════════════════════════════════════════════════════
9. PHONE ↔ TV DISCOVERY
══════════════════════════════════════════════════════════════════════════════

Implement local discovery using Android NSD / DNS-SD.

Advertise:

_elysium-tv._tcp

Discovery record must contain only non-sensitive bootstrap metadata.

Do not treat IP address as device identity.

Phone should display:

"TV Sala — TCL 55..."
"TV Dormitorio — Hisense..."

after verified pairing information exists.

Support:

discovery
manual IP fallback
reconnect
network changes
router reboot
TV reboot
phone reboot.

══════════════════════════════════════════════════════════════════════════════
10. SECURE PAIRING
══════════════════════════════════════════════════════════════════════════════

First launch TV:

show QR
+
short human-readable code.

Pairing creates cryptographic identities.

Use modern authenticated local channel.

Required properties:

mutual authentication
forward-secure session negotiation where practical
unique device credentials
Android Keystore
certificate/public-key pinning
connection IDs
sequence numbers
anti-replay
session expiration
peer revocation.

No permanent shared default password.

No plaintext credential in Room.

No unauthenticated 0.0.0.0 production control port.

Unknown peer:

REJECT.

Malformed frame:

REJECT.

Replay:

REJECT.

EOF during authentication:

FAIL CLOSED.

══════════════════════════════════════════════════════════════════════════════
11. UNIVERSAL PROTOCOL
══════════════════════════════════════════════════════════════════════════════

Phone must never primarily send Android keycodes.

Send semantic actions.

Examples:

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

Protocol envelope:

protocolVersion
messageId
connectionId
deviceId
action
timestamp
deadline
sequenceNumber
capabilityContext
auth metadata.

TV response states:

RECEIVED
ACCEPTED
EXECUTED
OBSERVED
FAILED
UNSUPPORTED
PERMISSION_REQUIRED

Never collapse all of them into boolean success.

══════════════════════════════════════════════════════════════════════════════
12. NEXUS ACCESSIBILITY ENHANCED MODE
══════════════════════════════════════════════════════════════════════════════

Implement:

NexusAccessibilityService

Only user-enabled.

Responsibilities:

observe applicable KeyEvents
observe focus
observe current package/context where permitted
execute supported global actions
generate semantic observations.

On API levels supporting global D-pad actions:

map:

Navigate.Up
Navigate.Down
Navigate.Left
Navigate.Right
Ok

to official global actions.

Home/Back similarly where available.

Never use Accessibility to:

circumvent lock screens
approve security dialogs
install software
change security settings
perform hidden autonomous behavior
or bypass user control.

Every action originates from an explicit authorized Nexus command.

Provide prominent disclosure and explicit activation UX.

Core product must degrade gracefully when Accessibility is disabled.

══════════════════════════════════════════════════════════════════════════════
13. OEM REMOTE SEMANTIC OBSERVER
══════════════════════════════════════════════════════════════════════════════

When permitted, observe the original TV remote.

Store:

timestamp
keyCode
scanCode
InputDevice id
InputDevice descriptor
source
repeatCount
current app/context

but label these fields correctly.

scanCode
≠
IR frame.

KeyEvent
≠
physical IR waveform.

This subsystem learns:

what action happened,

not:

what photons were emitted.

Create:

SemanticRemoteObservation

and NEVER store it as RawIrSignal.

══════════════════════════════════════════════════════════════════════════════
14. NEXUS TV IME
══════════════════════════════════════════════════════════════════════════════

Implement:

NexusTvIme : InputMethodService

User explicitly selects it.

Phone can send:

TextCommit
Backspace
DeleteWord
Enter
CursorLeft
CursorRight
SelectAll
ClipboardPaste after explicit action.

TV Node commits through InputConnection.

Security requirements:

never log passwords
never send secure field contents to analytics
never persist sensitive typed text by default
mark secure input sessions
prevent background text injection from untrusted peers.

Consumer feature:

phone automatically presents a keyboard when the TV reports
an editable field and Nexus IME is active.

══════════════════════════════════════════════════════════════════════════════
15. BLUETOOTH HID TRANSPORT
══════════════════════════════════════════════════════════════════════════════

Implement mobile:

BluetoothTvHidTransport

through supported Android Bluetooth HID Device APIs.

Descriptor:

Keyboard
Consumer Control
optional Mouse

Start conservative.

Test:

UP
DOWN
LEFT
RIGHT
ENTER
ESC/BACK candidate
VOLUME_UP
VOLUME_DOWN
MUTE
PLAY_PAUSE

Do not label mappings VERIFIED until physical TV tests confirm them.

Build compatibility table:

manufacturer
model
OS
HID report
observed result.

HID route is a transport,
not a compatibility assumption.

══════════════════════════════════════════════════════════════════════════════
16. MEDIA SESSION INTEGRATION
══════════════════════════════════════════════════════════════════════════════

Where legitimate platform APIs and user permission expose active media:

observe:

package
playing/paused
metadata if available
playback position if available
supported media actions.

Prefer semantic MediaController actions over simulated keypresses
when the target app exposes them.

Fallback:

TV Node global action
→ Bluetooth HID
→ IR

depending on verified availability.

══════════════════════════════════════════════════════════════════════════════
17. APP LAUNCHER
══════════════════════════════════════════════════════════════════════════════

Implement real TV application discovery/launch.

Use Leanback launch intents and explicit known packages.

Do NOT claim an app can launch unless an actual launch intent resolves.

Example capabilities:

NETFLIX
YOUTUBE
PRIME_VIDEO
DISNEY
SPOTIFY
PLEX

Only expose installed/resolvable targets.

Avoid broad package enumeration unless genuinely required.

══════════════════════════════════════════════════════════════════════════════
18. TV OBSERVATION ENGINE
══════════════════════════════════════════════════════════════════════════════

Create:

TvObservationEngine

Adapters:

AccessibilityKeyObserver
AccessibilityUiObserver
ForegroundContextObserver
AudioStateObserver
MediaSessionObserver
NexusNodeObserver

Output:

ObservationEvent

Examples:

KeyObserved(VOLUME_UP)
VolumeChanged(20,21)
ForegroundPackageChanged(...)
FocusChanged(...)
PlaybackChanged(...)
TvNodeReconnect(...)
AppLaunched(...)

Observations are facts.

Do not infer causality yet.

══════════════════════════════════════════════════════════════════════════════
19. CAUSAL CORRELATION ENGINE
══════════════════════════════════════════════════════════════════════════════

Create:

CausalActionVerifier

An action is not confirmed solely because something changed soon afterward.

Use:

pre-state
action
bounded observation window
expected direction
reversal/challenge when safe
repeat trials
randomized small timing jitter
exact candidate ID.

Example:

A = volume 20

IR candidate X VolumeUp

B = volume 21

same codeSet VolumeDown

C = volume 20

If repeated consistently:

WIFI_ORACLE_CONFIRMED

Do not perform causal tests for dangerous/destructive actions automatically.

══════════════════════════════════════════════════════════════════════════════
20. SOFTWARE-ONLY IR ORACLE
══════════════════════════════════════════════════════════════════════════════

This is a CORE priority.

Purpose:

use Phone IR + TV Node observation to discover which existing IR signal
actually controls the exact physical television.

Pipeline:

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
persist evidence.

Do NOT ask user to manually test hundreds of candidates whenever
machine-verifiable state exists.

Create:

IrOracleCalibrationEngine

Input:

DeviceTwin
CandidateQuery
safe actions
TvObservationEngine
IrTransmitter

Output:

OracleCalibrationResult

SUCCESS_EXACT
INCONCLUSIVE
NO_OBSERVABLE_STATE
EXHAUSTED
USER_CONFIRMATION_REQUIRED

══════════════════════════════════════════════════════════════════════════════
21. SAFE IR PROBING
══════════════════════════════════════════════════════════════════════════════

Initial autonomous safe set:

VOLUME_UP
VOLUME_DOWN

MUTE only if mute state can be observed/reversed reliably.

POWER:
never autonomous by default.

INPUT:
never autonomous without explicit user participation.

For each trial:

freeze candidate
freeze action
record signalId
record physicalSha
record catalogBuildId
record waveformSha
record carrier
record transmit result
record observation.

No attribution after cursor advancement.

══════════════════════════════════════════════════════════════════════════════
22. UNIFY UNIVERSAL + BRAND + MODEL PROBING
══════════════════════════════════════════════════════════════════════════════

There must be ONE candidate engine.

CandidateQuery:

deviceType = TV
brand = optional
model = optional
platform = optional
actions = safe probe set

Universal:

brand = null
model = null

Brand:

brand = Samsung
model = null

Exact model:

brand = Samsung
model = UN...

No separate implementation.

Never brand-query with deviceType="".

Never arbitrary LIMIT 200.

Use bounded-memory paging until candidate space is exhausted.

══════════════════════════════════════════════════════════════════════════════
23. PROBE CURSOR CONTRACT
══════════════════════════════════════════════════════════════════════════════

Cursor states:

UNINITIALIZED
READY
EXHAUSTED
ERROR

initialize()
must produce:

READY + non-null candidate

or:

EXHAUSTED.

advance()
must atomically return the new current candidate.

Never:

launch async mutation
then synchronously read old cursor state.

Tests must cross every page boundary.

══════════════════════════════════════════════════════════════════════════════
24. IR RUNTIME TRUTH
══════════════════════════════════════════════════════════════════════════════

Maintain ONE canonical IR protocol registry shared/generated for:

Python ingestion
Kotlin runtime
future firmware.

No duplicated names.

Catalog:
canonical family
canonical variant.

Source-original strings are provenance only.

Every production candidate must resolve:

catalog row
→ RuntimeProtocolBinding
→ exact codec
→ exact variant
→ encoder or RAW waveform
→ valid physical waveform.

Generate:

runtime-executable-report.json

for the EXACT packaged catalog.

Unknown runtime coverage:

0.

══════════════════════════════════════════════════════════════════════════════
25. RAW SIGNAL AUTHORITY
══════════════════════════════════════════════════════════════════════════════

When a valid RAW waveform exists:

RAW physical representation remains authoritative.

A protocol decode is a derived projection.

Never require protocol recognition for replay if RAW is validated.

Store:

RawCaptureSha
NormalizedPhysicalSha
carrier
timings
repeat semantics
toggle semantics
press semantics

where available.

══════════════════════════════════════════════════════════════════════════════
26. CURRENT IR CODECS
══════════════════════════════════════════════════════════════════════════════

Do not promote from unit-test success to commercial use.

Each protocol/variant gets:

STRUCTURAL
GOLDEN_VECTOR
ROUNDTRIP
OPTICAL
REAL_DEVICE
HIL

status independently.

SIRC:

explicit:

SIRC_12
SIRC_15
SIRC_20

SIRC20 must separately represent:

address
subdevice
command.

No implicit 13-bit "address" approximation.

AIWA:

required subdevice must fail if missing.

No:

subDevice ?: 0.

RC5 / RC6 / Kaseikyo:

remain laboratory-only until physical/independent verification.

══════════════════════════════════════════════════════════════════════════════
27. PHONE IR HARDWARE DIAGNOSTICS
══════════════════════════════════════════════════════════════════════════════

Create engineering screen:

IR Hardware Diagnostics

Display:

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
variant.

Never show:

"worked"

from transmit result alone.

Use:

TRANSMIT_ACCEPTED

until physical reaction is observed.

══════════════════════════════════════════════════════════════════════════════
28. ROUTE PLANNER
══════════════════════════════════════════════════════════════════════════════

Per UniversalAction, score routes independently.

Potential routes:

TV_NODE_NATIVE
TV_NODE_ACCESSIBILITY
TV_NODE_MEDIA_SESSION
BLUETOOTH_HID
DIRECT_IR
CONSUMER_LAN
NEXUS_BRIDGE
CEC
ADB_ENGINEERING

Score dimensions:

availability
verification level
expected latency
recent success
confidence
security/trust
battery/cost
state observability.

Different actions may use different routes.

Example:

Navigation:
TV_NODE

Volume:
IR

Text:
NEXUS_IME

Playback:
MEDIA_SESSION

Power:
IR

This is correct.

Do not force one transport for entire device.

══════════════════════════════════════════════════════════════════════════════
29. ADB RECLASSIFICATION
══════════════════════════════════════════════════════════════════════════════

Keep ADB implementation.

Rename product meaning:

ADB_ENGINEERING

It is an engineering/laboratory transport.

Do not advertise it as zero-setup consumer control.

Capabilities exposed by ADB adapter must be exact.

Do not claim:

HDMI_CEC
if no CEC implementation exists.

Do not claim:

InputSource
if execute() cannot perform InputSource.

Do not claim:

readable=true
when readState() returns null.

pair()
must perform or represent real pairing state,
not unconditional success.

No internal fake capability declaration.

══════════════════════════════════════════════════════════════════════════════
30. BACKGROUND / PROCESS DEATH
══════════════════════════════════════════════════════════════════════════════

Design around Android lifecycle reality.

Do not assume daemon permanence.

Persist durable state:

paired peers
DeviceTwin
capabilities
current profile
calibration session
last exact candidate
evidence.

On restart:

restore exact identities.

Never restore by approximate index after catalog changes.

State machine:

OFFLINE
DISCOVERABLE
PAIRING
CONNECTED
ACTIVE
DEGRADED
RECONNECTING

Process death is a required test.

══════════════════════════════════════════════════════════════════════════════
31. DEVICE TWIN
══════════════════════════════════════════════════════════════════════════════

Create authoritative:

DeviceTwin

Fields:

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
evidence summary.

ProtocolBinding examples:

TvNodeBinding
IrBinding
BluetoothHidBinding
LanBinding
BridgeBinding
CecBinding.

Never merge devices based solely on:

same manufacturer
+
same model.

Two identical TVs in the same house remain distinct devices.

══════════════════════════════════════════════════════════════════════════════
32. EVIDENCE LEDGER
══════════════════════════════════════════════════════════════════════════════

All important physical/control events produce immutable evidence.

Evidence classes:

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

Verification status is derived from evidence.

Never manually set a stronger state than the evidence supports.

══════════════════════════════════════════════════════════════════════════════
33. SOFTWARE-ONLY SUCCESS GATE
══════════════════════════════════════════════════════════════════════════════

Before buying/custom-building IR capture hardware, demonstrate with
only phone + TVs:

TV Node installs.
Discovery works.
Pairing secure.
Reconnect works.
Nexus IME works.
Accessibility mode works where supported.
Bluetooth HID works on at least one real TV.
Phone IR works.
TvObservationEngine works.
IR Oracle automatically identifies at least one correct code set.
Exact profile survives process death.
Same DeviceTwin survives TV/router/phone reboot.
No silent failures.

Then expand to:

3 TVs
5 TVs
10 TVs

from different vendors/platform families.

Only then begin hardware acquisition phase.

══════════════════════════════════════════════════════════════════════════════
34. PHASE B — NEXUS BRIDGE
══════════════════════════════════════════════════════════════════════════════

Only after Phase A.

Purpose:

remove dependence on phone IR emitter
+
gain physical IR reception
+
gain better transmission
+
eventually add CEC.

Prototype hardware:

ESP32-S3 class MCU
IR demodulated receiver
wide-band/raw optical receiver
high-power IR LED driver
BLE
USB-C
optional Wi-Fi
local flash.

Do NOT start with custom PCB.

Start with development modules and breadboard.

══════════════════════════════════════════════════════════════════════════════
35. PHYSICAL IR ACQUISITION
══════════════════════════════════════════════════════════════════════════════

With Bridge:

Original Remote
      ↓
IR receivers
      ↓
measured carrier
raw timings
repeat structure
toggle behavior
      ↓
PhysicalSignalAcquisitionEngine
      ↓
semantic correlation with TV Node
      ↓
replay
      ↓
TV observation
      ↓
evidence.

This replaces endless Internet searching.

External IR databases become:

BOOTSTRAP
FALLBACK
REFERENCE

not permanent source of truth.

══════════════════════════════════════════════════════════════════════════════
36. CAPTURE PROTOCOL
══════════════════════════════════════════════════════════════════════════════

Controller coordinates:

CaptureSession
CaptureToken
ExpectedAction.

Phone:

ARM_CAPTURE(token, VOLUME_UP)

TV Node:

EXPECT_ACTION(token)

Bridge:

ARM_RECEIVER(token)

User presses OEM remote.

Bridge returns:

carrier
pattern
captureSha
receiver identity.

TV Node may return:

KeyObserved(VOLUME_UP)

Correlation engine combines them.

Timestamp alone is insufficient.

Use explicit capture tokens.

══════════════════════════════════════════════════════════════════════════════
37. MULTI-CAPTURE SIGNAL LEARNING
══════════════════════════════════════════════════════════════════════════════

Never learn a key from one capture only when verification is possible.

Capture:

tap #1
tap #2
tap #3
long hold
release/repress when relevant.

Analyze:

carrier variance
timing variance
frame stability
repeat frame
toggle bit
rolling state
stateful protocol.

Classify:

STABLE
REPEAT_BASED
TOGGLE
STATEFUL
UNSTABLE

Store all raw captures.

Derived normalized signal does not replace them.

══════════════════════════════════════════════════════════════════════════════
38. HYBRID REMOTE PROFILES
══════════════════════════════════════════════════════════════════════════════

A modern OEM remote may use:

POWER = IR
DPAD = Bluetooth
VOICE = Bluetooth
TEXT = software

Elysium must model this.

Example:

POWER → IR_CAPTURED
VOLUME_UP → IR_CAPTURED
DPAD_UP → TV_NODE
HOME → TV_NODE
TEXT → NEXUS_IME
VOICE → UNSUPPORTED

Do not invent an IR mapping for a button that the OEM remote never
transmits via IR.

══════════════════════════════════════════════════════════════════════════════
39. OEM FUTURE MODE
══════════════════════════════════════════════════════════════════════════════

Optional future:

NexusTvNodeSystemEdition

If a manufacturer grants legitimate access to:

LIRC
rc-core
HAL
driver
system service

create an OEM adapter.

Never design consumer version assuming /dev/lirc0 access.

Never attempt to bypass SELinux/root restrictions.

OEM integration is an accelerator,
not a dependency.

══════════════════════════════════════════════════════════════════════════════
40. OWN ELYSIUM PHYSICAL CORPUS
══════════════════════════════════════════════════════════════════════════════

Long-term goal:

Elysium Physical Remote Corpus.

For every exact model/action:

device model
firmware family
original remote
action
carrier
raw waveform
normalized physical fingerprint
decoder projection
repeat semantics
replay result
real TV result
evidence level
capture hardware
catalog build
timestamp.

External datasets become secondary.

Our empirical corpus becomes primary.

══════════════════════════════════════════════════════════════════════════════
41. COMMUNITY LEARNING WITHOUT BAD CROWDSOURCING
══════════════════════════════════════════════════════════════════════════════

User-generated capture never becomes global verified truth immediately.

Promotion:

USER_CAPTURED
→ MULTI_CAPTURE_STABLE
→ LOCAL_REPLAY_VERIFIED
→ TV_NODE_CONFIRMED
→ MULTI_DEVICE_CORROBORATED
→ LAB_VERIFIED
→ HIL_VERIFIED
→ RETAIL_MATRIX_VERIFIED

Track:

unique physical devices
unique installations
firmware families
success count
failure count
regressions.

Require independence where appropriate.

══════════════════════════════════════════════════════════════════════════════
42. RETAILER KNOWLEDGE GRAPH
══════════════════════════════════════════════════════════════════════════════

Create:

Retailer
    ↓
RetailerSKU
    ↓
GTIN / MPN
    ↓
ExactDeviceModel
    ↓
FirmwareFamily
    ↓
OriginalRemoteFamily
    ↓
DeviceTwin template
    ↓
Per-action bindings
    ↓
Evidence
    ↓
Compatibility Certificate

Tables:

retailers
retailer_locations
retailer_skus
retailer_inventory_snapshots
device_models
device_model_variants
firmware_families
original_remote_models
retail_compatibility_certificates
retail_certificate_actions.

══════════════════════════════════════════════════════════════════════════════
43. RETAIL INVENTORY
══════════════════════════════════════════════════════════════════════════════

Public website ingestion may bootstrap development.

Production should prefer:

official API
CSV
SFTP
ERP export
partner feed.

Retailers:

MONGE_CR
VERDUGO_CR
GOLLO_CR.

Do not claim complete Gollo coverage from incomplete crawling.

Every inventory snapshot is dated.

Compatibility is calculated against that snapshot.

══════════════════════════════════════════════════════════════════════════════
44. RETAIL COMPATIBILITY DEFINITION
══════════════════════════════════════════════════════════════════════════════

"Compatible with retailer inventory"

means:

every active SKU has exact model mapping
+
required CORE actions physically verified
+
no unresolved regression.

CORE TV actions:

POWER
VOLUME_UP
VOLUME_DOWN
MUTE
INPUT
UP
DOWN
LEFT
RIGHT
OK
BACK
HOME

with applicability rules where a TV lacks a concept.

EXTENDED:

channel
numbers
guide
media
settings
info
color buttons
app shortcuts.

OEM_SPECIAL:

voice
air mouse
magic pointer
vendor proprietary functionality.

Do NOT promise universal OEM_SPECIAL equivalence.

══════════════════════════════════════════════════════════════════════════════
45. COMPATIBILITY CERTIFICATES
══════════════════════════════════════════════════════════════════════════════

Create signed:

RetailCompatibilityCertificate

Fields:

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
signature.

No boolean:

works=true.

Compatibility is action-level.

══════════════════════════════════════════════════════════════════════════════
46. RETAIL COVERAGE ENGINE
══════════════════════════════════════════════════════════════════════════════

Calculate:

activeSkuCount
knownSkuCount
coreVerifiedCount
extendedVerifiedCount
fullVerifiedCount
pendingCount
regressionCount.

Allow:

100% CORE VERIFIED

only when:

coreVerifiedCount == activeSkuCount
AND
pendingCount == 0
AND
regressionCount == 0.

No marketing override.

══════════════════════════════════════════════════════════════════════════════
47. FIRST COMMERCIAL MATRIX
══════════════════════════════════════════════════════════════════════════════

Pilot one retailer first.

Recommended sequence:

MONGE
→ EL VERDUGO
→ GOLLO

Reason:

solve one closed inventory completely before expanding.

For every unique exact TV model:

install TV Node where possible
discover software capabilities
IR-oracle calibrate existing codes
capture physical IR later where necessary
verify CORE
store evidence
issue certificate.

Reuse evidence between retailers only when exact hardware/region/firmware
compatibility is legitimately equivalent.

Never reuse because:

same brand
same size
similar model name.

══════════════════════════════════════════════════════════════════════════════
48. RETAIL EMPLOYEE UX
══════════════════════════════════════════════════════════════════════════════

Create Retail Setup Mode.

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
ready.

Target known-model setup:

<20 seconds

only after measured.

══════════════════════════════════════════════════════════════════════════════
49. CONSUMER UX
══════════════════════════════════════════════════════════════════════════════

Customer should never see:

codec
carrier
protocol IDs
SQLite
signal hashes.

Normal flow:

Detect TV
↓
Pair
↓
Capabilities found
↓
Control ready.

If IR calibration needed:

"Vamos a calibrar el control"

not:

"Test codeSet 183".

Advanced engineering diagnostics remain available separately.

══════════════════════════════════════════════════════════════════════════════
50. SELF-HEALING
══════════════════════════════════════════════════════════════════════════════

Failure of one binding does not invalidate whole device.

Example:

HOME stops working.

Mark:

HOME = REGRESSION.

Keep:

POWER
VOL
MUTE
NAV

working.

Recalibrate HOME only.

RoutePlanner may switch:

TV_NODE
→ HID
→ IR

without creating another pseudo-device.

══════════════════════════════════════════════════════════════════════════════
51. SECURITY
══════════════════════════════════════════════════════════════════════════════

Zero-trust principles.

Every command has:

authenticated origin
target identity
required trust
permission evaluation
audit event.

Credential storage:

Android Keystore.

Encryption:

modern AEAD.

No hardcoded master production passwords.

No debug production signing.

No fail-open pairing.

No unlimited unbounded transport queues.

No silent peer merge.

No unauthenticated capture listener.

══════════════════════════════════════════════════════════════════════════════
52. PRIVACY
══════════════════════════════════════════════════════════════════════════════

Local-first.

Core remote must not require cloud.

Sensitive telemetry off by default.

Do not upload:

typed passwords
private text
screen contents
Wi-Fi credentials
device identifiers

without explicit reason/consent.

Community evidence upload:

opt-in
minimized
pseudonymized where possible.

Physical signal corpus does not require personal identity.

══════════════════════════════════════════════════════════════════════════════
53. ANDROID RELEASE TOOLCHAIN
══════════════════════════════════════════════════════════════════════════════

Maintain current supported target requirements.

Use toolchain officially supporting compile/target SDK.

Release build must fail if:

keystore missing
password missing
alias missing
signing configuration invalid.

No fallback release password in source.

CI must install exact target SDK/build tools.

Generate:

APK/AAB
Git SHA
catalogBuildId
SBOM
artifact SHA-256
provenance report.

══════════════════════════════════════════════════════════════════════════════
54. TEST PYRAMID
══════════════════════════════════════════════════════════════════════════════

UNIT:

protocol encoders
protocol decoders
candidate ranking
identity
capabilities
route scoring
crypto
serialization.

INTEGRATION:

real packaged catalog
SQLite
TV Node protocol
IME
Accessibility adapter
HID adapter
process recovery.

ON DEVICE:

phone
TV Node
IR transmitter
Bluetooth.

REAL DEVICE:

actual TV behavior.

HIL:

independent physical receiver.

MATRIX:

multiple brands/models/firmwares.

RETAIL:

exact active SKUs.

No level substitutes for the next.

══════════════════════════════════════════════════════════════════════════════
55. SOFTWARE-ONLY DEVICE MATRIX
══════════════════════════════════════════════════════════════════════════════

Begin immediately with real TVs available.

Capture:

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
oracle capability
observations
latency.

A TV is not "Android compatible" merely because an APK installs.

Record exact behavior.

══════════════════════════════════════════════════════════════════════════════
56. KPI FRAMEWORK
══════════════════════════════════════════════════════════════════════════════

Targets become claims only after measured.

Engineering targets:

Pairing success >99%
Reconnect >99.9%
Wrong-device dispatch = 0
Silent failure = 0
Replay accepted = 0
Credential leakage = 0
Known-TV setup median <20s
LAN action latency p50 <30ms
LAN action latency p95 <100ms
Core retail coverage = 100%
Unknown active retail SKU = 0
Open CORE regressions = 0
Profile migration loss = 0
Catalog/runtime variant mismatch = 0

Measure rather than assume.

══════════════════════════════════════════════════════════════════════════════
57. OBSERVABILITY
══════════════════════════════════════════════════════════════════════════════

Structured events:

PAIR_STARTED
PAIR_SUCCESS
PAIR_FAILED

TV_NODE_DISCOVERED
TV_NODE_CONNECTED
TV_NODE_DISCONNECTED

ACTION_RECEIVED
ACTION_EXECUTED
ACTION_FAILED

IR_CANDIDATE_SELECTED
IR_ENCODED
IR_TX_ACCEPTED
IR_TX_FAILED

TV_OBSERVATION
ORACLE_TRIAL_STARTED
ORACLE_TRIAL_CONFIRMED
ORACLE_TRIAL_INCONCLUSIVE

PROFILE_INSTALLED
PROFILE_REVALIDATED

ROUTE_SELECTED
ROUTE_FAILED
ROUTE_FALLBACK

Never log secrets.

Every physical test must be reconstructable from IDs/evidence.

══════════════════════════════════════════════════════════════════════════════
58. DOCUMENTATION TRUTH
══════════════════════════════════════════════════════════════════════════════

README, transport matrix and Reality Ledger must reflect current measured state.

Remove stale static numbers.

Prefer generated values.

Do not say:

"verified commands"

for merely imported catalog entries.

Use exact wording:

cataloged
runtime-executable
TV-node-observed
real-device-verified
HIL-verified
retail-verified.

CI should detect documentation/evidence contradictions where practical.

══════════════════════════════════════════════════════════════════════════════
59. IMPLEMENTATION PRIORITY — NOW
══════════════════════════════════════════════════════════════════════════════

DO NOW, WITH NO NEW HARDWARE:

A.
TV Node skeleton + shared protocol.

B.
Secure discovery/pairing.

C.
CapabilityManifest.

D.
Nexus Accessibility Enhanced Mode.

E.
Nexus TV IME.

F.
TV Observation Engine.

G.
Bluetooth HID TV transport.

H.
Unify IR candidate engine.

I.
Software-only IR Oracle.

J.
Persist DeviceTwin + exact winning IR bindings.

K.
Test real phone + real TVs.

L.
Build 3-TV → 5-TV → 10-TV software matrix.

DO NOT prioritize yet:

new massive external IR scraping
new UI decoration
Matter
Thread
console work
new device classes
hardware PCB
cloud architecture
marketplace.

Close the TV control vertical first.

══════════════════════════════════════════════════════════════════════════════
60. HARDWARE ENTRY GATE
══════════════════════════════════════════════════════════════════════════════

Only begin Nexus Bridge hardware when software-only system proves:

secure TV pairing
TV observation
HID
IME
IR transmission
automatic oracle calibration
persistent profiles
multi-route fallback

on real TVs.

Then hardware solves only genuinely physical gaps:

raw IR reception
universal IR transmission
TV-off control
CEC
phone-without-IR
independent evidence.

══════════════════════════════════════════════════════════════════════════════
61. PR / DELIVERY SLICING
══════════════════════════════════════════════════════════════════════════════

PR1 — TV Node Foundation
shared domain, protocol, identity, capability manifest.

PR2 — Secure Pairing
NSD + QR + authenticated local channel + Keystore.

PR3 — TV Enhanced Control
Accessibility + observation.

PR4 — Nexus IME
phone text → TV.

PR5 — Bluetooth HID
real TV tests.

PR6 — Unified IR Runtime
brand/model/universal candidate pipeline.

PR7 — IR Oracle
state observation + causal verification.

PR8 — DeviceTwin / Persistence
multi-route exact identity.

PR9 — Real TV Software Matrix
3→5→10 models.

PR10 — Retail Data Graph
retailers/SKUs/models/certificates.

PR11 — Nexus Bridge Prototype
only after software gate.

PR12 — Physical Acquisition
IR RX/replay/evidence.

PR13 — HIL
independent capture.

PR14 — Retail Matrix Pilot.

Do not combine all into one giant unreviewable PR.

══════════════════════════════════════════════════════════════════════════════
62. REQUIRED DELIVERABLE FOR EVERY PHASE
══════════════════════════════════════════════════════════════════════════════

Every agent must finish a phase with:

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

Never simply say:

"Implemented successfully."

══════════════════════════════════════════════════════════════════════════════
63. SOFTWARE-FIRST ACCEPTANCE TEST
══════════════════════════════════════════════════════════════════════════════

Final Phase-A demonstration:

Fresh TV Node install.

Phone discovers TV.

TV displays pairing code.

Phone pairs securely.

Kill phone app.

Restart.

Reconnect exact same DeviceTwin.

TV remote key observed.

Phone D-pad controls TV through TV Node.

Phone keyboard types into TV through Nexus IME.

Bluetooth HID controls supported TV.

Phone IR sends VolumeUp candidate.

TV Node detects actual state/action change.

Oracle performs inverse challenge.

Exact IR signal is confirmed.

Persist:

deviceId
codeSetId
signal IDs
physical SHAs
catalogBuildId
verification evidence.

Force-stop apps.

Restart phone + TV.

Profile remains.

Disconnect Wi-Fi.

Verified IR still controls TV.

Restore Wi-Fi.

Route planner automatically returns to preferred route.

No manual profile recreation.

This is the software-only golden vertical.

══════════════════════════════════════════════════════════════════════════════
64. PHYSICAL GOLDEN VERTICAL — FUTURE
══════════════════════════════════════════════════════════════════════════════

After Nexus Bridge exists:

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
→ exact profile.

No Internet database involved.

This becomes the ultimate IR acquisition loop.

══════════════════════════════════════════════════════════════════════════════
65. COMMERCIAL GOLDEN VERTICAL
══════════════════════════════════════════════════════════════════════════════

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
→ update/revalidation.

Only then:

RETAIL_MATRIX_VERIFIED.

══════════════════════════════════════════════════════════════════════════════
66. FINAL RELEASE TRUTH GATE
══════════════════════════════════════════════════════════════════════════════

NO PRODUCTION_APPROVED until all applicable gates are true:

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

══════════════════════════════════════════════════════════════════════════════
67. ULTIMATE ARCHITECTURAL PRINCIPLE
══════════════════════════════════════════════════════════════════════════════

The objective is NOT:

"make one protocol control everything."

The objective is:

"make every authorized intent reach the correct physical device through
the strongest currently available verified route."

Therefore:

Software when software is strongest.

IR when IR is strongest.

Bluetooth when Bluetooth is strongest.

LAN when LAN is legitimate and reliable.

CEC when CEC hardware exists.

Nexus Bridge when a physical endpoint is needed.

OEM integration when legitimately available.

All routes converge on:

UniversalAction
+
DeviceTwin
+
Evidence.

══════════════════════════════════════════════════════════════════════════════
68. FINAL PRODUCT DEFINITION
══════════════════════════════════════════════════════════════════════════════

Elysium Nexus shall become:

A local-first distributed universal control fabric in which a phone,
a cooperative TV Node and optional future Nexus hardware work together
to identify the exact physical device, discover its capabilities,
choose the best verified transport for each action, execute the action,
observe the result whenever possible, learn from real evidence,
recover automatically from transport failures and preserve exact
compatibility knowledge for future sessions and future users.

The highest-value asset is not the UI.

It is the evidence-backed graph:

Retailer
→ SKU
→ exact physical model
→ firmware
→ capabilities
→ action
→ route
→ signal
→ observed result
→ evidence
→ compatibility certificate.

NO MOCK.
NO GUESS.
NO ROOT DEPENDENCY.
NO OEM DEPENDENCY.
NO INTERNET DEPENDENCY FOR CORE CONTROL.
NO CLAIM WITHOUT PROOF.

BUILD SOFTWARE FIRST.

USE HARDWARE ONLY TO SOLVE PHYSICALLY IMPOSSIBLE SOFTWARE GAPS.

TURN EVERY REAL DEVICE TEST INTO DURABLE KNOWLEDGE.

THAT IS THE ELYSIUM NEXUS ARCHITECTURE.
══════════════════════════════════════════════════════════════════════════════
```

---

## Implementation Execution Note (first offensive)

Before these 68 sections, the FIRST offensive is strictly:

**TV Node → secure pairing → Accessibility → IME → observation →
Bluetooth HID → IR Oracle → DeviceTwin → real tests.**

Closing loop to achieve before any hardware:

```text
Teléfono
   ↕ Wi-Fi
TV Node
   ↕
Android TV
   ↕
estado real

Teléfono
   ├── Bluetooth HID ──→ TV
   └── IR ─────────────→ TV
                           ↓
                       TV Node
                           ↓
                    confirma resultado
```

Nexus Bridge (Phase B) arrives ONLY to solve what physics prevents
APK-only software from achieving: RAW reception, universal emitter for
IR-less phones, TV-off control, CEC, independent optical evidence.

Commercial metric is not "106 thousand signals / 1400 brands" but:

> **SKU exacto → TV exacto → funciones exactas → rutas exactas →
> evidencia exacta → certificado exacto.**

"100% CORE VERIFIED" is only claimable over the complete dated active
inventory of one chain.