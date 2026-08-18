# PHASE V07_17 — SOFTWARE-ONLY IR ORACLE + PERSISTED LOCAL EVIDENCE

**Commit de entrega:** `(hash)(hash) — 17 de agosto de 2026.**

## Resumen

Primera máquina automática de evidencia física REAL sin hardware Bridge: el teléfono
(controller) pide un snapshot de volumen REAL al TV Node por el canal AEAD existente,
dispara el IR candidato, re-observa, dispara la señal inversa y re-observa la
restauración exacta. Solo una corrida 100% causal multi-trial se convierte en evidencia
`REAL_DEVICE_OBSERVED` perseguida en el store inmutable.

## Master Order v0.10 — fases cerradas

### Fase 9 (parcial, pre-existente) → 25 — IR Oracle software-only
- **Wire (tv-node, compartido `:tvlink`)**: nuevo código `OBSERVE_VOLUME(0x1E)` al FINAL
  del enum `TvActionCode` — los bytes existentes no cambian.
- `ObservationCapableDispatcher` (transport/, compartido): responde `EXECUTED` con el
  detalle canónico `vol=<raw>/<max>,muted=<b>` cuando hay motor de observación; si no,
  `UNSUPPORTED` honesto. Todas las demás acciones delegan.
- **Hallazgo corregido (stop-the-line)**: `TvLinkServer` interceptaba `action == null`
  (forward-compat codes) ANTES del dispatcher y respondía `UNSUPPORTED` hardcodeado —
  OBSERVE_VOLUME jamás llegaba a la capa de observación. Ahora **el dispatcher es dueño
  de la respuesta para todo código**; ejecutor simple → UNSUPPORTED (nunca silencioso).
- `AndroidAudioObservationEngine` (app-level): lee AudioManager real, observación-only.
- Controller: `TvNodePhoneLink.observeVolume(seq)` — probe honesto contra el wire
  compartido; parsea el detalle canónico con regex estricta; null sobre
  no-EXECUTED/malformado (fail-closed, nunca un dato inventado).

### Fase 25 — Motivación — IROracleEngine (controller, `core/oracle/`)
- Protocolo de desafío: `before → tx(candidato) → after → tx(inverso) → restored`.
- Veredicto **Confirmado SOLO si TODOS los trials** cambiaron en la dirección correcta
  Y la reversión restauró el estado exacto. Cualquier trial fallido → Unconfirmed (honesto).
- Sin lane de observación / transmisor que rechaza / sin inversa → Unsupported.
- `OracleTransmitter` / `OracleObserver` seams puros JVM → suite completa sin hardware.
- `physicalSha256` determinista = sha256(carrierHz + waveform).

### Fase 26 — Persistencia local de evidencia real
- `OracleEvidenceLedger`: JSONL append-only, fsync por append, seqs contiguas fail-closed
  (misma honestidad que el EvidenceStore), duplicados rechazados; el archivo NUNCA se
  reescribe (fix: append en modo `FileOutputStream(file, true)` — el `outputStream()`
  anterior truncaba).
- `OracleEvidencePromoter`: promueve a `PhysicalTestEvidence` con estatus
  `REAL_DEVICE_OBSERVED` SOLO con veredicto `CONFIRMED`, trials ≥ 2 y todos OK;
  apéndice inmutable vía `EvidenceStore`; id `oracle-<eventId>` y nunca duplica.
- Unconfirmed se registra en el ledger con `trialsOk/trialsTotal` y el primer fallo.

### Fase 13 — Autoridad compartida (twins eliminados en este dominio)
- `EvidenceEvent` (canonical compartido): evento causal único consumido por el motor de
  promoción del catálogo; el controller lo produce, el tv-node lo compila.
- Contrato de observación migrado a la autoridad canónica: `TvObservationEngine` +
  `VolumeObservation` viven ahora en `canonical/TvObservation.kt` (compilado en AMBOS
  builds vía `:tvlink`) — imposible que controller y TV Node divergan sobre qué es un
  snapshot de volumen. App-level glue (AudioManager) queda en `observe/`.

## Verificación (JVM tests, targeted)
- `core.oracle` **19/19**: unanimidad confirma, stale demota, reversión ausente falla,
  sin lane Unsupported (no Unconfirmed), sin inversa rechazado ANTES de cualquier burst,
  transmisor bloqueado honesto, sha determinista, EvidenceEvent solo desde Confirmed.
- `core.transport.tvnode` **9/9** (incluye nuevo E2E `phone observes real tv volume over
  the wire — phase 25 oracle lane`): phone real + TvLinkServer real + dispatcher de
  observación real → `vol=12/50,muted=false` round-trip. 28/28 en total.
- TV Node (transport + canonical + observe) verde.
- OBSERVE_VOLUME: los códigos previos del wire están intactos (byte-compat).

## No implica
- Ninguna señal fue probada aún sobre un TV físico — `REAL_DEVICE_OBSERVED` solo existe
  cuando el ORACLE lo confirma en hardware real y el ledger lo persiste.
- `OBSERVE_VOLUME` no decodifica a `UniversalAction` (es un probe, nunca un efecto).