import Foundation

public final class Inbox {
    public let root: URL
    public let partials: URL
    private let fm = FileManager.default
    public init(root: URL) throws {
        self.root = root; partials = root.appendingPathComponent(".partial", isDirectory: true)
        try fm.createDirectory(at: partials, withIntermediateDirectories: true)
    }
    public static func validate(_ offer: Message) throws {
        guard let id = offer.id, UUID(uuidString: id) != nil, let name = offer.name, !name.isEmpty,
            name != ".", name != "..", name.utf8.count <= 240,
            !name.contains("/"), !name.contains("\\"), !name.unicodeScalars.contains(where: { $0.value < 32 }),
            let size = offer.size, size >= 0, size <= Wire.maxFile,
            let hash = offer.sha256, hash.count == 64, hash.allSatisfy({ "0123456789abcdef".contains($0) })
        else { throw PortError.invalid("Invalid file metadata") }
    }
    private func url(_ id: String, _ suffix: String) throws -> URL {
        guard UUID(uuidString: id) != nil else { throw PortError.invalid("Invalid transfer ID") }
        return partials.appendingPathComponent(id + suffix)
    }
    public func destination(_ offer: Message) -> URL { root.appendingPathComponent(offer.id! + "-" + offer.name!) }
    public func prepare(_ offer: Message) throws -> Int64 {
        try Self.validate(offer)
        let metadata = try url(offer.id!, ".json"), part = try url(offer.id!, ".part")
        if fm.fileExists(atPath: destination(offer).path) {
            guard try Wire.hash(destination(offer)) == offer.sha256 else { throw PortError.invalid("Completed file changed") }
            return offer.size!
        }
        if let bytes = try? Data(contentsOf: metadata) {
            let old = try JSONDecoder().decode(Message.self, from: bytes)
            guard old.name == offer.name, old.size == offer.size, old.sha256 == offer.sha256 else { throw PortError.invalid("Resume metadata mismatch") }
        } else { try JSONEncoder().encode(offer).write(to: metadata, options: .atomic) }
        if !fm.fileExists(atPath: part.path) { fm.createFile(atPath: part.path, contents: nil) }
        let offset = (try fm.attributesOfItem(atPath: part.path)[.size] as? NSNumber)?.int64Value ?? 0
        guard offset <= offer.size! else { throw PortError.invalid("Invalid partial file size") }
        let available = (try fm.attributesOfFileSystem(forPath: root.path)[.systemFreeSize] as? NSNumber)?.int64Value ?? 0
        guard offer.size! - offset < available else { throw PortError.invalid("Not enough storage") }
        return offset
    }
    public func append(_ offer: Message, offset: Int64, data: Data) throws -> Int64 {
        guard data.count <= Wire.chunkSize, !data.isEmpty, offset >= 0, offset + Int64(data.count) <= offer.size! else { throw PortError.invalid("Invalid chunk") }
        let part = try url(offer.id!, ".part")
        let handle = try FileHandle(forWritingTo: part); defer { try? handle.close() }
        guard try handle.seekToEnd() == UInt64(offset) else { throw PortError.invalid("Chunk offset mismatch") }
        try handle.write(contentsOf: data); try handle.synchronize()
        return offset + Int64(data.count)
    }
    public func finish(_ offer: Message) throws -> URL {
        let target = destination(offer)
        if fm.fileExists(atPath: target.path) { return target }
        let part = try url(offer.id!, ".part")
        let size = (try fm.attributesOfItem(atPath: part.path)[.size] as? NSNumber)?.int64Value
        guard size == offer.size, try Wire.hash(part) == offer.sha256 else {
            try cancel(offer.id!); throw PortError.invalid("Integrity verification failed; retry the file")
        }
        try fm.moveItem(at: part, to: target); try? fm.removeItem(at: url(offer.id!, ".json")); return target
    }
    public func cancel(_ id: String) throws {
        for suffix in [".part", ".json"] { try? fm.removeItem(at: url(id, suffix)) }
    }
    public func cleanExpired() {
        let cutoff = Date().addingTimeInterval(-7 * 86400)
        for file in (try? fm.contentsOfDirectory(at: partials, includingPropertiesForKeys: [.contentModificationDateKey])) ?? [] {
            if let date = try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate, date < cutoff { try? fm.removeItem(at: file) }
        }
    }
}
