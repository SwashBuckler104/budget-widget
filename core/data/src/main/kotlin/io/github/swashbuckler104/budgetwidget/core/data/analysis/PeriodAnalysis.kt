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
import java.time.temporal.ChronoUnit

/**
 * Money in one category (top-level, or a subcategory in [children]). [category] is null for the
 * "To sort" slice: money not sorted yet. Money is paise.
 */
data class CategorySpend(
    val category: Category?,
    val totalPaise: Long,
    val count: Int,
    /** Subcategory breakdown, largest first; empty for a leaf. */
    val children: List<CategorySpend> = emptyList(),
)

/**
 * A merchant's spending in the period. [name] is null for transactions without one; [merchantKey]
 * is null when none of them has a key (then only the name identifies it).
 */
data class MerchantSpend(val name: String?, val totalPaise: Long, val count: Int, val merchantKey: String? = null)

/** Everything the Analysis tab and the Insight widget show for one period (SCOPE.md F7). Money is paise. */
data class PeriodAnalysis(
    val period: AnalysisPeriod,
    val spentPaise: Long,
    val incomePaise: Long,
    /** Days the average covers: the elapsed days of a period holding today, all days of a past one. */
    val daysCounted: Int,
    /** The previous period's spending up to the same point (all of it for a past period). */
    val previousSpentPaise: Long,
    /** Spending by top-level category (plus "To sort"), largest first. */
    val categories: List<CategorySpend>,
    /** Income by top-level category (plus "To sort"), largest first. */
    val incomeCategories: List<CategorySpend>,
    /** Spending per bucket ([AnalysisPeriod.unit]); index 0 is the period's first day or month. */
    val buckets: List<Long>,
    /** Running total per bucket, up to today's bucket for a period holding today. */
    val cumulative: List<Long>,
    /** The previous period's running total, all of it. */
    val previousCumulative: List<Long>,
    /** The bucket holding today, or null when the period doesn't. */
    val todayBucket: Int?,
    /** Largest merchants, at most [TOP_MERCHANTS]. */
    val topMerchants: List<MerchantSpend>,
    /** Transactions in the period at all (any status), to tell an empty period apart. */
    val transactionCount: Int,
) {
    val netPaise: Long get() = incomePaise - spentPaise
    val averagePerDayPaise: Long get() = if (daysCounted > 0) spentPaise / daysCounted else 0

    /** Average per elapsed bucket: per day, or per month for a year. */
    val averagePerBucketPaise: Long get() = if (cumulative.isNotEmpty()) spentPaise / cumulative.size else 0

    /** Change against [previousSpentPaise] in percent, null when there is nothing to compare with. */
    val changePercent: Int?
        get() {
            if (previousSpentPaise <= 0) return null
            // Clamped: a previous period of a few paise would otherwise overflow Int.
            val percent = (spentPaise - previousSpentPaise) * PERCENT / previousSpentPaise
            return percent.coerceIn(-MAX_PERCENT, MAX_PERCENT).toInt()
        }

    companion object {
        const val TOP_MERCHANTS = 5
        private const val PERCENT = 100L
        private const val MAX_PERCENT = 999_999L

        /**
         * Computes [period] from [transactions] (any order). [categories] maps every category id
         * to the category; [today] decides how much of a current period has elapsed. With
         * [accountId] only that account's transactions count.
         */
        fun compute(
            transactions: List<Transaction>,
            categories: Map<Long, Category>,
            period: AnalysisPeriod,
            today: LocalDate,
            zone: ZoneId,
            accountId: Long? = null,
        ): PeriodAnalysis {
            val kindOf: (Long) -> CategoryKind? = { categories[it]?.kind }
            val previous = period.previous()
            val scoped = if (accountId == null) transactions else transactions.filter { it.accountId == accountId }
            fun dateOf(t: Transaction): LocalDate = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate()
            val inPeriod = scoped.filter { dateOf(it) in period }
            val spend = inPeriod.filter { it.isSpend(kindOf) }
            val previousSpend = scoped.filter { it.isSpend(kindOf) && dateOf(it) in previous }

            val elapsed = when {
                today.isBefore(period.start) -> 0
                today.isAfter(period.endInclusive) -> period.days
                else -> (ChronoUnit.DAYS.between(period.start, today) + 1).toInt()
            }
            // A current period compares with the previous one up to the same day; a past one with all of it.
            val previousUpTo = if (today in period) {
                minOf(previous.start.plusDays(elapsed - 1L), previous.endInclusive)
            } else {
                previous.endInclusive
            }

            val buckets = bucketTotals(spend, period, ::dateOf)
            val todayBucket = if (today in period) bucketOf(period, today) else null
            val elapsedBuckets = when {
                todayBucket != null -> todayBucket + 1
                today.isAfter(period.endInclusive) -> buckets.size
                else -> 0
            }
            return PeriodAnalysis(
                period = period,
                spentPaise = spend.sumOf { it.amountPaise },
                incomePaise = inPeriod.filter { it.isIncome(kindOf) }.sumOf { it.amountPaise },
                daysCounted = elapsed,
                previousSpentPaise = previousSpend.filter {
                    !dateOf(it).isAfter(previousUpTo)
                }.sumOf { it.amountPaise },
                categories = byCategory(spend, categories),
                incomeCategories = byCategory(inPeriod.filter { it.isIncome(kindOf) }, categories),
                buckets = buckets,
                cumulative = buckets.runningReduce(Long::plus).take(elapsedBuckets),
                previousCumulative = bucketTotals(previousSpend, previous, ::dateOf).runningReduce(Long::plus),
                todayBucket = todayBucket,
                topMerchants = topMerchants(spend),
                transactionCount = inPeriod.size,
            )
        }

        /** The number of buckets [period] is split into. */
        fun bucketCount(period: AnalysisPeriod): Int = when (period.unit) {
            BucketUnit.DAY -> period.days
            BucketUnit.MONTH -> bucketOf(period, period.endInclusive) + 1
        }

        /** The first day of bucket [index] of [period] (clamped to the period's start). */
        fun bucketStart(period: AnalysisPeriod, index: Int): LocalDate = when (period.unit) {
            BucketUnit.DAY -> period.start.plusDays(index.toLong())
            BucketUnit.MONTH -> maxOf(YearMonth.from(period.start).plusMonths(index.toLong()).atDay(1), period.start)
        }

        private fun bucketOf(period: AnalysisPeriod, date: LocalDate): Int = when (period.unit) {
            BucketUnit.DAY -> ChronoUnit.DAYS.between(period.start, date).toInt()
            BucketUnit.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(period.start), YearMonth.from(date)).toInt()
        }

        private fun bucketTotals(
            items: List<Transaction>,
            period: AnalysisPeriod,
            dateOf: (Transaction) -> LocalDate,
        ): List<Long> {
            val totals = LongArray(bucketCount(period))
            items.forEach { t ->
                val date = dateOf(t)
                if (date in period) totals[bucketOf(period, date)] += t.amountPaise
            }
            return totals.toList()
        }

        private fun topMerchants(spend: List<Transaction>): List<MerchantSpend> = spend
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
            .take(TOP_MERCHANTS)

        private fun byCategory(items: List<Transaction>, categories: Map<Long, Category>): List<CategorySpend> {
            val (sorted, unsorted) = items.partition {
                it.status == TransactionStatus.CATEGORIZED && it.categoryId in categories
            }
            val groups = sorted.groupBy { t ->
                val leaf = categories.getValue(t.categoryId!!)
                leaf.parentId?.let(categories::get) ?: leaf
            }.map { (top, members) ->
                val children = if (members.all { it.categoryId == top.id }) {
                    emptyList()
                } else {
                    members.groupBy { categories.getValue(it.categoryId!!) }
                        .map { (leaf, leafItems) ->
                            CategorySpend(leaf, leafItems.sumOf { it.amountPaise }, leafItems.size)
                        }
                        .sortedByDescending { it.totalPaise }
                }
                CategorySpend(top, members.sumOf { it.amountPaise }, members.size, children)
            }
            val toSort = if (unsorted.isEmpty()) {
                emptyList()
            } else {
                listOf(CategorySpend(null, unsorted.sumOf { it.amountPaise }, unsorted.size))
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
