import AppKit
import SwiftUI

enum Brand {
    static let menuIcon: NSImage = {
        let url = Bundle.module.url(forResource: "DropDuoMenu", withExtension: "png")!
        let image = NSImage(contentsOf: url)!
        image.size = NSSize(width: 22, height: 22)
        image.isTemplate = true
        return image
    }()
    static let mark: NSImage = NSImage(contentsOf: Bundle.module.url(forResource: "DropDuoMark", withExtension: "png")!)!
    /// Cobalt from branding/mark.json, lifted in dark mode so it keeps contrast on dark surfaces.
    static let cobalt = Color(nsColor: NSColor(name: "DropDuoCobalt") { appearance in
        appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
            ? NSColor(srgbRed: 0x4C / 255, green: 0x7E / 255, blue: 0xF3 / 255, alpha: 1)
            : NSColor(srgbRed: 0x25 / 255, green: 0x58 / 255, blue: 0xDD / 255, alpha: 1)
    })
}
