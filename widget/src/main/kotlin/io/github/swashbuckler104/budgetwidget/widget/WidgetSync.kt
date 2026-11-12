package io.github.swashbuckler104.budgetwidget.widget

import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps widgets in step with the database while the process lives: any write to transactions
 * (so today's spending too), categories or recorded days redraws them, debounced
 * because launchers rate-limit widget updates (SCOPE.md F4). Every write happens in this process,
 * so nothing is missed; entry points that may die right away also call `refreshNow` themselves.
 */
internal class WidgetSync @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val days: DayCloseRepository,
    private val widgets: GlanceInboxWidgets,
) : ProcessStartTask {
    @OptIn(FlowPreview::class)
    override fun start(scope: CoroutineScope) {
        // Change signals only: every emission means "something was written", no rows are loaded.
        // The first value is the state at process start, which the widgets already show.
        val changes = combine(transactions.observeChanges(), categories.observeGroups(), days.observeLatestDay()) {
                _,
                _,
                _,
            ->
        }.drop(1)
        scope.launch { changes.debounce(QUICK_SORT_DEBOUNCE).collect { widgets.refreshQuickSort() } }
        // The chart re-renders a bitmap: at most every 2 s while sorting (SCOPE.md F5).
        scope.launch { changes.debounce(INSIGHT_DEBOUNCE).collect { widgets.refreshInsight() } }
    }

    private companion object {
        val QUICK_SORT_DEBOUNCE = 500.milliseconds
        val INSIGHT_DEBOUNCE = 2.seconds
    }
}
