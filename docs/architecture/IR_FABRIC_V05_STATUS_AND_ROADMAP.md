# Estado del Dominio IR v0.5.0 y Hoja de Ruta de Ingeniería

> **Fecha**: 6 de Agosto de 2026  
> **Versión**: `v0.5.0-engineering-preview`  
> **Commit**: `f36473ce6440970f812a257128222ef2061e761d`  
> **Estado de la Suite**: Build Gradle Verde (Unit Tests: PASS | Assemble: PASS | Lint: PASS)

---

## 1. Lo que se ha Implementado (Nivel 1 — Path Autoritativo de Señales)

Se corrigieron los 10 bloqueadores principales del flujo de producción que impedían transmitir comandos reales tras guardar perfiles.

### 1.1 Persistencia y Binding Autoritativo de Señales
- **Cero `signalId` Fabricados**: `IrConnectFlow.kt` elimina la generación de IDs tipo `sig_${winnerCandidate.id}_${action.name}`. Los comandos se construyen utilizando **únicamente** los `signalId` devueltos por SQLite (`ir_catalog.db`). Si una señal no posee binding en la base de datos, se omite.
- **Validación SHA-256 Pre-Transmisión**: En `TvControlScreen.kt`, antes de emitir cualquier comando IR, se calcula el hash físico de la señal recuperada del catálogo (`actualFingerprint`) y se verifica que coincida exactamente con el hash del perfil instalado (`binding.physicalFingerprint`).
- **Eliminación de Fallbacks Físicos en Sondeo**: Si una marca o modelo no posee datos en SQLite, el flujo no emite comandos NEC/Samsung inventados. Se introduce `ProbeUiState.NoCompatibleCandidates`, mostrando una interfaz con opciones explícitas para el usuario.

### 1.2 Persistencia Room y Navegación por `profileId`
- **Room Database API Asíncrona**: `InstalledIrProfileRepository.kt` implementa métodos `suspend` (`saveProfileSuspend`, `getProfileSuspend`) sobre `ElysiumUserDatabase` (Room), eliminando el monopolio del archivo JSON legacy.
- **Pantalla "Mis Controles" (`InstalledProfilesScreen.kt`)**: Nueva pantalla accesible desde el Hub que lista todos los perfiles de control IR guardados en almacenamiento local, con opciones de apertura directa o eliminación.
- **Navegación Persistente**: `HubDestination.Control` transporta `profileId: String?`. Tras un reinicio de proceso o `adb force-stop`, la aplicación re-hidrata el perfil desde la autoridad persistente en lugar de depender de objetos temporales.

### 1.3 Calidad de Código y Runtime
- **Singleton Repository en UI**: `TvControlScreen` utiliza una única instancia de `IrCatalogRepository(context)` recordada mediante `remember`.
- **Botones Dinámicos**: La cuadrícula del control remoto se sintetiza dinámicamente desde las acciones contenidas en el perfil instalado (`activeProfile.commands.keys`).
- **Manejo Seguro de Excepciones**: En `IrProtocol.encode()`, se cambió `catch (e: Throwable)` por `catch (e: Exception)` para no interceptar `CancellationException` ni errores graves del JVM.
- **Integración con Universal Fabric**: `DefaultActionTranslator` asigna acciones `Protocol.DirectIr` y `Protocol.HubIr` a `DeviceState.IrCommand`.
- **Actualización de Versión**: Gradle actualizado a `versionCode = 5` y `versionName = "0.5.0-engineering-preview"`.

---

## 2. Lo que Falta para Completar el Sistema (Hoja de Ruta)

### 2.1 Nivel 2 — Confiabilidad Técnica y Supply Chain de Datos

1. **Unificación de Pipelines Python (Bloqueador #13)**
   - Consolidar `build_catalog.py` y `build_v4_catalog.py` en una única herramienta CLI autoritativa: `python tools/ir-data/catalog.py build`.
2. **Procedencia y Licencia Estricta por Archivo (Bloqueadores #9, #10, #11)**
   - Ingerir realmente todas las fuentes aprobadas (no solo Flipper-IRDB).
   - Calcular hashes reales de contenido (`content_sha256`), commits de introducción y estado de licencia por archivo (`APPROVED` vs `QUARANTINED`).
3. **Poblado de Modelos y Relaciones en Catálogo (Bloqueadores #9, #14, #15)**
   - Poblar las tablas `device_models`, `code_set_models` y `signal_sources`.
   - Garantizar que el exportador canónico incluya la tabla `signal_sources` y aplique la normalización Unicode NFKC.
4. **Preservación de Variantes de Protocolos (Bloqueador #6)**
   - Evitar degradar SIRC12/15/20 a un mismo enum genérico. Conservar `variantId`, parámetros extendidos, políticas de repetición (`RepeatPolicy`) y alternancia (`TogglePolicy`).
5. **Máquina de Estados de Sondeo y Confirmación Multi-Acción (Bloqueadores #23, #24)**
   - Introducir `ProbeAttempt(attemptId, candidateId, codeSetId, signalId, action)`.
   - Exigir la verificación de al menos 3 acciones (`VolumeUp`, `VolumeDown`, `Mute`) del mismo `codeSetId` antes de marcar el perfil como verificado.

### 2.2 Nivel 3 — Verificación Física (HIL) y Producto Industrial

1. **Harness HIL (Hardware-in-the-Loop) (Bloqueador #29)**
   - Construir el harness de prueba con microcontrolador (ESP32/Arduino) y receptor TSOP.
   - Validar que el LED del Honor Magic V2 emita la frecuencia de carrier exacta y que la trama decodificada por el receptor coincida bit a bit con la señal esperada.
2. **Decoder Round-Trip Autónomo (Bloqueador #7)**
   - Promover los codecs de `UNIT_SHAPE_VALIDATED` a `GOLDEN_VECTOR_VERIFIED` comprobando la decodificación completa contra los 49 vectores dorados.
3. **Build de Release Firmada y CI Completo (Bloqueadores #30, #31)**
   - Configurar la firma con Keystore de producción (no firma debug).
   - Incorporar en GitHub Actions los pasos de lint, build del catálogo, verificación canónica y SBOM.
