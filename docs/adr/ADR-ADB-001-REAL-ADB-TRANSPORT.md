# ADR-ADB-001: Real ADB transport for Android TV control over Wi-Fi

* Status: Accepted
* Deciders: Elysium Nexus engineering (laboratorio: Honor Magic V2 / MagicOS 8)
* Date: 2026-08-13
* Replaces: CONCEPT stub `AndroidTvAdbAdapter` (fake RPC)

## Context

"CONTROL UNIVERSAL · WI-FI" necesita controlar Android TV / Google TV /
Fire TV sin instalar nada en la TV y sin depender de un host puente. El
camino estándar de la industria es ADB over TCP (`adb tcpip 5555` + RSA
auth). La primera implementación (concepto) simulaba las llamadas; había
que escribir el wire protocol real en JVM pura (sin dependencias) y
verificarlo contra adbd real.

## Hallazgos de laboratorio (protocolo wire)

Verificados por captura de tráfico del cliente adb oficial contra el adbd
del Honor (traces en `docs/testing/evidence/ULT21/traces/`):

1. **CNXN**: versión `0x01000001`, maxdata `0x00100000`, banner de
   features completo del cliente stock (`host::features=...delayed_ack`),
   `data_check` = checksum (suma de bytes) del payload. Un banner corto o
   versión vieja funciona en AOSP, pero para réplica exacta usamos el
   banner completo.
2. **AUTH**: TOKEN (20 B) → firma RSA-SHA1 de 256 B (2 intentos) → ante
   fallo, oferta `AUTH RSAPUBLICKEY` (`base64(x509 DER) + " adb-key\n"`).
   El dialog "Allow USB debugging" de la TV aparece SOLO en el primer
   contacto; el tap humano puede tardar → el socket debe ampliar su
   timeout mientras espera (never dejar el `soTimeout` corto activo).
3. **OPEN para shell**: servicio `shell,v2,raw:$comando` SIN NUL
   terminador y con `data_check = 0` — exactamente lo que envía el
   cliente stock (la traza lo muestra byte a byte). Variantes
   `shell:$cmd\0` o con checksum NO son lo que manda el oficial.
4. **WRTE ACK**: `A_OKAY` con `(arg0, arg1)` espejados del WRTE recibido.
5. **TCP_NODELAY obligatorio en MagicOS**: sin `TCP_NODELAY` el adbd del
   Honor no responde ni al CNXN (descubierto experimentalmente; el
   cliente stock lo activa). En AOSP no es necesario, pero activarlo
   siempre es inofensivo y obligatorio para una clase de dispositivos.

## Decisión

1. Escribir el cliente ADB en JVM pura (JCA solamente): `AdbWirelessClient`
   + `AdbAuthorization` (RSA-2048).
2. Replicar el wire del cliente stock bit a bit (CNXN completo, firma,
   `shell,v2,raw:` + dck=0, ACK espejo, TCP_NODELAY).
3. **La llave RSA se persiste** (`AdbAuthorizationStore`, SharedPreferences):
   emparejar una vez, conectar siempre. Es lo que hace el producto
   "funcionar a la primera" en cualquier dispositivo tras el primer dialog.
4. Nombre del dispositivo vía `getprop ro.product.model` (identify);
   control por `input keyevent <KEYCODE>` (injección nativa del sistema).
5. Descubrimiento: mDNS `_adb._tcp` + barrido de subred (puerto 5555) +
   entrada manual de IP. La UI ofrece las tres.

## Quirks del laboratorio (documentados, no modelan el producto)

- El adbd de MagicOS atiende preferentemente el socket autorizado
  sobreviviente del `adb` server: conexiones nuevas completan AUTH pero
  los OPEN pueden quedar en silencio ("sesión activa única"). El formato
  del cliente Kotlin es idéntico al oficial (2 capturas independientes);
  el gate es del firmware, no del protocolo.
- La huella que `dumpsys adb` reporta como `last_key_received` es
  **MD5** del DER de la llave pública (no SHA1/SHA256) en algunas
  versiones de MagicOS; útil para correlación, no para el cliente.

## Consecuencias

- Sin dependencias Maven (sin cons-libs de Apple/Google para ADB).
- El APK funciona como controlador local sin red externa (regla §43).
- Estado de compatibilidad del transporte: `PARTIALLY_VERIFIED`
  (handshake/auth/identify verificados en lab; keyevents pendientes de
  un Android TV de producción real).
- Para licencias §22: el transporte es genérico AOSP; ningún backend de
  consola está implicado.