package io.github.swashbuckler104.budgetwidget.feature.transactions.edit

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.edit.ManualTransaction
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEditor
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import io.github.swashbuckler104.budgetwidget.feature.transactions.NoOpParser
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room, repositories and editor. Fake SMS in the style of docs/handoff/probe-sms.tsv. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddFromSmsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: BudgetDatabase
    private val viewModels = mutableListOf<AddFromSmsViewModel>()
    private val now = 1_794_000_000_000L
    private val body = "Rs.1,240.00 credited to your Acct XXXXXX81 by NEFT. Ref XXXXXXXXXX65"

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

    private fun viewModel(): AddFromSmsViewModel {
        val sort = SortTransactionUseCase(db, { now }, InboxEvents(emptySet()))
        return AddFromSmsViewModel(
            TransactionRepository(db.transactionDao(), db.unparsedSmsDao(), db.accountDao()),
            CategoryRepository(db),
            TransactionEditor(db, sort, InboxEvents(emptySet()), NoOpParser) { now },
        ) { now }.also { viewModels += it }
    }

    private suspend fun unparsed() = db.unparsedSmsDao().insertOrIgnore(
        UnparsedSmsEntity(
            sender = "JD-NEWBNK-S",
            bodyHash = "h1",
            body = body,
            receivedAt = now - 60_000,
            reason = UnparsedReason.UNKNOWN_SENDER,
        ),
    )

    private suspend fun salary() = db.categoryDao().insert(
        CategoryEntity(
            parentId = null,
            name = "Salary",
            iconKey = "💼",
            colorArgb = 0,
            kind = CategoryKind.INCOME,
            sortOrder = 0,
        ),
    )

    @Test
    fun `the form starts from the SMS, and saving moves it off the unparsed list`() = runBlocking {
        val id = unparsed()
        val salary = salary()
        val viewModel = viewModel()
        viewModel.load(id)
        val state = withTimeout(TIMEOUT_MS) {
            viewModel.state.first { it.loaded && it.categories.containsKey(salary) }
        }
        val prefill = state.prefill!!
        assertThat(prefill.amountPaise).isEqualTo(1_240_00)
        assertThat(prefill.direction).isEqualTo(Direction.IN)
        assertThat(prefill.occurredAt).isEqualTo(now - 60_000)

        val done = CompletableDeferred<Boolean>()
        viewModel.add(
            ManualTransaction(
                1_240_00,
                Direction.IN,
                salary,
                null,
                null,
                prefill.occurredAt,
                smsSender = prefill.smsSender,
                smsBody = prefill.smsBody,
            ),
        ) { done.complete(it) }
        assertThat(withTimeout(TIMEOUT_MS) { done.await() }).isTrue()

        val stored = db.transactionDao().getMany(listOf(1)).single()
        assertThat(stored.source).isEqualTo(TransactionSource.MANUAL)
        assertThat(stored.smsSender).isEqualTo("JD-NEWBNK-S")
        assertThat(stored.smsBody).isEqualTo(body)
        assertThat(db.unparsedSmsDao().get(id)).isNull()
    }

    @Test
    fun `an entry that is gone gives no form`() = runBlocking {
        val viewModel = viewModel()
        viewModel.load(42)
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it.loaded } }
        assertThat(state.prefill).isNull()
    }

    @Test
    fun `the prefilled sheet shows the SMS, its amount and money in`() {
        compose.setContent {
            AddTransactionContent(
                groups = emptyList(),
                categories = emptyMap(),
                nowMillis = now,
                onAdd = {},
                prefill = AddPrefill(1, 1_240_00, Direction.IN, now, "JD-NEWBNK-S", body),
            )
        }
        compose.onNodeWithText("Add transaction from SMS").assertExists()
        compose.onNodeWithText("JD-NEWBNK-S").assertExists()
        compose.onNodeWithText(body).assertExists()
        compose.onNodeWithText("1,240").assertExists()
        compose.onNodeWithText("Income").assertIsSelected()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
