# Phase ULT.20 — System Back Button: Session-Killing Fix

Code name: «El back ya no mata la sesión».

## Problem (Jor, reporte directo)

1. Presionar la flecha atrás del sistema cerraba la app inmediatamente
   (vuelo directo al home del Android, proceso muerto).
2. Al reabrir, la app arrancaba «de cero»: splash completo, Hub inicial,
   conversación/progreso perdido.

## Root cause (verificado en código)

- `MainActivity` no interceptaba el botón back del sistema:
  no había `BackHandler` ni `onBackPressedDispatcher`.
- `navStack = remember { mutableStateOf(...) }` se perdía ante cualquier
  recreación de la Activity (background + proceso muerto, o la propia
  navegación del sistema). Resultado: «de cero» siempre.

## Fix (código, §38)

1. **`HubStackCodec.kt`** (nuevo, puro JVM): serializa `HubDestination`
   a códigos estables (`hub`, `tvc`, `cat:NAME`, `cpk:cat:id`,
   `cdev/id`, `con/id`, `ip`, `ctl/id`, `ur`, `usbc`, `ac/id`,
   `learn`, `al`, `sl`, `macd`).
   - Destinos con objetos vivos (`MacPairing`/`MacControl`,
     `AutomationEditor`/`SceneEditor`) deliberadamente NO restaurables:
     degradan al padre.
   - `decodeStack` degrada a `listOf(Hub)` ante cualquier código inválido.
2. **`MainActivity.kt`**:
   - `navStack` ahora `rememberSaveable` con `Saver` vía `HubStackCodec`
     → la pila sobrevive recreaciones y proceso muerto.
   - `splashVisible` pasó a `rememberSaveable` → no se re-reproduce el
     splash de 2.8s al restaurar la sesión.
   - `BackHandler` (siempre activo):
     - Tope `IrLearner` → `IrCaptureBridge.stop()` + limpia resultado.
     - Tope `MacPairing`/`MacControl` → `macTransport.disconnect()`.
     - Pila > 1 → pop (navegar hacia atrás, igual que el botón ATRÁS de UI).
     - Pila == 1 (raíz Hub) → `moveTaskToBack(true)`: la app va al fondo,
       el proceso y la sesión sobreviven.

## Verificación física (Honor Magic V2, 2026-08-13)

| # | Prueba | Resultado |
|---|--------|-----------|
| 1 | Hub → Controles de TV → `keyevent 4` | POP a Hub, app viva (no cierra) |
| 2 | Back en la raíz (Hub) | `moveTaskToBack`: PID 7107 intacto, Activity preservada |
| 3 | Controles de TV → HOME → reabrir | **Restaura «Controles de TV»** (sin splash, sin «de cero») |

Test JVM: `HubStackCodecTest` — 11 pruebas verdes (round-trip de todas
las rutas, degradación de destinos vivos, códigos inválidos → Hub).
Suite total: **1,226 tests verdes** · `assembleDebug` verde · `lintDebug` verde.

## Evidencia

- `docs/testing/evidence/ULT20/ULT20_01_restored_after_back.png`
  (pantalla restaurada «Controles de TV» tras reabrir).
- UI dumps en cada paso de la tabla anterior.

## Estado de madurez

- Comportamiento de back: `VERIFIED_LAB` (Honor Magic V2, Android 14).
- La restauración a pantallas profundas (learner incluido, `learn` el
  resultado se pierde a propósito → vuelve a escuchar) es estable.