package io.github.swashbuckler104.budgetwidget.widget

import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
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
    private val widgets: InboxWidgets,
) : ProcessStartTask {
    @OptIn(FlowPreview::class)
    override fun start(scope: CoroutineScope) {
        scope.launch {
            // Change signals only: every emission means "something was written", no rows are loaded.
            combine(transactions.observeChanges(), categories.observeGroups(), days.observeLatestDay()) { _, _, _ -> }
                // The first value is the state at process start, which the widgets already show.
                .drop(1)
                .debounce(DEBOUNCE)
                .collect { widgets.refreshNow() }
        }
    }

    private companion object {
        val DEBOUNCE = 500.milliseconds
    }
}
