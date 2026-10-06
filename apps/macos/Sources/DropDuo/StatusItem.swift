import AppKit

/// Menu bar icon and its menu, rebuilt each time it opens so status and progress stay current.
@MainActor final class StatusItemController: NSObject, NSMenuDelegate {
    private let model: AppModel
    private let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
    var openApp: () -> Void = {}
    var openSettings: () -> Void = {}

    init(model: AppModel) {
        self.model = model
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
            menu.addItem(disabled(model.isOnline(device) ? "\(device.name) · Connected" : "\(device.name) · Not reachable"))
        } else {
            menu.addItem(disabled("No paired devices"))
        }
        let active = model.transfers.filter { ["Preparing", "Sending", "Receiving"].contains($0.state) }
        if let first = active.first {
            let verb = first.direction == "Received" ? "Receiving" : "Sending"
            menu.addItem(disabled(active.count == 1 ? "\(verb) \(first.name) · \(Int(first.progress * 100))%" : "\(verb) \(active.count) files…"))
        }
        if model.devices.count > 1 {
            let picker = NSMenuItem(title: "Send To", action: nil, keyEquivalent: ""); let submenu = NSMenu()
            for device in model.devices {
                let entry = action(device.name + (model.isOnline(device) ? "" : " (not reachable)")) { [model] in model.selected = device.id }
                entry.state = device.id == model.selected ? .on : .off
                submenu.addItem(entry)
            }
            picker.submenu = submenu; menu.addItem(picker)
        }
        menu.addItem(.separator())
        menu.addItem(action("Open DropDuo") { [weak self] in self?.openApp() })
        let send = action("Send Files…") { [model] in NSApp.activate(ignoringOtherApps: true); model.chooseFiles() }
        send.isEnabled = model.online.contains(model.selected)
        menu.addItem(send)
        menu.addItem(action("Open Received Files") { [model] in NSWorkspace.shared.open(model.inboxRoot) })
        menu.addItem(.separator())
        menu.addItem(action("Settings…", key: ",") { [weak self] in self?.openSettings() })
        menu.addItem(action("Quit DropDuo", key: "q") { NSApp.terminate(nil) })
    }

    private func disabled(_ title: String) -> NSMenuItem {
        let item = NSMenuItem(title: title, action: nil, keyEquivalent: ""); item.isEnabled = false; return item
    }

    private func action(_ title: String, key: String = "", _ run: @escaping () -> Void) -> NSMenuItem {
        let item = NSMenuItem(title: title, action: #selector(MenuAction.run), keyEquivalent: key)
        let target = MenuAction(run); item.target = target; item.representedObject = target
        return item
    }
}

/// Holds a menu item's closure; NSMenuItem keeps it alive through representedObject.
private final class MenuAction: NSObject {
    let body: () -> Void
    init(_ body: @escaping () -> Void) { self.body = body }
    @objc func run() { body() }
}
