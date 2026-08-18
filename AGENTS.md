# AGENTS.md — Elysium Nexus Universal Control OS

> Project memory for any agent (Mavis, Codex, Cursor, OpenCode, Aider, Gemini CLI, Devin, …) operating in this repository.
> Read this file first. Then read the latest `docs/changelogs/PHASE_*.md`.
> If the latest changelog points to specific subsystem docs, read those too.

## What this project is

**Elysium Nexus Universal Control OS** — a platform that transforms any authorized control intent into the correct action on the correct device via the best available route, with minimal configuration, practical latency, execution evidence where possible, and automatic recovery from failures.

Code name: **ELYSiUM NEXUS — UNIVERSAL INTENT FABRIC / RETAIL TRUTH OS**.

The governing constitution is `docs/architecture/MASTER_ORDER_SOFTWARE_FIRST.md` (consolidated 68-section software-first order, 2026-08-15: phone + TV Node + IR + Bluetooth + LAN + evidence; software before hardware; retail truth), supported by `docs/architecture/MASTER_ORDER.md`
(100-point Universal Intent Fabric order) updated with **v0.7 Retail Truth Master Implementation Order**.

## Maturity Scale (Strict Commercial Taxonomy)

Only the following 11 maturity levels are permitted to describe subsystem or compatibility status:
1. `CONCEPT` — Proposed idea, architectural note.
2. `DESIGNED` — Documented spec, ADR or interface design.
3. `IMPLEMENTED` — Code written and compiled.
4. `UNIT_VERIFIED` — Covered by passing JVM/Rust unit tests.
5. `INTEGRATION_VERIFIED` — Integration test suite green.
6. `ON_DEVICE_VERIFIED` — Transmitted cleanly from physical host (e.g. Android ConsumerIrManager TX_OK).
7. `REAL_DEVICE_VERIFIED` — Observed physical reaction on target device (e.g. TV responds to optical pulse).
8. `HIL_VERIFIED` — Dual-path hardware-in-the-loop test lab verified (optical raw capture + independent decoder).
9. `DEVICE_MATRIX_VERIFIED` — Complete CORE action matrix verified on specific exact device MPN.
10. `RETAIL_MATRIX_VERIFIED` — 100% of dated active SKU matrix for a retailer (Monge, Gollo, Verdugo) verified.
11. `PRODUCTION_APPROVED` — Signed build meeting all 25 Final Commercial Truth Gate checks.

No other vocabulary (such as "universal", "compatible with all TVs", "100% supported") may imply proof without evidence.

## Commercial Hard Rules (v0.7 Retail Truth)

1. **NO MOCK. NO FAKE TV. NO TEMPLATE-AS-TRUTH. NO GUESSED COMPATIBILITY. NO AI-GENERATED PRODUCTION IR CODE. NO SILENT FALLBACK. NO CLAIM WITHOUT EVIDENCE.**
2. **Retail Target**: GOLLO_CR, MONGE_CR, VERDUGO_CR. "100% CORE VERIFIED" claims are generated exclusively from physical evidence against a dated active SKU matrix.
3. **No impersonation of commercial devices.** We do not forge DualSense, DualShock, Xbox Controller, Joy-Con, or Pro Controller. We ship our own descriptor under our own VID/PID named `Elysium Nexus Gamepad`.
4. **Licensed console backends are gated.** Direct PS4/5, Xbox One/Series, Switch/2 backends compile **only** when a vendor license, an authorized SDK, and provisioned secrets are present.
5. **Disconnection must neutralize everything.** Test #38 is a release blocker.
6. **No silent claims.** Compatibility states are strictly derived from evidence using the 11-level maturity scale.
7. **ADB Wi-Fi is DEVELOPER_ONLY.** ADB transport is strictly classified for developer/lab use and must NEVER be presented as a consumer retail control path.
8. **Experimental Codecs Gated.** Codecs marked `EXPERIMENTAL` (`RC5`, `RC6`, `Kaseikyo`) are `LAB_ONLY` and blocked from commercial runtime candidate lookups.
9. **No hardcoded release credentials.** Release signing requires verified environment variables (`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`). Build fails closed if missing.
10. **Hardware-Ecosystem Baseline.** Commercial guarantee is backed by **Elysium Nexus Bridge** (IR TX/RX + BLE + USB-C) for universal physical control across all smartphones.

### Supabase credential handling (local project, 2026-08-15)

- Project ref: `trccikkcmdqnutwfjrbf`. Endpoint: `https://trccikkcmdqnutwfjrbf.supabase.co`.
- Credentials live ONLY in `/.env` (gitignored). Template committed as `/.env.example` (placeholders only).
- **Anon / publishable keys are client-safe** and may be wired into the APK via BuildConfig from a gitignored file.
- **Service-role / secret keys bypass RLS — NEVER in an APK, never in committed files or workflows.** Owner-only local scripts.
- CI Gate 0 (`android-ci.yml`) fails closed if `.env` is tracked or a live credential pattern appears in tracked files.
- If chat/shared logs exposed a key: **rotate it** in the Supabase dashboard before use.
- Remote state (verified 2026-08-17): DB reachable from this Mac only via the **IPv4 pooler**
  `aws-0-us-east-1.pooler.supabase.com:6543` (role `postgres.trccikkcmdqnutwfjrbf`; the
  `db.<ref>.supabase.co` host is IPv6-only here). `public` has **0 business tables**; the
  only function is `rls_auto_enable`, an event trigger that enables RLS on every new
  `public` table (RLS at birth — do not fight it, design policies with it). `.env`
  `SUPABASE_DB_URL` already points to the pooler.
- Integrations (2026-08-17): Supabase **MCP server** configured globally in
  `~/.config/opencode/opencode.jsonc` (remote, project-ref scoped, OAuth completed via
  `opencode mcp auth supabase`); **agent skills** installed at
  `.agents/skills/supabase` and `.agents/skills/supabase-postgres-best-practices`.
  The MCP becomes available to an opencode session only after restarting opencode.
- Direct Postgres (`SUPABASE_DB_URL`) password is owner-provided, never committed.

## Repository layout (from §6)

```
elysium-nexus-controller/
├── apps/
│   ├── android-controller/      # the APK (Phase 1 onward)
│   ├── macos-agent/             # Phase 3
│   ├── windows-agent/           # Phase 3
│   ├── linux-agent/             # Phase 3
│   └── profile-studio/          # editor host (later)
├── firmware/                    # Nexus Receiver (Phase 4)
├── crates/                      # shared Rust core (input-core, mapping-core, …)
├── platform/                    # backend adapters per host
├── schemas/                     # versioned JSON/proto schemas
├── databases/                   # mappings, devices, games, compatibility
├── tools/                       # importers, profilers, testers
├── docs/
│   ├── architecture/            # vision, master order, ADRs
│   ├── adr/                     # decision records
│   ├── research/                # Phase 0 investigations
│   ├── protocol/                # Elysium Link spec
│   ├── hardware/                # receiver selection
│   ├── security/                # threat model, crypto notes
│   ├── licensing/               # matrix, contracts
│   ├── testing/                 # test plans, soak reports
│   ├── compatibility/           # device matrix
│   └── changelogs/              # PHASE_<N>_<NAME>.md per iteration
└── .github/workflows/
```

Every module must declare: **purpose · public API · owner · allowed deps · tests · threat model · maturity**. Empty modules are forbidden (§6, last paragraph).

## Working contract with the user (Jor)

Mirrored from `~/.minimax/memory/user.md`. Apply it here the same way it applies to Elysium Vanguard.

* **Continuous execution.** "sigue sin parar hasta completar el proyecto". Don't ask "¿sigo o pauso?". Pick the next smallest concrete sub-task and ship it.
* **One deliverable per turn minimum.** Code + tests + build + changelog + media. No status-only turns.
* **Quality floor.** 0 lint errors, all unit tests green, `assembleDebug` green. If a test catches a real bug, fix the bug, keep the test, surface it.
* **No platform gates.** "puede pesar 20GB, qué me vale". No size pressure, no Play Store packaging, no min-SDK dance for store compliance. We DO respect min-SDK where it's a real device-compatibility need (the cheap-Android degradation path is a *feature*, not a gate).
* **Bilingual UI strings stay bilingual** (es + en), as the existing convention dictates once UI lands.
* **Test-discovered regressions are good news.** Surface them. Don't bury.
* **Verify-on-request (Jor, 2026-08-09).** Duración de las olas de gradle: Jor manda que NO se corra la compilación/pruebas por fase. Regla: escribir código + tests unitarios como parte de cada entrega, actualizar changelog/matriz, commitea; correr gradle (`testDebugUnitTest`/`assembleDebug`/`lintDebug`/`compileDebugKotlin`) SOLO cuando Jor lo pida explícitamente. La verificación batch final se ejecuta cuando Jor pide "haz las pruebas". No compilar "por si acaso".
* **Operating mode (Jor, 2026-08-09).** Modo de trabajo:
  - Durante el desarrollo: solo editar código, revisar archivos y documentar. Cero pruebas, cero lint, cero suites, cero compilaciones — a menos que Jor lo ordene.
  - Cuando Jor ordene: compilaciones incrementales + pruebas específicas durante el desarrollo; la suite completa SOLO antes de publicar.
  - Un solo proceso Gradle a la vez (sin colisiones).
  - Cambios divididos en entregas comprobables (commit por fase).
  - Actualizaciones claras de estado: implementando → probando → compilando → subiendo → esperando CI.
  - Reutilizar el APK cuando el hash demuestre que no cambió.
  - Sin loops de autopilot ni subagentes: trabajo directo del agente principal.

## Iteration loop (mirrors `elysium-autopilot` for this project)

```
forever:
    read latest docs/changelogs/PHASE_<N>_*.md     # what we just shipped
    read docs/architecture/MASTER_ORDER.md §45       # where we're going
    read relevant docs/adr/ADR-*.md                 # decisions already taken
    pick the smallest concrete sub-task that
        - is well-scoped (≤1 focused session)
        - has an obvious first commit
        - layers cleanly on existing code
        - unblocks the most downstream
    implement end-to-end:
        - code
        - unit tests (JVM tests for Kotlin, cargo test for Rust)
        - ./gradlew :app:testDebugUnitTest       # green
        - ./gradlew :app:assembleDebug           # green
        - ./gradlew :app:lintDebug               # green
        - docs/changelogs/PHASE_<NEW>_<NAME>.md
        - update this file or AGENTS.md if reusable
    next iteration
```

The loop halts only when:
1. A test fails in a way that needs an architectural decision Jor must sign.
2. Hardware Jor must touch (e.g., flashing the Nexus Receiver dev board for the first time).
3. A vendor license / SDK needs to be obtained (gate the build, write a `docs/licensing/STATUS.md`, continue with non-gated subsystems).

## Build / test commands (from `apps/android/`)

```bash
cd apps/android

./gradlew help                              # sanity-check the wrapper
./gradlew :app:testDebugUnitTest            # JVM unit tests (1,021 tests)
./gradlew :app:assembleDebug                # builds debug APK
./gradlew :app:assembleRelease              # builds release APK (R8)
./gradlew :app:lintDebug                    # lint, abortOnError=true
./gradlew :app:cyclonedxDirectBom           # generates SBOM (CycloneDX)
./gradlew clean :app:testDebugUnitTest \
              :app:assembleDebug \
              :app:lintDebug                # the full Phase 0.1 gate
```

Python tooling:
```bash
cd tools/ir-data
python3 catalog.py                          # unified catalog build (uses ingest_v5)
python3 ingest_v5.py --profile production   # Schema v4 native ingestion
python3 seed_device_models_v4.py            # populate device_models for ranking
python3 verify_source_locks.py              # verify source lock integrity
ruff check *.py --select E,F,W             # Python lint
```

There is no `gradlew` at the workspace root — each subsystem
keeps its own build entry point.

Wrapper lives at `apps/android/gradlew`.

## CI pipeline (`.github/workflows/android-ci.yml`)

Every push triggers:
1. Catalog integrity (SHA + quick_check + foreign_key_check)
2. Source lock verification
3. Python lint (ruff)
4. JVM unit tests (1,021)
5. Android lint
6. Debug build
7. Release build (R8 minification)
8. SBOM generation (CycloneDX, 268 components)
9. Artifact upload (debug APK, release APK, SBOM, test results)
10. Instrumented tests on Android 34 emulator

## Source of truth ordering (when files disagree)

1. `docs/architecture/MASTER_ORDER.md` (the user's order) wins on intent.
2. The latest `docs/changelogs/PHASE_<N>_*.md` wins on current state.
3. `docs/adr/ADR-*.md` explains why a given decision was taken.
4. Code is the final word on what *is* shipped.

If any of these contradict each other, write a focused note in the next changelog explaining the reconciliation. Do not silently edit the order to match the code.
