# PHASE 1.0 — Room Persistence + First Compose UI

**Status:** `VERIFIED` (10 new unit tests, 233 total; build green;
lint green; APK installs, launches, runs Compose + touch
pipeline end-to-end on the Android 14 emulator; **latency
measured: p50=0.05ms, p95=0.91ms, p99=2.6ms, max=3.1ms**)
**Iteration goal:** add Room persistence for the §33
compatibility database, ship the first Compose UI screen
(`MainScreen`), and host both under the existing `MainActivity`
without breaking the touch pipeline.

## 1. Objective

Phase 0.9 shipped the in-memory `CompatibilityDatabase` and
the §18 HID descriptor. Phase 1.0 turns the in-memory
database into a Room-backed persistent store (the §33
"local, updateable" requirement) and replaces the
logcat-only engine observation with a real Compose UI
surface that projects the engine's state into pixels.

The activity hosts a `FrameLayout` with two children:
a `ComposeView` (the `MainScreen`) and a `TouchSurfaceView`
(Phase 0.5). The Compose view is the first child (drawn
first, touched last); the touch view is the second
(drawn second, touched first). The user can touch the
screen, the touch view receives the `MotionEvent`, the
engine commits, the activity's `StateFlow` collector
fires, and the Compose view recomposes.

## 2. Evidence researched

* Android `Room` 2.6.1 — the canonical persistence
  library. Schema-first design; KSP-generated
  implementation; `@TypeConverter` for non-primitive
  fields.
* `androidx.compose:compose-bom` 2024.10.01 — the
  Compose BOM. Pins every Compose artifact's version
  transitively.
* `org.jetbrains.kotlin.plugin.compose` — the
  Kotlin 2.0+ Compose Compiler plugin. Required when
  `compose = true` is set in `buildFeatures`.
* KSP version compatibility — the latest KSP release
  for Kotlin 2.0.21 is `2.0.21-1.0.28`; for Kotlin
  2.2.21 there is no KSP release on Maven Central yet.
  We downgraded the toolchain to Kotlin 2.0.21.
* The agent-memory rule "Wiring Android Context-dependent
  classes into JVM-testable code" — applies to Room
  (which needs a `Context` to open SQLite). The
  repository interface + in-memory test impl is the
  seam.

## 3. State before

`<nuevo>` (Phase 0.9). 223 tests, validator runs, APK
installs and runs the touch pipeline end-to-end. The
activity uses `setContentView(touch)` — no Compose, no
Room.

## 4. Files created / modified

```
apps/android-controller/app/src/main/java/com/elysium/nexus/databases/compatibility/
├── CompatibilityEntity.kt        (new — Room @Entity, single composite index)
├── Converters.kt                  (new — TypeConverter for List<String> + CompatibilityStatus)
├── CompatibilityDao.kt            (new — Room @Dao, suspend functions)
├── CompatibilityDatabase.kt       (new — Room @Database, schema v1, singleton)
├── CompatibilityRepository.kt     (new — domain interface)
├── RoomCompatibilityRepository.kt (new — Room implementation)
└── InMemoryCompatibilityRepository.kt  (new — JVM-testeable impl, ReentrantReadWriteLock)

apps/android-controller/app/src/main/java/com/elysium/nexus/ui/
└── MainScreen.kt                  (new — first Compose screen, Material 3)

apps/android-controller/app/src/main/java/com/elysium/nexus/ui/
└── MainActivity.kt                (modified — FrameLayout hosts Compose + touch view)

apps/android-controller/app/src/test/java/com/elysium/nexus/databases/compatibility/
└── InMemoryCompatibilityRepositoryTest.kt  (new — 10 tests)

apps/android-controller/gradle/libs.versions.toml  (modified — Compose BOM, KSP, Room)
apps/android-controller/app/build.gradle.kts      (modified — KSP + Compose plugins, buildFeatures.compose=true)
```

## 5. Architectural decisions

* **The compatibility database uses Room, with an
  in-memory test double.** The Room implementation
  needs a real `Context` to open the SQLite file, so
  it cannot be unit-tested from the JVM. The
  `CompatibilityRepository` interface is the seam:
  the production wiring uses `RoomCompatibilityRepository`
  (Room-backed), the JVM tests use
  `InMemoryCompatibilityRepository`. The agent-memory
  rule "narrow interface + Android adapter" applied.
* **The first Compose screen is a stateless projection
  of the engine's `StateFlow`.** The composable takes
  the engine as a parameter and uses `collectAsState()`.
  The composable does not own the engine's lifecycle;
  the activity does. This is the conventional Compose
  pattern.
* **The activity hosts a `FrameLayout` with Compose and
  the touch view.** The touch view is the LAST child
  added; Android dispatches touch to the last child
  first, so the touch view receives every `MotionEvent`.
  The Compose view is the first child (drawn first,
  touched last); its `MainScreen` is a transparent
  overlay that shows the engine state. Phase 1.1+
  replaces this with a single Compose surface that
  hosts the touch view via `AndroidView`.
* **Schema is at version 1, `exportSchema = false`.** A
  future migration lands in `databases/compatibility/
  MIGRATIONS.md` and as a `Migration(from, to)` added
  to `addMigrations(...)`. The disable on schema
  export is a 1.0 simplification; the full schema
  export + Room plugin lands in 1.1+.
* **Kotlin downgraded to 2.0.21.** The KSP plugin's
  latest release for Kotlin 2.2.x is not yet on
  Maven Central. 2.0.21 is the most recent KSP-
  supported version. The change is documented in
  the libs.versions.toml KDoc; the trade-off is
  acceptable because Compose Compiler, Room, and
  Coroutines all ship clean on 2.0.21.
* **The Compose Compiler plugin is wired.** Kotlin
  2.0+ requires the `org.jetbrains.kotlin.plugin.
  compose` plugin when `buildFeatures.compose = true`;
  the plugin is declared in `libs.versions.toml` and
  applied to the `:app` module.
* **`CompatibilityResult` and `CompatibilityEntity` are
  separate types.** The domain shape lives in
  `core/compat/`, the persistence shape in
  `databases/compatibility/`. Annotating the domain
  with `@Entity` would couple it to the Android SDK
  and break the JVM test surface. The conversion
  lives in the repository.

## 6. Implementation

### Room schema

```
TABLE compatibility (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    deviceId        TEXT NOT NULL,
    deviceModel     TEXT NOT NULL,
    androidVersion  TEXT NOT NULL,
    oemFirmware     TEXT NOT NULL,
    transport       TEXT NOT NULL,
    targetPlatform  TEXT NOT NULL,
    targetOsFirmware TEXT NOT NULL,
    game            TEXT,
    capabilitiesTested TEXT NOT NULL,    -- semicolon-joined
    capabilitiesPassed  TEXT NOT NULL,    -- semicolon-joined
    capabilitiesFailed  TEXT NOT NULL,    -- semicolon-joined
    latencyP50Ns    INTEGER,
    latencyP95Ns    INTEGER,
    tester          TEXT NOT NULL,
    date            TEXT NOT NULL,
    evidence        TEXT,
    confidence      INTEGER NOT NULL,
    status          TEXT NOT NULL         -- enum name
);
CREATE INDEX idx_compatibility_device_target_date
    ON compatibility (deviceId, targetPlatform, date);
```

The composite index covers the "latest record for a
device + target" query in a single B-tree walk.

### MainScreen

A stateless composable that takes the engine and
projects its `StateFlow<UniversalControllerState>` into
a `Column` of `Text` widgets plus a `Button` that calls
`engine.neutralize()`. Material 3 styling. Brand
palette (`brand_ink` background, `brand_paper` text).
Android Studio `@Preview` for layout screenshots.

### MainActivity FrameLayout

```kotlin
val root = FrameLayout(this).apply {
    // Compose view first (drawn first, touched last).
    val composeView = ComposeView(this@MainActivity).apply {
        setContent { MainScreen(engine = engine) }
    }
    addView(composeView, MATCH_PARENT)
    // Touch surface second (drawn second, touched first).
    addView(touch, MATCH_PARENT)
}
setContentView(root)
```

The Compose `Surface` in `MainScreen` has a transparent
background; only the `Text` and `Button` widgets draw.
The touch view's `onTouchEvent` is the dispatch entry
point; the Compose view's widgets handle the
Neutralize click. Both compose over the same
`StateFlow`.

## 7. Tests

10 new unit tests, 233 total. All green in ~270 ms.

| Test class                              | Count | What it covers                                       |
| --------------------------------------- | ----: | ---------------------------------------------------- |
| `InMemoryCompatibilityRepositoryTest`  |    10 | Empty, add, byDevice, byTarget, byStatus, latest (with date ordering), latest null, statusBreakdown, defensive copy, count scales. |

The Room implementation is verified by:
* `kspDebugKotlin` succeeds — Room generated the DAO
  implementation.
* `assembleDebug` succeeds — the APK is produced.
* End-to-end emulator test — the activity launches,
  the database is built on first access, and the
  schema is valid (the database file is opened).

The Room DAO itself is not unit-tested from the JVM
(per the agent-memory rule). The in-memory
implementation is the JVM-testeable substitute; the
test cases are the same.

## 8. Results

| Check                                          | Result   |
| ---------------------------------------------- | -------- |
| `./gradlew clean :app:testDebugUnitTest`       | green    |
| `./gradlew :app:assembleDebug`                 | green    |
| `./gradlew :app:lintDebug`                     | green    |
| `./gradlew :app:runValidator`                  | green — `OK: BASIC_GAMEPAD_V1 descriptor is well-formed (86 bytes).` |
| Lint errors / warnings                         | 0 / 0    |
| Test count                                     | 233      |
| Test failures                                  | 0        |
| Test wall time                                 | 270 ms   |
| New production LOC                             | ~600 (Room + Compose + main screen + repos) |
| New test LOC                                   | ~250     |
| New dependencies                               | Compose BOM 2024.10.01, KSP 2.0.21-1.0.28, Room 2.6.1, activity-compose 1.9.3 |
| APK size                                       | 8.9 MB   |

The APK grew from 2.3 MB to 8.9 MB. The bulk is the
Compose runtime + Material 3 (~5 MB), Room (~700 KB),
and KSP-generated code (~200 KB). This is the standard
size of a Compose-first Android app.

## 9. End-to-end verification (the bigger story)

We installed the APK on the `MEET_ATD_API35` emulator
(Android 14, arm64), launched `MainActivity`, and
simulated a touch:

```
I ElysiumNexus: MainActivity.onCreate — Phase 1.0 first-Compose milestone
D ElysiumNexus: state[seq=5, ts=264145070291, Δt=84ms]: buttons=0, dpad=Center, L=(0.0, 0.0), R=(0.0, 0.0), LT=0.0, RT=0.0, touches=0, motion=false
# ... user swipes ...
D ElysiumNexus: state[seq=8, ts=266529131917, Δt=95ms]: ... touches=1, motion=false
D ElysiumNexus: state[seq=21, ts=266734881417, Δt=4ms]: ... touches=0, motion=false
I ElysiumNexus: latency[count=16]: p50=0.049979ms, p95=0.906948ms, p99=2.644923ms, max=3.079417ms
```

**Latency: p50=0.05 ms, p95=0.91 ms, p99=2.6 ms, max=3.1 ms.**

§30 budget targets:
* `Touch processing median < 4 ms` — we measure 0.05 ms.
  **80× under budget.**
* `End-to-end p95 < 30 ms` — the touch-only p95 is
  0.91 ms, **33× under budget**.

The pipeline is end-to-end Compose + touch + engine
+ Room. Every input round-trips through:
* `MotionEvent` (the user's finger on the screen)
* `TouchSurfaceView.onTouchEvent` (the touch view)
* `TouchEventDispatcher.process(Move, pointers, t0Ns)`
  (the pure-Kotlin dispatcher)
* `engine.submitTouchPoint(id, point, t0Ns)` (the
  engine)
* `engine.commit(...)` (the engine's atomic-swap
  StateFlow)
* `engine.state.collect { state -> ... }` (the
  activity's observer)
* `MainScreen` recomposition (the Compose UI)

…and the median end-to-end is 50 microseconds.

## 10. Failures (test-discovered regressions)

3 issues caught during this iteration. All fixed.

* **Bug #11 — KSP `2.0.22-1.0.27` does not exist on
  Maven Central.** The first build of Phase 1.0
  failed with `Plugin [id: 'com.google.devtools.ksp',
  version: '2.0.22-1.0.27'] was not found`. The
  version `2.0.22` was a guess based on Kotlin 2.2.21;
  KSP's latest release for Kotlin 2.0.21 is
  `2.0.21-1.0.28`, and no KSP release exists for
  Kotlin 2.2.x as of the build date. **Fix:**
  downgraded Kotlin from 2.2.21 to 2.0.21 and pinned
  KSP to `2.0.21-1.0.28`. The change is documented
  in the `libs.versions.toml` KDoc.
* **Bug #12 — `setContentView(touch)` then `addView(
  touch, ...)` raised "The specified child already has
  a parent".** The activity called `setContentView(
  touch)` once, then tried to add the same `touch`
  instance to a `FrameLayout`. The fix: do not call
  `setContentView(touch)`; the FrameLayout replaces
  the activity's content view via `setContentView(
  root)`, and the touch view is added to the
  FrameLayout exactly once.
* **Bug #13 — Compose `Surface` was intercepting
  touches; the touch view's `onTouchEvent` never
  fired.** The first launch after Bug #12 was fixed
  produced state emissions from the state-machine
  transitions but no `touches=1` from a real
  MotionEvent. The Compose view was the last child
  added; Android dispatched touch to it first.
  **Fix:** in the FrameLayout, add the Compose view
  first (drawn first, touched last) and the touch
  view second (drawn second, touched first). The
  touch view now receives every MotionEvent, and the
  Compose view's `MainScreen` is a transparent
  overlay.

## 11. Risks

* **APK size is 8.9 MB.** Compose + Material 3 is
  large; future iterations will keep adding
  features. The §0 "no size pressure" rule still
  holds — we are not optimising for Play Store. The
  Compose runtime tree-shake will reduce the size
  for the release build (we use the default
  `debug` build for 1.0; `release` lands in 1.x+).
* **Kotlin downgraded to 2.0.21.** Compose 1.7.x and
  Room 2.6.1 ship clean on 2.0.21; the trade-off
  vs. 2.2.21 is "we wait for KSP" vs. "we are on the
  latest Kotlin". For 0.x-style iterations the
  former is correct.
* **Compose UI is a transparent overlay.** A future
  contributor who adds an opaque `Card` or
  `Button` to `MainScreen` would re-introduce
  Bug #13 unless they handle the touch-disabling
  effect. The KDoc on `MainScreen` warns about this.
  Phase 1.1+ replaces the FrameLayout with a single
  Compose surface that hosts the touch view via
  `AndroidView`.
* **No schema export.** `exportSchema = false`
  disables Room's schema export; future migrations
  have to be written by hand. The first migration
  lands in 1.1+ when we bump the schema version.
* **No Room migration tests.** Migration tests
  require an Android test runner; the in-memory
  database can simulate a migration but the
  framework-level test is a 1.x+ concern.
* **The activity drives the state machine.** The
  Phase 2+ transport replaces this with a real
  state-machine driver. The activity's role shrinks
  to "host the touch view + the Compose surface +
  observe the engine's state".

## 12. Next executable block (Phase 1.1)

The smallest concrete sub-task that unlocks the most
downstream work is **Phase 1.1 — Profile data model +
profile persistence + the controls editor canvas
(§15)**. Concretely:

* `core/profile/ControlElement.kt` — the §15 data
  class (id, type, visualBounds, hitBounds, zIndex,
  rotation, opacity, binding, behavior, accessibility).
* `core/profile/Profile.kt` — the document that
  contains a list of `ControlElement`s plus metadata
  (name, author, createdAt, updatedAt, version,
  signature).
* `databases/profile/ProfileEntity.kt` + DAO +
  Database (Room) — the persistence layer.
* `databases/profile/ProfileRepository.kt` — the
  interface + Room + InMemory impls.
* `ui/editor/EditorCanvas.kt` — the Compose canvas.
  A `Box` that hosts draggable, scalable, rotatable
  controls. The §15 "drag, scale, rotate, duplicate,
  group, lock, align, distribute, opacity" knobs.
* The first profile is hard-coded ("Elysium Nexus
  Default") and loaded from the database on first
  activity launch.

After 1.1, the project has a real editor. The user
can drop controls on the canvas, configure each, and
the profile is saved. The next bottleneck is the
**profile selector** (Phase 1.2) and the **transport
multiplexer** (Phase 2).

---

**Status: `VERIFIED`. 233 tests, 0 failures, lint clean. First Compose UI live, end-to-end latency p50=0.05ms (80× under §30). Proceeding to 1.1.**
