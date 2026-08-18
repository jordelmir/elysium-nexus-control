# Release Channels (Phase 34)

## Canales

| Canal | Política | Gate |
| --- | --- | --- |
| `nightly` | cada commit verde de develop | build+unit+lint verde |
| `engineering-preview` | releases de desarrollo, debug-signed permitidas SOLO aquí | harness de verificación (no prometido como compatible) |
| `beta` | RC funcionales | TV Node suite verde + Controller 100% unit verde |
| `retail-rc` | candidatos retail con evidencia | fechas/artefactos retail + evidence no vacía |
| `stable` | ÚNICA release que puede ser `PRODUCTION_APPROVED` | Final Truth Gate completo + firmas de release + matrices |

**Regla dura:** artefactos debug NUNCA entran a `stable`. Una release firmada con
la identidad de debug NO puede ser `latest` estable.

## Estado actual (2026-08-17)

- Release GitHub actual: `v0.9.0` — APKs **debug-signed** + DMG, publicada como
  `latest`. Dictamen (P0-18): DEBE reclasificarse como
  **Prerelease / engineering-preview** hasta que existan firmas de release reales
  (Controller Phase 30 ✓ guard fail-closed; TV Node Phase 33 ✓ guard fail-closed;
  identidades/provisión de secrets = pasos manuales del owner).

## Acción

- [ ] Reclasificar `v0.9.0` como Prerelease (engineering-preview)
- [ ] Provisionar `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` / `release.jks`
- [ ] Provisionar `TV_NODE_RELEASE_STORE_PASSWORD` / `TV_NODE_RELEASE_KEY_PASSWORD` / `tv-node-release.jks`
- [ ] Primer `stable` solo tras Final Truth Gate (V010_STATUS)