package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.google.common.truth.Truth.assertThat
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import java.math.BigDecimal
import org.junit.Test

class ParsedTransactionMapperTest {
    private val sms = RawSms("AD-HDFCBK", "body", 1_000)

    @Test
    fun `rupees become paise with HALF_UP rounding`() {
        assertThat(ParsedTransactionMapper.toPaiseOrNull(BigDecimal("150"))).isEqualTo(15_000)
        assertThat(ParsedTransactionMapper.toPaiseOrNull(BigDecimal("0.005"))).isEqualTo(1)
        assertThat(ParsedTransactionMapper.toPaiseOrNull(BigDecimal("0.004"))).isEqualTo(0)
        assertThat(ParsedTransactionMapper.toPaiseOrNull(BigDecimal("1e30"))).isNull()
    }

    @Test
    fun `type mapping follows the scope table`() {
        fun map(type: TransactionType, card: Boolean = false) =
            (ParsedTransactionMapper.map(pt(type, isFromCard = card), sms) as SmsParseResult.Transaction).parsed

        with(map(TransactionType.EXPENSE)) { assertThat(direction to isCard).isEqualTo(Direction.OUT to false) }
        with(map(TransactionType.CREDIT)) { assertThat(direction to isCard).isEqualTo(Direction.OUT to true) }
        with(map(TransactionType.INCOME)) { assertThat(direction).isEqualTo(Direction.IN) }
        with(map(TransactionType.INVESTMENT)) {
            assertThat(direction to parserType).isEqualTo(Direction.OUT to ParserType.INVESTMENT)
        }
        with(map(TransactionType.EXPENSE, card = true)) { assertThat(isCard).isTrue() }
    }

    @Test
    fun `transfers are incoming only when our account is the destination`() {
        fun dir(from: String?, to: String?) = (
            ParsedTransactionMapper.map(
                pt(TransactionType.TRANSFER, fromAccount = from, toAccount = to),
                sms,
            ) as SmsParseResult.Transaction
            ).parsed.direction

        assertThat(dir(from = "XX9999", to = "XX1234")).isEqualTo(Direction.IN)
        assertThat(dir(from = "XX1234", to = "XX9999")).isEqualTo(Direction.OUT)
        assertThat(dir(from = null, to = null)).isEqualTo(Direction.OUT)
    }

    @Test
    fun `non-INR and invalid amounts are not transactions`() {
        assertThat(
            ParsedTransactionMapper.map(pt(TransactionType.EXPENSE, currency = "USD"), sms),
        ).isEqualTo(SmsParseResult.Unparsed(UnparsedReason.UNSUPPORTED_CURRENCY))
        assertThat(
            ParsedTransactionMapper.map(pt(TransactionType.EXPENSE, amount = BigDecimal.ZERO), sms),
        ).isEqualTo(SmsParseResult.Unparsed(UnparsedReason.INVALID_AMOUNT))
        assertThat(
            ParsedTransactionMapper.map(pt(TransactionType.EXPENSE, amount = BigDecimal("-5")), sms),
        ).isEqualTo(SmsParseResult.Unparsed(UnparsedReason.INVALID_AMOUNT))
    }

    @Test
    fun `balance alerts carry the balance, not the zero amount`() {
        val result = ParsedTransactionMapper.map(
            pt(TransactionType.BALANCE_UPDATE, amount = BigDecimal.ZERO, balance = BigDecimal("2500.75")),
            sms,
        )
        assertThat(result).isEqualTo(SmsParseResult.BalanceUpdate("HDFC Bank", "1234", 250_075))
        assertThat(
            ParsedTransactionMapper.map(pt(TransactionType.BALANCE_UPDATE, amount = BigDecimal.ZERO), sms),
        ).isEqualTo(SmsParseResult.Unparsed(UnparsedReason.INVALID_AMOUNT))
    }

    @Test
    fun `a balance kept in a foreign account currency is dropped`() {
        val parsed = (
            ParsedTransactionMapper.map(
                pt(TransactionType.CREDIT, balance = BigDecimal("100"), accountCurrency = "USD"),
                sms,
            ) as SmsParseResult.Transaction
            ).parsed
        assertThat(parsed.balanceAfterPaise).isNull()
    }

    @Test
    fun `the user's own account or card is never the merchant`() {
        val own = ParsedTransactionMapper::isOwnAccountReference
        assertThat(own("A/c XX8812", "8812")).isTrue()
        assertThat(own("A/c X5503", null)).isTrue()
        assertThat(own("your Edge CSB Bank RuPay Credit Card ending 6788", "6788")).isTrue()
        assertThat(own("Card ending 1234", "1234")).isTrue()
        assertThat(own("BLINKIT", "2207")).isFalse()
        assertThat(own("Card Factory", "2207")).isFalse()
        assertThat(own("ACME Account Services", null)).isFalse()
    }

    @Test
    fun `hash is upstream's id and our fields come from the raw SMS`() {
        val upstream = pt(TransactionType.EXPENSE)
        val parsed = (ParsedTransactionMapper.map(upstream, sms) as SmsParseResult.Transaction).parsed
        assertThat(parsed.txnHash).isEqualTo(upstream.generateTransactionId())
        assertThat(parsed.occurredAt).isEqualTo(1_000)
        assertThat(parsed.sender).isEqualTo("AD-HDFCBK")
    }

    private fun pt(
        type: TransactionType,
        amount: BigDecimal = BigDecimal("150.00"),
        balance: BigDecimal? = null,
        currency: String = "INR",
        accountCurrency: String? = null,
        isFromCard: Boolean = false,
        fromAccount: String? = null,
        toAccount: String? = null,
    ) = ParsedTransaction(
        amount = amount,
        type = type,
        merchant = "Shop",
        reference = null,
        accountLast4 = "1234",
        balance = balance,
        smsBody = "body",
        sender = "AD-HDFCBK",
        timestamp = 1_000,
        bankName = "HDFC Bank",
        isFromCard = isFromCard,
        currency = currency,
        fromAccount = fromAccount,
        toAccount = toAccount,
        accountCurrency = accountCurrency,
    )
}
