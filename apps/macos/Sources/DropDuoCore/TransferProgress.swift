import Foundation

/// Per-attempt progress. The initial offset is a baseline, so resumed bytes never count as speed.
public struct TransferProgress: Sendable {
    public let totalBytes: Int64
    public private(set) var transferredBytes: Int64
    public private(set) var updatedAt: TimeInterval
    private struct Sample: Sendable { var time: TimeInterval; var bytes: Int64 }
    private var samples: [Sample]

    public init(totalBytes: Int64, transferredBytes: Int64, at time: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        self.totalBytes = totalBytes; self.transferredBytes = transferredBytes; updatedAt = time
        samples = [Sample(time: time, bytes: transferredBytes)]
    }

    public mutating func advance(bytes: Int64, at time: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        if bytes < transferredBytes || time < updatedAt {
            self = TransferProgress(totalBytes: totalBytes, transferredBytes: bytes, at: time); return
        }
        while samples.count > 1 && samples[1].time <= time - 5 { samples.removeFirst() }
        // Bound the history to about 20 samples, even on very fast links.
        if time - samples.last!.time >= 0.25 { samples.append(Sample(time: time, bytes: bytes)) }
        transferredBytes = bytes; updatedAt = time
    }

    public func isStalled(at time: TimeInterval = ProcessInfo.processInfo.systemUptime) -> Bool { time - updatedAt >= 5 }

    public func bytesPerSecond(at time: TimeInterval = ProcessInfo.processInfo.systemUptime) -> Double? {
        let first = samples[0]
        guard !isStalled(at: time), updatedAt - first.time >= 1, transferredBytes > first.bytes else { return nil }
        return Double(transferredBytes - first.bytes) / (max(time, updatedAt) - first.time)
    }

    public func remainingSeconds(at time: TimeInterval = ProcessInfo.processInfo.systemUptime) -> Double? {
        guard totalBytes > transferredBytes, let rate = bytesPerSecond(at: time) else { return nil }
        return Double(totalBytes - transferredBytes) / rate
    }

    public var sizeDescription: String { "\(Self.formatBytes(Double(transferredBytes))) / \(Self.formatBytes(Double(totalBytes)))" }

    public func rateDescription(at time: TimeInterval = ProcessInfo.processInfo.systemUptime) -> String {
        if isStalled(at: time) { return "Waiting for data…" }
        guard let rate = bytesPerSecond(at: time) else { return "Calculating time left…" }
        guard let remaining = remainingSeconds(at: time) else { return "Finishing…" }
        return "\(Self.formatBytes(rate))/s · \(Self.formatRemaining(remaining))"
    }

    private static func formatBytes(_ bytes: Double) -> String {
        let units = ["B", "KB", "MB", "GB", "TB"]
        var value = bytes, unit = 0
        while value >= 1000 && unit < units.count - 1 { value /= 1000; unit += 1 }
        let number = String(format: unit == 0 || value >= 10 ? "%.0f" : "%.1f", locale: Locale.current, value)
        return "\(number) \(units[unit])"
    }

    private static func formatRemaining(_ seconds: Double) -> String {
        if seconds < 10 { return "less than 10 sec left" }
        if seconds < 60 { return "about \(Int(ceil(seconds / 5)) * 5) sec left" }
        if seconds < 3600 { return "about \(Int(ceil(seconds / 60))) min left" }
        return "about \(Int(ceil(seconds / 3600))) hr left"
    }
}
