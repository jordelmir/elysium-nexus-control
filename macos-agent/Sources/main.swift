import Foundation
import CoreGraphics
import IOKit
import IOKit.usb

// MARK: - Wire format tags (must match UsbHidTransport.kt)
let TAG_MOUSE_MOVE: UInt8     = 0x01
let TAG_MOUSE_BUTTON: UInt8   = 0x02
let TAG_MOUSE_SCROLL: UInt8   = 0x03
let TAG_KEYBOARD: UInt8       = 0x04
let TAG_TOUCHPAD_MOVE: UInt8  = 0x05
let TAG_TOUCHPAD_CLICK: UInt8 = 0x06
let TAG_TOUCHPAD_SCROLL: UInt8 = 0x07
let TAG_GAMEPAD_STATE: UInt8  = 0x10
let TAG_PING: UInt8           = 0xFE
let TAG_RELEASE_ALL: UInt8    = 0xFF

// MARK: - HID usage codes for common keys
enum HIDKey: UInt8 {
    case a = 0x04, b, c, d, e, f, g, h, i, j, k, l, m
    case n, o, p, q, r, s, t, u, v, w, x, y, z
    case k1 = 0x1E, k2, k3, k4, k5, k6, k7, k8, k9, k0
    case enter = 0x28, escape, backspace, tab, space
    case minus = 0x2D, equal, lBracket, rBracket, backslash
    case semicolon = 0x33, quote, grave, comma, period, slash
    case capsLock = 0x39
    case f1 = 0x3A, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12
    case printScreen = 0x46, scrollLock, pause
    case insert = 0x49, home, pageUp, deleteKey, end, pageDown
    case right = 0x4F, left, down, up
    case lCtrl = 0xE0, lShift, lAlt, lGui
    case rCtrl = 0xE4, rShift, rAlt, rGui
}

/// Maps HID usage code to macOS virtual keycode.
/// Returns nil for unmapped keys.
func hidToMacKeyCode(_ hid: UInt8) -> CGKeyCode? {
    let map: [UInt8: CGKeyCode] = [
        0x04: 0,    // a
        0x05: 11,   // b
        0x06: 8,    // c
        0x07: 2,    // d
        0x08: 14,   // e
        0x09: 3,    // f
        0x0A: 5,    // g
        0x0B: 4,    // h
        0x0C: 34,   // i
        0x0D: 38,   // j
        0x0E: 40,   // k
        0x0F: 37,   // l
        0x10: 46,   // m
        0x11: 45,   // n
        0x12: 31,   // o
        0x13: 35,   // p
        0x14: 12,   // q
        0x15: 15,   // r
        0x16: 1,    // s
        0x17: 17,   // t
        0x18: 32,   // u
        0x19: 9,    // v
        0x1A: 13,   // w
        0x1B: 7,    // x
        0x1C: 16,   // y
        0x1D: 6,    // z
        0x1E: 18,   // 1
        0x1F: 19,   // 2
        0x20: 20,   // 3
        0x21: 21,   // 4
        0x22: 23,   // 5
        0x23: 22,   // 6
        0x24: 26,   // 7
        0x25: 28,   // 8
        0x26: 25,   // 9
        0x27: 29,   // 0
        0x28: 36,   // enter
        0x29: 53,   // escape
        0x2A: 51,   // backspace
        0x2B: 48,   // tab
        0x2C: 49,   // space
        0x2D: 27,   // minus
        0x2E: 24,   // equal
        0x2F: 33,   // [
        0x30: 30,   // ]
        0x31: 42,   // backslash
        0x33: 41,   // ;
        0x34: 39,   // '
        0x35: 50,   // `
        0x36: 43,   // ,
        0x37: 47,   // .
        0x38: 44,   // /
        0x39: 57,   // capslock
        0x3A: 122,  // f1
        0x3B: 120,  // f2
        0x3C: 99,   // f3
        0x3D: 118,  // f4
        0x3E: 96,   // f5
        0x3F: 97,   // f6
        0x40: 98,   // f7
        0x41: 100,  // f8
        0x42: 101,  // f9
        0x43: 109,  // f10
        0x44: 103,  // f11
        0x45: 111,  // f12
        0x4F: 124,  // right
        0x50: 123,  // left
        0x51: 125,  // down
        0x52: 126,  // up
        0xE0: 59,   // lctrl
        0xE1: 56,   // lshift
        0xE2: 58,   // lalt
        0xE3: 55,   // lcmd
        0xE4: 62,   // rctrl
        0xE5: 60,   // rshift
        0xE6: 61,   // ralt
        0xE7: 54,   // rcmd
    ]
    return map[hid]
}

// MARK: - Event injection

func postMouseEvent(type: CGEventType, point: CGEventPoint, button: CGMouseButton = .left) {
    let event = CGEvent(
        mouseEventSource: nil,
        mouseType: type,
        mouseCursorPosition: point,
        mouseButton: button
    )
    event?.post(tap: .cghidEventTap)
}

func postKeyEvent(keyCode: CGKeyCode, keyDown: Bool) {
    let event = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: keyDown)
    event?.post(tap: .cghidEventTap)
}

func postScrollEvent(dy: Int32) {
    let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 1, wheel1: dy, wheel2: 0, wheel3: 0)
    event?.post(tap: .cghidEventTap)
}

// MARK: - USB serial reading (CDC ACM)

/// Read from a serial device (USB CDC ACM) and process HID reports.
/// The Android phone presents as /dev/tty.usbmodem* when connected via USB.
func findAndOpenAndroidUSB() -> Int32 {
    // Scan for USB serial devices.
    let candidates = [
        "/dev/tty.usbmodem1101",
        "/dev/tty.usbmodem1103",
        "/dev/tty.usbmodem1102",
        "/dev/tty.usbmodem1201",
        "/dev/tty.usbmodem1301",
    ]
    for path in candidates {
        let fd = open(path, O_RDWR | O_NOCTTY | O_NONBLOCK)
        if fd >= 0 {
            print("[agent] Opened USB device: \(path)")
            return fd
        }
    }
    // Fallback: scan /dev for any tty.usbmodem*
    if let files = try? FileManager.default.contentsOfDirectory(atPath: "/dev") {
        for file in files.sorted() {
            if file.hasPrefix("tty.usbmodem") {
                let path = "/dev/\(file)"
                let fd = open(path, O_RDWR | O_NOCTTY | O_NONBLOCK)
                if fd >= 0 {
                    print("[agent] Opened USB device: \(path)")
                    return fd
                }
            }
        }
    }
    return -1
}

func configureSerial(_ fd: Int32) {
    var tty = termios()
    tcgetattr(fd, &tty)
    // 115200 baud, 8N1, no flow control.
    cfsetspeed(&tty, B115200)
    tty.c_cflag = UInt(CS8 | CREAD | CLOCAL)
    tty.c_iflag = 0
    tty.c_oflag = 0
    tty.c_lflag = 0
    tty.c_cc.16 = 1  // VMIN
    tty.c_cc.17 = 0  // VTIME
    tcflush(fd, TCIOFLUSH)
    tcsetattr(fd, TCSANOW, &tty)
}

// MARK: - Main loop

var mouseLocation = CGPoint(x: 960, y: 540)

func processFrame(_ data: [UInt8]) {
    guard let tag = data.first else { return }
    switch tag {
    case TAG_MOUSE_MOVE:
        guard data.count >= 5 else { return }
        let dx = Int16(bitPattern: UInt16(data[1]) | UInt16(data[2]) << 8)
        let dy = Int16(bitPattern: UInt16(data[3]) | UInt16(data[4]) << 8)
        mouseLocation.x += CGFloat(dx)
        mouseLocation.y += CGFloat(dy)
        mouseLocation.x = max(0, min(mouseLocation.x, CGFloat(CGDisplayPixelsWide(CGMainDisplayID()))))
        mouseLocation.y = max(0, min(mouseLocation.y, CGFloat(CGDisplayPixelsHigh(CGMainDisplayID()))))
        postMouseEvent(type: .mouseMoved, point: mouseLocation)

    case TAG_MOUSE_BUTTON:
        guard data.count >= 3 else { return }
        let button: CGMouseButton = data[1] == 0 ? .left : data[1] == 1 ? .right : .center
        let pressed = data[2] != 0
        let type: CGEventType = pressed ? .leftMouseDown : .leftMouseUp
        postMouseEvent(type: pressed ?
            (data[1] == 0 ? .leftMouseDown : data[1] == 1 ? .rightMouseDown : .otherMouseDown) :
            (data[1] == 0 ? .leftMouseUp : data[1] == 1 ? .rightMouseUp : .otherMouseUp),
            point: mouseLocation, button: button)

    case TAG_MOUSE_SCROLL:
        guard data.count >= 3 else { return }
        let dy = Int16(bitPattern: UInt16(data[1]) | UInt16(data[2]) << 8)
        postScrollEvent(dy: Int32(dy))

    case TAG_KEYBOARD:
        guard data.count >= 3 else { return }
        let hidKey = data[1]
        let pressed = data[2] != 0
        if let macKey = hidToMacKeyCode(hidKey) {
            postKeyEvent(keyCode: macKey, keyDown: pressed)
        }

    case TAG_TOUCHPAD_MOVE:
        guard data.count >= 6 else { return }
        let x = Int16(bitPattern: UInt16(data[1]) | UInt16(data[2]) << 8)
        let y = Int16(bitPattern: UInt16(data[3]) | UInt16(data[4]) << 8)
        mouseLocation.x = CGFloat(x)
        mouseLocation.y = CGFloat(y)
        postMouseEvent(type: .mouseMoved, point: mouseLocation)

    case TAG_TOUCHPAD_SCROLL:
        guard data.count >= 3 else { return }
        let dy = Int16(bitPattern: UInt16(data[1]) | UInt16(data[2]) << 8)
        postScrollEvent(dy: Int32(dy))

    case TAG_GAMEPAD_STATE:
        // Gamepad → keyboard mapping (Phase 3+).
        break

    case TAG_PING:
        print("[agent] Ping received — connection alive")

    case TAG_RELEASE_ALL:
        // Release all keys and mouse buttons.
        for key: UInt8 in 0x04...0xE7 {
            if let macKey = hidToMacKeyCode(key) {
                postKeyEvent(keyCode: macKey, keyDown: false)
            }
        }
        postMouseEvent(type: .leftMouseUp, point: mouseLocation, button: .left)
        postMouseEvent(type: .rightMouseUp, point: mouseLocation, button: .right)
        postMouseEvent(type: .otherMouseUp, point: mouseLocation, button: .center)
        print("[agent] All inputs released")

    default:
        break
    }
}

// MARK: - Entry point

print("╔══════════════════════════════════════╗")
print("║  Elysium Nexus USB Agent v1.0        ║")
print("║  Latency: < 2ms (USB direct)         ║")
print("╚══════════════════════════════════════╝")
print("")
print("Waiting for Android device via USB-C...")

while true {
    let fd = findAndOpenAndroidUSB()
    if fd < 0 {
        sleep(1)
        continue
    }
    configureSerial(fd)
    print("[agent] Connected! Reading HID reports...")

    var buffer = [UInt8](repeating: 0, count: 64)
    var frameBuffer = [UInt8]()
    var frameLen = 0

    while true {
        var readfds = fd_set()
        FD_ZERO(&readfds)
        FD_SET(fd, &readfds)
        var timeout = timeval(tv_sec: 0, tv_usec: 100_000) // 100ms timeout
        let ready = select(fd + 1, &readfds, nil, nil, &timeout)
        if ready < 0 {
            print("[agent] Select error, reconnecting...")
            close(fd)
            break
        }
        if ready == 0 { continue } // timeout, retry

        let n = read(fd, &buffer, 64)
        if n <= 0 {
            print("[agent] Device disconnected, waiting for reconnection...")
            close(fd)
            break
        }

        // Simple framing: collect bytes until we have a complete frame.
        for i in 0..<n {
            let byte = buffer[i]
            if byte >= 0x01 && byte <= 0x10 || byte >= 0xFE {
                // Start of a new frame.
                if !frameBuffer.isEmpty && frameLen > 0 {
                    processFrame(Array(frameBuffer.prefix(frameLen)))
                }
                frameBuffer = [byte]
                frameLen = 1
                // Determine expected frame length.
                let expectedLen: Int
                switch byte {
                case TAG_MOUSE_MOVE: expectedLen = 5
                case TAG_MOUSE_BUTTON: expectedLen = 3
                case TAG_MOUSE_SCROLL: expectedLen = 3
                case TAG_KEYBOARD: expectedLen = 3
                case TAG_TOUCHPAD_MOVE: expectedLen = 6
                case TAG_TOUCHPAD_CLICK: expectedLen = 3
                case TAG_TOUCHPAD_SCROLL: expectedLen = 3
                case TAG_GAMEPAD_STATE: expectedLen = 16
                case TAG_PING, TAG_RELEASE_ALL: expectedLen = 1
                default: expectedLen = 1
                }
                if frameLen >= expectedLen {
                    processFrame(frameBuffer)
                    frameBuffer = []
                    frameLen = 0
                }
            } else if frameLen > 0 && frameLen < 64 {
                frameBuffer.append(byte)
                frameLen += 1
            }
        }
    }
}
