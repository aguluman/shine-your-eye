package com.invarware.shineye.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedactionTest {

    private fun redact(text: String) = Redacted.of(Raw(text)).text

    @Test
    fun phoneNumbersInEveryCommonShape() {
        assertEquals("Call [PHONE] now", redact("Call 08031234567 now"))
        assertEquals("Call [PHONE] now", redact("Call 0803 123 4567 now"))
        assertEquals("Call [PHONE] now", redact("Call 0803-123-4567 now"))
        assertEquals("Call +[PHONE] now", redact("Call +234 803 123 4567 now"))
    }

    @Test
    fun bankingIdentifiers() {
        assertEquals("BVN: [ID]", redact("BVN: 22212345678"))
        assertEquals("Acct [ACCOUNT]", redact("Acct 0123456789"))
        assertEquals("Your OTP is [CODE]", redact("Your OTP is 482913"))
        assertEquals("Card [CARD]", redact("Card 5399 8312 3456 7890"))
        assertEquals("PIN [CODE]", redact("PIN 1234"))
        assertEquals("PIN[CODE]", redact("PIN1234"))
    }

    @Test
    fun amountsStayBecauseTheyCarryMeaning() {
        assertEquals("You received ₦50,000.00", redact("You received ₦50,000.00"))
        assertEquals("Pay N5,000 fee", redact("Pay N5,000 fee"))
        assertEquals("Pay #2500 now", redact("Pay #2500 now"))
        assertEquals("NGN 10,000 credited", redact("NGN 10,000 credited"))
    }

    @Test
    fun anAmountCannotSmuggleAnAccountNumber() {
        assertEquals("Send #[ACCOUNT]", redact("Send #0123456789"))
        assertEquals("₦50,000 to [ACCOUNT]", redact("₦50,000 to 0123456789"))
    }

    @Test
    fun linksKeepTheirHostAndLoseTheirPath() {
        assertEquals("Visit gtbank-verify.xyz/… today", redact("Visit https://gtbank-verify.xyz/login?acct=0123456789 today"))
        assertEquals("See bit.ly/…", redact("See bit.ly/3xYz9"))
        assertEquals("Open gtbank.com.", redact("Open https://www.gtbank.com/."))
    }

    @Test
    fun theUserinfoTrickShowsTheRealHost() {
        assertEquals("Login at evil.xyz/…", redact("Login at https://gtbank.com@evil.xyz/login"))
    }

    @Test
    fun emailAddressesGo() {
        assertEquals("Write to [EMAIL] please", redact("Write to ada.obi@gmail.com please"))
    }

    @Test
    fun theSummaryCountsWhatLeft() {
        val redacted = Redacted.of(Raw("Call 08031234567 or 08098765432, OTP 1234, https://x.xyz/a"))
        assertEquals(listOf("2 phone numbers", "1 code", "1 link path"), redacted.describe())
    }

    @Test
    fun noRunOfFourDigitsSurvivesOutsideAnAmount() {
        val random = Random(20261130)
        repeat(20_000) {
            val text = randomMessage(random)
            val redacted = redact(text)
            assertTrue(exposedDigits(redacted).isEmpty(), "leaked digits in <$redacted> from <$text>")
        }
    }

    @Test
    fun noEmailAddressSurvives() {
        val random = Random(7)
        repeat(5_000) {
            val text = randomMessage(random) + " " + "abc${random.nextInt(1000)}@mail.com"
            assertTrue('@' !in redact(text).substringAfterLast(' '), "email survived in ${redact(text)}")
        }
    }

    @Test
    fun redactingTwiceChangesNothing() {
        val random = Random(42)
        repeat(20_000) {
            val once = redact(randomMessage(random))
            assertEquals(once, redact(once))
        }
    }

    private val alphabet = "0123456789 -./,#NnGg₦٣٤٥٦abcxyz:@[]()\n\u00A0"
    private val fragments = listOf(
        "https://", "www.", ".com", ".ng", "/login?id=", "@", "PIN", "OTP", "NGN", "₦", "#", "N", "+234", "0803",
        "bit.ly/", "user@", "gtbank-", ".xyz",
    )

    private fun randomMessage(random: Random): String = buildString {
        repeat(random.nextInt(1, 40)) {
            if (random.nextInt(4) == 0) append(fragments[random.nextInt(fragments.size)])
            else append(alphabet[random.nextInt(alphabet.length)])
        }
    }
}
