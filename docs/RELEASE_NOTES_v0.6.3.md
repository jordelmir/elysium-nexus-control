# Elysium Nexus Universal Control OS — Release Notes v0.6.3

> **Estado: RELEASED (APK debug publicado en GitHub Release).** Commit: `a39fc78` (main).
> APK: `app-debug.apk` · SHA-256 `580e668f5f280a4e8dace8f3ea9f03856a18dd88b8bf087e3f9ace8fe24ab0f8`

---

## Lo nuevo en esta versión

### ULT.21 — CONTROL UNIVERSAL · WI-FI (ADB real) — `571e6b6`
- **Transporte ADB real en JVM pura** para Android TV / Google TV / Fire TV:
  handshake CNXN/AUTH completo (firma RSA-SHA1 + `AUTH RSAPUBLICKEY`),
  servicio `shell,v2,raw:` byte-idéntico al cliente adb oficial (verificado
  por captura de tráfico), ACK espejo, `TCP_NODELAY`.
- **Llave RSA persistida** (SharedPreferences): la TV muestra el diálogo
  "Allow USB debugging" UNA sola vez; después reconecta solo.
- **Sección UI**: card neon en el Hub → escaneo mDNS `_adb._tcp` +
  barrido de subred (puerto 5555) + IP manual → teclado de control
  (pad direccional, OK, vol ±, mute, power, home, menu, transporte, canales).
- `AdbTvMemory` (`193a4c3`): reconexión automática a la última TV.
- Estado de compatibilidad: `PARTIALLY_VERIFIED` (handshake/auth/identify
  contra adbd real en laboratorio; keyevents pendientes de un Android TV
  de producción).

### ULT.18 — IR: máquina de estados + recuperación de sesión — `aee94ba`
- **Fix "Session Recovery Failed"**: el restore de sesión valida el hash
  del catálogo; si cambió entre force-stop y relaunch, se descarta la
  sesión obsoleta y se reinicia el barrido automáticamente.
- `IrProbeEngine`/`ProbeCursor` con `CursorState`/`CursorInitResult`
  explícitos (fin del pseudocódigo en el motor).
- `IrRuntimeDiagnostics`: telemetría IR tipada (logcat + FileLog, MagicOS).
- Endurecimiento: `MacTransport` fail-closed (PAIR_OK obligatorio) y fix
  del `CredentialVault` (AndroidKeyStore AES-GCM).

### Base (ya publicada en v0.6.0–v0.6.2)
- Catálogo IR instalable con build identity + gate de elegibilidad (§7/§8).
- Barrido universal IR, sonda Konka, transporte USB-C (Mac/PC), BT HID.
- Matriz de compatibilidad de transportes §33 (`docs/compatibility/`).

## Documentación de esta entrega

- `docs/changelogs/PHASE_ULT_18_IR_RUNTIME.md`
- `docs/changelogs/PHASE_ULT_21_ADB_WIFI.md`
- `docs/adr/ADR-ADB-001-REAL-ADB-TRANSPORT.md` (hallazgos del wire + quirks MagicOS)
- `docs/compatibility/TRANSPORT_MATRIX.md` (estados medidos §33)
- Evidencia de laboratorio: `docs/testing/evidence/ULT18/`, `docs/testing/evidence/ULT21/`

## Instalación

```
adb install -r app-debug.apk
```

APK debug (firmado con la debug keystore local). Para distribución
comercial se firma con la keystore de release.

## Siguiente frente

- Verificar keyevents ADB contra un Android TV real (pasar ADB Wi-Fi a
  `VERIFIED_LAB`).
- Puerta de licencias §2/§22 para backends de consola.
