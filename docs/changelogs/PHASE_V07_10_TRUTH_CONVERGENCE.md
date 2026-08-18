# PHASE V07-10 — TRUTH CONVERGENCE: EVIDENCE MODEL V2 + CORE MATRIX + CERTIFICATE CRYPTO + RETAIL FEED TRUTH + SUITE TV NODE

> Date: 2026-08-16. Branch: `fix/v0.10-truth-convergence`.
> Order: **Master Order v0.10 (auditoría EV-REA sobre `afcebd1`)** — Phases 0–11
> y 19 ejecutadas: STOP THE LINE, Evidence Model V2, Per-Action CORE Matrix,
> Regression Engine, Claim Status derivado, Certificate Crypto V2, Canonical
> Payload, Retail Feed Truth, Executable Report V2 y la suite completa del
> TV Node. Subsistemas corregidos: ClaimPromotionEngine, CompatibilityCertificateEngine,
> RetailFeedIngestionEngine (modo autoritativo), rutas de creación de evidencia.
> Maturity: Retails claims siguen **BLOCKED** (0 evidencia física) — ahora por
> construcción. TV Node slices 4/5: **IMPLEMENTED → UNIT_VERIFIED** (suite 95/95).

## PHASE 0 — STOP THE LINE (commercial blockers)

Los tres componentes de "truth" fueron los primeros en corregirse porque podían
declarar más verdad comercial de la que la evidencia contiene. Ninguna ruta
runtime depende aún de ellos; es código de promoción/certificación autocontenido.

## PHASE 1 — EVIDENCE MODEL V2 (P0-1 eliminado)

`RetailDataModel.kt`:
- **Eliminado** `status: String = "HIL_VERIFIED"` (el default hacía que
  construir evidencia SIN status naciera como HIL — falsa verdad, P0-1).
- Nuevo enum tipado **`PhysicalEvidenceStatus`** (sin default): `RUNTIME_EXECUTABLE,
  ON_DEVICE_TRANSMITTED, REAL_DEVICE_OBSERVED, INDEPENDENT_DECODE_VERIFIED,
  HIL_VERIFIED, REGRESSION, FAILED` + `isPass`/`isFailure` de conveniencia.
- `PhysicalTestEvidence.status` ahora es **obligatorio** (fail-closed por tipos).

`EvidenceRecorder.kt` (nuevo):
- `EvidenceRecorder.recordRuntime / recordOnDeviceTransmitted / recordRealDevice /
  recordFailure` — las ÚNICAS puertas de creación; validan identidad (id/model/
  acción/señal/SHA/carrier>0) y el recorder de fallos rechaza statuses de paso.
- `EvidencePromotionService.promoteToHil(evidence, HilArtifacts)` — la ÚNICA ruta
  a `HIL_VERIFIED`; exige artifacts dual-path completos (rawCaptureRef +
  independentDecoderRef) y rechaza evidencia en fallo. Sin artifacts → null.

## PHASE 4 — PER-ACTION CORE MATRIX (P0-2 eliminado)

`CoreActionPolicy.kt` (nuevo): `TV_CORE_ACTIONS` = POWER_TOGGLE, VOLUME_UP,
VOLUME_DOWN, MUTE, INPUT_SELECT, UP, DOWN, LEFT, RIGHT, OK, HOME, BACK —
política `retail-core-policy-v1`. `CoreActionResult` (PASS/FAIL/UNSUPPORTED/
NOT_APPLICABLE/REGRESSION/PENDING). `ClaimPromotionEngine.deriveCoreMatrix` ahora
deriva el resultado POR ACCIÓN; `isCoreComplete` exige TODAS satisfechas. Un solo
VOLUME_UP ya NO eleva un SKU (test de regresión incluido).

## PHASE 5 — REGRESSION ENGINE (P0-3 eliminado)

- `regressionCount = 0` hardcodeado ELIMINADO: se computa de la evidencia —
  cualquier REGRESSION/FAILED en una acción CORE de un modelo conocido
  incrementa `regressionCount` y bloquea `is100PercentCoreVerified`. REGRESSION
  domina sobre evidencia de paso para la misma acción (fail-closed).
- `deriveClaimStatus` ahora devuelve `DerivationResult(status, hasRegression)`
  — un regresión NO se esconde en el status derivado.

## PHASE 6 — CLAIM STATUS DERIVED (nunca ordinal)

- Comparaciones `status >= X` por ordinal ELIMINADAS: ahora `isAtLeast()` usa el
  partial order explícito `STATUS_ORDER`. Ningún caller puede escribir el status.

## PHASES 7-8 — CERTIFICATE CRYPTO V2 (P0-5/P0-6/P0-7 eliminados)

`CertificateCrypto.kt` (nuevo):
- Interfaces `CertificateSigner` / `CertificateVerifier` + implementaciones
  **Ed25519** reales (`java.security.Signature "Ed25519"`, JVM/CI/Android moderno).
- `generateEd25519KeyPair()` para tests y tooling offline. La private key JAMÁS
  entra al APK ni al repo (solo la pública verifica).

`CompatibilityCertificateEngine.kt` reescrito:
- `signerSecret = "ELYSIUM_NEXUS_RETAIL_TRUTH_KEY_2026"` y SHA-256(payload+secret)
  **ELIMINADOS** (P0-5). Firma asimétrica real vía [CertificateSigner] (P0-6).
- `issueCertificate(sku, evidenceList, signer, appCommit, catalogBuildId,
  validUntil)` — fail-closed ANTES de firmar:
  - evidencia vacía → null; SKU sin modelo → null;
  - **evidencia de otro model → null** (P0-7: wrong-device impossible);
  - cualquier FAILED/REGRESSION → null;
  - todas las CORE actions del policy con evidencia de paso → si no, null.
- Payload canónico determinista con schemaVersion, policyVersion, keyId,
  evidenceIds, evidenceShas, build identity, ventana de validez (P0-8 shape).
- El modelo `RetailCompatibilityCertificate` creció: deviceModelId, evidenceIds,
  schemaVersion, policyVersion, appCommit, catalogBuildId, validFrom/validUntil,
  keyId, signatureAlgorithm (sin defaultes de secretos).

## PHASES 9-10 — RETAIL FEED TRUTH (P0-8 eliminado)

`RetailFeedIngestionEngine.kt` reescrito:
- `getMongeActiveSnapshot()`/`getVerdugoActiveSnapshot()` → **`getMongeResearchBootstrapSample()` /
  `getVerdugoResearchBootstrapSample()`** con `productionEligible = false` y
  `sourceAuthority = "research-bootstrap"`. El claim "official 51-pantalla baseline"
  desapareció: el artefacto declara recordCount real (8 y 6) + contentSha256 real.
- Nuevo `RetailFeedArtifact` (retailer, snapshotId, sourceAuthority, retrievedAt,
  recordCount, contentSha256, records, productionEligible) — la forma autoritativa
  que exigirá la ruta productiva (partner API / snapshot versionado firmado).
- `RetailCoverageEngine.computeCoverage(artifact, evidenceMap)` — **refusa**
  (null) cualquier feed no production-eligible: una muestra de bootstrap JAMÁS
  puede alimentar números comerciales.

## PHASE 11 — EXECUTABLE REPORT V2 (P0-9/P0-10)

`tools/ir-data/catalog_executable_report.py`:
- `totalSignalsAudited` (contaba BINDINGS: 223.571) → **`uniqueSignalsAudited`
  (173.566) + `bindingsAudited` (223.571) + `uniqueSignalsBound`**. La clasificación
  pasa a DISTINCT por señal.
- Reporte regenerado (`runtime-executable-report.json`, version 0.10.0-truth-convergence):
  RAW 80.829, PARAMETRIC 66.737, EXPERIMENTAL_LAB_ONLY 17.608, UNSUPPORTED 8.392;
  commercialGate `isPass=false` (honesto: unsupported global ≠ 0; el KPI productivo
  será unsupportedTargetBindings=0 por SKU objetivo, fuera de alcance de esta entrega).

## PHASE 19 — TV NODE SUITE EJECUTADA (orden explícita)

`apps/android-tv-node` — `:app:testDebugUnitTest` **95/95, 0 failures** +
`lintDebug` 0 errores + `assembleDebug` green (9s + 18s, config cache reusada).
Breakdown por clase: PairingGateTest 11, TvLinkProtocolTest 19, TvLinkTransportTest 5,
TvActionExecutorTest 6, VolumeActionInterpreterTest 6, TvLinkHandshakeTest 9,
PairingNonceTest 2, TvChannelCryptoTest 12, PairingSessionTest 10, PairingCodeTest 4,
TvNodeCanonicalTest 5, TvCredentialVaultTest 6. (La expectativa "~99" del auditor
era aproximada; el conteo real verificado es 95.)

→ Slices 4/5 promovidos a **`UNIT_VERIFIED`** en sus changelogs (PHASE_V07_TV_06/07).

## VERIFICATION SUMMARY

| Gate | Resultado |
| --- | --- |
| Suite TV Node (95 tests) | ✅ 0 failures |
| lintDebug TV Node | ✅ 0 errores |
| assembleDebug TV Node | ✅ green |
| Python report regenerate | ✅ determinista, gate FAIL honesto |
| Controller JVM suite | ⏳ PENDIENTE de orden de Jor (verify-on-request) — código nuevo escrito con tests, sin compilar aún |

## NEXT (per v0.10)

- Phases 12–18 (legal ledger, shared domain authority, TV Node pairing identity
  V2, pairing transcript, gate mandatory, vault fail-closed, vault V2) +
  Phase 20+ (listener con puerto real, controller-side client, QR UX, capability
  truth V2, lifecycle). El suite TV Node ya quedó verde para buildar encima sin
  sorpresas.
- (Jor) orden de "haz las pruebas" → batch del Controller para verificar los
  tests nuevos del truth layer.