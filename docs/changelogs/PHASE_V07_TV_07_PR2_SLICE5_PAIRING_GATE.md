# PHASE V07-TV-07 — PR2 SLICe 5: PAIRING GATE — LA PROOF DEL CÓDIGO + PINNING ANTES DE CHANNEL_READY

> Date: 2026-08-15. Maturity BEFORE: `IMPLEMENTED` (transporte TCP real + mirror
> phone + vault Keystore — 15 tests transport/vault nuevos en slice 4, suite
> PENDIENTE de ejecución por verify-on-request de Jor).
> Maturity AFTER: `IMPLEMENTED → UNIT_VERIFIED pendiente`: gate de pairing
> escribió el hueco de autenticación real del vertical — el wire ahora prueba
> QUE el peer vio el QR y sabe el código de la pantalla, y PINNEA su fingerprint
> de forma durable en el vault antes de emitir CHANNEL_READY. Código + tests
> escritos; suite PENDIENTE de ejecución (NUNCA se corrió gradle en esta
> entrega — la promoción de estado se decide cuando la suite corra).
> Order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` **§10** ("Unknown
> peer: REJECT", "Malformed frame: REJECT", "certificate/public-key pinning",
> "No unauthenticated 0.0.0.0 production control port", "session expiration,
> peer revocation") + §61 PR2 slicing ("Secure Pairing: NSD + QR + authenticated
> local channel + Keystore") + el NEXT BLOCKER del changelog previo (slice 5:
> on-device pairing flow). Este slice construye la PROOF del código + el
> pinning durable que el on-device slice solo orquesta.

## WHAT CHANGED

Hueco real cerrado: antes de este slice, el `TvLinkServer` autenticaba la
POSEsiÓN de las channel keys (AEAD nonce echo) pero **NUNCA** probaba que el
peer supiera ni el código de 6 dígitos de la pantalla ni el nonce del QR que
escaneó —§10 exigía "Unknown peer: REJECT" y el servidor servía a CUALQUIER
peer que completara un DH en la LAN. Este slice añade la prueba de la ceremonia
de pairing y el pin durable:

### `transport/PairingGate.kt` (new) — el seam de autorización §10
- `PairingGate` interface: `authorize(peerFingerprint, confirm) → Verdict`
  (Authorized | Denied(reason)). Verdict NUNCA es un booleano ambiguo.
- `PairingConfirm`: el payload sellado que el phone prueba — `u8 codeLen |
  code.utf8 | nonce.utf8 (16 hex)`. Code = lo que la persona tecleó viendo la
  pantalla; nonce = el del QR escaneado (anti-replay: nonce viejo no confirma
  sesiones nuevas). `parse` estricto → malformado = `null` = Denied (fail-closed).

### `transport/CodeConfirmPairingGate.kt` (new) — la decisión, en orden
1. **RECONNECT**: si el fingerprint del peer YA está pinnado en el vault →
   Authorized SIN código (par parejado previamente; "ambos lados pin en vault").
2. **FIRST PAIRING**: exige sesión activa OPEN y NO expirada, exige `confirm`,
   primero coteja el nonce del QR de la sesión (anti-replay), luego `verifyCode`
   en la sesión (constant-time + contador de intentos → límite anti-fuerza
   bruta). En éxito → `pinPeerAndCheckFingerprint` durable + Authorized.
3. **DENIED** cualquier otra cosa (sin sesión, sesión expirada/gastada,
   fingerprint sin pin todavía, nonce distinto, código malo, confirm ausente).
   El server responde ERROR frame + teardown (fail-closed).

### `protocol/TvLinkProtocol.kt` — frame nuevo `PAIR_CONFIRM(0x19)`
- Enviado por el phone INMEDIATAMENTE después de NONCE_ECHO_ACK y ANTES de
  CHANNEL_READY. En el rango privado del TV link (0x10..), sin colisión con el
  MAC link (0x01..0x0F).

### `protocol/TvLinkHandshake.kt` — retiene la clave pública del peer
- `peerPublicKeyBytes` + `peerFingerprint` (8-hex SHA-256). Sin esto el server
  no podría saber QUÉ fingerprint pinnar (era local a `parseHello` y se perdía).

### `transport/TvLinkServer.kt` — gate opcional antes de CHANNEL_READY
- Constructor: `TvLinkServer(dispatcher, pairingGate = null, rng)` — **default
  null = comportamiento byte-idéntico al slice 4** (los 5 tests previos siguen
  verdes sin cambios).
- `handle()`: tras `Established` (possession proof), si hay gate → lee UN frame
  sellado PAIR_CONFIRM, lo DES-autentica bajo la RX key (solo un peer con las
  keys puede fabricarlo), parsea, llama al gate. Authorized → CHANNEL_READY;
  Denied → ERROR frame + `Outcome.Failed`.
- Fail-closed en SILENCIO: `socket.soTimeout = PAIR_CONFIRM_TIMEOUT` (10 s)
  alrededor del read; un peer que no prueba jamás → timeout → teardown, nunca
  un half-open que quede colgado (§10 EOF/auth → FAIL CLOSED).

### `transport/TvLinkClient.kt` — el mirror phone aprende a probar el código
- Constructor: `TvLinkClient(connectionId, pairingConfirm = null, rng)` —
  null por defecto = mismo 4-step del slice 4 (tests previos intactos).
- Con `pairingConfirm` no-nulo: tras NONCE_ECHO_ACK envía PAIR_CONFIRM sellado
  y luego lee CHANNEL_READY — byte-por-byte contra el server con gate.

## WHY

Sin la proof del código, el vertical "TV Node → pairing seguro" tenía un agujero
de seguridad real (cualquier peer LAN servido tras un DH) y el on-device slice
habría orquestado una ceremonia que en realidad nunca probaba nada. La regla
§10 "Unknown peer: REJECT" y "certificate/public-key pinning" quedan ahora
CUMplidas a nivel wire: el primer pairing prueba código + nonce y pinnea; el
reconnect se autoriza por el pin durable. Todo en JVM pura (nada de Android),
por lo que es unit-testable con socket loopback real.

## FILES CHANGED

- `.../transport/PairingGate.kt` (new) — interface + `PairingConfirm` codec
- `.../transport/CodeConfirmPairingGate.kt` (new) — decisión §10 en orden
- `.../protocol/TvLinkProtocol.kt` (modified) — frame `PAIR_CONFIRM(0x19)`
- `.../protocol/TvLinkHandshake.kt` (modified) — retención peer pubkey + fingerprint
- `.../transport/TvLinkServer.kt` (modified) — gate opcional pre-CHANNEL_READY + timeout
- `.../transport/TvLinkClient.kt` (modified) — `pairingConfirm` opcional sellado
- `apps/android-tv-node/app/src/test/.../transport/PairingGateTest.kt` (new, 11 tests)

## ARCHITECTURE IMPACT

- El camino del link queda completo y veraz: **socket → DH + possession proof →
  prueba DE LA CEREMONIA (código + nonce) → pin durable → CHANNEL_READY →
  ACTION sellados**. Antes: socket → DH (y nada más).
- `PairingGate` es un seam puro: el on-device slice compondrá
  `CodeConfirmPairingGate(vault, PairingSession)` con el vault Android-Keystore;
  no cambian call sites.
- Retro-compatibilidad por diseño: gate y confirm son opcionales con default
  null → el transporte previo (sin pairing) es un caso reducido, no un fork.
- `TvLinkHandshake.peerFingerprint` es ahora la identidad de pin (§10) que el
  phone ALSO coteja contra el QR (ya lo hacía por su lado con `ServerIdentity`).

## TESTS ADDED

`PairingGateTest` (11): codec encode↔parse y malformados rechazados (3);
reconnect por pin preexistente sin código (1); primer pairing con código bueno
+ nonce correcto → Authorized y PIN duradero (1); código malo → Denied, nunca
sin pin (1); nonce distinto con código correcto → Denied (1); peer desconocido
con sesión expirada → Denied (1); sin sesión + sin pin → Denied (1); sin
confirm en primer pairing → Denied (1); wire REAL: pairing por socket loopback
autoriza y sirve acciones con el peer pinnado (1); wire REAL: peer que prueba
código INCORRECTO → server `Outcome.Failed` y nada pinnado (1).

## TEST RESULTS

NOT RUN — verify-on-request (Jor). Pendiente:
`cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest` (esperado 73+15
slice4 + 11 slice5 = esperado 99).

## REAL-DEVICE TEST RESULT

None ordered this phase (mismo payaso que slice 4: el pairing presencial en
VER_N49 — QR en pantalla + cotejo manual del código + pinning Keystore
on-device — es el próximo slice on-device, este slice le da el mecanismo
verdadero de autorización).

## KNOWN LIMITATIONS

- Falcon multi-connection: `TvLinkServer.handle()` sigue single-socket (un
  peer a la vez); el accept loop multi-peer es trabajo posterior.
- El `PairingSession.bindChannel` (derivación de keys propia de la sesión) y la
  derivación del handshake viven hoy separadas; se documenta como trabajo
  futuro unificar los keypairs efímeros para que un solo dominio de claves
  gobierne ceremonia + canal (sin inventar integración no probada).
- On-device: falta unir `NexusTvDiscovery` (NSD URI) ↔ `TvLinkServer.handle()`
  y renderizar QR + fingerprint; eso es el próximo slice on-device.

## SECURITY IMPACT

Cierra el hueco crítico del vertical: §10 "Unknown peer: REJECT" ahora es
verdad a nivel wire. El PAIR_CONFIRM viaja AEAD-sellado (poseer las keys es
requisito para fabricarlo), el nonce del QR liga la confirmación a la sesión
exacta (anti-replay), `verifyCode` mantiene constant-time + intentos acotados,
el pin es durable en vault (nunca plaintext) y el timeout de 10 s evita el
half-open. Nada se inventa: sin prueba → ERROR + teardown.

## EVIDENCE GENERATED

`PairingGate` + `CodeConfirmPairingGate` + frame `PAIR_CONFIRM` + retención de
fingerprint + gate/confirm opcionales en server/client; 11 tests nuevos
(pendientes de ejecución); este changelog. Sin evidencia de device aún — por
eso `IMPLEMENTED`, no promocionado.

## MATURITY

BEFORE: `IMPLEMENTED` (transporte + mirror + vault, suite sin correr).
AFTER: **`IMPLEMENTED`** (gate + pinning codificados y testeados en código,
ejecución pendiente). Promoción a `UNIT_VERIFIED` cuando la suite corra a la
orden. El on-device (pairing presencial VER_N49) sigue siendo el siguiente
slice, ahora con un mecanismo de autorización VERDADERO que orquestar.

## NEXT BLOCKER

PR2 slice 6 (on-device): unir `NexusTvDiscovery` (NSD URI) con
`TvLinkServer.handle()` + `CodeConfirmPairingGate`, renderizar QR + fingerprint
en pantalla (PairingActivity), cotejo manual del código en el phone, correr el
pairing presencial en VER_N49 — ahí `AndroidKeyStoreTvCredentialVault` y el
gate completo alcanzan `ON_DEVICE_VERIFIED` y el controller phone copia el
mirror con `pairingConfirm`.
