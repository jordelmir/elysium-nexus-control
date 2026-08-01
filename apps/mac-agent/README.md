# Elysium Nexus — Mac Agent

A small menu-bar app for macOS that lets the
[Elysium Nexus Android app](../android/) control
your Mac over the local network.

The agent runs in the menu bar. When the Android
phone pairs with it, every gesture on the phone
(trackpad, keyboard, modifier, scroll, pinch,
3-finger swipe) is sent over the local network
and injected as a real macOS event via `CGEvent`.

## Quick start

```bash
# 1. Build
./build.sh

# 2. Launch
open ".build/Elysium Nexus.app"

# 3. Grant Accessibility (one-time)
#    System Settings → Privacy & Security → Accessibility
#    → add "Elysium Nexus Mac Agent" and toggle ON

# 4. On the Android phone, tap "Mac / PC" on the Hub,
#    wait for the scan, tap your Mac in the list,
#    type the 6-digit PIN shown on the Mac.
```

## Architecture

```
Android phone (trackpad / keyboard gestures)
        │   Wi-Fi (LAN, mDNS _elysium._tcp)
        ▼
Mac agent (this app)
   ├─ BonjourAdvertiser  publishes _elysium._tcp
   ├─ TCPServer          accepts connections on :7878
   ├─ ConnectionHandler   per-conn state machine
   │     ├─ awaitingHello  read client X25519 pubkey
   │     ├─ awaitingPin    read 6 encrypted PIN digits
   │     └─ ready          read encrypted event frames
   ├─ PairingManager      shows 6-digit PIN in a SwiftUI window
   ├─ EventInjector       CGEvent.post(mouse/keyboard)
   └─ StatusBarController menu bar UI
```

## Protocol

Binary, length-prefixed, big-endian. Every frame
is `u32 length | u8 type | payload (length-1)`.
After the X25519 handshake, every payload is
ChaCha20-Poly1305 sealed; the on-wire payload is
`nonce(12) | ciphertext | tag(16)`.

| Type   | Name        | Direction     | Payload (plaintext)                          |
|--------|-------------|---------------|----------------------------------------------|
| 0x01   | HELLO       | client→server | 32-byte X25519 public key                   |
| 0x02   | HELLO_ACK   | server→client | 32-byte X25519 public key                   |
| 0x03   | PIN_DIGIT   | client→server | 1 byte (0-9)                                |
| 0x04   | PAIR_OK     | server→client | 1 byte (0=fail, 1=ok)                       |
| 0x05   | MOUSE_MOVE  | client→server | 8 bytes (float32 dx, float32 dy)            |
| 0x06   | MOUSE_BUTTON| client→server | 2 bytes (button u8, state u8)               |
| 0x07   | SCROLL      | client→server | 8 bytes (float32 dx, float32 dy)            |
| 0x08   | KEY         | client→server | 9 bytes (action u8, hid_usage u32, mods u32) |
| 0x09   | PINCH       | client→server | 4 bytes (float32 factor)                    |
| 0x0A   | HEARTBEAT   | bidirectional | empty                                       |
| 0x0B   | GOODBYE     | bidirectional | empty                                       |

**Modifiers bitmask** (mirrors `CGEventFlags`):
- `1 << 1`  = Shift  (`kCGEventFlagMaskShift`)
- `1 << 18` = Control
- `1 << 19` = Option
- `1 << 20` = Command

**Buttons**: 0 = left, 1 = right, 2 = middle.
**States**: 0 = up, 1 = down.
**Key actions**: 0 = down, 1 = up, 2 = repeat.

## Security

- **X25519 ECDH** for key agreement.
- **ChaCha20-Poly1305** for per-frame encryption
  (AEAD — confidentiality + integrity).
- **HKDF-SHA256** for key derivation with
  domain-separated salt + info.
- **6-digit PIN** confirmed by the user on the
  phone before the channel opens.
- **No remote access by default** — the agent only
  listens on the LAN. A future release will add
  optional relay with end-to-end encryption.

## Permissions

macOS requires the user to grant:

- **Accessibility** — System Settings → Privacy &
  Security → Accessibility. Required for
  `CGEvent.post`.
- **Local Network** — macOS 14+ shows a prompt on
  first launch. Required for the phone to discover
  the Mac via Bonjour.
- **Input Monitoring** — optional. Required only
  if a future release adds local keystroke
  capture (Phase 2).

The agent detects missing permissions at startup
and logs a warning. The status bar shows a hint.

## Limitations

- The agent uses a **plain TCP** channel — TLS is
  on the roadmap but the X25519 + ChaCha20 layer
  already provides end-to-end encryption and
  authenticated integrity.
- The agent supports **one paired phone at a
  time**. A second connection kicks the first one.
- The agent is **unsigned**. macOS will show a
  Gatekeeper warning on first launch — right-click
  the .app and "Open" to allow it. A signed +
  notarized build is a future release.
- The agent does **not** capture local
  keystrokes (Phase 2). It only *posts* events
  from the phone to the Mac.

## Development

```bash
swift build -c debug
swift run elysium-agent
```

To verify the protocol from a script, use
`nc localhost 7878` and send a 4-byte length +
1-byte type + payload for each frame.

## File map

```
Sources/ElysiumAgent/
├── main.swift                  # Entry point
├── Protocol.swift              # Frame format
├── Crypto.swift                # X25519 + ChaCha20
├── BonjourAdvertiser.swift     # mDNS service
├── TCPServer.swift             # Network framework listener
├── ConnectionHandler.swift      # Per-conn state machine
├── EventInjector.swift         # CGEvent posting
├── StatusBarController.swift   # Menu-bar UI
├── PINWindow.swift              # SwiftUI pairing window
└── PermissionManager.swift     # Accessibility check
```
