package io.github.swashbuckler104.budgetwidget.core.data.list

import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of the transaction list with its totals (SCOPE.md F7). Money is paise. */
data class DayGroup(
    val date: LocalDate,
    val transactions: List<Transaction>,
    /** [isSpend] total. */
    val spentPaise: Long,
    /** [isIncome] total. */
    val incomePaise: Long,
)

/** Groups [transactions] by local day in [zone], keeping their order (newest first in, newest day first out). */
fun groupByDay(transactions: List<Transaction>, zone: ZoneId, kindOf: (Long) -> CategoryKind?): List<DayGroup> =
    transactions
        .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
        .map { (date, items) ->
            DayGroup(
                date = date,
                transactions = items,
                spentPaise = items.filter { it.isSpend(kindOf) }.sumOf { it.amountPaise },
                incomePaise = items.filter { it.isIncome(kindOf) }.sumOf { it.amountPaise },
            )
        }
