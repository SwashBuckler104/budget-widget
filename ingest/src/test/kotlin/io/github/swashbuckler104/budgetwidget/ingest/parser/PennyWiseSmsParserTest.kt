package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.google.common.truth.Truth.assertThat
import com.pennywiseai.parser.core.bank.BankParserFactory
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import org.junit.Test

class PennyWiseSmsParserTest {
    private val parser = PennyWiseSmsParser()

    @Test
    fun `phone numbers are never bank senders, even when an upstream parser claims them`() {
        // Upstream's Everest Bank (Nepal) parser matches any 7-10 digit sender.
        assertThat(BankParserFactory.isKnownBankSender("9812345678")).isTrue()

        listOf("9812345678", "+919812345678", "98123 45678", "+91-98123-45678", "5551234").forEach {
            assertThat(parser.isKnownBankSender(it)).isFalse()
        }
    }

    @Test
    fun `a personal SMS that looks like a debit is still not parsed`() {
        val sms = RawSms("9812345678", "Rs.500 debited from A/c X1234 on 13Sep25. Avl Bal Rs.999", 1)
        assertThat(parser.isKnownBankSender(sms.sender)).isFalse()
        assertThat(parser.parse(sms)).isEqualTo(SmsParseResult.Unparsed(UnparsedReason.NO_MATCH))
    }

    @Test
    fun `promotional headers are never transaction alerts`() {
        val promo =
            RawSms(
                "JD-KOTAKB-P",
                "Apply & link your Kotak Credit Card to UPI & get 5 Reward Points per Rs.100 spent. T&C: https://kotak.example/x",
                1,
            )
        assertThat(BankParserFactory.parse(promo.body, promo.sender, 1)).isNotNull() // upstream false positive
        assertThat(parser.isKnownBankSender(promo.sender)).isFalse()
        assertThat(parser.isKnownBankSender("JD-KOTAKB-S")).isTrue()
        assertThat(parser.isKnownBankSender("CP-HDFCBK-T")).isTrue()
        assertThat(parser.isKnownBankSender("AD-HDFCBK")).isTrue()
    }

    @Test
    fun `foreign-currency banks are not v1 bank senders`() {
        assertThat(BankParserFactory.isKnownBankSender("EVEREST")).isTrue()
        assertThat(parser.isKnownBankSender("EVEREST")).isFalse()
    }

    @Test
    fun `our banks are recognised`() {
        listOf("AD-HDFCBK", "JD-KOTAKB-S", "AD-FEDBNK", "VK-JTEDGE-S", "AD-SLCEIT-S", "AX-AXISBK-S", "ATMSBI").forEach {
            assertThat(parser.isKnownBankSender(it)).isTrue()
        }
    }
}
