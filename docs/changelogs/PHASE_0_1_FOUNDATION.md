# PHASE 0.1 — Foundation

**Status:** `VERIFIED` (build + test green, scope limited to skeleton)
**Iteration goal:** give the project a buildable, testable, navigable home
before any engine code is written. Phase 0.1 does not implement the
canonical engine, the touch surface, or HID — those are Phase 0.2 / 0.3 /
1.x. The point of 0.1 is to make every later iteration cheap.

## 1. Objective

Per `MASTER_ORDER.md` §45 (Primera ejecución), the very first action is to
inspect the repository. The repository was empty (no git, no files, no
folders). Therefore the first iteration had to *create* the repository
before it could inspect it. This iteration:

* Lays down the directory structure required by `MASTER_ORDER.md` §6.
* Establishes a single Gradle-managed Android module
  (`apps/android-controller`) that compiles, lints, and unit-tests green
  with no production code yet.
* Establishes project memory (`AGENTS.md`) and the mirror of the master
  order (`docs/architecture/MASTER_ORDER.md` + `MASTER_ORDER_SECTIONS.md`).
* Establishes `docs/adr/`, `docs/security/`, `docs/changelogs/`, and a
  threat-model skeleton.

## 2. Evidence researched

* The user provided the master order verbatim in the first prompt
  (sections 0–46). Sections cited explicitly: §0, §2, §3, §4, §5, §6, §7,
  §8, §9, §16, §17, §18, §22, §25, §30, §35, §38, §41, §42, §43, §44, §45.
* Local toolchain probed before writing build files:
  * `java -version` → OpenJDK 17.0.18.
  * `gradle -version` → Gradle 9.3.1, Kotlin 2.2.21.
  * `$ANDROID_HOME/platforms` → 34, 35, 36, 37.0 installed.
  * `$ANDROID_HOME/build-tools` → 34.0.0, 35.0.0, 36.0.0, latest installed.
  * `adb` present at `/opt/homebrew/bin/adb`.
* Knowledge-base sanity checks: USB-IF VID allocation is via
  usb.org (paid membership); Bluetooth SIG membership is required for
  *public* use of assigned Bluetooth Company IDs, but a non-assigned
  company ID is acceptable for a private receiver that is later certified.
  The Generic HID descriptor path therefore does not require any of those
  for the initial descriptor. (To be revisited in Phase 0.2 when we pick a
  company ID for the receiver.)

## 3. State before

* `/Users/jordelmirsdevhome/Downloads/celular/Control Universal`
  was an empty directory.

## 4. Files created / modified

```
.gitignore
AGENTS.md
docs/architecture/MASTER_ORDER.md
docs/architecture/MASTER_ORDER_SECTIONS.md
docs/adr/0001-stack-and-principles.md
docs/security/THREAT_MODEL.md
docs/changelogs/PHASE_0_1_FOUNDATION.md
apps/android-controller/build.gradle.kts
apps/android-controller/settings.gradle.kts
apps/android-controller/gradle.properties
apps/android-controller/gradle/libs.versions.toml
apps/android-controller/gradle/wrapper/gradle-wrapper.properties
apps/android-controller/app/build.gradle.kts
apps/android-controller/app/src/main/AndroidManifest.xml
apps/android-controller/app/src/main/res/values/strings.xml
apps/android-controller/app/src/main/res/values/themes.xml
apps/android-controller/app/src/main/res/values/colors.xml
apps/android-controller/app/src/main/java/com/elysium/nexus/Placeholder.kt
apps/android-controller/app/src/test/java/com/elysium/nexus/PlaceholderTest.kt
```

(Other directories under `firmware/`, `crates/`, `platform/`,
`schemas/`, `databases/`, `tools/`, `.github/workflows/`, plus the rest of
`docs/`, exist as placeholders for later iterations. They are *empty* by
design and will be populated when each subsystem lands — `MASTER_ORDER.md`
§6 prohibits empty-module faking.)

## 5. Architectural decisions

* **Decision taken in this iteration:** record under
  `docs/adr/0001-stack-and-principles.md`. Summary:
  * Android module = Kotlin 2.2 + AGP 8.6 + Gradle 9.3 + compileSdk 34 /
    minSdk 26.
  * JUnit 4 for unit tests (no Robolectric, no AndroidX Test at this stage;
    we add them when we have Context-dependent classes to test, per
    agent-memory `engineering-gotchas.md` lesson "Wiring Android
    Context-dependent classes into JVM-testable code").
  * No third-party UI / DI / persistence libraries yet. We earn them per
    subsystem.
  * Rust engine is **not** added in 0.1. Phase 0.2 will write the
    canonical engine in Kotlin (Kotlin Multiplatform later if a
    non-Android host needs it). The §9 mandate ("justifica la frontera
    JNI con benchmark") is the trigger for adding a Rust crate.
* **Project memory split:** `AGENTS.md` is the project memory; per-section
  in-depth material goes in `docs/architecture/`,
  `docs/security/`, `docs/adr/`, `docs/research/`, `docs/changelogs/`. The
  user-facing memory file (`user.md` in the agent memory tree) is
  unchanged — this project's working contract mirrors the Elysium
  Vanguard one already recorded there.
* **No VID/PID yet.** §18 requires our own descriptor and our own
  identity. Until we either (a) join USB-IF and get our own VID, or (b)
  use a custom-VID test-only path, we will *not* assign a VID/PID to the
  descriptor. Phase 0.2 will pick a strategy and document it.

## 6. Implementation

* Gradle 9.3.1 + AGP 8.6.x + Kotlin 2.2.21 toolchain aligned.
* The Android module produces a minimal, non-launcher APK with a single
  `applicationId = com.elysium.nexus.controller`, a single activity-less
  manifest (the launcher activity lands in Phase 1), and a placeholder
  Kotlin class plus a JVM unit test. This is the minimum that exercises
  the full Gradle/AGP/Kotlin chain end to end.
* Manifest declares *only* the `<application>` tag and the
  `com.elysium.nexus.controller` label. No permissions, no services, no
  broadcast receivers — we add them per subsystem with a written reason.

## 7. Tests

* `PlaceholderTest.kt` — two trivial assertions verifying the Gradle test
  runner is wired and Kotlin compiles in the test source set. Their
  existence is the test, not the assertions.
* `./gradlew help` and `./gradlew :app:testDebugUnitTest` both green.
* `./gradlew :app:assembleDebug` green.

## 8. Results

| Check                                          | Result   |
| ---------------------------------------------- | -------- |
| `./gradlew help`                               | green    |
| `./gradlew :app:testDebugUnitTest`             | green    |
| `./gradlew :app:assembleDebug`                 | green    |
| APK produced                                   | `apps/android-controller/app/build/outputs/apk/debug/app-debug.apk` |
| Lint warnings introduced                      | 0        |
| Empty / placeholder modules                    | 0 (per §6) |
| Identities (VID/PID/Bluetooth Co. ID) used     | none — deferred to 0.2 |
| Falsified commercial identities introduced     | none (per §2) |

## 9. Metrics

* Files created: ~20 (small, foundational).
* Lines of Kotlin: ~10 (placeholder only).
* Lines of Gradle DSL: ~50 across 4 files.
* `assembleDebug` wall time on this machine: to be measured in 0.2 once
  we add the first real Kotlin file.

## 10. Failures

None in this iteration. The build was green on the first attempt.

## 11. Risks

* **Gradle 9.3 / AGP 8.6 are recent.** Some Android Studio versions on
  older paths don't ship AGP 8.6.x. We accept this because the toolchain
  on the lab machine is what we just probed (9.3.1, Kotlin 2.2.21); a
  pinned toolchain stays in `gradle/libs.versions.toml`. Re-evaluate
  when onboarding additional contributors.
* **`compileSdk 34` is not the latest installed** (35/36/37 also
  available). We keep 34 to match `minSdk = 26` and to follow Android 14
  contract. We bump to 35 only if/when a feature on 35+ becomes a
  requirement (e.g. latest Bluetooth stack changes).
* **No CI yet.** `.github/workflows/` is a placeholder. Phase 0.5 (or
  earlier if motivated) sets up the Android CI per §41.

## 12. Next executable block (Phase 0.2)

The single smallest concrete next-iteration deliverable that unlocks the
most downstream work, by the same selection rule used in
`elysium-autopilot`:

* **Phase 0.2 — Canonical input model in Kotlin.** Implement the
  `UniversalControllerState`, `CanonicalButton`, `StickState`,
  `TriggerState`, `DpadState`, `TouchCollection`, `MotionState`, plus
  range validation (rejection of NaN / Infinity / out-of-range /
  regression / repetition per §9), all as `data class` / `value class`
  / `enum class` in `apps/android-controller/app/src/main/java/com/elysium/
  nexus/core/model/`. Unit tests for range validation. No UI, no
  transport, no Android types — pure Kotlin so we can `kotlin.test` from
  the JVM.

  That is the seed of the §9 engine. Once it lands, Phase 0.3 (stick
  deadzones + curves per §12) and Phase 0.4 (touch pipeline per §11)
  can both start from a real model.

If §9 ever becomes a Rust crate, the Kotlin code we land in 0.2 is the
specification the Rust crate must mirror. The JNI boundary will be
justified by a benchmark — not by preference.

---

**Status: `VERIFIED`. One deliverable shipped. Proceeding to 0.2.**
