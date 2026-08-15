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

## ⬇️ Descarga / Download (v0.9.0)

Descargables públicos reconstruidos a `main` (`7ddaac2`), verificado cada uno por SHA-256:
<https://github.com/jordelmir/elysium-nexus-control/releases/tag/v0.9.0>

| App | Archivo | SHA-256 |
|-----|---------|---------|
| Controller (Android phone) | `ElysiumNexus-UniversalController-v0.7.0-retail-truth-debug.apk` | `06353574554ddc26f7d2b2d7fc931015d578e9fdf2182a7a04e3bd24d11f9365` |
| TV Node (Android TV / Google TV) | `ElysiumNexus-TVNode-v0.1.0-debug.apk` | `2dc1d6bcf6db0c16ef2f45c64b88bc2325c9201b4afa353f93f3d508cf3b8c13` |
| Mac Agent (menú bar) | `Elysium-Nexus-Mac.dmg` | `ce8e33f4b135ff319d8984d5a0162700e7a147e92202f1c5db50949f8af312e3` |

```bash
adb install -r ElysiumNexus-UniversalController-v0.7.0-retail-truth-debug.apk
adb install -r ElysiumNexus-TVNode-v0.1.0-debug.apk
open Elysium-Nexus-Mac.dmg
```

> [!NOTE]
> Los APK publicados son **debug** firmados con la keystore local, porque la
> firma de release exige credenciales env verificadas (Regla Comercial Hard #9,
> fail-closed). Docs anteriores usan la misma política (v0.6.3 publicó
> `app-debug.apk`). El camino retail de control físico garantizado es el
> **Elysium Nexus Bridge**; ADB Wi-Fi es `DEVELOPER_ONLY`.

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

# --- TV Node (subyacente en apps/android-tv-node, wrapper propio) ---
cd apps/android-tv-node
./gradlew :app:assembleDebug        # TV Node debug APK (0.1.0-tvnode)
./gradlew :app:testDebugUnitTest    # JVM tests del TV Node (99 esperados tras slice 5)
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
