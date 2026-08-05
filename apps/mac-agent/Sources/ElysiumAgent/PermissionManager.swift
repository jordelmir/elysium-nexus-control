//
// Elysium Nexus — Mac Agent
// Permission helpers. macOS requires the user to
// grant Accessibility (Privacy & Security →
// Accessibility) before the agent can synthesize
// events via CGEvent.post. We check both
// permissions and warn early if they're missing.
//
import Foundation
import ApplicationServices
import IOKit.hid
import AppKit

final class PermissionManager {
    /// `true` if the agent has Accessibility
    /// permission. We use the
    /// `AXIsProcessTrustedWithOptions` API
    /// (deprecated since macOS 10.9 but still the
    /// canonical way to check).
    func hasAccessibility() -> Bool {
        // AXIsProcessTrusted returns true if the
        // app is in the Accessibility allow-list.
        return AXIsProcessTrusted()
    }

    /// `true` if the agent has Input Monitoring
    /// permission (macOS 10.15+). Required for
    // capturing keystrokes (future). Not strictly
    // required for *posting* events, which only
    // needs Accessibility.
    func hasInputMonitoring() -> Bool {
        if #available(macOS 10.15, *) {
            return IOHIDCheckAccess(kIOHIDRequestTypeListenEvent) == kIOHIDAccessTypeGranted
        }
        return true
    }

    /// Prompt the user to grant Accessibility
    /// permission. Opens System Settings to the
    /// right pane.
    func requestAccessibility() {
        let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!
        NSWorkspace.shared.open(url)
    }
}
