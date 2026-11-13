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
    dependencies: [
        .package(url: "https://github.com/stasel/WebRTC.git", from: "125.0.0"),
        .package(url: "https://github.com/supabase/supabase-swift.git", from: "2.55.4"),
        .package(path: "VanguardFabric")
    ],
    targets: [
        .executableTarget(
            name: "elysium-agent",
            dependencies: [
                .product(name: "WebRTC", package: "WebRTC"),
                .product(name: "Supabase", package: "supabase-swift"),
                .product(name: "CSystemMetrics", package: "VanguardFabric"),
                .product(name: "VanguardAgents", package: "VanguardFabric"),
                .product(name: "VanguardAudit", package: "VanguardFabric"),
                .product(name: "VanguardCapture", package: "VanguardFabric"),
                .product(name: "VanguardClipboard", package: "VanguardFabric"),
                .product(name: "VanguardCompute", package: "VanguardFabric"),
                .product(name: "VanguardDiscovery", package: "VanguardFabric"),
                .product(name: "VanguardDomain", package: "VanguardFabric"),
                .product(name: "VanguardFiles", package: "VanguardFabric"),
                .product(name: "VanguardIdentity", package: "VanguardFabric"),
                .product(name: "VanguardInput", package: "VanguardFabric"),
                .product(name: "VanguardObservability", package: "VanguardFabric"),
                .product(name: "VanguardPermissions", package: "VanguardFabric"),
                .product(name: "VanguardProtocol", package: "VanguardFabric"),
                .product(name: "VanguardRender", package: "VanguardFabric"),
                .product(name: "VanguardScheduler", package: "VanguardFabric"),
                .product(name: "VanguardSecurity", package: "VanguardFabric"),
                .product(name: "VanguardSession", package: "VanguardFabric"),
                .product(name: "VanguardTelemetry", package: "VanguardFabric"),
                .product(name: "VanguardTerminal", package: "VanguardFabric"),
                .product(name: "VanguardTransport", package: "VanguardFabric"),
                .product(name: "VanguardUI", package: "VanguardFabric"),
                .product(name: "VanguardUpdates", package: "VanguardFabric"),
                .product(name: "VanguardVideo", package: "VanguardFabric"),
                .product(name: "VanguardWorkspace", package: "VanguardFabric")
            ],
            path: "Sources/ElysiumAgent",
            resources: [
                .process("Resources"),
                .process("Fabric/VanguardConsoleMac/ConsoleAssets.xcassets"),
                .process("Fabric/VanguardNodeMac/NodeAssets.xcassets")
            ]
        ),
        .testTarget(
            name: "ElysiumAgentTests",
            dependencies: [.target(name: "elysium-agent")],
            path: "Tests/ElysiumAgentTests"
        )
    ]
)
