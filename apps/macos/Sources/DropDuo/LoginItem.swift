import AppKit
import ServiceManagement

/// Opens DropDuo when the user logs in, so a paired phone can reach the Mac after a restart.
enum LoginItem {
    private static let chosenKey = "dropduo.loginItemChosen"

    static var isEnabled: Bool { SMAppService.mainApp.status == .enabled }
    static var needsApproval: Bool { SMAppService.mainApp.status == .requiresApproval }
    static func openSystemSettings() { SMAppService.openSystemSettingsLoginItems() }

    static func set(_ enabled: Bool) throws {
        UserDefaults.standard.set(true, forKey: chosenKey)
        if enabled { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
    }

    /// Turns it on once a phone is paired, unless the user already chose in Settings.
    static func enableByDefault() {
        guard Bundle.main.bundleIdentifier != nil, !UserDefaults.standard.bool(forKey: chosenKey) else { return }
        // Retried on later launches if macOS refuses, such as when the app runs from a quarantined download.
        if (try? SMAppService.mainApp.register()) != nil { UserDefaults.standard.set(true, forKey: chosenKey) }
    }

    /// True when macOS opened DropDuo at login rather than the user opening it.
    static var launchedAtLogin: Bool {
        guard let event = NSAppleEventManager.shared().currentAppleEvent else { return false }
        return event.eventID == kAEOpenApplication && event.paramDescriptor(forKeyword: keyAEPropData)?.enumCodeValue == keyAELaunchedAsLogInItem
    }
}
