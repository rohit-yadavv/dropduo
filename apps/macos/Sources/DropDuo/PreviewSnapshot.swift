#if DEBUG
import AppKit

/// Debug-only: with DROPDUO_SNAPSHOT=<dir>, saves each window in light and dark, then quits.
enum PreviewSnapshot {
    @MainActor static func scheduleIfRequested() {
        guard let directory = ProcessInfo.processInfo.environment["DROPDUO_SNAPSHOT"] else { return }
        let name = ProcessInfo.processInfo.environment["DROPDUO_PREVIEW_SCENE"] ?? "device"
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            for (suffix, appearance) in [("light", NSAppearance.Name.aqua), ("dark", .darkAqua)] {
                NSApp.appearance = NSAppearance(named: appearance)
                try? await Task.sleep(for: .seconds(1))
                for (index, window) in NSApp.windows.enumerated() where window.isVisible && window.frame.width > 300 {
                    guard let view = window.contentView?.superview, let rep = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { continue }
                    view.cacheDisplay(in: view.bounds, to: rep)
                    try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: directory).appendingPathComponent("\(name)-\(index)-\(suffix).png"))
                }
            }
            NSApp.terminate(nil)
        }
    }
}
#endif
