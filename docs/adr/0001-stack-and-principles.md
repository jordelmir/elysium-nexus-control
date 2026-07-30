# ADR-0001 — Stack and Principles for Elysium Nexus

| Field        | Value                                              |
| ------------ | -------------------------------------------------- |
| Date         | 2026-07-30                                         |
| Status       | Accepted (Phase 0.1)                               |
| Deciders     | Mavis (engineering lead) + Jor (project owner)     |
| Section refs | `MASTER_ORDER.md` §6, §9, §11, §26, §31, §41       |

## Context

`MASTER_ORDER.md` §6 mandates a multi-language, multi-platform repository
(Android APK + macOS/Windows/Linux agents + Rust crates + firmware +
schemas + databases + tools). The very first executable iteration (§45)
is a small, measurable Android-only slice. We have to pick the
foundational toolchain now — every later subsystem is built on top of
it.

Constraints in scope:

* Lab device: Honor Magic V2 (Android 14, MagicOS). Architecture must
  not couple to Honor. (§0.)
* Engine language: **preferably Rust** but "no Rust as decoration;
  justify the JNI boundary with a benchmark". (§9.)
* Concurrency: structured, no `GlobalScope`, no `unwrap()` in
  production. (§31.)
* Testability: JVM unit tests must be cheap and fast for the core
  math; Context-dependent code must be testable without Robolectric
  gymnastics. (Engineering memory: define a narrow interface that
  captures only the values read; Hilt module adapts the Android
  concrete in production; tests stub with a 5-line impl.)
* Quality floor: 0 lint errors, all unit tests green, `assembleDebug`
  green. (Project `AGENTS.md`.)

Constraints explicitly out of scope for this ADR:

* Picking a USB-IF VID (deferred to 0.2).
* Picking a Bluetooth Company ID (deferred to 0.2).
* Adding DI, persistence, networking, BLE, UI — earned per subsystem.
* Picking a Rust/JNI boundary (deferred to the benchmark §9 asks for).

## Decision

### Build system

* **Gradle 9.3.1** wrapper, **AGP 8.6.x**, **Kotlin 2.2.21**.
* **compileSdk = 34, targetSdk = 34, minSdk = 26.** minSdk 26 = Android
  8.0 = the lowest version that still receives the `BluetoothHidDevice`
  improvements worth targeting. Bumping to 27+ is reviewed in 0.2 only
  if a real device class needs it.
* Single Android module for now: `apps/android-controller`. We split
  into `apps/android-controller:app` + `apps/android-controller:core`
  when the core math stops fitting comfortably in one module (estimate:
  by Phase 1 end).

### Languages

* **Kotlin for everything Android-side** in 0.1–1.x. UI, canonical
  engine, transport, services, ViewModel. Pure Kotlin where possible so
  JVM unit tests are fast.
* **Rust only when a measured benefit exists.** Phase 0.2 writes the
  canonical engine in Kotlin. If a benchmark (e.g. a stick-mapping
  10k-iter loop or a parser test) shows Kotlin is the bottleneck, we
  spin a Rust crate in `crates/input-core/` and justify the JNI
  boundary with numbers in `docs/research/`. Otherwise the Rust
  directory stays empty per §6.
* **Swift** (macOS), **C++/Rust** (Windows), **Rust** (Linux) are picked
  when we get to their respective phases. This ADR does not pre-decide.

### Libraries in 0.1

* None beyond the AGP defaults. We deliberately start with no Compose,
  no Hilt, no Room, no Retrofit, no OkHttp, no Kotlinx Serialization.
  Each is added with a written reason in the changelog of the iteration
  that adds it.
* **JUnit 4** for the test source set (matches AGP 8.6 default; no need
  to add JUnit 5 yet).
* **No detekt / ktlint** in 0.1. Both will land in 0.5 alongside CI.

### Threading

* **Structured concurrency.** Every coroutine is launched in a
  lifecycle- or service-scoped `CoroutineScope`. No `GlobalScope` in
  production code. (Pathological patterns are caught in code review and
  by `Lint` rules added later.)
* **Single shared scheduler** for the canonical engine: a dedicated
  `CoroutineDispatcher` (single-threaded, ordered) so all state mutations
  are serialized and racy handoff is impossible. We will define the
  scheduler in 0.2.

### State management

* Canonical model is immutable `data class` / `value class` snapshots.
  Mutations happen in the engine by `copy()`. Sticks/triggers/touch
  arrive as new snapshots; the engine compares against the last one
  and rejects (does not mutate) on regression / out-of-range / NaN /
  Infinity. (§9.)
* No `MutableState` of Compose gets updated per stick sample. (§11.)

### Identity

* **No VID/PID assigned yet.** We do not copy or claim any commercial
  identity. (§2.)
* The receiver is the *only* path that *must* have a stable USB VID;
  it will be discussed when we get to Phase 4 hardware selection.

### Quality gates

* `./gradlew help` must be green from the very first iteration.
* `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`
  must be green before any phase is declared `VERIFIED`.
* The "release blocker" test from §38 is a property-test in the
  neutralization pipeline; it is implemented in 0.4.

## Consequences

* 0.1 stays small. No premature complexity. No abandoned dependencies.
* Every later subsystem has a *smallest possible first commit* that
  doesn't drag the whole stack behind it.
* When we need Compose / Hilt / Room, we will earn them. When we need
  Rust, we will earn it. The earning is what makes the codebase
  defensible at review.
* The first concrete violation we will catch and fix is **"added a
  library without a written reason"**. The rule is: a `build.gradle.kts`
  line that adds a dependency is a one-line ADR.

## Alternatives considered

* **Compose + Material 3 from day 1.** Rejected for 0.1 because §11
  explicitly says the analog path needs a *specialized View*, not
  Compose. UI toolkit is irrelevant until Phase 1 lands the editor.
* **Kotlin Multiplatform from day 1.** Rejected because the §9 mandate
  is for a Rust engine if any, not Kotlin everywhere. We keep the
  option open by using pure-Kotlin in the core so a future KMP port is
  a mechanical split, not a rewrite.
* **minSdk 28 or 29.** Rejected for now. Honor Magic V2 ships with
  Android 13, so 26 covers it. Android 8.0 is also still a sizeable
  share of the cheap-Android fallback target §0 mentions.
* **AGP 8.5 instead of 8.6.** Rejected; AGP 8.6 has the most stable
  Kotlin 2.2 toolchain. We are not on the bleeding edge (AGP 8.7+ is
  out) because the lab machine's Gradle 9.3.1 is best paired with
  8.6.x.

## References

* `MASTER_ORDER.md` §6, §9, §11, §26, §31, §41.
* `MASTER_ORDER_SECTIONS.md` same sections.
* Agent memory `engineering-gotchas.md` — "Wiring Android
  Context-dependent classes into JVM-testable code" (rule for Phase 1
  Hilt wiring).
* `PHASE_0_1_FOUNDATION.md` — implementation of this ADR.
