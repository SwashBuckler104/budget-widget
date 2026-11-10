package io.github.swashbuckler104.budgetwidget.core.data.suggest

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.category.CategorySeeder
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

class SuggestionRankerTest {
    @Test
    fun `sources in priority order, eligible only, no repeats, filled to the limit`() {
        val ranked = SuggestionRanker.rank(
            sources = listOf(listOf(9L), listOf(3L, 9L), listOf(99L), listOf(4L)),
            eligible = listOf(1L, 2L, 3L, 4L, 9L),
            limit = 4,
        )
        assertThat(ranked).containsExactly(9L, 3L, 4L, 1L).inOrder()
    }

    @Test
    fun `fewer eligible leaves than the limit returns them all`() {
        assertThat(
            SuggestionRanker.rank(listOf(emptyList()), listOf(5L, 6L), limit = 6),
        ).containsExactly(5L, 6L).inOrder()
    }
}

@RunWith(AndroidJUnit4::class)
class SuggestionEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var db: BudgetDatabase
    private val now = 100.days.inWholeMilliseconds
    private lateinit var engine: SuggestionEngine
    private lateinit var sort: SortTransactionUseCase

    @Before
    fun setUp() {
        db = inMemoryDb()
        engine = SuggestionEngine(db) { now }
        sort = SortTransactionUseCase(db, { now }, InboxEvents(emptySet()))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(scope: kotlinx.coroutines.test.TestScope) = CategorySeeder(
        db,
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                tmp.root.resolve("s.preferences_pb")
            },
        ),
    ).ensureSeeded()

    private suspend fun id(name: String) = db.categoryDao().getAll().single { it.name == name }.id

    private suspend fun names(request: SuggestionRequest) = engine.suggest(request).map { it.name }

    @Test
    fun `fresh install falls back to seed defaults by direction`() = runTest {
        seed(this)
        val out = names(SuggestionRequest(null, Direction.OUT, 250_00, ParserType.EXPENSE))
        assertThat(out).containsExactly(
            "Groceries",
            "Restaurant, Fast-food",
            "Fuel",
            "Public transport",
            "Drug-store, Chemist",
            "Phone, Cell phone",
        ).inOrder()
        val incoming = names(SuggestionRequest(null, Direction.IN, 50_000_00, ParserType.INCOME))
        assertThat(incoming.take(4)).containsExactly("Salary", "Freelancing", "Self Transfer", "Other").inOrder()
    }

    @Test
    fun `transaction type hints come before amount-band and global usage`() = runTest {
        seed(this)
        // Both legs of a self transfer get "Self Transfer": the expense one going out, the income one coming in.
        val incoming = engine.suggest(SuggestionRequest(null, Direction.IN, 1_000_00, ParserType.TRANSFER))
        val outgoing = engine.suggest(SuggestionRequest(null, Direction.OUT, 1_000_00, ParserType.TRANSFER))
        assertThat(incoming.first().name).isEqualTo("Self Transfer")
        assertThat(
            incoming.first().kind,
        ).isEqualTo(io.github.swashbuckler104.budgetwidget.core.model.CategoryKind.INCOME)
        assertThat(outgoing.first().name).isEqualTo("Self Transfer")
        assertThat(
            outgoing.first().kind,
        ).isEqualTo(io.github.swashbuckler104.budgetwidget.core.model.CategoryKind.EXPENSE)
        assertThat(outgoing[1].name).isEqualTo("Savings (emergency fund)")
        val investment = names(SuggestionRequest(null, Direction.OUT, 5_000_00, ParserType.INVESTMENT))
        assertThat(investment.first()).isEqualTo("Financial investments (SIP, bonds, etc.)")
    }

    @Test
    fun `rule first, then merchant history, then recent amount band, then global`() = runTest {
        seed(this)
        val tx = db.transactionDao()
        suspend fun sorted(
            hash: String,
            key: String?,
            amount: Long,
            category: String,
            at: Long = now -
                1.days.inWholeMilliseconds,
            remember: Boolean = true,
        ) {
            val id = tx.insertOrIgnore(testTxn(hash, key = key, amount = amount, at = at))
            sort.categorize(id, id(category), rememberMerchant = remember)
        }
        sorted("g1", "fuel station", 5_000_00, "Fuel")
        sorted("g2", "fuel station", 5_000_00, "Fuel")
        sorted("g3", "fuel station", 5_000_00, "Fuel") // global winner: Fuel (3)
        sorted("b1", "cafe", 300_00, "Drinks, Alcohol") // in the ±50% band of 250
        sorted("b2", "old cafe", 300_00, "Pets, Animals", at = now - 90.days.inWholeMilliseconds) // band but too old
        sorted("h1", "zomato", 900_00, "Groceries", remember = false) // history for zomato
        sorted("r1", "zomato", 900_00, "Restaurant, Fast-food") // rule for zomato

        val ranked = names(SuggestionRequest("zomato", Direction.OUT, 250_00, ParserType.EXPENSE))
        assertThat(
            ranked.take(4),
        ).containsExactly("Restaurant, Fast-food", "Groceries", "Drinks, Alcohol", "Fuel").inOrder()
        assertThat(ranked).hasSize(6)
    }

    @Test
    fun `archived and non-leaf categories and wrong-kind categories are never suggested`() = runTest {
        seed(this)
        db.categoryDao().setArchivedWithChildren(id("Food & Drinks"), true)
        val out = engine.suggest(SuggestionRequest(null, Direction.OUT, 100_00, null))
        assertThat(
            out.map {
                it.name
            },
        ).containsNoneOf("Groceries", "Restaurant, Fast-food", "Drinks, Alcohol", "Food & Drinks", "Salary")
        assertThat(out).hasSize(6)
        val income = engine.suggest(SuggestionRequest(null, Direction.IN, 100_00, null))
        assertThat(
            income.map {
                it.kind
            },
        ).doesNotContain(io.github.swashbuckler104.budgetwidget.core.model.CategoryKind.EXPENSE)
    }
}
