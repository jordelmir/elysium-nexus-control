# PHASE V07-09 — SUPABASE SEGURO: CREDENCIALES PROTEGIDAS + LINK + GUARD CI

> Date: 2026-08-15. Maturity: esta entrega es de INFRAESTRUCTURA/SEGURIDAD (no
> código de producto). Estado: `IMPLEMENTED` (verificado por ejecución real de
> conectividad + link + guard). Orden de la fuente: AGENTS.md §"No commitear
> secretos" + Regla Comercial Hard #9 (fail-closed) + charter de autonomía
> (proteger credenciales, nunca filtrarlas en logs).

## WHAT CHANGED

Jor entregó credenciales de un proyecto Supabase (ref `trccikkcmdqnutwfjrbf`)
con la orden: **usarlas, protegerlo todo (.gitignore, .env, etc.)**. Se ejecutó:

### 1. `.env` local protegido (NUNCA commiteado)
- `/.env` con: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_PUBLISHABLE_KEY`
  (segura de exponer en cliente) y `SUPABASE_SERVICE_ROLE_KEY` +
  `SUPABASE_SECRET_KEY` (marcadas como DANGER ZONE, RLS-bypass, jamás en APK ni
  archivos commiteados).
- `/.env.example` (plantilla commiteada, SOLO placeholders) para que cualquier
  colaborador sepa qué proveer sin copiar secretos.

### 2. `.gitignore` blindado
- Nuevas reglas: `.env`, `.env.*`, con excepción explícita de `!.env.example`
  (la plantilla sí se commitea) y `supabase/.temp`, `supabase/.branches*`.

### 3. Guardia CI anti-fuga (fail-closed) en `android-ci.yml`
- Gate 0 nuevo: `Guard against tracked secrets /.env`:
  - falla si `git ls-files` contiene algún `.env` trackeado;
  - falla si `git grep` encuentra patrones de credenciales vivas de Supabase
    (`sb_secret_*`, `sb_publishable_*`, `SUPABASE_SERVICE_ROLE_KEY=…<30 chars>`)
    en archivos trackeados (excluye la plantilla y docs);
  - patrones exigidos con sufijo largo para no auto-matchezar su propia
    definición (verificado: el guard pasa en el árbol limpio Y detecta un
    archivo con la key real).

### 4. CLI Supabase link (local, `.temp` gitignored)
- `supabase init` + `supabase link --project-ref trccikkcmdqnutwfjrbf` OK.
- `project-ref` guardado en `supabase/.temp/project-ref` (gitignored).
- `supabase/config.toml` commiteable sin secretos (solo placeholders `env(...)`).

### 5. Verificación de conectividad (read-only, secretos enmascarados en logs)
- Storage (anon): HTTP 200 ✅
- PostgREST root (service_role): HTTP 200 ✅ (schemas vacíos — `public` sin
  tablas aún)
- Auth health: 401 (esperado: el endpoint requiere clave/rol; no es fallo)
- El password directo de Postgres (`SUPABASE_DB_URL`) quedó como `CHANGE_ME`:
  Jor tiene el password real (nunca se entrega vía chat).

## WHY

La app Controller necesitará backend cloud (sync de perfiles/mappings,
catálogo, telemetría). Sin protección, una credencial commiteada = breach
inmediato. La política: claves de cliente en el APK (publishable/anon), claves
de servicio SOLO en scripts locales del dueño, y un guard CI que garantiza la
Regla fail-closed "nunca una credencial en el árbol".

## FILES

- `/.env` (gitignored, credenciales reales locales)
- `/.env.example` (commiteado, plantilla placeholders)
- `/.gitignore` (reglas .env/supabase)
- `/.github/workflows/android-ci.yml` (Gate 0 anti-fuga)
- `/supabase/config.toml` (init, sin secretos)
- `/supabase/.gitignore` (generado por CLI)
- `/docs/changelogs/PHASE_V07_09_SUPABASE_SEGURO.md` (este archivo)

## TESTS / EVIDENCE

- `git check-ignore .env` → matchea `.gitignore:25` ✅
- `git check-ignore .env.example` → NO ignorado (se commitea) ✅
- `git grep` de patrones vivos sobre archivos trackeados → sin matches ✅
- Guard régimen de detección probado con archivo sintético (la key real se
  detecta) ✅
- `supabase link` OK, `project-ref` correcto en `.temp` ✅
- curls read-only con códigos 200/401 esperados, secretos enmascarados ✅
- `python3 yaml` valida el workflow ✅

## LIMITATIONS

- Sin tablas todavía en `public` (proyecto fresco o schema por crear).
- `SUPABASE_DB_URL` directo pendiente del password real de Jor.
- El release signing de CI sigue esperando `RELEASE_STORE_PASSWORD` /
  `RELEASE_KEY_PASSWORD` del dueño (job separado, skip quando ausentes).

## SECURITY

- Ninguna credencial real en archivos commiteados (verificado con `git grep`).
- `service_role`/`secret` key solo en `/ .env` local gitignored; nunca en APK.
- El guard CI detecta regresiones automáticamente.
- Si Jor comparte este chat con terceros: **rotar `sb_secret` y el
  `service_role`** (aparecieron en el chat) — la política documentada aquí
  asume que las actuales viven solo localmente tras la rotación.

## NEXT BLOCKER

Decidir qué feature de cloud entra primero: (a) sync de perfiles/mappings del
Controller vía PostgREST + Auth (sign-up con publishable/anon), o (b) catálogo
IR por API, o (c) telemetría/matriz de evidencia. En cuanto Jor elija, crear el
schema (migración SDL) y el módulo cliente Kotlin con el anon key vía
BuildConfig desde archivo gitignored.
