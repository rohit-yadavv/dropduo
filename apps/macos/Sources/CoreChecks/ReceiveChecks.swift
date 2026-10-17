import Foundation
import DropDuoCore

/// A received file that moved to the chosen folder isn't received twice, and lands under its own name.
func checkReceivedFiles() throws {
    let fm = FileManager.default
    let dir = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? fm.removeItem(at: dir) }
    let inbox = try Inbox(root: dir.appendingPathComponent("inbox"))
    let source = dir.appendingPathComponent("source"); try Data("abc".utf8).write(to: source)
    let offer = Message("offer", id: UUID().uuidString, name: "photo.jpg", size: 3, sha256: try Wire.hash(source))
    _ = try inbox.prepare(offer); _ = try inbox.append(offer, offset: 0, data: Data("abc".utf8))
    let folder = dir.appendingPathComponent("Chosen")
    let placed = try ReceivedFolder.place(try inbox.finish(offer)!, named: offer.name!, in: folder)
    expectEqual(placed.lastPathComponent, "photo.jpg")
    // The phone sending it again after a dropped connection completes without any data.
    expectEqual(try inbox.prepare(offer), 3)
    expectEqual(try inbox.finish(offer), nil)
    var changed = offer; changed.sha256 = String(repeating: "0", count: 64)
    expectThrows(try inbox.prepare(changed))
    for name in ["photo.jpg", "photo.jpg", "README", "README"] {
        let file = dir.appendingPathComponent(UUID().uuidString); try Data().write(to: file)
        _ = try ReceivedFolder.place(file, named: name, in: folder)
    }
    expectEqual(try fm.contentsOfDirectory(atPath: folder.path).sorted(), ["README", "README 2", "photo 2.jpg", "photo 3.jpg", "photo.jpg"])
}

/// Android's OneTimeCodeTest uses the same examples.
func checkOneTimeCodes() {
    let examples: [(String, String?)] = [
        ("482913", "482913"),
        ("G-482913 is your Google verification code.", "482913"),
        ("Your OTP is 123 456. Do not share it.", "123456"),
        ("123456 is your OTP for a payment of Rs.1500 at Amazon", "123456"),
        ("Use code 7741 to sign in", "7741"),
        ("Code:5520", "5520"),
        ("Meeting at 10:30 in room 4021", nil),
        ("Your code expires on 2026-10-17", nil),
        ("Call 555-123-4567 about your code", nil),
        ("Pay $2500 with this code", nil),
        ("Pin version 1.2345", nil),
        ("Order A1234567 code", nil),
        ("12345678901", nil),
    ]
    for (text, code) in examples { precondition(OneTimeCode.find(in: text) == code, "Wrong code for \"\(text)\"") }
}
