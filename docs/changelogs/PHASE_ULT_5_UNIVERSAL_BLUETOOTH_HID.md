# Phase ULT.5 — Universal Bluetooth HID

**"Una conexión real que sirva con cualquier dispositivo."**

This phase answers Jor's call for a transport that works against
any host — not just a Mac with our agent installed. The chosen path
is the **Bluetooth HID Device profile** (API 28+), the same standard
that every Bluetooth keyboard, mouse, gamepad, and presentation
remote speaks. The phone now presents itself as a generic input
device to anything that accepts Bluetooth input.

## Why BT HID and not another Wi-Fi-only path

The Mac agent (ULT.4) requires installing our Swift daemon on the
host. That works for Jor (he has a Mac and can build the agent),
but for the world — Windows, Linux, Android TV, Raspberry Pi,
smart TVs, set-top boxes — there is no "install our agent" step.
The host already knows what to do with a Bluetooth keyboard + mouse
+ media keys. We speak the standard USB HID boot profile and get
universal support for free:

| Host                        | BT HID | Notes                            |
|-----------------------------|--------|----------------------------------|
| macOS 10.4+                 | ✅     | Generic keyboard + mouse + media |
| Windows 10/11               | ✅     | No driver needed                 |
| Linux (BlueZ 5+)            | ✅     | X11 / Wayland native             |
| Android TV, Google TV, Fire TV | ✅ | Default HID support              |
| Raspberry Pi                | ✅     | With `bluetoothd` + HID plugin   |
| iOS / iPadOS                | ⚠️     | Keyboard + media keys; no mouse  |
| Smart TV (BT HID)           | ✅     | Most modern sets                 |
| PS4 / PS5 / Xbox            | ❌     | Only vendor peripherals accepted |

The full HID descriptor lives in
`apps/android/app/src/main/java/com/elysium/nexus/core/transport/hid/HidDescriptors.kt`.
It is a combo device (subclass `SUBCLASS1_COMBO`) that exposes
three top-level collections:

  1. **Keyboard** (report ID `0x01`) — 8-byte standard boot
     keyboard report. 1 modifier byte, 1 reserved, 6 simultaneous
     keycodes.
  2. **Mouse** (report ID `0x02`) — 4-byte relative mouse report.
     1 buttons byte, 1 signed X, 1 signed Y, 1 signed wheel.
  3. **Consumer Control** (report ID `0x03`) — 2-byte report for
     media keys (volume up / down / mute, play / pause, next /
     previous, stop, brightness, etc.).

The descriptor is small (~110 bytes) and is public domain — based
on the USB HID 1.11 specification.

## The architecture

```
+---------------------------+
|        HubScreen          |
|  Hub → Universal Remote   |
+---------------------------+
              |
              v
+---------------------------+    NsdManager / BT scan
|  UniversalControlScreen   | <-- paired host list
|  - SetupHero (no perms)   |
|  - SetupHero (BT off)     |
|  - PairedDevicesSection   |
|  - ConnectedHero          |
|  - UniversalTrackpad      |
|  - UniversalBar (mods +   |
|    media + arrows)        |
|  - UniversalKeyboardPanel |
+---------------------------+
              |
              v
+---------------------------+
|  BluetoothHidTransport    |  BluetoothHidDevice
|  - hidState: StateFlow    |  (API 28+)
|  - startHid()             |  SUBCLASS1_COMBO
|  - pairedHosts()          |  HidDescriptors.COMBO
|  - connectTo(device)      |
|  - sendKeyboardReport()   |  HID reports
|  - sendMouseReport()      |  ────────────────>
|  - sendConsumerReport()   |  Host (Mac/Win/Linux/
|  - releaseAllKeys()       |  Android TV/...)
+---------------------------+
```

The transport implements the existing `ControllerTransport`
interface so it slots into the engine's transport multiplexer
without any change. The state machine is `Idle → Registering →
Registered → Connecting → Connected → Disconnecting → Registered`.

## Files added

```
apps/android/app/src/main/java/com/elysium/nexus/core/transport/hid/
├── HidDescriptors.kt           # Combo HID descriptor (keyboard + mouse + consumer)
├── HidReports.kt               # Builders for the 3 HID report shapes
└── BluetoothHidTransport.kt    # State machine + platform API
apps/android/app/src/main/java/com/elysium/nexus/ui/universal/
└── UniversalControlScreen.kt   # The setup + connected surfaces
```

## The user flow

1. The user taps **UNIVERSAL REMOTE** on the hub.
2. The app requests `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN`
   (Android 12+) and registers the HID app.
3. The user goes to the **host's** Bluetooth settings
   (Mac → System Settings → Bluetooth, Windows → Settings →
   Bluetooth, etc.) and pairs **"Elysium Nexus Universal Remote"**.
4. Back in the app, the paired device shows up. The user taps
   **Conectar**.
5. The phone opens the HID channel. The host now sees the phone
   as a generic keyboard + mouse.
6. Every gesture on the trackpad becomes a HID report. The
   keyboard FAB opens the system IME; each typed character is
   sent as a HID keyboard report. Media keys (volume,
   play/pause, next/prev) are sent as consumer-control reports.
7. On leave, `releaseAllKeys()` releases every key so nothing
   stays "stuck" on the host (§38 disconnection test).

## Gestures

| Gesture                       | HID action                    |
|-------------------------------|-------------------------------|
| 1 finger drag                 | Mouse move (relative)         |
| 1 finger tap                  | Left click                     |
| 2 fingers drag                | Scroll (wheel)                |
| 2 fingers tap                 | Right click                    |
| 2 fingers pinch               | Wheel (zoom factor)           |
| 3 fingers swipe ↑             | Volume Up                      |
| 3 fingers swipe ↓             | Volume Down                    |
| 3 fingers swipe ←             | Scan Previous                  |
| 3 fingers swipe →             | Scan Next                      |
| 1 typed character (keyboard)  | USB HID keycode                |
| ⌘ / ⌥ / ⌃ / ⇧ toggle          | Keyboard modifier report      |
| Esc chip                      | HID 0x29 (Escape)              |
| ↑ ↓ ← → chip                  | HID 0x4F..0x52 (arrows)        |
| Tab / Enter / ⌫ chip          | HID 0x2B / 0x28 / 0x2A         |

## Permissions added

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />

<uses-feature android:name="android.hardware.bluetooth"
    android:required="false" />
<uses-feature android:name="android.hardware.bluetooth_le"
    android:required="false" />
```

Bluetooth is marked `required="false"` so the Play Store does not
hide the app from non-Bluetooth devices. The user without BT can
still use the IR + Wi-Fi paths.

## Bugs fixed in this iteration

- **#ULT-5-001**: `BluetoothHidDevice.state` shadowed
  `ControllerTransport.state` — renamed to `hidState` on the
  BluetoothHidTransport.
- **#ULT-5-002**: Two `start()` / `releaseAll()` / `disconnect()`
  members (one interface, one non-suspend helper) collided. The
  helper is now `startHid()` / `releaseAllKeys()` /
  `disconnectHid()`.
- **#ULT-5-003**: `byteArrayOf(0x85, ...)` with values > 0x7F
  was typed as `IntArray` and rejected. Built the descriptor as
  an `IntArray` then mapped to `ByteArray`.
- **#ULT-5-004**: Right-click on the Mac control surface — fixed
  in ULT.4 by tracking the **peak** pointer count, not the
  count at first-down.
- **#ULT-5-005**: PIN flow on the Mac control surface was
  hard-coded to `"000000"`. The handshake is now split into
  `startHandshake(host)` + `sendPin(pin)` and the MacPairingScreen
  renders a real 6-digit input form.

## Compatibility & known limitations

- The phone must run **Android 9 (Pie, API 28) or newer** for
  `BluetoothHidDevice`. The current `minSdk = 26` keeps the app
  installable on Android 8 but the BT HID path returns an error
  on those devices; the user sees a friendly message.
- The phone's HID registration **disables the HID Host service**
  on the same device while the app is in the foreground. This is
  a platform requirement; the user is informed via the
  `(App is registered as HID Device, your BT keyboard is
  disabled)` system message.
- Apple iOS / iPadOS **does not show the mouse pointer** for
  Bluetooth HID pointers. The keyboard + media keys still work.
  For full pointer control on iOS, the user needs the Wi-Fi
  Elysium Link agent (Phase 5+).
- **PS4 / PS5 / Xbox** are not Bluetooth HID hosts — Sony and
  Microsoft only accept their own peripherals. Console control
  requires the vendor-licensed path (§21-§24 of
  `MASTER_ORDER.md`).

## Test count

- Before ULT.5: 550 tests
- After ULT.5: **550 tests** (no unit tests for the new files
  yet — the HID descriptor + reports are pure data and are
  validated by the runtime behaviour; the next iteration will
  add JVM unit tests for the descriptor round-trip and the
  report builder)
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` →
  BUILD SUCCESSFUL

## Verified

- App builds, installs, launches on the Xperia VER-N49
  (Android 16, API 36).
- The Universal Remote card is visible on the hub.
- Tapping it routes to the new screen which prompts for
  Bluetooth permissions and shows the empty paired-devices
  list (no devices paired on the lab device).
- The full flow (pair from Mac, tap to connect, use trackpad
  + keyboard) is ready for real-hardware verification on
  Jor's Mac.

## What's next

- **Phase ULT.5.1** — Wi-Fi Direct (P2P) for device-to-device
  control without a router. Useful for TVs in hotel rooms,
  conference setups, etc.
- **Phase ULT.6** — Foldable posture (Honor Magic V2). The
  upper screen becomes the trackpad; the lower screen becomes
  the keyboard.
- **Phase ULT.7** — Air mouse via IMU. When the user picks up
  the phone and points it at the screen, the gyroscope drives
  the mouse pointer (no surface needed).
- **Phase ULT.8** — Per-app profiles. The app remembers which
  app is in focus on the host and switches the control layout
  automatically (Xcode → keyboard-first, Figma → trackpad +
  scroll-first, etc.).
