package io.github.swashbuckler104.budgetwidget.feature.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.analysis.AnalysisPeriod
import io.github.swashbuckler104.budgetwidget.core.data.analysis.PeriodAnalysis
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** The kinds of period the Analysis tab offers, in switcher order. */
enum class PeriodKind { WEEK, MONTH, YEAR, CUSTOM }

val AnalysisPeriod.kind: PeriodKind
    get() = when (this) {
        is AnalysisPeriod.Week -> PeriodKind.WEEK
        is AnalysisPeriod.Month -> PeriodKind.MONTH
        is AnalysisPeriod.Year -> PeriodKind.YEAR
        is AnalysisPeriod.Custom -> PeriodKind.CUSTOM
    }

data class AnalysisUiState(
    val loading: Boolean = true,
    val period: AnalysisPeriod = AnalysisPeriod.Month.of(LocalDate.now()),
    val today: LocalDate = LocalDate.now(),
    /** The date of the oldest transaction: the switcher never goes to a period before it. */
    val firstDate: LocalDate = LocalDate.now(),
    val analysis: PeriodAnalysis? = null,
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    /** Inbox size, for the banner (SCOPE.md F7). */
    val pending: Int = 0,
) {
    val canGoBack: Boolean get() = period.shift(-1)?.let { !it.endInclusive.isBefore(firstDate) } ?: false
    val canGoForward: Boolean get() = period.shift(1)?.let { !it.start.isAfter(today) } ?: false

    /** The period holds today: comparisons are "up to the same point". */
    val isCurrent: Boolean get() = today in period
}

/**
 * Drives Tab 1 (SCOPE.md F7 "Analysis"): one week, month, year or custom range at a time,
 * optionally one account. The period survives process death in [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    days: DayCloseRepository,
    private val clock: WallClock,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val periodText = saved.getStateFlow(KEY_PERIOD, AnalysisPeriod.Month.of(today()).encode())
    private val accountId = saved.getStateFlow(KEY_ACCOUNT, NO_ACCOUNT)

    val state: StateFlow<AnalysisUiState> = combine(
        transactions.observeTransactions(),
        categories.observeGroups(),
        transactions.observeAccounts(),
        periodText.map { AnalysisPeriod.decode(it) ?: AnalysisPeriod.Month.of(today()) },
        accountId.map { id -> id.takeIf { it != NO_ACCOUNT } },
    ) { all, groups, accounts, period, account ->
        val byId = groups.flatMap { listOf(it.category) + it.children }.associateBy { it.id }
        val now = today()
        // Transactions are newest first: the last one is the oldest.
        val first = all.lastOrNull()?.let { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() } ?: now
        AnalysisUiState(
            loading = false,
            period = period,
            today = now,
            firstDate = minOf(first, now),
            analysis = PeriodAnalysis.compute(all, byId, period, now, zone, account),
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

    private val period: AnalysisPeriod
        get() = AnalysisPeriod.decode(periodText.value) ?: AnalysisPeriod.Month.of(today())

    /** Moves by [delta] periods, never before the first transaction's period or past today's. */
    fun shift(delta: Long) {
        val s = state.value
        val target = period.shift(delta) ?: return
        if (target.endInclusive.isBefore(s.firstDate) || target.start.isAfter(today())) return
        saved[KEY_PERIOD] = target.encode()
    }

    /**
     * Switches to a week, month or year: the one holding the day the current view is anchored on
     * (today when it's in view, else the shown period's last day). [PeriodKind.CUSTOM] goes
     * through [selectRange] instead.
     */
    fun selectKind(kind: PeriodKind) {
        val current = period
        val anchor = if (today() in current) today() else current.endInclusive
        val target = when (kind) {
            PeriodKind.WEEK -> AnalysisPeriod.Week.of(anchor)
            PeriodKind.MONTH -> AnalysisPeriod.Month.of(anchor)
            PeriodKind.YEAR -> AnalysisPeriod.Year(anchor.year)
            PeriodKind.CUSTOM -> return
        }
        saved[KEY_PERIOD] = target.encode()
    }

    /** A custom range of local dates; reversed ends are swapped and future days are cut off. */
    fun selectRange(start: LocalDate, endInclusive: LocalDate) {
        val (from, to) = if (endInclusive.isBefore(start)) endInclusive to start else start to endInclusive
        val end = minOf(to, today())
        saved[KEY_PERIOD] = AnalysisPeriod.Custom(minOf(from, end), end).encode()
    }

    fun selectAccount(id: Long?) {
        saved[KEY_ACCOUNT] = id ?: NO_ACCOUNT
    }

    /** `[start, end)` of the shown period as epoch ms, for the drill-down list. */
    fun periodRange(): Pair<Long, Long> {
        val p = period
        return p.start.atStartOfDay(zone).toInstant().toEpochMilli() to
            p.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_PERIOD = "period"
        const val KEY_ACCOUNT = "account"
        const val NO_ACCOUNT = 0L
    }
}
