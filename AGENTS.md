# AGENTS.md — Elysium Nexus Universal Controller

> Project memory for any agent (Mavis, Codex, Cursor, OpenCode, Aider, Gemini CLI, Devin, …) operating in this repository.
> Read this file first. Then read the latest `docs/changelogs/PHASE_0_*.md`.
> If the latest changelog points to specific subsystem docs, read those too.

## What this project is

A platform that turns any Android (initial lab device: **Honor Magic V2**) into a **universal, professional, dynamic control surface** for:

* Mac / Windows / Linux desktop
* Other Android / Android TV / Chromebook
* PS Remote Play / Xbox Remote Play
* Generic HID hosts (Steam, browsers, emulators, native games)
* A future physical **Elysium Nexus Receiver** (BLE / Wi-Fi / USB)
* Licensed console backends (PS4/5, Xbox One/Series, Switch/2) — **only after vendor authorization**

Code name: **Elysium Nexus Universal Controller**.

The whole order lives in `docs/architecture/MASTER_ORDER.md` (mirrored from the user's first prompt). Treat it as the constitution. Every architectural decision references one or more of its sections.

## Hard rules (no exceptions)

These mirror `MASTER_ORDER.md` §2, §3, §4, §6, §30, §35, §38, §41, §45. They are not negotiable.

1. **No impersonation of commercial devices.** We do not forge DualSense, DualShock, Xbox Controller, Joy-Con, or Pro Controller. We ship our own descriptor under our own VID/PID (allocated via the USB-IF path) named `Elysium Nexus Gamepad`. We never present a fake Xbox- or PlayStation-branded identity to a host. (See §2, §18.)
2. **Licensed console backends are gated.** Direct PS4/5, Xbox One/Series, Switch/2 backends compile **only** when a vendor license, an authorized SDK, and provisioned secrets are present. Without those, the build is `REQUIRES_VENDOR_LICENSE` and the runtime surface is empty. (§2, §21, §22, §23, §24, §41.)
3. **No Accessibility abuse for gamepad injection.** Accessibility Service is **not** a substitute for a real system-level gamepad. We use Accessibility for accessibility. (§25.)
4. **Disconnection must neutralize everything.** Test #38 is a release blocker. If a single input is "stuck" after an abrupt disconnect, the change is rejected. (§38.)
5. **No silent claims.** We do not assert compatibility we have not measured. Compatibility states are exactly: `VERIFIED_LAB`, `VERIFIED_COMMUNITY`, `PARTIALLY_VERIFIED`, `UNVERIFIED`, `REGRESSION`, `BLOCKED`. (§33, §44.)
6. **No Rust as decoration.** If a Rust crate is added, it must justify a JNI/FFI boundary with a benchmark. Otherwise it lives in `crates/` only when a measured benefit exists. (§9.)
7. **No GlobalScope in Kotlin.** Structured concurrency tied to lifecycle or service scope. No leaks. (§31.)
8. **No `unwrap()` in production Rust.** Typed errors, no panics on external data. (§31.)
9. **No device-hardcoding.** Honor Magic V2 is the **lab** device, not the **target**. All capabilities come from `WindowManager` + `InputDevice` + sensor introspection. Foldable postures are first-class. (§0, §16.)
10. **No commercial / cloud gate for the core.** The APK must work as a local controller without a network. (§43, item 25.)
11. **No "completado" claims for UI or pseudocode.** A subsystem is `VERIFIED` only when it has code, tests, and a green build. (§44.)

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

## Build / test commands (from `apps/android-controller/`)

```bash
cd apps/android-controller

./gradlew help                              # sanity-check the wrapper
./gradlew :app:testDebugUnitTest            # JVM unit tests
./gradlew :app:assembleDebug                # builds app-debug.apk
./gradlew :app:lintDebug                    # lint, abortOnError=true
./gradlew clean :app:testDebugUnitTest \
              :app:assembleDebug \
              :app:lintDebug                # the full Phase 0.1 gate
```

There is no `gradlew` at the workspace root in 0.1 — each subsystem
keeps its own build entry point. We introduce a workspace-level
Gradle invocation when a second module needs to be cross-built with
the Android one (anticipated in Phase 4 when firmware and Rust
crates join).

Wrapper lives at `apps/android-controller/gradlew` (added in Phase 0.1).

## Source of truth ordering (when files disagree)

1. `docs/architecture/MASTER_ORDER.md` (the user's order) wins on intent.
2. The latest `docs/changelogs/PHASE_<N>_*.md` wins on current state.
3. `docs/adr/ADR-*.md` explains why a given decision was taken.
4. Code is the final word on what *is* shipped.

If any of these contradict each other, write a focused note in the next changelog explaining the reconciliation. Do not silently edit the order to match the code.
