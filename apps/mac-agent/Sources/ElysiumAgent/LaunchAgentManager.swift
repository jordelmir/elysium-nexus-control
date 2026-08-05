import Foundation
import ServiceManagement

/**
 * LaunchAgent Manager for Mac Agent.
 *
 * Configures `elysium-agent` to automatically launch in headless background daemon mode
 * whenever the macOS user logs in or the Mac boots up.
 *
 * Plist path: `~/Library/LaunchAgents/com.elysium.agent.plist`
 */
public final class LaunchAgentManager {
    public static let plistLabel = "com.elysium.agent"
    
    public static var plistURL: URL {
        let libraryDir = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first!
        return libraryDir.appendingPathComponent("LaunchAgents/\(plistLabel).plist")
    }

    public static func isInstalled() -> Bool {
        return FileManager.default.fileExists(atPath: plistURL.path)
    }

    public static func installLaunchAgent(executablePath: String? = nil) -> Bool {
        let binaryPath = executablePath ?? Bundle.main.executablePath ?? "/usr/local/bin/elysium-agent"
        
        let plistContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>Label</key>
            <string>\(plistLabel)</string>
            <key>ProgramArguments</key>
            <array>
                <string>\(binaryPath)</string>
                <string>--headless</string>
            </array>
            <key>RunAtLoad</key>
            <true/>
            <key>KeepAlive</key>
            <true/>
            <key>StandardOutPath</key>
            <string>/tmp/elysium_agent.log</string>
            <key>StandardErrorPath</key>
            <string>/tmp/elysium_agent.log</string>
        </dict>
        </plist>
        """

        let fm = FileManager.default
        let launchAgentsDir = plistURL.deletingLastPathComponent()

        do {
            if !fm.fileExists(atPath: launchAgentsDir.path) {
                try fm.createDirectory(at: launchAgentsDir, withIntermediateDirectories: true, attributes: nil)
            }
            try plistContent.write(to: plistURL, atomically: true, encoding: .utf8)
            Log.info("LaunchAgentManager — Successfully created \(plistURL.path)")

            // Register with launchctl
            let task = Process()
            task.executableURL = URL(fileURLWithPath: "/bin/launchctl")
            task.arguments = ["load", "-w", plistURL.path]
            try task.run()
            task.waitUntilExit()

            Log.info("LaunchAgentManager — Registered LaunchAgent with launchctl")
            return true
        } catch {
            Log.error("LaunchAgentManager — Failed to install LaunchAgent: \(error)")
            return false
        }
    }

    public static func uninstallLaunchAgent() -> Bool {
        guard isInstalled() else { return true }

        let task = Process()
        task.executableURL = URL(fileURLWithPath: "/bin/launchctl")
        task.arguments = ["unload", "-w", plistURL.path]
        try? task.run()
        task.waitUntilExit()

        do {
            try FileManager.default.removeItem(at: plistURL)
            Log.info("LaunchAgentManager — Removed LaunchAgent plist")
            return true
        } catch {
            Log.error("LaunchAgentManager — Failed to remove LaunchAgent plist: \(error)")
            return false
        }
    }
}
