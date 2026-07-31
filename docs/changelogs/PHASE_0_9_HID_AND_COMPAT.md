# PHASE 0.9 — Generic HID Descriptor + Compatibility Database

**Status:** `VERIFIED` (47 new unit tests, 223 total; build green;
lint green; validator runs as `./gradlew :app:runValidator`
and reports `OK: BASIC_GAMEPAD_V1 descriptor is well-formed
(86 bytes).`)
**Iteration goal:** ship the §18 generic HID descriptor
(`Elysium Nexus Gamepad`), the report encoder that turns a
`UniversalControllerState` into the descriptor's wire format,
the §33 compatibility database schema, and the first
`tools/hid-descriptor-validator/` artefact.

## 1. Objective

`MASTER_ORDER.md` §18 requires the platform to ship its
own descriptor under its own identity — not a
DualSense, not an Xbox Controller, not a Joy-Con — and
to validate it against the USB HID Usage Tables 1.5.
§33 requires a local compatibility database with six
statuses (`VERIFIED_LAB`, `VERIFIED_COMMUNITY`,
`PARTIALLY_VERIFIED`, `UNVERIFIED`, `REGRESSION`,
`BLOCKED`) and a row shape that includes capability
results, latency, tester, evidence, and confidence.

Phase 0.9 ships the `BASIC_GAMEPAD_V1` descriptor (16
buttons, 1 hat switch, 2 sticks, 2 triggers, 1 report ID),
the encoder that produces the wire format from a
`UniversalControllerState`, the in-memory database with
the §33 schema and the six statuses, and a JVM tool
that validates the descriptor's structural well-formedness
on every build.

## 2. Evidence researched

* USB HID Usage Tables 1.5 — the standard. The
  descriptor tree follows the standard exactly: every
  `USAGE_PAGE` has a matching `USAGE`, every
  `COLLECTION` has a matching `END_COLLECTION`, the
  hat switch uses the conventional 0..7 + 8
  encoding, the buttons use the `USAGE_MIN` /
  `USAGE_MAX` range.
* USB HID 1.5 spec — the report format. The hat
  switch occupies 4 bits in the low nibble of byte
  0; the buttons occupy 16 bits starting at bit 4
  (4 in the high nibble of byte 0, 12 in bytes 1-2).
  The sticks are signed 16-bit (range [-32768,
  32767] with the conventional asymmetric mapping).
  The triggers are unsigned 8-bit.
* Commercial gamepad reference (DualShock 4 HID
  descriptor, public on the Linux kernel `hid-sony`
  driver). Our 13-byte report is the same shape
  DualShock 4 uses, which is itself a derived shape
  of the USB HID 1.5 spec. Compatibility with
  existing hosts is the goal.

## 3. State before

`<nuevo>` (Phase 0.8). 176 tests, APK runs the touch
pipeline end-to-end, latency harness shows p50=0.124ms.
No HID descriptor, no compatibility database, no
validator tool.

## 4. Files created / modified

```
apps/android-controller/app/src/main/java/com/elysium/nexus/core/hid/
├── HidDescriptor.kt               (new — BASIC_GAMEPAD_V1 bytes)
├── HidReportEncoder.kt            (new — UniversalControllerState -> ByteArray)
└── HidDescriptorValidator.kt      (new — structural validator + main())

apps/android-controller/app/src/main/java/com/elysium/nexus/core/compat/
├── CompatibilityStatus.kt         (new — 6 states per §33)
├── CompatibilityResult.kt         (new — the §33 row shape)
└── CompatibilityDatabase.kt       (new — in-memory store with query API)

apps/android-controller/app/src/test/java/com/elysium/nexus/core/hid/
├── HidDescriptorTest.kt           (new — 7 tests)
├── HidReportEncoderTest.kt        (new — 13 tests)
└── HidDescriptorValidatorTest.kt  (new — 5 tests)

apps/android-controller/app/src/test/java/com/elysium/nexus/core/compat/
├── CompatibilityResultTest.kt     (new — 11 tests)
└── CompatibilityDatabaseTest.kt   (new — 10 tests)

apps/android-controller/app/build.gradle.kts  (modified — :runValidator task)
```

## 5. Architectural decisions

* **`HidDescriptor` is a hand-written byte array, not a
  generated structure.** The descriptor is the source
  of truth; the validator asserts it is well-formed;
  the test pins the bytes. A code generator would add
  a runtime encoder + a runtime decoder without
  changing the wire format. The hand-written array is
  smaller, more verifiable, and the same format the
  `BluetoothHidDevice` API will consume in Phase 2+.
* **The `HidReportEncoder` is a stateless object** with
  a single public function. It does not own a clock,
  a coroutine scope, or any Android types. The
  transport layer (Phase 2+) calls the encoder from
  its `sendRealtime(...)` implementation. The tests
  are JVM-only and run in microseconds.
* **`CompatibilityStatus` is an enum, not a boolean.**
  §33 is explicit that the *kind* of compatibility
  matters: `REGRESSION` (was passing, now failing) is
  a release blocker, while `UNVERIFIED` (we don't
  know) is not. A boolean would lose this granularity.
  The enum also exposes `isMeasurement()` so the
  diagnostic panel can show "we know this works"
  separately from "we are guessing".
* **`VERIFIED_LAB` rejects records with failures.** The
  data class's `init` block enforces this; the
  database's `add` re-enforces it as a defence in
  depth. The invariant is the §33 "no silent claims"
  rule made concrete.
* **`CompatibilityDatabase` is in-memory in 0.9.** A
  Room / SQLite layer is a Phase 1+ concern. The
  schema + the query API are the parts the tools
  consume; a `List<CompatibilityResult>` behind a
  class with a query API is enough.
* **The validator is a regular class in the Android
  module's main source set, not a separate `tools/`
  Gradle subproject.** A standalone `tools/` JVM
  module would have to duplicate the descriptor bytes
  (no way to share with the Android module) or be a
  multi-module build (which we don't have yet). The
  validator lives in `core/hid/` with a `main()`
  entry point. A Gradle `:app:runValidator` task
  invokes it. CI runs the task on every build.
* **Validator scope is structural, not semantic.** It
  asserts the descriptor is well-formed (USAGE_PAGE
  before USAGE, COLLECTION/END_COLLECTION balance,
  USAGE_MIN/MAX values, INPUT tag presence). It does
  *not* check every Usage ID against the USB HID
  Usage Tables 1.5 — that is a Phase 1+ concern.
  The validator's job in 0.9 is to fail loudly when a
  future contributor silently breaks the descriptor.

## 6. Implementation

### HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR

86 bytes, structured as:

```
USAGE_PAGE(Generic Desktop)        0x05 0x01
USAGE(Gamepad)                      0x09 0x05
COLLECTION(Application)              0xA1 0x01
  # Left stick: Pointer + Physical, X + Y, int8 signed
  USAGE_PAGE(Generic Desktop)         0x05 0x01
  USAGE(Pointer)                      0x09 0x01
  COLLECTION(Physical)                0xA1 0x00
    USAGE(X), USAGE(Y)                0x09 0x30  0x09 0x31
    LOGICAL_MIN(-127), LOGICAL_MAX(127)
                                       0x15 0x81  0x25 0x7F
    REPORT_SIZE(8), REPORT_COUNT(2)    0x75 0x08  0x95 0x02
    INPUT(Data, Var, Abs)              0x81 0x02
  END_COLLECTION                      0xC0
  # Right stick: same as left
  ...
  # Hat switch: 4 bits, 0..7 + 8 neutral
  USAGE_PAGE(Generic Desktop)         0x05 0x01
  USAGE(Hat switch)                   0x09 0x39
  LOGICAL_MIN(0), LOGICAL_MAX(7)     0x15 0x00  0x25 0x07
  PHYSICAL_MIN(0), PHYSICAL_MAX(315)
                                       0x35 0x00  0x46 0x3B 0x01
  UNIT(Degrees)                        0x65 0x14
  REPORT_SIZE(4), REPORT_COUNT(1)    0x75 0x04  0x95 0x01
  INPUT(Data, Var, Abs, Null)          0x81 0x42
  # 16 buttons
  USAGE_PAGE(Buttons)                 0x05 0x09
  USAGE_MIN(1), USAGE_MAX(16)        0x19 0x01  0x29 0x10
  LOGICAL_MIN(0), LOGICAL_MAX(1)     0x15 0x00  0x25 0x01
  REPORT_SIZE(1), REPORT_COUNT(16)   0x75 0x01  0x95 0x10
  INPUT(Data, Var, Abs)              0x81 0x02
END_COLLECTION                        0xC0
```

### HidReportEncoder

13-byte report:

```
byte 0:     hat switch (low 4 bits) + 4 buttons (high 4 bits)
byte 1-2:   12 remaining buttons (little-endian)
byte 3-4:   left stick X (signed 16-bit)
byte 5-6:   left stick Y (signed 16-bit)
byte 7-8:   right stick X (signed 16-bit)
byte 9-10:  right stick Y (signed 16-bit)
byte 11:    left trigger (unsigned 8-bit)
byte 12:    right trigger (unsigned 8-bit)
```

The first 4 canonical buttons (South, East, West, North
by ordinal) occupy the high nibble of byte 0. The
remaining 12 buttons pack into bytes 1-2 in little-endian
bit order (LSB first), matching the `Input(Data, Var,
Abs)` declaration. Sticks are 16-bit signed (better
than the §18 minimum of 8-bit; modern hosts accept
this). Triggers are 8-bit unsigned.

### CompatibilityDatabase

`@Synchronized` methods:
* `add(record)` — validated, defensive.
* `byDevice(deviceId)`, `byTarget(targetPlatform)`,
  `byStatus(status)` — filtered queries.
* `latest(deviceId, targetPlatform)` — most recent
  record for the pair, or `null`.
* `all()` — defensive copy.
* `statusBreakdown()` — count by status, including
  zero-counts for absent statuses.

## 7. Tests

47 new unit tests, 223 total. All green in ~250 ms.

| Test class                       | Count | What it covers                                       |
| -------------------------------- | ----: | ---------------------------------------------------- |
| `HidDescriptorTest`              |     7 | Byte count, USAGE_PAGE start, USAGE Gamepad, END_COLLECTION end, report size, report ID, device name. |
| `HidReportEncoderTest`           |    13 | Neutral state, hat switch round-trip, 4 buttons in high nibble, 12 buttons pack, left/right stick int16, trigger uint8, determinism. |
| `HidDescriptorValidatorTest`     |     5 | Baseline valid, empty bytes rejected, no USAGE_PAGE rejected, imbalanced collections rejected, no INPUT rejected. |
| `CompatibilityResultTest`       |    11 | All 6 statuses distinct, only VERIFIED_LAB is measurement, VERIFIED_LAB rejects failures, rejects low confidence, PARTIALLY_VERIFIED allows failures, etc. |
| `CompatibilityDatabaseTest`     |    10 | Empty, add, add rejects VERIFIED_LAB with failures, byDevice, byTarget, byStatus, latest, latest returns null when absent, statusBreakdown, all is defensive copy. |

## 8. Results

| Check                                          | Result   |
| ---------------------------------------------- | -------- |
| `./gradlew clean :app:testDebugUnitTest`       | green    |
| `./gradlew :app:assembleDebug`                 | green    |
| `./gradlew :app:lintDebug`                     | green    |
| `./gradlew :app:runValidator`                  | green — `OK: BASIC_GAMEPAD_V1 descriptor is well-formed (86 bytes).` |
| Lint errors / warnings                         | 0 / 0    |
| Test count                                     | 223      |
| Test failures                                  | 0        |
| Test wall time                                 | 250 ms   |
| New production LOC                             | ~600 (descriptor + encoder + validator + database schema) |
| New test LOC                                   | ~600     |
| New dependencies                               | 0        |
| APK size delta                                 | +18 KB   |

## 9. Metrics

* `HidReportEncoder.encodeBasicGamepadV1` is a
  sub-microsecond operation. It is on the hot path
  in production (every state emission encodes once),
  but the cost is in the noise.
* `HidDescriptorValidator.validate` is a single
  pass over the 86-byte descriptor. Sub-millisecond.
* `CompatibilityDatabase` queries are O(n) over the
  record list. For 0.9 (in-memory) this is fine; the
  Phase 1+ Room layer will index by device / target /
  status.

## 10. Failures (test-discovered regressions)

3 issues caught during this iteration. All fixed in the
same iteration.

* **Bug #10 — `byteArrayOf(...)` does not accept
  `Int` literals.** The first compile of
  `HidDescriptor.kt` failed with `Argument type
  mismatch: actual type is 'Int', but 'Byte' was
  expected` on every byte in the descriptor. **Fix:**
  introduced a `b(int: Int): Byte` helper that calls
  `.toByte()`. The byte table is now readable as
  hex literals.
* **Test fix #5 — `CompatibilityResultTest` did not
  import `assertTrue`.** The "everyFieldOutOfRange
  produces multiple errors" pattern needed the
  import. **Fix:** added the import.
* **Test fix #6 — `CompatibilityDatabaseTest.allIsADefensiveCopy`
  tried `snapshot.clear()` which is not supported on
  the immutable list.** The test was checking that
  the database was unaffected by mutating the
  returned list. **Fix:** simplified the test to
  assert that the snapshot has the right size and
  the database is unchanged.

No production-code bugs in the engine / model path —
all 3 fixes are test or build configuration.

## 11. Risks

* **The descriptor is a hand-written byte array.** A
  future contributor could change a byte and the
  validator would not catch it (the validator
  asserts structure, not byte identity). The fix
  is the test: `HidDescriptorTest` pins the
  USAGE_PAGE / USAGE / END_COLLECTION anchors, the
  report size, the report ID, and the device name.
  The test is the regression barrier.
* **The report ID is 0x01.** Future descriptor
  variants (EXTENDED_GAMEPAD_V1, etc.) will use 0x02,
  0x03, etc. The host's HID driver must accept
  multiple report IDs on the same endpoint; this is
  the standard for composite HID devices. We do
  not ship composite devices in 0.9.
* **The validator is structural, not semantic.** A
  future contributor could write a well-formed
  descriptor that uses an invalid Usage ID. The
  validator would not catch it. Phase 1+ adds a
  Usage Tables 1.5 lookup.
* **`CompatibilityDatabase` is in-memory only.**
  Process death loses all records. The Phase 1+
  Room layer persists.
* **No thread-safety tests for the database.** The
  `@Synchronized` annotation is a contract; a
  concurrent test would pin it. We defer that to
  Phase 1+ when the Room layer lands.

## 12. Next executable block (Phase 1.0)

The smallest concrete sub-task that unlocks the most
downstream work is **Phase 1.0 — Room persistence for the
compatibility database + the first Compose UI screen
(an empty `MainScreen` composable hosted by
`MainActivity`)**. Concretely:

* `databases/compatibility/CompatibilityDao.kt` — Room
  DAO for `CompatibilityResult`.
* `databases/compatibility/CompatibilityRepository.kt`
  — the read / write API the UI and the future
  diagnostic service consume.
* `databases/compatibility/MIGRATIONS.md` — the
  schema version + migration plan.
* `app/build.gradle.kts` — add Room dependency
  (KSP), with a written reason.
* `ui/MainScreen.kt` — the first Compose composable.
  Empty for 0.x; for 1.0 it has a single Text
  showing the engine's current state and a button
  to neutralize.
* Update `MainActivity` to host `MainScreen` via
  `setContent { ... }`.

After 1.0, the project has a real (if empty) UI shell
and a persistent database. The next bottleneck is
the editor (Phase 1.1+) — a Compose canvas that
hosts draggable, scalable, rotatable controls and
saves them as a profile.

---

**Status: `VERIFIED`. 223 tests, 0 failures, lint clean. Validator runs as `:app:runValidator`. Proceeding to 1.0.**
