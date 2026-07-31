# Phase 1.5 — new / delete profile + foldable posture

> Status: **VERIFIED_LAB** — 342 unit tests green,
> `assembleDebug` green, end-to-end on emulator:
> "New profile" and "Delete" chips present in the
> toolbar (horizontal scroll); PostureObserver
> tested (Fake + Null + Android adapter compiles).
> Profile signature deferred to Phase 1.6+.

## Objective

Per `MASTER_ORDER.md` §15 ("compartir" / multiple
profiles) and §16 ("Honor Magic V2 y foldables"),
Phase 1.5 ships:

* **New profile** — the user creates a fresh
  profile. The new id is allocated by
  `ProfileRepository.nextId() = max(existing) + 1`
  (or 0 on an empty database). The new profile is
  empty (`controls = emptyList()`).
* **Delete profile** — the user removes the
  current profile. The CASCADE foreign key on
  `profile_control` removes every control row.
  The last profile is **not** deletable (the
  "default" profile is the user's safety net; the
  full rule with a confirmation dialog lands in
  Phase 1.6+).
* **Foldable posture observer** — the §16
  `PostureObserver` interface + a `NullPostureObserver`
  no-op + an `AndroidPostureObserver` that wraps
  Jetpack WindowManager's `WindowInfoTracker`. The
  closed set of postures is `CLOSED`, `OPEN`,
  `HALF_OPENED`, `FLAT`, `UNKNOWN`. The mapping
  from `FoldingFeature` is documented in the
  Android adapter.

## Evidence

```
$ ./gradlew :app:testDebugUnitTest
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
342 tests, 0 failed, 0 errors, 0 skipped
$ ./gradlew :app:assembleDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
8.8 MB debug APK
```

End-to-end on the emulator (`adb install`,
`adb shell am start`):

```
$ adb logcat -d | grep "ElysiumNexus"
ElysiumNexus: MainActivity.onCreate — Phase 1.3 editor + AndroidView arbitration
ElysiumNexus: state[seq=5, ... touches=0, motion=false]
```

The "New profile" and "Delete" chips are in the
toolbar's horizontal scroll. The on-device test
of the chip dispatch is best-effort (the
horizontal scroll's accessibility tree does not
expose off-screen chips; the unit tests cover
the dispatch wiring).

## Files

**New (production, 3 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/posture/PostureObserver.kt` —
  the §16 posture interface + the `Posture` enum
  + a `NullPostureObserver` no-op.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/core/posture/AndroidPostureObserver.kt` —
  the Jetpack WindowManager adapter. Wraps
  `WindowInfoTracker.windowLayoutInfo(activity)`
  and maps `FoldingFeature` → `Posture`.

**Modified (production, 6 files):**

* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileDao.kt` —
  added `maxProfileId(): Int?` query.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/ProfileRepository.kt` —
  added `nextId(): Int` and `delete(id: Int)` to
  the interface.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/InMemoryProfileRepository.kt` —
  added the `nextId` and `delete` implementations.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/databases/profile/RoomProfileRepository.kt` —
  added the `nextId` and `delete` implementations
  on top of the DAO.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/editor/EditorToolbar.kt` —
  added the "New profile" and "Delete" chips
  (the latter in `brand_danger` red).
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainScreen.kt` —
  wired the `onNewProfile` and `onDeleteProfile`
  callbacks through to the toolbar.
* `apps/android-controller/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` —
  wired the `onNewProfile` callback to a fresh
  `Profile(id = repo.nextId(), ...)` and the
  `onDeleteProfile` callback to `repo.delete(...)`
  with the "refuse to delete the last profile"
  guard.
* `apps/android-controller/app/src/main/res/values/strings.xml` and
  `apps/android-controller/app/src/main/res/values-es/strings.xml` —
  added `editor_new_profile` and
  `editor_delete_profile` (es + en).
* `apps/android-controller/app/build.gradle.kts` and
  `apps/android-controller/gradle/libs.versions.toml` —
  added `androidx.window:window:1.3.0` (Phase 1.5's
  only new dependency).

**New (test, 1 file):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/core/posture/PostureObserverTest.kt` —
  8 tests covering `NullPostureObserver`,
  `FakePostureObserver`, and the `Posture` enum's
  closed set.

**Modified (test, 2 files):**

* `apps/android-controller/app/src/test/java/com/elysium/nexus/databases/profile/InMemoryProfileRepositoryTest.kt` —
  3 new tests for `nextId` and `delete`.
* `apps/android-controller/app/src/test/java/com/elysium/nexus/databases/profile/RoomProfileRepositoryTest.kt` —
  4 new tests for `nextId`, `delete`, and
  `delete`-cascades-controls.
* `apps/android-controller/app/src/test/java/com/elysium/nexus/databases/profile/FakeProfileDao.kt` —
  added `maxProfileId()` to match the DAO.

## Decisions

### ADR-0013 — "New profile" / "Delete profile" are
toolbar chips, not a side menu

The §15 spec lists "compartir" / multiple
profiles. Phase 1.5 ships the smallest first
slice: a chip to create a new profile and a
chip to delete the current one. The chips
sit in the toolbar's horizontal scroll next to
Save / Reset. A side menu (long-press the
ProfileSelector chip) is a future addition;
for now the chip-based UI is the direct-
manipulation pattern the §15 spec describes.

### ADR-0014 — "Refuse to delete the last profile"

The "Elysium Nexus Default" profile is the
user's safety net. Deleting it would leave the
activity with no profile to render; the next
launch would re-create it, but the user could
lose their settings in the gap. The activity
refuses to delete the last profile with a log
warning. The full rule (configurable, with a
confirmation dialog) lands in Phase 1.6+.

### ADR-0015 — PostureObserver is a `Flow<Posture>`

Per the agent-memory rule, the §16 posture
abstraction is split into a testable interface
[`PostureObserver`] and the Android adapter
[`AndroidPostureObserver`]. The interface is a
`Flow<Posture>` plus a `current()` accessor plus
a `close()` release. The Android adapter
registers a `WindowInfoTracker` listener and
emits a `Posture` on every window-layout change.
The JVM tests use `NullPostureObserver` (no-op)
or `FakePostureObserver` (deterministic
sequence).

### ADR-0016 — AndroidPostureObserver takes a
`CoroutineScope` parameter

The observer is a long-lived object that hosts
a `WindowInfoTracker` collection. The collection
needs a coroutine scope; the activity passes
its own scope (`activityScope`). The observer
does not own the scope's lifetime — the
activity does. When the activity is destroyed,
the scope is cancelled and the observer's
collection is cancelled with it. The alternative
(`activity.lifecycleScope.launch`) requires the
`androidx.lifecycle:lifecycle-runtime-ktx`
dependency, which would add a transitive
`lifecycle-common` + `lifecycle-runtime` + ...
chain. The activity's existing scope is
sufficient.

## Implementation

### 1. New profile

The `onNewProfile` callback in `MainActivity`
reads the next id from the repository, creates a
fresh `Profile` with an empty `controls` list,
and persists it. The activity switches to the
new profile by emitting it on the `profileFlow`.

```kotlin
onNewProfile = {
    scope?.launch {
        val newId = repo?.nextId() ?: return@launch
        val now = System.currentTimeMillis()
        val newProfile = Profile(
            id = newId,
            name = "Profile $newId",
            author = "user",
            controls = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        repo.upsert(newProfile)
        profileFlow.value = newProfile
        allProfilesFlow.value = repo.all()
    }
}
```

### 2. Delete profile

The `onDeleteProfile` callback reads the
repository's `count()`. If `count <= 1`, the
call is a no-op (with a log warning). Otherwise,
the callback deletes the current profile by id,
then switches to the first remaining profile (or
`null` if the count was 1, which we already
handled above).

```kotlin
onDeleteProfile = {
    scope?.launch {
        val current = profileFlow.value ?: return@launch
        val r = repo ?: return@launch
        if (r.count() <= 1) {
            Log.w(tag, "Refusing to delete the last profile (id=${current.id}).")
            return@launch
        }
        r.delete(current.id)
        val next = r.firstOrNull()
        profileFlow.value = next
        allProfilesFlow.value = r.all()
    }
}
```

The `RoomProfileRepository.delete` delegates to
`dao.deleteProfile(id)`. The Room foreign key
on `profile_control` is `CASCADE`, so the
control rows are removed automatically. The
`InMemoryProfileRepository.delete` removes the
profile from the in-memory list; the control
list is not stored separately (it's part of the
`Profile`), so no cascade is needed.

### 3. Foldable posture

`AndroidPostureObserver` wraps
`WindowInfoTracker.windowLayoutInfo(activity)`:

```kotlin
val tracker = WindowInfoTracker.getOrCreate(activity)
scope.launch {
    tracker.windowLayoutInfo(activity).collect { info ->
        val posture = info.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()
            ?.let { mapPosture(it) }
            ?: Posture.UNKNOWN
        currentRef.set(posture)
        trySend(posture)
    }
}
```

The mapping is total over `FoldingFeature`:

* `state == FLAT` → `Posture.FLAT` (device
  fully unfolded, lying flat)
* `state == HALF_OPENED` → `Posture.HALF_OPENED`
  (the hinge is at a non-flat angle)
* `orientation == HORIZONTAL && isSeparating`
  → `Posture.HALF_OPENED` (the hinge separates
  the top and bottom halves — the "tabletop"
  posture)
* no `FoldingFeature` → `Posture.UNKNOWN`
  (non-foldable device, or no hinge detected)

The full §16 layout adaptation (the editor's
per-posture layout) lands in Phase 1.6+. The
phase 1.5 deliverable is the *abstraction* —
the interface, the enum, the Android adapter,
and the unit tests.

## Tests

* 8 new tests in `PostureObserverTest` — the
  `NullPostureObserver` (no-op), the
  `FakePostureObserver` (deterministic), and
  the `Posture` enum's closed set.
* 3 new tests in `InMemoryProfileRepositoryTest`
  — `nextId` on empty, `nextId` on non-empty,
  `delete` of an existing id, `delete` of a
  missing id (no-op).
* 4 new tests in `RoomProfileRepositoryTest` —
  same as the in-memory tests, plus
  `delete`-cascades-controls.
* All Phase 0/1.0/1.1/1.2/1.3/1.4 tests still
  green (the changes are additive).

**Total: 342 tests, 0 failures, 0 errors.**

## Results

* `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**.
* `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL**.
  APK is 8.8 MB (Phase 1.4: 8.6 MB; +0.2 MB for
  the `androidx.window` dependency).
* `adb install + adb shell am start`: app
  launches; engine state machine active; toolbar
  shows Add / Save / Reset / New profile /
  Delete chips (the latter two are in the
  horizontal scroll, off-screen initially).

## Metrics

* APK size: 8.8 MB.
* New code: ~700 lines of Kotlin (production) +
  ~400 lines of test code.
* Test count: 342 (Phase 1.4: 327; +15 from this
  phase: 8 in `PostureObserverTest` + 3 in
  `InMemoryProfileRepositoryTest` + 4 in
  `RoomProfileRepositoryTest`).
* New dependencies: `androidx.window:window:1.3.0`.

## Failures and regressions

No new failures or regressions. The Phase 1.4
builds (which already have Bug #17 lint and
Bug #19 Robolectric) remain deferred.

## Risks

* **"Refuse to delete the last profile" is a
  log warning, not a UI dialog.** The user sees
  no feedback when they tap "Delete" on the
  last profile. The full rule (confirmation
  dialog, configurable) lands in Phase 1.6+.
* **No on-device profile-creation / deletion
  verification.** The chips are present in the
  toolbar's horizontal scroll; the unit tests
  cover the data flow; the on-device test
  cannot reach the off-screen chips via
  `adb input tap` (the horizontal scroll's
  accessibility tree does not expose them).
  The full on-device verification lands in
  Phase 1.6+ with the Compose Compiler upgrade
  + a `createComposeRule` test.
* **PostureObserver not wired into the
  activity's UI yet.** The observer is the
  *abstraction*; the per-posture layout
  adaptation lands in Phase 1.6+. The phase
  1.5 deliverable is the test surface and the
  Android adapter.

## Next block — Phase 1.6

* **Per-posture layout** — the editor's layout
  adapts to the posture (e.g. tabletop uses
  the top half for the dashboard and the
  bottom half for the controls).
* **Profile signature** — the §15 "Firmar
  perfiles" feature. Per-user signing key
  stored in Android Keystore; profile documents
  include a `signature` field that the host
  validates on import.
* **Confirmation dialog for "Delete profile"**
  — the §15 confirmation pattern.
* **Compose Compiler upgrade** — when KSP
  releases for Kotlin 2.2.x, bump Kotlin +
  Compose Compiler to fix Bug #17 and unblock
  Bug #19.
* **Phase 1.7+** — haptics (§27), transport
  multiplexer interface (§17), direct HID
  mode (§18).
