# AGENTS.md — Elysium Nexus

> Project memory for any agent (Codex, Claude Code, Aider, Cline, etc.) operating in this repository.
> Read this file first. Then read `docs/architecture/MASTER_ORDER.md` (the governing constitution).
> If implementing, also read the relevant docs/changelogs/PHASE_*.md.

## What this project is

**Elysium Nexus** — a **Software-First Universal TV Fabric** that converts an authorized human intent into the correct physical or software action on the correct TV through the best verified route available.

**Pivot fundamental (2026-08-15):** Elysium Nexus is no longer "an APK with many controls." It is a **distributed control platform**: phone + TV Node + IR + Bluetooth + LAN + evidence. Software-first with what we have today; hardware only where physically impossible via software.

**Core cycle:**
```
User Intent → UniversalAction → Best Verified Route → TV Node → confirms resultado
```

**Commercial target:** MONGE → EL VERDUGO → GOLLO (Costa Rica retailers). Compatibility earned model-by-model, action-by-action.

## Governing Constitution

The single source of truth is **`docs/architecture/MASTER_ORDER.md`** (68 sections). Read it before any implementation session. Key sections:

- §0 — Absolute Product Constitution (non-negotiable rules)
- §1 — Reality / Maturity Model (CONCEPT → PRODUCTION_APPROVED)
- §3 — Final Product Model (Universal Intent Fabric)
- §4 — Software-First Rule (Phase A = phone + TV only)
- §5 — TV Node architecture (`apps/android-tv-node/`)
- §28 — Route Planner (multi-transport scoring)
- §33 — Software-Only Success Gate (must pass before hardware)
- §40 — Elysium Physical Corpus (primary empirical data)
- §42 — Retailer Knowledge Graph
- §49 — First Commercial Matrix (MONGE first)
- §58 — PR Sequence (PR1–PR14)
- §65 — Software-Only Acceptance Test
- §68 — Final Release Truth Gate (30 gates)

## Maturity Scale

Only these 11 levels:

1. CONCEPT
2. DESIGNED
3. IMPLEMENTED
4. UNIT_VERIFIED
5. INTEGRATION_VERIFIED
6. ON_DEVICE_VERIFIED
7. REAL_DEVICE_VERIFIED
8. HIL_VERIFIED
9. DEVICE_MATRIX_VERIFIED
10. RETAIL_MATRIX_VERIFIED
11. PRODUCTION_APPROVED

No other vocabulary may imply proof without evidence.

## Repository Layout

```
elysium-nexus-control/
├── apps/
│   ├── android-controller/      # Phone APK (consumer surface)
│   ├── android-tv-node/         # TV-side Node app (Phase A)
│   ├── macos-agent/             # Future
│   └── profile-studio/          # Future
├── shared/
│   ├── nexus-domain/
│   ├── nexus-protocol/
│   ├── nexus-crypto/
│   ├── nexus-identity/
│   ├── nexus-capabilities/
│   ├── nexus-evidence/
│   └── nexus-serialization/
├── firmware/                    # Nexus Bridge (Phase B)
├── hub/                         # Future hub services
├── crates/                      # Shared Rust core
├── schemas/                     # JSON/proto schemas
├── databases/
│   └── devices/                 # TV profiles, IR codes
├── tools/                       # Importers, profilers, validators
│   ├── ir-data/                 # Catalog pipeline
│   ├── ir-analyzer/
│   ├── compatibility-runner/
│   └── claims-audit/
├── ir-data/                     # IR signal database
│   ├── data/
│   ├── schema/
│   └── approvals/
├── docs/
│   ├── architecture/            # MASTER_ORDER.md, REALITY_LEDGER.md, ARCHITECTURE_MAP.md
│   ├── adr/                     # Decision records
│   ├── changelogs/              # PHASE_<N>_<NAME>.md per iteration
│   ├── protocol/                # Elysium Link protocol spec
│   ├── hardware/                # Nexus Bridge docs
│   ├── security/
│   ├── testing/
│   └── compatibility/           # Device matrix
├── art/
└── .github/workflows/
```

## Source of Truth Ordering (when files disagree)

1. `docs/architecture/MASTER_ORDER.md` (user's constitution) wins on intent.
2. Latest `docs/changelogs/PHASE_<N>_*.md` wins on current state.
3. `docs/adr/ADR-*.md` explains why a decision was taken.
4. Code is the final word on what **is** shipped.

If any contradict each other, write a focused note in the next changelog. Do not silently edit the order to match the code.

## Hard Rules

- NO MOCK AS PRODUCTION. NO FAKE DEVICE SUPPORT. NO GUESSED IR COMMAND.
- NO AI-GENERATED PRODUCTION IR SIGNAL. NO SILENT FALLBACK.
- NO CLAIM WITHOUT EVIDENCE.
- TX_OK ≠ TV reacted. Database entry ≠ working signal.
- An UNKNOWN value must never silently become "generic", "supported", or "compatible".
- ADB is DEVELOPER_ONLY. Never a consumer retail control path.
- Accessibility is explicit/user-authorized. Never silent.
- No hardcoded release secrets. Build fails closed if signing config invalid.
- Software-only Phase A must succeed before any hardware (Phase B) begins.

## Working Contract with the User (Jor)

- **Continuous execution.** "Sigue sin parar hasta completar el proyecto." Pick the next smallest concrete sub-task and ship it.
- **One deliverable per turn minimum.** Code + tests + changelog. No status-only turns.
- **Verify-on-request.** Write code + unit tests per deliverable, update changelog, commit. Run gradle tests/builds ONLY when Jor explicitly asks. No compilation "por si acaso."
- **No platform gates.** No Play Store size/packaging pressure.
- **Bilingual UI** (es + en) once UI lands.
- **Test-discovered regressions are good news.** Surface them.

## PR Sequence (from §56)

PR1 — Nexus Domain + Identity + Crypto
PR2 — Universal Protocol
PR3 — TV Node Baseline
PR4 — Discovery + Pairing
PR5 — Nexus TV IME
PR6 — Accessibility Enhanced Mode
PR7 — Bluetooth HID Transport
PR8 — Software-Only IR Oracle (CORE priority)
PR9 — Route Planner + DeviceTwin
PR10 — Retail Data Graph
PR11 — Nexus Bridge Prototype (after software gate)
PR12 — Physical Acquisition
PR13 — HIL
PR14 — Retail Matrix Pilot

## Build / Test Commands

```bash
# Android (phone controller)
cd apps/android-controller
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

# Android TV Node
cd apps/android-tv-node
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug

# Python tooling
cd tools/ir-data
python3 catalog.py
python3 verify_source_locks.py
ruff check *.py

# Rust (shared crates)
cd crates
cargo test
```