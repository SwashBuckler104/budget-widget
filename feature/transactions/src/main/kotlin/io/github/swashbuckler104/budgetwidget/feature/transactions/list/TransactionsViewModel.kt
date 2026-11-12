package io.github.swashbuckler104.budgetwidget.feature.transactions.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.edit.EditResult
import io.github.swashbuckler104.budgetwidget.core.data.edit.ManualTransaction
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEdit
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEditor
import io.github.swashbuckler104.budgetwidget.core.data.edit.Undo
import io.github.swashbuckler104.budgetwidget.core.data.edit.Undoable
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.list.DayGroup
import io.github.swashbuckler104.budgetwidget.core.data.list.TransactionFilter
import io.github.swashbuckler104.budgetwidget.core.data.list.groupByDay
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionDetail
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A pending transaction in the pinned inbox (SCOPE.md F7). */
data class InboxEntry(
    val transaction: Transaction,
    /** From an earlier local day (SCOPE.md §3). */
    val overdue: Boolean,
    /** The payment it may repeat (SCOPE.md F2 "Looks like the same payment"), if still stored. */
    val duplicateOf: Transaction?,
)

data class TransactionsUiState(
    val loading: Boolean = true,
    /** Pending, oldest first. Empty while a filter is active (results then include pending ones). */
    val inbox: List<InboxEntry> = emptyList(),
    val days: List<DayGroup> = emptyList(),
    val filter: TransactionFilter = TransactionFilter(),
    /** Opened from Analysis with a fixed category and month: no inbox, back arrow. */
    val drillDown: Boolean = false,
    val categories: Map<Long, Category> = emptyMap(),
    val groups: List<CategoryGroup> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selection: Set<Long> = emptySet(),
    /** Every stored transaction, to tell "nothing yet" from "nothing matches". */
    val total: Int = 0,
) {
    val selecting: Boolean get() = selection.isNotEmpty()

    /** Ids on screen in display order: the inbox first, then the days. */
    val visibleIds: List<Long> get() = inbox.map { it.transaction.id } +
        days.flatMap { day -> day.transactions.map { it.id } }
}

/** One-shot feedback; the ones with [undo] show an Undo action (SCOPE.md F7). */
sealed interface TransactionsMessage {
    val undo: Undo? get() = null

    data class Sorted(val count: Int, override val undo: Undo?) : TransactionsMessage

    data class Ignored(val count: Int, override val undo: Undo?) : TransactionsMessage

    data class Merged(override val undo: Undo?) : TransactionsMessage

    data class KeptBoth(override val undo: Undo?) : TransactionsMessage

    data class BackInInbox(override val undo: Undo?) : TransactionsMessage

    data class Deleted(override val undo: Undo?) : TransactionsMessage

    data object Saved : TransactionsMessage

    data object Added : TransactionsMessage

    data object Reported : TransactionsMessage

    data class Failed(val result: EditResult) : TransactionsMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    private val editor: TransactionEditor,
    private val smsSync: SmsSync,
    private val clock: WallClock,
) : ViewModel() {
    private val drillDown: TransactionFilter? = savedState.get<Long>(ARG_CATEGORY_ID)?.let { categoryId ->
        TransactionFilter(
            categoryId = categoryId.takeIf { it > 0 },
            accountId = savedState.get<Long>(ARG_ACCOUNT_ID)?.takeIf { it > 0 },
            fromMillis = savedState.get<Long>(ARG_FROM_MILLIS),
            toMillis = savedState.get<Long>(ARG_TO_MILLIS),
        )
    }
    private val filter = MutableStateFlow(drillDown ?: TransactionFilter())
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val editingId = MutableStateFlow<Long?>(null)
    private val messageChannel = Channel<TransactionsMessage>(Channel.BUFFERED)

    val messages: Flow<TransactionsMessage> = messageChannel.receiveAsFlow()

    val state: StateFlow<TransactionsUiState> = combine(
        transactions.observeTransactions(),
        categories.observeGroups(),
        transactions.observeAccounts(),
        filter,
        selection,
    ) { all, groups, accounts, currentFilter, selected ->
        build(all, groups, accounts, currentFilter, selected)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TransactionsUiState())

    /** The transaction open in the edit sheet, with its SMS; null when none (or it was deleted). */
    val editing: StateFlow<TransactionDetail?> = editingId
        .flatMapLatest { id -> id?.let { transactions.observeDetail(it) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun build(
        all: List<Transaction>,
        groups: List<CategoryGroup>,
        accounts: List<Account>,
        currentFilter: TransactionFilter,
        selected: Set<Long>,
    ): TransactionsUiState {
        val byId = groups.flatMap { listOf(it.category) + it.children }.associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val filtering = drillDown != null || currentFilter.isActive
        val shown = if (filtering) {
            all.filter { t -> currentFilter.matches(t, { byId[it]?.parentId }, { byId[it]?.name }) }
        } else {
            all.filter { it.status != TransactionStatus.PENDING }
        }
        val inbox = if (filtering) {
            emptyList()
        } else {
            val ids = all.associateBy { it.id }
            all.asReversed().filter { it.status == TransactionStatus.PENDING }.map { t ->
                InboxEntry(
                    transaction = t,
                    overdue = Instant.ofEpochMilli(t.occurredAt).atZone(zone).toLocalDate() < today,
                    duplicateOf = t.duplicateOfId?.let(ids::get),
                )
            }
        }
        val present = all.mapTo(HashSet()) { it.id }
        return TransactionsUiState(
            loading = false,
            inbox = inbox,
            days = groupByDay(shown, zone) { byId[it]?.kind },
            filter = currentFilter,
            drillDown = drillDown != null,
            categories = byId,
            groups = groups,
            accounts = accounts,
            // Drop ids that vanished (deleted manual entries) so the bar can't count ghosts.
            selection = selected.filterTo(HashSet()) { it in present },
            total = all.size,
        )
    }

    fun setFilter(update: (TransactionFilter) -> TransactionFilter) = filter.update(update)

    fun clearFilters() = filter.update { TransactionFilter(query = it.query) }

    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }

    fun selectAll() = selection.update { state.value.visibleIds.toSet() }

    fun clearSelection() = selection.update { emptySet() }

    fun openEditor(id: Long?) = editingId.update { id }

    fun categorize(ids: Collection<Long>, categoryId: Long, bulk: Boolean) = launch {
        val done = editor.categorize(ids, categoryId, rememberMerchant = !bulk)
        clearSelection()
        TransactionsMessage.Sorted(done.changed, done.undo).takeIf { done.changed > 0 }
    }

    fun ignore(ids: Collection<Long>) = launch {
        val done = editor.ignore(ids)
        clearSelection()
        TransactionsMessage.Ignored(done.changed, done.undo).takeIf { done.changed > 0 }
    }

    fun merge(id: Long) = undoable({ editor.merge(id) }) { TransactionsMessage.Merged(it) }

    fun keepBoth(id: Long) = undoable({ editor.keepBoth(id) }) { TransactionsMessage.KeptBoth(it) }

    fun unignore(id: Long) = undoable({ editor.unignore(id) }) { TransactionsMessage.BackInInbox(it) }

    fun delete(id: Long) = launch {
        val done = editor.deleteManual(id)
        if (done.changed > 0) editingId.update { null }
        TransactionsMessage.Deleted(done.undo).takeIf { done.changed > 0 }
    }

    fun reportWrongAmount(id: Long) = launch { TransactionsMessage.Reported.takeIf { editor.reportWrongAmount(id) } }

    /** Saves the edit sheet; it closes on success. */
    fun save(edit: TransactionEdit) = launch {
        when (val result = editor.save(edit)) {
            is EditResult.Saved -> {
                editingId.update { null }
                TransactionsMessage.Saved
            }

            else -> TransactionsMessage.Failed(result)
        }
    }

    fun add(manual: ManualTransaction, onAdded: () -> Unit) = launch {
        when (val result = editor.addManual(manual)) {
            is EditResult.Saved -> {
                onAdded()
                TransactionsMessage.Added
            }

            else -> TransactionsMessage.Failed(result)
        }
    }

    fun undo(undo: Undo) {
        viewModelScope.launch { editor.undo(undo) }
    }

    fun syncNow() = smsSync.reconcileNow()

    fun backfill(days: Int) = smsSync.backfill(clock.nowMillis() - days.days.inWholeMilliseconds)

    private fun undoable(action: suspend () -> Undoable, message: (Undo?) -> TransactionsMessage) = launch {
        val done = action()
        message(done.undo).takeIf { done.changed > 0 }
    }

    private fun launch(block: suspend () -> TransactionsMessage?) {
        viewModelScope.launch { block()?.let { messageChannel.send(it) } }
    }

    companion object {
        /** Drill-down arguments (navigation route fields): a category (0 = any) and a time range. */
        const val ARG_CATEGORY_ID = "categoryId"
        const val ARG_ACCOUNT_ID = "accountId"
        const val ARG_FROM_MILLIS = "fromMillis"
        const val ARG_TO_MILLIS = "toMillis"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
