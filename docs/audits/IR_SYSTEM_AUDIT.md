# IR Subsystem Initial Audit & Diagnostics — Elysium Nexus

**Date**: 2026-08-05  
**Auditor**: Antigravity Engineering  
**Repository State**: Pre-IR Fabric Production Repair  

---

## 1. Critical Defects & Root Causes Identified

### 1.1 Non-Positive IR Duration Slices (`IllegalArgumentException: Non-positive IR slice`)
- **Location**: [`IrWaveform.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/IrWaveform.kt#L115)
- **Defect**: The waveform encoder explicitly appended `pattern.add(0)` at the end of frames under the assumption that Android IR patterns must be even-length arrays.
- **Android Framework Reality**: Android's [`ConsumerIrService.java`](https://android.googlesource.com/platform/frameworks/base/+/81f52b053da6/services/core/java/com/android/server/ConsumerIrService.java) enforces `require(pattern.all { it > 0 })`. Any duration slice $\le 0$ throws `IllegalArgumentException: Non-positive IR slice`.
- **Android HAL Reality**: Android IR HAL ([IConsumerIr.hal](https://android.googlesource.com/platform/hardware/interfaces/+/master/ir/1.0/IConsumerIr.hal)) accepts odd-length pattern arrays and automatically turns off the carrier at the end of transmission.
- **Consequence**: `AndroidIrTransmitter.transmit()` caught the exception, returned `false`, and the UI displayed "Sent", while Android never transmitted any signal over the IR LED.

### 1.2 Truncated 16-Bit MSB-First NEC Encoding
- **Location**: [`IrWaveform.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/IrWaveform.kt#L93-L126)
- **Defect**: `encodeNec` generated an 8-bit address + 8-bit command (16 bits total) encoded MSB-first.
- **Physical Protocol Reality**: Standard NEC physical frames consist of 32 bits transmitted LSB-first:
  1. Address (8 bits LSB)
  2. Inverted Address (`Address XOR 0xFF`) (8 bits LSB)
  3. Command (8 bits LSB)
  4. Inverted Command (`Command XOR 0xFF`) (8 bits LSB)
  5. 560 µs stop mark.
- **Consequence**: Even if Android had accepted the waveform, physical IR receivers and TVs would fail to recognize the truncated 16-bit MSB sequence as a valid NEC frame.

### 1.3 Silent Protocol Fallback to NEC
- **Location**: [`IrConnectFlow.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/ui/connect/IrConnectFlow.kt#L598-L632), [`TvControlScreen.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/ui/control/TvControlScreen.kt#L476-L478)
- **Defect**: Protocols like Samsung, SonySIRC, Kaseikyo, RC6, and Raw fell back to calling `IrWaveform.encodeNec()` when connecting or transmitting unsupported buttons.
- **Consequence**: Non-NEC devices received corrupt NEC bursts regardless of their declared protocol, making diagnosis impossible.

### 1.4 Hardcoded Universal Command Bytes in UI
- **Location**: [`DeviceTemplate.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/core/device/DeviceTemplate.kt)
- **Defect**: Visual `DeviceButton` instances directly specified physical `commandCode` bytes (e.g. `0x07` for `vol_up`) shared across all television brands.
- **Consequence**: Physical command bytes vary per manufacturer, model, and chassis. Command codes must belong to `IrCodeSet`, while buttons specify semantic `IrAction`s.

### 1.5 Infinite Repetition of Identical Candidate Waveforms
- **Location**: [`IrConnectFlow.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/ui/connect/IrConnectFlow.kt#L226-L238)
- **Defect**: Clicking "Send test" incremented the attempt counter while re-transmitting the exact same power signal without advancing candidates or deduplicating fingerprints.
- **Consequence**: 528 connection attempts amounted to re-transmitting 1 failing code 528 times.

### 1.6 Inaccurate Capability Probe & Race Condition in Guard
- **Location**: [`AndroidIrTransmitter.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/fabric/infrared/AndroidIrTransmitter.kt#L62)
- **Defect**: `hasEmitter` was evaluated as `manager != null` rather than `manager?.hasIrEmitter() == true`. `transmitInFlight` used `@Volatile` check-then-set logic subject to multi-thread race conditions.

### 1.7 False UI State Displays
- **Location**: [`TvControlScreen.kt`](file:///Users/jordelmirsdevhome/Downloads/celular/Control%20Universal/apps/android/app/src/main/java/com/elysium/nexus/ui/control/TvControlScreen.kt#L194)
- **Defect**: UI displayed "Conectado" and "Enviado" before or regardless of `ConsumerIrManager.transmit()` execution results or user confirmation.

---

## 2. Remediation Plan Matrix

| Item | Subsystem | Action Required | Status |
| :--- | :--- | :--- | :--- |
| **P0-1** | `IrWaveform` | Remove `pattern.add(0)`, enforce `it > 0`, allow odd pattern size, cap total duration < 2s | Pending |
| **P0-2** | NEC Codec | Implement 32-bit LSB-first physical NEC frame + 560 µs stop mark | Pending |
| **P0-3** | Codecs | Remove silent NEC fallbacks, return `UnsupportedProtocol` | Pending |
| **P0-4** | Domain Model | Create `IrAction`, `IrSignal`, `IrCodeSet`, separate from `DeviceButton` | Pending |
| **P0-5** | Probe Engine | Create `IrProbeEngine` with `VOLUME_UP` primary action & candidate progression | Pending |
| **P0-6** | Android Adapter| Add `IrTransmitResult` sealed hierarchy, `hasIrEmitter()` check & `Mutex` locking | Pending |
| **P0-7** | UI Feedback | Display typed transmission results & unverified profile status | Pending |
| **P0-8** | ADRs & Docs | Create ADR-001, ADR-002, ADR-003, and changelog | Pending |
