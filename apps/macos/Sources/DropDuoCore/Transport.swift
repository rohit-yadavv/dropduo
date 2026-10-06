import Foundation
import Network

private final class StartGate: @unchecked Sendable {
    private let lock = NSLock()
    private var finished = false
    func claim() -> Bool { lock.lock(); defer { lock.unlock() }; if finished { return false }; finished = true; return true }
}
public final class FramedConnection: @unchecked Sendable {
    public let connection: NWConnection
    private let queue = DispatchQueue(label: "app.dropduo.connection")
    public init(_ connection: NWConnection) { self.connection = connection }
    public func start() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let gate = StartGate()
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: if gate.claim() { continuation.resume() }
                case .failed(let error): if gate.claim() { continuation.resume(throwing: error) }
                case .cancelled: if gate.claim() { continuation.resume(throwing: PortError.invalid("Connection closed")) }
                default: break
                }
            }
            connection.start(queue: queue)
        }
    }
    public func close() { connection.cancel() }
    private func read(_ count: Int) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            connection.receive(minimumIncompleteLength: count, maximumLength: count) { data, _, _, error in
                if let error { continuation.resume(throwing: error) }
                else if let data, data.count == count { continuation.resume(returning: data) }
                else { continuation.resume(throwing: PortError.invalid("Connection closed")) }
            }
        }
    }
    public func receive(max: Int = Wire.maxFrame) async throws -> Data {
        let header = try await read(4)
        let length = header.reduce(0) { ($0 << 8) | Int($1) }
        guard length > 0, length <= max else { throw PortError.invalid("Invalid frame size") }
        return try await read(length)
    }
    public func send(_ data: Data) async throws {
        guard !data.isEmpty, data.count <= Wire.maxFrame else { throw PortError.invalid("Invalid frame size") }
        var size = UInt32(data.count).bigEndian
        let bytes = Data(bytes: &size, count: 4) + data
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: bytes, completion: .contentProcessed { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            })
        }
    }
}
public actor SecureChannel {
    private let framed: FramedConnection
    private var sender: FrameCipher
    private var receiver: FrameCipher
    private var sendTail: Task<Void, Error>?
    public init(framed: FramedConnection, sendKey: Data, receiveKey: Data) {
        self.framed = framed; sender = FrameCipher(key: sendKey); receiver = FrameCipher(key: receiveKey)
    }
    public func send(_ message: Message) async throws {
        let frame = try sender.seal(Wire.encode(message))
        let previous = sendTail
        let connection = framed
        let write = Task { try await previous?.value; try await connection.send(frame) }
        sendTail = write
        try await write.value
    }
    public func receive() async throws -> Message {
        let frame = try await framed.receive()
        return try JSONDecoder().decode(Message.self, from: receiver.open(frame))
    }
    public func close() { framed.close() }
}
public struct PeerEvent: Sendable {
    public var id: String
    public var name: String
    public var direction: String
    public var state: String
    public var progress: Double
    public var path: String?
    public var text: String?
    public var error: String?
    public init(id: String, name: String, direction: String, state: String, progress: Double = 0, path: String? = nil, text: String? = nil, error: String? = nil) {
        self.id = id; self.name = name; self.direction = direction; self.state = state
        self.progress = progress; self.path = path; self.text = text; self.error = error
    }
}
public actor PeerEngine {
    private let channel: SecureChannel
    private let inbox: Inbox
    private let event: @Sendable (PeerEvent) -> Void
    private var incoming: [String: Message] = [:]
    private var responses: [String: Message] = [:]
    private var cancelled: Set<String> = []
    private var active: Set<String> = []
    private var alive = true
    public var receivingEnabled = true
    public init(channel: SecureChannel, inbox: Inbox, event: @escaping @Sendable (PeerEvent) -> Void) {
        self.channel = channel; self.inbox = inbox; self.event = event
    }
    public func setReceiving(_ enabled: Bool) { receivingEnabled = enabled }
    static let connectionLost = "Connection lost. It resumes when the devices reconnect."
    static let paused = "Receiving is paused on the other device. Turn it back on there, then retry."
    static let textTooLong = "Text is too long to send. Share it as a file instead."
    public func run() async throws {
        defer {
            alive = false
            for offer in incoming.values { event(PeerEvent(id: offer.id!, name: offer.name!, direction: "Received", state: "Interrupted", error: "Connection lost. The sender can retry when the devices reconnect.")) }
            incoming.removeAll()
        }
        while alive { try await handle(channel.receive()) }
    }
    public func stop() async { alive = false; await channel.close() }
    public func cancel(_ id: String) async {
        guard active.contains(id) || incoming[id] != nil else { return }
        cancelled.insert(id); incoming.removeValue(forKey: id); try? inbox.cancel(id)
        try? await channel.send(Message("cancel", id: id))
        event(PeerEvent(id: id, name: "Transfer", direction: "", state: "Cancelled"))
        if !active.contains(id) { cancelled.remove(id) }
    }
    private func wait(_ id: String) async throws -> Message {
        let deadline = Date().addingTimeInterval(60)
        while alive && Date() < deadline {
            if cancelled.contains(id) { throw PortError.invalid("Transfer cancelled") }
            if let message = responses.removeValue(forKey: id) {
                if message.type == "error" { throw PortError.invalid(message.error ?? "Transfer rejected") }
                return message
            }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        throw PortError.interrupted(Self.connectionLost)
    }
    public func sendFile(_ file: URL, id: String = UUID().uuidString) async throws {
        guard !active.contains(id), active.count < 4 else { throw PortError.invalid("Transfer already active") }
        active.insert(id); cancelled.remove(id); responses.removeValue(forKey: id); defer { active.remove(id); cancelled.remove(id); responses.removeValue(forKey: id) }
        do {
            let name = file.lastPathComponent
            event(PeerEvent(id: id, name: name, direction: "Sent", state: "Preparing"))
            let attributes = try FileManager.default.attributesOfItem(atPath: file.path)
            guard attributes[.type] as? FileAttributeType == .typeRegular, let number = attributes[.size] as? NSNumber
            else { throw PortError.invalid("Only files can be sent, not folders") }
            guard number.int64Value <= Wire.maxFile else { throw PortError.invalid("Files larger than 32 GB can't be sent") }
            let size = number.int64Value
            let hash = try await Task.detached { try Wire.hash(file) }.value
            if cancelled.contains(id) { throw PortError.invalid("Transfer cancelled") }
            let offer = Message("offer", id: id, name: name, size: size, sha256: hash)
            try Inbox.validate(offer); try await channel.send(offer)
            let response = try await wait(id)
            guard response.type == "accept", let offset = response.offset, offset >= 0, offset <= size else { throw PortError.invalid("Invalid resume response") }
            let handle = try FileHandle(forReadingFrom: file); defer { try? handle.close() }
            try handle.seek(toOffset: UInt64(offset)); var sent = offset
            while sent < size {
                if cancelled.contains(id) { throw PortError.invalid("Transfer cancelled") }
                guard let chunk = try handle.read(upToCount: min(Wire.chunkSize, Int(size - sent))), !chunk.isEmpty else { throw PortError.invalid("Source file changed") }
                try await channel.send(Message("chunk", id: id, offset: sent, data: chunk.base64EncodedString()))
                let ack = try await wait(id)
                guard ack.type == "ack", ack.offset == sent + Int64(chunk.count) else { throw PortError.invalid("Invalid transfer acknowledgement") }
                sent += Int64(chunk.count)
                event(PeerEvent(id: id, name: name, direction: "Sent", state: "Sending", progress: size == 0 ? 1 : Double(sent)/Double(size)))
            }
            try await channel.send(Message("finish", id: id))
            guard try await wait(id).type == "complete" else { throw PortError.invalid("File was not confirmed") }
            event(PeerEvent(id: id, name: name, direction: "Sent", state: "Complete", progress: 1, path: file.path))
        } catch {
            let wasCancelled = cancelled.contains(id)
            // Connection loss is resumable; rejections and local failures are not.
            let lost = !wasCancelled && (!alive || error is NWError || (error as? PortError)?.isInterruption == true)
            event(PeerEvent(id: id, name: file.lastPathComponent, direction: "Sent", state: wasCancelled ? "Cancelled" : "Interrupted", path: file.path, error: lost ? Self.connectionLost : error.localizedDescription))
            if lost { throw PortError.interrupted(Self.connectionLost) }
            throw error
        }
    }
    public func sendText(_ text: String) async throws {
        guard !text.isEmpty else { throw PortError.invalid("Type something to send") }
        guard text.utf8.count <= 64_000 else { throw PortError.invalid(Self.textTooLong) }
        let id = UUID().uuidString
        active.insert(id); defer { active.remove(id); responses.removeValue(forKey: id) }
        try await channel.send(Message("text", id: id, text: text))
        guard try await wait(id).type == "complete" else { throw PortError.invalid("Text was not confirmed") }
        event(PeerEvent(id: id, name: "Text", direction: "Sent", state: "Complete", progress: 1, text: text))
    }
    private func handle(_ message: Message) async throws {
        guard let id = message.id, UUID(uuidString: id) != nil else { throw PortError.invalid("Invalid message ID") }
        switch message.type {
        case "accept", "ack", "complete", "error": if active.contains(id) { responses[id] = message }
        case "cancel":
            guard active.contains(id) || incoming[id] != nil else { return }
            cancelled.insert(id); incoming.removeValue(forKey: id); try? inbox.cancel(id)
            event(PeerEvent(id: id, name: "Transfer", direction: "", state: "Cancelled"))
            if !active.contains(id) { cancelled.remove(id) }
        default:
            do {
                switch message.type {
                case "offer":
                    guard receivingEnabled else { throw PortError.invalid(Self.paused) }
                    guard incoming.count < 4 else { throw PortError.invalid("The other device is already receiving 4 files. Retry when they finish.") }
                    let offset = try inbox.prepare(message); incoming[id] = message
                    event(PeerEvent(id: id, name: message.name!, direction: "Received", state: "Receiving", progress: 0))
                    try await channel.send(Message("accept", id: id, offset: offset))
                case "chunk":
                    guard let offer = incoming[id], let offset = message.offset, let encoded = message.data,
                        let bytes = Data(base64Encoded: encoded) else { throw PortError.invalid("Unknown transfer or invalid chunk") }
                    let next = try inbox.append(offer, offset: offset, data: bytes)
                    try await channel.send(Message("ack", id: id, offset: next))
                    event(PeerEvent(id: id, name: offer.name!, direction: "Received", state: "Receiving", progress: Double(next)/Double(max(1, offer.size!))))
                case "finish":
                    guard let offer = incoming[id] else { throw PortError.invalid("Unknown transfer") }
                    let file = try inbox.finish(offer)
                    incoming.removeValue(forKey: id)
                    try await channel.send(Message("complete", id: id))
                    event(PeerEvent(id: id, name: offer.name!, direction: "Received", state: "Complete", progress: 1, path: file.path))
                case "text":
                    guard receivingEnabled else { throw PortError.invalid(Self.paused) }
                    guard let text = message.text, text.utf8.count <= 64_000 else { throw PortError.invalid(Self.textTooLong) }
                    event(PeerEvent(id: id, name: "Text", direction: "Received", state: "Complete", progress: 1, text: text))
                    try await channel.send(Message("complete", id: id))
                default: throw PortError.invalid("Unsupported message")
                }
            } catch {
                if let offer = incoming.removeValue(forKey: id) { event(PeerEvent(id: id, name: offer.name!, direction: "Received", state: "Interrupted", error: error.localizedDescription)) }
                try await channel.send(Message("error", id: id, error: error.localizedDescription))
            }
        }
    }
}
