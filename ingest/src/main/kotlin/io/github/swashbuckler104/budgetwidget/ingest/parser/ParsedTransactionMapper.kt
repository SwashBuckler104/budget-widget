package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.sms.ParsedSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Maps the upstream [ParsedTransaction] to our types (SCOPE.md F2 type mapping). This is the
 * only place `BigDecimal` rupees become `Long` paise: scaled by 100 and rounded HALF_UP.
 */
internal object ParsedTransactionMapper {
    private const val INR = "INR"

    fun map(parsed: ParsedTransaction, sms: RawSms): SmsParseResult {
        if (!parsed.currency.equals(
                INR,
                ignoreCase = true,
            )
        ) {
            return SmsParseResult.Unparsed(UnparsedReason.UNSUPPORTED_CURRENCY)
        }
        val balancePaise = parsed.balance
            ?.takeIf { parsed.accountCurrency == null || parsed.accountCurrency.equals(INR, ignoreCase = true) }
            ?.let(::toPaiseOrNull)

        val type = when (parsed.type) {
            TransactionType.BALANCE_UPDATE -> {
                // Upstream reports amount 0 for balance alerts; the balance is the payload.
                return if (balancePaise == null) {
                    SmsParseResult.Unparsed(UnparsedReason.INVALID_AMOUNT)
                } else {
                    SmsParseResult.BalanceUpdate(parsed.bankName, parsed.accountLast4, balancePaise)
                }
            }

            TransactionType.INCOME -> ParserType.INCOME

            TransactionType.EXPENSE -> ParserType.EXPENSE

            TransactionType.CREDIT -> ParserType.CREDIT

            TransactionType.TRANSFER -> ParserType.TRANSFER

            TransactionType.INVESTMENT -> ParserType.INVESTMENT
        }
        val amountPaise = toPaiseOrNull(parsed.amount)?.takeIf { it > 0 }
            ?: return SmsParseResult.Unparsed(UnparsedReason.INVALID_AMOUNT)

        return SmsParseResult.Transaction(
            ParsedSms(
                txnHash = parsed.generateTransactionId(),
                amountPaise = amountPaise,
                direction = direction(type, parsed),
                parserType = type,
                isCard = parsed.isFromCard || type == ParserType.CREDIT,
                merchant = parsed.merchant?.takeUnless { isOwnAccountReference(it, parsed.accountLast4) },
                reference = parsed.reference,
                bankName = parsed.bankName,
                accountLast4 = parsed.accountLast4,
                balanceAfterPaise = balancePaise,
                sender = sms.sender,
                body = sms.body,
                occurredAt = sms.receivedAt,
            ),
        )
    }

    private fun direction(type: ParserType, parsed: ParsedTransaction): Direction = when (type) {
        ParserType.INCOME -> Direction.IN

        ParserType.EXPENSE, ParserType.CREDIT, ParserType.INVESTMENT -> Direction.OUT

        // A transfer is incoming only when our account is the destination and not the source.
        ParserType.TRANSFER -> {
            val last4 = parsed.accountLast4
            val isTo = last4 != null && parsed.toAccount?.endsWith(last4) == true
            val isFrom = last4 != null && parsed.fromAccount?.endsWith(last4) == true
            if (isTo && !isFrom) Direction.IN else Direction.OUT
        }
    }

    private val accountWord = Regex("""\b(?:a/?c|acct|account|card)\b""", RegexOption.IGNORE_CASE)
    private val maskedAccount = Regex("""^(?:a/?c|acct|account)\s*(?:no\.?\s*)?[x*]*\d+$""", RegexOption.IGNORE_CASE)

    /**
     * Some upstream formats return the user's own account or card as the merchant ("A/c XX8812",
     * "your Edge CSB Bank RuPay Credit Card ending 6788"). That is never the payee, and it would
     * train merchant rules on the account, so it becomes "no merchant" (docs/upstream/0003).
     */
    fun isOwnAccountReference(merchant: String, accountLast4: String?): Boolean {
        val text = merchant.trim()
        if (maskedAccount.matches(text)) return true
        if (!accountWord.containsMatchIn(text)) return false
        return text.startsWith("your ", ignoreCase = true) || (accountLast4 != null && text.contains(accountLast4))
    }

    /** Rupees to paise, HALF_UP; null if it doesn't fit in a Long. */
    fun toPaiseOrNull(rupees: BigDecimal): Long? = try {
        rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}
