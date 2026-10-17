import Foundation
import DropDuoCore

func checkTransferProgress() {
    // Warm up on the current attempt, excluding both preparation and the resume offset.
    var resumed = TransferProgress(totalBytes: 1000, transferredBytes: 800, at: 100)
    expectEqual(resumed.bytesPerSecond(at: 100), nil)
    resumed.advance(bytes: 825, at: 100.5)
    expectEqual(resumed.remainingSeconds(at: 100.5), nil)
    resumed.advance(bytes: 900, at: 102)
    expectEqual(resumed.bytesPerSecond(at: 102), 50)
    expectEqual(resumed.remainingSeconds(at: 102), 2)

    // Missing progress slows the estimate, then hides it instead of counting down to zero.
    var stalled = TransferProgress(totalBytes: 1000, transferredBytes: 0, at: 0)
    stalled.advance(bytes: 200, at: 2)
    expectEqual(stalled.bytesPerSecond(at: 2), 100)
    expectEqual(stalled.bytesPerSecond(at: 4), 50)
    expectEqual(stalled.remainingSeconds(at: 4), 16)
    expectEqual(stalled.bytesPerSecond(at: 7), nil)
    expectEqual(stalled.remainingSeconds(at: 7), nil)
    expectEqual(stalled.rateDescription(at: 7), "Waiting for data…")

    // The recent window adapts to changed link speed; a restarted attempt warms up again.
    var recent = TransferProgress(totalBytes: 100_000, transferredBytes: 0, at: 0)
    for second in 1...5 { recent.advance(bytes: Int64(second * 100), at: Double(second)) }
    for second in 6...11 { recent.advance(bytes: Int64(500 + (second - 5) * 1000), at: Double(second)) }
    expectEqual(recent.bytesPerSecond(at: 11), 1000)
    recent.advance(bytes: 10, at: 12)
    expectEqual(recent.bytesPerSecond(at: 12), nil)
    recent.advance(bytes: 20, at: 13)
    expectEqual(recent.bytesPerSecond(at: 13), 10)

    // Empty/completed files have no countdown, and multi-GB byte counts don't overflow.
    expectEqual(TransferProgress(totalBytes: 0, transferredBytes: 0, at: 0).remainingSeconds(at: 1), nil)
    var finished = TransferProgress(totalBytes: 1000, transferredBytes: 0, at: 0)
    finished.advance(bytes: 1000, at: 2)
    expectEqual(finished.remainingSeconds(at: 2), nil)
    let gib: Int64 = 1024 * 1024 * 1024
    var large = TransferProgress(totalBytes: 4 * gib, transferredBytes: gib, at: 0)
    large.advance(bytes: 2 * gib, at: 2)
    expectEqual(large.bytesPerSecond(at: 2), Double(gib) / 2)
    expectEqual(large.remainingSeconds(at: 2), 4)
    expectEqual(large.rateDescription(at: 2).hasSuffix("less than 10 sec left"), true)
    expectEqual(TransferProgress(totalBytes: 1000, transferredBytes: 0, at: 0).rateDescription(at: 0), "Calculating time left…")
}

func checkTransferActivities() {
    // Each assertion ends exactly once, including when the owner is later deallocated.
    let token = NSObject()
    var ended = 0
    do {
        let activity = TransferActivity(begin: { token }, end: { received in
            expectEqual(received === token, true); ended += 1
        })
        activity.end(); activity.end()
        expectEqual(ended, 1)
    }
    expectEqual(ended, 1)

    // Ending one of several transfers must leave the others protected.
    var active = 0
    func start() -> TransferActivity {
        TransferActivity(begin: { active += 1; return NSObject() }, end: { _ in active -= 1 })
    }
    var first: TransferActivity? = start()
    let second = start()
    expectEqual(active, 2)
    first?.end(); first = nil
    expectEqual(active, 1)
    second.end()
    expectEqual(active, 0)

    // Failed receives release their assertion even before ownership reaches the incoming map.
    enum Failure: Error { case interrupted }
    func prepare() throws {
        let activity = start()
        defer { withExtendedLifetime(activity) {} }
        throw Failure.interrupted
    }
    expectThrows(try prepare())
    expectEqual(active, 0)
}
