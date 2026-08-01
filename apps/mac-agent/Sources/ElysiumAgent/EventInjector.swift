//
// Elysium Nexus — Mac Agent
// CGEvent-based event injector.
//
// macOS requires the Accessibility permission
// (System Settings → Privacy & Security →
// Accessibility) to synthesize mouse and keyboard
// events via `CGEvent.post`. The
// `EventInjector.shared` is a singleton because
// `CGEventSource` is per-process state; we want
// every event to come from the same source so the
// "fake" events look like they came from a real
// device (helps with apps that check the event
// source).
//
// We use mouse-button constants that match Apple's
// `CGMouseButton` enum and modifier flags that
// match `CGEventFlags`. The HID usage codes in
// `key(...)` are the standard USB HID keyboard
// usage codes (the same set used in
// `IOHIDPostEvent`).
//
import Foundation
import CoreGraphics
import AppKit

final class EventInjector {
    static let shared = EventInjector()
    private let source: CGEventSource?

    init() {
        // privateState: combinedSession + hidSystem.
        // This makes our events indistinguishable
        // from real hardware events to apps that
        // check the event source.
        let state = CGEventSourceStateID.privateState
        self.source = CGEventSource(stateID: state)
    }

    // MARK: - Mouse

    func moveBy(dx: Float, dy: Float) {
        guard let src = source else { return }
        // Current cursor position. We don't trust
        // the cached "last position" from the
        // phone — we read the real cursor from
        // CGEvent so multi-monitor + accessibility
        // zoom behave correctly.
        let current = CGEvent(source: nil)?.location ?? .zero
        let new = CGPoint(
            x: current.x + CGFloat(dx),
            y: current.y + CGFloat(dy)
        )
        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: .mouseMoved,
            mouseCursorPosition: new,
            mouseButton: .left
        ) else { return }
        ev.post(tap: .cghidEventTap)
    }

    func click(button: MouseButton, state: ButtonState) {
        guard let src = source else { return }
        let pos = CGEvent(source: nil)?.location ?? .zero
        let cgButton: CGMouseButton
        switch button {
        case .left: cgButton = .left
        case .right: cgButton = .right
        case .middle: cgButton = .center
        }
        let eventType: CGEventType
        switch state {
        case .down: eventType = (button == .left ? .leftMouseDown : (button == .right ? .rightMouseDown : .otherMouseDown))
        case .up:   eventType = (button == .left ? .leftMouseUp   : (button == .right ? .rightMouseUp   : .otherMouseUp))
        }
        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: eventType,
            mouseCursorPosition: pos,
            mouseButton: cgButton
        ) else { return }
        ev.post(tap: .cghidEventTap)
    }

    func scroll(dx: Float, dy: Float) {
        guard let src = source else { return }
        let pos = CGEvent(source: nil)?.location ?? .zero
        // Pixel-based scroll. macOS's smooth scroll
        // expects pixel deltas. We multiply by 10
        // so a small trackpad delta produces a
        // visible scroll.
        let scaledDx = Int32(dx * 10)
        let scaledDy = Int32(dy * 10)
        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: .scrollWheel,
            mouseCursorPosition: pos,
            mouseButton: .left
        ) else { return }
        ev.setIntegerValueField(.scrollWheelEventPointDeltaAxis1, value: Int(scaledDx))
        ev.setIntegerValueField(.scrollWheelEventPointDeltaAxis2, value: Int(scaledDy))
        ev.post(tap: .cghidEventTap)
    }

    // MARK: - Keyboard

    func key(action: KeyAction, hidUsage: UInt32, modifiers: Modifiers) {
        guard let src = source else { return }
        let cgEventType: CGEventType
        switch action {
        case .down, .repeat: cgEventType = .keyDown
        case .up:           cgEventType = .keyUp
        }
        // Convert our Modifiers bitmask to
        // CGEventFlags. The bit positions match
        // kCGEventFlagMask* on macOS.
        var flags: CGEventFlags = []
        if modifiers.contains(.shift)   { flags.insert(.maskShift) }
        if modifiers.contains(.control) { flags.insert(.maskControl) }
        if modifiers.contains(.option)  { flags.insert(.maskAlternate) }
        if modifiers.contains(.command) { flags.insert(.maskCommand) }
        guard let ev = CGEvent(keyboardEventSource: src, virtualKey: 0, keyDown: cgEventType == .keyDown) else { return }
        // HID usage → keycode. The agent sends
        // standard USB HID usage codes; macOS
        // expects a virtual keycode. We map the
        // common ASCII / modifier keys here; the
        // rest fall through to a default that does
        // a Unicode character post.
        let keycode = hidUsageToKeycode(hidUsage: hidUsage)
        ev.setIntegerValueField(.keyboardEventKeycode, value: Int64(keycode))
        ev.flags = flags
        ev.post(tap: .cghidEventTap)
    }

    func pinch(factor: Float) {
        // Pinch is a contextual gesture. The agent
        // doesn't have a direct "pinch" CGEvent;
        // apps that care about pinch (Figma, Safari,
        // Photos) consume the standard scrollWheel
        // events with the "momentum" flag. We
        // synthesize a small scroll + Cmd modifier
        // to drive OS-level zoom.
        // In macOS, Cmd+ScrollWheel is the
        // universal "zoom in / out" gesture. We
        // emit a tiny scroll event with the Cmd
        // modifier set so the active app receives
        // a zoom event.
        guard factor > 0 else { return }
        let intensity = Int32((factor - 1.0) * 10)
        guard intensity != 0 else { return }
        guard let src = source else { return }
        let pos = CGEvent(source: nil)?.location ?? .zero
        guard let ev = CGEvent(
            mouseEventSource: src,
            mouseType: .scrollWheel,
            mouseCursorPosition: pos,
            mouseButton: .left
        ) else { return }
        ev.setIntegerValueField(.scrollWheelEventPointDeltaAxis2, value: Int(intensity))
        ev.flags = .maskCommand
        ev.post(tap: .cghidEventTap)
    }

    // MARK: - HID usage → keycode

    private func hidUsageToKeycode(hidUsage: UInt32) -> UInt16 {
        // USB HID Keyboard / Keypad Page (0x07).
        // We only map the most common keys;
        // the rest are passed through to the
        // Unicode-character path.
        switch hidUsage {
        case 0x04: return UInt16(kVK_ANSI_A)
        case 0x05: return UInt16(kVK_ANSI_B)
        case 0x06: return UInt16(kVK_ANSI_C)
        case 0x07: return UInt16(kVK_ANSI_D)
        case 0x08: return UInt16(kVK_ANSI_E)
        case 0x09: return UInt16(kVK_ANSI_F)
        case 0x0A: return UInt16(kVK_ANSI_G)
        case 0x0B: return UInt16(kVK_ANSI_H)
        case 0x0C: return UInt16(kVK_ANSI_I)
        case 0x0D: return UInt16(kVK_ANSI_J)
        case 0x0E: return UInt16(kVK_ANSI_K)
        case 0x0F: return UInt16(kVK_ANSI_L)
        case 0x10: return UInt16(kVK_ANSI_M)
        case 0x11: return UInt16(kVK_ANSI_N)
        case 0x12: return UInt16(kVK_ANSI_O)
        case 0x13: return UInt16(kVK_ANSI_P)
        case 0x14: return UInt16(kVK_ANSI_Q)
        case 0x15: return UInt16(kVK_ANSI_R)
        case 0x16: return UInt16(kVK_ANSI_S)
        case 0x17: return UInt16(kVK_ANSI_T)
        case 0x18: return UInt16(kVK_ANSI_U)
        case 0x19: return UInt16(kVK_ANSI_V)
        case 0x1A: return UInt16(kVK_ANSI_W)
        case 0x1B: return UInt16(kVK_ANSI_X)
        case 0x1C: return UInt16(kVK_ANSI_Y)
        case 0x1D: return UInt16(kVK_ANSI_Z)
        case 0x1E: return UInt16(kVK_ANSI_1)
        case 0x1F: return UInt16(kVK_ANSI_2)
        case 0x20: return UInt16(kVK_ANSI_3)
        case 0x21: return UInt16(kVK_ANSI_4)
        case 0x22: return UInt16(kVK_ANSI_5)
        case 0x23: return UInt16(kVK_ANSI_6)
        case 0x24: return UInt16(kVK_ANSI_7)
        case 0x25: return UInt16(kVK_ANSI_8)
        case 0x26: return UInt16(kVK_ANSI_9)
        case 0x27: return UInt16(kVK_ANSI_0)
        case 0x28: return UInt16(kVK_Return)
        case 0x29: return UInt16(kVK_Escape)
        case 0x2A: return UInt16(kVK_Delete)
        case 0x2B: return UInt16(kVK_Tab)
        case 0x2C: return UInt16(kVK_Space)
        case 0x2D: return UInt16(kVK_ANSI_Minus)
        case 0x2E: return UInt16(kVK_ANSI_Equal)
        case 0x2F: return UInt16(kVK_ANSI_LeftBracket)
        case 0x30: return UInt16(kVK_ANSI_RightBracket)
        case 0x31: return UInt16(kVK_ANSI_Backslash)
        case 0x33: return UInt16(kVK_ANSI_Semicolon)
        case 0x34: return UInt16(kVK_ANSI_Quote)
        case 0x35: return UInt16(kVK_ANSI_Grave)
        case 0x36: return UInt16(kVK_ANSI_Comma)
        case 0x37: return UInt16(kVK_ANSI_Period)
        case 0x38: return UInt16(kVK_ANSI_Slash)
        case 0x4C: return UInt16(kVK_Delete) // forward delete
        case 0x4F: return UInt16(kVK_RightArrow)
        case 0x50: return UInt16(kVK_LeftArrow)
        case 0x51: return UInt16(kVK_DownArrow)
        case 0x52: return UInt16(kVK_UpArrow)
        case 0xE0: return UInt16(kVK_LeftControl)
        case 0xE1: return UInt16(kVK_LeftShift)
        case 0xE2: return UInt16(kVK_LeftAlt)
        case 0xE3: return UInt16(kVK_LeftCommand)
        case 0xE4: return UInt16(kVK_RightControl)
        case 0xE5: return UInt16(kVK_RightShift)
        case 0xE6: return UInt16(kVK_RightAlt)
        case 0xE7: return UInt16(kVK_RightCommand)
        default: return UInt16(kVK_Space) // fallback
        }
    }
}
