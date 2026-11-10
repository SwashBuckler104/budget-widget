package io.github.swashbuckler104.budgetwidget.widget

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bumped on every explicit refresh. `updateAll` only recomposes a widget whose Glance session is
 * still alive, and its snapshot otherwise recomputes on database changes alone, so time-based text
 * (`~2 h left`, Overdue, today's spending after a clock or time-zone change) would stay stale.
 * The snapshot flow listens to this too.
 */
@Singleton
internal class WidgetRefreshSignal @Inject constructor() {
    private val count = MutableStateFlow(0L)
    val ticks: StateFlow<Long> = count.asStateFlow()

    fun bump() = count.update { it + 1 }
}
