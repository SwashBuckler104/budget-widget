package io.github.swashbuckler104.budgetwidget.widget

import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayMath
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionEngine
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionRequest
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

/** Everything one widget instance shows. Money is paise; [now] is epoch ms (UTC). */
internal data class WidgetSnapshot(
    /** Every PENDING transaction, backfill included. */
    val pending: Int,
    /** Pending ones from an earlier local day (SCOPE.md §3 "Overdue"). */
    val overdue: Int,
    /** The card: the instance's current transaction, or the oldest. */
    val focus: Transaction?,
    val focusOverdue: Boolean,
    /** Suggestions for [focus] (SCOPE.md F8), up to 6. */
    val chips: List<Category>,
    /** The next two after [focus], for the large size. */
    val upNext: List<Transaction>,
    val todaySpentPaise: Long,
    val streak: Int,
    /** `~N h left` in the day's last hours, null before ("closes at midnight"). */
    val hoursLeft: Int?,
    val now: Long,
)

internal class WidgetSnapshotLoader @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val suggestions: SuggestionEngine,
    private val days: DayCloseRepository,
    private val clock: WallClock,
    private val signal: WidgetRefreshSignal,
) {
    /** Recomputes on any inbox, category or recorded-day change, and on every explicit refresh. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(currentId: Long?): Flow<WidgetSnapshot> = combine(
        transactions.observePending(),
        categories.observeGroups(),
        days.observeLatestDay(),
        signal.ticks,
    ) { pending, _, _, _ -> pending }.mapLatest { load(it, currentId) }

    suspend fun load(currentId: Long?): WidgetSnapshot = load(transactions.observePending().first(), currentId)

    /** Pending ids, oldest first: the order the widget steps through. */
    suspend fun pendingIds(): List<Long> = transactions.observePending().first().map { it.id }

    private suspend fun load(pending: List<Transaction>, currentId: Long?): WidgetSnapshot {
        val zone = ZoneId.systemDefault()
        val nowMillis = clock.nowMillis()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        fun overdue(t: Transaction) = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate() < today

        val index = pending.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val focus = pending.getOrNull(index)
        val rotated = pending.drop(index) + pending.take(index)
        val chips = focus?.let {
            suggestions.suggest(SuggestionRequest(it.merchantKey, it.direction, it.amountPaise, it.parserType))
        }.orEmpty()
        val status = days.status(zone)
        return WidgetSnapshot(
            pending = pending.size,
            overdue = pending.count(::overdue),
            focus = focus,
            focusOverdue = focus != null && overdue(focus),
            chips = chips,
            upNext = rotated.drop(1).take(UP_NEXT),
            todaySpentPaise = status.todaySpentPaise,
            streak = status.streak,
            hoursLeft = DayMath.hoursLeft(now.toLocalTime()),
            now = nowMillis,
        )
    }

    private companion object {
        const val UP_NEXT = 2
    }
}

/** Moving through the inbox (oldest first) from one widget instance. Pure. */
internal object WidgetNavigation {
    /** The transaction [delta] steps from [current] (wrapping); null for an empty inbox. */
    fun step(ids: List<Long>, current: Long?, delta: Int): Long? {
        if (ids.isEmpty()) return null
        val index = ids.indexOf(current).coerceAtLeast(0)
        return ids[(index + delta).mod(ids.size)]
    }

    /** After [removed] leaves the inbox, the one that took its place (wrapping to the oldest). */
    fun afterRemoving(ids: List<Long>, removed: Long): Long? {
        val index = ids.indexOf(removed)
        val rest = ids - removed
        if (rest.isEmpty()) return null
        return rest[index.coerceAtLeast(0) % rest.size]
    }
}
