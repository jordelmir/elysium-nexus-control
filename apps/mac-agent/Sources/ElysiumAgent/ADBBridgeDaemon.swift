import Foundation

/**
 * ADB Bridge Daemon — Automatic USB ADB Reverse Port Forwarder.
 *
 * Continuously monitors for connected Android devices via USB (`adb devices`).
 * When an Android phone (e.g. Honor Magic V2) is detected via USB-C, it automatically
 * executes `adb reverse tcp:7878 tcp:7878` to bridge the phone's loopback connection
 * to the Mac agent daemon.
 *
 * This enables 100% Zero-Touch operation:
 * - User turns on Mac
 * - Mac boots and elysium-agent starts at login
 * - User plugs in USB-C cable from Android phone to Mac
 * - ADBBridgeDaemon automatically runs `adb reverse tcp:7878 tcp:7878`
 * - User opens APK on Android phone
 * - Screen sharing and control surface fire IMMEDIATELY with ZERO manual terminal commands!
 */
public final class ADBBridgeDaemon {
    private var timer: Timer?
    private var isRunning = false
    private let queue = DispatchQueue(label: "com.elysium.adb-daemon", qos: .background)
    private var lastDeviceCount = -1

    public static let shared = ADBBridgeDaemon()

    private init() {}

    public func start() {
        guard !isRunning else { return }
        isRunning = true
        Log.info("ADBBridgeDaemon — Starting automatic USB ADB reverse monitoring...")

        // Run check immediately, then poll every 3 seconds
        checkAndBridgeADB()

        DispatchQueue.main.async { [weak self] in
            self?.timer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in
                self?.queue.async {
                    self?.checkAndBridgeADB()
                }
            }
        }
    }

    public func stop() {
        guard isRunning else { return }
        isRunning = false
        DispatchQueue.main.async { [weak self] in
            self?.timer?.invalidate()
            self?.timer = nil
        }
        Log.info("ADBBridgeDaemon — Stopped")
    }

    private func checkAndBridgeADB() {
        let adbPath = findADBPath()
        guard !adbPath.isEmpty else {
            return
        }

        // 1. Run `adb devices`
        let devicesOutput = runProcess(executable: adbPath, arguments: ["devices"])
        let lines = devicesOutput.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("List of devices") }

        let activeDevices = lines.filter { $0.contains("device") && !$0.contains("offline") }
        let currentCount = activeDevices.count

        // If devices changed or on initial run, execute `adb reverse tcp:7878 tcp:7878`
        if currentCount > 0 && currentCount != lastDeviceCount {
            Log.info("ADBBridgeDaemon — Android device detected via USB (\(currentCount) device(s)). Executing adb reverse tcp:7878 tcp:7878...")
            let reverseOutput = runProcess(executable: adbPath, arguments: ["reverse", "tcp:7878", "tcp:7878"])
            Log.info("ADBBridgeDaemon — adb reverse output: \(reverseOutput.trimmingCharacters(in: .whitespacesAndNewlines))")
        }

        lastDeviceCount = currentCount
    }

    private func findADBPath() -> String {
        let possiblePaths = [
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
            "\(NSHomeDirectory())/Library/Android/sdk/platform-tools/adb",
            "/Users/jordelmirsdevhome/Library/Android/sdk/platform-tools/adb"
        ]

        let fm = FileManager.default
        for path in possiblePaths {
            if fm.fileExists(atPath: path) {
                return path
            }
        }
        return ""
    }

    private func runProcess(executable: String, arguments: [String]) -> String {
        let task = Process()
        let pipe = Pipe()

        task.executableURL = URL(fileURLWithPath: executable)
        task.arguments = arguments
        task.standardOutput = pipe
        task.standardError = pipe

        do {
            try task.run()
            task.waitUntilExit()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            return String(data: data, encoding: .utf8) ?? ""
        } catch {
            return ""
        }
    }
}
