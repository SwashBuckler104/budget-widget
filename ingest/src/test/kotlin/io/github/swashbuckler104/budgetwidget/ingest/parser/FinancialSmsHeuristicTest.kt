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

    @Test
    fun `reminders, offers, failures and requests are not transactions, a real alert is`() {
        val body = FinancialSmsHeuristic::looksLikeTransactionBody
        assertThat(
            body("EMI of Rs.12,500.00 for your Loan A/c XX4567 is due on 05-Nov-26. Ignore if already paid."),
        ).isFalse()
        assertThat(body("Rs.12,500.00 will be debited from A/c XX8812 on 05-Nov-26 towards BAJAJ FINANCE")).isFalse()
        assertThat(
            body("Your EMI of INR 6,300.00 for Loan A/c no. XX771904 has bounced. Paid charges apply."),
        ).isFalse()
        assertThat(body("Transaction of INR 950 on Card no. XX2207 was declined. Not debited.")).isFalse()
        assertThat(
            body("ravi@okaxis has requested Rs.600.00 from you on UPI, to be debited from your account"),
        ).isFalse()
        assertThat(body("Pre-approved loan of Rs.5,00,000 credited to your account instantly! Apply now")).isFalse()
        // Real alerts, including a reversal that mentions a failed payment.
        assertThat(body("Rs 320.00 reversed and credited to your A/c XX3071 for failed UPI txn Ref No 6301")).isTrue()
        // The card's or lender's side of a payment: the bank debit holds the money (ADR-0042).
        assertThat(
            body("received payment of Rs.5,000.00 via UPI & credited to your SBI Credit Card ending 2290"),
        ).isFalse()
        assertThat(body("we have received your EMI payment of Rs. 12,500 towards your Loan A/c XX4567")).isFalse()
        // SBI writes the movement as a noun.
        assertThat(body("Your A/C XXXXX5503 has a debit by transfer of Rs 8,450.00 on 05/11/26. -SBI")).isTrue()
        assertThat(body("Your A/C XXXXX5503 has a credit by NEFT of Rs 82,000.00 on 01/11/26 by ACME. -SBI")).isTrue()
        assertThat(body("Get Rs 500 off on your credit card account, limited time!")).isFalse()
        // Bank SMS from a sender that fails the header check (spaces) are still judged by the body.
        assertThat(body("Rs.649.00 spent on your SBI Credit Card ending with 2290 on 05Nov26.")).isTrue()
    }

    @Test
    fun `a shop's own credit is not money in an account`() {
        // Template from the owner's parser feedback; made-up amount.
        assertThat(looks("VM-MYCLIQ-S", "CREDITED: Rs. 100 CLiQ Cash has been credited to your wallet.")).isFalse()
        assertThat(looks("AD-SHOPCO-S", "500 reward points worth Rs 125 credited to your card account")).isFalse()
        assertThat(looks("AD-SHOPCO-S", "Rs 125 credited to your wallet for the refund of order 1234")).isTrue()
    }
}
