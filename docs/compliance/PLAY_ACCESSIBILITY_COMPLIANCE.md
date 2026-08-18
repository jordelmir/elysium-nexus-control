# Play Console — AccessibilityService Compliance (Phase 27)

**Estado:** IMPLEMENTED (política + test) — la UI de consentimiento llega con la
superficie Compose (fase posterior).

## Principio rector

**Accessibility es Enhanced Mode OPCIONAL, nunca el core.** El control legítimo de
volumen funciona sin Accessibility (verificado por test
`phase 27 - core volume control works without the accessibility grant` en
`TvNodeCoreTest` → Verdict.Confirmed con solo `volumeExecutable`).

## Requisitos Play (AccessibilityService API)

1. **Declaración en Play Console** — la app declara el uso de
   `android.accessibilityservice.AccessibilityService`, con la finalidad
   declarada: control remoto universal de TV (navegación/Home/Back por acción
   global), filtrado opcional de teclas remotas y observación de foreground.
2. **Aviso destacado dentro de la app (prominent disclosure)** — pantalla/gate
   ANTES de cualquier uso del servicio que explique:
   - qué datos se leen (solo eventos de teclado/foreground, nunca contenido),
   - cómo se usan (traducir tecla → acción UniversalAction, nunca captura de
     texto de usuario para el servicio),
   - qué se comparte (nada; todo parsea en el dispositivo).
3. **Consentimiento afirmativo (affirmative consent)** — el usuario activa el
   servicio él mismo en Ajustes → Accesibilidad (flujo del sistema) y confirma
   con un botón explícito en la app; nunca arranca solo.
4. **Política de privacidad en Play** — enlaza a la política del producto y a
   esta página.
5. **Video de demostración y revisión (Play Console)** — el envío del accesibility
   declara el caso de uso exacto y adjunta video de demostración antes del GA.

## Prohibiciones (automatización)

- CERO automatización autónoma de UI del usuario fuera de la acción remota
  pedida explícitamente (un solo clic = un solo action remoto).
- CERO recolección oculta.
- CERO datos no declarados.
- CERO uso del servicio como keepalive (Phase 24: lifecycle honesto vía
  ConnectivityManager, no Accessibility).

## Core sin Accessibility (obligatorio)

| Ruta | Requisito |
| --- | --- |
| IR (controller) | ConsumerIrManager — sin Accessibility |
| TvLink wire | pairing + canal AEAD — sin Accessibility |
| Volumen (TvActionExecutor) | `volumeExecutable` (AudioManager) — sin Accessibility |
| Media transport | media session + notification listener — sin Accessibility |

## Verificación

- `TvNodeCoreTest` → test Fase 27: `Confirmed` con grants sin accessibility.
- El manifesto declara el servicio con `canRequestFilterKeyEvents` solo tras
  consentimiento (flow futuro de UI).