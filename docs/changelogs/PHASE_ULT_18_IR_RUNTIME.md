# PHASE ULT.18 — IR PROBE / SESSION RUNTIME REGRESSION (V0.6.3)

> Fecha: 2026-08-13 · Rama: fix/v0.6.3-ir-runtime-regression · Commit: (este lote)

## Contexto

Lote acumulado en la rama durante el debugging del error "Session Recovery Failed"
(al restaurar una sesión IR obsoleta tras force-stop) y de la integridad del
catalogo IR. Incluye el fix de sesión (ULT.21 en el código) + endurecimiento
del transporte Mac y del vault de credenciales descubierto en el camino.

## Contenido

**Motor de sonda IR — máquina de estados explícita**

- `IrProbeEngine`: `CursorState` (`UNINITIALIZED → READY → EXHAUSTED`),
  `initialize(): CursorInitResult` (Ready / NoCandidates / Error); tras
  `READY`, `currentCandidate` nunca es null. Fin del "pseudocódigo" en el
  motor: ahora tiene contrato + tests.
- `ProbeCursor`: integrado con la máquina de estados (EXHAUSTED explícito).
- `IrProbeViewModel`: consume `CursorInitResult` — init fallido →
  `ProbeUiState.Error` con razón; 0 candidatos → `NoCompatibleCandidates`.

**Recuperación de sesión sin "Session Recovery Failed" (fix ULT.21)**

- `IrConnectFlow`: el restore de `SavedStateHandle` valida el hash del
  catálogo vigente contra el hash al inicio de la sesión; si cambió
  (instalación de catálogo/actualización entre force-stop y relaunch) la
  sesión obsoleta se descarta (`resetSessionIdentity`) en vez de fallar.
- `ProbeUiState.RecoveryRequired` ya no bloquea: auto-reinicio del barrido
  desde cero con telemetría del evento.
- Motor `EXHAUSTED` en UI → sesión completada/limpiada.

**Diagnóstico estructurado IR**

- `IrRuntimeDiagnostics` (nuevo): eventos tipados `IrDiagnosticEvent`
  (CATALOG_*/CANDIDATE_*/BINDING_*/PROTOCOL_*/ENCODE_*/TX_*/RESTORE_*),
  logcat + FileLog (MagicOS cifra logcat). Reemplaza Log.d/e ad-hoc.

**Endurecimiento colateral**

- `MacTransport`: fail-closed en el handshake — si el servidor responde un
  frame distinto de `PAIR_OK`, la conexión se rechaza con Error (antes se
  auto-aprobaba una conexión "zero-PIN").
- `CredentialVault`: corrección de obtención del `KeyGenerator` AndroidKeyStore
  para AES-GCM (el doble wrap `getInstance(AES_GCM)` rompía el vault).

**Datos / tooling**

- `ingest_v5.py`: ganchos de integridad ampliados.
- Assets del catálogo IR regenerados (db + manifest + stats).
- Evidencia visual: `docs/testing/evidence/ULT18/` (home, hub TV, barrido
  universal, sonda Konka).

## Verificación

- Fix de sesión verificado en el build instalado (barrido sin "Session
  Recovery Failed" tras force-stop + relaunch).
- Tests IR (waveform, paged probe, golden vectors codec, integridad de
  catálogo) actualizados y verdes en la corrida anterior del lote.
- Compilación: `compileDebugKotlin` + `compileDebugUnitTestKotlin` verde en
  el commit actual de la rama.

## Archivos

- `fabric/infrared/`: `IrProbeEngine.kt`, `ProbeCursor.kt`,
  `IrRuntimeDiagnostics.kt` (nuevo)
- `ui/connect/`: `IrConnectFlow.kt`, `IrProbeViewModel.kt`
- `core/transport/mac/MacTransport.kt`, `fabric/identity/CredentialVault.kt`
- Tests IR ×4, `tools/ir-data/ingest_v5.py`, assets `assets/ir/*`
- `docs/testing/evidence/ULT18/`