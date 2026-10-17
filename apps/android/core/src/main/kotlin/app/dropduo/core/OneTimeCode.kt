package app.dropduo.core

/**
 * Finds a one-time code, like a sign-in code from a text message, so it can be copied by itself.
 * The Mac has the same rules in `OneTimeCode.swift`; keep the two in step.
 */
object OneTimeCode {
    private val hints = listOf("code", "otp", "passcode", "pin", "verif", "one-time", "one time", "2fa", "login", "log in", "sign in", "sign-in")
    private val currencies = listOf("$", "₹", "€", "£", "rs", "inr", "usd", "eur")
    private val candidates = Regex("""\d{3}[ -]\d{3}|\d{4,8}""")

    /** A 4–8 digit number, or two groups of three like "123 456", in a text that mentions a code; or a text that is only such a number. */
    fun find(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.length > 500) return null
        if (Regex("""\d{4,8}""").matches(trimmed)) return trimmed
        if (hints.none { trimmed.lowercase().contains(it) }) return null
        return candidates.findAll(trimmed).firstOrNull { standsAlone(trimmed, it.range.first, it.range.last + 1) }?.value?.filter { it.isAsciiDigit() }
    }

    /** Rejects numbers that are part of a longer number, a word, an amount, a date or a decimal. */
    private fun standsAlone(text: String, start: Int, end: Int): Boolean {
        for ((side, beyond) in listOf(text.getOrNull(start - 1) to text.getOrNull(start - 2), text.getOrNull(end) to text.getOrNull(end + 1))) {
            if (side == null) continue
            if (side.isAsciiDigit() || side.isLetter()) return false
            // "1.2345", "2026-10-17" and "555-123-4567" carry on past the separator.
            if (side in ".,:/-" && beyond?.isAsciiDigit() == true) return false
        }
        val prefix = text.substring(maxOf(0, start - 4), start).lowercase().filter { it != ' ' && it != '.' }
        return currencies.none { prefix.endsWith(it) }
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
