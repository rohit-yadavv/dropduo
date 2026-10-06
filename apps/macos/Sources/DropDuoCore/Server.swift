import Foundation
import Network

private final class ConnectionBudget: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    func acquire() -> Bool { lock.lock(); defer { lock.unlock() }; guard count < 16 else { return false }; count += 1; return true }
    func release() { lock.lock(); count -= 1; lock.unlock() }
}
public final class PortServer: @unchecked Sendable {
    public var onReady: @Sendable (Int) -> Void = { _ in }
    /// The listener failed, so nothing can connect until it restarts.
    public var onListenerError: @Sendable (NWError) -> Void = { _ in }
    /// One connection was rejected or dropped during the handshake.
    public var onError: @Sendable (String) -> Void = { _ in }
    public var lookup: @Sendable (Hello) async throws -> Data = { _ in throw PortError.invalid("Unknown peer") }
    public var onPeer: @Sendable (String, String, SecureChannel) async -> Void = { _, _, _ in }
    private var listener: NWListener?
    private let budget = ConnectionBudget()
    public init() {}
    public func start(port: UInt16 = 53318, serviceName: String = "DropDuo") throws {
        let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: port)!)
        self.listener = listener
        listener.service = NWListener.Service(name: serviceName, type: "_dropduo._tcp")
        listener.stateUpdateHandler = { [weak self, weak listener] state in
            if case .ready = state { self?.onReady(Int(listener?.port?.rawValue ?? port)) }
            if case let .failed(error) = state { self?.onListenerError(error) }
        }
        listener.newConnectionHandler = { [weak self] connection in
            guard let self, self.budget.acquire() else { connection.cancel(); return }
            Task {
                defer { self.budget.release() }
                let framed = FramedConnection(connection)
                let timeout = Task { try await Task.sleep(nanoseconds: 120_000_000_000); framed.close() }
                do {
                    try await framed.start()
                    let hello = try JSONDecoder().decode(Hello.self, from: await framed.receive(max: 4096))
                    guard hello.version == 1, UUID(uuidString: hello.pairID) != nil, hello.name.utf8.count <= 128,
                          let client = Data(base64Encoded: hello.nonce), client.count == 32 else { throw PortError.invalid("Unsupported or invalid handshake") }
                    let secret = try await self.lookup(hello)
                    guard Wire.verify(hello.proof, key: secret, text: hello.transcript) else { throw PortError.invalid("Authentication failed") }
                    let server = Wire.random(32), serverNonce = server.base64EncodedString()
                    let proof = Wire.hmac(secret, "dropduo/1/welcome|\(hello.pairID)|\(hello.nonce)|\(serverNonce)")
                    try await framed.send(JSONEncoder().encode(Welcome(nonce: serverNonce, proof: proof)))
                    timeout.cancel()
                    let channel = SecureChannel(framed: framed,
                        sendKey: Wire.derive(secret: secret, client: client, server: server, direction: "s2c"),
                        receiveKey: Wire.derive(secret: secret, client: client, server: server, direction: "c2s"))
                    await self.onPeer(hello.pairID, hello.name, channel)
                } catch { timeout.cancel(); framed.close(); self.onError(error.localizedDescription) }
            }
        }
        listener.start(queue: DispatchQueue(label: "app.dropduo.listener"))
    }
    public func stop() { listener?.cancel(); listener = nil }
}
