# PHASE ULT.9 — USB HID Transport + UI Wiring

**Date:** 2026-08-02
**Status:** SHIPPED ✅

## What was built

### 1. USB HID Transport (`core/transport/usb/UsbHidTransport.kt`)
- Raw HID reports sent over USB bulk endpoint
- Wire format: tagged binary packets (0x01=mouse move, 0x02=button, 0x03=scroll, 0x04=keyboard, 0x05=touchpad move, 0x06=touchpad click, 0x07=touchpad scroll, 0x10=gamepad, 0xFE=ping, 0xFF=release all)
- USB permission handling with `RECEIVER_EXPORTED` flag (lint clean)
- `PendingIntent` with explicit package (lint clean for `MutableImplicitPendingIntent`)
- Gamepad state packing: buttons (u64), sticks (i8), triggers (u8)
- Mouse/keyboard/touchpad helper methods for the control surface

### 2. USB-C Hub Card + Navigation
- Yellow "USB-C CABLADO" card in `HubScreen` (between MAC/PC and UNIVERSAL REMOTE)
- `HubDestination.UsbC` added to navigation stack
- `UsbCConnectionScreen` — connection status display with latency info
- `MainActivity` wired: tap → navigation → back

### 3. NeonYellow Color
- Added `NeonYellow`, `NeonYellowDim`, `NeonYellowGlow` to `ElysiumColors`

### 4. USB Device Filter
- `res/xml/usb_device_filter.xml` — matches any USB accessory
- Manifest `USB_ACCESSORY_ATTACHED` intent filter on `MainActivity`

### 5. Wire Format Tests (`UsbHidWireFormatTest.kt`)
- 15 tests covering all packet types, bit packing, axis conversion, trigger conversion

### 6. Mac USB Agent (`macos-agent/Sources/main.swift`)
- Swift CLI that reads HID reports from USB serial (`/dev/tty.usbmodem*`)
- Injects events into macOS via `CGEventPost`
- Complete HID→macOS keycode mapping
- Wire format matches Android `UsbHidTransport.kt` tags
- Not compiled yet (needs `swift build` on Mac)

## Verification
- `./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — BUILD SUCCESSFUL
- Installed on Honor Magic V2 via ADB
- USB-C card visible and navigates to connection screen
- All 15 wire format tests pass
- 0 lint errors

## Files changed
- `apps/android/app/src/main/java/com/elysium/nexus/core/transport/usb/UsbHidTransport.kt` — NEW
- `apps/android/app/src/main/java/com/elysium/nexus/ui/usb/UsbCConnectionScreen.kt` — NEW
- `apps/android/app/src/main/java/com/elysium/nexus/ui/theme/ElysiumTheme.kt` — NeonYellow
- `apps/android/app/src/main/java/com/elysium/nexus/ui/hub/HubScreen.kt` — USB-C card
- `apps/android/app/src/main/java/com/elysium/nexus/ui/hub/HubNavigation.kt` — UsbC dest
- `apps/android/app/src/main/java/com/elysium/nexus/ui/MainActivity.kt` — USB-C nav
- `apps/android/app/src/main/AndroidManifest.xml` — USB intent filter
- `apps/android/app/src/main/res/xml/usb_device_filter.xml` — NEW
- `apps/android/app/src/test/java/com/elysium/nexus/core/transport/usb/UsbHidWireFormatTest.kt` — NEW
- `macos-agent/Sources/main.swift` — NEW (Mac USB daemon)
- `macos-agent/Package.swift` — NEW
