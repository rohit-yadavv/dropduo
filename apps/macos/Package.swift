// swift-tools-version: 6.0
import PackageDescription
let package = Package(name: "Nearport", platforms: [.macOS(.v14)], products: [
    .library(name: "NearportCore", targets: ["NearportCore"]),
    .executable(name: "Nearport", targets: ["Nearport"]),
    .executable(name: "nearport-interop", targets: ["Interop"])
], targets: [
    .target(name: "NearportCore"),
    .executableTarget(name: "Nearport", dependencies: ["NearportCore"]),
    .executableTarget(name: "Interop", dependencies: ["NearportCore"]),
    .executableTarget(name: "CoreChecks", dependencies: ["NearportCore"])
], swiftLanguageModes: [.v5])
