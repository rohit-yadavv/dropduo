import Foundation
import AppKit
import DropDuoCore

struct Device: Codable, Identifiable { var id: String; var name: String }
struct Transfer: Codable, Identifiable {
    var id: String; var peer: String; var name: String; var direction: String; var state: String
    var progress: Double; var path: String?; var text: String?; var error: String?; var date: Date
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
    @Published var route = "Share"
    let inboxRoot = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Downloads/DropDuo", isDirectory: true)
    private let stateRoot = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support/DropDuo", isDirectory: true)
    private let server = PortServer()
    private var engines: [String: PeerEngine] = [:]
    private var pending: Ticket?
    private var pendingApproval = false
    private var port = 53318
    private let hostID: String
    init() {
        hostID = UserDefaults.standard.string(forKey: "dropduo.hostID") ?? UUID().uuidString
        UserDefaults.standard.set(hostID, forKey: "dropduo.hostID")
        try? FileManager.default.createDirectory(at: stateRoot, withIntermediateDirectories: true)
        devices = load("devices.json") ?? []; transfers = load("history.json") ?? []
        for index in transfers.indices where ["Preparing", "Sending", "Receiving"].contains(transfers[index].state) { transfers[index].state = "Interrupted" }
        receiving = UserDefaults.standard.object(forKey: "dropduo.receiving") as? Bool ?? true
        selected = devices.first?.id ?? ""
        server.lookup = { [weak self] hello in
            guard let self else { throw PortError.invalid("App closed") }; return try await self.authenticate(hello)
        }
        server.onReady = { [weak self] port in Task { @MainActor in self?.port = port; self?.status = "Ready on your local network" } }
        server.onError = { [weak self] message in Task { @MainActor in self?.status = message } }
        server.onPeer = { [weak self] id, name, channel in await self?.connected(id: id, name: name, channel: channel) }
        do { try server.start(serviceName: "DropDuo-" + hostID) } catch { self.error = error.localizedDescription }
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
        selected = hello.pairID; pending = nil; self.ticket = nil; return secret
    }
    private func connected(id: String, name: String, channel: SecureChannel) async {
        if let old = engines[id] { await old.stop() }
        do {
            let inbox = try Inbox(root: inboxRoot.appendingPathComponent(id, isDirectory: true)); inbox.cleanExpired()
            let engine = PeerEngine(channel: channel, inbox: inbox) { [weak self] event in Task { @MainActor in self?.record(event, peer: id) } }
            engines[id] = engine; online.insert(id); if selected.isEmpty { selected = id }
            await engine.setReceiving(receiving)
            do { try await engine.run() } catch { status = "Connection ended. Your phone will reconnect when reachable." }
            if engines[id] === engine { engines.removeValue(forKey: id); online.remove(id) }
        } catch { self.error = error.localizedDescription; await channel.close() }
    }
    private func record(_ event: PeerEvent, peer: String) {
        if let index = transfers.firstIndex(where: { $0.id == event.id && $0.peer == peer }) {
            transfers[index].state = event.state; transfers[index].progress = event.progress
            transfers[index].path = event.path ?? transfers[index].path
            transfers[index].text = event.text ?? transfers[index].text; transfers[index].error = event.error
        } else {
            transfers.insert(Transfer(id: event.id, peer: peer, name: event.name, direction: event.direction, state: event.state,
                progress: event.progress, path: event.path, text: event.text, error: event.error, date: Date()), at: 0)
        }
        transfers = Array(transfers.prefix(100))
        if ["Complete", "Interrupted", "Cancelled"].contains(event.state) { save(transfers, "history.json") }
    }
    func chooseFiles() { let panel = NSOpenPanel(); panel.allowsMultipleSelection = true; panel.canChooseDirectories = false; if panel.runModal() == .OK { send(panel.urls) } }
    func send(_ files: [URL], retryID: String? = nil) {
        guard let engine = engines[selected] else { error = "Your phone is offline. Open DropDuo on it and connect to the same local network."; return }
        let peer = selected
        Task { for file in files {
            let id = retryID ?? UUID().uuidString
            record(PeerEvent(id: id, name: file.lastPathComponent, direction: "Sent", state: "Preparing", path: file.path), peer: peer)
            do { try await engine.sendFile(file, id: id) } catch { self.error = error.localizedDescription }
        } }
    }
    func sendText() { guard let engine = engines[selected] else { error = "Connect your phone first."; return }; let value = text; Task { do { try await engine.sendText(value); text = "" } catch { self.error = error.localizedDescription } } }
    func cancel(_ transfer: Transfer) { if let engine = engines[transfer.peer] { Task { await engine.cancel(transfer.id) } } }
    func retry(_ transfer: Transfer) { guard let path = transfer.path else { return }; selected = transfer.peer; send([URL(fileURLWithPath: path)], retryID: transfer.id) }
    func forget(_ device: Device) {
        let engine = engines.removeValue(forKey: device.id); Task { await engine?.stop() }
        online.remove(device.id); SecureStore.remove(device.id); devices.removeAll { $0.id == device.id }; save(devices, "devices.json")
        if selected == device.id { selected = devices.first?.id ?? "" }
    }
    func persistSettings() { UserDefaults.standard.set(receiving, forKey: "dropduo.receiving") }
    func clearHistory() { transfers.removeAll { !["Preparing", "Sending", "Receiving"].contains($0.state) }; save(transfers, "history.json") }
}
