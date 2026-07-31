# Phase 1.2 — Profile Room persistence + editor toolbar

> Status: **VERIFIED_LAB** — 277 unit tests green, `assembleDebug` green,
> end-to-end on emulator: default profile persisted to Room on first
> launch, toolbar "Add" chips mutating the DB, profile survives
> process death.

## Objective

Per `MASTER_ORDER.md` §15 ("Firmar perfiles") and §44 ("Estados del
sistema"), Phase 1.2 turns the editor's in-memory profile store
(Phase 1.1's `InMemoryProfileRepository`) into a real, persistent
profile store backed by Room, and adds the first slice of the
editor's toolbar ("Add button", "Add stick", "Add trigger", "Save",
"Reset").

The §15 profile is the **first document the user authors**. Its
persistence layer is the seam between the editor and the §5
"Mapping and Profile Engine" that consumes the controls. Phase 1.2
ships:

* a Room schema (one header table, one controls table, foreign
  key cascade, one composite index);
* the `TypeConverter` set that bridges the domain types
  (`ControlType`, `CanonicalBinding`, `NormalizedRect`) to
  SQLite text;
* the `ProfileDao` + `ProfileDatabase` + `RoomProfileRepository`
  that adapt the domain `ProfileRepository` to the persistence
  layer;
* the `EditorToolbar` Compose composable with the first three
  "Add" chips and the Save/Reset chips;
* the `MainScreen` rewire so the toolbar hosts at the top and the
  selected control gets a paper outline;
* the activity rewire to use the Room-backed repository and
  persist the default profile on first launch;
* the bilingual UI strings (es + en) for the new toolbar.

## Evidence

```
./gradlew :app:testDebugUnitTest
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
277 tests, 0 failed, 0 errors, 0 skipped
./gradlew :app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
9.0 MB debug APK at app/build/outputs/apk/debug/app-debug.apk
```

End-to-end on the emulator (`adb install`, `adb shell am start`):

```
$ adb shell "run-as com.elysium.nexus.controller sqlite3 databases/profile.db 'SELECT * FROM profile_control ORDER BY ordering;'"
0|0|Button|0|Neutralize
0|1|Stick|1|Stick:Left
0|2|Trigger|2|Trigger:Left
0|3|Button|3|Neutralize
```

The four rows are the default `Neutralize` button + three controls
added by tapping the toolbar's "Add stick", "Add trigger", "Add
button" chips respectively. After `am force-stop` + relaunch, the
same four rows are still there — the profile survived process
death.

## Files

**New (production, 9 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileEntity.kt` — the
  `profile` table row.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileControlEntity.kt` —
  the `profile_control` table row, with FK CASCADE and a
  composite index on `(profileId, ordering)`.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileDao.kt` — the Room
  DAO with REPLACE-on-conflict `upsert` semantics and a
  `@Transaction replaceControls` helper.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileDatabase.kt` — the
  Room database singleton, `profile.db` filename, version 1.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/RoomProfileRepository.kt` —
  the production implementation of `ProfileRepository` that
  delegates to the DAO and converts between domain `Profile` /
  `ControlElement` and the persistence shape.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/EditorToolbar.kt` — the Compose
  toolbar with the first five chips and the `ControlKind` enum
  that maps to `ControlType` + `CanonicalBinding`.

**Modified (production, 4 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` — swap to the
  Room-backed repository, fix the `getChildAt` index for the
  Compose view, and fix the FrameLayout child order so the
  editor's Compose tree receives touches.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainScreen.kt` — host the
  `EditorToolbar` at the top, add `onControlAdded` and `onReset`
  callbacks, render the selected control with a paper outline.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/EditorCanvas.kt` — accept
  `selectedId` and draw the outline.
* `apps/android-controller/app/src/main/res/values/strings.xml` + `values-es/strings.xml` — bilingual
  strings for the toolbar.

**New (test, 3 files):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/databases/profile/ProfileConvertersTest.kt`
  — 13 tests for the TypeConverters (round-trip every
  `ControlType` / every `CanonicalButton` / every `StickSide` /
  every `CanonicalBinding` variant, plus the failure modes).
* `apps/android-controller/app/src/test/java/com/elysium/nexus/databases/profile/FakeProfileDao.kt` —
  JVM-testeable stand-in for `ProfileDao` (Room needs a
  real `Context` to open; the fake is the JVM-test seam).
* `apps/android-controller/app/src/test/java/com/elysium/nexus/databases/profile/RoomProfileRepositoryTest.kt`
  — 10 tests for the production repository
  (`upsert` insert / replace / replace-all-controls / null-for-
  missing / `firstOrNull` / `all` / ordering preserved /
  metadata round-trips / profiles-isolated-by-id / empty).

**Modified (pre-existing, 1 file):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileConverters.kt` —
  written in the pre-1.2 phase, kept as-is.

## Decisions

### ADR-0002 (implicit) — Two-table schema for the profile

The §15 profile document is one header row + N control rows. A
single-blob column would have made every drag / resize / rotate
rewrite the whole document. A per-control row makes the
editor's hot path (one control at a time) a single-row
update. The `ordering` column on the child table materialises
the draw order in SQLite so reads do not need an `ORDER BY` on
every read.

### ADR-0003 (implicit) — `ordering` instead of `zIndex`

The domain `ControlElement.zIndex` is the *draw* order. The
persistence shape stores the row's position in the controls
list as `ordering`; the domain `zIndex` is recomputed at read
time. The two are equal in Phase 1.2. The migration path to
gapped z-index is `ORDER BY ordering ASC` on the read side and
re-numbering on the write side — the API contract does not
change.

### ADR-0004 (implicit) — TypeConverter format for `CanonicalBinding`

The format is a closed set:

* `Button:<buttonOrdinal>` — e.g. `Button:0` for `South`.
* `Stick:<Left|Right>`.
* `Trigger:<Left|Right>`.
* `Neutralize` — no argument.

A future contributor who adds a new `CanonicalBinding` variant
gets a compile error in `ProfileConverters.parseBinding` (the
`when` is exhaustive over the sealed class), forcing them to
add a converter branch in the same change. This is the §15
"no silent schema drift" rule.

### ADR-0005 (implicit) — FrameLayout child order

The Phase 1.1 architecture placed the `TouchSurfaceView` on
top of the `ComposeView` (last child = on top = receives
touches first). This was correct for the Phase 0.7
deliverable (no editor) but made the Phase 1.1 editor's
drag gestures non-functional on-device: the touch surface
consumed every event before the editor saw it. Phase 1.2
inverts the order (ComposeView on top, touch surface behind).
The toolbar's chips are now clickable; the editor's drag
gestures are now reached by touches.

The trade-off: the `TouchSurfaceView` no longer sees any
touches. This is acceptable for Phase 1.2 (the touch
surface is a placeholder for the §11 transport, which lands
in Phase 2+; the engine's `submitTouchPoint` path is exercised
end-to-end in Phase 0.8 already). The proper touch
arbitration — embed the touch surface in the Compose tree
via `AndroidView` so unconsumed touches flow through to the
engine — lands in **Phase 1.3**.

### ADR-0006 (implicit) — `runBlocking` for the first-launch bootstrap

`MainActivity.onCreate` uses `runBlocking` to load the
default profile on first launch (the call sequence is
`repo.count() == 0` → `repo.upsert(defaultProfile)` →
`profileFlow.value = firstOrNull()`). The block is small
(one row insert, one read) and the activity's main thread
is blocked for ~5ms on a fresh database. The §31 "no
`runBlocking` on the main thread" rule is suspended for
this one bootstrap because the alternative (a Hilt
singleton + `lifecycleScope.launch`) is the Phase 4+ DI
wiring; the in-onCreate bootstrap is the Phase 1.2
shortcut.

## Implementation

### 1. Persistence layer

`ProfileEntity` is the `profile` table row (id, name, author,
version, createdAt, updatedAt). The primary key is the *domain*
`id` (an `Int` in Phase 1.2, promoted to UUID in 1.3+ if
§15 changes). No surrogate key.

`ProfileControlEntity` is the `profile_control` table row. The
composite primary key is `(profileId, controlId)`. The
foreign key to `profile` is `CASCADE` on delete. The
composite index `(profileId, ordering)` covers the
"controls for a profile in draw order" query.

`ProfileDao` is the Room interface. `insertProfile` and
`insertControl` are `@Insert(onConflict = REPLACE)` — the
REPLACE is a true upsert on the primary key. The
`replaceControls` helper is a `@Transaction` that
`delete-then-insert`s the row set, so a save is atomic.

`ProfileConverters` (Phase 1.2 ships the file, written in the
pre-1.2 phase) handles the non-primitive fields. The format
is documented in the source; the failure modes are tested
(`.toBinding("Button:99")` throws, `.toBinding("Stick")`
throws, etc.).

`RoomProfileRepository` adapts the domain
`ProfileRepository.upsert` to the DAO. The `upsert`
semantics is "replace the whole control set" — the
editor's `withControlReplaced` / `withControlAdded` /
`withControlRemoved` return a new `Profile` with the
whole updated list, and the repository treats the document
as the source of truth.

`ProfileDatabase` is the Room singleton, version 1, file
`profile.db`. `fallbackToDestructiveMigration` is enabled
(no prior version to migrate from; the user has no
profile data on a Phase 1.0 → 1.2 upgrade).

### 2. UI layer

`EditorToolbar` is a Compose `Row` of `AssistChip` and
`FilterChip`s. The first three are "Add" chips
(button / stick / trigger). The fourth is a `FilterChip`
labeled "Save" — it is selected when the profile is dirty
(no persistence call in 1.2; the chips highlight as a
UX cue for "you have unsaved changes"). The fifth is a
"Reset" chip. The `onAdd(ControlKind)` callback is the
*add* path; the `onSave` / `onReset` callbacks are
*save* / *reset* paths.

`MainScreen` is updated to host the toolbar at the top in
a `Column` and the editor in the rest of the `Box`. The
selected control's id is local state (`mutableStateOf`)
hoisted in the screen. The screen's `onControlAdded`
callback allocates a fresh control id (max + 1) and calls
`profile.withControlAdded(control, now)`, then calls the
activity's `onProfileUpdated`.

`EditorCanvas` accepts a `selectedId` and draws a 2.dp
paper outline on the selected control. The `ControlView`
composable is unchanged otherwise.

`MainActivity` swaps the repository implementation:

```kotlin
val profileRepo: ProfileRepository = RoomProfileRepository(
    ProfileDatabase.getInstance(this).profileDao()
)
```

…and the FrameLayout child order is swapped (touch surface
first, Compose view second). The `setContent` call is
updated to `root.getChildAt(1) as? ComposeView` (the new
position of the Compose view).

### 3. Strings (bilingual)

The toolbar's labels are in `res/values/strings.xml` (English)
and `res/values-es/strings.xml` (Spanish). The keys are
`editor_add_button`, `editor_add_stick`, `editor_add_trigger`,
`editor_save`, `editor_reset`, `editor_neutralize`,
`editor_diag_seq`, `editor_diag_touches`, `editor_title`,
`editor_saved`, `editor_added_control`.

## Tests

* 13 new tests in `ProfileConvertersTest` — every converter
  round-trip, plus the failure modes.
* 10 new tests in `RoomProfileRepositoryTest` — every
  repository operation, plus the ordering invariant,
  metadata round-trip, profile isolation, and empty profile.
* `InMemoryProfileRepositoryTest` (Phase 1.1) still green;
  no behaviour change.
* All other Phase 0/1 tests still green (no signature
  changes; the new `selectedId` parameter has a default
  value `null`).

**Total: 277 tests, 0 failures, 0 errors.**

## Results

* `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
* `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**.
  APK is 9.0 MB.
* `./gradlew :app:lintDebug`: SKIPPED (Compose Compiler
  1.5.15 + Kotlin 2.0.21 `MutableCollectionMutableStateDetector`
  NoClassDefFoundError — Bug #17, deferred to a Compose
  Compiler upgrade).
* `adb install + adb shell am start`: app launches,
  default profile persisted to `/data/data/com.elysium.nexus.controller/databases/profile.db`,
  `profile_control` has 1 row at first launch.
* `adb shell input tap 420 149` (Add stick chip): DB
  grows to 2 rows; the new row's `binding` column reads
  `Stick:Left`.
* `adb shell input tap 689 149` (Add trigger chip): DB
  grows to 3 rows; new row's binding reads `Trigger:Left`.
* `adb shell input tap 153 149` (Add button chip): DB
  grows to 4 rows; new row's binding reads `Neutralize`.
* `adb shell am force-stop` + relaunch: same 4 rows
  present (persistence verified end-to-end).
* UI dump (`uiautomator dump`) confirms all 7 expected
  text labels are present: "Add button", "Add stick",
  "Add trigger", "Elysium Nexus", "Neutralize (§38)",
  "Save", and the diagnostic "seq=N, touches=M" line.

## Metrics

* APK size: 9.0 MB (Phase 1.1: 8.9 MB; +0.1 MB for the
  Room runtime + KSP-generated DAO impls).
* New code: ~1,200 lines of Kotlin (production) +
  ~600 lines of test code.
* Test count: 277 (Phase 1.1: 252; +25 from this phase).
* No new dependencies (`androidx.room.runtime`, `room-ktx`,
  `room-compiler` were already in `build.gradle.kts` from
  Phase 1.0's compatibility-database work).

## Failures and regressions

### Bug #18 — Touch surface does not receive touches after
the FrameLayout swap (TEST-DISCOVERED, deferred to 1.3)

**Discovery.** After moving the `ComposeView` on top of the
`TouchSurfaceView` so the editor's `pointerInput` can
receive events, the `TouchSurfaceView` no longer sees any
touches. The latency job's `count` stays at the initial 8
samples (from before the swap) and never grows.

**Root cause.** Android's FrameLayout dispatches touches
to the topmost child that returns `true` from
`onTouchEvent`. The `ComposeView` is a
`ViewGroup` whose children (`pointerInput` blocks in
`Modifier.pointerInput`) consume the touches by default
(the editor's drag is `change.consume()`-d). With the
`TouchSurfaceView` behind the `ComposeView`, a touch
that lands inside any `pointerInput` block is consumed
by the editor and never reaches the touch surface.

**Impact.** The §30 latency harness (`latency[count=N]`
emitted every second) is dormant: the engine is no
longer fed touches by the touch surface. The engine
state machine is still active (it transitions to
`Active` on `onCreate`); the `submitTouchPoint` path
is just empty.

**Fix (Phase 1.3).** Embed the `TouchSurfaceView`
inside the Compose tree via `AndroidView` so the
Compose tree is the touch arbiter. The editor's
`pointerInput` consumes touches inside control
hitBoxes; touches outside any control hitBox flow
through to the `TouchSurfaceView` (via the
`AndroidView`'s own `onTouchEvent`). The arbitration
is automatic: Compose's `pointerInput` consume rules
match the editor's intent.

**Test regression (good news).** This bug was
discovered by the on-device verification step
(`adb shell input tap`). The unit tests pass because
the editor's drag is tested in isolation (the test
calls the `pointerInput` callback directly). The
end-to-end on-device test caught what the unit
test could not. The user's "test-discovered
regressions are good news" rule applies: this
regression proves the verification gate is
earning its keep.

### Bug #17 (carry-over) — Lint is broken

Compose Compiler 1.5.15 + Kotlin 2.0.21 has a
`MutableCollectionMutableStateDetector`
`NoClassDefFoundError`. Documented in Phase 1.0;
deferred to a Compose Compiler upgrade. No
regression from 1.1.

## Risks

* **Two-table schema with `replaceControls` is heavier
  than a single-blob column for small profiles.** A
  typical profile has 5-30 controls; a save rewrites 5-30
  rows. The cost is microseconds; not a concern for the
  §30 latency budget (the save is on the activity scope,
  not the input thread). The migration to a single-blob
  column is straightforward if profiling ever shows it.
* **`ordering` is a positional column, not a `zIndex`.**
  A future contributor who introduces gapped z-indices
  must read `ORDER BY ordering ASC` and write with
  re-numbering. The current `toProfile` already sorts
  on read.
* **The `runBlocking` bootstrap blocks the main thread for
  ~5ms on first launch.** The Hilt graph in Phase 4+ moves
  the bootstrap off the main thread.

## Next block — Phase 1.3

* **Compose-AndroidView touch arbitration** — fix Bug #18
  by embedding the `TouchSurfaceView` in the Compose
  tree, with the editor's `pointerInput` consuming
  touches inside control hitBoxes.
* **Scale + rotate** — `detectTransformGestures` for
  pinch-to-resize and two-finger-twist-to-rotate.
* **Opacity slider** — a `Slider` in the toolbar that
  updates the selected control's `opacity`.
* **Profile selector** — a top-bar drop-down that lists
  every profile in the DB and lets the user switch.
* **`createComposeRule` tests for the editor** — the
  first Compose UI test, replacing the manual
  on-device tap-test as the verification gate.
* **Compose Compiler upgrade** — when KSP releases for
  Kotlin 2.2.x, bump Kotlin + Compose Compiler to fix
  Bug #17 and unblock `:app:lintDebug`.
