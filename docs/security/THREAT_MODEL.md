# Threat Model — Elysium Nexus Universal Controller

> Living document. Updated whenever a new subsystem lands. Each
> subsection follows STRIDE lightly and explicitly calls out the assets,
> trust boundaries, and the residual risk after mitigations.

## 0. Scope

* The Android APK (`apps/android-controller`).
* Future desktop agents (macOS / Windows / Linux).
* Future Elysium Nexus Receiver (firmware).
* The Elysium Link protocol.
* Future SDKs (`ElysiumGameControllerSdk`).

Out of scope (delegated to vendor licensing programs): direct licensed
backends for PS4/5, Xbox One/Series, Switch/2. Those backends do not
compile until vendor authorization is in place (§2, §21, §22, §23, §24).

## 1. Assets

| Asset                                | Why it matters                                          |
| ------------------------------------ | ------------------------------------------------------- |
| Pairing credentials (per session)    | Lets an attacker impersonate a phone to a host.         |
| Long-term device identity (per APK)  | Lets an attacker impersonate a phone across reboots.    |
| Profile files (user layouts)         | Could be tampered to inject malicious bindings.         |
| Firmware image (receiver)            | Brick, backdoor, or surveillance device if tampered.    |
| USB / Bluetooth / Wi-Fi link         | Eavesdropping or injection of fake input.               |
| HID descriptor & capabilities report | Determines what the host thinks the device is.          |
| Host-side virtual HID device         | Persistent fake device if cleanup is missed.            |
| Latency-sensitive path               | Reordering / stuck controls → real-world harm.          |

## 2. Trust boundaries

```
[Phone UI] ⇄ [App process] ⇄ [Android OS / Keystore / Bluetooth stack]
                                        ⇅
                               [Elysium Link]
                                        ⇅
                       [Desktop agent / Receiver]
                                        ⇅
                              [Host HID stack]
                                        ⇅
                                  [Game / App]
```

* **Phone UI** is partially trusted: it is the user, but it is also the
  most likely place for a malicious overlay to live. We rely on Android
  OS-level protections (foreground, package signing) plus our own
  hardening (no Accessibility abuse, no overlay by our own service).
* **App process** is fully trusted. We harden it with Play Integrity /
  SafetyNet-free options (we do not require Google Play services — see
  §2: no Play Store dependency for the project).
* **Android OS / Keystore / Bluetooth stack** is trusted. We rely on
  `BluetoothHidDevice` and Keystore where they exist. We do not
  re-implement Bluetooth encryption.
* **Elysium Link** is hostile: an attacker on the same Wi-Fi or with a
  BLE sniffer can replay, inject, or modify packets. We require
  authenticated encryption with replay protection.
* **Desktop agent / Receiver** is partially trusted: the receiver runs
  code we wrote, but the host OS is shared with other apps; we need to
  be a good citizen (clean up virtual devices on disconnect per §26,
  §38).
* **Host HID stack** is trusted to do what it says, but we cannot
  assume it is bug-free. Our defense is the §38 disconnect test
  (no ghost device, no ghost session).
* **Game / App** is untrusted. We do not give it any access to our
  pairing state, profile store, or keystore. We only emit input events
  and read its published capabilities.

## 3. STRIDE summary

| Category              | Risk                                                                                | Mitigation (current)                                          | Mitigation (planned)                                            | Status    |
| --------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------- | --------------------------------------------------------------- | --------- |
| **S**poofing          | Fake phone → host.                                                                  | Pairing + mutual auth (planned 1.x).                          | QR + numeric compare + per-session keys.                        | Planned   |
| **T**ampering         | Profile file injected with malicious binding.                                      | Profiles only declare bindings/curves/gestures/theme/metadata (§15). | Signed profile import, versioned, replay-protected.              | Planned   |
| **R**epudiation       | User denies a "stuck button" incident.                                              | None yet.                                                     | Append-only local audit log, monotonic timestamps.              | Planned   |
| **I**nformation disc. | Eavesdropper learns input.                                                          | Authenticated encryption (planned).                            | Per-session derived keys, key rotation.                         | Planned   |
| **D**oS               | Flooding the link to starve real input.                                              | Rate limiting (planned).                                       | Sequence-number drop policy, depth bounds.                      | Planned   |
| **E**levation         | App process gains privileges it should not have.                                    | Minimal manifest, no Accessibility abuse, no system overlay.   | Per-subsystem manifest review.                                  | Current   |

## 4. Specifics

### 4.1 Identity (§28)

* Phone-side long-term key in **Android Keystore** (TEE-backed on most
  devices, including Honor Magic V2 — confirm in 0.2 hardware survey).
* Receiver-side long-term key in **secure element** (chosen per §20
  hardware evaluation, deferred to Phase 4).
* Desktop agent: macOS Keychain, Windows DPAPI, Linux `secret-tool` /
  `libsecret`.
* No production keys in Git. The `vendor-keys/` directory is in
  `.gitignore` and exists only for CI's secret-injection path.

### 4.2 Channel (§28.3)

* All real-time frames authenticated (AEAD) with sequence numbers
  (anti-replay) and session-epoch salt. Replay rejected; old frames
  never overwrite a newer one. (§19, §36.)
* Version negotiation on connect; downgrade attempts cause disconnect
  with explicit error.
* Bounds checking on every parser. Size is validated before memory is
  reserved. (§37.)

### 4.3 Firmware (§28.4)

* Secure boot on the receiver.
* Signed firmware with anti-rollback.
* A/B partitions.
* Recovery mode.
* Manifest signed; SBOM generated per release.
* Reproducible builds when the toolchain allows.

### 4.4 Privacy (§28.5)

* No collection of: credentials, typed text, screenshots, clipboard,
  game history, opened apps. Period.
* Local telemetry per §34 — input rate, latency, transport state,
  battery, thermal. **No keyboard content.** No payload sampling.

### 4.5 Neutralization (§38 — release blocker)

* The state machine (§32) emits a neutral frame on every transition
  out of `Active`. The neutral frame is the *same* regardless of which
  transport — buttons released, sticks centered, triggers zero, D-pad
  neutral, touch cancelled, keys released, mouse released, motion
  recentered.
* The disconnect test is run as a property-based test in CI per §36.
  A single "stuck control" on disconnect is a release blocker.

## 5. Residual risk

* **The host OS is shared.** A malicious app on the host can poll the
  Elysium virtual HID device and read its input. This is fundamental to
  the platform: input is by design observable by the host. We do not
  pretend to defend against a compromised host.
* **A compromised phone is a compromised phone.** If the user's device
  is rooted or contains a malicious Accessibility Service, we cannot
  defend against it. We do, however, commit to not providing an
  Accessibility-based shortcut that *we* would use as a primary path.
  (§25.)
* **The receiver has a single point of physical trust.** A determined
  attacker with physical access can reflash the receiver. The signed
  firmware + secure boot policy is the limit of what we can promise;
  beyond that, the user has a hardware tampering problem, not a
  software one.

## 6. Open items (will be resolved per phase)

* [ ] Phase 0.2 — Identity scheme, key derivation function choice.
* [ ] Phase 0.4 — Elysium Link wire format + AEAD choice (likely
      ChaCha20-Poly1305 over QUIC for Wi-Fi, AES-CCM over BLE for the
      link layer).
* [ ] Phase 0.5 — Lint/detekt rules enforcing "no Accessibility
      injection", "no GlobalScope", "no unwrap in production".
* [ ] Phase 4 — Hardware secure element on the receiver.
* [ ] Phase 5 — Remote Play companion: the privacy boundary is the
      official Remote Play app. We never intercept its credentials. We
      never proxy its input.
* [ ] Phase 6+ — Vendor-license-specific threat models; their threat
      models belong in `docs/licensing/<vendor>/THREAT_MODEL.md`,
      under NDA.
