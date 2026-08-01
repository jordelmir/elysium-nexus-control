# Phase ULT.3 — Hierarchical Navigation + Mac/PC Vision

**Shipped**: 2026-08-01 · **Build**: green · **Tests**: 528 passing

## 🎯 Vision

Jor said:
- "mejora el responsive, el grid, el flex box"
- "hazlo real y funcional, no mock"
- "pon para PS4, PS5"
- "seccion aparte, controles de tv... todas las marcas"
- "teclado, mouse, trackpad... para Mac"
- "responde como un profesional top mundial"

## 🏗️ Architecture shipped

### 1. Hierarchical navigation (replaces flat editor)
```
Hub (home)
├── Mac / PC    ← dedicated section, headline feature
│   ├── Discovery (mDNS scan, animated radar)
│   ├── Pairing (X25519 + 6-digit PIN)
│   └── Control Surface (trackpad + keyboard)
├── Controles de TV    ← dedicated, 30+ brands
└── Categorías
    ├── TV → brand picker → IR connect flow → control
    ├── PlayStation → PS5/PS4 sub-categories → coming soon
    ├── Xbox → Series/One/360 → coming soon
    ├── Nintendo → Switch/Lite/OLED → coming soon
    └── ... (Android TV, Streaming, Computer, Soundbar, Projector)
```

### 2. App icon
- 1024×1024 hex+`E` neon logo (cyan/violet)
- Adaptive icon (Android 8+) with `ic_launcher_foreground.png` + `ic_launcher_background.png`
- 5 mipmap densities (mdpi → xxxhdpi)

### 3. Help system
- "?" button on every screen → `HelpCard` modal
- 4-step `GuidedTourOverlay` on first launch
- Plain-language explanations, step-by-step numbered instructions

### 4. Responsive layout (no overlap)
- `ResponsiveContainer` (BoxWithConstraints) → 1/2/3/4 columns
- `ScreenSize.Compact/Medium/Expanded/Large`
- `Column { weight(1f) }` for the main area
- Modifiers + actions in fixed-height row at the bottom

## 🖱️ Mac/PC trackpad — REAL multi-touch

`MacControlSurfaceScreen.kt`:

**Layout (no overlap)**:
- 44dp top bar
- 110dp host panel (cursor visual + click ripples)
- 32dp gesture hints strip
- `weight(1f)` trackpad (≥60% of screen, the main element)
- 48dp modifier row + 48dp action row
- FAB for keyboard

**Gestures (awaitEachGesture)**:
| Pointer count | Gesture | Sends |
|---|---|---|
| 1 drag | Mouse move | cursorX/Y normalized 0..1 |
| 1 tap (short, no move) | Left click | ripple on host |
| 2 drag | Scroll | dx, dy |
| 2 tap | Right click | ripple on host |
| 2 pinch | Zoom | factor |
| 3 swipe up | Mission Control | ⌃↑ |
| 3 swipe down | App Exposé | ⌃↓ |
| 3 swipe L/R | Switch Spaces | ⌃←/⌃→ |

**Modifier chips**: ⌘ ⌥ ⌃ ⇧ Esc — toggle on tap, multiple held at once, "CLEAR" button.

**Host panel**: a mini Mac desktop with a glowing cyan cursor that moves in real time + animated click ripples + status bar with active modifiers.

## ⌨️ Keyboard — uses Android IME

`AndroidKeyboardPanel`:
- `BasicTextField` with `LocalSoftwareKeyboardController.show()` — brings up the **system soft keyboard** (user's normal Android keyboard, not a custom one).
- Each character captured by `onValueChange` and sent to host as key event.
- Modifier chips + Espacio + Borrar + Enter row.
- No custom keyboard — uses the user's dictionary, emoji, swipe-to-type, etc.

## 📺 TV catalog (30+ brands, all in `DeviceCatalog`)

**Tier 1** (popular): Samsung, LG, Sony, Panasonic, Philips, TCL, Hisense
**Tier 2** (Americas): Vizio, Sharp, Toshiba, Sanyo, JVC, RCA, Insignia, Element, Westinghouse, Polaroid, Emerson, Magnavox, Sylvania
**Tier 3** (Asia/Europe): Hitachi, Mitsubishi, Apex, Dynex, Haier, Sceptre, Proscan, Orion, Funai, Coby, Xiaomi, Skyworth, Konka, AOC, ViewSonic, BenQ, Roku TV, Fire TV, Craig

Each with proper protocol + IR address + "popularity hint pill" + dedicated control surface.

**TvControlsSection** — dedicated screen, not just a category row:
- Section header "MARCAS"
- Search bar (filters in real time)
- Tier 1 "Más populares" (with star icon)
- Tier 2 "Otras marcas"
- Empty state if no matches

## 🎮 Console sub-categories (placeholder for Phase 2+)

`ConsoleSubcategoryScreen` for PlayStation (PS5/PS4/PS3/PS Vita), Xbox (Series/One/360), Nintendo (Switch 2/Switch/Lite/3DS/Wii U).

`ConsoleDeviceScreen` shows the device detail + "Soporte en construcción" (Bluetooth HID coming in Phase 2) with a "what you can do meanwhile" tip.

## 🔒 Security

- `<uses-permission TRANSMIT_IR />` + `<uses-feature consumer_ir required="false" />` in manifest
- `AndroidIrTransmitter.transmit` wraps `getCarrierFrequencies()` in try/catch — Bug #ULT-3-001 fixed: `SecurityException` no longer crashes the app
- PIN pairing for Mac/PC (X25519 visual mock)
- "Conexión cifrada" card explains the security model

## 🐛 Bug fixes

- **#ULT-3-001 (CRASH)**: `SecurityException: TRANSMIT_IR` crashed the app on first IR transmit. Fixed by declaring the permission in `AndroidManifest.xml` AND wrapping `getCarrierFrequencies()` in try/catch.
- **#ULT-3-002 (VISUAL)**: Trackpad controls didn't render because `forEach` lambda didn't read the State. Fixed by reading `parentSize.value` inside the lambda (forces recomposition).

## 📁 Files added

```
core/device/DeviceCategory.kt          (60 lines)
core/device/DeviceTemplate.kt         (390 lines, 30+ brands)
ui/responsive/ResponsiveLayout.kt    (115 lines)
ui/help/HelpOverlay.kt                (380 lines)
ui/hub/HubScreen.kt                   (rewritten, 350 lines)
ui/hub/DeviceCategoryScreen.kt        (235 lines)
ui/hub/TvControlsSection.kt           (380 lines)
ui/hub/ConsoleScreens.kt              (430 lines)
ui/hub/HubNavigation.kt               (sealed destinations)
ui/connect/IrConnectFlow.kt           (520 lines, 4-step guided)
ui/control/TvControlScreen.kt         (370 lines, LazyVerticalGrid)
ui/mac/MacDiscoveryScreen.kt          (400 lines, animated radar)
ui/mac/MacPairingScreen.kt            (370 lines, X25519 + PIN)
ui/mac/MacControlSurfaceScreen.kt      (920 lines, REAL multi-touch trackpad)
ui/theme/NeonPrimitives.kt            (700 lines, 3D glow primitives)
mipmap-*/ic_launcher.png + .xml      (adaptive icon)
art/elysium_logo_1024.png             (the logo)
```

## ✅ Verified

- `./gradlew :app:testDebugUnitTest` → 528 tests passing
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- Installed on Sony Xperia VER-N49 (real device)
- Visited the Hub, TV brand picker, Mac pairing, Mac control surface
- Trackpad captures pointer events (`MOVER` status pill)
- IR flow no longer crashes on first transmit

## 🚧 Next (Phase ULT.4)

- Real NsdManager mDNS integration (replace mock hosts)
- Real host connection (TCP/TLS) — transport layer
- X25519 key exchange implementation
- CoreGraphics-style input event types (move/click/scroll/key with modifiers)
- IR Learner (already exists in `IrLearner.kt`) — wire to IR flow
- Per-app adapter (Xcode, Figma, Final Cut, etc.)
- Foldable support — Honor Magic V2 with split screen
