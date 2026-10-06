import Foundation
import AppKit
import Network
import DropDuoCore

struct Device: Codable, Identifiable { var id: String; var name: String }
struct Transfer: Codable, Identifiable {
    var id: String; var peer: String; var name: String; var direction: String; var state: String
    var progress: Double; var path: String?; var text: String?; var error: String?; var date: Date
    /// Set when a sent file was cut off by a lost connection; it resumes when the peer reconnects.
    var autoRetry: Bool?
}
@MainActor final class AppModel: ObservableObject {
    @Published var targeted = false
    @Published var confirmation: Device?
    @Published var devices: [Device] = []
    @Published var online: Set<String> = []
    @Published var selected: String = ""
    @Published var transfers: [Transfer] = []
    @Published var status = "Starting local connection…"
    @Published var error: String?
    @Published var ticket: Ticket?
    @Published var receiving: Bool = true { didSet { for engine in engines.values { Task { await engine.setReceiving(receiving) } } } }
    @Published var text = ""
    @Published var openAtLogin = LoginItem.isEnabled
    @Published var loginError: String?
    let inboxRoot = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Downloads/DropDuo", isDirectory: true)
    private let stateRoot = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/DropDuo", isDirectory: true)
    private let server = PortServer()
    private var engines: [String: PeerEngine] = [:]
    private var running: Set<String> = []
    private var pending: Ticket?
    private var pendingApproval = false
    private var port = 53318
    private let hostID: String
    let notifier = Notifier()
    /// Set by the app so problems surface as notifications when no DropDuo window is open.
    var windowVisible: () -> Bool = { true }
    init() {
        hostID = UserDefaults.standard.string(forKey: "dropduo.hostID") ?? UUID().uuidString
        #if DEBUG
        // Sample data for UI review without touching saved pairings, the Keychain or the network.
        if ProcessInfo.processInfo.environment["DROPDUO_PREVIEW"] == "1" { seedPreview(); PreviewSnapshot.scheduleIfRequested(); return }
        #endif
        UserDefaults.standard.set(hostID, forKey: "dropduo.hostID")
        try? FileManager.default.createDirectory(at: stateRoot, withIntermediateDirectories: true)
        devices = load("devices.json") ?? []; transfers = load("history.json") ?? []
        for index in transfers.indices where ["Preparing", "Sending", "Receiving"].contains(transfers[index].state) {
            transfers[index].state = "Interrupted"
            if transfers[index].direction == "Sent", transfers[index].path != nil { transfers[index].autoRetry = true }
        }
        receiving = UserDefaults.standard.object(forKey: "dropduo.receiving") as? Bool ?? true
        selected = devices.first?.id ?? ""
        server.lookup = { [weak self] hello in
            guard let self else { throw PortError.invalid("App closed") }; return try await self.authenticate(hello)
        }
        server.onReady = { [weak self] port in Task { @MainActor in self?.port = port; self?.status = "Ready on your local network" } }
        server.onListenerError = { [weak self] error in Task { @MainActor in self?.listenerFailed(error) } }
        server.onPeer = { [weak self] id, name, channel in await self?.connected(id: id, name: name, channel: channel) }
        do { try server.start(serviceName: "DropDuo-" + hostID) } catch { report("DropDuo couldn't start receiving: " + error.localizedDescription) }
        notifier.lookup = { [weak self] id in self?.transfers.first { $0.id == id } }
        notifier.start()
        if !devices.isEmpty { notifier.requestPermission(); LoginItem.enableByDefault() }
    }
    private func listenerFailed(_ error: NWError) {
        if case .posix(.EADDRINUSE) = error {
            status = "Port \(port) is in use. Quit other copies of DropDuo, then reopen it."
        } else {
            status = "Can't receive right now. Check your network, then reopen DropDuo."
        }
    }
    /// Shows an alert in the window, or a notification when the user sent from Finder or the menu bar.
    func report(_ message: String) { if windowVisible() { error = message } else { notifier.problem(message) } }
    private func name(_ peer: String) -> String { devices.first { $0.id == peer }?.name ?? "Your phone" }
    private func unreachable(_ peer: String) -> String {
        "\(name(peer)) isn't reachable. Open DropDuo on the phone and check both devices are on the same Wi-Fi."
    }
    private func load<T: Decodable>(_ name: String) -> T? { guard let data = try? Data(contentsOf: stateRoot.appendingPathComponent(name)) else { return nil }; return try? JSONDecoder().decode(T.self, from: data) }
    private func save<T: Encodable>(_ object: T, _ name: String) { do { try JSONEncoder().encode(object).write(to: stateRoot.appendingPathComponent(name), options: .atomic) } catch { self.error = "Could not save app state: " + error.localizedDescription } }
    func makeTicket() {
        guard let host = localAddresses().first else { error = "Connect your Mac to Wi-Fi or Ethernet first."; return }
        var ticket = Ticket(pairID: UUID().uuidString, host: host, port: port, secret: Wire.random(32).base64EncodedString(),
            name: Host.current().localizedName ?? "Mac", expires: Int64(Date().timeIntervalSince1970) + 300)
        ticket.discoveryName = "DropDuo-" + hostID
        pending = ticket; self.ticket = ticket
    }
    var selectedDevice: Device? { devices.first { $0.id == selected } }
    func isOnline(_ device: Device) -> Bool { online.contains(device.id) }
    func cancelPairing() { pending = nil; ticket = nil }
    func useAddress(_ address: String) { pending?.host = address; ticket = pending }
    func localAddresses() -> [String] {
        var result: [String] = []; var interfaces: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&interfaces) == 0 else { return [] }; defer { freeifaddrs(interfaces) }
        var cursor = interfaces
        while let current = cursor {
            let item = current.pointee; cursor = item.ifa_next
            guard let address = item.ifa_addr, address.pointee.sa_family == UInt8(AF_INET), item.ifa_flags & UInt32(IFF_UP) != 0 else { continue }
            let interface = String(cString: item.ifa_name)
            guard interface.hasPrefix("en") || interface.hasPrefix("bridge") else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(address, socklen_t(address.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 { result.append(String(cString: host)) }
        }
        var seen = Set<String>(); return result.filter { seen.insert($0).inserted }
    }
    private func authenticate(_ hello: Hello) async throws -> Data {
        if let device = devices.first(where: { $0.id == hello.pairID }), let secret = SecureStore.load(device.id) {
            guard Wire.verify(hello.proof, key: secret, text: hello.transcript) else { throw PortError.invalid("Authentication failed") }; return secret
        }
        guard !pendingApproval, let ticket = pending, ticket.pairID == hello.pairID,
            ticket.expires > Int64(Date().timeIntervalSince1970), let secret = Data(base64Encoded: ticket.secret),
            Wire.verify(hello.proof, key: secret, text: hello.transcript) else { throw PortError.invalid("Pairing expired or device not trusted") }
        pendingApproval = true; defer { pendingApproval = false }
        let alert = NSAlert(); alert.messageText = "Pair with \(hello.name)?"
        alert.informativeText = "Approve only if this is the phone that scanned your QR code. It will be allowed to send files and text to this Mac."
        alert.addButton(withTitle: "Pair device"); alert.addButton(withTitle: "Reject")
        NSApp.activate(ignoringOtherApps: true)
        guard alert.runModal() == .alertFirstButtonReturn else { throw PortError.invalid("Pairing declined") }
        guard ticket.expires > Int64(Date().timeIntervalSince1970), pending?.pairID == ticket.pairID else { throw PortError.invalid("Pairing expired; generate a new code") }
        try SecureStore.save(secret, id: hello.pairID)
        devices.append(Device(id: hello.pairID, name: hello.name)); save(devices, "devices.json")
        selected = hello.pairID; pending = nil; self.ticket = nil
        notifier.requestPermission(); LoginItem.enableByDefault()
        return secret
    }
    private func connected(id: String, name: String, channel: SecureChannel) async {
        if let old = engines[id] { await old.stop() }
        do {
            let inbox = try Inbox(root: inboxRoot.appendingPathComponent(id, isDirectory: true)); inbox.cleanExpired()
            let engine = PeerEngine(channel: channel, inbox: inbox) { [weak self] event in Task { @MainActor in self?.record(event, peer: id) } }
            engines[id] = engine; online.insert(id); if selected.isEmpty { selected = id }
            await engine.setReceiving(receiving)
            resumePending(id)
            try? await engine.run()
            if engines[id] === engine { engines.removeValue(forKey: id); online.remove(id) }
        } catch { self.error = error.localizedDescription; await channel.close() }
    }
    private func record(_ event: PeerEvent, peer: String) {
        let finished = event.state == "Complete" && transfers.first(where: { $0.id == event.id && $0.peer == peer })?.state != "Complete"
        if let index = transfers.firstIndex(where: { $0.id == event.id && $0.peer == peer }) {
            transfers[index].state = event.state; transfers[index].progress = event.progress
            transfers[index].path = event.path ?? transfers[index].path
            transfers[index].text = event.text ?? transfers[index].text; transfers[index].error = event.error
            if ["Complete", "Cancelled"].contains(event.state) { transfers[index].autoRetry = nil }
        } else {
            transfers.insert(Transfer(id: event.id, peer: peer, name: event.name, direction: event.direction, state: event.state,
                progress: event.progress, path: event.path, text: event.text, error: event.error, date: Date()), at: 0)
        }
        transfers = Array(transfers.prefix(100))
        if finished, let transfer = transfers.first(where: { $0.id == event.id && $0.peer == peer }) {
            if transfer.direction == "Received" { notifier.received(transfer, from: name(peer)) } else { notifier.sent(transfer, to: name(peer)) }
        }
        if ["Complete", "Interrupted", "Cancelled"].contains(event.state) { save(transfers, "history.json") }
    }
    func chooseFiles() { let panel = NSOpenPanel(); panel.allowsMultipleSelection = true; panel.canChooseDirectories = false; if panel.runModal() == .OK { send(panel.urls) } }
    func send(_ urls: [URL]) {
        guard !devices.isEmpty else { report("Pair your phone first: choose Pair a Device in DropDuo."); return }
        let files = urls.filter { (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) != true }
        if files.count < urls.count { report(files.isEmpty ? "Folders can't be sent. Open the folder and choose the files inside." : "Folders were skipped. Only files can be sent.") }
        if !files.isEmpty { send(files.map { ($0, UUID().uuidString) }, to: selected) }
    }
    /// Sends one file at a time so resumed batches stay within the peer's active-transfer limit.
    private func send(_ files: [(url: URL, id: String)], to peer: String) {
        guard let engine = engines[peer] else { report(unreachable(peer)); return }
        let files = files.filter { running.insert($0.id).inserted }
        Task { for (file, id) in files {
            if transfers.first(where: { $0.id == id && $0.peer == peer })?.state == "Cancelled" { running.remove(id); continue }
            record(PeerEvent(id: id, name: file.lastPathComponent, direction: "Sent", state: "Preparing", path: file.path), peer: peer)
            do { try await engine.sendFile(file, id: id); running.remove(id) } catch {
                running.remove(id)
                let interrupted = (error as? PortError)?.isInterruption == true
                if let index = transfers.firstIndex(where: { $0.id == id && $0.peer == peer }) { transfers[index].autoRetry = interrupted ? true : nil; save(transfers, "history.json") }
                if !interrupted { report(error.localizedDescription) }
                // The phone may have reconnected while this attempt was still failing on the old connection.
                else if let current = engines[peer], current !== engine { resumePending(peer) }
            }
        } }
    }
    private func resumePending(_ peer: String) {
        let pending = transfers.filter { $0.peer == peer && $0.direction == "Sent" && $0.state == "Interrupted" && $0.autoRetry == true && !running.contains($0.id) }
            .reversed().compactMap { transfer in transfer.path.map { (url: URL(fileURLWithPath: $0), id: transfer.id) } }
        if !pending.isEmpty { send(Array(pending), to: peer) }
    }
    func sendText() { guard let engine = engines[selected] else { error = unreachable(selected); return }; let value = text; Task { do { try await engine.sendText(value); text = "" } catch { self.error = error.localizedDescription } } }
    func cancel(_ transfer: Transfer) {
        if let index = transfers.firstIndex(where: { $0.id == transfer.id && $0.peer == transfer.peer }), transfers[index].autoRetry == true {
            transfers[index].autoRetry = nil; transfers[index].state = "Cancelled"; save(transfers, "history.json")
        }
        if let engine = engines[transfer.peer] { Task { await engine.cancel(transfer.id) } } }
    func retry(_ transfer: Transfer) { guard let path = transfer.path else { return }; selected = transfer.peer; send([(URL(fileURLWithPath: path), transfer.id)], to: transfer.peer) }
    func forget(_ device: Device) {
        let engine = engines.removeValue(forKey: device.id); Task { await engine?.stop() }
        online.remove(device.id); SecureStore.remove(device.id); devices.removeAll { $0.id == device.id }; save(devices, "devices.json")
        if selected == device.id { selected = devices.first?.id ?? "" }
    }
    func setOpenAtLogin(_ enabled: Bool) {
        loginError = nil
        do {
            try LoginItem.set(enabled)
            if enabled, LoginItem.needsApproval {
                loginError = "Allow DropDuo in System Settings → General → Login Items."
                LoginItem.openSystemSettings()
            }
        } catch {
            loginError = "macOS didn't allow this. Move DropDuo to Applications, open it from there, and try again."
        }
        openAtLogin = LoginItem.isEnabled || LoginItem.needsApproval
    }
    func persistSettings() { UserDefaults.standard.set(receiving, forKey: "dropduo.receiving") }
    func clearHistory() { transfers.removeAll { !["Preparing", "Sending", "Receiving"].contains($0.state) }; save(transfers, "history.json") }
    #if DEBUG
    private func seedPreview() {
        let scene = ProcessInfo.processInfo.environment["DROPDUO_PREVIEW_SCENE"] ?? "device"
        if scene == "welcome" { status = "Ready on your local network"; return }
        defer { if scene == "pairing" { makeTicket() }; if scene == "offline" { online = [] }; if scene == "empty" { transfers = [] } }
        let phone = Device(id: "preview-phone", name: "motorola edge 40 neo")
        devices = [phone, Device(id: "preview-tablet", name: "Pixel Tablet")]; online = [phone.id]; selected = phone.id
        status = "Ready on your local network"
        let now = Date()
        transfers = [
            Transfer(id: "5", peer: phone.id, name: "Quarterly report.pdf", direction: "Sent", state: "Sending", progress: 0.62, path: "/tmp/Quarterly report.pdf", date: now),
            Transfer(id: "4", peer: phone.id, name: "Text", direction: "Received", state: "Complete", progress: 1, text: "https://dropduo.app/download", date: now.addingTimeInterval(-60)),
            Transfer(id: "3", peer: phone.id, name: "IMG_2041.jpg", direction: "Received", state: "Complete", progress: 1, path: "/tmp/IMG_2041.jpg", date: now.addingTimeInterval(-600)),
            Transfer(id: "2", peer: phone.id, name: "Holiday clip.mov", direction: "Sent", state: "Interrupted", progress: 0.3, path: "/tmp/Holiday clip.mov", error: "Connection interrupted", date: now.addingTimeInterval(-3600)),
            Transfer(id: "1", peer: phone.id, name: "Text", direction: "Sent", state: "Complete", progress: 1, text: "Meeting notes are in the shared folder, check the second page.", date: now.addingTimeInterval(-90000))
        ]
    }
    #endif
}
