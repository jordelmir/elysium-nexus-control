# Elysium Nexus IR Data Source & Licensing Policy

## 1. Compliance Matrix

| Source Name | License Type | Ingestion Allowed | Bundling in Production APK | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Flipper-IRDB** | CC0-1.0 | YES | YES | Audit SHA-256 hash per imported file |
| **SmartIR** | MIT | YES | YES | Preserve MIT copyright attribution |
| **probonopd/irdb** | Custom Open | YES | CONDITIONAL | Keep notice, open issue notification |
| **radioxoma/infrared** | MIT | YES | YES | Reference vectors for unit tests |
| **LIRC Remotes** | Mixed / GPL | YES (Quarantine) | NO | Local research only until audited |
| **Global Caché** | Proprietary | NO | NO | Restricted to Global Caché hardware |
| **RemoteCentral** | Copyrighted | NO | NO | User manual reference only |

## 2. Ingestion Rules
1. No raw dataset without a recorded source artifact SHA-256 hash, URL, and SPDX license declaration shall be bundled into release data packs.
2. Unverified datasets default to `VerificationStatus.UNVERIFIED`.
3. Profiles are promoted to `VERIFIED_LAB` only upon physical logic analyzer / external TSOP capture confirmation.
