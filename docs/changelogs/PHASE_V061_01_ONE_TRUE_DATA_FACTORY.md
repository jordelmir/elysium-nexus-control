# PHASE V0.6.1–01 — ONE TRUE DATA FACTORY + TRUTH CLOSURE (Phases 0.1–0.4, 1–5)

Orden: ELYSIUM NEXUS V0.6.1 TRUTH CLOSURE & PHYSICAL PROOF (auditoría externa sobre `fix/v0.6-physical-truth-and-identity-gate`).
Estado: **IMPLEMENTED + BUILD VERIFIED** (data factory Python). Los P0-1…P0-14 de catálogo quedan cerrados en esta fase; los P0-15+ (Kotlin) son las siguientes fases.

## Mandato cumplido

> Cerrar brechas entre DECLARED / IMPLEMENTED / EXECUTABLE / VERIFIED / PHYSICALLY PROVEN.
> No merge a main, no release, no tag, no RC, no HIL claims.

## Phase 0 — STOP THE LINE ✓

- **0.1** `CatalogManifest.kt` reescrito: el parser JSON propietario `ManifestJson` desapareció; ahora usa `org.json.JSONObject` con validación fail-closed explícita. `REQUIRED_KEYS` = 8 (incluye `policyVersion`). El manifest real empaquetado (`counts` anidado) **parsea correctamente** (el bug del auditor P0-1 era real).
- **0.2** `CatalogManifestTest` reescrito: lee el asset EXACTO `src/main/assets/ir/ir_catalog.manifest.json` del disco (sin fixture) + 9 casos de rechazo. **`testDebugUnitTest --tests CatalogManifestTest` → BUILD SUCCESSFUL.** Se arreglaron 4 errores de compilación pre-existentes (`KpiHarness` p50/p95 → `.toDouble()`; `IrCatalogDatabaseManager` → `setOfNotNull` ×2).
- **0.3** Schema V5: `signals` gana `protocol_definition_id` + `protocol_variant_id` (FKs reales), `carrier_evidence`, `evidence_level`, `eligibility_status`; `code_sets` gana `evidence_level`/`eligibility_status`; taxonomy de `catalog_rejections.rejection_kind` ampliada (UNSUPPORTED_ENCODING, AMBIGUOUS_VARIANT, INVALID_CARRIER, MISSING_CARRIER, MALFORMED_ADDRESS, MALFORMED_COMMAND, MALFORMED_RAW, RAW_DURATION_TOO_LONG) + índices nuevos.
- **0.4** `check_catalog_eligibility.py` reescrito:
  - joins por FK reales (`protocol_definition_id`), nunca `codec_id` vs `pd.id` (P0-2 ✓).
  - RAW no requiere variante paramétrica (P0-3 ✓) — su waveform es la representación física.
  - elegibilidad por signal: `PROBE_ELIGIBLE` / `RESEARCH_ONLY` (P0-4/P0-5 ✓).
  - Superficies SEPARADAS: `probe_*` (INTERNAL_UNVERIFIED puede probarse) y `claim_*` (requiere verificación real; `--require-claims` fatal para builds de compatibilidad).
  - Resultado actual: **PROBE_SURFACE OK** (POWER 1424, VOLUME_UP 956, VOLUME_DOWN 805, MUTE 883), CLAIM vacío → build = Engineering Preview (honesto, no Compatibility Build).

## Phase 1 — ONE TRUE DATA FACTORY ✓

- Los 3 seeders fueron **eliminados del production path** (P0-6/P0-7).
- Nuevo `tools/ir-data/source_adapters.py`: curated TV, KINTECH y templates entran vía el MISMO `EntityCache` fail-closed con lock authority que las fuentes externas. Cero mutación directa de DB.
- `elysium-template-hypotheses` es **RESEARCH_ONLY por construcción**: `import_templates` lanza excepción bajo `--profile production` (el dato peligroso no existe en el artefacto production).
- `catalog.py`: pasos `step_seed_*` → `step_import_curated/kintech/templates`; flags `--import-*`; pipeline production = ingest → curated → kintech → (templates **skip**) → optimize → hash → manifest → verify.

## Phase 2 — SUPPLY CHAIN FAIL-CLOSED ✓

- Datasets first-party versionados con artefacto + licencia + hash real:
  - `ir-data/data/curated-tv/v1/{ir_codes_db.json, LICENSE.txt}`
  - `ir-data/data/ir-templates/v1/{templates.json, LICENSE.txt}` (17 hipótesis, HYPOTHESIS-ONLY)
- `sources.lock.json`: 8 fuentes (los 3 first-party anclados con `kind=artifact`, SHAs bytes-exactos, `approvalReference`, templates `productionEnabled=false`).
- `verify_source_locks.py`: nueva rama `artifact` — verifica bytes exactos del artefacto + licencia contra el lock (misma postura fail-closed que los checkouts git). **✅ All 8 source locks verified**.
- Adiós a los hashes `0x64` y a `license_status=APPROVED` sin evidencia.

## Phase 3 — CONTENT-ADDRESSED PHYSICAL GRAPH ✓

- Toda identidad autoritativa es **SHA-256 completo** (P0-9):
  - `SignalParametricId = SHA256(pid-v1 | codec | exact variantId | carrier | addr | sub | cmd | repeat | toggle)`
  - `SignalRawId = SHA256(rpid-v1 | carrier | exact durations uncompressed)` — **sin zlib en la identidad** (P0-10): cambiar el códec de compresión no cambia el ID físico.
  - `CodeSetId = SHA256(csid-v1 | sourceRevision | remoteId | sorted bindings(a|sig|repeat|press))` — un cambio físico en un binding produce un code set NUEVO.
  - `BindingId = SHA256(bnd-v1 | cs | action | signal | repeat | press)`.
- Resolución canónica de `protocol_variant_id` derivada de `PROTOCOL_MAP` (familia→key canónico) — consistente con `seed_protocol_definitions`.

## Phase 4 — STRICT PHYSICAL INGESTION ✓

- Eliminados todos los fallbacks silenciosos (P0-11/P0-12/P0-13):
  - Flipper: address/command/frequency inválidos → `MALFORMED_ADDRESS/COMMAND` / `INVALID_CARRIER`; raw sin frecuencia → `MISSING_CARRIER`; raw inválido → `MALFORMED_RAW`/`RAW_DURATION_TOO_LONG`. `normative_carrier_default` contado pero nunca fabricado.
  - SmartIR: broadlink y raw-json = `FORMAT_NORMATIVE` (espec del formato: 38 kHz); negativos/duraciones > 250 ms → rejections tipadas (bug real encontrado en el primer build).
  - probonopd: carrier = `FORMAT_NORMATIVE` (el CSV no declara frecuencia); device/function no parseable → `MALFORMED_ADDRESS`.
  - LIRC: `frequency = None` por defecto (fin del 38 kHz silencioso); conf sin frecuencia → `MISSING_CARRIER`; `LIRC_RAW` materializable → `UNSUPPORTED_ENCODING` typed rejection (el runtime Kotlin no tiene ese codec).
  - irplus: frecuencia declarada = `SOURCE_DECLARED`; ausente = `FORMAT_NORMATIVE` (mandato del formato).
- Las rejections ahora viven **en el DB** (`catalog_rejections`) además del manifest: 7.590 filas = MALFORMED_RAW 7.290, UNSUPPORTED_PROTOCOL 279, MISSING_CARRIER 16, RAW_DURATION_TOO_LONG 5. El rejection content participa del `catalogBuildId`.

## Phase 5 — EVIDENCE / ELIGIBILITY SEPARATION ✓

- Ejes independientes materializados: `evidence_level` (SOURCE_IMPORTED hoy) + `eligibility_status` (PROBE_ELIGIBLE / RESEARCH_ONLY). `verification_status` ya no significa más de lo que es.
- `INSERT` de code sets deriva su elegibilidad del suelo de sus propias señales (nunca se adivina después).

## Verificación (medible, no "completado" verbal)

- **Build A** y **Build B** (reconstrucción completa desde cero): canonical `92e9389f206e31ef…`, catalogBuildId `19c845d6c0a07707…`, DB SHA `be7aa15fd5d5a6dd…` **byte-idénticos** → reproducibilidad demostrada (fase 25 en esencia, para CI más adelante).
- FK violations: **0**. `signal_sources`:**85.249** (el auditor pedía esto como prioridad absoluta: toda señal con su source exacto). `carrier_evidence`: FORMAT_NORMATIVE 77.581 / SOURCE_DECLARED 7.628.
- Gate: `PASS — catalog is probe-usable`; CLAIM_EMPTY honesto.
- `verify_source_locks` → ✅ 8/8 (5 checkouts git + 3 artefactos first-party bytes-exactos).
- JVM: `CatalogManifestTest` verde (corrido, autorizado por la orden Phase 0.2).

## Arquitectura (grano)

```text
sources.lock.json (autoridad)
  │ checkout/artifact verify  (verify_source_locks)
  ▼
ingest externos + SourceAdapters first-party   ← UN solo EntityCache fail-closed
  ▼
staging → validate → provenance → evidence → eligibility → ONE Schema V5 DB
  ▼
signal_sources / protocol FKs / carrier_evidence / eligibility_status
  ▼
canonical hash + rejection manifest + license manifest → catalogBuildId
```

## Reconciliaciones

- `sources.lock.json` gana 3 fuentes first-party con `kind=artifact` (no son repos git; el verifier ahora tiene rama de artefactos).
- Los templates ya no se leen de la constante en código: viven en `data/ir-templates/v1/templates.json` (artefacto anclado al lock).
- El seeder legacy `seed_curated_brands_v4.py` se conserva como fuente de kernel de parsing (mapas/pares), nunca como mutador de DB.

## Siguiente

Phase 6+ (Kotlin): Protocol fidelity (VariantUnsupported/SIRC exacto), RuntimeSignalResolver, durable profile install, process-death, Device Identity, Credential Vault, LG real, Mac channel v2, CI data/release factory, HIL/matrix/claims.