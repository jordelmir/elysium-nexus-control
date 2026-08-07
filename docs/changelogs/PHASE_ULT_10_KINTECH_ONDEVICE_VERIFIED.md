# PHASE_ULT_10_KINTECH_ONDEVICE_VERIFIED.md

> Iteración en la que la semilla multi-marca deja de ser "JSON huérfano" y se
> convierte en el catálogo real que resuelve **Kintech** y **Control Universal
> TV** en el lab Honor Magic V2 (VER_N49, serial `adb-A2VQ024305000780`).

## Qué se buscaba
El usuario reportaba que *tocar Kintech cerraba el APK*. La causa raíz era que
el SQLite v4 (`ir_catalog.db`) embebido no contenía las marcas nuevas del
JSON huérfano (`ir_codes_db.json`): el gestor de candidatos de
`IrConnectFlow` consultaba la DB y no resolvía nada → excepción → crash.

## Qué se hizo
1. **Seeder multi-marca determinista** (`tools/ir-data/seed_curated_brands_v4.py`)
   - `kintech`, `universal_tv`, `Control Universal TV` con 15 acciones
   NEC@38000, source `elysium-nexus-curated` con `production_approved=1`.
   - `samsung`/`lg`/`sony` ya existían → skip idempotente.
   - Manifest regenerado; gate de hash DB↔manifest PASS.
   - Verificación offline del APK final:
     `code_set c4d7c130871ae174` (Kintech) y `433171b542b6`
     (Control Universal TV) presentes con `VOLUME_UP`/`MUTE` y
     `production_approved=1`.
- Test JVM `CatalogCuratedSeedGateTest` (hash binario + marcas presentes): verde.
- Test instrumentado `IrCatalogRepositoryInstrumentedTest` ampliado a las 7
  marcas incluyendo **Kintech** y **Control Universal TV**.

## Bug real encontrado (raíz del crash)
El DataBase Manager hacía, en ANSI del asset drift la siguiente secuencia:

1. `dbFile.delete()`
2. copiar asset → `tmpFile`
3. `tmpFile.outputStream().fd.sync()` ← **reabre el archivo con un
   `FileOutputStream()` nuevo, que por defecto TRUNCA a 0 bytes**
4. `verifyDatabaseIntegrity(tmpFile)` → `quick_check` sobre DB de 0 bytes
   devuelve `ok` (SQLite trata un archivo vacío como DB válida)
5. rename → el `ir_catalog.db` final quedaba **vacío (0 bytes)**

Consecuencia en el runtime: cada query abría/cerraba una conexión propia y
el archivo 0 bytes producía exactamente `SQLiteException: no such table:
code_sets ... (OS error - 2: No one of file/archivo)` durante la
preparación, y el UI colapsaba al tocar *Kintech*.

Además: `getDatabase()` abría una conexión nueva **por query** y la
cerraaba al final; si el archivo se reemplazaba mientras quedaban
conexiones huérfirlas, el prepare fallaba con `OS error - 2`.

## Fix
- **Manager** (`IrCatalogDatabaseManager`): la instalación ahora escribe el
  asset **directamente al path final** con overwrite y fsync en el MISMO
  stream abierto (nunca reabro con `FileOutputStream()` para sync). Verifica
  `length() > 0` y `integrity` DESPUÉS del copy y borra solo si falla.
- **Repository** (`IrCatalogRepository`): una sola conexión read-only
  cacheada por proceso (guard + synchronized), abierta **después** del
  install exitoso. Quitados los 8 `database.close()` de los queries (cerraban
  la conexión cacheada → `attempt to re-open an already-closed object`).

## Verificación on-device (VER_N49)
- Suite instrumentado completo (`connectedDebugAndroidTest`): **verde**
  (ReleaseBlocker + repository + probe).
- Probe `DbManagerProbeInstrumentedTest` confirma que el DB instalado queda
  **no-vacío** tras la instalación del asset (una extensión del fix).
- Flujo visual real via ADB (uiautomator):
  - Home → *Controles de TV* → **Kintech** → Paso 1 de 6 →
    *COMENZAR PRUEBA* → **"Prueba de Volumen — Candidato 1 de 1" +
    "TRANSMITIDO: 38000 HZ"** — sin crash.
  - Idéntico para **Control Universal TV** (Paso 2 de 6, 38000 HZ).
- **Observación**: el logcat de este honor (MagicOS) viene CIFRADO por un
  layer HKS; los logs de app no son legibles por adb. El `logcat -b crash`
  no muestra entradas del paquete.

## Estado / roadmap
- Nivel 1: dictamen v0.5.0 ok (selector determinista), ahora con Kintech/
  Control Universal TV resolviéndose desde catálogo seed.
- Próximos: Nivel 2 (prefs IR + sondeo avanzado) y Nivel 3 (garantía de
  desgaste) — ver `docs/architecture/MASTER_ORDER.md` §45.

## Archivos tocados
- `apps/android/app/src/main/java/.../IrCatalogDatabaseManager.kt`
- `apps/android/app/src/main/java/.../IrCatalogRepository.kt`
- `apps/android/app/src/main/assets/ir/ir_catalog.db` (+ manifest)
- `apps/android/app/src/androidTest/.../IrCatalogRepositoryInstrumentedTest.kt`
- `apps/android/app/src/androidTest/.../DbManagerProbeInstrumentedTest.kt` (nuevo)
- `apps/android/app/src/test/.../CuratedSeedGateTest.kt` (nuevo)
- `tools/ir-data/seed_curated_brands_v4.py` (nuevo)
- `tools/ir-data/seed_kintech_v4.py` (nuevo, precursor)