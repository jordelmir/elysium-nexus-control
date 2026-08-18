# PHASE V07-14 — FINAL TRUTH GATE + Evidence Store + One Policy Engine + Legal Ledger + Capability Grants

Branch: `fix/v0.10-truth-convergence` · Fecha: 2026-08-17
Commit: `74f54f2`
Alcance: Master Order v0.10, fases 2, 3, 10, 12, 23 + Final Commercial Truth Gate (instalado como gate CI permanente).

## Qué se entrega

### Fase 2 — IMMUTABLE EVIDENCE STORE
- `EvidenceStore` (interfaz) + `JsonLineEvidenceStore` (JSONL append-only, fsync antes de retornar, sin APIs de update/delete).
- `supersede()` crea registros tombstones que enlazan al superseded SIN tocar el original; el archivo no se reescribe jamás (load verifica contigüidad de seq 1..N, fail-closed ante archivo reescrito/corrupto).
- Formato JSONL neutral: también consumible por tooling Python / revisión legal.

### Fase 3 — ONE EVIDENCE POLICY ENGINE (política declarativa única)
- `schemas/protocol/retail-core-policy-v1.json`: autoridad única (coreActions TV, claimLadder, rules: noDefaultStatus, regressionDominates, hilRequiresDualPathArtifacts, researchFeedsNotProductionEligible, maturityScale).
- Kotlin `EvidencePolicyEngine`: parse fail-closed + `verifyAgainstInAppConstants()` — cualquier divergencia con `CoreActionPolicy`/`CLAIM_LADDER` rompe.
- Python `tools/ir-data/tv_claim_policy.py`: consume el MISMO JSON; `--check` verifica JSON == Kotlin (regex de constantes) == semántica Python, y que schemas == copia test-resource byte-idéntica. Gate CI 3a.
- `ClaimPromotionEngine.STATUS_ORDER` expuesto públicamente como `CLAIM_LADDER` (contrato del cross-check).

### Fase 10 — RETAIL FEED SOURCES autoritativos
- `RetailFeedSource` (interfaz): `SignedCsvRetailFeed` (firma SHA-256 del payload crudo + hash canónico de contenido), `PartnerApiRetailFeed` (solo partners autorizados, fail-closed), `VersionedSnapshotRetailFeed` (manifiesto versionado, resuelve la última versión y la verifica).
- Todo éxito es production-eligible por construcción; cualquier inconsistencia => `Failure` (nunca un artefacto degradado). Bootstrap research permanece `productionEligible=false`.

### Fase 12 — SUPPLY CHAIN LEGAL EVIDENCE LEDGER
- Kotlin `LegalEvidenceLedger`: máquina de estados controlada `UNREVIEWED → REVIEW_REQUIRED → DOCUMENTED → SATISFIED` (o `BLOCKED`), con `transitionGuard` que rechaza transiciones ilegales.
- `legal-evidence/ledger.json`: 4 entradas (probonopd-notification, flipper-file-provenance, hardware-copy-obligation, legal-review-status = REVIEW_REQUIRED => blocker pre-release honesto).
- `tools/legal/generate_third_party_notices.py`: genera `THIRD_PARTY_NOTICES.md` SOLO desde el ledger + sources.lock.json; `--check` (gate CI 3b) detecta staleness. Sin claims de cumplimiento por prosa: toda obligación tiene entrada de ledger + artifact path.

### Fase 23 — CAPABILITY TRUTH V2 (sin ordinales)
- `TvAccessLevel` (escalera ordinal) ELIMINADO. Sustituido por `TvCapabilityGrants`: 12 booleans independientes (accessibilityGranted, notificationListenerGranted, imeInstalledAndEnabled, globalHome/Back/DpadAvailable, mediaTransportGranted, volumeObservable/Executable, keyFilteringObservable, volumeFixed).
- `TvActionExecutor` gatea con `grants.volumeExecutable`; `CapabilityManifestBuilder` deriva desde facts + grants, nunca por comparación ordinal.
- El Final Truth Gate (Z6) detecta por regex cualquier reintroducción de `TvAccessLevel.ordinal` o comparación ordinal.

### FINAL COMMERCIAL TRUTH GATE
- Kotlin `FinalTruthGate.failures()` + `FinalTruthGateTest`: 8 comprobaciones ejecutables en módulo (sin status por defecto, política única, regressionCount computado, matriz CORE completa, HIL requiere artifacts duales, firma Ed25519 sin secretos, research feeds nunca comerciales, claims derivados nunca escritos).
- Python `tools/truth_engine/final_truth_gate.py`: scanner repo-wide Z1–Z7 (HIL hardcodeado, regressionCount literal ≠ 0, claims "100%" sin evidencia, secretos tipo JWT/Supabase, policy researchFeeds, comparaciones ordinales, notices stale). Gate CI 3c.
- Ambos gates quedan cableados en `android-ci.yml` (3a/3b/3c tras ruff).

## Verificación (evidencia)
- Controller: `:app:compileDebugKotlin` BUILD SUCCESSFUL; tests dirigidos Bloque A (evidence+legal+gate) 30/30; `:app:lintDebug` BUILD SUCCESSFUL.
- TV Node: suite completa **100/100** (99 previos + 1 nuevo test de grants independientes), 0 failures/errors, tras eliminar la escalera ordinal.
- `tv_claim_policy.py --check`: OK (JSON == Kotlin == Python).
- `generate_third_party_notices.py --check`: OK.
- `final_truth_gate.py`: **PASS** (exit 0).
- ruff: 4 errores W291 corregidos con `--fix`; 0 restantes.

## Estado multi-fase (acumulado en la rama)
Fases v0.10 completadas a la fecha: 1–12, 14–21, 23 + Final Truth Gate. Pendientes: 13 (documentar autoridad :tvlink como Phase 13), 22 (QR UX), 24 (lifecycle), 25/26 (IR Oracle + evidencia), 27–34 (accessibility Play, rotación Supabase, gitleaks, RLS, CI TV Node, toolchain, signing TV Node, canales), 35+ (hardware/retail real).

## Notas de reconciliación
- La Fase 3 exige un único engine consumido por runtime Kotlin, gate Python y CI: la semántica de derivación vive en `ClaimPromotionEngine` (in-app) y el cross-check JSON↔Kotlin↔Python garantiza identidad; el JSON es la autoridad declarativa. Documentado, no silencioso.
- La Fase 12 declara `legal-review-status=REVIEW_REQUIRED`: PRODUCTION_APPROVED queda bloqueado honestamente hasta revisión legal (nada de claims prosa de cumplimiento).
