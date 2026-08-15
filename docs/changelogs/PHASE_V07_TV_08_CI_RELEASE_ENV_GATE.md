# PHASE V07-TV-08 — CI VERDE: GATE DE RELEASE CON `env` CONTEXT (SETTLE DE LA RESTRICCIÓN DE `secrets` EN `if:`)

> Date: 2026-08-15. Infraestructure fix — no maturity del vertical afectada.
> Motivo: la cascada de reds del CI (runs 31895741039, 31896224230, 31896324261,
> 31896692725, 31897145285, 31897196996 — todos `failure`, workflow INVALID,
> 0 jobs) bloqueaba cualquier push posterior a `main`.
> Order: Regla Comercial Hard #9 (fail-closed del release signing, commit
> `0f39958`). Requería: pipeline verde siempre; release R8 SOLO con credenciales
> verificadas; NUNCA un APK release unsigned.

## ROOT CAUSE (confirmado con evidencia)

- El resumen previo asumía que GitHub Actions prohíbe `secrets` en `if:` de STEP
  pero lo PERMITE a nivel de JOB (`aef42d1` movió el build release a su propio
  job con `if: ${{ secrets.RELEASE_STORE_PASSWORD != '' && ... }}` a nivel job).
- GitHub rechazó el workflow igual: `Invalid workflow file:
  .github/workflows/android-ci.yml#L1 (Line: 250, Col: 9): Unrecognized
  named-value: 'secrets'` (run `31896692725`, confirmado leyendo el HTML del
  run). Conclusión verificada: **el contexto `secrets` NO se puede usar en
  NINGÚN `if:` — ni step ni job.**
- Fix original (Gate 7 con `if:` de step, `f75437c`) falló igual (run
  `31896324261`): mismo error con `secrets` en el `if:` del step.

## WHAT CHANGED (`75e8cc2`)

`apps/../.github/workflows/android-ci.yml` — job `release`:

1. **Eliminado** el `if: ${{ secrets... }}` a nivel de job (la causa del parse error).
2. Secrets mapeadas a `env` del job (contexto permitido):
   - `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`.
3. Los STEPS `Build release APK (R8 minification, signed)` y
   `Upload release APK` ahora llevan
   `if: ${{ env.RELEASE_STORE_PASSWORD != '' && env.RELEASE_KEY_PASSWORD != '' }}`
   (el contexto `env` SÍ es válido en `if:` de step).
4. Comentarios actualizados en el job `build` y `release` para reflejar la regla
   real (sin `secrets` en ningún `if:`) y la semántica fail-closed que se
   conserva.
5. YAML validado (`python3 -c "import yaml; yaml.safe_load(...)"` → OK).

Semántica Regla #9 conservada: sin secrets provisionadas, los env quedan vacíos,
los pasos de release se **skippean** (no error, no fallo), el job completa
`success`, y NO se produce ningún APK release unsigned.

## VERIFICATION (evidencia ejecutada)

- Run CI post-fix: **31897386268** — `completed / success`.
  - Workflow PARSED (job `release` presente, steps de release skippeados y
    marcados `-` en el log).
  - Todos los gates verdes: JVM tests → lint → debug → SBOM.
  - Anotaciones: solos avisos no-bloqueantes (`Node.js 20 deprecated`,
    `actions/setup-java@v4 deprecated`). Sin red.
- Antes del fix: `31896692725`, `31896324261` failures por invalid workflow;
  `31895741039`, `31896224230` failures por `RELEASE SIGNING BLOCKED` (Gate del
  release job, esperado sin secrets).
- Estado de secrets del repo: `gh secret list` vacío → la ruta release sigue
  pendiente de Jor (cuando provisione las secrets / release.jks vía `gh secret
  set`, los steps entrarán y el APK firmado se subirá de verdad).

## RECONCILIATION / NOTE

- Documentación previa en comentarios del workflow afirmaba que `secrets`
  era válido en `if:` de job. Es FALSO; esta entrega corrige tanto el código
  como los comentarios (AGENTS.md: si un archivo contradice la realidad, se
  documenta la reconciliación — aquí la realidad (GitHub) gana sobre el track
  del comentario).

## NEXT

- (Jor) provisionar secrets de release + `release.jks` → verificar que los
  steps de release se ejecutan y suben `app-release.apk` firmado.
- Suite TV Node 99/99 sigue PENDIENTE de orden (verify-on-request) — slices
  4/5 permanecen `IMPLEMENTED`.
