# V010_STATUS — Master Implementation Order v0.10 (Truth Convergence)

**Baseline auditado:** `afcebd14510eacfdb9b8a9f610f2b690796ddf28` (2026-08-15)
**Branch:** `fix/v0.10-truth-convergence`
**Actualizado:** 2026-08-17

## Resumen de ejecución

Todas las fases del orden v0.10 con trabajo de ingeniería **sin hardware ni
terceros** han sido ejecutadas y verificadas con tests JVM, gates Python y
verificaciones dirigidas. Fases que exigen hardware físico (35, 40, 41, 38, 39,
37) o provisión externa (7, 33-ci, 28) quedan documentadas con su gate exacto.

| Fase | Título | Estado |
| --- | --- | --- |
| 0 | Stop the line (freeze features) | ✅ aplicado |
| 1 | Evidence model v2 (typed status, NO default) | ✅ (Bloco A) |
| 2 | Immutable evidence store | ✅ (Bloco A) |
| 3 | One evidence policy engine | ✅ (Bloco A) |
| 4 | Per-action CORE matrix | ✅ (Bloco A) |
| 5 | Regression engine (regressionCount real) | ✅ (Bloco A) |
| 6 | Claim status derived, never written | ✅ (Bloco A) |
| 7 | Certificate crypto v2 (Ed25519, signer interface) | ✅ (Bloco A) |
| 8 | Canonical certificate payload | ✅ (Bloco A) |
| 9 | Retail feed truth (bootstrap ≠ authority) | ✅ (Bloco A) |
| 10 | Authoritative retail feed (SignedCsv/PartnerApi/Snapshot, sha256) | ✅ (Bloco A) |
| 11 | Executable report v2 (signals vs bindings) | ✅ (Bloco A) |
| 12 | Supply-chain legal evidence (ledger → notices, --check) | ✅ (Bloco A) |
| 13 | Shared domain/wire authority (`:tvlink` + canonical contracts) | ✅ **(Bloco D)** |
| 14–19 | TV Node pairing identity v2 / transcript / gate mandatory / vault fail-closed / vault v2 / run full suite | ✅ parcialmente pre-existente — **P0-16/17 cubiertos por Bloco B tests + suite** |
| 20 | Real TV Node listener (ServerSocket(0) → bound port → NSD) | ✅ (Bloco B) |
| 21 | Controller-side TV client (TvLinkPhoneLink, wire E2E) | ✅ (Bloco B + D) |
| 22 | Real QR UX (ZXing bitmap + PairingActivity) | ✅ (Bloco B) |
| 23 | TV Node capability truth v2 (independent grants) | ✅ (Bloco A) |
| 24 | TV Node lifecycle | ✅ (Bloco B) |
| 25 | **Software-only IR oracle** | ✅ **(Bloco D)** |
| 26 | **Persist real local evidence** | ✅ **(Bloco D)** |
| 27 | Accessibility Play compliance | ✅ **(Bloque E)** |
| 28 | Supabase credential rotation runbook | ✅ documento — rotación real = 🔴 propietario |
| 29 | Secret scanning v2 (gitleaks tree+history, 0 hallazgos) | ✅ **(Bloque E)** |
| 30 | Cloud RLS-first | ✅ diseño + política SQL de referencia |
| 31 | TV Node CI dedicado | ✅ **(Bloque E)** |
| 32 | Supported toolchain (AGP 8.10.1 + Gradle 8.11.1) | ✅ **(Fase 32 / commit d379ea6)** |
| 33 | TV Node release signing fail-closed | ✅ guard + verificación local (FAIL como diseñado) |
| 34 | Release channels | ✅ política — reclasificar v0.9.0 = 🔴 propietario |
| 35 | Real TV E2E gate | 🔴 requiere TV físico |
| 36 | Sankey evidence migration | ✅ `databases/evidence/sankey-tv.json` (honesto: 0 física) |
| 37 | Real retail matrices | 🔴 requiere feeds autoritativos fechados |
| 38 | Nexus Bridge | 🔴 hardware posterior |
| 39 | HIL | 🔴 lab hardware |
| 40 | Device matrix | 🔴 30–50 TVs reales |
| 41 | Retail matrix | 🔴 depende 37/40 |
| FTG | Final Truth Gate | ✅ PASS (Z1–Z7) |

## Verificación ejecutada en esta sesión (2026-08-17)

| Item | Resultado |
| --- | --- |
| Controller `compileDebugKotlin` | ✅ |
| Controller oracle tests (core.oracle) | ✅ 19/19 |
| Controller tvnode-phone tests (incl. nuevo E2E OBSERVE_VOLUME) | ✅ 28/28 |
| TV Node transport/canonical/observe | ✅ |
| TV Node TvNodeCoreTest (nuevo test Fase 27) | ✅ |
| TV Node `assembleRelease` sin credenciales | ✅ FALLA como diseñado (fail-closed) |
| `final_truth_gate.py` | ✅ PASS |
| `tv_claim_policy.py --check` | ✅ OK |
| `generate_third_party_notices.py --check` | ✅ current |
| gitleaks tree + `--log-opts=--all` | ✅ 0 + 0 |
| Fase 32 previa (controller suite completa) | ✅ 1328/1328, tv-node 107/107 |

## Commits de esta rama (frescos)

```
56ee48d  feat(ph27-36): play compliance, rotation runbook, gitleaks v2, ...
d379ea6  chore(ph32): migrate both builds to supported toolchain AGP 8.10.1 ...
ec4d9ab  feat(ph25-26-13): software-only IR oracle + persisted local evidence ...
54abd22  Bloco B fases 22/24 (previo)
74f54f2  Bloco A fases 2/3/10/12/23 + Final Truth Gate (previo)
```

## Bloqueadores que requieren al propietario (Jor)

1. **Rotar service-role de Supabase** (P0-19) → registrar evento en runbook.
2. **Provisionar `release.jks` + `tv-node-release.jks`** y los 4 env vars de CI →
   primer release firmado de ambos APKs.
3. **Reclasificar v0.9.0 (latest, debug) → Prerelease/engineering-preview** (P0-18).
4. **TV físico** para Fase 35 (E2E TV Node) y comenzar a llenar la evidencia del
   oracle (Fase 26 ya persistirá `REAL_DEVICE_OBSERVED` la primera vez que se
   confirme un par señal↔reversión).

## Qué NO está reclamado (honestidad)

- CERO `REAL_DEVICE_VERIFIED` nuevos: ninguna señal probada sobre TV físico en
  esta rama.
- CERO claims retail: `PhysicalTestEvidence` en el store inmutable sigue sin
  filas físicas; superficie comercial 0.
- TV Node sigue `UNIT_VERIFIED` (JVM); `REAL_DEVICE_VERIFIED` espera la Fase 35.
- `stable` sigue intacto hasta PRODUCTION_APPROVED.