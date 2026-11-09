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
    val groups: List<CategoryGroup> = emptyList(),
    /** The opened transaction was sorted, or the inbox is empty. */
    val finished: Boolean = false,
    val working: Boolean = false,
)

/**
 * Drives the Quick-Sort sheet. Opened with [ARG_TRANSACTION_ID] it sorts that one transaction and
 * finishes; without it, it walks the inbox oldest first. "Skip" moves an item to the back.
 */
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

    private val pending: Flow<List<Transaction>> = transactions.observePending()
    private val groups: Flow<List<CategoryGroup>> = categories.observeGroups()

    private val current: Flow<Transaction?> = if (fixedId != null) {
        transactions.observeTransaction(fixedId)
    } else {
        combine(pending, skipped) { list, skip ->
            list.firstOrNull { it.id !in skip } ?: skip.firstNotNullOfOrNull { id -> list.firstOrNull { it.id == id } }
        }
    }

    private val suggested: Flow<List<Category>> = combine(
        current.map {
            it?.toRequest()
        }.distinctUntilChanged(),
        groups,
    ) { request, _ -> request }
        .mapLatest { request -> request?.let { suggestions.suggest(it) }.orEmpty() }

    val state: StateFlow<QuickSortUiState> = combine(
        combine(current, pending, groups, ::Triple),
        suggested,
        if (fixedId != null) sortedFixed else flowOf(false),
        working,
    ) { (transaction, pendingList, allGroups), chips, fixedDone, busy ->
        QuickSortUiState(
            loading = false,
            transaction = transaction,
            remaining = pendingList.size,
            overdue =
                transaction != null && transaction.status == TransactionStatus.PENDING &&
                    localDate(transaction.occurredAt) < localDate(clock.nowMillis()),
            suggestions = chips,
            groups = allGroups,
            finished = fixedDone || transaction == null,
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
