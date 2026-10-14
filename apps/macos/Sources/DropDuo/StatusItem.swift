import AppKit

/// Menu bar icon and its menu, rebuilt each time it opens so status and progress stay current.
@MainActor final class StatusItemController: NSObject, NSMenuDelegate {
    private let model: AppModel
    private let updater: AppUpdater
    private let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
    var openApp: () -> Void = {}
    var openSettings: () -> Void = {}

    init(model: AppModel, updater: AppUpdater) {
        self.model = model
        self.updater = updater
        super.init()
        item.button?.image = Brand.menuIcon
        item.button?.setAccessibilityLabel("DropDuo")
        item.button?.toolTip = "DropDuo"
        let menu = NSMenu(); menu.delegate = self; item.menu = menu
    }

    // MARK: Menu

    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()
        if let device = model.selectedDevice {
            let online = model.isOnline(device)
            menu.addItem(disabled(online ? "\(device.name) · Connected" : "\(device.name) · Not reachable", image: dot(online ? .systemGreen : .tertiaryLabelColor)))
        } else {
            menu.addItem(disabled("No paired devices", image: symbol("smartphone")))
        }
        let active = model.transfers.filter { ["Preparing", "Sending", "Receiving"].contains($0.state) }
        if let first = active.first {
            let receiving = first.direction == "Received"
            let verb = receiving ? "Receiving" : "Sending"
            menu.addItem(disabled(active.count == 1 ? "\(verb) \(first.name) · \(Int(first.progress * 100))%" : "\(verb) \(active.count) files…",
                image: symbol(receiving ? "arrow.down.circle" : "arrow.up.circle")))
        }
        if model.devices.count > 1 {
            let picker = NSMenuItem(title: "Send To", action: nil, keyEquivalent: ""); let submenu = NSMenu()
            picker.image = symbol("smartphone")
            for device in model.devices {
                let entry = action(device.name + (model.isOnline(device) ? "" : " (not reachable)")) { [model] in model.selected = device.id }
                entry.state = device.id == model.selected ? .on : .off
                submenu.addItem(entry)
            }
            picker.submenu = submenu; menu.addItem(picker)
        }
        menu.addItem(.separator())
        menu.addItem(action("Open DropDuo", image: "macwindow") { [weak self] in self?.openApp() })
        let send = action("Send Files…", image: "square.and.arrow.up") { [model] in NSApp.activate(ignoringOtherApps: true); model.chooseFiles() }
        send.isEnabled = !model.selected.isEmpty
        menu.addItem(send)
        menu.addItem(action("Open Received Files", image: "folder") { [model] in NSWorkspace.shared.open(model.inboxRoot) })
        menu.addItem(.separator())
        menu.addItem(action("Settings…", key: ",", image: "gearshape") { [weak self] in self?.openSettings() })
        let update = action(updater.availableVersion.map { "Update to \($0)…" } ?? "Check for Updates…", image: "arrow.down.circle") { [updater] in updater.check() }
        update.isEnabled = updater.canCheck
        menu.addItem(update)
        menu.addItem(action("Quit DropDuo", key: "q", image: "power") { NSApp.terminate(nil) })
    }

    private func disabled(_ title: String, image: NSImage? = nil) -> NSMenuItem {
        let item = NSMenuItem(title: title, action: nil, keyEquivalent: ""); item.isEnabled = false; item.image = image; return item
    }

    private func action(_ title: String, key: String = "", image: String? = nil, _ run: @escaping () -> Void) -> NSMenuItem {
        let item = NSMenuItem(title: title, action: #selector(MenuAction.run), keyEquivalent: key)
        let target = MenuAction(run); item.target = target; item.representedObject = target
        item.image = image.flatMap(symbol)
        return item
    }

    private func symbol(_ name: String) -> NSImage? { NSImage(systemSymbolName: name, accessibilityDescription: nil) }

    /// A small status dot sized like a menu icon.
    private func dot(_ color: NSColor) -> NSImage {
        NSImage(size: NSSize(width: 16, height: 16), flipped: false) { rect in
            color.setFill(); NSBezierPath(ovalIn: NSRect(x: rect.midX - 4, y: rect.midY - 4, width: 8, height: 8)).fill(); return true
        }
    }
}

/// Holds a menu item's closure; NSMenuItem keeps it alive through representedObject.
private final class MenuAction: NSObject {
    let body: () -> Void
    init(_ body: @escaping () -> Void) { self.body = body }
    @objc func run() { body() }
}
