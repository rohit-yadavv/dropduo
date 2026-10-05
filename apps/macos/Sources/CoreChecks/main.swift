import Foundation
import DropDuoCore
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
}

func expectEqual<T: Equatable>(_ left: T, _ right: T) { precondition(left == right, "Values differ") }
func expectThrows<T>(_ expression: @autoclosure () throws -> T) { do { _ = try expression(); fatalError("Expected failure") } catch {} }

let checks = CoreTests()
try checks.testAuthenticatedFramesRejectReplayAndTampering()
try checks.testWorstCaseChunkFitsInFrame()
try checks.testTicketRoundTrip()
try checks.testInboxResumeIntegrityAndSafeNames()
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
print("PASS: authenticated frames, worst-case chunk size, replay/tampering, pairing ticket, resume, integrity and safe names")
