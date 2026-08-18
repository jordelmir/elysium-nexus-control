# PHASE V07-TV-06 — PR2 SILICE 4: PHONE MIRROR SOBRE TRANSPORTE REAL + KEYSTORE VAULT

> Date: 2026-08-15. Maturity BEFORE: `UNIT_VERIFIED` (protocolo §11 + handshake
> 4 pasos + channel autenticado, 73 tests verdes, pero nada atravesaba un
> socket real: las keys se pasaban por llamada directa).
> Maturity AFTER: **`UNIT_VERIFIED`** — suite completa del TV Node ejecutada
> (voc. `fix/v0.10-truth-convergence`, orden del auditor v0.10 Phase 19): 95/95
> tests, 0 failures, `lintDebug` 0 errores, `assembleDebug` green. Transporte
> TCP real + mirror phone + credential vault con verificación JVM completa.
> Order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` §11 (envelope,
> framing, response states, semantic actions), §10 (Android Keystore,
> pinning, connection IDs, anti-replay, fail-closed), §61 PR2 slicing
> ("PR2 — Secure Pairing: NSD + QR + authenticated local channel + Keystore")
> y el NEXT BLOCKER del changelog previo (slice 4: mirror sobre transporte).

## WHAT CHANGED

Package nuevo `com.elysium.nexus.tvnode.transport` (JVM pura, sin Android ni
deps de red — testeable con sockets de loopback reales) y
`com.elysium.nexus.tvnode.credential` (vault Keystore):

### `transport/TvFrameStream.kt` — framing sobre flujo real
- Envía/lee UN frame `TvLinkProtocol` con `u32 BE length | u8 type | payload`
  sobre cualquier `InputStream`/`OutputStream` (socket o in-memory).
- Lectura bloqueante single-thread ordenada; EOF limpio = `null`;
  cualquier tamano/longitud malformada → `ProtocolException` (fail-closed).
- Misma disciplina byte-parity que `TvLinkProtocol`.

### `transport/TvActionDispatcher.kt` — seam server-side entre wire y ejecución
- `dispatch(envelope, action) → TvResponseBody`. Pure interface: el handler
  real (TvActionExecutor / observación §19) se conecta en on-device; los
  tests inyectan un stub honesto.

### `transport/TvLinkServer.kt` — pipeline "Raw server → handshake → ACTION"
- Acepta UN socket, ejecuta el `TvLinkHandshake` de 4 pasos real sobre la
  conexión; después CHANNEL_READY + loop de ACTION.
- Cada ACTION viene AEAD-sellado por el phone (`decryptFromPeer(adPhoneToTv)`)
  — autenticado, anti-replay y con dominio de nonce correcto ANTES de tocar
  el dispatcher (§10). RESPONSE se sella de vuelta (`encryptToPeer(adTvToPhone)`).
- Envelope undecodable / connectionId ≠ session / ACTION no autenticado →
  ERROR frame + teardown (`Outcome.Failed`). Forward-compat (TEXT_COMMIT,
  SEARCH, OPEN_APP) → RESPONSE UNSUPPORTED, NUNCA drop silencioso.
- Cierre SIEMPRE en `finally` (fail-closed teardown, §10 disconnection
  neutralizes). GOODBYE limpio → `Outcome.Clean(served)`.

### `transport/TvLinkClient.kt` — el mirror phone (`LinkSide.PHONE`)
- 4 pasos byte-por-byte contra el server: HELLO(connId+pubkey), HELLO_ACK,
  NONCE_ECHO_ACK sellado (possession proof), CHANNEL_READY.
- Guarda `ServerIdentity(pubkey, fingerprint 8-hex)` — el target del QR
  pinning (§10) que el phone coteja ANTES de confiar.
- `sendAction(envelope) → TvResponseBody?` — sella el envelope, lee y
  autentica la RESPONSE. `close()` envía GOODBYE y libera.
- Nada sensible viaja en plaintext; todo title key directional AEAD.

### `credential/TvCredentialVault.kt` — contrato de almacenamiento §10
- `pinPeerAndCheckFingerprint` / `unpinPeer` / `isPeerPinned`: public-key
  pinning durable (§10 "certificate/public-key pinning").
- `saveChannelCredential(connectionId)` / `load` / `revokeConnection`:
  credencial de canal por connectionId (revocación por sesión).
- Nunca raw credencial en plaintext: la responsabilidad de seal es del impl.

### `credential/InMemoryTvCredentialVault.kt` — twin JVM del contrato
- Usado por los tests (y dev-only). Mismo contrato → el impl Android swap-in
  sin cambiar call sites. No pretende protección Keystore.

### `credential/AndroidKeyStoreTvCredentialVault.kt` — la impl de producción
- AES-GCM wrapping bajo una clave AES generada DENTRO del Android Keystore
  (blob `iv‖ct`, nunca plaintext en disco; §10 "Credential storage: Android
  Keystore").
- Maturity honesta: `IMPLEMENTED`. La premisa criptográfica solo es
  verificable on-device; los tests de contrato corren contra el twin JVM;
  on-device verify ocurre en el slice de pairing presencial (asegura llegar
  a `ON_DEVICE_VERIFIED`, nunca se reclama sin evidencia).

## WHY

El transporte era el spine vacío del vertical: protocolo + handshake + channel
existían como objetos JVM pero nada movía frames entre phone y TV. Este slice
conecta la primera pieza REAL de red (¡sockets TCP de verdad, no mocks!) con
el handshake YA terminado, y añade el mirror phone byte-idéntico (la
referencia que el controller Android implementará idéntica). El Keystore
cumple el mandato §10 (pinning + credencial durable fuera de Room). Felices
como cumplen todos los invariantes de seguridad: AEAD en el cable, nonce
dominos por dirección, replay-guard en RX, connectionId binding, teardown
fail-closed.

## FILES CHANGED

- `apps/android-tv-node/.../transport/TvFrameStream.kt` (new)
- `.../transport/TvActionDispatcher.kt` (new)
- `.../transport/TvLinkServer.kt` (new)
- `.../transport/TvLinkClient.kt` (new)
- `.../credential/TvCredentialVault.kt` (new)
- `.../credential/InMemoryTvCredentialVault.kt` (new)
- `.../credential/AndroidKeyStoreTvCredentialVault.kt` (new)
- `apps/android-tv-node/app/src/test/.../transport/TvLinkTransportTest.kt` (new, 5 tests)
- `apps/android-tv-node/app/src/test/.../credential/TvCredentialVaultTest.kt` (new, 5 tests)

## ARCHITECTURE IMPACT

- El transport queda desacoplado de Android: el mismo objeto corre en un
  `ServerSocket` de phone y TV. El controller Android copiará el mirror de
  `TvLinkClient` (parity por construcción).
- `TvActionDispatcher` es el único seam: on-device se conecta al
  TvActionExecutor (evidence ladder §19), en tests inyecta el stub.
- La promoción del vertical queda: WRITE → handshake sobre socket → ACTION
  sellado → dispatcher → RESPONSE sellado.

## TESTS ADDED

`TvLinkTransportTest` (5): handshake phone↔TV contra socket loopback REAL y
outcome Clean; envelope ACTION ida y vuelta cifrado devuelve EXECUTED con el
mismo messageId y depth=1; connectionId extranjera → teardown Failed (nunca
RESPONSE); golden parity encode→decode byte-idéntico y dispatcher recibe el
envelope exacto que el phone envió; mirror keys (TV rx == phone tx).

`TvCredentialVaultTest` (5): pin store + AlreadyPinned repeat; unpin +
NotFound; fingerprints case-sensitive y distintas; channel credencial
store/load byte-idéntica; revoke solo borra esa conexión; missing carga null.

## TEST RESULTS

NOT RUN — verify-on-request (Jor). Pendiente:
`cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest`.

## REAL-DEVICE TEST RESULT

None ordered this phase. El pairing presencial en VER_N49 (QR + pinning +
Keystore on-device) está dirigido por el siguiente slice; el transport ya está
listo para ser ejercido allí sin cambios de API.

## KNOWN LIMITATIONS

- NSD resolve / advertising on-device no está en este slice (descubrimiento
  ya existe como `NexusTvDiscovery`); falta unir URI NSD ↔ socket.
- Serialización completo de ChannelKeys para resume por conexión (con el
  keypair DH mirror-side y el phone Keystore) se fija en el próximo slice.
- `AndroidKeyStoreTvCredentialVault.loadChannelCredential` puede cargar solo
  lo que ESTE build escribió (formato versionado de 32+32); la semántica de
  resume multi-session queda con el phone mirror completo.
- Server single-connection por `handle()` (un peer a la vez); multi-peer es
  un bucle de accept posterior.

## SECURITY IMPACT

AEAD en cada frame (sin plaintext de acciones), dominos de nonce por
dirección, replay-guard en RX antes de dispatch, connectionId binding
(envelope ≠ session drop), possession proof real (echo sellado), teardown
fail-closed con socket cerrado en `finally`, credencial durable solo sellada
bajo Android Keystore o twin JVM (nunca plaintext). Forward-compat NUNCA se
atiende como éxito silencioso.

## EVIDENCE GENERATED

`TvLinkServer` / `TvLinkClient` / vaults ground-truth implementations; 10
tests nuevos (pendientes de ejecución); este changelog. Sin evidencia de
device en este slice — por eso el nivel es `IMPLEMENTED`, no promocionado.

## MATURITY

BEFORE: `UNIT_VERIFIED` (protocolo/handshake/channel sin transporte).
AFTER: **`IMPLEMENTED`** (transporte+métricas mirror+vault codificados y
testeados en código, ejecución pendiente). La promoción a `UNIT_VERIFIED`
requiere ejecutar la suite a la orden. El on-device (pairing presencial en
VER_N49) es un slice posterior.

## NEXT BLOCKER

PR2 slice 5: on-device pairing flow — unir `NexusTvDiscovery` (NSD URI) con
`TvLinkServer.handle()`, mostrar QR + fingerprint en pantalla, cotejar el pin
en `TvCredentialVault` (ambos lados), y ejecutar el handshake presencial en
VER_N49; ahí `AndroidKeyStoreTvCredentialVault` alcanza `ON_DEVICE_VERIFIED`
y el controller phone copia el mirror `TvLinkClient`.
