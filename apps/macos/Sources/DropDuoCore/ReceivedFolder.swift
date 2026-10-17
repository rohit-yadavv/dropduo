import Foundation

/// Where received files end up: the folder chosen in Settings, under their own names.
public enum ReceivedFolder {
    /// Moves `file` into `folder` as `name`, or as "name 2.ext" when that's taken, the way Finder names copies.
    public static func place(_ file: URL, named name: String, in folder: URL) throws -> URL {
        let fm = FileManager.default
        try fm.createDirectory(at: folder, withIntermediateDirectories: true)
        let base = (name as NSString).deletingPathExtension, ext = (name as NSString).pathExtension
        var number = 1
        while true {
            let candidate = number == 1 ? name : ext.isEmpty ? "\(base) \(number)" : "\(base) \(number).\(ext)"
            let target = folder.appendingPathComponent(candidate)
            number += 1
            if fm.fileExists(atPath: target.path) { continue }
            do { try fm.moveItem(at: file, to: target); return target }
            // Something else took the name since the check.
            catch CocoaError.fileWriteFileExists { continue }
        }
    }
}
