# Supabase Credential Rotation — Runbook (Phase 28)

**Disparador:** cualquier clave sensible que haya aparecido en chat/logs/artefactos
compartidos se considera `POTENTIALLY_COMPROMISED` hasta evidence de rotación.
(P0-19 del re-audit; un changelog previo recomendó rotar claves expuestas en chat.)

## Política

- **service-role / secret keys: POTENTIALLY_COMPROMISED hasta rotación demostrada.**
  Bypass de RLS → NUNCA en APK, NUNCA en archivos commiteados, NUNCA en workflows
  de CI. Owner-only local scripts.
- **Anon/publishable keys:** client-safe, pueden entrar al APK vía BuildConfig
  desde archivo gitignored.
- Tras rotar: registrar el evento SIN contenido de credencial
  (`SECURITY_EVENT_<ts> rotate service-role; old revoked`).

## Procedimiento (dashboard supabase.com)

```text
1. Settings → API → (service_role | anon) → Regenerate key
2. La nueva clave aparece UNA vez: cópiala al .env local (gitignored)
3. Revoke: el dashboard invalida la anterior al regenerar
4. Gira `SUPABASE_DB_URL` (password de Postgres owner-provided) si se expuso
5. Audit: Settings → Authentication → Logs / Postgres logs → confirmar que la
   clave vieja dejó de usarse (fecha de rotación en adelante = solo nueva)
6. Test: checker read-only local con la NUEVA clave
   (tools/supabase/… — el checker enmascara secrets en output)
7. Nunca loguear la clave nueva; nunca incrustarla en commit
```

## Evidencia de rotación

El registro que CI/agentes pueden verificar es un evento en
`docs/security/SUPABASE_ROTATION_EVENTS.md`:

| Evento | Fecha | Scope | Estado |
| --- | --- | --- | --- |
| *pendiente* | — | service-role (P0-19) | 🔴 NO ROTADA AÚN |

(Normalmente se añadirá una fila `2026-08-17 rotate service-role; old revoked`.)

## Estado de gate

Mientras la fila esté en rojo, el dictamen es:

> `service-role` = POTENTIALLY_COMPROMISED — no usar para integraciones nuevas;
> rotar antes del primer sync de producto cloud. El core local NO depende de ella
> (Fase 30: offline core).