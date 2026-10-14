#if DEBUG
import AppKit

/// Debug-only: with DROPDUO_SNAPSHOT=<dir>, saves each window in light and dark, then quits.
enum PreviewSnapshot {
    @MainActor static func scheduleIfRequested() {
        guard let directory = ProcessInfo.processInfo.environment["DROPDUO_SNAPSHOT"] else { return }
        let name = ProcessInfo.processInfo.environment["DROPDUO_PREVIEW_SCENE"] ?? "device"
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            // Capture the window as active so accent-colored controls render the way people see them.
            NSApp.activate(ignoringOtherApps: true)
            if let window = NSApp.windows.first(where: { $0.isVisible && $0.frame.width > 600 && $0.sheetParent == nil }) {
                let environment = ProcessInfo.processInfo.environment
                if let width = environment["DROPDUO_PREVIEW_WIDTH"].flatMap(Double.init),
                   let height = environment["DROPDUO_PREVIEW_HEIGHT"].flatMap(Double.init) {
                    window.setContentSize(NSSize(width: width, height: height))
                }
                window.makeKeyAndOrderFront(nil)
            }
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
