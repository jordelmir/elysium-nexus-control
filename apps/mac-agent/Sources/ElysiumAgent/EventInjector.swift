//
// Elysium Nexus — Mac Agent
// Professional CGEvent-based event injector.
// Synthesizes mouse, trackpad, scroll, pinch, keyboard,
// and media key events into macOS.
//
import Foundation
import CoreGraphics
import AppKit
import Carbon

final class EventInjector {
    static let shared = EventInjector()
    private let source: CGEventSource?
    private var isLeftMouseDown: Bool = false
    private var isRightMouseDown: Bool = false

    init() {
        // hidSystemState works across all macOS versions
        // with standard Accessibility permissions.
        self.source = CGEventSource(stateID: .hidSystemState)
    }

    // MARK: - Mouse & Trackpad Movement

    /// Sensitivity multiplier. Phone touch deltas are
    /// small (5-15px per frame) while Mac screens are
    /// large (2560×1440+). A multiplier of 4.5 makes
    /// the cursor feel responsive and 1:1 with a
    /// real trackpad.
    private let sensitivity: CGFloat = 4.5

    func moveBy(dx: Float, dy: Float) {
        guard let src = source else { return }
        let current = CGEvent(source: nil)?.location ?? .zero
        let screenBounds = CGDisplayBounds(CGMainDisplayID())
        let newPos = CGPoint(
            x: min(max(0, current.x + CGFloat(dx) * sensitivity), screenBounds.width - 1),
            y: min(max(0, current.y + CGFloat(dy) * sensitivity), screenBounds.height - 1)
        )
        
        let mouseType: CGEventType
        if isLeftMouseDown {
            mouseType = .leftMouseDragged
        } else if isRightMouseDown {
            mouseType = .rightMouseDragged
        } else {
            mouseType = .mouseMoved
        }

        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: mouseType,
            mouseCursorPosition: newPos,
            mouseButton: isRightMouseDown ? .right : .left
        ) else { return }
        
        ev.post(tap: .cghidEventTap)
    }

    /// Absolute mouse move to normalized coordinates (0.0 to 1.0)
    /// for direct touchscreen control on screen preview.
    func moveTo(normX: Float, normY: Float) {
        guard let src = source else { return }
        let screenBounds = CGDisplayBounds(CGMainDisplayID())
        let targetX = min(max(0, CGFloat(normX) * screenBounds.width), screenBounds.width - 1)
        let targetY = min(max(0, CGFloat(normY) * screenBounds.height), screenBounds.height - 1)
        let newPos = CGPoint(x: targetX, y: targetY)

        let mouseType: CGEventType
        if isLeftMouseDown {
            mouseType = .leftMouseDragged
        } else if isRightMouseDown {
            mouseType = .rightMouseDragged
        } else {
            mouseType = .mouseMoved
        }

        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: mouseType,
            mouseCursorPosition: newPos,
            mouseButton: isRightMouseDown ? .right : .left
        ) else { return }

        ev.post(tap: .cghidEventTap)
    }

    // MARK: - Mouse Clicks (Left / Right / Middle)

    func click(button: MouseButton, state: ButtonState) {
        guard let src = source else { return }
        let pos = CGEvent(source: nil)?.location ?? .zero
        let cgButton: CGMouseButton
        let eventType: CGEventType

        switch button {
        case .left:
            cgButton = .left
            isLeftMouseDown = (state == .down)
            eventType = (state == .down) ? .leftMouseDown : .leftMouseUp
        case .right:
            cgButton = .right
            isRightMouseDown = (state == .down)
            eventType = (state == .down) ? .rightMouseDown : .rightMouseUp
        case .middle:
            cgButton = .center
            eventType = (state == .down) ? .otherMouseDown : .otherMouseUp
        }

        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: eventType,
            mouseCursorPosition: pos,
            mouseButton: cgButton
        ) else { return }
        
        ev.post(tap: .cghidEventTap)
    }

    // MARK: - Trackpad Smooth Scroll

    func scroll(dx: Float, dy: Float) {
        guard let src = source else { return }
        let scaledDx = Int32(dx * 3)
        let scaledDy = Int32(dy * 3)

        guard let ev = CGEvent(
            scrollWheelEvent2Source: src,
            units: .pixel,
            wheelCount: 2,
            wheel1: scaledDy,
            wheel2: scaledDx,
            wheel3: 0
        ) else { return }
        
        ev.post(tap: .cghidEventTap)
    }

    // MARK: - Trackpad Pinch / Zoom

    func pinch(factor: Float) {
        guard factor > 0 else { return }
        let delta = Int32((factor - 1.0) * 15)
        guard delta != 0 else { return }
        guard let src = source else { return }

        guard let ev = CGEvent(
            scrollWheelEvent2Source: src,
            units: .pixel,
            wheelCount: 1,
            wheel1: delta,
            wheel2: 0,
            wheel3: 0
        ) else { return }

        ev.flags = .maskCommand
        ev.post(tap: .cghidEventTap)
    }

    // MARK: - Keyboard

    func key(action: KeyAction, hidUsage: UInt32, modifiers: Modifiers) {
        guard let src = source else { return }
        let keyDown = (action == .down || action == .repeat)

        var flags: CGEventFlags = []
        if modifiers.contains(.shift)   { flags.insert(.maskShift) }
        if modifiers.contains(.control) { flags.insert(.maskControl) }
        if modifiers.contains(.option)  { flags.insert(.maskAlternate) }
        if modifiers.contains(.command) { flags.insert(.maskCommand) }

        let keycode = hidUsageToKeycode(hidUsage: hidUsage)
        guard let ev = CGEvent(keyboardEventSource: src, virtualKey: CGKeyCode(keycode), keyDown: keyDown) else { return }
        
        ev.flags = flags
        ev.post(tap: .cghidEventTap)
    }

    // MARK: - Media Keys

    func media(_ type: MediaKey) {
        postMediaEvent(keyCode: type.rawValue, keyDown: true)
        usleep(15_000)
        postMediaEvent(keyCode: type.rawValue, keyDown: false)
    }

    private func postMediaEvent(keyCode: Int, keyDown: Bool) {
        let event = NSEvent.otherEvent(
            with: .systemDefined,
            location: .zero,
            modifierFlags: NSEvent.ModifierFlags(rawValue: 0),
            timestamp: 0,
            windowNumber: 0,
            context: nil,
            subtype: 8,
            data1: (keyCode << 16) | ((keyDown ? 0x0A : 0x0B) << 8),
            data2: -1
        )
        event?.cgEvent?.post(tap: .cghidEventTap)
    }

    // MARK: - Enums & Helpers

    enum MediaKey: Int {
        case volumeUp = 0
        case volumeDown = 1
        case brightnessUp = 2
        case brightnessDown = 3
        case mute = 7
        case playPause = 16
        case previous = 17
        case next = 18
    }

    private func hidUsageToKeycode(hidUsage: UInt32) -> UInt16 {
        switch hidUsage {
        // Letters A-Z
        case 0x04: return 0    // A
        case 0x05: return 11   // B
        case 0x06: return 8    // C
        case 0x07: return 2    // D
        case 0x08: return 14   // E
        case 0x09: return 3    // F
        case 0x0A: return 5    // G
        case 0x0B: return 4    // H
        case 0x0C: return 34   // I
        case 0x0D: return 38   // J
        case 0x0E: return 40   // K
        case 0x0F: return 37   // L
        case 0x10: return 46   // M
        case 0x11: return 45   // N
        case 0x12: return 31   // O
        case 0x13: return 35   // P
        case 0x14: return 12   // Q
        case 0x15: return 15   // R
        case 0x16: return 1    // S
        case 0x17: return 17   // T
        case 0x18: return 32   // U
        case 0x19: return 9    // V
        case 0x1A: return 13   // W
        case 0x1B: return 7    // X
        case 0x1C: return 16   // Y
        case 0x1D: return 6    // Z
        // Numbers 1-0
        case 0x1E: return 18   // 1
        case 0x1F: return 19   // 2
        case 0x20: return 20   // 3
        case 0x21: return 21   // 4
        case 0x22: return 23   // 5
        case 0x23: return 22   // 6
        case 0x24: return 26   // 7
        case 0x25: return 28   // 8
        case 0x26: return 25   // 9
        case 0x27: return 29   // 0
        // Actions & Controls
        case 0x28: return 36   // Return / Enter
        case 0x29: return 53   // Escape
        case 0x2A: return 51   // Delete / Backspace
        case 0x2B: return 48   // Tab
        case 0x2C: return 49   // Space
        case 0x2D: return 27   // - / _
        case 0x2E: return 24   // = / +
        case 0x2F: return 33   // [ / {
        case 0x30: return 30   // ] / }
        case 0x31: return 42   // \ / |
        case 0x33: return 41   // ; / : / Ñ
        case 0x34: return 39   // ' / " / Ç
        case 0x35: return 50   // ` / ~ / º
        case 0x36: return 43   // , / <
        case 0x37: return 47   // . / >
        case 0x38: return 44   // / / ?
        case 0x39: return 57   // Caps Lock
        // Function keys F1-F12
        case 0x3A: return 122  // F1
        case 0x3B: return 120  // F2
        case 0x3C: return 99   // F3
        case 0x3D: return 118  // F4
        case 0x3E: return 96   // F5
        case 0x3F: return 97   // F6
        case 0x40: return 98   // F7
        case 0x41: return 100  // F8
        case 0x42: return 101  // F9
        case 0x43: return 109  // F10
        case 0x44: return 103  // F11
        case 0x45: return 111  // F12
        // Arrows
        case 0x4F: return 124  // Right Arrow
        case 0x50: return 123  // Left Arrow
        case 0x51: return 125  // Down Arrow
        case 0x52: return 126  // Up Arrow
        // Non-US / ISO keys
        case 0x64: return 50   // Non-US \ and | or < >
        default:   return 49   // Fallback space
        }
    }
}
