package io.github.swashbuckler104.budgetwidget.core.database.model

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus

/**
 * A transaction as lists show it: no SMS body, raw merchant or reference, so observing every
 * transaction stays cheap. Joined with its account's bank name and last 4 digits.
 */
data class TransactionRow(
    val id: Long,
    val source: TransactionSource,
    val amountPaise: Long,
    val direction: Direction,
    val parserType: ParserType?,
    val merchantDisplay: String?,
    val merchantKey: String?,
    val accountId: Long?,
    val occurredAt: Long,
    val isCard: Boolean,
    val status: TransactionStatus,
    val ignoreReason: IgnoreReason?,
    val categoryId: Long?,
    val autoCategorized: Boolean,
    val isBackfill: Boolean,
    val note: String?,
    val duplicateOfId: Long?,
    val editedByUser: Boolean,
    val bankName: String?,
    val accountLast4: String?,
)
