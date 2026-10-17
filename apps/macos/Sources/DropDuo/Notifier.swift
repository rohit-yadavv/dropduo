import AppKit
import UserNotifications
import DropDuoCore

/// Posts "received" and "sent" banners, and handles their Show in Finder, Open and Copy actions.
@MainActor final class Notifier: NSObject, UNUserNotificationCenterDelegate {
    /// Looks up a transfer by ID so notification payloads never carry file paths or shared text.
    var lookup: (String) -> Transfer? = { _ in nil }
    var openApp: () -> Void = {}
    /// UNUserNotificationCenter traps outside an app bundle, such as `swift run`.
    private let center: UNUserNotificationCenter? = Bundle.main.bundleIdentifier == nil ? nil : .current()
    private var requested = false

    func start() {
        guard let center else { return }
        center.delegate = self
        let reveal = UNNotificationAction(identifier: "reveal", title: "Show in Finder")
        let copy = UNNotificationAction(identifier: "copy", title: "Copy")
        let open = UNNotificationAction(identifier: "open", title: "Open Link")
        let code = UNNotificationAction(identifier: "code", title: "Copy Code")
        center.setNotificationCategories([
            UNNotificationCategory(identifier: "file", actions: [reveal], intentIdentifiers: []),
            UNNotificationCategory(identifier: "text", actions: [copy], intentIdentifiers: []),
            UNNotificationCategory(identifier: "link", actions: [open, copy], intentIdentifiers: []),
            UNNotificationCategory(identifier: "code", actions: [code, copy], intentIdentifiers: [])
        ])
    }

    /// Asks once a phone is paired, so the prompt makes sense to the user.
    func requestPermission() {
        guard let center, !requested else { return }
        requested = true
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    /// `folder` is where a file was saved, like "Downloads › DropDuo".
    func received(_ transfer: Transfer, from device: String, folder: String? = nil) {
        if let text = transfer.text {
            if webLink(text) != nil { post(id: transfer.id, title: "Link from \(device)", body: text, category: "link") }
            else if let code = OneTimeCode.find(in: text) { post(id: transfer.id, title: "Code \(code) from \(device)", body: String(text.prefix(200)), category: "code") }
            else { post(id: transfer.id, title: "Text from \(device)", body: String(text.prefix(200)), category: "text") }
        } else {
            post(id: transfer.id, title: "\(transfer.name) from \(device)", body: "Saved to \(folder ?? "your received files"). Click to show it in Finder.", category: "file")
        }
    }

    func sent(_ transfer: Transfer, to device: String) {
        post(id: transfer.id, title: "Sent to \(device)", body: transfer.text == nil ? transfer.name : "Text", category: nil)
    }

    func problem(_ message: String) { post(id: UUID().uuidString, title: "DropDuo", body: message, category: nil) }

    private func post(id: String, title: String, body: String, category: String?) {
        guard let center else { return }
        let content = UNMutableNotificationContent()
        content.title = title; content.body = body; content.threadIdentifier = "transfers"
        if let category { content.categoryIdentifier = category }
        content.userInfo = ["transfer": id]
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }

    // Banners only help when DropDuo isn't in front; the window already shows the transfer.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        let active = await MainActor.run { NSApp.isActive }
        return active ? [] : [.banner, .list]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let id = response.notification.request.content.userInfo["transfer"] as? String ?? ""
        let action = response.actionIdentifier
        await MainActor.run { handle(action, id: id) }
    }

    private func handle(_ action: String, id: String) {
        let transfer = lookup(id)
        if let text = transfer?.text, action == "copy" {
            copy(text)
        } else if let code = transfer?.text.flatMap({ OneTimeCode.find(in: $0) }), action == "code" {
            copy(code)
        } else if let link = transfer?.text.flatMap(webLink), action == "open" || action == UNNotificationDefaultActionIdentifier {
            NSWorkspace.shared.open(link)
        } else if let path = transfer?.path, transfer?.direction == "Received", FileManager.default.fileExists(atPath: path) {
            NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)])
        } else {
            openApp()
        }
    }

    private func copy(_ text: String) { NSPasteboard.general.clearContents(); NSPasteboard.general.setString(text, forType: .string) }
}
