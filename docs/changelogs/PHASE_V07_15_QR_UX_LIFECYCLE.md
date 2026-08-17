# PHASE V07-15 — REAL QR UX + TV NODE LIFECYCLE

Branch: `fix/v0.10-truth-convergence` · Fecha: 2026-08-17
Alcance: Master Order v0.10, fases 22 (QR real phone↔TV) y 24 (lifecycle del TV Node).

## Qué se entrega

### Fase 22 — REAL QR UX
**TV side (`apps/android-tv-node`):**
- `QrPairingRenderer`: matriz QR pura JVM (ZXing `QRCodeWriter`, dependencia `com.google.zxing:core` 3.5.3 añadida a tv-node y a `:tvlink` para la compilación compartida en el controller). `renderMatrix(payload, size)` devuelve `null` si la sesión no tiene payload vivo (expirada/no abierta) o el tamaño es irrazonable — nunca inventa un QR.
- `PairingActivity`: ahora renderiza el QR real (bitmap 512px) debajo del texto; el código de 6 dígitos se muestra POR SEPARADO (el QR por sí solo nunca pare el dispositivo).

**Phone side (`apps/android`):**
- `TvPairingFlowController`: máquina de estados pura `Idle → AwaitingCode(deviceId) → Ready(identity) | Failed(reason)` sobre un `Gateway` inyectado (resolve NSD → connect → confirm con el código escrito por el usuario). El código NUNCA se auto-advina: `onCodeEntered` exige 6 dígitos y el fallo es terminal (estados Failed no se re-intentan).

### Fase 24 — TV NODE LIFECYCLE
- `TvNodeLifecycleController`: decisiones puras `ReRegisterDiscovery | StopDiscovery | Noop` desde estado observable (listenerBound, networkAvailable, discoveryRegistered). Fail-closed: sin listener nunca toca discovery; sin red desregistra (no anuncia nada inalcanzable).
- `TvNodeApp`: wiring delgado con `ConnectivityManager.registerDefaultNetworkCallback` → `reconcileSurface()` aplica el veredicto; `startDiscovery`/`stopDiscovery` desacoplan el registro de la instancia.

## Verificación (evidencia)
- TV Node: suite completa **107/107** (100 previos + 4 QR renderer + 3 lifecycle), 0 failures.
- Controller: `:app:compileDebugKotlin` OK; tests `core.transport.tvnode` **9/9** (E2E 2/2 previos + `TvPairingFlowControllerTest` 7/7).
- `:app:lintDebug` OK en ambos builds.
- Gates: `final_truth_gate.py` PASS · `tv_claim_policy.py --check` OK · `generate_third_party_notices.py --check` OK.

## Notas
- ZXing `core` es Java puro: los tests JVM del renderer hacen round-trip QR → decode (`RGBLuminanceSource` + `QRCodeReader`) y verifican el texto exacto del payload.
- El escaneo por cámara (ML Kit/CameraX) sigue fuera: la fase entrega render + parse + flujo + validación de código; el hardware de cámara se añade cuando aterrice la capa UI dedicada.