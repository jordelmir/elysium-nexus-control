# Elysium Nexus Universal Control OS — Release Notes v0.6.0 (branch draft)

> **State: DRAFT — NOT RELEASED.** This documents the `fix/v0.6-physical-truth-and-identity-gate` branch implementing the external audit's 70-section order **ELYSIUM NEXUS v0.6 — PHYSICAL TRUTH, DEVICE IDENTITY & PRODUCTION GATE**.
> No tag, no release until §69/§70 (Physical Truth Gate) — see `docs/audits/V06_REALITY_LEDGER.md`.

---

## Mission

One chain of truth: INTENT → IDENTITY → RESOLUTION → ROUTING → EXECUTION → EFFECT → OBSERVATION → EVIDENCE → RECOVERY. Fail-closed, deterministic, reproducible. Phase plan PTG-01 → PTG-16 mapped in the Reality Ledger.

## Shipped in this branch

### PTG-01 — Reality Ledger + Catalog Installer Stop-the-Line (§1, §2, §10) — `9c6d031`
- **`docs/audits/V06_REALITY_LEDGER.md`** — every relevant module classified ONCE on a 9-rung ladder (DESIGNED → PRODUCTION_APPROVED), P0 stop-the-line table (installer, eligibility, signing, plaintext credentials, LG skeleton), phase map PTG-01→PTG-16. Zero claims above INTEGRATION_VERIFIED; zero physical-device claims.
- **Catalog installer rebuild** — `EXPECTED_MANIFEST_HASH` hardcode deleted; manifest is the single authority; strict JSON-lite parser (`CatalogManifest.kt`, 7 identity fields, null schema → REJECTED, fail-closed); `builds/<catalogBuildId>/` layout with atomic pointer swap, rollback-keep (previous build retained), legacy-root adoption; temp → fsync → SHA → integrity check → promote.
- **Build identity (§9/§10)** — `catalogBuildId = SHA256(ptg-v1|schema|canonical|lock|rejections|licenses|policy)`; stats artifact rewritten in the same step with the same buildId (stale-artifact divergence is now a hard failure).

### PTG-02 — Eligibility Gate + Strict Ingestion + Provenance Fail-Closed (§3, §7, §8) — `00e1a41`
- **`tools/ir-data/check_catalog_eligibility.py`** — read-only SQL gate, exit 0/1, wired **CI fast Gate 3b** + **android-ci Gate 2c**. Real gate run:
  - Probe surface (runtime pipeline): POWER 1,432 · VOLUME_UP 964 · VOLUME_DOWN 812 · MUTE 891 — **PASS**.
  - Eligible surface: **RED by design** — all 2,350 code sets `INTERNAL_UNVERIFIED`; the gate proves the evidence gap instead of faking it (PTG-05 evidence pipeline flips it green).
- **Product bug found and fixed**: power probe was dead on fresh installs (canonical key is `POWER_TOGGLE`, not `POWER`). Runtime candidate queries now include `INTERNAL_UNVERIFIED` (probing-to-verify is the pipeline's purpose; only BLOCKED is excluded).
- **Strict ingestion (§7)** — unknown protocol → structured REJECTION (source/file/row/reason/detail/action/protocol), no 38 kHz invention; `ir_catalog_rejections.json` real counts participate in build identity.
- **Provenance fail-closed (§8)** — `sources.lock.json` is the sole revision-identity authority; zero-hash fabrication impossible in production builds; license `APPROVED` only with lock evidence (`licenseFileSha256` + `sourceContentSha256`), else `AWAITING_EVIDENCE`.

## Not in this branch (honest list)

- No eligible (claimed) production candidates yet — evidence pipeline is PTG-05.
- No physical hardware results (LG TV/HIL/matrix) — documented, never claimed; PTG-15.
- Debug-signed only; production signing PTG-14. Plaintext paired-device credentials still in Room — PTG-07.
- Gradle verification pending Jor's explicit batch order (testDebugUnitTest/lintDebug/assembleDebug/compileDebugKotlin) per working contract.