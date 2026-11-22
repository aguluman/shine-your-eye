package com.invarware.shineye.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalsTest {

    private fun signals(text: String) = Analysis.of(Raw(text)).signals

    @Test
    fun askingForAnOtpIsCaught() {
        assertTrue(Signal.ASKS_FOR_SECRET in signals("Dear customer, kindly send the OTP sent to your phone to complete the reversal."))
    }

    @Test
    fun aGenuineOtpMessageIsNotAccused() {
        val genuine = "Your OTP is 482913. It expires in 5 minutes. Do not share it with anyone, including bank staff."
        assertFalse(Signal.ASKS_FOR_SECRET in signals(genuine))
        assertEquals(Risk.LOW, floorOf(signals(genuine)))
    }

    @Test
    fun aNegationDoesNotHideARealRequest() {
        assertTrue(Signal.ASKS_FOR_SECRET in signals("Do not share this with anyone else. Reply with your PIN to restore access."))
    }

    @Test
    fun lookalikeDomainsImpersonateBanks() {
        assertTrue(Signal.IMPERSONATES_BANK in signals("Verify now: https://gtbank-secure-update.xyz/login"))
        assertTrue(Signal.IMPERSONATES_BANK in signals("Visit kudabank-support.com to restore your account"))
        assertTrue(Signal.IMPERSONATES_BANK in signals("https://gtbank.com@zenithbank-verify.top/"))
    }

    @Test
    fun officialDomainsAreTrusted() {
        val found = signals("Download our app from https://www.gtbank.com/personal")
        assertFalse(Signal.IMPERSONATES_BANK in found)
        assertFalse(Signal.UNOFFICIAL_LINK in found)
    }

    @Test
    fun ordinaryWordsAreNotBrands() {
        assertFalse(usesBrand("palatefood.com"))
        assertFalse(usesBrand("cuba.travel"))
        assertTrue(usesBrand("uba-alert.com"))
    }

    @Test
    fun theClassicBvnScamIsAScam() {
        val text = "URGENT: Your account has been restricted due to BVN update. Click bit.ly/bvn-upd8 to update within 24 hours."
        val found = signals(text)
        assertTrue(Signal.ACCOUNT_THREAT in found)
        assertTrue(Signal.ID_UPDATE in found)
        assertTrue(Signal.SHORTENED_LINK in found)
        assertEquals(Risk.HIGH, floorOf(found))
    }

    @Test
    fun theFakeCreditAlertRefundIsCaught() {
        val text = "Good evening, I mistakenly sent N25,000 to your account. Please send it back to 0123456789, God bless you."
        assertTrue(Signal.REFUND_REQUEST in signals(text))
    }

    @Test
    fun aPidginThreatIsStillAThreat() {
        assertTrue(Signal.ACCOUNT_THREAT in signals("Your account don block. Abeg call us sharp sharp."))
    }

    @Test
    fun pidginIsRecognisedAsNotEnglish() {
        assertEquals(Language.OTHER, Analysis.of(Raw("Abeg una account don block, wetin I go do?")).language)
        assertEquals(Language.ENGLISH, Analysis.of(Raw("Your account has been credited.")).language)
    }

    @Test
    fun addingASignalNeverLowersTheFloor() {
        val all = Signal.entries
        for (mask in 0 until (1 shl all.size)) {
            val set = all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            for (extra in all) assertTrue(floorOf(set + extra) >= floorOf(set), "floor dropped adding $extra to $set")
        }
    }
}

class FalsePositiveTest {

    private fun signals(text: String) = Analysis.of(Raw(text)).signals

    @Test
    fun anOrdinaryRidePromoIsNotAWarning() {
        val text = """
            Happy Independence Day, Nigeria!
            Heading out today? Let Linc Ride get you there.
            Book on WhatsApp: Send RIDE to 07051797935 or tap
            https://www.tmii.io/iRHanN
            Wherever today takes you, ride with Linc.
        """.trimIndent()
        val found = signals(text)
        assertFalse(Signal.UNOFFICIAL_LINK in found)
        assertFalse(Signal.CALL_THIS_NUMBER in found)
        assertEquals(Risk.LOW, floorOf(found))
    }

    @Test
    fun aBankMessageWithAPhoneAndLinkIsStillFlagged() {
        val found = signals("Dear customer, your account will be restricted. Call 08031234567 or visit https://mybank-verify.xyz/notice")
        assertTrue(Signal.ACCOUNT_THREAT in found)
        assertTrue(Signal.UNOFFICIAL_LINK in found)
        assertTrue(Signal.CALL_THIS_NUMBER in found)
        assertEquals(Risk.ELEVATED, floorOf(found))
    }

    @Test
    fun aParcelPhishingMessageStillReachesTheModel() {
        val found = signals("Your parcel is waiting. Visit parcel-ng.xyz/track")
        assertFalse(Signal.UNOFFICIAL_LINK in found)
        assertEquals(Risk.LOW, floorOf(found))
    }
}
