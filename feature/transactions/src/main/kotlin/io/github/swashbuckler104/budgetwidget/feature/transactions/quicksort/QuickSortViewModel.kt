package io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortResult
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionEngine
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionRequest
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickSortUiState(
    val loading: Boolean = true,
    val transaction: Transaction? = null,
    /** Pending transactions left, including the one shown. */
    val remaining: Int = 0,
    /** Pending and from an earlier local day (SCOPE.md §3 "Overdue"). */
    val overdue: Boolean = false,
    val suggestions: List<Category> = emptyList(),
    /** [suggestions] are computed for the shown transaction (false while they load). */
    val suggestionsReady: Boolean = false,
    val groups: List<CategoryGroup> = emptyList(),
    /** The opened transaction was sorted, or the inbox is empty. */
    val finished: Boolean = false,
    /** Opened for one transaction: close when finished instead of showing "All sorted". */
    val singleTransaction: Boolean = false,
    val working: Boolean = false,
)

/**
 * Drives the Quick-Sort sheet. Opened with [ARG_TRANSACTION_ID] it sorts that one transaction and
 * finishes; without it, it walks the inbox oldest first. "Skip" moves an item to the back.
 */
private data class Focus(val transaction: Transaction?, val remaining: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuickSortViewModel @Inject constructor(
    savedState: SavedStateHandle,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    private val suggestions: SuggestionEngine,
    private val sort: SortTransactionUseCase,
    private val clock: WallClock,
) : ViewModel() {
    private val fixedId: Long? = savedState.get<Long>(ARG_TRANSACTION_ID)?.takeIf { it > 0 }
    private val skipped = MutableStateFlow<List<Long>>(emptyList())
    private val sortedFixed = MutableStateFlow(false)
    private val working = MutableStateFlow(false)

    private val groups: Flow<List<CategoryGroup>> = categories.observeGroups()

    /**
     * The shown transaction and the inbox size, always from the same inbox emission: two separate
     * subscriptions could briefly pair "no transaction" with a stale count.
     */
    private val focus: Flow<Focus> = (
        if (fixedId != null) {
            combine(transactions.observeTransaction(fixedId), transactions.observePending()) { t, list ->
                Focus(t, list.size)
            }
        } else {
            combine(transactions.observePending(), skipped) { list, skip ->
                val shown =
                    list.firstOrNull { it.id !in skip }
                        ?: skip.firstNotNullOfOrNull { id -> list.firstOrNull { it.id == id } }
                Focus(shown, list.size)
            }
        }
        ).shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** Suggestions tagged with the transaction they were computed for. */
    private val suggested: Flow<Pair<Long?, List<Category>>> = combine(
        focus.map { f -> f.transaction?.let { it.id to it.toRequest() } }.distinctUntilChanged(),
        groups,
    ) { request, _ -> request }
        .mapLatest { request -> request?.first to request?.second?.let { suggestions.suggest(it) }.orEmpty() }

    val state: StateFlow<QuickSortUiState> = combine(
        focus,
        groups,
        suggested,
        if (fixedId != null) sortedFixed else flowOf(false),
        working,
    ) { (transaction, remaining), allGroups, (suggestedFor, chips), fixedDone, busy ->
        QuickSortUiState(
            loading = false,
            transaction = transaction,
            remaining = remaining,
            overdue =
                transaction != null && transaction.status == TransactionStatus.PENDING &&
                    localDate(transaction.occurredAt) < localDate(clock.nowMillis()),
            // Never show chips computed for the previous transaction.
            suggestions = if (suggestedFor == transaction?.id) chips else emptyList(),
            suggestionsReady = suggestedFor == transaction?.id,
            groups = allGroups,
            finished = fixedDone || transaction == null,
            singleTransaction = fixedId != null,
            working = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuickSortUiState())

    /** Sorts the shown transaction into [category]; [remember] teaches the merchant rule. */
    fun choose(category: Category, note: String, remember: Boolean) = act { id ->
        sort.categorize(id, category.id, note, remember)
    }

    fun ignore() = act { id -> sort.ignore(id) }

    fun skip() {
        val id = state.value.transaction?.id ?: return
        if (fixedId != null) sortedFixed.value = true else skipped.update { it - id + id }
    }

    private fun act(block: suspend (Long) -> SortResult) {
        val id = state.value.transaction?.id ?: return
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try {
                if (block(id) == SortResult.Done) {
                    if (fixedId != null) sortedFixed.value = true else skipped.update { it - id }
                }
            } finally {
                working.value = false
            }
        }
    }

    private fun Transaction.toRequest() = SuggestionRequest(merchantKey, direction, amountPaise, parserType)

    private fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        /** Intent extra / saved-state key: the transaction to sort; absent = walk the inbox. */
        const val ARG_TRANSACTION_ID = "transactionId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
