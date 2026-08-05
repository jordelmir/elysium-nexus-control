# PHASE 0.2 CHANGELOG — Universal Control & Database Expansion

**Date:** 2026-08-04  
**Author:** Antigravity  
**Status:** VERIFIED_LAB  

## Summary of Deliverables

### 1. Smart TV & Streaming Hub Adapters (`com.elysium.nexus.fabric.adapter.smarttv`)
- **`RokuEcpAdapter.kt`**: REST API protocol (ECP port 8060). Keypresses, app launch by ID, device info XML parsing.
- **`LgWebOsAdapter.kt`**: SSAP WebSocket protocol (port 3000/3001). Manifest registration payload, client key storage, SSAP URIs for audio, power, media, apps, toasts.
- **`SamsungTizenAdapter.kt`**: WebSocket JSON `ms.remote.control` protocol (port 8001/8002). Authentication tokens, `KEY_*` commands.

### 2. Industrial-Grade Open-Source IR Database (`databases/devices/ir_codes_db.json`)
- Coverage of **22+ global brands** (Samsung, LG, Sony, Panasonic, Vizio, TCL, Hisense, Philips, Toshiba, Sharp, Denon, Yamaha, Bose, Daikin, Mitsubishi, Carrier, Epson, BenQ, JBL, Sonos, Apple TV, Fire TV).
- Carrier frequency, duty cycle, mark/space durations (µs) for **NEC, NECext, Samsung32, RC5, RC6, SIRC15, Kaseikyo, Denon**.
- Aggregated into APK assets for instant Room persistence query.

### 3. Precision Gyroscopic Air Mouse Engine (`AirMouseEngine.kt`)
- **1€ Filter (Casiez 2012)**: Adaptive low-pass filtering eliminating jitter at rest without lag.
- **Sensor Fusion**: Gyroscope + Game Rotation Vector.
- Dead-zone filtering (0.005 rad/s) & flick gesture detection.

### 4. 1-Tap Scene & Macro Engine (`SceneExecutionEngine.kt`)
- Multi-protocol sequence execution with configurable delays and auto-retries.
- Built-in Cinema Mode & Gaming Mode templates.

## Verification
- Unit tests (`./gradlew :app:testDebugUnitTest`): **PASSED**
- Assembly (`./gradlew :app:assembleDebug`): **PASSED**
- Lint Audit (`./gradlew :app:lintDebug`): **PASSED (0 Errors)**
