import Foundation
import CryptoKit

/// What a send reads: a file as it is, or a folder packed into one archive that the receiver unpacks.
/// Packing happens while reading, so sending a folder needs no extra disk space, and resuming reads from any offset.
///
/// Folder archive: "DDF1", then for each item a kind byte (1 folder, 2 file), a 2-byte path length and the UTF-8 path
/// inside the folder, "/"-separated; files add an 8-byte size and their contents. A 0 byte ends it. Integers are
/// big-endian, and a folder comes before its contents.
public struct Payload: Sendable {
    struct Part: Sendable { var start: Int64; var header: Data; var file: URL?; var size: Int64 }
    private var parts: [Part] = []
    public private(set) var size: Int64 = 0
    public let isFolder: Bool
    public static let maxPath = 2048

    public init(file: URL, size: Int64) { isFolder = false; append(Data(), file: file, size: size) }

    public init(folder: URL) throws {
        isFolder = true
        append(Data("DDF1".utf8))
        try add(folder, path: "")
        append(Data([0]))
    }

    /// Whether the phone can save an item with this name.
    public static func isValid(_ name: String) -> Bool {
        !name.isEmpty && name != "." && name != ".." && !name.contains("/") && name.utf8.count <= 255
            && !name.unicodeScalars.contains { $0.value < 32 }
    }

    private mutating func append(_ header: Data, file: URL? = nil, size: Int64 = 0) {
        parts.append(Part(start: self.size, header: header, file: file, size: size))
        self.size += Int64(header.count) + size
    }

    private mutating func add(_ folder: URL, path: String) throws {
        let keys: Set<URLResourceKey> = [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey]
        // macOS may hand back decomposed names; the phone gets the composed form other systems expect.
        let items = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: Array(keys))
            .map { (url: $0, name: $0.lastPathComponent.precomposedStringWithCanonicalMapping) }.sorted { $0.name < $1.name }
        for (item, name) in items {
            // Finder's view settings and custom folder icons mean nothing on the phone.
            if name == ".DS_Store" || name == "Icon\r" { continue }
            let values = try item.resourceValues(forKeys: keys)
            // Links could point outside the folder or back into it.
            if values.isSymbolicLink == true { continue }
            let child = path.isEmpty ? name : path + "/" + name
            guard Self.isValid(name), child.utf8.count <= Self.maxPath else {
                throw PortError.invalid("“\(name)” has a name your phone can't save. Rename it, then send the folder again.")
            }
            if values.isDirectory == true {
                append(Self.header(1, child))
                try add(item, path: child)
            } else if values.isRegularFile == true {
                let size = Int64(values.fileSize ?? 0)
                append(Self.header(2, child) + Wire.integer(UInt64(size)), file: item, size: size)
            }
            guard self.size <= Wire.maxFile else { throw PortError.invalid("Folders larger than 32 GB can't be sent") }
        }
    }

    private static func header(_ kind: UInt8, _ path: String) -> Data {
        let bytes = Data(path.utf8)
        return Data([kind, UInt8(bytes.count >> 8), UInt8(bytes.count & 0xFF)]) + bytes
    }

    public func hash() throws -> String {
        let reader = Reader(self)
        var hash = SHA256(), offset: Int64 = 0
        while offset < size {
            let data = try reader.read(at: offset, count: Wire.chunkSize)
            hash.update(data: data); offset += Int64(data.count)
        }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// The last part starting at or before `offset`; empty parts share a start with the next one and are skipped.
    fileprivate func index(containing offset: Int64) -> Int {
        var low = 0, high = parts.count - 1
        while low < high {
            let middle = (low + high + 1) / 2
            if parts[middle].start <= offset { low = middle } else { high = middle - 1 }
        }
        return low
    }

    /// Reads the payload in order, keeping the current file open between reads.
    public final class Reader {
        private let payload: Payload
        private var current: (index: Int, handle: FileHandle)?
        public init(_ payload: Payload) { self.payload = payload }
        deinit { try? current?.handle.close() }

        /// `count` bytes from `offset`, or fewer at the end of the payload.
        public func read(at offset: Int64, count: Int) throws -> Data {
            var result = Data(), position = offset
            var index = payload.index(containing: offset)
            while result.count < count, index < payload.parts.count {
                let part = payload.parts[index]
                let local = position - part.start, headerSize = Int64(part.header.count)
                if local < headerSize {
                    let take = min(Int64(count - result.count), headerSize - local)
                    result += part.header.subdata(in: Int(local)..<Int(local + take)); position += take
                } else if local - headerSize < part.size, let file = part.file {
                    let want = Int(min(Int64(count - result.count), part.size - (local - headerSize)))
                    let handle = try handle(index, file)
                    try handle.seek(toOffset: UInt64(local - headerSize))
                    guard let data = try handle.read(upToCount: want), data.count == want else {
                        throw PortError.invalid("“\(file.lastPathComponent)” changed while sending. Send it again.")
                    }
                    result += data; position += Int64(want)
                } else {
                    index += 1
                }
            }
            return result
        }

        private func handle(_ index: Int, _ file: URL) throws -> FileHandle {
            if let current, current.index == index { return current.handle }
            try? current?.handle.close(); current = nil
            let handle = try FileHandle(forReadingFrom: file)
            current = (index, handle)
            return handle
        }
    }
}
