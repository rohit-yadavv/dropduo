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
    /// Brand cobalt, lifted in dark mode so it keeps contrast on dark surfaces.
    static let cobalt = dynamic("DropDuoCobalt", light: NSColor(srgbRed: 0x25 / 255, green: 0x58 / 255, blue: 0xDD / 255, alpha: 1),
        dark: NSColor(srgbRed: 0x4C / 255, green: 0x7E / 255, blue: 0xF3 / 255, alpha: 1))
    /// Cards and incoming bubbles: white on the light window, a lifted gray in dark mode.
    static let surface = dynamic("DropDuoSurface", light: .white, dark: NSColor(white: 1, alpha: 0.07))
    static let panelSurface = dynamic("DropDuoPanelSurface", light: .white, dark: NSColor(srgbRed: 0.17, green: 0.18, blue: 0.2, alpha: 1))
    /// Filled controls and sent notes need enough contrast for white labels in both appearances.
    static let actionFill = Color(nsColor: NSColor(srgbRed: 0x25 / 255, green: 0x58 / 255, blue: 0xDD / 255, alpha: 1))
    static let hairline = dynamic("DropDuoHairline", light: NSColor(white: 0, alpha: 0.08), dark: NSColor(white: 1, alpha: 0.09))
    /// Shadows only read in light mode; dark surfaces separate by lightness instead.
    static let shadow = dynamic("DropDuoShadow", light: NSColor(srgbRed: 0.1, green: 0.14, blue: 0.3, alpha: 0.07), dark: .clear)

    private static func dynamic(_ name: String, light: NSColor, dark: NSColor) -> Color {
        Color(nsColor: NSColor(name: name) { $0.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua ? dark : light })
    }
}

/// The Mac and the phone joined by a dotted line. It turns cobalt when they're connected
/// and runs toward whichever side is receiving.
struct DuoLink: View {
    enum Link { case on, searching, off }
    var link: Link
    /// 1 while the Mac sends, -1 while it receives, 0 when idle.
    var flow = 0
    var tile: CGFloat = 52
    /// Puts the DropDuo mark between the two devices.
    var mark = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 0) {
            DeviceTile(symbol: "laptopcomputer", size: tile, lit: false)
            dots
            if mark {
                Image(nsImage: Brand.mark).resizable().interpolation(.high).frame(width: tile * 0.62, height: tile * 0.62)
                    .shadow(color: Brand.shadow, radius: 6, y: 2)
                dots
            }
            DeviceTile(symbol: "smartphone", size: tile, lit: link == .on)
        }
        .accessibilityHidden(true)
        .animation(reduceMotion ? nil : .easeOut(duration: 0.35), value: link)
    }

    private var dots: some View {
        let moving = (link == .searching || flow != 0) && !reduceMotion
        let color = link == .on ? Brand.cobalt : Color.secondary.opacity(0.45)
        return TimelineView(.animation(paused: !moving)) { context in
            Canvas { canvas, size in
                let period: CGFloat = 10
                let speed = link == .searching ? 9.0 : 24.0
                let shift = moving ? CGFloat((context.date.timeIntervalSinceReferenceDate * speed).truncatingRemainder(dividingBy: Double(period))) : 0
                var path = Path()
                path.move(to: CGPoint(x: 0, y: size.height / 2)); path.addLine(to: CGPoint(x: size.width, y: size.height / 2))
                // A growing dash phase pulls the dots toward the start, so negate it to run left to right.
                canvas.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [0.1, period], dashPhase: flow < 0 ? shift : -shift))
            }
        }
        .frame(minWidth: 36, maxWidth: .infinity).frame(height: tile).padding(.horizontal, 10)
    }
}

private struct DeviceTile: View {
    let symbol: String
    let size: CGFloat
    let lit: Bool
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
        Image(systemName: symbol).font(.system(size: size * 0.4)).foregroundStyle(.primary)
            .frame(width: size, height: size)
            .background(Brand.surface, in: shape)
            .overlay(shape.strokeBorder(lit ? Brand.cobalt.opacity(0.65) : Brand.hairline, lineWidth: lit ? 1.5 : 1))
            .shadow(color: Brand.shadow, radius: 4, y: 1)
    }
}
