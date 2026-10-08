// swift-tools-version: 6.0
import PackageDescription
let package = Package(name: "DropDuo", platforms: [.macOS(.v14)], products: [
    .library(name: "DropDuoCore", targets: ["DropDuoCore"]),
    .executable(name: "DropDuo", targets: ["DropDuo"]),
    .executable(name: "dropduo-interop", targets: ["Interop"])
], dependencies: [
    .package(url: "https://github.com/sparkle-project/Sparkle", exact: "2.10.0")
], targets: [
    .target(name: "DropDuoCore"),
    .executableTarget(name: "DropDuo", dependencies: ["DropDuoCore", .product(name: "Sparkle", package: "Sparkle")], resources: [.process("Resources")],
        linkerSettings: [.unsafeFlags(["-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks"])]),
    .executableTarget(name: "Interop", dependencies: ["DropDuoCore"]),
    .executableTarget(name: "CoreChecks", dependencies: ["DropDuoCore"])
], swiftLanguageModes: [.v5])
