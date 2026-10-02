// swift-tools-version: 6.0
import PackageDescription
let package = Package(name: "DropDuo", platforms: [.macOS(.v14)], products: [
    .library(name: "DropDuoCore", targets: ["DropDuoCore"]),
    .executable(name: "DropDuo", targets: ["DropDuo"]),
    .executable(name: "dropduo-interop", targets: ["Interop"])
], targets: [
    .target(name: "DropDuoCore"),
    .executableTarget(name: "DropDuo", dependencies: ["DropDuoCore"], resources: [.process("Resources")]),
    .executableTarget(name: "Interop", dependencies: ["DropDuoCore"]),
    .executableTarget(name: "CoreChecks", dependencies: ["DropDuoCore"])
], swiftLanguageModes: [.v5])
