# Supabase — RLS-First Cloud Architecture (Phase 30)

**Principio absoluto:** el control local JAMÁS depende del cloud. Supabase solo
sirve: sync opcional, backup de evidencia, retail feeds, corroboración comunitaria,
device graph, telemetría opt-in.

## Orden de construcción (RLS-FIRST)

```text
1. Supabase Auth (antes de cualquier tabla de producto)
2. Row Level Security en TODAS las tablas de negocio
3. ownership user/device (cada fila con owner_id resuelto del JWT)
4. roles retailer (MONGE_CR / GOLLO_CR / VERDUGO_CR / staff)
5. evidence append-only policy (INSERT permitido, UPDATE/DELETE prohibido)
6. audit logs (tabla append-only, service-role-only)
7. rate limits (Supabase rate limiting + capas por rol)
8. service_role: SOLO server-side (edge functions / scripts owner)
```

## Política de ejemplo (referencia para la migración)

```sql
-- evidence: append-only por política, nunca modificable por clientes
alter table evidence_rows enable row level security;

create policy "evidence_append_only_owner"
on evidence_rows for insert
to authenticated
with check (
  (select auth.uid()) = owner_id
);

create policy "no_update_evidence"
on evidence_rows for update as restrictive
using (false);

create policy "no_delete_evidence"
on evidence_rows for delete as restrictive
using (false);

-- lectura: dueño o retailer con rol
create policy "evidence_read_owner_or_retailer"
on evidence_rows for select
to authenticated
using (
  (select auth.uid()) = owner_id
  or exists (
    select 1 from retailer_roles r
    where r.uid = (select auth.uid())
      and r.retailer = retailer_name
  )
);
```

## Reglas duras

| Item | Regla |
| --- | --- |
| service-role key | SERVER-ONLY; prohibida en APK/workflows/commits |
| anon key | client-safe (BuildConfig desde archivo gitignored) |
| RLS | activado antes del primer dato de producto; sin tablas públicas |
| evidencia | append-only; correcciones = superseding records (fase 2) |
| local core | cero llamadas cloud en la ruta del botón VOL+ |

## Estado actual

- `IMPLEMENTED`: higiene (`.env` gitignored, `.env.example`, leak guard, checker
  read-only que enmascara secrets).
- `DESIGNED`: este documento. Migraciones SQL llegan ANTES que las tablas de
  producto.