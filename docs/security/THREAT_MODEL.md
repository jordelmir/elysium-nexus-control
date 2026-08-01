# Threat Model

> **Status:** Phase ULT.0 — initial threat model
> per `MASTER_ORDER.md` §28 + §31. Living document;
> the next phase expands each section as the
> relevant subsystem ships.

This product controls physical systems. The
attack surface is large (cameras, locks, lights,
HVAC, garage doors, energy systems). The threat
model is the discipline that keeps the surface
defensible.

## 1. Adversaries

| Adversary | Motivation | Capability |
|-----------|------------|------------|
| **Casual snoop** | curiosity | on-network passive |
| **Script kiddie** | defacement, joy | off-the-shelf exploits |
| **Disgruntled guest** | revenge | physical access + a paired device |
| **Compromised cloud** | mass exploitation | supply-chain on a vendor SDK |
| **Stalker** | surveillance | target one home, persistent |
| **Nation-state** | strategic compromise | patient, multi-vector |

## 2. Assets

| Asset | Where | Sensitivity |
|-------|-------|-------------|
| Device control keys | Android Keystore, Hub TPM, Receiver SE | **CRITICAL** |
| Live camera stream | LAN, optional cloud relay | **HIGH** (privacy) |
| Lock state | local DB + cloud backup (encrypted) | **CRITICAL** (physical safety) |
| Doorbell video | local + cloud (encrypted, opt-in) | **HIGH** |
| IR blast power | Hub transmitter | **MEDIUM** (annoyance if abused) |
| Energy control | local + optional cloud | **HIGH** (cost, fire risk) |
| User voice recordings | device-local; never on cloud unless explicit | **HIGH** (privacy) |
| User biometric | Keystore only; never leaves device | **CRITICAL** |
| Automation logs | local-first; cloud optional | **MEDIUM** |
| Compatibility data | public | **LOW** |
| Profile JSON | user-shared | **MEDIUM** (privacy of layout) |

## 3. Trust boundaries

```
┌─────────────────────────────────────────────────┐
│ DEVICE (Android, Mac, Win, Linux)               │
│  - Keystore / Secure Enclave                    │
│  - User input → canonical                       │
│  - Display → user                               │
└────────────────┬────────────────────────────────┘
                 │ Elysium Link (TLS 1.3, mTLS)
                 │
┌────────────────┴────────────────────────────────┐
│ HUB / DESKTOP AGENT / RECEIVER                 │
│  - Identified by device cert                    │
│  - Operates on LAN by default                   │
│  - Cloud relay requires explicit opt-in        │
└────────────────┬────────────────────────────────┘
                 │ Local LAN + opt-in cloud
                 │
┌────────────────┴────────────────────────────────┐
│ CLOUD RELAY (optional)                          │
│  - Encrypted at rest                            │
│  - User can revoke                              │
│  - Logs every access                            │
└────────────────┬────────────────────────────────┘
                 │
                 │ Vendor APIs (opt-in)
                 │
┌────────────────┴────────────────────────────────┐
│ GOOGLE / APPLE / ALEXA / HOME ASSISTANT         │
│  - Vendor's threat model                        │
│  - User authorizes explicitly per scope         │
└─────────────────────────────────────────────────┘
```

## 4. Threats (STRIDE per boundary)

### T1 — Spoofing

- **T1.1** Spoofed device on the LAN. *Mitigation:*
  mTLS with platform-stored device certs; LAN
  discovery uses challenge-response.
- **T1.2** Spoofed user to cloud. *Mitigation:* OAuth
  + per-device refresh; anomalous-login detection
  (Phase 8+).
- **T1.3** Spoofed IR signal. *Mitigation:* the
  device is the source of truth; an IR blast is
  `COMMAND_SENT` not `STATE_CONFIRMED` (per §6.5
  `COMMAND_SENT / STATE_ESTIMATED / STATE_CONFIRMED
  / STATE_UNKNOWN`).

### T2 — Tampering

- **T2.1** Tampered APK on device. *Mitigation:*
  signed APK; Play Integrity API attestation when
  available; signature-pinned updates.
- **T2.2** Tampered automation. *Mitigation:* every
  automation has a hash + author + last-modified
  timestamp; UI shows the diff when editing.
- **T2.3** Tampered DB. *Mitigation:* `Profile`
  and `DeviceTwin` are signed on write (HMAC); the
  Hub persists events to a write-ahead log with
  hash-chained entries.
- **T2.4** Tampered firmware on Hub / Receiver.
  *Mitigation:* signed firmware + secure boot +
  anti-rollback counter + version pin.

### T3 — Repudiation

- **T3.1** User denies sending a command.
  *Mitigation:* every command has a `correlationId`,
  `actorId`, `deviceId`, `timestampNs`, and a
  digital signature; the audit log is append-only.
- **T3.2** Hub denies a lock action.
  *Mitigation:* the lock's own audit log (LIF/Level
  /Schlage-style) is the source of truth; the
  Elysium audit log mirrors it.

### T4 — Information disclosure

- **T4.1** Live camera stream exposed.
  *Mitigation:* TLS + per-session token; session
  expires on app backgrounding; explicit indicator
  on every viewer; revoke from any device.
- **T4.2** Microphone always-on. *Mitigation:*
  Android requires `RECORD_AUDIO` opt-in; Hub has a
  physical microphone switch; the on-screen
  indicator is always present when active.
- **T4.3** Profile JSON leaks user layout.
  *Mitigation:* profiles are shared by user choice
  (Phase 1.17 share intent); the schema can carry
  a `public` flag.
- **T4.4** Voice recordings stored. *Mitigation:*
  raw audio is never persisted; only the
  intent + entities + result are kept.

### T5 — Denial of service

- **T5.1** Cloud relay flooded. *Mitigation:*
  per-device rate limit; backpressure to local
  control; user can revoke the relay.
- **T5.2** Hub flooded. *Mitigation:* bounded
  queues; backpressure to UI; per-device
  rate limit.
- **T5.3** IR blaster abused. *Mitigation:*
  duty-cycle limit + thermal protection
  (per §6.2 "Protección térmica del Hub").

### T6 — Elevation of privilege

- **T6.1** Guest becomes admin. *Mitigation:*
  RBAC per §31.3; role change requires Owner +
  re-authentication + audit entry.
- **T6.2** App escapes sandbox. *Mitigation:*
  every Android capability that touches a
  security-class action (lock, disarm, garage)
  requires step-up auth (§18.1) + explicit
  confirmation + audit.
- **T6.3** Automation runs with too many rights.
  *Mitigation:* automations declare the actions
  they need at creation; the executor checks
  `ActionRisk` (§31.4) at runtime; high-risk
  actions require interactive confirmation
  unless the automation is `TRUSTED` (Owner-
  authored + signed + 7-day-old).

## 5. Trust on first use (TOFU)

- The Hub / Receiver have a **physical** pairing
  button (per §7.1). The user holds the button,
  the device emits a one-time code, the user
  scans it. No QR from a screen (which an attacker
  could swap).
- Cloud accounts are OAuth; the cloud never sees
  the device's LAN key.
- New devices join the fabric with `attestation`
  (Matter-style) when supported; otherwise via
  install code + signed cert.

## 6. Action risk policy

Per §31.4. Every action has a risk class. The
policy engine refuses to execute high-risk actions
without the right `AuthenticationLevel`. The
default policy is:

| Risk class | Auth required | Confirmation |
|------------|---------------|--------------|
| Informational | none | none |
| Low | device session | none |
| Reversible | device session | none |
| PhysicalMotion | device session | summary |
| PrivacySensitive | device session | summary + 5s grace |
| SecuritySensitive | step-up biometric or PIN | explicit + audit |
| HighPower | step-up biometric or PIN | explicit + audit |
| LifeSafety | step-up biometric or PIN + Owner role | explicit + audit + notify |

The Owner can dial per-action permissions per-user
(Phase 7+).

## 7. Audit log

Every command produces an audit entry:

```kotlin
data class AuditEntry(
    val correlationId: CorrelationId,
    val actorId: UserId,
    val deviceId: DeviceId,
    val capability: Capability,
    val command: Command,
    val time: Instant,
    val origin: Origin,           // DEVICE | HUB | CLOUD | GUEST
    val authorization: Authorization, // RBAC + ABAC verdict
    val result: CommandStatus,    // §40
    val verification: Verification, // §6.5 confirmation class
    val signature: ByteArray      // HMAC by device key
)
```

The audit log is **append-only**. The Hub is the
canonical store; the Android app and desktop agents
mirror. The log is locally searchable; cloud
storage is opt-in and end-to-end encrypted.
