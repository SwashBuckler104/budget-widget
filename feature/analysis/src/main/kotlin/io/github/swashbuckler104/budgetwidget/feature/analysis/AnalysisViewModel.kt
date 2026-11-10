package io.github.swashbuckler104.budgetwidget.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.analysis.MonthAnalysis
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class AnalysisUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    /** The month holding today: the switcher never goes past it. */
    val currentMonth: YearMonth = YearMonth.now(),
    /** The month of the oldest transaction: the switcher never goes before it. */
    val firstMonth: YearMonth = YearMonth.now(),
    val analysis: MonthAnalysis? = null,
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    /** Inbox size, for the banner (SCOPE.md F7). */
    val pending: Int = 0,
) {
    val canGoBack: Boolean get() = month > firstMonth
    val canGoForward: Boolean get() = month < currentMonth
}

/** Drives Tab 1 (SCOPE.md F7 "Analysis"): one month at a time, optionally one account. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    days: DayCloseRepository,
    private val clock: WallClock,
) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val month = MutableStateFlow(today())
    private val accountId = MutableStateFlow<Long?>(null)

    val state: StateFlow<AnalysisUiState> = combine(
        transactions.observeTransactions(),
        categories.observeGroups(),
        transactions.observeAccounts(),
        month,
        accountId,
    ) { all, groups, accounts, shownMonth, account ->
        val byId = groups.flatMap { listOf(it.category) + it.children }.associateBy { it.id }
        val now = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val current = YearMonth.from(now)
        // Transactions are newest first: the last one is the oldest.
        val first =
            all.lastOrNull()?.let { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) } ?: current
        AnalysisUiState(
            loading = false,
            month = shownMonth,
            currentMonth = current,
            firstMonth = minOf(first, current),
            analysis = MonthAnalysis.compute(all, byId, shownMonth, now, zone, account),
            accounts = accounts.filterNot { it.hidden },
            accountId = account,
            pending = all.count { it.status == TransactionStatus.PENDING },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AnalysisUiState())

    /** Close-the-day streak for the header (SCOPE.md F6); follows sorts and recorded days. */
    val streak: StateFlow<Int> = combine(transactions.observeChanges(), days.observeLatestDay()) { _, _ -> }
        .mapLatest { days.status().streak }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** Moves the month by [delta], within the first and current month. */
    fun shiftMonth(delta: Long) {
        val s = state.value
        month.update { (it.plusMonths(delta)).coerceIn(s.firstMonth, s.currentMonth) }
    }

    fun selectAccount(id: Long?) = accountId.update { id }

    /** `[start, end)` of the shown month as epoch ms, for the drill-down list. */
    fun monthRange(): Pair<Long, Long> {
        val m = month.value
        return m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun today(): YearMonth = YearMonth.from(Instant.ofEpochMilli(clock.nowMillis()).atZone(zone))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
