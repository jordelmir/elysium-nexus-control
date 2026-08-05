import Foundation
import IOKit
import IOKit.hid
import AppKit

/**
 * Lightweight USB-C HID Daemon for Mac Agent.
 *
 * Listens for incoming bulk transfers / USB HID events from connected Android phones
 * over USB-C and injects them directly as native macOS CGEvents.
 */
public final class USBDaemon {
    private var manager: IOHIDManager?
    private var isRunning = false

    public init() {}

    public func start() {
        guard !isRunning else { return }
        isRunning = true
        Log.info("USBDaemon — Starting USB HID listener on macOS...")

        manager = IOHIDManagerCreate(kCFAllocatorDefault, IOOptionBits(kIOHIDOptionsTypeNone))
        guard let manager = manager else {
            Log.error("Failed to create IOHIDManager")
            return
        }

        IOHIDManagerSetDeviceMatching(manager, nil)
        let context = UnsafeMutableRawPointer(Unmanaged.passUnretained(self).toOpaque())

        let inputCallback: IOHIDValueCallback = { context, result, sender, value in
            guard let context = context else { return }
            let daemon = Unmanaged<USBDaemon>.fromOpaque(context).takeUnretainedValue()
            daemon.handleHIDValue(value)
        }

        IOHIDManagerRegisterInputValueCallback(manager, inputCallback, context)
        IOHIDManagerScheduleWithRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.defaultMode.rawValue)
        IOHIDManagerOpen(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        Log.info("USBDaemon — Active and listening for USB-C HID events (<1ms latency)")
    }

    public func stop() {
        guard isRunning, let manager = manager else { return }
        IOHIDManagerUnscheduleFromRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.defaultMode.rawValue)
        IOHIDManagerClose(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        self.manager = nil
        isRunning = false
        Log.info("USBDaemon — Stopped")
    }

    private func handleHIDValue(_ value: IOHIDValue) {
        let element = IOHIDValueGetElement(value)
        let usagePage = IOHIDElementGetUsagePage(element)
        let usage = IOHIDElementGetUsage(element)
        let intVal = IOHIDValueGetIntegerValue(value)

        // Map USB Generic Desktop / Pointer / Mouse / Keyboard usages
        if usagePage == 0x01 { // Generic Desktop Page
            if usage == 0x30 { // X axis
                injectRelativeMouseMove(dx: CGFloat(intVal), dy: 0)
            } else if usage == 0x31 { // Y axis
                injectRelativeMouseMove(dx: 0, dy: CGFloat(intVal))
            }
        } else if usagePage == 0x09 { // Button Page
            let button = Int(usage)
            let isPressed = intVal != 0
            injectMouseButton(button: button, pressed: isPressed)
        }
    }

    private func injectRelativeMouseMove(dx: CGFloat, dy: CGFloat) {
        let loc = CGEvent(source: nil)?.location ?? .zero
        let newLoc = CGPoint(x: loc.x + dx, y: loc.y + dy)
        if let event = CGEvent(mouseEventSource: nil, mouseType: .mouseMoved, mouseCursorPosition: newLoc, mouseButton: .left) {
            event.post(tap: .cghidEventTap)
        }
    }

    private func injectMouseButton(button: Int, pressed: Bool) {
        let loc = CGEvent(source: nil)?.location ?? .zero
        let type: CGEventType = (button == 1) ? (pressed ? .leftMouseDown : .leftMouseUp) : (pressed ? .rightMouseDown : .rightMouseUp)
        let btn: CGMouseButton = (button == 1) ? .left : .right
        if let event = CGEvent(mouseEventSource: nil, mouseType: type, mouseCursorPosition: loc, mouseButton: btn) {
            event.post(tap: .cghidEventTap)
        }
    }
}
