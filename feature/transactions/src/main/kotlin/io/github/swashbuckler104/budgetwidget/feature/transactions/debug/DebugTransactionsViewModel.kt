package io.github.swashbuckler104.budgetwidget.feature.transactions.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DebugTransactionsState(
    val loading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val unparsedCount: Int = 0,
    val categories: Map<Long, Category> = emptyMap(),
) {
    val pendingCount: Int get() = transactions.count { it.status == TransactionStatus.PENDING }
}

@HiltViewModel
class DebugTransactionsViewModel @Inject constructor(
    repository: TransactionRepository,
    categories: CategoryRepository,
    private val smsSync: SmsSync,
    private val clock: WallClock,
) : ViewModel() {
    val state: StateFlow<DebugTransactionsState> = combine(
        repository.observeTransactions(),
        repository.observeUnparsedCount(),
    ) { transactions, unparsed ->
        DebugTransactionsState(loading = false, transactions = transactions, unparsedCount = unparsed)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DebugTransactionsState())

    fun syncNow() = smsSync.reconcileNow()

    fun backfill(days: Int) = smsSync.backfill(clock.nowMillis() - days.days.inWholeMilliseconds)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
