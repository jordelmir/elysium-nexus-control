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
- **CORRECCIÓN 2026-08-17 (segunda pasada de Jor: "yo no las roté")**: el 401 anterior de
  `publishable`/`anon` contra `/rest/v1/` era un **falso positivo de la ruta**: el endpoint
  raíz sin tabla exige service-role (`{"hint":"Only secret API keys can be used for this endpoint."}`).
- Verificación concluyente en `/auth/v1/token?grant_type=password` (con credenciales de prueba):
  anon JWT clásico → `400 invalid_credentials` y `sb_publishable_` → `400 invalid_credentials`.
  400 en vez de 401 = la **KEY AUTENTICA** correctamente; el 400 es de las credenciales falsas.
- Estado real: **las 4 credenciales (.env) son válidas contra el proyecto**. No hay rotación
  de plataforma; no hay key rota.
- Pendiente de mitigación (buena práctica, no urgente): las keys viajaron por chat; rotación
  con `tools/supabase/rotate_service_role.py` sigue disponible cuando Jor provea `SB_ACCESS_TOKEN`.
- Pendiente para catálogo remoto vía REST/APK: password del rol `postgres` para
  `SUPABASE_DB_URL` (Jor la entregó como placeholder `[YOUR-PASSWORD]`).
- Spec OpenAPI del proyecto (`.rest` schema): expone **solo `POST /rpc/rls_auto_enable`** —
  cero tablas públicas. Coherente con la estrategia RLS-first (Fase 36): el catálogo remoto
  consumible por el APK no existe aún como tablas expuestas; es trabajo posterior, no un defecto.
- **Conexión Postgres directa (2026-08-17, con password entregada por Jor)**: `db.trccikk…`
  no tiene ruta IPv4 en este Mac (IPv6-only, "No route to host"); conecta por el **pooler
  público** `aws-0-us-east-1.pooler.supabase.com:6543` con rol `postgres.trccikk…` (mismo
  password). Verificado: `current_user=postgres`, schemas stock (auth/extensions/graphql/
  public/realtime/storage/vault), **cero tablas en `public`**, única función
  `rls_auto_enable` = event trigger que habilita RLS automáticamente en toda tabla nueva de
  `public` (RLS de nacimiento, estrategia confirmada en vivo).
- `.env`: `SUPABASE_DB_URL` actualizada al endpoint pooler funcional (gitignored).
- Password de la DB entregada por chat → recomendar rotación de la DB password en el
  dashboard (Database → Reset password) junto con la rotación de API keys, cuando Jor provea
  credenciales nuevas.

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