import Foundation
import CoreGraphics

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

// MARK: - Event injection

func postMouseEvent(type: CGEventType, point: CGPoint, button: CGMouseButton = .left) {
    let event = CGEvent(
        mouseEventSource: nil,
        mouseType: type,
        mouseCursorPosition: point,
        mouseButton: button
    )
    event?.post(tap: CGEventTapLocation.cghidEventTap)
}

func postKeyEvent(keyCode: CGKeyCode, keyDown: Bool) {
    let event = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: keyDown)
    event?.post(tap: CGEventTapLocation.cghidEventTap)
}

func postScrollEvent(dy: Int32) {
    let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 1, wheel1: dy, wheel2: 0, wheel3: 0)
    event?.post(tap: CGEventTapLocation.cghidEventTap)
}

// MARK: - HID usage code → macOS virtual keycode

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

// MARK: - USB serial reading (CDC ACM)

func findAndOpenAndroidUSB() -> Int32 {
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
    cfsetspeed(&tty, speed_t(B115200))
    tty.c_cflag = UInt(CS8 | CREAD | CLOCAL)
    tty.c_iflag = 0
    tty.c_oflag = 0
    tty.c_lflag = 0
    tty.c_cc.16 = 1  // VMIN
    tty.c_cc.17 = 0  // VTIME
    tcflush(fd, TCIOFLUSH)
    tcsetattr(fd, TCSANOW, &tty)
}

// MARK: - Frame processing

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
        let screenW = CGDisplayPixelsWide(CGMainDisplayID())
        let screenH = CGDisplayPixelsHigh(CGMainDisplayID())
        mouseLocation.x = max(0, min(mouseLocation.x, CGFloat(screenW)))
        mouseLocation.y = max(0, min(mouseLocation.y, CGFloat(screenH)))
        postMouseEvent(type: CGEventType.mouseMoved, point: mouseLocation)

    case TAG_MOUSE_BUTTON:
        guard data.count >= 3 else { return }
        let pressed = data[2] != 0
        let type: CGEventType
        let button: CGMouseButton
        switch data[1] {
        case 0:
            type = pressed ? CGEventType.leftMouseDown : CGEventType.leftMouseUp
            button = CGMouseButton.left
        case 1:
            type = pressed ? CGEventType.rightMouseDown : CGEventType.rightMouseUp
            button = CGMouseButton.right
        default:
            type = pressed ? CGEventType.otherMouseDown : CGEventType.otherMouseUp
            button = CGMouseButton.center
        }
        postMouseEvent(type: type, point: mouseLocation, button: button)

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
        postMouseEvent(type: CGEventType.mouseMoved, point: mouseLocation)

    case TAG_TOUCHPAD_SCROLL:
        guard data.count >= 3 else { return }
        let dy = Int16(bitPattern: UInt16(data[1]) | UInt16(data[2]) << 8)
        postScrollEvent(dy: Int32(dy))

    case TAG_GAMEPAD_STATE:
        break

    case TAG_PING:
        print("[agent] Ping received — connection alive")

    case TAG_RELEASE_ALL:
        for key: UInt8 in 0x04...0xE7 {
            if let macKey = hidToMacKeyCode(key) {
                postKeyEvent(keyCode: macKey, keyDown: false)
            }
        }
        postMouseEvent(type: CGEventType.leftMouseUp, point: mouseLocation, button: CGMouseButton.left)
        postMouseEvent(type: CGEventType.rightMouseUp, point: mouseLocation, button: CGMouseButton.right)
        postMouseEvent(type: CGEventType.otherMouseUp, point: mouseLocation, button: CGMouseButton.center)
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
        withUnsafeMutablePointer(to: &readfds) { ptr in
            ptr.withMemoryRebound(to: Int32.self, capacity: Int(FD_SETSIZE)) { raw in
                for i in 0..<Int(FD_SETSIZE) { raw[i] = 0 }
            }
        }
        let fdInt = Int32(fd)
        withUnsafeMutablePointer(to: &readfds) { ptr in
            ptr.withMemoryRebound(to: Int32.self, capacity: Int(FD_SETSIZE)) { raw in
                raw[Int(fd / 32)] |= 1 << (fd % 32)
            }
        }
        var timeout = timeval(tv_sec: 0, tv_usec: 100_000)
        let ready = select(fdInt + 1, &readfds, nil, nil, &timeout)
        if ready < 0 {
            print("[agent] Select error, reconnecting...")
            close(fdInt)
            break
        }
        if ready == 0 { continue }

        let n = read(fdInt, &buffer, 64)
        if n <= 0 {
            print("[agent] Device disconnected, waiting for reconnection...")
            close(fdInt)
            break
        }

        for i in 0..<n {
            let byte = buffer[i]
            if (byte >= 0x01 && byte <= 0x10) || byte >= 0xFE {
                if !frameBuffer.isEmpty && frameLen > 0 {
                    processFrame(Array(frameBuffer.prefix(frameLen)))
                }
                frameBuffer = [byte]
                frameLen = 1
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
