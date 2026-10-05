import AppKit
import SwiftUI

enum Brand {
    static let menuIcon: NSImage = {
        let image = Self.image("DropDuoMenu")
        image.size = NSSize(width: 22, height: 22)
        image.isTemplate = true
        return image
    }()
    static let mark: NSImage = image("DropDuoMark")
    /// Avoids Bundle.module, which calls fatalError when the SwiftPM resource bundle is missing or unreadable
    /// (for example when launched from inside a mounted archive). A missing image must never crash the app.
    private static func image(_ name: String) -> NSImage {
        let swiftPMBundle = "DropDuo_DropDuo.bundle"
        let candidates = [Bundle.main.url(forResource: name, withExtension: "png"),
            Bundle.main.resourceURL.flatMap { Bundle(url: $0.appendingPathComponent(swiftPMBundle)) }?.url(forResource: name, withExtension: "png"),
            Bundle(url: Bundle.main.bundleURL.appendingPathComponent(swiftPMBundle))?.url(forResource: name, withExtension: "png")]
        for case let url? in candidates { if let image = NSImage(contentsOf: url) { return image } }
        return NSImage(systemSymbolName: "arrow.left.arrow.right.circle", accessibilityDescription: "DropDuo") ?? NSImage()
    }
    /// Cobalt from branding/mark.json, lifted in dark mode so it keeps contrast on dark surfaces.
    static let cobalt = Color(nsColor: NSColor(name: "DropDuoCobalt") { appearance in
        appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
            ? NSColor(srgbRed: 0x4C / 255, green: 0x7E / 255, blue: 0xF3 / 255, alpha: 1)
            : NSColor(srgbRed: 0x25 / 255, green: 0x58 / 255, blue: 0xDD / 255, alpha: 1)
    })
}
