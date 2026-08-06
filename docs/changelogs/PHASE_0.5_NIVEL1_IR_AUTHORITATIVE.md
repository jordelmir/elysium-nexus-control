# PHASE 0.5 — Nivel 1: Refactorización Autoritativa del Path IR

## Contexto y Diagnóstico

Basado en el dictamen técnico riguroso de 31 puntos, se identificó un fallo crítico en el flujo de sondeo y transmisión:
1. `IrConnectFlow` fabricaba identificadores `sig_${winnerCandidate.id}_${action.name}` si no encontraba el binding directo en memoria. Esto producía un `signalId` inexistente en SQLite.
2. `TvControlScreen` consultaba `repo.getSignal(binding.signalId)`, fallando silenciosamente con "Signal ... missing from SQLite catalog".
3. `TvControlScreen` instanciaba un `IrCatalogRepository(context)` por cada pulsación de botón, abriendo y cerrando SQLite ineficientemente.
4. La navegación transfería un objeto `InstalledIrProfile` temporal sin permitir recuperar el perfil desde almacenamiento persistente por `profileId` tras `force-stop`.
5. Faltaba una pantalla dedicada ("Mis Controles") para listar perfiles guardados.

## Cambios Implementados

### 1. Path de Señales Autoritativo sin Fabricación (`IrConnectFlow.kt`)
- Se eliminó la generación de `signalId` inventados. Si una acción no proviene de `commandSignalIds` o `commandBindings` reales de SQLite, es omitida.
- Si un candidato ganador no tiene ningún binding real, se rechaza la instalación del perfil.
- Se agregó la variante `ProbeUiState.NoCompatibleCandidates` para manejar explícitamente marcas sin señales en SQLite, ofreciendo opciones guiadas (búsqueda por modelo, control o marca alternativa) en lugar de fallbacks hardcodeados.

### 2. Control Remoto Autoritativo y Verificación de Hash (`TvControlScreen.kt`)
- Se centralizó `IrCatalogRepository` mediante `remember { }` (instancia única a nivel de pantalla Compose).
- Se implementó la verificación de fingerprint SHA-256 (`actualFingerprint != binding.physicalFingerprint`) antes de transmitir. Si el catálogo cambia o la señal se corrompe, se aborta la transmisión con un log explícito.
- Se agregaron botones dinámicos sintetizados directamente desde `activeProfile.commands.keys` en lugar de guiarse únicamente por los botones del `DeviceTemplate`.
- Soporte para carga diferida de perfiles mediante `profileId` desde Room/almacenamiento persistente.

### 3. Pantalla "Mis Controles" y Navegación Autoritativa (`InstalledProfilesScreen.kt`, `HubNavigation.kt`, `MainActivity.kt`)
- Se creó `InstalledProfilesScreen.kt` ("Mis Controles"), permitiendo visualizar la lista de perfiles guardados localmente, eliminarlos o abrirlos directamente.
- Se actualizó `HubDestination.Control` para aceptar `profileId: String?`.
- Se integró la navegación desde la pantalla principal mediante una tarjeta destacada "Mis Controles" en el Hub.

### 4. Room Database Async API (`InstalledIrProfileRepository.kt`)
- Se agregaron métodos `suspend` (`saveProfileSuspend`, `getProfileSuspend`) para operaciones de Room sin bloquear el hilo UI.

### 5. Configuración y Limpieza de Excepciones (`IrProtocol.kt`, `build.gradle.kts`, `ActionDispatcher.kt`)
- En `IrProtocol.encode()`, se cambió `catch (e: Throwable)` por `catch (e: Exception)` para no tragar excepciones fatales o de cancelación de coroutines.
- En `build.gradle.kts`, se actualizó a `versionCode = 5` y `versionName = "0.5.0-engineering-preview"`.
- En `ActionDispatcher.kt`, `DefaultActionTranslator` ahora produce `DeviceState.IrCommand` cuando la ruta es de protocolo infrarrojo.

## Estado de Verificación
- **Unit Tests**: Ejecutados vía Gradle.
- **Build**: APK Debug generado.
- **Lint**: Verificado.
