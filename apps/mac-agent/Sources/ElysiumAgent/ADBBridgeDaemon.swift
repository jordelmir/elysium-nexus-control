import Foundation

/// Keeps an ADB reverse route for every authorized Android transport.
///
/// `adb reverse` without `-s <serial>` fails as soon as more than one device is
/// attached. A failed first attempt must also be retried: USB debugging may be
/// authorized after the app has already started.
public final class ADBBridgeDaemon {
    private let queue = DispatchQueue(label: "com.elysium.adb-daemon", qos: .utility)
    private var timer: DispatchSourceTimer?
    private var isRunning = false
    private var bridgedSerials = Set<String>()
    private var lastNoAdbWarningAt: Date?

    public static let shared = ADBBridgeDaemon()

    private init() {}

    public func start() {
        queue.async { [weak self] in
            guard let self, !self.isRunning else { return }
            self.isRunning = true
            Log.info("ADBBridgeDaemon: monitoring authorized Android devices for USB/Wi-Fi reverse routes")
            self.checkAndBridgeADB()

            let timer = DispatchSource.makeTimerSource(queue: self.queue)
            timer.schedule(deadline: .now() + .seconds(3), repeating: .seconds(3), leeway: .milliseconds(500))
            timer.setEventHandler { [weak self] in self?.checkAndBridgeADB() }
            self.timer = timer
            timer.resume()
        }
    }

    public func stop() {
        queue.async { [weak self] in
            guard let self, self.isRunning else { return }
            self.isRunning = false
            self.timer?.cancel()
            self.timer = nil
            self.bridgedSerials.removeAll()
            Log.info("ADBBridgeDaemon: stopped")
        }
    }

    private func checkAndBridgeADB() {
        guard isRunning else { return }
        guard let adbPath = findADBPath() else {
            if lastNoAdbWarningAt.map({ Date().timeIntervalSince($0) > 60 }) ?? true {
                Log.warn("ADBBridgeDaemon: Android platform-tools adb was not found; USB direct mode needs platform-tools installed")
                lastNoAdbWarningAt = Date()
            }
            return
        }

        let devices = runADB(adbPath, arguments: ["devices"])
        guard devices.status == 0 else {
            Log.warn("ADBBridgeDaemon: adb devices failed: \(devices.output.trimmingCharacters(in: .whitespacesAndNewlines))")
            return
        }

        let serials = Set(devices.output
            .split(whereSeparator: \.isNewline)
            .compactMap { line -> String? in
                let fields = line.split(whereSeparator: { $0 == " " || $0 == "\t" })
                guard fields.count >= 2, fields[1] == "device" else { return nil }
                let serial = String(fields[0])
                guard serial != "List", !serial.isEmpty else { return nil }
                return serial
            })

        bridgedSerials.formIntersection(serials)
        for serial in serials.subtracting(bridgedSerials).sorted() {
            let result = runADB(adbPath, arguments: ["-s", serial, "reverse", "tcp:7878", "tcp:7878"])
            if result.status == 0 {
                bridgedSerials.insert(serial)
                Log.info("ADBBridgeDaemon: reverse route ready for Android device \(serial)")
            } else {
                let detail = result.output.trimmingCharacters(in: .whitespacesAndNewlines)
                Log.warn("ADBBridgeDaemon: reverse route failed for \(serial): \(detail.isEmpty ? "adb exited with status \(result.status)" : detail)")
            }
        }
    }

    private func findADBPath() -> String? {
        let paths = [
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
            "\(NSHomeDirectory())/Library/Android/sdk/platform-tools/adb",
            "/Users/jordelmirsdevhome/Library/Android/sdk/platform-tools/adb"
        ]
        return paths.first { FileManager.default.isExecutableFile(atPath: $0) }
    }

    private func runADB(_ executable: String, arguments: [String]) -> (output: String, status: Int32) {
        let process = Process()
        let pipe = Pipe()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = arguments
        process.standardOutput = pipe
        process.standardError = pipe

        do {
            try process.run()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            process.waitUntilExit()
            return (String(data: data, encoding: .utf8) ?? "", process.terminationStatus)
        } catch {
            return (error.localizedDescription, -1)
        }
    }
}
