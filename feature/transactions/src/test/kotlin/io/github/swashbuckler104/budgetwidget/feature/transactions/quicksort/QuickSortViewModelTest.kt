package io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionEngine
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

/** Real Room, repositories, suggestions and sorting; only time is fixed. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class QuickSortViewModelTest {
    private lateinit var db: BudgetDatabase
    private val viewModels = mutableListOf<QuickSortViewModel>()

    // Local noon, so "a minute ago" and "now" share a local date in every time zone (CI runs in UTC).
    private val now = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            ).build()
    }

    @After
    fun tearDown() {
        // Stop the ViewModels' database flows before closing the database (else connections leak).
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(transactionId: Long? = null) = newViewModel(transactionId).also { viewModels += it }

    private fun newViewModel(transactionId: Long?) = QuickSortViewModel(
        SavedStateHandle(transactionId?.let { mapOf(QuickSortViewModel.ARG_TRANSACTION_ID to it) }.orEmpty()),
        TransactionRepository(db.transactionDao(), db.unparsedSmsDao()),
        CategoryRepository(db),
        SuggestionEngine(db) { now },
        SortTransactionUseCase(db) { now },
    ) { now }

    private suspend fun QuickSortViewModel.await(predicate: (QuickSortUiState) -> Boolean) = withTimeout(TIMEOUT_MS) {
        state.first {
            !it.loading &&
                predicate(it)
        }
    }

    @Test
    fun `walks the inbox oldest first, learns rules and ends all sorted`() = runBlocking {
        val food = category("Food")
        val older = txn("older", at = now - 2 * DAY)
        val newer = txn("newer", at = now - 60_000)
        val vm = viewModel()

        val first = vm.await { it.transaction != null }
        assertThat(first.transaction!!.id).isEqualTo(older)
        assertThat(first.remaining).isEqualTo(2)
        assertThat(first.overdue).isTrue()
        assertThat(first.suggestions.map { it.id }).containsExactly(food)

        vm.choose(first.suggestions.single(), note = "weekly", remember = true)
        val second = vm.await { it.transaction?.id == newer }
        assertThat(second.overdue).isFalse()
        assertThat(db.transactionDao().get(older)!!.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(db.transactionDao().get(older)!!.note).isEqualTo("weekly")
        assertThat(db.merchantRuleDao().get("shop")!!.categoryId).isEqualTo(food)

        vm.ignore()
        val done = vm.await { it.finished }
        assertThat(done.remaining).isEqualTo(0)
        assertThat(db.transactionDao().get(newer)!!.status).isEqualTo(TransactionStatus.IGNORED)
    }

    @Test
    fun `skip moves the item to the back of the inbox`() = runBlocking {
        category("Food")
        val a = txn("a", at = 1)
        val b = txn("b", at = 2)
        val vm = viewModel()
        assertThat(vm.await { it.transaction != null }.transaction!!.id).isEqualTo(a)
        vm.skip()
        assertThat(vm.await { it.transaction?.id == b }.remaining).isEqualTo(2)
        vm.skip()
        assertThat(vm.await { it.transaction?.id == a }.transaction!!.id).isEqualTo(a)
    }

    @Test
    fun `opened for one transaction, sorting it finishes`() = runBlocking {
        val food = category("Food")
        txn("other", at = 1)
        val target = txn("target", at = 2)
        val vm = viewModel(transactionId = target)
        val shown = vm.await { it.transaction != null }
        assertThat(shown.transaction!!.id).isEqualTo(target)
        vm.choose(shown.suggestions.first { it.id == food }, note = "", remember = false)
        vm.await { it.finished }
        assertThat(db.transactionDao().get(target)!!.categoryId).isEqualTo(food)
        assertThat(db.merchantRuleDao().get("shop")).isNull()
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

    private suspend fun txn(hash: String, at: Long) = db.transactionDao().insertOrIgnore(
        TransactionEntity(
            txnHash = hash,
            source = TransactionSource.SMS,
            amountPaise = 250_00,
            direction = Direction.OUT,
            parserType = null,
            merchantRaw = "Shop",
            merchantDisplay = "Shop",
            merchantKey = "shop",
            accountId = null,
            occurredAt = at,
            smsSender = null,
            smsBody = null,
            reference = null,
            balanceAfterPaise = null,
            isCard = false,
            status = TransactionStatus.PENDING,
            isBackfill = false,
            createdAt = 0,
            updatedAt = 0,
        ),
    )

    private companion object {
        const val DAY = 86_400_000L
        const val TIMEOUT_MS = 10_000L
    }
}
