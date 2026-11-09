package io.github.swashbuckler104.budgetwidget.core.model.sms

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType

/**
 * A transaction read from one bank SMS. Money is in paise (positive; [direction] gives the
 * sign). [occurredAt] is the SMS receive time, a UTC instant in epoch milliseconds.
 *
 * [txnHash] is stable for the same SMS whether it arrives through the receiver or the inbox scan,
 * so it is the idempotency key.
 */
data class ParsedSms(
    val txnHash: String,
    val amountPaise: Long,
    val direction: Direction,
    val parserType: ParserType,
    val isCard: Boolean,
    val merchant: String?,
    val reference: String?,
    val bankName: String,
    val accountLast4: String?,
    val balanceAfterPaise: Long?,
    val sender: String,
    val body: String,
    val occurredAt: Long,
)
