# Risk Register

> **Status:** Phase ULT.0 — initial risk register
> per `MASTER_ORDER.md` §0 + §28. Living document;
> the next phase expands each row as the relevant
> subsystem ships.

A risk is `(ID, description, likelihood, impact,
mitigation, owner, status)`. Likelihood and impact
are 1-5; the score is `likelihood × impact`. A
score ≥ 12 is `BLOCKED_BY_HARDWARE` or
`BLOCKED_BY_VENDOR`; 6-11 is `HIGH`; 3-5 is `MED`;
1-2 is `LOW`.

## R0 — strategic

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R0.1 | Vendor licensing stalls the project | 3 | 5 | 15 | `REQUIRES_VENDOR_LICENSE` gate; §21-§24 explicitly empty; §6 spec | Jor | `BLOCKED_BY_VENDOR` |
| R0.2 | Hardware unavailability (Hub, IR-enabled phone) | 4 | 4 | 16 | Hub BOM has alternatives; emulator covers ~80% of code; HiL rigs in §39 plan | Jor | `BLOCKED_BY_HARDWARE` |
| R0.3 | Reverse-engineering lawsuit from a console vendor | 2 | 5 | 10 | §2 "no impersonation"; we ship our own VID/PID; we never claim PlayStation- or Xbox-branded identity | Jor | `MONITORED` |
| R0.4 | Accessibility Services ban from Play Store | 1 | 4 | 4 | §25 — Accessibility is for accessibility, not gamepad injection | Jor | `OK` |

## R1 — Android (shipped surface)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R1.1 | Compose Compiler bug (`MutableCollectionMutableStateDetector`) | 5 | 2 | 10 | lint deferred; UI tests deferred; both close with the KSP 2.2.x + Compose Compiler upgrade | Mavis | `BLOCKED_BY_UPSTREAM` |
| R1.2 | Robolectric regression (`createAndroidComposeRule`) | 4 | 2 | 8 | Compose UI tests deferred; closes with R1.1 | Mavis | `BLOCKED_BY_UPSTREAM` |
| R1.3 | `adb` allows a third-party app to inject HID events | 3 | 4 | 12 | only the OEM-signed adb can; the app is for personal/creative use, not Play Store | Jor | `ACCEPTED` |
| R1.4 | Touch surface accidentally swallows gestures | 2 | 2 | 4 | Bug #18 fix verified (latency count 0→4 across 4 taps); PointerInput stack audited | Mavis | `OK` |
| R1.5 | Engine emission drift over 24h (memory leak) | 1 | 3 | 3 | Soak test (§40) is the gate; not run yet | Mavis | `MED` |

## R2 — desktop agents (Phase 3, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R2.1 | macOS sandbox blocks HID input | 4 | 5 | 20 | needs `com.apple.security.cs.allow-unsigned-executable-memory` or `Accessibility` permission; UX shows how to grant | Jor | `BLOCKED_BY_HARDWARE` |
| R2.2 | Windows Defender flags unsigned binary | 5 | 3 | 15 | code-signing cert (post-MVP) | Jor | `BLOCKED_BY_VENDOR` |
| R2.3 | Linux udev rules need root | 4 | 2 | 8 | per-user udev rule; install instructions | Mavis | `MED` |

## R3 — Hub / Receiver (Phase 4, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R3.1 | Hub has a hardware security flaw | 2 | 5 | 10 | secure boot + signed firmware + TPM; penetration test (Phase 6+) | Jor | `BLOCKED_BY_HARDWARE` |
| R3.2 | Receiver bootloader is brickable | 3 | 3 | 9 | dual-bank OTA; recover via UART | Mavis | `MED` |
| R3.3 | Hub thermals trip in enclosed cabinet | 3 | 2 | 6 | thermal throttling; chassis spec; duty cycle limits on IR | Jor | `MONITORED` |
| R3.4 | Receiver USB stack has known CVEs | 2 | 4 | 8 | use vendor SDK with security advisories feed | Mavis | `MONITORED` |

## R4 — Smart home (Phase 4-5, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R4.1 | Matter certification stalls the project | 3 | 4 | 12 | implement against open-source `matter-sdk`; CSA membership is a separate path | Jor | `BLOCKED_BY_VENDOR` |
| R4.2 | Zigbee coordinator firmware is closed | 4 | 2 | 8 | use `zigbee2mqtt` + `zigpy` where possible; document the closed-firmware path | Mavis | `MED` |
| R4.3 | Z-Wave SDK requires NDA | 5 | 3 | 15 | Z-Wave JS is open; the official SDK is gated by NDA; document the split | Jor | `BLOCKED_BY_VENDOR` |
| R4.4 | Device quirks: firmware update changes behavior | 4 | 3 | 12 | `quirks` database; per-firmware version pinning; `regression` status in compatibility DB | Mavis | `MONITORED` |

## R5 — Cameras + access (Phase 6, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R5.1 | Live camera stream leaks | 4 | 5 | 20 | TLS + per-session token + expiry; on-viewer indicator; revoke from any device | Mavis | `MED` |
| R5.2 | Lock is bypassed via replay | 3 | 5 | 15 | step-up auth per §18.1; lock's own audit log; lock never acts on a single IR blast | Mavis | `MONITORED` |
| R5.3 | Doorbell is DoS'd so the legit visitor is missed | 4 | 3 | 12 | local chime that does not require cloud | Jor | `MONITORED` |
| R5.4 | Camera AI is biased | 3 | 4 | 12 | human review of all auto-actions; confidence threshold; opt-out per category | Mavis | `MONITORED` |

## R6 — Ecosystems (Phase 7, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R6.1 | Google Home API deprecates the local path | 3 | 3 | 9 | we treat GH as opt-in; local-first | Mavis | `MONITORED` |
| R6.2 | Alexa skill is rejected for security review | 3 | 3 | 9 | OAuth 2.0 + scope per device; no full account read | Mavis | `MONITORED` |
| R6.3 | Home Assistant breaks our WebSocket consumer | 2 | 2 | 4 | version pin + retry with backoff | Mavis | `LOW` |

## R7 — AI + voice (Phase 8, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R7.1 | Voice is recorded without consent | 3 | 5 | 15 | always-on mic is opt-in per device; physical switch on Hub; on-screen indicator | Jor | `MED` |
| R7.2 | AI sends a wrong command | 4 | 4 | 16 | the plan is **visible** before execution (§29); high-risk actions require explicit user confirmation regardless of automation | Mavis | `MONITORED` |
| R7.3 | Wake word false-positives | 3 | 2 | 6 | confidence threshold + device-local model | Mavis | `MONITORED` |

## R8 — Energy + appliances (Phase 9, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R8.1 | Wrong tariff schedule bills user incorrectly | 3 | 3 | 9 | tariff changes are visible in app; never auto-applied without confirmation | Mavis | `MONITORED` |
| R8.2 | EV charger over-draws the home circuit | 2 | 5 | 10 | max-amp cap; circuit-aware load shedding; manual override | Mavis | `MONITORED` |
| R8.3 | Oven turns on unattended | 2 | 5 | 10 | require step-up auth + presence check + timer | Mavis | `MONITORED` |

## R9 — Retail + marketplace (Phase 10, not yet shipped)

| ID | Risk | L | I | Score | Mitigation | Owner | Status |
|----|------|---|---|-------|------------|-------|--------|
| R9.1 | Marketplace profile is malicious | 3 | 4 | 12 | sandboxed profile interpreter; sign every profile; revocation list | Mavis | `MONITORED` |
| R9.2 | Retail demo mode leaks store-network creds | 2 | 4 | 8 | per-demo identities; per-demo pairing; revocation on demo end | Mavis | `MONITORED` |
| R9.3 | Installer key is exfiltrated | 2 | 5 | 10 | short-lived tokens; hardware-bound; audit | Jor | `MONITORED` |

## 10. Update discipline

- This register is reviewed every phase.
- New risks are added with a score and an owner.
- A risk's status changes from `MONITORED` to
  `OK` when the mitigation is shipped + tested.
- A risk's status is `ACCEPTED` only with the
  Owner's explicit sign-off.
- A `BLOCKED_BY_*` status means the next phase
  cannot close the risk; the project's roadmap
  must include the unblock action.
