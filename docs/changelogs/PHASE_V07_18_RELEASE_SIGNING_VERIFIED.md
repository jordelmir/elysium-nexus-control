# Phase V0.7-18 — Release Signing Verified + TV Node Unsigned Regression Fix

> Fecha: 2026-08-17 · Tipo: verificación batch dirigida (guard releases) + fix de regresión
> Alcance: Controller + TV Node release signing, fail-closed guards, credenciales Supabase protegidas

## Objetivo

Completar la verificación dirigida pendiente del Bloque D: `assembleRelease` firmado y
verificable en **ambos** builds (Controller y TV Node), cerrando la Regla Comercial Hard #9
(fail closed: jamás un release sin firma) con evidencia ejecutada.

## Problema 1 — env nunca llegaba a Gradle (zsh)

- Síntoma: el guard bloqueaba con `RELEASE DIAG: envStore=0` pese a `source secrets/release-credentials.env`.
- Causa: las líneas del archivo eran `VAR=valor` **sin** `export`; `source` en zsh asigna
  variables de shell que NO se exportan a procesos hijo → el daemon JVM de Gradle nunca veía el env.
- Fix operativo + documentado: cargar con `set -a; source secrets/release-credentials.env; set +a`
  y el archivo ahora usa `export VAR=...` explícito en cada línea.
- Verificado: `assembleRelease` sin el export falla cerrado; con export, firma completa.

## Problema 2 — `app-release-unsigned.apk` en TV Node (regresión de firma)

- Síntoma: TV Node `:app:assembleRelease` producía `app-release-unsigned.apk` incluso con
  credenciales correctas; el guard no se disparaba.
- Causa raíz: en `apps/android-tv-node/app/build.gradle.kts` el bloque `buildTypes { release { ... } }`
  estaba declarado **antes** de `signingConfigs { create("release") }`. AGP resuelve los
  buildTypes eagerly: `signingConfigs.findByName("release")` devolvía `null` en ese momento y
  `signingConfig` jamás se asignaba.
- Fix: reordenar `signingConfigs` **antes** de `buildTypes` (mismo orden que el Controller,
  que funcionaba) + nota de causa raíz en el archivo para que no se reordene.
- Verificado: `app-release.apk` firmado con `CN=Elysium Nexus TV Node`.

## Problema 3 — keystores PKCS12 con store/key passwords distintos

- Síntoma (Controller): `KeytoolException: Failed to read key elysium-nexus: Given final block not properly padded`.
- Causa: el secrets file tenía `RELEASE_STORE_PASSWORD` ≠ `RELEASE_KEY_PASSWORD`; la spec
  PKCS12 exige un único password (AGP apksigner lo aplica estrictamente; `keytool -certreq`
  lo ignora silenciosamente — por eso el diagnóstico anterior no lo detectó).
- Fix: secrets file unificado a un único password por build (mismo valor para store y key),
  keystores regenerados (`release.jks` Controller + `tv-node-release.jks` TV Node, RSA 4096,
  PKCS12). Los keystores anteriores quedaron como `*.bak-2026-08-17` — **NO trackeados**
  (`.gitignore`: `*.jks.*`) y sin secretos en el repo.
- **Nota de identidad**: los certificados de firma son nuevos (2026-08-17). Nada publicado
  dependía de los anteriores (pre-release interno).

## Evidencia ejecutada (2026-08-17)

| Build | Resultado | Firma verificada (apksigner verify) |
| --- | --- | --- |
| `apps/android :app:assembleRelease` | BUILD SUCCESSFUL | `CN=Elysium Nexus Controller, OU=Retail Truth, O=Elysium Nexus, C=CR` (SHA-256 `451fd518…`) |
| `apps/android-tv-node :app:assembleRelease` | BUILD SUCCESSFUL | `CN=Elysium Nexus TV Node, OU=Retail Truth, O=Elysium Nexus, C=CR` |
| Guard sin credenciales | BUILD FAILED (BLOCKED, fail closed) | — |
| Ambos keystores `keytool -list` | 1 entrada cada uno, PKCS12 | — |

Nota operativa introducida (útil en CI local): el guard imprime `RELEASE DIAG:` con el estado
de `storeFile`/longitudes de password (nunca valores) cuando va a bloquear.

## Credenciales Supabase — hallazgos de la verificación local (P0-19)

- Las 3 credenciales entregadas están en `/.env` (gitignored, `chmod 600`), nunca impresas.
- `SUPABASE_SECRET_KEY` (service-role): **LIVE** — REST `/rest/v1/` responde 200.
- `SUPABASE_PUBLISHABLE_KEY` y `SUPABASE_ANON_KEY` (clásico): **401** contra `/rest/v1/`.
  Interpretación: las keys anon/publishable del `.env` no verifican contra el gateway actual
  (posible rotación reciente en el dashboard o key revocada). **Bloqueante para cualquier uso
  del APK vía publishable.**
- Management API con `sb_secret_`: 401 (no es una credencial de Management API — esperado).
- **Pendiente externo (Jor)**: rotar la service-role — la key fue entregada por chat y debe
  considerarse expuesta. Un solo paso: crear `SB_ACCESS_TOKEN` (dashboard → Account → Access
  Tokens) → `tools/supabase/rotate_service_role.py` rota todo automáticamente (fail-closed,
  revoca la vieja, actualiza `.env`, registra el evento). Y verificar/regenerar las keys
  publishable/anon en el dashboard para desbloquear el APK.

## Archivos tocados

- `apps/android/app/build.gradle.kts` — diagnóstico `RELEASE DIAG` en el guard.
- `apps/android-tv-node/app/build.gradle.kts` — signingConfigs antes de buildTypes (fix unsigned).
- `.gitignore` — `*.jks.*` (cubre backups de keystores).
- `secrets/release-credentials.env` — (gitignored) exports unificados, password único.
- `tools/supabase/rotate_service_role.py` — pendiente de sesión anterior; listo y fail-closed.
- `docs/security/SUPABASE_ROTATION_EVENTS.md` — estado: NO ROTADA aún.

## Próximo paso sugido

Verificación batch completa bajo demanda de Jor ("haz las pruebas"): suite unitaria + lint +
assembleDebug en ambos proyectos.