package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import javax.inject.Inject

/**
 * [SmsParser] backed by PennyWise parser-core (third_party/pennywise, AGPL-3.0).
 *
 * Upstream covers banks in many countries, and some match senders loosely (Everest Bank, Nepal,
 * claims every 7-10 digit number). We narrow it so personal SMS are never treated as financial
 * (hard constraint 4, docs/DECISIONS.md ADR-0017):
 * - a phone number is never a bank sender (Indian banks must use registered alphanumeric headers);
 * - promotional headers (TRAI suffix `-P`, e.g. `JD-KOTAKB-P`) are marketing, never transaction
 *   alerts; upstream parses some card offers ("get 5 points per Rs.100 spent") as spends;
 * - only INR parsers count (v1 tracks INR only).
 */
internal class PennyWiseSmsParser @Inject constructor() : SmsParser {
    override fun isKnownBankSender(sender: String): Boolean = parsersFor(sender).isNotEmpty()

    override fun parse(sms: RawSms): SmsParseResult {
        val parsed = try {
            // Same order and semantics as BankParserFactory.parse, restricted to our parsers.
            parsersFor(sms.sender).firstNotNullOfOrNull { it.parse(sms.body, sms.sender, sms.receivedAt) }
        } catch (_: RuntimeException) {
            // Upstream regex code on unexpected input; keep the SMS for a bug report instead.
            return SmsParseResult.Unparsed(UnparsedReason.PARSER_ERROR)
        } ?: return SmsParseResult.Unparsed(UnparsedReason.NO_MATCH)
        return ParsedTransactionMapper.map(parsed, sms)
    }

    private fun parsersFor(sender: String): List<BankParser> {
        if (PHONE_NUMBER.matches(sender.replace(SEPARATORS, ""))) return emptyList()
        if (PROMOTIONAL_HEADER.matches(sender)) return emptyList()
        return BankParserFactory.getParsers(sender).filter { it.getCurrency().equals(INR, ignoreCase = true) }
    }

    private companion object {
        const val INR = "INR"
        val PHONE_NUMBER = Regex("""\+?\d+""")
        val SEPARATORS = Regex("""[\s().-]""")

        /** TRAI header `XX-HEADER-P`: P promotional (S service, T transactional, G government). */
        val PROMOTIONAL_HEADER = Regex("""^[A-Z]{2}-[A-Z0-9]+-P$""", RegexOption.IGNORE_CASE)
    }
}
