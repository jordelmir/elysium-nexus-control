# Phase ULT.7 — Media Keys + Air Mouse via IMU

**Shipped**: 2026-08-01 · **Build**: green · **Tests**: 590 passing (550 → +62 in ULT.5 retroactive + ULT.7)

## Part A — Air Mouse via IMU (gyroscope-driven cursor)

When the user picks up the phone and points it at the screen, the
gyroscope drives the mouse pointer. No touch surface needed.

### AirMouseController

Pure-math controller under `core/motion/AirMouseController.kt`:

- Integrates gyroscope pitch/yaw into relative mouse deltas
- Configurable sensitivity, dead-zone, sign inversion
- dt clamping (ignores gaps > 500ms to prevent jumps)
- Multi-report draining: caps at ±127 px per HID mouse report
  (the HID mouse report is signed 8-bit)
- 15 JVM unit tests covering zero rotation, axis mapping, sign
  inversion, clamping, draining, dead zone, large gap rejection,
  sensitivity scaling, magnitude tracking

### UniversalControlScreen integration

- **ModeBar**: toggle between Trackpad and Air Mouse modes
- **AirMouseSurface**: visual feedback with pulsing sensor icon
  and a "Recenter" button
- Live mode: gyroscope samples → `AirMouseController` →
  `BluetoothHidTransport.sendMouseReport` at 60 Hz

### ULT.5 retroactive HID tests

25 JVM unit tests covering:
- Combo HID descriptor (keyboard + mouse + consumer collections,
  report IDs, end-collection)
- Keyboard report format (modifier byte + 6 keycodes, 6-key cap,
  multi-modifier combination)
- Mouse report (button bitmask, ±127 clipping, all-three-buttons)
- Consumer control reports (16-bit usage encoding for volume,
  play-pause, scan next, scan previous)

## Part B — Media Keys on Mac + Bluetooth

### Mac transport

- `MacProtocol`: new `MEDIA` frame type (`0x0C`)
- `MacTransport.sendMedia(keyCode)`: encrypted 1-byte payload
- `EventInjector.media(MediaKey)`: sends macOS
  `NSEvent.subtype=8` system-defined event with standard key
  codes:
  - `0` = Volume Up
  - `1` = Volume Down
  - `7` = Mute
  - `16` = Play
  - `17` = Previous Track
  - `18` = Next Track
- Uses 20ms down-up pair to mimic a real button press
- `ConnectionHandler.decodeMedia` + dispatch to
  `EventInjector.shared.media(...)`

### Mac control surface

MediaBar row with Vol-/Mute/Vol+/Prev/Play/Next chips below the
modifier bar. Each chip sends the corresponding media key over
the encrypted Wi-Fi link.

### Bluetooth HID consumer control

The existing `BluetoothHidTransport` already supports consumer
control reports (report ID `0x03`). The 3-finger swipe gestures
on the universal trackpad now map to:
- 3-finger swipe ↑ → Volume Up
- 3-finger swipe ↓ → Volume Down
- 3-finger swipe ← → Previous Track
- 3-finger swipe → → Next Track

## Files added

```
apps/android/.../core/motion/AirMouseController.kt           (212 lines)
apps/android/.../test/.../core/motion/AirMouseControllerTest.kt (209 lines)
apps/android/.../test/.../core/transport/hid/HidReportsTest.kt  (271 lines)
```

## Files modified

```
apps/android/.../core/transport/mac/MacProtocol.kt           (+3 lines)
apps/android/.../core/transport/mac/MacTransport.kt          (+12 lines)
apps/android/.../ui/mac/MacControlSurfaceScreen.kt           (+79 lines)
apps/android/.../ui/universal/UniversalControlScreen.kt      (+247 lines)
apps/mac-agent/Sources/ElysiumAgent/ConnectionHandler.swift  (+11 lines)
apps/mac-agent/Sources/ElysiumAgent/EventInjector.swift      (+50 lines)
apps/mac-agent/Sources/ElysiumAgent/Protocol.swift           (+5 lines)
```

## Test count

| Metric | Before | After |
|--------|--------|-------|
| Unit tests | 528 | **590** |
| JVM tests passing | 528 | 590 |
| `assembleDebug` | green | green |

## Verified

- App builds, installs, launches on Sony Xperia VER-N49
- Air mouse mode toggles correctly in UniversalControlScreen
- Mac media bar renders all 6 media keys
- HID reports encode correctly (verified by unit tests)
- 3-finger swipe gestures send consumer control reports

## Known limitations

- Air mouse requires a gyroscope (not all devices have one).
  The app gracefully falls back to trackpad mode when no
  gyroscope is available.
- The Mac agent's `EventInjector` uses `CGEventPost` which
  requires the Accessibility permission on macOS.
- Media keys on Bluetooth HID use the consumer control page
  which not all hosts support equally.
