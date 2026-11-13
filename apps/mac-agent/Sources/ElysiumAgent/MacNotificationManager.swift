//
// Elysium Nexus — Mac Agent
// Interactive & informative macOS notification coordinator.
// Uses UNUserNotificationCenter to deliver real-time banners
// with action buttons for pairing, connection, and screen mirroring.
//
import Foundation
import UserNotifications
import AppKit

public final class MacNotificationManager: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = MacNotificationManager()
    private var isAuthorized = false

    override private init() {
        super.init()
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound, .badge]) { [weak self] granted, error in
            self?.isAuthorized = granted
            if let error = error {
                Log.warn("MacNotificationManager: authorization error: \(error)")
            } else {
                Log.info("MacNotificationManager: authorization granted=\(granted)")
            }
        }
        setupCategories()
    }

    private func setupCategories() {
        let stopAction = UNNotificationAction(
            identifier: "STOP_SESSION",
            title: "Desconectar Sesión",
            options: [.destructive]
        )
        let openStatusAction = UNNotificationAction(
            identifier: "OPEN_DASHBOARD",
            title: "Abrir Dashboard",
            options: [.foreground]
        )
        let sessionCategory = UNNotificationCategory(
            identifier: "NEXUS_REMOTE_SESSION",
            actions: [stopAction, openStatusAction],
            intentIdentifiers: [],
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([sessionCategory])
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if #available(macOS 11.0, *) {
            completionHandler([.banner, .sound])
        } else {
            completionHandler([.alert, .sound])
        }
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        switch response.actionIdentifier {
        case "STOP_SESSION":
            Log.info("MacNotificationManager: User tapped STOP_SESSION")
            ScreenCaptureService.shared.stop()
        case "OPEN_DASHBOARD":
            DispatchQueue.main.async {
                NSApp.activate(ignoringOtherApps: true)
                for window in NSApp.windows where window.title.contains("Elysium Nexus") {
                    window.makeKeyAndOrderFront(nil)
                }
            }
        default:
            break
        }
        completionHandler()
    }

    public func notify(
        id: String = UUID().uuidString,
        title: String,
        subtitle: String? = nil,
        body: String,
        category: String? = nil
    ) {
        let content = UNMutableNotificationContent()
        content.title = title
        if let subtitle = subtitle { content.subtitle = subtitle }
        content.body = body
        content.sound = .default
        if let category = category { content.categoryIdentifier = category }

        let request = UNNotificationRequest(identifier: id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                Log.warn("MacNotificationManager: delivery failed for '\(title)': \(error)")
            }
        }
    }
}
