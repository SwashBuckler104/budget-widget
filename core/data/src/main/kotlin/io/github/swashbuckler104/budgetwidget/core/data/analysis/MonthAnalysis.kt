package io.github.swashbuckler104.budgetwidget.core.data.analysis

import io.github.swashbuckler104.budgetwidget.core.data.list.isIncome
import io.github.swashbuckler104.budgetwidget.core.data.list.isSpend
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Spending in one category (top-level, or a subcategory in [children]). [category] is null for
 * the "To sort" slice: spending not sorted yet. Money is paise.
 */
data class CategorySpend(
    val category: Category?,
    val totalPaise: Long,
    val count: Int,
    /** Subcategory breakdown, largest first; empty for a leaf. */
    val children: List<CategorySpend> = emptyList(),
)

/**
 * A merchant's spending in the month. [name] is null for transactions without one; [merchantKey]
 * is null when none of them has a key (then only the name identifies it).
 */
data class MerchantSpend(val name: String?, val totalPaise: Long, val count: Int, val merchantKey: String? = null)

/** Everything the Analysis tab shows for one month (SCOPE.md F7 Tab 1). Money is paise. */
data class MonthAnalysis(
    val month: YearMonth,
    val spentPaise: Long,
    val incomePaise: Long,
    /** Days the average covers: elapsed days for the current month, all for a past one. */
    val daysCounted: Int,
    /** The previous month's spending up to the same day of month (whole month for a past one). */
    val previousSpentPaise: Long,
    /** Top-level categories (plus "To sort"), largest first. */
    val categories: List<CategorySpend>,
    /** Spending per day of the month; index 0 is the 1st. */
    val daily: List<Long>,
    /** Running total per day, up to today for the current month. */
    val cumulative: List<Long>,
    /** The previous month's running total, whole month. */
    val previousCumulative: List<Long>,
    /** Largest merchants, at most [TOP_MERCHANTS]. */
    val topMerchants: List<MerchantSpend>,
    /** Transactions in the month at all (any status), to tell an empty month apart. */
    val transactionCount: Int,
) {
    val netPaise: Long get() = incomePaise - spentPaise
    val averagePerDayPaise: Long get() = if (daysCounted > 0) spentPaise / daysCounted else 0

    /** Change against [previousSpentPaise] in percent, null when there is nothing to compare with. */
    val changePercent: Int?
        get() {
            if (previousSpentPaise <= 0) return null
            // Clamped: a previous month of a few paise would otherwise overflow Int.
            val percent = (spentPaise - previousSpentPaise) * PERCENT / previousSpentPaise
            return percent.coerceIn(-MAX_PERCENT, MAX_PERCENT).toInt()
        }

    companion object {
        const val TOP_MERCHANTS = 5
        private const val PERCENT = 100L
        private const val MAX_PERCENT = 999_999L

        /**
         * Computes [month] from [transactions] (any order). [categories] maps every category id to
         * the category; [today] decides how much of the current month has elapsed. With
         * [accountId] only that account's transactions count.
         */
        fun compute(
            transactions: List<Transaction>,
            categories: Map<Long, Category>,
            month: YearMonth,
            today: LocalDate,
            zone: ZoneId,
            accountId: Long? = null,
        ): MonthAnalysis {
            val kindOf: (Long) -> CategoryKind? = { categories[it]?.kind }
            val previous = month.minusMonths(1)
            val scoped = if (accountId == null) transactions else transactions.filter { it.accountId == accountId }
            fun dateOf(t: Transaction): LocalDate = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
            val inMonth = scoped.filter { YearMonth.from(dateOf(it)) == month }
            val inPrevious = scoped.filter { YearMonth.from(dateOf(it)) == previous }
            val spend = inMonth.filter { it.isSpend(kindOf) }
            val previousSpend = inPrevious.filter { it.isSpend(kindOf) }

            val current = YearMonth.from(today)
            val elapsed = when {
                month == current -> today.dayOfMonth
                month < current -> month.lengthOfMonth()
                else -> 0
            }
            val previousUpTo = if (month ==
                current
            ) {
                minOf(elapsed, previous.lengthOfMonth())
            } else {
                previous.lengthOfMonth()
            }

            val daily = LongArray(month.lengthOfMonth())
            spend.forEach { daily[dateOf(it).dayOfMonth - 1] += it.amountPaise }
            val previousDaily = LongArray(previous.lengthOfMonth())
            previousSpend.forEach { previousDaily[dateOf(it).dayOfMonth - 1] += it.amountPaise }

            return MonthAnalysis(
                month = month,
                spentPaise = spend.sumOf { it.amountPaise },
                incomePaise = inMonth.filter { it.isIncome(kindOf) }.sumOf { it.amountPaise },
                daysCounted = elapsed,
                previousSpentPaise = previousDaily.take(previousUpTo).sum(),
                categories = byCategory(spend, categories),
                daily = daily.toList(),
                cumulative = daily.toList().runningReduce(Long::plus).take(elapsed),
                previousCumulative = previousDaily.toList().runningReduce(Long::plus),
                topMerchants = spend
                    .groupBy { it.merchantKey ?: it.merchantDisplay?.lowercase() }
                    .map { (_, items) ->
                        MerchantSpend(
                            name = items.firstNotNullOfOrNull { it.merchantDisplay },
                            totalPaise = items.sumOf { it.amountPaise },
                            count = items.size,
                            merchantKey = items.firstNotNullOfOrNull { it.merchantKey },
                        )
                    }
                    // Rows without a merchant name aren't one merchant: leave them out.
                    .filter { it.name != null }
                    .sortedByDescending { it.totalPaise }
                    .take(TOP_MERCHANTS),
                transactionCount = inMonth.size,
            )
        }

        private fun byCategory(spend: List<Transaction>, categories: Map<Long, Category>): List<CategorySpend> {
            val (sorted, unsorted) = spend.partition {
                it.status == TransactionStatus.CATEGORIZED &&
                    it.categoryId in categories
            }
            val groups = sorted.groupBy { t ->
                val leaf = categories.getValue(t.categoryId!!)
                leaf.parentId?.let(categories::get) ?: leaf
            }.map { (top, items) ->
                val children = if (items.all { it.categoryId == top.id }) {
                    emptyList()
                } else {
                    items.groupBy { categories.getValue(it.categoryId!!) }
                        .map { (leaf, leafItems) ->
                            CategorySpend(leaf, leafItems.sumOf { it.amountPaise }, leafItems.size)
                        }
                        .sortedByDescending { it.totalPaise }
                }
                CategorySpend(top, items.sumOf { it.amountPaise }, items.size, children)
            }
            val toSort = if (unsorted.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    CategorySpend(
                        null,
                        unsorted.sumOf {
                            it.amountPaise
                        },
                        unsorted.size,
                    ),
                )
            }
            return (groups + toSort).sortedByDescending { it.totalPaise }
        }

        /** Spending per day for the [days] days ending [today] (oldest first), across month boundaries. */
        fun lastDays(
            transactions: List<Transaction>,
            categories: Map<Long, Category>,
            today: LocalDate,
            zone: ZoneId,
            days: Int,
        ): List<Long> {
            val first = today.minusDays(days - 1L)
            val totals = LongArray(days)
            transactions.forEach { t ->
                if (!t.isSpend { categories[it]?.kind }) return@forEach
                val date = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
                if (date < first || date > today) return@forEach
                totals[(date.toEpochDay() - first.toEpochDay()).toInt()] += t.amountPaise
            }
            return totals.toList()
        }
    }
}
