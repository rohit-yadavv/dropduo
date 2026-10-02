// Render the authored vector source with CoreGraphics; no image-generation dependency.
import AppKit
import ImageIO
import UniformTypeIdentifiers

struct Mark: Decodable {
    struct Path: Decodable { let color: String; let commands: [[Value]] }
    enum Value: Decodable {
        case number(Double), text(String)
        init(from decoder: Decoder) throws {
            let c = try decoder.singleValueContainer()
            if let n = try? c.decode(Double.self) { self = .number(n) }
            else { self = .text(try c.decode(String.self)) }
        }
        var number: Double { if case .number(let n) = self { return n }; fatalError("Expected coordinate") }
        var text: String { if case .text(let s) = self { return s }; fatalError("Expected command") }
    }
    let colors: [String: String]; let paths: [Path]; let frontClip: [Double]; let offsetY: Double
}
let root = URL(fileURLWithPath: CommandLine.arguments[1])
let mark = try JSONDecoder().decode(Mark.self, from: Data(contentsOf: root.appendingPathComponent("branding/mark.json")))
func path(_ commands: [[Mark.Value]]) -> CGPath {
    let p = CGMutablePath()
    for command in commands {
        let n = command.dropFirst().map(\.number)
        switch command[0].text {
        case "M": p.move(to: CGPoint(x: n[0], y: n[1]))
        case "H": p.addLine(to: CGPoint(x: n[0], y: p.currentPoint.y))
        case "V": p.addLine(to: CGPoint(x: p.currentPoint.x, y: n[0]))
        case "C": p.addCurve(to: CGPoint(x: n[4], y: n[5]), control1: CGPoint(x: n[0], y: n[1]), control2: CGPoint(x: n[2], y: n[3]))
        case "Z": p.closeSubpath()
        default: fatalError("Unsupported path command")
        }
    }
    return p
}
let paths = mark.paths.map { path($0.commands) }
func color(_ hex: String) -> CGColor {
    let value = UInt32(hex.dropFirst(), radix: 16)!
    return CGColor(srgbRed: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, alpha: 1)
}
func render(size: Int, destination: URL, tile: Bool, monochrome: Bool = false) throws {
    guard let c = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { fatalError("Could not create bitmap") }
    c.translateBy(x: 0, y: Double(size)); c.scaleBy(x: Double(size) / 1024, y: -Double(size) / 1024)
    if tile {
        c.setFillColor(color(mark.colors["background"]!))
        c.addPath(CGPath(roundedRect: CGRect(x: 32, y: 32, width: 960, height: 960), cornerWidth: 210, cornerHeight: 210, transform: nil)); c.fillPath()
    }
    c.translateBy(x: 0, y: mark.offsetY)
    for index in paths.indices {
        c.setFillColor(color(monochrome ? "#000000" : mark.colors[mark.paths[index].color]!))
        c.addPath(paths[index]); c.drawPath(using: .eoFill)
    }
    c.saveGState()
    let r = mark.frontClip
    c.clip(to: CGRect(x: r[0], y: r[1], width: r[2], height: r[3]))
    c.setFillColor(color(monochrome ? "#000000" : mark.colors["ink"]!))
    c.addPath(paths[0]); c.drawPath(using: .eoFill); c.restoreGState()
    try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard let image = c.makeImage(), let writer = CGImageDestinationCreateWithURL(destination as CFURL, UTType.png.identifier as CFString, 1, nil) else { fatalError("Could not write image") }
    CGImageDestinationAddImage(writer, image, nil)
    guard CGImageDestinationFinalize(writer) else { fatalError("Could not finish image") }
}
let iconset = root.appendingPathComponent(".artifacts/branding/DropDuo.iconset")
try FileManager.default.createDirectory(at: iconset, withIntermediateDirectories: true)
for base in [16,32,128,256,512] {
    try render(size: base, destination: iconset.appendingPathComponent("icon_\(base)x\(base).png"), tile: true)
    try render(size: base * 2, destination: iconset.appendingPathComponent("icon_\(base)x\(base)@2x.png"), tile: true)
}
try render(size: 1024, destination: root.appendingPathComponent("branding/dropduo-icon.png"), tile: true)
let resources = root.appendingPathComponent("apps/macos/Resources")
let uiResources = root.appendingPathComponent("apps/macos/Sources/DropDuo/Resources")
try FileManager.default.createDirectory(at: resources, withIntermediateDirectories: true)
try render(size: 128, destination: uiResources.appendingPathComponent("DropDuoMark.png"), tile: true)
try render(size: 64, destination: uiResources.appendingPathComponent("DropDuoMenu.png"), tile: false, monochrome: true)
let process = Process()
process.executableURL = URL(fileURLWithPath: "/usr/bin/iconutil")
process.arguments = ["-c", "icns", iconset.path, "-o", resources.appendingPathComponent("DropDuo.icns").path]
try process.run(); process.waitUntilExit()
guard process.terminationStatus == 0 else { fatalError("ICNS conversion failed") }
print("Exported DropDuo SVG, Android vectors, PNGs and ICNS.")
