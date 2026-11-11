package io.github.swashbuckler104.budgetwidget.core.model.sms

/**
 * An SMS as delivered by the system: multipart messages are already joined. [receivedAt] is a
 * UTC instant in epoch milliseconds.
 */
data class RawSms(val sender: String, val body: String, val receivedAt: Long)

/**
 * The app's view of the SMS parser (anti-corruption layer, SCOPE.md §5). Only `:ingest`
 * implements it; everything else depends on this interface and on [SmsParseResult].
 */
interface SmsParser {
    /** Cheap sender check. Messages from other senders are never parsed or stored. */
    fun isKnownBankSender(sender: String): Boolean

    /**
     * For a sender that is not a known bank: whether the message still clearly reads as a bank
     * transaction alert, so it is kept in the unparsed list for a parser fix (ADR-0028).
     */
    fun looksLikeUnknownBankAlert(sms: RawSms): Boolean = false

    fun parse(sms: RawSms): SmsParseResult
}

sealed interface SmsParseResult {
    data class Transaction(val parsed: ParsedSms) : SmsParseResult

    /** A balance alert: not a transaction, it only updates the account's balance. */
    data class BalanceUpdate(val bankName: String, val accountLast4: String?, val balancePaise: Long) : SmsParseResult

    /** A known bank sender whose message did not become a transaction (kept for bug reports). */
    data class Unparsed(val reason: UnparsedReason) : SmsParseResult
}

enum class UnparsedReason {
    /** No parser recognised the message (OTPs, promotions, unknown formats). */
    NO_MATCH,

    /** v1 only tracks INR (SCOPE.md F2). */
    UNSUPPORTED_CURRENCY,

    /** Zero, negative or unrepresentable amount. */
    INVALID_AMOUNT,

    /** The parser threw. Recorded instead of crashing so one odd SMS can't stall the inbox scan. */
    PARSER_ERROR,

    /** Parsed, but the user reported the amount as wrong from the edit sheet (SCOPE.md F7). */
    WRONG_AMOUNT,

    /** No parser knows the sender, but the message reads like a bank transaction alert (ADR-0028). */
    UNKNOWN_SENDER,
}
