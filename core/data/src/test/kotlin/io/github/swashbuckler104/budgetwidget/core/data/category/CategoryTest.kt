package io.github.swashbuckler104.budgetwidget.core.data.category

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var db: BudgetDatabase
    private lateinit var repo: CategoryRepository

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = CategoryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun seeder(scope: TestScope) = CategorySeeder(
        db,
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                tmp.root.resolve("s.preferences_pb")
            },
        ),
    )

    @Test
    fun `seeding inserts the scope set once and never brings back deleted seeds`() = runTest {
        val seeder = seeder(this)
        seeder.ensureSeeded()
        val groups = repo.observeGroups().first()
        assertThat(groups.map { it.category.name }).containsExactly(
            "Food & Drinks", "Shopping", "Housing", "Transportation", "Vehicle", "Life & Entertainment",
            "Communication, PC", "Financial Expenses", "Investments", "Income",
        ).inOrder()
        val food = groups.first().children.map { it.name }
        assertThat(food).containsExactly("Groceries", "Restaurant, Fast-food", "Drinks, Alcohol").inOrder()
        val income = groups.single { it.category.name == "Income" }.children.map { it.name }
        assertThat(income).containsExactly("Salary", "Freelancing", "Other", "Self Transfer").inOrder()
        val financial = groups.single { it.category.name == "Financial Expenses" }.children.map { it.name }
        assertThat(financial.last()).isEqualTo("Self Transfer")
        assertThat(groups.sumOf { it.children.size }).isEqualTo(44)
        assertThat(
            groups.flatMap { g ->
                g.children.map { it.name.length }
            }.max(),
        ).isAtMost(CategoryRepository.MAX_NAME)
        assertThat(groups.single { it.category.name == "Income" }.category.kind).isEqualTo(CategoryKind.INCOME)
        assertThat(groups.single { it.category.name == "Investments" }.category.kind).isEqualTo(CategoryKind.TRANSFER)
        assertThat(groups.flatMap { g -> g.children.map { it.colorArgb == g.category.colorArgb } }.all { it }).isTrue()

        val housing = groups.single { it.category.name == "Housing" }.category.id
        assertThat(repo.delete(housing)).isEqualTo(DeleteResult.Deleted)
        seeder.ensureSeeded()
        assertThat(repo.observeGroups().first().none { it.category.name == "Housing" }).isTrue()
    }

    @Test
    fun `create validates names, depth and inherits kind and color from the parent`() = runTest {
        val draft = CategoryDraft("Pets", "🐶", 0xFF112233.toInt(), CategoryKind.EXPENSE)
        val pets = (repo.create(null, draft) as CategoryResult.Saved).id
        assertThat(repo.create(null, draft.copy(name = " pets "))).isEqualTo(CategoryResult.DuplicateName)
        assertThat(repo.create(null, draft.copy(name = "  "))).isEqualTo(CategoryResult.BlankName)
        assertThat(repo.create(null, draft.copy(name = "x".repeat(41)))).isEqualTo(CategoryResult.NameTooLong)
        assertThat(repo.create(null, draft.copy(name = "Plants", emoji = " "))).isEqualTo(CategoryResult.BlankEmoji)

        val food = (repo.create(pets, CategoryDraft("Food", "🦴", 0, CategoryKind.INCOME)) as CategoryResult.Saved).id
        val child = repo.observeGroups().first().single().children.single()
        assertThat(child.id).isEqualTo(food)
        assertThat(child.kind).isEqualTo(CategoryKind.EXPENSE)
        assertThat(child.colorArgb).isEqualTo(0xFF112233.toInt())
        assertThat(repo.create(food, draft.copy(name = "Kibble"))).isEqualTo(CategoryResult.TooDeep)

        repo.update(pets, draft.copy(colorArgb = 0xFF445566.toInt()))
        assertThat(repo.observeGroups().first().single().children.single().colorArgb).isEqualTo(0xFF445566.toInt())
    }

    @Test
    fun `reorder, archive and delete rules`() = runTest {
        val a = (repo.create(null, CategoryDraft("A", "🅰️", 0, CategoryKind.EXPENSE)) as CategoryResult.Saved).id
        val b = (repo.create(null, CategoryDraft("B", "🅱️", 0, CategoryKind.EXPENSE)) as CategoryResult.Saved).id
        val b1 = (repo.create(b, CategoryDraft("B1", "1️⃣", 0, CategoryKind.EXPENSE)) as CategoryResult.Saved).id
        repo.reorder(listOf(b, a))
        assertThat(repo.observeGroups().first().map { it.category.id }).containsExactly(b, a).inOrder()

        repo.setArchived(b, true)
        assertThat(repo.observeGroups().first().first().pickableLeaves).isEmpty()

        val used = db.transactionDao().insertOrIgnore(testTxn("t"))
        db.transactionDao().categorize(used, b1, null, false, 1)
        assertThat(repo.delete(b)).isEqualTo(DeleteResult.InUse(1))
        assertThat(repo.delete(a)).isEqualTo(DeleteResult.Deleted)
        assertThat(repo.observeGroups().first().map { it.category.id }).containsExactly(b)
    }
}
