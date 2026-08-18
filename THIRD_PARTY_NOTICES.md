# Third-Party Open Source & Asset Notices — Elysium Nexus OS v0.10

This document provides attribution and legal compliance notices for third-party open source datasets,
protocol specifications, and curated signals incorporated into Elysium Nexus OS.
Generated from the Legal Evidence Ledger (`legal-evidence/ledger.json`) by
`tools/legal/generate_third_party_notices.py`. Legal status is decided ONLY in the ledger — never here.

---

## 1. Supply Chain Source Provenance

| Source Identifier | License Grant | Verified Commit | Tree / Content Hash | Commercial Eligibility |
| :--- | :--- | :--- | :--- | :--- |
| `flipper-irdb` | `CC0-1.0` | `d126fb1b` | `daf6bd47b726` | `COMMERCIAL_ATTRIBUTION_REQUIRED` |
| `smartir` | `MIT` | `e4df2957` | `7ec8d2db467e` | `MIT_NO_BRAND_REUSE` |
| `probonopd-irdb` | `LicenseRef-IRDB-CUSTOM` | `11aa5eb3` | `86451ba7f416` | `COMMERCIAL_ATTRIBUTION_REQUIRED` |
| `radioxoma-infrared` | `MIT` | `96179666` | `574ef5654f25` | `CURATED_INTERNAL` |
| `harctoolbox-irp-protocols` | `Public Domain` | `8636d20a` | `33a60ea3d969` | `CURATED_INTERNAL` |
| `elysium-curated-observed` | `LicenseRef-ELYSIUM-CURATED-V1` | `curated-` | `8465003647f5` | `CURATED_INTERNAL` |
| `elysium-nexus-curated` | `LicenseRef-ELYSIUM-CURATED-V1` | `curated-` | `8465003647f5` | `CURATED_INTERNAL` |
| `elysium-template-hypotheses` | `LicenseRef-ELYSIUM-HYPOTHESES` | `ir-templ` | `bdd992d62661` | `CURATED_INTERNAL` |

---

## 2. Commercial Compliance Obligations

### A. probonopd/irdb License
Elysium Nexus OS incorporates signal structures from the open-source `irdb` project.
- **Attribution Notice**: Signal data structures derived from `probonopd/irdb` are included pursuant to the IRDB License.
- **Commercial Obligations**: Pre-use notification and hardware test copy availability provisions are governed by the legal evidence ledger entry `probonopd-notification`.

### B. Flipper-IRDB (CC0-1.0)
- **Provenance Lock**: All imported signals from Flipper-IRDB are pinned to verified commits post-dating `2319685` (CC0-1.0 grant epoch). Ledger: `flipper-file-provenance` (SATISFIED).

### C. SmartIR (MIT License)
- **Brand Protection**: The "SmartIR" trademark is used solely for source attribution and does not imply endorsement of Elysium Nexus OS products.

---

## 3. Legal Evidence Ledger (Master Order v0.10 Phase 12)

| Ledger Entry | Status | Artifact Path | Obligations |
| :--- | :--- | :--- | :--- |
| `probonopd-notification` | :blue_book: `DOCUMENTED` | `ir-data/sources/lock/probonopd-irdb.lock.json` | Pre-use notification to the license steward before commercial use; Attribution notice in THIRD_PARTY_NOTICES.md section 2.A; Hardware test copy availability under the Elysium Nexus Commercial Program guidelines |
| `flipper-file-provenance` | :white_check_mark: `SATISFIED` | `ir-data/sources/lock/flipper-irdb.lock.json` | All imported signals pinned to commits post-dating 2319685 (CC0-1.0 grant epoch) |
| `hardware-copy-obligation` | :blue_book: `DOCUMENTED` | `docs/licensing/RETAIL_HARDWARE_COPY_POLICY.md` | One physical test unit per retailer SKU family available for independent verification; Test units registered with serialized evidence IDs before any RETAIL_MATRIX_VERIFIED claim |
| `legal-review-status` | :warning: `REVIEW_REQUIRED` | `ir-data/sources.lock.json` | Third-party IRDB material cleared for commercial distribution; probonopd pre-use notification delivered and confirmed |

**Pre-release review required**: `legal-review-status` — PRODUCTION_APPROVED requires them SATISFIED or BLOCKED exemption.

---
*Generated automatically by tools/legal/generate_third_party_notices.py (Master Order v0.10 Phase 12).*
