package io.github.swashbuckler104.budgetwidget.core.data.list

import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus

/**
 * Spending (ADR-0020): outgoing, not ignored, and not sorted into a transfer-kind category (moving
 * money to your own accounts or investments isn't spending). Pending transactions count.
 * [kindOf] maps a category id to its kind (null for an unknown id).
 */
fun Transaction.isSpend(kindOf: (Long) -> CategoryKind?): Boolean =
    direction == Direction.OUT && counts(kindOf)

/** Income: the mirror of [isSpend] for incoming money. */
fun Transaction.isIncome(kindOf: (Long) -> CategoryKind?): Boolean =
    direction == Direction.IN && counts(kindOf)

private fun Transaction.counts(kindOf: (Long) -> CategoryKind?): Boolean =
    status != TransactionStatus.IGNORED && categoryId?.let(kindOf) != CategoryKind.TRANSFER
