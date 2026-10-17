import Foundation

/// One idle-sleep assertion per active transfer; display sleep remains allowed.
package final class TransferActivity {
    private var token: NSObjectProtocol?
    private let endActivity: (NSObjectProtocol) -> Void

    package init(begin: () -> NSObjectProtocol = {
        ProcessInfo.processInfo.beginActivity(options: .idleSystemSleepDisabled, reason: "Transferring files with DropDuo")
    }, end: @escaping (NSObjectProtocol) -> Void = { ProcessInfo.processInfo.endActivity($0) }) {
        endActivity = end; token = begin()
    }

    package func end() {
        guard let token else { return }
        self.token = nil; endActivity(token)
    }

    deinit { end() }
}
