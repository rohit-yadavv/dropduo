import Foundation

/// Finds a one-time code, like a sign-in code from a text message, so it can be copied by itself.
/// Android has the same rules in `OneTimeCode.kt`; keep the two in step.
public enum OneTimeCode {
    private static let hints = ["code", "otp", "passcode", "pin", "verif", "one-time", "one time", "2fa", "login", "log in", "sign in", "sign-in"]
    private static let currencies = ["$", "₹", "€", "£", "rs", "inr", "usd", "eur"]
    private static let candidates = try! NSRegularExpression(pattern: #"\d{3}[ -]\d{3}|\d{4,8}"#)

    /// A 4–8 digit number, or two groups of three like "123 456", in a text that mentions a code; or a text that is only such a number.
    public static func find(in text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count <= 500 else { return nil }
        if trimmed.range(of: #"^\d{4,8}$"#, options: .regularExpression) != nil { return trimmed }
        guard hints.contains(where: trimmed.lowercased().contains) else { return nil }
        let characters = Array(trimmed)
        for match in candidates.matches(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) {
            guard let range = Range(match.range, in: trimmed) else { continue }
            let start = trimmed.distance(from: trimmed.startIndex, to: range.lowerBound), end = trimmed.distance(from: trimmed.startIndex, to: range.upperBound)
            if standsAlone(characters, start, end) { return String(trimmed[range].filter(\.isASCIIDigit)) }
        }
        return nil
    }

    /// Rejects numbers that are part of a longer number, a word, an amount, a date or a decimal.
    private static func standsAlone(_ text: [Character], _ start: Int, _ end: Int) -> Bool {
        func at(_ index: Int) -> Character? { text.indices.contains(index) ? text[index] : nil }
        for (side, beyond) in [(at(start - 1), at(start - 2)), (at(end), at(end + 1))] {
            guard let side else { continue }
            if side.isASCIIDigit || side.isLetter { return false }
            // "1.2345", "2026-10-17" and "555-123-4567" carry on past the separator.
            if ".,:/-".contains(side), beyond?.isASCIIDigit == true { return false }
        }
        let prefix = String(text[max(0, start - 4)..<start]).lowercased().filter { $0 != " " && $0 != "." }
        return !currencies.contains(where: prefix.hasSuffix)
    }
}

private extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}
