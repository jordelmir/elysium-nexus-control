// swift-tools-version:5.9
//
// Elysium Nexus — Mac Agent
// A small menu-bar app that lets the Elysium Nexus
// Android app control your Mac: trackpad, keyboard,
// mouse, and gestures. Built with SwiftUI + Network
// framework + CryptoKit + CoreGraphics.
//
// Build:  swift build -c release
// Run:    swift run -c release elysium-agent
//
// See README.md for the full setup, including the
// Accessibility permission that macOS requires to
// inject mouse and keyboard events.
//
import PackageDescription

let package = Package(
    name: "ElysiumAgent",
    platforms: [
        .macOS(.v13)
    ],
    targets: [
        .executableTarget(
            name: "elysium-agent",
            dependencies: ["ElysiumAgent"]
        ),
        .target(
            name: "ElysiumAgent",
            path: "Sources/ElysiumAgent"
        )
    ]
)
