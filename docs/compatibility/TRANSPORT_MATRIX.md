# Matriz de compatibilidad de transportes (§33)

> Regla §33: no hay claims silenciosos. Cada transporte declara su estado
> medido: `VERIFIED_LAB` · `VERIFIED_COMMUNITY` · `PARTIALLY_VERIFIED` ·
> `UNVERIFIED` · `REGRESSION` · `BLOCKED`. Actualizado: 2026-08-13.

| Transporte | Ruta | Estado | Evidencia | Notas |
|---|---|---|---|---|
| ADB Wi-Fi (Android TV/Google TV/Fire TV) | `AndroidTvAdbAdapter` + `AdbWirelessClient` | `PARTIALLY_VERIFIED` | `docs/testing/evidence/ULT21/` (handshake, AUTH dialog, identidad, trazas del wire oficial ×2) | Handshake/auth/identify probados contra adbd real (Honor MagicOS). Keyevents pendientes de un Android TV de producción. |
| BT HID (remote universal) | `BluetoothHidTransport` | `VERIFIED_LAB` | fases ULT.5–ULT.7 | Emparejado y controlado contra host real en laboratorio. |
| USB-C HID (Mac/PC) | `UsbCConnectionScreen` + daemon host | `VERIFIED_LAB` | fases ULT.8–ULT.9 | Latencia sub-ms medida en lab. |
| Mac Wi-Fi (mDNS + X25519) | `MacTransport` + agent | `VERIFIED_LAB` | fases ULT.8–ULT.9 | Fail-closed PAIR_OK (ULT.18). |
| IR (blaster/blaster-less) | `IrProbeEngine` + catálogo | `VERIFIED_LAB` | ULT.13–ULT.18, `docs/testing/evidence/ULT18/` | Barrido universal + sonda Konka real. |
| HMDI-CEC (roto en fase activa) | — | `UNVERIFIED` | — | Sin hardware CEC en lab. |
| Consolas licenciadas (PS/Xbox/Switch) | — | `BLOCKED` | `docs/licensing/` | Requiere licencia + SDK + secrets (§2, §22). |

## Estados por dispositivo de laboratorio

| Dispositivo | Modelo | Transportes verificados |
|---|---|---|
| Honor Magic V2 | VER-N49 (MagicOS 8, adbd hisuite) | IR, USB-C, Mac, ADB-handshake (quirk "sesión activa única" documentado en ADR-ADB-001) |

## Cómo se actualiza

1. Nueva evidencia en `docs/testing/evidence/ULT<N>/` (screenshots, logs, capturas).
2. Subir el estado de la fila SOLO con medición; bajar a `REGRESSION` si
   un test del lab falla tras un cambio.
3. La fila `PARTIALLY_VERIFIED` pasa a `VERIFIED_LAB` cuando el control de
   teclas (keyevent/volumen) se mide end-to-end contra un Android TV real
   (TCL/Hisense/Sony/Fire TV).