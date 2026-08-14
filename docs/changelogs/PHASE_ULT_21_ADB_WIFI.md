# PHASE ULT.21 — ADB REAL · CONTROL UNIVERSAL WI-FI

> Fecha: 2026-08-13 · Rama: main · Previo: ULT.19 (f6cd9a7) + ULT.20 (e4967e0)

## Qué se construyó

**Transporte ADB real (JVM pura, sin dependencias)** para controlar Android TV / Google TV / Fire TV por Wi-Fi:

- `AdbWirelessClient` — cliente completo del protocolo wire ADB:
  - Handshake CNXN/AUTH: versión `0x01000001`, maxdata `0x00100000`, banner de features completo del cliente adb stock (302 B).
  - Autorización: RSA-SHA1 sobre el token (SIGNATURE) y, para el primer contacto, oferta de `AUTH RSAPUBLICKEY` → dialog estándar "Allow USB debugging" en la TV; la espera del tap humano amplía el timeout del socket (`authorizationTimeoutMs`).
  - `shell()` abre el servicio `shell,v2,raw:` SIN NUL y con `data_check=0` — exactamente los bytes del cliente adb oficial (verificado por captura de tráfico en el laboratorio, `docs/testing/evidence/ULT21/traces`).
  - ACK de WRTE espejando `(arg0, arg1)` según `OVERVIEW.TXT`.
  - `TCP_NODELAY` en el socket — requisito real del adbd del Honor (MagicOS): sin él adbd no responde ni al CNXN (hallado experimentalmente).
- `AdbAuthorization` — generación RSA-2048, firma JCA, `toPem()`/`loadFromPem()`, carga desde `~/.android/adbkey` (útil para el test de integración).
- `AdbAuthorizationStore` — **la llave se persiste en SharedPreferences**: emparejar una vez, reconectar siempre sin dialog (clave comercial "a la primera"). Contract + impl SharedPrefs + impl in-memory para tests JVM.
- `AndroidTvAdbAdapter` — adapter `TvLanAdapter` real: `discover()` (mDNS `_adb._tcp` + puerto 5555 abierto), `identify()` (modelo vía `getprop`), `execute()` (`input keyevent`), keycodes Android completos.
- `AndroidTvKeyCodes`.

**Sección UI "CONTROL UNIVERSAL · WI-FI"** (`ui/wifi/WifiUniversalScreen.kt`):

- Descubrimiento automático: mDNS `_adb._tcp` + barrido paralelo de la subred (5555) + IP manual.
- Lista de dispositivos → conectar (dialog en la TV la primera vez) → teclado de control: pad direccional, OK, vol ±, mute, power, home, menu, transporte ⏮⏯⏭⏹, canales ±.
- Tarjeta neon en el Hub + destino `HubDestination.WifiUniversal` + codec navegación restaurable (`wifi`).
- (Revert de la tarjeta MAC/PC a "Comparte tu pantalla · trackpad + teclado + mouse" navegando a USB-C, quedó en este mismo lote.)

## Evidencia de laboratorio (Honor Magic V2, IP 192.168.1.11)

- Dialog de primer contacto: SIG → SIG → PUBKEY → dialog ("Allow USB debugging") → tap → **CNXN → autorizado** (flujo del producto OK). `docs/testing/evidence/ULT21/`.
- Firma con llave persistida: la sesión se establece por SIGNATURE sin dialog (camino del producto para reconexiones).
- Capturas proxy del cliente adb stock contra el mismo adbd: CNXN 1.1/1MB + banner completo (302 B) + TWO SIGNATURE rounds... → OPEN(`shell,v2,raw:` + `dck=0`) → OKAY/WRTE/CLSE. Réplica byte a byte en el cliente Kotlin.
- `AdbWirelessRealAdbdTest`: se salta sin `ADB_TEST_HOST`; con llave persistente en disco (`ADB_TEST_KEY_PATH`) la fase 1 (dialog) y la fase 2 (SIGNATURE) se ejercitan con la MISMA llave.
- Quirk del laboratorio documentado: el adbd de MagicOS solo atiende a la "última sesión activa" (las conexiones paralelas reciben blackhole y los OPEN de sesiones dialog-keys quedan en silencio); con TCP_NODELAY el handshake sí progresa. El formato del cliente Kotlin es idéntico al del cliente oficial (2 capturas independientes).

## Pruebas

- Nuevas: `AdbAuthorizationStoreTest` (3) — identidad estable, round-trip PEM, clear. `header_roundTrips`. Verdes.
- Test de integración real skippable: `AdbWirelessRealAdbdTest`.
- Compilación: `compileDebugUnitTestKotlin` + `compileDebugKotlin` → BUILD SUCCESSFUL.

## Pendiente (no bloqueante del lote)

- Verificar end-to-end el keyevent contra un Android TV real de producción (el adbd del Honor no responde OPEN en sesiones dialog-keys — limitación del firmware del laboratorio, no del cliente).
- `assembleDebug` + `lintDebug` completos antes de publish (regla de Jor: solo cuando se ordene).

## Archivos

- `fabric/tv/adb/`: `AdbWirelessClient.kt`, `AdbAuthorization.kt`, `AdbAuthorizationStore.kt`, `AndroidTvAdbAdapter.kt`, `AndroidTvKeyCodes.kt`
- `ui/wifi/WifiUniversalScreen.kt`
- `ui/hub/`: `HubNavigation.kt` (+WifiUniversal), `HubScreen.kt` (card), `HubStackCodec.kt` (`wifi`)
- `ui/MainActivity.kt` (wiring)
- Tests: `fabric/tv/adb/AdbAuthorizationStoreTest.kt`, `fabric/tv/adb/AdbWirelessRealAdbdTest.kt`
- Evidencia: `docs/testing/evidence/ULT21/`

## Compatibilidad (regla §33 — sin claims silenciosos)

- Estado: `PARTIALLY_VERIFIED` — handshake/authorization/identify verificados contra adbd real (Honor MagicOS); control de teclas pendiente de verificación final en hardware de producción (Android TV).