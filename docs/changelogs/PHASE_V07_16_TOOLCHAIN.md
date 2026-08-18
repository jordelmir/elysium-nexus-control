# PHASE V07_16 — SUPPORTED ANDROID TOOLCHAIN (AGP 8.10.1 + Gradle 8.11.1)

**Commit de entrega:** `d379ea6` — 17 de agosto de 2026.

## Master Order v0.10 — Fase 32

Controller y TV Node migrados a la combinación oficial documentada por Google para
compileSdk/API 36: **AGP 8.10.1 + Gradle 8.11.1 + JDK 17 + Kotlin 2.0.21-1.0.28**
(`apps/android/gradle/libs.versions.toml` y `apps/android-tv-node/gradle/libs.versions.toml`;
wrappers `gradle-8.11.1-bin.zip` en ambos).

La combinación anterior (AGP 8.7.3 + Gradle 9.3.1) no está documentada como soportada
para API 36.

## Verificación (pre-Bloque D, ya registrada)
- TV Node: `testDebugUnitTest` + `lintDebug` + `assembleDebug` BUILD SUCCESSFUL.
- Controller: `compileDebugKotlin` + suite completa `testDebugUnitTest` **1328/1328,
  0 failures** (primera corrida completa del controller), KSP 2.0.21-1.0.28.
- Placeholders unsupported restantes compilan sin regresiones bajo el daemon 8.11.1.

## No implica
- No es una promesa de release firmado: las fases 33 (TV Node signing) y 34 (canales)
  siguen pendientes.
- El daemon 8.11.1 puede dejar un proceso vivo tras timeouts largos — se reinicia con
  `./gradlew --stop`.