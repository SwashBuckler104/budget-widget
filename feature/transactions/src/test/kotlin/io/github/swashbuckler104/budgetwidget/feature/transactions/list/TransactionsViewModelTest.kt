package io.github.swashbuckler104.budgetwidget.feature.transactions.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEdit
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEditor
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room, repositories and editor; only time and SMS sync are fixed. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TransactionsViewModelTest {
    private lateinit var db: BudgetDatabase
    private val viewModels = mutableListOf<TransactionsViewModel>()
    private val now = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            )
                .build()
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(args: Map<String, Any> = emptyMap()): TransactionsViewModel {
        val sort = SortTransactionUseCase(db, { now }, InboxEvents(emptySet()))
        return TransactionsViewModel(
            SavedStateHandle(args),
            TransactionRepository(db.transactionDao(), db.unparsedSmsDao(), db.accountDao()),
            CategoryRepository(db),
            TransactionEditor(db, sort, InboxEvents(emptySet())) { now },
            NoSync,
        ) { now }.also { viewModels += it }
    }

    private suspend fun TransactionsViewModel.await(predicate: (TransactionsUiState) -> Boolean) =
        withTimeout(TIMEOUT_MS) { state.first { !it.loading && predicate(it) } }

    @Test
    fun `pending items are pinned oldest first with overdue and duplicate info, sorted ones grouped by day`() =
        runBlocking {
            val food = category("Food")
            val old = txn("old", at = now - 2 * DAY)
            val original = txn("orig", at = now - 60_000)
            val dup = txn("dup", at = now - 30_000)
            db.transactionDao().setDuplicateOf(dup, original)
            val sorted = txn("sorted", at = now - 3 * DAY, status = TransactionStatus.CATEGORIZED, category = food)
            val vm = viewModel()

            val state = vm.await { it.inbox.size == 3 }
            assertThat(state.inbox.map { it.transaction.id }).containsExactly(old, original, dup).inOrder()
            assertThat(state.inbox.first().overdue).isTrue()
            assertThat(state.inbox.last().duplicateOf?.id).isEqualTo(original)
            assertThat(state.days.flatMap { it.transactions }.map { it.id }).containsExactly(sorted)
            assertThat(state.days.single().spentPaise).isEqualTo(100_00)
        }

    @Test
    fun `a filter shows matching pending ones in the list and hides the inbox`() = runBlocking {
        txn("a", at = now - 1, merchant = "Swiggy")
        txn("b", at = now - 2, merchant = "Uber")
        val vm = viewModel()
        vm.await { it.inbox.size == 2 }

        vm.setFilter { it.copy(query = "swig") }
        val filtered = vm.await { it.filter.query == "swig" }
        assertThat(filtered.inbox).isEmpty()
        assertThat(filtered.days.flatMap { it.transactions }.map { it.merchantDisplay }).containsExactly("Swiggy")

        vm.clearFilters()
        assertThat(vm.await { true }.filter.query).isEqualTo("swig")
    }

    @Test
    fun `drill-down opens with a fixed category and range`() = runBlocking {
        val food = category("Food")
        txn("in", at = now - DAY, status = TransactionStatus.CATEGORIZED, category = food)
        txn("old", at = now - 40 * DAY, status = TransactionStatus.CATEGORIZED, category = food)
        val vm = viewModel(
            mapOf(
                TransactionsViewModel.ARG_CATEGORY_ID to food,
                TransactionsViewModel.ARG_FROM_MILLIS to now - 30 * DAY,
                TransactionsViewModel.ARG_TO_MILLIS to now,
            ),
        )
        val state = vm.await { it.days.isNotEmpty() }
        assertThat(state.drillDown).isTrue()
        assertThat(state.days.flatMap { it.transactions }).hasSize(1)
    }

    @Test
    fun `bulk ignore clears the selection and undo brings the items back`() = runBlocking {
        val a = txn("a", at = now - 1)
        val b = txn("b", at = now - 2)
        val vm = viewModel()
        vm.await { it.inbox.size == 2 }
        vm.toggleSelection(a)
        vm.toggleSelection(b)
        assertThat(vm.await { it.selection.size == 2 }.selecting).isTrue()

        vm.ignore(vm.state.value.selection)
        val message = withTimeout(TIMEOUT_MS) { vm.messages.first() }
        assertThat(message).isInstanceOf(TransactionsMessage.Ignored::class.java)
        assertThat((message as TransactionsMessage.Ignored).count).isEqualTo(2)
        val after = vm.await { it.inbox.isEmpty() }
        assertThat(after.selection).isEmpty()

        vm.undo(message.undo!!)
        vm.await { it.inbox.size == 2 }
        assertThat(db.transactionDao().get(a)!!.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `the editor follows the open transaction and closes after saving`() = runBlocking {
        val food = category("Food")
        val a = txn("a", at = now - 1)
        val vm = viewModel()
        vm.openEditor(a)
        val detail = withTimeout(TIMEOUT_MS) { vm.editing.first { it != null } }!!
        assertThat(detail.smsBody).isEqualTo("body a")

        vm.save(
            TransactionEdit(
                id = a,
                direction = Direction.OUT,
                categoryId = food,
                note = null,
                merchantName = "Shop",
            ),
        )
        assertThat(withTimeout(TIMEOUT_MS) { vm.messages.first() }).isEqualTo(TransactionsMessage.Saved)
        withTimeout(TIMEOUT_MS) { vm.editing.first { it == null } }
        assertThat(db.transactionDao().get(a)!!.categoryId).isEqualTo(food)
    }

    private suspend fun category(name: String) = db.categoryDao().insert(
        CategoryEntity(
            parentId = null,
            name = name,
            iconKey = "🍽️",
            colorArgb = 0,
            kind = CategoryKind.EXPENSE,
            sortOrder = 0,
        ),
    )

    private suspend fun txn(
        hash: String,
        at: Long,
        merchant: String = "Shop",
        status: TransactionStatus = TransactionStatus.PENDING,
        category: Long? = null,
    ) = db.transactionDao().insertOrIgnore(
        TransactionEntity(
            txnHash = hash,
            source = TransactionSource.SMS,
            amountPaise = 100_00,
            direction = Direction.OUT,
            parserType = null,
            merchantRaw = merchant,
            merchantDisplay = merchant,
            merchantKey = merchant.lowercase(),
            accountId = null,
            occurredAt = at,
            smsSender = "AD-HDFCBK",
            smsBody = "body $hash",
            reference = null,
            balanceAfterPaise = null,
            isCard = false,
            status = status,
            categoryId = category,
            isBackfill = false,
            createdAt = at,
            updatedAt = at,
        ),
    )

    private object NoSync : SmsSync {
        override fun schedulePeriodicReconcile() = Unit

        override fun reconcileNow() = Unit

        override fun backfill(sinceMillis: Long) = Unit
    }

    private companion object {
        const val DAY = 86_400_000L
        const val TIMEOUT_MS = 5_000L
    }
}
