package io.github.swashbuckler104.budgetwidget.ingest.parser

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.sms.ParsedSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Bank and wallet SMS formats parser-core doesn't have, written from the owner's parser feedback
 * (ADR-0061). Upstream stays the parser for every sender it knows: these only claim senders no
 * upstream parser matches, each by its TRAI header and exact wording, and every format has golden
 * cases (`ingest/src/test/resources/golden/`) and an upstream draft (`docs/upstream/0006`).
 *
 * Wallet credits are transfers in and stay in the inbox: a top-up from the user's bank and a
 * refund read the same, so the user decides. Rupees become paise only through
 * [ParsedTransactionMapper.toPaiseOrNull] (hard constraint 5).
 */
internal object LocalFormats {
    /** What one message says, in rupees; null fields are absent from it. */
    private data class Reading(
        val amount: BigDecimal,
        val type: ParserType,
        val direction: Direction,
        val merchant: String? = null,
        val last4: String? = null,
        val balance: BigDecimal? = null,
    )

    private class Format(val bankName: String, val sender: Regex, val read: (String) -> Reading?)

    fun knows(sender: String): Boolean = FORMATS.any { it.sender.matches(sender) }

    /** The transaction [sms] describes, or null when its sender's format doesn't match the text. */
    fun parse(sms: RawSms): ParsedSms? {
        val format = FORMATS.firstOrNull { it.sender.matches(sms.sender) } ?: return null
        val reading = format.read(sms.body) ?: return null
        val amountPaise = ParsedTransactionMapper.toPaiseOrNull(reading.amount)?.takeIf { it > 0 } ?: return null
        return ParsedSms(
            txnHash = txnHash(sms, amountPaise),
            amountPaise = amountPaise,
            direction = reading.direction,
            parserType = reading.type,
            isCard = false,
            merchant = reading.merchant,
            reference = null,
            bankName = format.bankName,
            accountLast4 = reading.last4,
            balanceAfterPaise = reading.balance?.let(ParsedTransactionMapper::toPaiseOrNull),
            sender = sms.sender,
            body = sms.body,
            occurredAt = sms.receivedAt,
        )
    }

    /** Stable for the same SMS from the receiver and the inbox scan (sender, amount, body). */
    private fun txnHash(sms: RawSms, amountPaise: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("local|${sms.sender}|$amountPaise|${sms.body}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(HASH_LENGTH)

    private const val HASH_LENGTH = 32
    private const val AMOUNT = """([\d,]+(?:\.\d{1,2})?)"""
    private const val RUPEES = """(?:₹|rs\.?|inr)\s*"""

    private fun rupees(text: String): BigDecimal? = text.replace(",", "").toBigDecimalOrNull()

    private fun header(core: String) = Regex("""^[A-Z]{2}-$core(-[A-Z])?$""", RegexOption.IGNORE_CASE)

    private fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private fun Regex.amountIn(body: String, group: Int = 1): BigDecimal? =
        find(body)?.groupValues?.get(group)?.let(::rupees)

    /** NKGSB Co-op Bank: "XXXXX a/c X929 is CREDITED INR 2.03 on ... Clr bal is INR 999.99." */
    private val NKGSB_MOVE = regex("""\ba/c\s*[x*]*(\d{2,})\s+is\s+(credited|debited)\s+$RUPEES$AMOUNT""")
    private val NKGSB_BALANCE = regex("""\bclr\.?\s*bal(?:ance)?\s+is\s+$RUPEES$AMOUNT""")

    /** Indian Railways R-Wallet: "Rs.20.00 debited from your RWallet Account on ... Available Balance Rs.485.10." */
    // The wallet's name is one word ("RWallet"), masked in parser feedback; the sender pins the format.
    private val RWALLET_MOVE = regex("""$RUPEES$AMOUNT\s+(credited to|debited from)\s+your\s+[a-z-]+\s+account""")
    private val RWALLET_BALANCE = regex("""\bavailable\s+balance(?:\s+is)?\s+$RUPEES$AMOUNT""")

    /** CRED wallet: "₹25 has been successfully credited to your CRED wallet", "You have successfully paid Rs.1.0". */
    private val CRED_CREDIT =
        regex("""$RUPEES$AMOUNT\s+has\s+been\s+successfully\s+credited\s+to\s+your\s+\w+\s+wallet""")
    private val CRED_PAID = regex("""\byou\s+have\s+successfully\s+paid\s+$RUPEES$AMOUNT""")
    private val CRED_BALANCE = regex("""\bwallet\s+balance\s+is\s+$RUPEES$AMOUNT""")

    private val FORMATS = listOf(
        Format("NKGSB Bank", header("NKGSBK")) { body ->
            val move = NKGSB_MOVE.find(body) ?: return@Format null
            val credited = move.groupValues[2].equals("credited", ignoreCase = true)
            Reading(
                amount = rupees(move.groupValues[3]) ?: return@Format null,
                type = if (credited) ParserType.INCOME else ParserType.EXPENSE,
                direction = if (credited) Direction.IN else Direction.OUT,
                last4 = move.groupValues[1].takeLast(4),
                balance = NKGSB_BALANCE.amountIn(body),
            )
        },
        Format("Indian Railways R-Wallet", header("IRSMS[A-Z]?")) { body ->
            val move = RWALLET_MOVE.find(body) ?: return@Format null
            val debit = move.groupValues[2].startsWith("debited", ignoreCase = true)
            Reading(
                amount = rupees(move.groupValues[1]) ?: return@Format null,
                type = if (debit) ParserType.EXPENSE else ParserType.TRANSFER,
                direction = if (debit) Direction.OUT else Direction.IN,
                // Every R-Wallet debit is a ticket; a credit is a top-up or a refund.
                merchant = if (debit) "Indian Railways" else null,
                balance = RWALLET_BALANCE.amountIn(body),
            )
        },
        Format("CRED Wallet", header("CREDWL")) { body ->
            val balance = CRED_BALANCE.amountIn(body)
            CRED_PAID.amountIn(body)?.let { paid ->
                return@Format Reading(paid, ParserType.EXPENSE, Direction.OUT, balance = balance)
            }
            CRED_CREDIT.amountIn(body)?.let { Reading(it, ParserType.TRANSFER, Direction.IN, balance = balance) }
        },
    )
}
