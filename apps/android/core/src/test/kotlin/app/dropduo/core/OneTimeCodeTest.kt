package app.dropduo.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Mac's CoreChecks use the same examples. */
class OneTimeCodeTest {
    @Test fun findsCodesAndSkipsOtherNumbers() {
        val examples = listOf(
            "482913" to "482913",
            "G-482913 is your Google verification code." to "482913",
            "Your OTP is 123 456. Do not share it." to "123456",
            "123456 is your OTP for a payment of Rs.1500 at Amazon" to "123456",
            "Use code 7741 to sign in" to "7741",
            "Code:5520" to "5520",
            "Meeting at 10:30 in room 4021" to null,
            "Your code expires on 2026-10-17" to null,
            "Call 555-123-4567 about your code" to null,
            "Pay $2500 with this code" to null,
            "Pin version 1.2345" to null,
            "Order A1234567 code" to null,
            "12345678901" to null,
        )
        for ((text, code) in examples) assertEquals(text, code, OneTimeCode.find(text))
    }
}
