# Phase V0.7-19 — Full Verification + Truth Convergence Closeout

> Fecha: 2026-08-18 · Tipo: verificación batch completa y cierre de la orden v0.10
> Orden: "completa al 100% y sincroniza todo en GitHub" (Jor)

## Resumen

Cierre de la orden maestra v0.10 (TRUTH CONVERGENCE). Todo el trabajo de
ingeniería **sin hardware físico** ya estaba implementado en la rama
`fix/v0.10-truth-convergence`; esta fase ejecutó la **verificación batch
completa** (Fase 19 de la orden: correr TODAS las suites antes de desarrollar
más), auditó los P0 de la re-auditoría contra el código vivo, fijó lo que
quedaba, actualizó registros y sincronizó el repo en GitHub.

## Auditoría P0 contra código vivo (2026-08-18)

| P0 | Hallazgo de auditoría | Estado en código vivo |
| --- | --- | --- |
| P0-1 | `PhysicalTestEvidence.status` default `HIL_VERIFIED` | ✅ `val status: PhysicalEvidenceStatus` — tipado, SIN default; `EvidenceRecorder.promoteToHil()` es la única vía y exige artefactos |
| P0-2 | Una tecla → SKU "CORE verified" | ✅ `ClaimPromotionEngine.deriveCoreMatrix()` con `CoreActionPolicy.TV_CORE_ACTIONS` completa; `isCoreComplete = all PASS` |
| P0-3 | `regressionCount = 0` hardcodeado | ✅ computado: `matrix.hasRegression` incrementa `regressionCount` y bloquea 100% |
| P0-4 | Dos motores de verdad | ✅ `EvidencePolicyEngine.kt` + policy declarativa `retail-core-policy-v1.json` (usada por Kotlin, tests, Python gate) |
| P0-5/6/7 | signerSecret hardcodeado, SHA256(secret), sin validación de ownership | ✅ eliminado del código (solo allowlist gitleaks de historial); `CertificateSigner` Ed25519 con clave pública en APK; verificación real de firma y payload canónico |
| P0-8 | "51-pantalla baseline" = 8 hardcoded | ✅ `getMongeResearchBootstrapSample()` / `getVerdugoResearchBootstrapSample()`, `productionEligible = false`; `RetailCoverageEngine` rechaza feeds no elegibles |
| P0-9 | Report audita bindings como signals | ✅ `uniqueSignalsAudited=173566` + `bindingsAudited=223571` separados en `runtime-executable-report.json` |
| P0-10 | Gate comercial nominal | ✅ `commercialGate.isPass=false` honesto (11910 unsupported globales); KPI target-retail documentado |
| P0-11 | Legal por prosa | ✅ `LegalEvidenceLedger.kt` + `tools/legal/generate_third_party_notices.py --check` |
| P0-12 | NSD `port = 0` | ✅ `TvLinkListener`: server socket bound → puerto real → NSD; `port = 0` nunca anunciado |
| P0-13 | Pin de 32 bits | ✅ identidad = full SHA-256 (64 hex); shortcode display-only |
| P0-14 | Dos key domains QR/handshake | ✅ QR lleva fingerprint de la clave pública del nodo; handshake presenta la misma |
| P0-15 | `pairingGate` nullable | ✅ constructor `TvLinkServer(dispatcher, pairingGate: PairingGate)` — MANDATORIO |
| P0-16 | Vault error → autoriza | ✅ `VaultResult.Error/NotFound → DENY`, nunca proceed |
| Fase 17 | Nonce 64 bits | ✅ `PairingNonce` 16 bytes (128 bits), regex `^[0-9a-f]{32}$` |
| Fase 18 | Vault sin AAD/256/KeyInfo | ✅ AES-256-GCM (`setKeySize(256)`), `KeyInfo.securityLevel` leído en runtime, durabilidad verificada |
| Fase 19 | Suite TV Node sin correr | ✅ **EJECUTADA: 108/108** ↓ |
| 22-24 | Capability ordinales / IME | ✅ `TvCapabilityGrants`: grants independientes (`imeInstalledAndEnabled`, notification, accessibility); sin ordinales como política |
| P0-19 | Rotación | 🔴 propietario: script fail-closed listo, espera `SB_ACCESS_TOKEN` |
| P0-20 | Guard con zonas ciegas | ✅ gitleaks v2 sobre árbol completo + historial, allowlist explícita, 0+0 hallazgos; Gate 0b en CI |

## Verificación batch ejecutada (evidencia)

| Build | Comando | Resultado |
| --- | --- | --- |
| Controller | `testDebugUnitTest` | ✅ **1347/1347**, 0 failures/errors (155 suites) |
| Controller | `lintDebug` | ✅ 0 errors |
| Controller | `assembleDebug` | ✅ APK debug |
| Controller | `assembleRelease` + apksigner | ✅ firmado `CN=Elysium Nexus Controller` |
| TV Node | `testDebugUnitTest` | ✅ **108/108**, 0 failures/errors (15 suites) |
| TV Node | `lintDebug` | ✅ 0 errors |
| TV Node | `assembleDebug` | ✅ APK debug |
| TV Node | `assembleRelease` + apksigner | ✅ firmado `CN=Elysium Nexus TV Node` |
| Supabase | keys + pooler + MCP | ✅ 4 credenciales válidas; DB RLS-first vacía; MCP OAuth completado |
| gitleaks | tree + historial | ✅ 0 + 0 |

## Matriz de madurez (post-cierre)

| Subsistema | Madurez |
| --- | --- |
| Retail Truth constitution | IMPLEMENTED |
| Evidence model v2 + store inmutable + policy engine | UNIT_VERIFIED |
| Claim promotion CORE matrix | UNIT_VERIFIED |
| Certificate Ed25519 | UNIT_VERIFIED |
| Retail feed truth (bootstrap ≠ authority) | UNIT_VERIFIED |
| Runtime IR policy (LAB_ONLY gate) | UNIT/INTEGRATION_VERIFIED |
| TV Node core slices 1–5 | **UNIT_VERIFIED (108/108)** |
| Phone→TV Node wire E2E (TvLinkPhoneLink + OBSERVE_VOLUME) | INTEGRATION_VERIFIED |
| Controller release signing | VERIFIED fail-closed + firmado local |
| TV Node release signing | VERIFIED fail-closed + firmado local |
| Real TV E2E (Fase 35) | NO DEMOSTRADO (requiere TV físico) |
| Physical evidence catalog | VACÍO (0 filas — honesto) |
| RETAIL_MATRIX_VERIFIED / PRODUCTION_APPROVED | NO GANADA |

## Pendientes propietario (externos, documentados con gate exacto)

1. `SB_ACCESS_TOKEN` → rotar service-role + DB password (P0-19).
2. GitHub Secrets (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`,
   `TV_NODE_RELEASE_STORE_PASSWORD`, `TV_NODE_RELEASE_KEY_PASSWORD`) → primer
   release firmado POR CI (los workflows ya están listos y fail-closed).
3. Reclasificar la release v0.9.0 actual a Prerelease / engineering-preview.
4. TV físico → Fase 35 E2E + primera evidencia real del oracle.

## Archivos tocados

- `docs/V010_STATUS.md` — verificación batch + estado actualizado.
- Este changelog.
- Push: `origin git@github.com:jordelmir/elysium-nexus-control.git` (rama `fix/v0.10-truth-convergence`).