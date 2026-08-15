# Elysium Nexus Universal Control OS — Release Notes v0.9.0

> **Estado: RELEASED (descargables públicos en GitHub Release).** Commit: `7ddaac2` (main).
> APKs debug (firma de release requiere credenciales env verificadas, Regla Comercial Hard #9).

---

## Descargables

| Entregable | SHA-256 |
|-----------|---------|
| `ElysiumNexus-UniversalController-v0.7.0-retail-truth-debug.apk` (161 MB) | `06353574554ddc26f7d2b2d7fc931015d578e9fdf2182a7a04e3bd24d11f9365` |
| `ElysiumNexus-TVNode-v0.1.0-debug.apk` (1.4 MB) | `2dc1d6bcf6db0c16ef2f45c64b88bc2325c9201b4afa353f93f3d508cf3b8c13` |
| `Elysium-Nexus-Mac.dmg` (4.6 MB) | `ce8e33f4b135ff319d8984d5a0162700e7a147e92202f1c5db50949f8af312e3` |

Download: https://github.com/jordelmir/elysium-nexus-control/releases/tag/v0.9.0

```bash
adb install -r ElysiumNexus-UniversalController-v0.7.0-retail-truth-debug.apk
adb install -r ElysiumNexus-TVNode-v0.1.0-debug.apk
open Elysium-Nexus-Mac.dmg
```

---

## Lo nuevo en esta versión

### Distribución pública (v0.9.0) — `7ddaac2`
- Primer release **unificado y descargable**: Controller (Android phone), TV Node
  (Android TV/Google TV) y Mac Agent, los tres reconstruidos al código actual de
  `main` y verificados por SHA-256.
- El APK del controller se re-empaquetó con `--rerun-tasks` (el artefacto local
  estaba obsoleto respecto a `0f39958` signing fail-closed).
- El APK del TV Node incluye por primera vez el **PR2 slice 5 (pairing gate)**:
  el servidor exige, antes de `CHANNEL_READY`, una `PAIR_CONFIRM` sellada que
  prueba el código de 6 dígitos + el nonce del QR, y pinnea el fingerprint del
  peer de forma durable en el vault (reconexión sin código tras el primer pairing).

### Base de producto (ya en `main`)
- **TV Node** (`0.1.0-tvnode`): emparejamiento QR + código, canal autenticado
  X25519+HKDF+ChaCha20-Poly1305, discovery NSD `_elysium-tv._tcp`, transporte
  TCP real, respuestas autenticadas, observación honesta. Maturity: transporte +
  pairing = `IMPLEMENTED` (suite pendiente de batch), observación =
  `ON_DEVICE_VERIFIED` parcial.
- **Controller** (`0.7.0-retail-truth`): catálogo IR local-first (106,033 señales,
  4,715 code sets, gate de integridad + elegibilidad), transporte USB-C Mac/PC,
  BT HID, ADB Wi-Fi (`DEVELOPER_ONLY`), LAN.
- **Mac Agent** (`v0.8.0+`): control del Mac por LAN (`_elysium._tcp` :7878),
  gestos → `CGEvent`.

---

## Nota de verdad comercial

Ningún claim de compatibilidad universal ni "100% de TVs" sin evidencia física
por SKU (escala de 11 niveles, `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md`).
El camino retail de control físico garantizado es el **Elysium Nexus Bridge**
(IR TX/RX + BLE + USB-C). ADB Wi-Fi es estrictamente `DEVELOPER_ONLY`.

## Documentación de esta entrega

- `docs/changelogs/PHASE_V07_08_PUBLIC_RELEASE_V09.md`
- `README.md` → sección **Descarga / Download**
- `docs/compatibility/TRANSPORT_MATRIX.md` (estados medidos §33)

## Siguiente frente

- Batch de tests del TV Node en cuanto Jor ordene (`:app:testDebugUnitTest`,
  esperado 99/99) → promoción slice 4/5 a `UNIT_VERIFIED`.
- PR2 slice 6 (TV Node on-device): pairing presencial QR + código en el
  TV VER_N49 → `ON_DEVICE_VERIFIED`.
