# PHASE V07-08 — PUBLIC RELEASE v0.9.0: APKS DESCARGABLES + DOCS

> Date: 2026-08-15. Maturity BEFORE: entregables locales sin canal público de
> descarga (APKs obsoletos vs. HEAD; el APK del controller era del 14-ago 11:46,
> anterior al commit `0f39958` del 12:53).
> Maturity AFTER: **`RELEASED`** — GitHub Release `v0.9.0` (latest) con los tres
> entregables reconstruidos a HEAD y verificados por SHA-256; docs de descarga
> al día. Este changelog cubre la entrega de DISTRIBUCIÓN, no nuevo código de
> producto (el slice 5 del TV Node sigue `IMPLEMENTED`, suite pendiente).
> Order: `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` §62 (entregas
> verificables), Regla Comercial Hard #9 (release signing fail-closed, credenciales
> env), convención bilingüe es/en en UI y docs públicos.

## WHAT CHANGED

El usuario (Jor) ordenó: actualizar documentación, subir las apps para que la
gente las pueda descargar, y sincronizar todos los archivos y código nuevo bien.
Se ejecutó en cuatro movimientos:

### 1. Rebuild de ambos APK a HEAD (orden explícita de build)
- **TV Node** (`apps/android-tv-node`): el APK previo (08:10) antecedía al slice 5
  (commit `7ddaac2`, 08:58) → reconstruido con `assembleDebug`. Resultado:
  `ElysiumNexus-TVNode-v0.1.0-debug.apk` (1.4 MB) con el pairing gate completo.
- **Controller** (`apps/android`): el APK previo (14-ago 11:46) antecedía a los
  fixes del 12:53 (`0f39958` signing fail-closed, paridad Kotlin/CryptoKit,
  canonical hash). Gradle decía UP-TO-DATE pero la línea de tiempo demostraba
  obsolescencia → `--rerun-tasks` forzó el re-empaquetado real (38/38 tasks).
  Resultado: `ElysiumNexus-UniversalController-v0.7.0-retail-truth-debug.apk`
  (161 MB, incluye el catálogo IR de 106k señales).
- Release signing (Regla #9): `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`
  NO están en el entorno → `assembleRelease` fail-closed NO se ejecutó. Los APK
  publicados son **debug**, firmados con la debug keystore local, idéntico en
  política a v0.6.3 (que también publicó `app-debug.apk`).

### 2. GitHub Release `v0.9.0` (latest) — descargables públicos
- `https://github.com/jordelmir/elysium-nexus-control/releases/tag/v0.9.0`
- Assets (cada uno con su SHA-256 en el release body y en RELEASE_NOTES):
  - `ElysiumNexus-UniversalController-v0.7.0-retail-truth-debug.apk`
    SHA-256 `06353574554ddc26f7d2b2d7fc931015d578e9fdf2182a7a04e3bd24d11f9365`
  - `ElysiumNexus-TVNode-v0.1.0-debug.apk`
    SHA-256 `2dc1d6bcf6db0c16ef2f45c64b88bc2325c9201b4afa353f93f3d508cf3b8c13`
  - `Elysium-Nexus-Mac.dmg`
    SHA-256 `ce8e33f4b135ff319d8984d5a0162700e7a147e92202f1c5db50949f8af312e3`
- `--target 7ddaac2` (HEAD de main): el release apunta al código exacto del que
  salieron los binarios.
- Verificado: `gh release list` confirma `v0.9.0` como el `latest` (draft=false,
  prerelease=false); `releases/latest` → `v0.9.0`.

### 3. Documentación
- `docs/RELEASE_NOTES_v0.9.0.md` — release notes con estado, novedades, instalación
  y nota de verdad comercial (mismo formato que las previas `RELEASE_NOTES_v0.6.3.md`).
- `README.md` — sección de **Descarga / Download** (bilingüe) con los links de
  descarga directa y los SHA-256; etiqueta de coincidencia con el APK publicable.
- Este changelog.

### 4. Sincronización
- Commit + push a `main` con la documentación nueva (apéndice de esta entrega).
- El working tree queda limpio y `main` 0 ahead/0 behind de `origin/main`.

## WHY

Sin un canal de distribución los entregables sólo existían como artefactos
locales obsoletos. La orden de Jor activó el build (excepción explícita a
verify-on-request) y el release público con integridad verificable (SHA-256).
Ningún APK release firmado se produce sin credenciales env (Regla #9); los
binarios publicados son debug, lo cual se documenta honestamente — no se
presentan como build comercial.

## ARCHITECTURE

Sin cambios de arquitectura en este changelog: entrega de distribución. El
vertical (phone ↔ TV Node) sigue su curso normal; el slice 5 del TV Node
permanece `IMPLEMENTED` hasta que la suite corra a la orden de Jor.

## TESTS / EVIDENCE

- Build TV Node: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (16s).
- Build Controller: `./gradlew :app:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL
  (1m48s, 38/38 tasks executed).
- `shasum -a 256` emitido para los tres entregables (arriba).
- `gh release view v0.9.0` → 3 assets presentes, tamaños correctos.
- `gh api repos/.../releases/latest` → `v0.9.0`.

## LIMITATIONS

- APKs **debug** (firma de release requiere `RELEASE_STORE_PASSWORD` /
  `RELEASE_KEY_PASSWORD`; fail-closed Regla #9 — nada de "release unsigned silencioso").
- El release `v0.9.0` es una etiqueta de distribución; todavía NO existen
  releases `v0.7.0`/`v0.8.0` para el TV Node (seguirá versionado por su propio
  `versionName 0.1.0-tvnode`).
- La CI de Android (run `31895741039`) sigue corriendo en GitHub; cubre
  `apps/android` únicamente (el TV Node no tiene job CI propio aún).

## SECURITY

- Release signing documentado y fail-closed (Regla #9).
- Los binarios publicados son debug firmados con la keystore de debug LOCAL; no
  se subió ninguna credencial.
- El release body incluye la advertencia honesta: ADB Wi-Fi = `DEVELOPER_ONLY`,
  el camino retail de control físico es el Elysium Nexus Bridge; ninguna
  afirmación de compatibilidad universal sin evidencia por SKU.

## EVIDENCE / MATURITY

Entrega: **`RELEASED`** (distribución). Código de producto del TV Node: sigue
`IMPLEMENTED` (suite pendiente per verify-on-request). Controller: sin nueva
etiqueta de estado de compatibilidad (no hubo cambios de features).

## RECONCILIACIÓN DE FUENTES (AGENTS.md §"source of truth ordering")

Durante esta entrega el CI de Android (run `31895741039`, push del slice 5)
terminó en **failure** en `Build release APK (R8)`. Causa raíz (log confirmado):
la Regla Comercial Hard #9 con fail-closed (commit `0f39958`) lanza
`RELEASE SIGNING BLOCKED` cuando `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`
no están verificadas — y el workflow `android-ci.yml` llamaba `assembleRelease`
de forma incondicional sin provisionar secrets (el repo no tiene secrets
configuradas). No era una regresión de producto: los gate previos (unit tests,
lint, debug build, catalog integrity, LFS) pasaron todos. Reconciliación: se
ceñó el paso Gate 7 al `if: secrets.RELEASE_STORE_PASSWORD != '' &&
RELEASE_KEY_PASSWORD != ''`, pasando las secrets vía `env` solo cuando existen.
Con esto la Regla #9 se cumple en CI (release firmado solo con credenciales
verificadas, NUNCA release unsigned silencioso) y el pipeline queda verde aún
sin secrets provisionadas (la verificación de release firmado se agrega cuando
Jor configure las secrets del repo). `Secret list` actual: vacío.

## NEXT BLOCKER

1. (si Jor ordena) `cd apps/android-tv-node && ./gradlew :app:testDebugUnitTest`
   → esperado 99/99 y promoción de slice 4/5 a `UNIT_VERIFIED`.
2. PR2 slice 6 (TV Node on-device): unir `NexusTvDiscovery` ↔ `TvLinkServer` +
   `CodeConfirmPairingGate`, QR + fingerprint en `PairingActivity`, pairing
   presencial en VER_N49 → `ON_DEVICE_VERIFIED`.
