import Foundation
import DropDuoCore

func fixture() throws {
    let secret = Data((0..<32).map(UInt8.init)), client = Data(repeating: 1, count: 32), server = Data(repeating: 2, count: 32)
    let key = Wire.derive(secret: secret, client: client, server: server, direction: "c2s")
    var cipher = FrameCipher(key: key)
    let plain = Data("DropDuo compatibility fixture".utf8)
    let fixture: [String: String] = ["secret": secret.base64EncodedString(), "client": client.base64EncodedString(), "server": server.base64EncodedString(), "key": key.base64EncodedString(), "plain": plain.base64EncodedString(), "frame": try cipher.seal(plain, nonce: Data(repeating: 3, count: 12)).base64EncodedString(), "helloProof": Wire.hmac(secret, "dropduo/1/hello|fixture|nonce|Android")]
    print(String(data: try JSONSerialization.data(withJSONObject: fixture, options: [.prettyPrinted, .sortedKeys]), encoding: .utf8)!)
}
@MainActor final class InteropHost {
    let directory: URL
    let server = PortServer()
    let secret = Wire.random(32)
    let pairID = "11111111-1111-4111-8111-111111111111"
    let outboundID = "22222222-2222-4222-8222-222222222222"
    let inboundID = "33333333-3333-4333-8333-333333333333"
    var received = false
    var textReceived = false
    var sent = false
    init(directory: URL) { self.directory = directory }
    func start() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let source = directory.appendingPathComponent("mac.bin")
        let bytes = Data((0..<800123).map { UInt8($0 % 251) })
        try bytes.write(to: source)
        let inbox = try Inbox(root: directory.appendingPathComponent("received"))
        let offer = Message("offer", id: inboundID, name: "android.bin", size: Int64(bytes.count), sha256: try Wire.hash(source))
        _ = try inbox.prepare(offer); _ = try inbox.append(offer, offset: 0, data: bytes.prefix(120_000))
        let hash = try Wire.hash(source)
        try JSONSerialization.data(withJSONObject: ["hash": hash, "size": bytes.count]).write(to: directory.appendingPathComponent("expected.json"))
        server.lookup = { [weak self] hello in
            guard let self else { throw PortError.invalid("Test stopped") }
            let secret = self.secret
            guard hello.pairID == self.pairID, Wire.verify(hello.proof, key: secret, text: hello.transcript) else { throw PortError.invalid("Authentication failed") }
            return secret
        }
        server.onError = { message in print("Interop connection: " + message) }
        server.onReady = { [weak self] port in Task { @MainActor in
            guard let self else { return }
            let ticket = Ticket(pairID: self.pairID, host: "127.0.0.1", port: port, secret: self.secret.base64EncodedString(), name: "Interop Mac", expires: Int64(Date().timeIntervalSince1970) + 300)
            do { try ticket.code().write(to: self.directory.appendingPathComponent("ticket.txt"), atomically: true, encoding: .utf8)
            try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: self.directory.appendingPathComponent("ticket.txt").path) } catch { print("Failed to write test ticket") }
        } }
        server.onPeer = { [weak self] _, _, channel in
            guard let self else { return }; await self.run(channel, source: source, inbox: inbox, expectedHash: hash)
        }
        try server.start(port: 0, serviceName: "DropDuo-Interop")
    }
    func run(_ channel: SecureChannel, source: URL, inbox: Inbox, expectedHash: String) async {
        let engine = PeerEngine(channel: channel, inbox: inbox) { [weak self] event in Task { @MainActor in
            guard let self else { return }
            if event.direction == "Received", event.state == "Complete", let path = event.path {
                if (try? Wire.hash(URL(fileURLWithPath: path))) == expectedHash { self.received = true }
            }
            if event.direction == "Received", event.text == "Android to Mac text" { self.textReceived = true }
            self.finishIfReady()
        } }
        let receive = Task { try await engine.run() }
        do {
            try await engine.sendFile(source, id: outboundID)
            try await engine.sendText("Mac to Android text")
            sent = true; finishIfReady()
            let deadline = Date().addingTimeInterval(30)
            while !(received && textReceived), Date() < deadline { try await Task.sleep(nanoseconds: 10_000_000) }
            guard received && textReceived else { throw PortError.invalid("Interop receive deadline exceeded") }
            finishIfReady()
            try await engine.sendText("Interop complete")
            _ = try await receive.value
        } catch { print("Interop: " + error.localizedDescription); await engine.stop(); receive.cancel() }
    }
    func finishIfReady() {
        if received && textReceived && sent { try? "PASS: Swift/JVM bidirectional files, resume, text and checksums".write(to: directory.appendingPathComponent("passed.txt"), atomically: true, encoding: .utf8) }
    }
}
if CommandLine.arguments.count == 3, CommandLine.arguments[1] == "serve" {
    let host = InteropHost(directory: URL(fileURLWithPath: CommandLine.arguments[2]))
    try host.start()
    try await Task.sleep(nanoseconds: 300_000_000_000)
} else { try fixture() }
