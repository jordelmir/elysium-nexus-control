//
// Elysium Nexus — Mac Agent
// Binary protocol shared with the Android app.
//
// Wire format
// -----------
// Every frame on the wire is length-prefixed:
//
//   ┌────────────┬────────┬───────────────────┐
//   │ length u32 │ type u8│      payload       │
//   │ (big-end)  │        │ (length - 1 bytes) │
//   └────────────┴────────┴───────────────────┘
//
// `length` is the total number of bytes in the
// frame INCLUDING the type byte but EXCLUDING
// itself. So a one-byte payload has length=1.
//
// Frame types
// -----------
// 0x01  HELLO          client → server
//                      payload: 32-byte X25519 public key
// 0x02  HELLO_ACK      server → client
//                      payload: 32-byte X25519 public key
// 0x03  PIN_DIGIT      client → server (encrypted)
//                      payload: 1 byte (digit 0-9)
// 0x04  PAIR_OK        server → client (encrypted)
//                      payload: 1 byte (0=fail, 1=ok)
// 0x05  MOUSE_MOVE     client → server (encrypted)
//                      payload: 8 bytes (float32 dx, float32 dy, normalized)
// 0x06  MOUSE_BUTTON   client → server (encrypted)
//                      payload: 2 bytes (button u8, state u8)
// 0x07  SCROLL         client → server (encrypted)
//                      payload: 8 bytes (float32 dx, float32 dy)
// 0x08  KEY            client → server (encrypted)
//                      payload: 9 bytes (action u8, hid_usage u32, modifiers u32)
// 0x09  PINCH          client → server (encrypted)
//                      payload: 4 bytes (float32 factor)
// 0x0A  HEARTBEAT      bidirectional
//                      payload: 0 bytes
// 0x0B  GOODBYE        bidirectional
//                      payload: 0 bytes
//
// The encryption (after PAIR_OK) is ChaCha20-Poly1305
// with a key derived from the X25519 shared secret via
// HKDF-SHA256. Each frame uses a fresh 12-byte nonce
// concatenated to the ciphertext. The encrypted
// payload is what `length` measures on the wire, so
// the peer can stream frames without knowing the
// plaintext size.
//
import Foundation

enum FrameType: UInt8 {
    case hello       = 0x01
    case helloAck    = 0x02
    case pinDigit    = 0x03
    case pairOk      = 0x04
    case mouseMove   = 0x05
    case mouseButton = 0x06
    case scroll      = 0x07
    case key         = 0x08
    case pinch       = 0x09
    case heartbeat   = 0x0A
    case goodbye     = 0x0B
    // Phase ULT.7 — media key (volume /
    // play / pause / next / previous). The
    // 1-byte payload is the macOS media key
    // code (0, 1, 7, 16, 17, 18).
    case media       = 0x0C
    // Phase ULT.9 — screen mirroring. The Mac
    // captures the screen as JPEG and streams
    // it as SCREEN_FRAME to the Android. The
    // Android can request start/stop via
    // SCREEN_REQUEST.
    case screenRequest = 0x0D  // client → server: 1 byte (0=stop, 1=start, 2=quality)
    case screenFrame   = 0x0E  // server → client: JPEG bytes (unencrypted for perf)
    case mouseAbsMove  = 0x0F  // client → server: 8 bytes (float32 normX, float32 normY)
}

enum MouseButton: UInt8 {
    case left = 0
    case right = 1
    case middle = 2
}

enum ButtonState: UInt8 {
    case up = 0
    case down = 1
}

enum KeyAction: UInt8 {
    case down = 0
    case up = 1
    case `repeat` = 2
}

//
// Modifiers bitmask. Mirrors the macOS CGEventFlags.
//
struct Modifiers: OptionSet, Equatable {
    let rawValue: UInt32
    static let none     = Modifiers([])
    static let shift    = Modifiers(rawValue: 1 << 1)   // kCGEventFlagMaskShift
    static let control  = Modifiers(rawValue: 1 << 18)  // kCGEventFlagMaskControl
    static let option   = Modifiers(rawValue: 1 << 19)  // kCGEventFlagMaskAlternate
    static let command  = Modifiers(rawValue: 1 << 20)  // kCGEventFlagMaskCommand
}

//
// A decoded frame.
//
struct Frame {
    let type: FrameType
    /// The plaintext payload. Empty for heartbeat / goodbye.
    let payload: Data
}

//
// Reads length-prefixed frames from a continuous
// `Data` stream. Returns the next frame + the
// remaining unconsumed bytes, or `nil` if there
// isn't enough data yet.
//
enum FrameParser {
    /// The maximum frame size we accept. Anything
    /// larger is treated as a protocol error. 1 MB
    /// is far more than a single mouse-move or key
    /// event; even a string of typed text at 1000
    /// wpm over 1 hour is < 5 MB.
    static let maxFrameSize: Int = 1_048_576

    static func read(from buffer: inout Data) -> Frame? {
        // Need at least 4 bytes for the length.
        guard buffer.count >= 4 else { return nil }
        let lengthBytes = buffer.prefix(4)
        let length = lengthBytes.withUnsafeBytes { ptr -> UInt32 in
            let b = ptr.bindMemory(to: UInt8.self)
            return (UInt32(b[0]) << 24) | (UInt32(b[1]) << 16) | (UInt32(b[2]) << 8) | UInt32(b[3])
        }
        let totalLength = Int(length) + 4
        // Sanity: reject obviously-wrong lengths.
        guard length >= 1, Int(length) <= maxFrameSize else {
            Log.warn("FrameParser: refusing frame of length \(length) (max \(maxFrameSize))")
            return nil
        }
        // Need enough bytes for the full frame.
        guard buffer.count >= totalLength else { return nil }
        let typeByte = buffer[4]
        let payload = buffer.subdata(in: 5..<totalLength)
        buffer.removeSubrange(0..<totalLength)
        guard let type = FrameType(rawValue: typeByte) else {
            Log.warn("FrameParser: unknown frame type \(typeByte)")
            return nil
        }
        return Frame(type: type, payload: payload)
    }
}

//
// Encodes a frame for the wire.
//
enum FrameEncoder {
    static func encode(_ type: FrameType, payload: Data = Data()) -> Data {
        let length = UInt32(1 + payload.count) // type + payload
        var out = Data(capacity: 4 + Int(length))
        // Big-endian length.
        out.append(UInt8((length >> 24) & 0xFF))
        out.append(UInt8((length >> 16) & 0xFF))
        out.append(UInt8((length >>  8) & 0xFF))
        out.append(UInt8((length      ) & 0xFF))
        out.append(type.rawValue)
        out.append(payload)
        return out
    }
}

//
// Encoders for typed payloads. The server and the
// Android client share these encodings. The order
// is big-endian.
//
enum PayloadEncoder {
    static func mouseMove(dx: Float, dy: Float) -> Data {
        var d = Data()
        d.append(Float32(dx).bigEndianData)
        d.append(Float32(dy).bigEndianData)
        return d
    }
    static func mouseButton(button: MouseButton, state: ButtonState) -> Data {
        return Data([button.rawValue, state.rawValue])
    }
    static func scroll(dx: Float, dy: Float) -> Data {
        return mouseMove(dx: dx, dy: dy)
    }
    static func key(action: KeyAction, hidUsage: UInt32, modifiers: Modifiers) -> Data {
        var d = Data()
        d.append(action.rawValue)
        d.append(UInt32(hidUsage).bigEndianData)
        d.append(UInt32(modifiers.rawValue).bigEndianData)
        return d
    }
    static func pinch(factor: Float) -> Data {
        return Float32(factor).bigEndianData
    }
    static func pinDigit(_ digit: UInt8) -> Data {
        precondition(digit < 10, "PIN digit must be 0-9")
        return Data([digit])
    }
}

extension Float32 {
    /// Big-endian bytes.
    var bigEndianData: Data {
        var v = self.bitPattern.bigEndian
        return Data(bytes: &v, count: MemoryLayout<UInt32>.size)
    }
}

extension UInt32 {
    /// Big-endian bytes.
    var bigEndianData: Data {
        var v = self.bigEndian
        return Data(bytes: &v, count: MemoryLayout<UInt32>.size)
    }
}
