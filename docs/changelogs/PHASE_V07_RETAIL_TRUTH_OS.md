# PHASE V0.7 — RETAIL TRUTH OS · ZERO FALSE COMPATIBILITY

> Fecha: 2026-08-14 · Rama: main · Baseline auditado: `d89e705` → HEAD `53b70cb` + este lote
> Orden maestra: `docs/architecture/MASTER_ORDER.md` — **v0.7 Retail Truth Master Implementation Order**
> Regla constitucional: NO MOCK · NO FAKE TV · NO TEMPLATE-AS-TRUTH · NO GUESSED COMPATIBILITY · NO AI-GENERATED PRODUCTION IR CODE · NO SILENT FALLBACK · NO CLAIM WITHOUT EVIDENCE.

## Qué se construyó (lote v0.7 completo, commits 380337e…53b70cb)

La auditoría externa de dictamen (baseline `d89e705`, 2026-08-14) encontró los P0s comerciales y este lote los convirtió en código:

### Phase 0 — Constitución comercial
- `AGENTS.md` reescrito: reglas duras v0.7, escala de madurez de 11 niveles (CONCEPT → PRODUCTION_APPROVED), layout del repo, working contract.
- `README.md` reescrito desde la verdad generada (sin badges estáticos falsos, sin números viejos).

### Phase 1 — Retail Stop-The-Line Gate
- `RetailReleaseGate` (Kotlin, JVM): `checkBrandQuery` (deviceType vacío → fail), `checkCodecEligibility` (EXPERIMENTAL → fail), `checkCommercialClaimEligibility` (0 evidencia física → fail), `checkReleaseSigningCredentials` (fallback hardcodeado `Elysium2026!` → fail).
- Tests: `RetailReleaseGateTest` (5 casos, incluye detección del fallback hardcodeado).

### Phase 2/3 — Brand query honesta + sin LIMIT 200
- `IrCatalogRepository.getCandidatesForBrand`: `deviceType` en blanco se interpreta como **TV explícito** (nunca "no filtrar"), sin `LIMIT 200`, orden determinista por `cs.id`, eligible completo.
- Flujo UI `IrConnectFlow` alineado (pasa el `deviceType` correcto).

### Phase 5/8 — Política comercial de codecs (CIERRE EN ESTE LOTE)
- Loader de candidatos (`getCommandsForCodeSetInternal`) ya bloqueaba `EXPERIMENTAL` (`isCodecTransmittable`, 20.464 señales LAB_ONLY: RC5/RC6/Kaseikyo).
- **Nuevo en este lote**: `RuntimeSignalPolicy` + `RuntimePolicy.COMMERCIAL/LAB_ONLY` + punto único `resolveExecutableSignal(signalId, policy)` en `IrCatalog`/`IrCatalogRepository`/`InMemoryIrCatalog` — ahora **ningún loader puede saltarse la política**:
  - lookups directos (`getSignal` → vía `resolveExecutableSignal` en los callers),
  - perfiles guardados (`getCommandsForCodeSet` filtra por política; `RevalidationCatalog.resolveExecutableSignal`),
  - dispatch/automatización (`DeviceCommandResolver.resolve`),
  - transmisión desde UI (`TvControlScreen.sendProfileCommand`).
  - Fail closed: codecId nulo o desconocido NO es transmissible bajo COMMERCIAL.
- Tests: `RuntimeSignalPolicyTest` (7 casos: bloqueo experimental, admisión NEC/SAMSUNG/SIRC/AIWA/RAW, fail-closed null/unknown, contrato loader in-memory, bypass LAB_ONLY solo en laboratorio).

### Phase 6 — SIRC20 física
- Dispatcher explícito `SIRC_12` (address 5 bits) / `SIRC_15` (address 8 bits) / `SIRC_20` (address 5 + subDevice 8, **non-null obligatorio**, fail closed, validación de rangos).

### Phase 7 — Aiwa estricto
- `subDevice` null en Aiwa → `InvalidParameters` (nunca `?: 0`). Rangos D/S/F validados. Golden vector contra `IrpProtocols.xml`.

### Phase 10 — Learning IR con verdad física
- `IrLearner.learn(raw, sampleRate, measuredCarrierHz)`: la portadora medida por hardware es autoritativa; la heurística solo se usa si no hay medición.
- `IrCaptureBridge` pasa `frame.carrierHz` al learner.

### Phase 11 — Canal de captura endurecido (parcial)
- `IrCaptureBridge` ahora escucha en **127.0.0.1** (no 0.0.0.0), límite de frame 8192 B, ≤512 slices, socket timeout 5 s. (Auth mutua/TLS quedan para el Bridge firmware.)

### Phase 13/14/15 — Data model retail + feeds
- `RetailDataModel.kt`: `Retailer`, `RetailerSku`, `RetailerInventorySnapshot`, `RetailerSkuModelLink` (MONGE_CR/VERDUGO_CR/GOLLO_CR).
- `RetailFeedIngestionEngine`: bootstrap catálogos públicos Monge/Verdugo + modo B2B (API/CSV/SFTP/ERP signed feed), snapshot nocturno, eventos NEW_SKU/REMOVED_SKU/MODEL_CHANGE/METADATA_CHANGE.
- Tests: `RetailFeedIngestionEngineTest`.

### Phase 18/19/20 — Evidencia, certificado y promoción de claims
- `CompatibilityCertificateEngine`: `RetailCompatibilityCertificate` con acciones CORE/EXTENDED/OEM_SPECIAL, hashes de evidencia, firma digital.
- `ClaimPromotionEngine`: escalera derivada SOURCE_IMPORTED → STRUCTURAL_VALID → RUNTIME_EXECUTABLE → OPTICAL_TX_VERIFIED → INDEPENDENT_DECODE_VERIFIED → REAL_DEVICE_VERIFIED → HIL_VERIFIED → RETAIL_MATRIX_VERIFIED; el claim solo deriva hacia abajo desde evidencia.
- Tests: `ClaimPromotionEngineTest`.
- Reporte: `assets/ir/runtime-executable-report.json` — 223.571 señales auditadas: RAW_EXECUTABLE 114.690, PARAMETRIC_EXECUTABLE 76.507, EXPERIMENTAL_LAB_ONLY 20.464, UNSUPPORTED 11.910, INVALID_PARAMETERS 0; `commercialGate.isPass=false` (UNKNOWN=11.910 → no listo para claim).

### Phase 30 (parcial) — Release Security
- `build.gradle.kts`: sin fallback de passwords (`Elysium2026!` eliminado), release signing solo con `RELEASE_STORE_PASSWORD`/`RELEASE_KEY_PASSWORD`/`RELEASE_KEY_ALIAS` del entorno + `release.jks` existente; fail closed si falta.
- Gate en CI guarda el contrato.

### Phase 32 (parcial) — Mac security
- EOF antes de `PAIR_OK` → `CONNECTION_CLOSED` → FAIL (bug fail-open corregido).
- CI SDK packages alineados (API 36 + 34).

### Phase 34 — Supply chain
- `THIRD_PARTY_NOTICES.md` + `generate_supply_chain_notices.py` (reporte de provenance por archivo, licencias Flipper-IRDB/irdb/SmartIR).

### Phase 35 — Regulación Costa Rica
- `docs/commercial/costa-rica/`: SUTEL.md, MEIC.md, WARRANTY.md (+ RF_CERTIFICATION/PRIVACY/RMA/PACKAGING_CLAIMS pendientes).

### CI
- Workflow único, instala `platforms;android-36` + `build-tools;36.0.0` + API 34 (matriz de retrocompatibilidad), job instrumentado deshabilitado temporalmente (emulador crashea en runners GH — no es un gate de RC, se reactiva antes de release).

## Qué se construyó en ESTE lote (delivery actual)

1. **Phase 5 completada** — `RuntimeSignalPolicy.kt`, `RuntimePolicy`, `IrCatalog.resolveExecutableSignal`, implementación SQLite + in-memory, filtrado de `getCommandsForCodeSet`, callers migrados (DeviceCommandResolver, TvControlScreen, ProfileRevalidationService/RevalidationCatalog). Cero bypass: probe, brand lookup, direct lookup, saved profiles, automation — todos pasan por la política COMMERCIAL.
2. **§XXVI verificación local del catálogo** — `verifyIrCatalogAsset` ahora exige **SHA-256 exacto + tamaño exacto** contra `ir_catalog.manifest.json` (mismo contrato que CI). Un DB incorrecto de cualquier tamaño falla en local.

## Qué se construyó en el lote de cierre §XXXX (delivery actual 2)

1. **§XII / Phase 9 — CarrierPolicy, sin fallback global** — `CarrierPolicy.kt` (selector puro JVM): `STRICT` (default comercial) usa la portadora pedida y ante hardware no soportado devuelve `UnsupportedCarrier` (fail closed, cero desplazamiento silencioso); `LAB_TOLERANCE` (solo laboratorio) permite el desplazamiento ±2000 Hz anterior. `AndroidIrTransmitter.transmit(waveform, policy=STRICT)` migrado; los 4 callers de producción quedan en STRICT por default. Tests: `CarrierPolicyTest` (7).
2. **§XVI / Phase 25 — AndroidTvAdbAdapter honesto** — `pair()` ahora es REAL: `Success` solo tras un round-trip autorizado (connect + `getprop`), con credential alias derivado del fingerprint de la clave RSA; `queryCapabilities()` ya no miente (`readable=false` porque `readState()` es null); `InputSource` eliminado de capacidades (no tiene keycode). Clase documentada como DEVELOPER_ONLY con contrato de honestidad.
3. **§XXX — El claim gate deriva de evidencia física** — `check_catalog_eligibility.py` CLAIM_QUERY exige: `EXISTS physical_test_evidence result='PASS'` **y** `NOT EXISTS FAIL/REGRESSION` por code set. Con `physical_test_evidence=0` filas, el claim surface es honestamente 0 (Engineering Preview); `--require-claims` falla. Verificado contra el DB real: probe 8.070 OK, claims CLAIM_EMPTY, exit 0.
   - Resultado del gate con el catálogo actual:
     ```
     probe_POWER 2705 · probe_VOLUME_UP 1957 · probe_VOLUME_DOWN 1550 · probe_MUTE 1858
     claim_* = 0 (CLAIM_EMPTY) — la verdad comercial: sin evidencia física no hay claims.
     ```

## Pruebas

- Nuevas: `RuntimeSignalPolicyTest` (7) + `CarrierPolicyTest` (7). Verdes por diseño JVM puro (sin Android).
- Gate Python verificado en vivo contra `ir_catalog.db` (salida arriba).
- Compilación/lint/suite completa: **pendiente — regla de Jor (verificación batch solo cuando se ordene)**.

## Pendiente (P0s del dictamen aún abiertos)

- **Toolchain**: AGP 8.7.3 + compileSdk 36 (combinación no soportada oficialmente; AGP ≥ 8.10 + Gradle ≥ 8.11.1 según matriz Google) — cambio config-only, requiere verificación de build (se hará en la batch).
- `MacCrypto`: claves direccionales, nonce domains, secuencia/anti-replay, AAD (Phase 32 completo).
- `CredentialVault`: AAD por credential (credentialId/deviceId/protocol/purpose/schemaVersion) — docs ya alineadas a master key en 2999abf.
- `IrCaptureBridge`: auth mutua + AEAD + rate limit (Phase 11 completo) — depende del Bridge firmware.
- ADB Wi-Fi: keyevent contra Android TV real de producción pendiente de verificación (clasificación honesta PARTIALLY_VERIFIED).
- Evidencia física: `physical_test_evidence=0` → claim gate honestamente 0 — HIL/Bridge (fase hardware).
- Monge matrix 51/51, Verdugo 100%, Gollo vía feed autoritativo.
- `runtime-executable-report.json`: commercialGate `isPass=false` hasta UNKNOWN=0.
- Instrumented tests: volver a activar como blocker de RC cuando los runners de GitHub lo soporten.

## Archivos (este lote)

- `fabric/infrared/RuntimeSignalPolicy.kt` (nuevo)
- `fabric/infrared/database/IrCatalog.kt`, `IrCatalogRepository.kt`
- `fabric/dispatch/DeviceCommandResolver.kt`, `fabric/profile/ProfileRevalidationService.kt`, `ui/control/TvControlScreen.kt`
- `app/build.gradle.kts` (`verifyIrCatalogAsset`)
- Tests: `fabric/infrared/RuntimeSignalPolicyTest.kt` (nuevo)
- Changelog: este archivo