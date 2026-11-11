package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import org.junit.Test

class FinancialSmsHeuristicTest {
    private fun looks(sender: String, body: String) =
        FinancialSmsHeuristic.looksLikeTransaction(RawSms(sender, body, 0))

    @Test
    fun `an alert from an unknown bank header is recognised`() {
        // Made-up wording in the style of a neobank alert; no real message.
        assertThat(looks("VM-JUPITR-S", "Rs.52,000.00 credited to your A/c XX9876 on 01-11-26. Ref 123456. -Jupiter"))
            .isTrue()
        assertThat(looks("AD-NEWBNK", "INR 250 debited from account ending 4321 via UPI to shop@okaxis")).isTrue()
        assertThat(looks("NEWBNK", "₹1,200.50 spent on your card XX11 at STORE")).isTrue()
    }

    @Test
    fun `personal, promotional, OTP and vague messages are not`() {
        assertThat(looks("+919812345678", "Rs 500 credited to your account, check")).isFalse()
        assertThat(looks("98123 45678", "Rs 500 credited to your account")).isFalse()
        assertThat(looks("JD-SHOPPR-P", "Rs 500 cashback credited to your wallet!")).isFalse()
        assertThat(looks("AD-NEWBNK-S", "123456 is your OTP for a payment of Rs 500 from your card")).isFalse()
        assertThat(
            looks("AD-NEWBNK-S", "Use verification code 1234 to add Rs 10 to your wallet, credited instantly"),
        ).isFalse()
        assertThat(looks("AD-NEWBNK-S", "Your account statement is ready")).isFalse()
        assertThat(looks("AD-NEWBNK-S", "Rs 500 is due on your card")).isFalse()
        // "hours 5" and "years 2" are not rupee amounts.
        assertThat(looks("AD-NEWBNK-S", "Your account was credited after 48 hours 5 days ago via UPI")).isFalse()
        assertThat(looks("AD-NEWBNK-S", "OTP482913 for Rs 500 debited from your card")).isFalse()
        assertThat(looks("AD-NEWBNK-S", "Do not share your MPIN. Rs 500 debited from A/c XX1")).isFalse()
    }
}
