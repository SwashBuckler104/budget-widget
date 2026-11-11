package io.github.swashbuckler104.budgetwidget.ingest.parser

import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms

/**
 * Recognises a bank transaction alert from a sender no parser knows (docs/DECISIONS.md ADR-0028),
 * so it can be kept in the unparsed list instead of vanishing: the owner's Jupiter account alerts
 * never showed up, and nothing told them why. Deliberately strict, because these bodies are
 * stored: a registered business header (never a phone number or a promotional `-P` header), an
 * amount in rupees, a money-movement verb and an account word, and no one-time password.
 */
internal object FinancialSmsHeuristic {
    fun looksLikeTransaction(sms: RawSms): Boolean {
        if (!BUSINESS_HEADER.matches(sms.sender) || PROMOTIONAL.matches(sms.sender)) return false
        val body = sms.body
        return AMOUNT.containsMatchIn(body) &&
            MOVEMENT.containsMatchIn(body) &&
            ACCOUNT.containsMatchIn(body) &&
            !SECRET.containsMatchIn(body)
    }

    /** TRAI headers: `XX-NAME`, optionally with an `-S`/`-T`/`-G`/`-P` suffix; or a bare 6-letter name. */
    private val BUSINESS_HEADER = Regex("""^([A-Z]{2}-[A-Z0-9]{3,9}(-[A-Z])?|[A-Z]{6})$""", RegexOption.IGNORE_CASE)
    private val PROMOTIONAL = Regex("""^[A-Z]{2}-[A-Z0-9]+-P$""", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*\d[\d,]*(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val MOVEMENT = Regex(
        """\b(credited|debited|spent|received|withdrawn|deposited|paid|sent|transferred)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ACCOUNT =
        Regex("""\b(a/c|acct|account|card|upi|vpa|wallet|imps|neft|rtgs)\b""", RegexOption.IGNORE_CASE)
    private val SECRET =
        Regex("""\b(otp|one[ -]time password|verification code|passcode|cvv)\b""", RegexOption.IGNORE_CASE)
}
