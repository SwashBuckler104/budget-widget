package io.github.swashbuckler104.budgetwidget.widget

import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.analysis.CategorySpend
import io.github.swashbuckler104.budgetwidget.core.data.analysis.MonthAnalysis
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the Insight widget shows (SCOPE.md F5). Money is paise. */
internal data class InsightSnapshot(
    val todaySpentPaise: Long,
    val monthSpentPaise: Long,
    /** Change against last month up to the same day, in percent; null without data to compare. */
    val changePercent: Int?,
    /** Spending for the last 7 days, oldest first; the last one is today. */
    val lastWeek: List<Long>,
    /** Day of the week of each [lastWeek] entry, to label the bars. */
    val lastWeekDays: List<LocalDate>,
    /** The month's three biggest top-level categories (or "To sort"), largest first. */
    val topCategories: List<CategorySpend>,
    val month: YearMonth,
) {
    companion object {
        fun compute(
            transactions: List<Transaction>,
            groups: List<CategoryGroup>,
            nowMillis: Long,
            zone: ZoneId,
        ): InsightSnapshot {
            val categories: Map<Long, Category> = groups.flatMap {
                listOf(it.category) + it.children
            }.associateBy { it.id }
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val month = YearMonth.from(today)
            val analysis = MonthAnalysis.compute(transactions, categories, month, today, zone)
            val week = MonthAnalysis.lastDays(transactions, categories, today, zone, DAYS)
            return InsightSnapshot(
                todaySpentPaise = week.last(),
                monthSpentPaise = analysis.spentPaise,
                changePercent = analysis.changePercent,
                lastWeek = week,
                lastWeekDays = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) },
                topCategories = analysis.categories.take(TOP),
                month = month,
            )
        }

        const val DAYS = 7
        const val TOP = 3
    }
}

internal class InsightSnapshotLoader @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: WallClock,
    private val signal: WidgetRefreshSignal,
) {
    /**
     * Recomputes on any transaction or category change and on explicit refreshes (the 00:05 day
     * tick), at most once per [DEBOUNCE] while changes keep coming (SCOPE.md F5).
     */
    @OptIn(FlowPreview::class)
    fun observe(): Flow<InsightSnapshot> = combine(
        transactions.observeTransactions(),
        categories.observeGroups(),
        signal.ticks,
    ) { all, groups, _ -> all to groups }
        .debounce(DEBOUNCE)
        .map { (all, groups) -> InsightSnapshot.compute(all, groups, clock.nowMillis(), ZoneId.systemDefault()) }

    suspend fun load(): InsightSnapshot = InsightSnapshot.compute(
        transactions.observeTransactions().first(),
        categories.observeGroups().first(),
        clock.nowMillis(),
        ZoneId.systemDefault(),
    )

    private companion object {
        val DEBOUNCE = 2.seconds
    }
}
