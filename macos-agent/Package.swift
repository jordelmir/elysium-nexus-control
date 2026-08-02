// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "ElysiumNexusAgent",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "elysium-nexus-agent",
            path: "Sources",
            linkerSettings: [
                .linkedFramework("CoreGraphics"),
                .linkedFramework("IOKit"),
                .linkedFramework("Foundation")
            ]
        )
    ]
)
