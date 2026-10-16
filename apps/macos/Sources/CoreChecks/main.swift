import Foundation
import DropDuoCore
import CryptoKit
struct CoreTests {
    func testAuthenticatedFramesRejectReplayAndTampering() throws {
        let key = Data(repeating: 7, count: 32)
        var sender = FrameCipher(key: key), receiver = FrameCipher(key: key)
        let frame = try sender.seal(Data("hello".utf8))
        expectEqual(try receiver.open(frame), Data("hello".utf8))
        expectThrows(try receiver.open(frame))
        var bad = frame; bad[bad.count - 1] ^= 1
        var fresh = FrameCipher(key: key)
        expectThrows(try fresh.open(bad))
    }
    func testWorstCaseChunkFitsInFrame() throws {
        // 0xFF bytes encode to base64 "/" only, the character JSON encoders may escape.
        let chunk = Message("chunk", id: UUID().uuidString, offset: Int64.max, data: Data(repeating: 0xFF, count: Wire.chunkSize).base64EncodedString())
        var sender = FrameCipher(key: Data(repeating: 1, count: 32))
        expectEqual(try sender.seal(Wire.encode(chunk)).count <= Wire.maxFrame, true)
    }
    func testTicketRoundTrip() throws {
        let ticket = Ticket(pairID: UUID().uuidString, host: "127.0.0.1", port: 53318,
            secret: Wire.random(32).base64EncodedString(), name: "Mac", expires: 100)
        expectEqual(try Ticket.parse(ticket.code()), ticket)
    }
    func testInboxResumeIntegrityAndSafeNames() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let inbox = try Inbox(root: dir)
        let source = dir.appendingPathComponent("source"); try Data("abcdef".utf8).write(to: source)
        let offer = Message("offer", id: UUID().uuidString, name: "file.txt", size: 6, sha256: try Wire.hash(source))
        expectEqual(try inbox.prepare(offer), 0)
        expectEqual(try inbox.append(offer, offset: 0, data: Data("abc".utf8)), 3)
        expectEqual(try Inbox(root: dir).prepare(offer), 3)
        expectThrows(try inbox.append(offer, offset: 0, data: Data("x".utf8)))
        _ = try inbox.append(offer, offset: 3, data: Data("def".utf8))
        expectEqual(try Data(contentsOf: inbox.finish(offer)), Data("abcdef".utf8))
        var bad = offer; bad.name = "../escape"
        expectThrows(try inbox.prepare(bad))
        var corrupt = offer; corrupt.id = UUID().uuidString; corrupt.sha256 = String(repeating: "0", count: 64)
        _ = try inbox.prepare(corrupt); _ = try inbox.append(corrupt, offset: 0, data: Data("abcdef".utf8))
        expectThrows(try inbox.finish(corrupt))
    }
    func testFolderPayload() throws {
        let fm = FileManager.default
        let dir = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? fm.removeItem(at: dir) }
        let album = dir.appendingPathComponent("Album")
        try fm.createDirectory(at: album.appendingPathComponent("sub"), withIntermediateDirectories: true)
        try fm.createDirectory(at: album.appendingPathComponent("empty"), withIntermediateDirectories: true)
        let big = Data((0..<300_000).map { UInt8($0 % 253) })
        try Data("abc".utf8).write(to: album.appendingPathComponent("a.txt"))
        try Data().write(to: album.appendingPathComponent("empty.txt"))
        try Data("ü".utf8).write(to: album.appendingPathComponent("naïve ü.txt"))
        try big.write(to: album.appendingPathComponent("sub/b.bin"))
        try Data("skip".utf8).write(to: album.appendingPathComponent(".DS_Store"))
        try fm.createSymbolicLink(at: album.appendingPathComponent("link"), withDestinationURL: album.appendingPathComponent("a.txt"))
        func entry(_ kind: UInt8, _ path: String, _ contents: Data? = nil) -> Data {
            let bytes = Data(path.utf8)
            return Data([kind, UInt8(bytes.count >> 8), UInt8(bytes.count & 0xFF)]) + bytes + (contents.map { Wire.integer(UInt64($0.count)) + $0 } ?? Data())
        }
        let expected = Data("DDF1".utf8) + entry(2, "a.txt", Data("abc".utf8)) + entry(1, "empty") + entry(2, "empty.txt", Data())
            + entry(2, "naïve ü.txt", Data("ü".utf8)) + entry(1, "sub") + entry(2, "sub/b.bin", big) + Data([0])
        let payload = try Payload(folder: album)
        expectEqual(payload.isFolder, true)
        expectEqual(payload.size, Int64(expected.count))
        let reader = Payload.Reader(payload)
        var whole = Data()
        while whole.count < expected.count { whole += try reader.read(at: Int64(whole.count), count: Wire.chunkSize) }
        expectEqual(whole, expected)
        // Resuming reads from any offset, including inside headers and across items.
        for offset in [0, 1, 4, 7, 12, 40, 100_000, expected.count - 300, expected.count - 1] {
            let count = min(1_000, expected.count - offset)
            expectEqual(try Payload.Reader(payload).read(at: Int64(offset), count: 1_000), expected.subdata(in: offset..<offset + count))
        }
        expectEqual(try reader.read(at: payload.size, count: 10), Data())
        expectEqual(try payload.hash(), SHA256.hash(data: expected).map { String(format: "%02x", $0) }.joined())
        let file = album.appendingPathComponent("sub/b.bin")
        expectEqual(try Payload(file: file, size: Int64(big.count)).hash(), try Wire.hash(file))
        try Data().write(to: album.appendingPathComponent("bad\nname"))
        expectThrows(try Payload(folder: album))
        // This Mac sends folders but doesn't receive them.
        let offer = Message("offer", id: UUID().uuidString, name: "Album", size: 5, sha256: String(repeating: "0", count: 64), kind: "folder")
        try Inbox.validate(offer)
        expectThrows(try Inbox(root: dir.appendingPathComponent("inbox")).prepare(offer))
        var unknown = offer; unknown.kind = "other"
        expectThrows(try Inbox.validate(unknown))
    }
}

func expectEqual<T: Equatable>(_ left: T, _ right: T, line: Int = #line) { precondition(left == right, "Values differ on line \(line)") }
func expectThrows<T>(_ expression: @autoclosure () throws -> T) { do { _ = try expression(); fatalError("Expected failure") } catch {} }

let checks = CoreTests()
try checks.testAuthenticatedFramesRejectReplayAndTampering()
try checks.testWorstCaseChunkFitsInFrame()
try checks.testTicketRoundTrip()
try checks.testInboxResumeIntegrityAndSafeNames()
try checks.testFolderPayload()
let temp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
try FileManager.default.createDirectory(at: temp, withIntermediateDirectories: true)
defer { try? FileManager.default.removeItem(at: temp) }
let inbox = try Inbox(root: temp)
let empty = temp.appendingPathComponent("empty"); try Data().write(to: empty)
let offer = Message("offer", id: UUID().uuidString, name: "empty.txt", size: 0, sha256: try Wire.hash(empty))
expectEqual(try inbox.prepare(offer), 0)
let finished = try inbox.finish(offer)
expectEqual(try inbox.prepare(offer), 0)
expectEqual(try inbox.finish(offer), finished)
expectThrows(try inbox.append(offer, offset: Int64.max, data: Data([1])))
var longName = offer; longName.name = String(repeating: "a", count: 219)
expectThrows(try inbox.prepare(longName))
var wrong = offer; wrong.size = 1
expectThrows(try inbox.prepare(wrong))
var paused = offer; paused.id = UUID().uuidString
_ = try inbox.prepare(paused); try inbox.cancel(paused.id!)
expectEqual(try inbox.prepare(paused), 0)
var malformed = FrameCipher(key: Data(repeating: 0, count: 32))
expectThrows(try malformed.open(Data(repeating: 0, count: 35)))
print("PASS: authenticated frames, worst-case chunk size, replay/tampering, pairing ticket, resume, integrity, safe names and folder payloads")
