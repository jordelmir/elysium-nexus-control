# Elysium Nexus Universal Control OS — Retail Truth Edition

> **v0.7.0-retail-truth** · Commercial Retail Control Platform targeting Monge, El Verdugo, and Gollo (Costa Rica). Built on a local-first IR Data Fabric with strict zero-false-claim physical evidence mandates.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ELYSIUM NEXUS RETAIL CONTROL PLATFORM                     │
│                                                                             │
│  [Android Phone App]  ⇄  [Elysium Nexus Bridge (IR TX/RX + BLE + USB-C)]    │
│                                      │                                      │
│                                      ▼                                      │
│                  Physical Optical Control & Verification                    │
│                                      │                                      │
│                                      ▼                                      │
│            Retail Active SKU Matrix (Monge · Gollo · El Verdugo)            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔴 Retail IR Data Fabric Metrics (v0.7.0-retail-truth)

| Asset Category | Measured Count | Retail Verification Status |
|----------------|----------------|----------------------------|
| **Brands** | 1,430 | `UNIT_SHAPE_VALIDATED` |
| **Device Types** | 264 | `UNIT_SHAPE_VALIDATED` |
| **Remotes** | 5,004 | `UNIT_SHAPE_VALIDATED` |
| **Code Sets** | 4,715 | `UNIT_SHAPE_VALIDATED` |
| **IR Signals** | 106,033 | `UNIT_SHAPE_VALIDATED` |
| **Command Bindings** | 223,571 | `UNIT_SHAPE_VALIDATED` |
| **Protocol Definitions** | 23 | `UNIT_SHAPE_VALIDATED` |
| **Protocol Variants** | 24 | `UNIT_SHAPE_VALIDATED` |
| **Explicit Rejections** | 37,753 | `PARSER_REJECTED` |
| **Physical Test Evidence** | **0** | **`COMMERCIAL_CLAIM_BLOCKED`** |
| **Retail Compatibility Assertions** | **0** | **`COMMERCIAL_CLAIM_BLOCKED`** |

> [!IMPORTANT]
> **Strict Commercial Doctrine**:
> Transmitting a waveform (`ConsumerIrManager TX_OK`) does NOT equal physical TV control.
> Claims of 100% compatibility are strictly forbidden until dual-path physical test evidence (`REAL_DEVICE_VERIFIED` / `HIL_VERIFIED`) is recorded against an exact retailer SKU MPN.

---

## 🛡️ Commercial Hard Rules (Zero False Compatibility)

1. **NO MOCK. NO FAKE TV. NO TEMPLATE-AS-TRUTH. NO GUESSED COMPATIBILITY. NO AI-GENERATED PRODUCTION IR CODE. NO SILENT FALLBACK. NO CLAIM WITHOUT EVIDENCE.**
2. **Retail Strategy**: Shift from software-only APK to hardware-backed **Elysium Nexus Bridge** for guaranteed physical control across all mobile devices (iOS & non-IR Android).
3. **Paging & Filtering**: Brand lookups strictly filter by `deviceType = TV` and use cursor-based pagination (no `LIMIT 200` truncation).
4. **Codec Eligibility**: Experimental codecs (`RC5`, `RC6`, `Kaseikyo`) are restricted to `LAB_ONLY`. Only `HIL_VERIFIED` codecs are commercial candidates.
5. **ADB Wi-Fi Reclassification**: ADB transport is strictly `DEVELOPER_ONLY` and cannot be presented as a consumer retail control path.
6. **No Hardcoded Release Secrets**: Production release signing requires explicit environment credentials (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`).

---

## 🛠️ Build & Verification Toolchain

| Component | Target SDK | AGP Version | Compile SDK | License Verification |
|-----------|------------|-------------|-------------|----------------------|
| Android App | **API 36** | 8.10.0+ | **API 36** | ✅ Lock-verified |
| IR Data Pipeline | Schema v4 | SQLite 3 | SHA-256 | ✅ Lock-verified |

### Build Commands
```bash
cd apps/android
# Run JVM unit tests
./gradlew :app:testDebugUnitTest

# Assemble Release APK (requires release env credentials)
./gradlew :app:assembleRelease
```

---

## 📄 License & Supply Chain Provenance

Included IR sources:
- **Flipper-IRDB** (CC0-1.0 locked to commit `d126fb1b`)
- **SmartIR** (MIT locked to commit `e4df2957`)
- **radioxoma/infrared** (CC0-1.0 locked to commit `96179666`)

Excluded / Gated:
- **probonopd/irdb** (GPL-2.0, gated until B2B compliance review)

Read [`AGENTS.md`](AGENTS.md) for full project rules and operating instructions.
