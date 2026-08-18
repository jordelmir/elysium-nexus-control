# PHASE V07_18 — RELEASE/CONFORMANCE BLOQUE E (fases 27–34, 36)

**Commit de entrega:** `56ee48d` — 17 de agosto de 2026.

## Fases del Master Order v0.10 cerradas

- **27 — Play Accessibility compliance**: `docs/compliance/PLAY_ACCESSIBILITY_COMPLIANCE.md`
  (declaración, aviso destacado, consentimiento afirmativo, política privacidad,
  video de demo Play, prohibiciones de automatización). Test nuevo en
  `TvNodeCoreTest`: core volumen `Confirmed` con SOLO `volumeExecutable` — sin
  Accessibility (la ruta core jamás depende del Enhanced Mode).
- **28 — Supabase rotation runbook**: `docs/security/SUPABASE_ROTATION_RUNBOOK.md`
  con política POTENTIALLY_COMPROMISED hasta rotación demostrada, procedimiento
  7 pasos y tabla de eventos (fila roja pendiente = acción del propietario).
- **29 — Secret scanning v2 (gitleaks)**: `.gitleaks.toml` + Gate 0b en
  `android-ci.yml`. Eliminada la zona ciega `':!docs/*' ':!*.md'` (P0-20).
  Scan local verificado: **0 hallazgos en árbol + 0 en historia completa**
  (`--log-opts=--all`, 232 commits). Allowlists explícitas solo: placeholders de
  `.env.example`, el secreto retractado documentado en changelogs (P0-5, solo
  menciones de su ELIMINACIÓN), y el FP de `KeyAgreement.PrivateKey` (CryptoKit).
- **30 — Cloud RLS-first**: `docs/supabase/RLS_FIRST_ARCHITECTURE.md` — Auth
  antes de tablas, RLS en todas, ownership user/device, roles retailer,
  evidence append-only (UPDATE/DELETE `using(false)`), audit logs, rate limits,
  service_role server-only; el core local nunca depende del cloud.
- **31 — TV Node CI dedicado**: `.github/workflows/tv-node-ci.yml` — secret
  guard → JVM tests (golden crypto, framing, pairing, E2E TCP) → lint →
  assembleDebug → reporte → job release con `apksigner verify` (fail-closed
  Phase 33). El TV Node ya no viaja de acompañante del Controller.
- **33 — TV Node release signing fail-closed**: identidad INDEPENDIENTE
  (`tv-node-release.jks` + `TV_NODE_RELEASE_*` env vars — jamás reutiliza el
  keystore del Controller). Guard `taskGraph.whenReady` verificado en local:
  `assembleRelease` sin credenciales → **BUILD FAILED** con el mensaje
  "TV NODE RELEASE SIGNING BLOCKED" (comportamiento deseado).
- **34 — Release channels**: `docs/releases/CHANNELS.md` —
  nightly/engineering-preview/beta/retail-rc/stable; debug NUNCA stable; stable
  solo con PRODUCTION_APPROVED. `v0.9.0` (debug, latest) debe reclasificarse
  como Prerelease — acción registrada para el propietario.
- **36 — Sankey evidence migration**: `databases/evidence/sankey-tv.json` —
  registro durable de los 5 templates Sankey (TV×3, curved, AC) y 4 marcas
  regionales con `evidenceStatus: NONE_PHYSICAL_EVIDENCE`; prohibición explícita
  template-as-truth y ruta hacia evidencia física (oracle Phase 25/26 / HIL).

## Verificación (dirigida)

- `TvNodeCoreTest` (nuevo test Fase 27) ✅
- TV Node `assembleRelease` fail-closed ✅ (falla diseñada verificada)
- gitleaks tree + history: ✅ 0/0
- Gates Python: `final_truth_gate.py` PASS, `tv_claim_policy --check` OK,
  `THIRD_PARTY_NOTICES` current ✅

## Pendiente (propietario)

1. Rotar service-role Supabase (P0-19)
2. Provisionar secrets de firma (controller + tv-node) para el primer CI release verde
3. Reclasificar v0.9.0 a Prerelease
4. TV físico: Fase 35 E2E + primera evidencia real del oracle (Fase 26)