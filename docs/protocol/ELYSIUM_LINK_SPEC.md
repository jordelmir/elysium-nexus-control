# Elysium Link Protocol Specification

> Version 1.0 — Phase ULT.12

## Overview

Elysium Link is the binary transport protocol
that connects the Android controller app to
hosts (Mac, Windows, Linux, the future
Nexus Receiver). The protocol provides:

- **X25519 key exchange** for forward secrecy
- **ChaCha20-Poly1305** AEAD encryption
- **Length-prefixed framing** for streaming
- **Low-latency** input forwarding (< 2ms)

## Wire Format

Every frame on the wire is length-prefixed:

```
┌────────────┬────────┬───────────────────┐
│ length u32 │ type u8│      payload       │
│ (big-end)  │        │ (length - 1 bytes) │
└────────────┴────────┴───────────────────┘
```

- `length`: total bytes INCLUDING type byte
  but EXCLUDING the 4 length bytes themselves.
- `type`: frame type identifier.
- `payload`: frame-specific data.

## Frame Types

| ID  | Name          | Direction       | Payload |
|-----|---------------|-----------------|---------|
| 0x01| HELLO         | client→server   | 32-byte X25519 public key |
| 0x02| HELLO_ACK     | server→client   | 32-byte X25519 public key |
| 0x03| PIN_DIGIT     | client→server (enc) | 1 byte (digit 0-9) |
| 0x04| PAIR_OK       | server→client (enc) | 1 byte (0=fail, 1=ok) |
| 0x05| MOUSE_MOVE    | client→server (enc) | 8 bytes (float32 dx, dy) |
| 0x06| MOUSE_BUTTON  | client→server (enc) | 2 bytes (button, state) |
| 0x07| SCROLL        | client→server (enc) | 8 bytes (float32 dx, dy) |
| 0x08| KEY           | client→server (enc) | 9 bytes (action, hid_usage, modifiers) |
| 0x09| PINCH         | client→server (enc) | 4 bytes (float32 factor) |
| 0x0A| HEARTBEAT     | bidirectional   | 0 bytes |
| 0x0B| GOODBYE      | bidirectional   | 0 bytes |
| 0x0C| MEDIA         | client→server (enc) | 2 bytes (key, action) |

## Encryption

After PAIR_OK, all frames are encrypted with
ChaCha20-Poly1305 using a key derived from
the X25519 shared secret via HKDF-SHA256.

Each encrypted frame:
1. Generate a fresh 12-byte nonce.
2. Encrypt payload with ChaCha20-Poly1305.
3. Append the 16-byte authentication tag.
4. Wire format: `length | type | nonce (12) | ciphertext + tag`.

The `length` field measures the encrypted
payload (nonce + ciphertext + tag), so the
peer can stream frames without knowing the
plaintext size.

## Connection Lifecycle

```
Client                          Server
  │                               │
  │──── HELLO (pubkey) ──────────>│
  │<─── HELLO_ACK (pubkey) ──────│
  │                               │
  │  [X25519 key exchange]        │
  │  [HKDF-SHA256 → key]          │
  │                               │
  │──── PIN_DIGIT (encrypted) ───>│
  │──── PIN_DIGIT (encrypted) ───>│
  │──── PIN_DIGIT (encrypted) ───>│
  │──── PIN_DIGIT (encrypted) ───>│
  │                               │
  │<─── PAIR_OK (encrypted) ─────│
  │                               │
  │  [Encrypted session begins]   │
  │                               │
  │──── MOUSE_MOVE ──────────────>│
  │──── KEY ─────────────────────>│
  │──── SCROLL ──────────────────>│
  │                               │
  │<─── HEARTBEAT ───────────────│
  │──── HEARTBEAT ───────────────>│
  │                               │
  │──── GOODBYE ─────────────────>│
```

## Modifiers Bitmask

The KEY frame's modifier field mirrors macOS
CGEventFlags for Mac compatibility:

| Bit | Modifier |
|-----|----------|
| 1<<1 | Shift |
| 1<<18 | Control |
| 1<<19 | Option/Alt |
| 1<<20 | Command/Win |

## Maximum Frame Size

1,048,576 bytes (1 MB). Frames exceeding
this limit are rejected.

## Implementations

- **Android**: `MacTransport` in
  `core/transport/mac/`
- **Mac**: `ElysiumAgent` (Swift 6.2) in
  `macos-agent/`
- **Windows**: Phase 3
- **Linux**: Phase 3
