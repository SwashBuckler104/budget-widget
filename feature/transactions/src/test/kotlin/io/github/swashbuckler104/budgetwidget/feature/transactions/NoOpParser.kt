package io.github.swashbuckler104.budgetwidget.feature.transactions

import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason

/** For `TransactionEditor` in these tests: no bank senders, and text is kept as it is. */
internal object NoOpParser : SmsParser {
    override fun isKnownBankSender(sender: String) = false

    override fun parse(sms: RawSms): SmsParseResult = SmsParseResult.Unparsed(UnparsedReason.NO_MATCH)
}
