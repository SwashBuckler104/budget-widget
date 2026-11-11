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

    private fun settings(scope: TestScope) = SettingsStore(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            tmp.root.resolve("s.preferences_pb")
        },
    )

    private fun seeder(scope: TestScope) = CategorySeeder(db, settings(scope))

    @Test
    fun `seeding inserts the scope set once and never brings back deleted seeds`() = runTest {
        val seeder = seeder(this)
        seeder.ensureSeeded()
        val groups = repo.observeGroups().first()
        assertThat(groups.map { it.category.name }).containsExactly(
            "Food & Drinks", "Shopping", "Housing", "Transportation", "Vehicle", "Life & Entertainment",
            "Communication, PC", "Financial Expenses", "Family", "Investments", "Income",
        ).inOrder()
        val food = groups.first().children.map { it.name }
        assertThat(food).containsExactly("Groceries", "Restaurant, Fast-food", "Drinks, Alcohol").inOrder()
        val income = groups.single { it.category.name == "Income" }.children.map { it.name }
        assertThat(income).containsExactly("Salary", "Freelancing", "Other", "Self Transfer").inOrder()
        val financial = groups.single { it.category.name == "Financial Expenses" }.children.map { it.name }
        assertThat(financial.last()).isEqualTo("Self Transfer")
        assertThat(groups.sumOf { it.children.size }).isEqualTo(47)
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
    fun `upgrading adds only new groups and adopts the user's own same-named category`() = runTest {
        val settings = settings(this)
        val seeder = CategorySeeder(db, settings)
        seeder.ensureSeeded()
        // Turn the database into a version 1 install: no Family seed, Housing deleted by the user.
        val before = repo.observeGroups().first()
        repo.delete(before.single { it.category.name == "Family" }.category.id)
        repo.delete(before.single { it.category.name == "Housing" }.category.id)
        settings.setSeededCategoriesVersion(1)
        // The user made their own Family, with one subcategory the seed doesn't have.
        val color = 0xFFE07CFF.toInt()
        val family = (
            repo.create(
                null,
                CategoryDraft("family", "🏠", color, CategoryKind.EXPENSE),
            ) as CategoryResult.Saved
            ).id
        val lend = (
            repo.create(
                family,
                CategoryDraft("Transfer, Lend", "💸", 0, CategoryKind.EXPENSE),
            ) as CategoryResult.Saved
            ).id
        repo.create(family, CategoryDraft("Pocket money", "👛", 0, CategoryKind.EXPENSE))
        repo.create(family, CategoryDraft("d-mart, kirana, groceries", "🛒", 0, CategoryKind.EXPENSE))

        seeder.ensureSeeded()

        val groups = repo.observeGroups().first()
        assertThat(groups.none { it.category.name == "Housing" }).isTrue()
        val adopted = groups.single { it.category.name.equals("Family", ignoreCase = true) }
        assertThat(adopted.category.id).isEqualTo(family)
        assertThat(adopted.category.name).isEqualTo("family")
        assertThat(adopted.category.emoji).isEqualTo("🏠")
        assertThat(adopted.category.colorArgb).isEqualTo(color)
        assertThat(adopted.category.isSeed).isTrue()
        assertThat(adopted.children.map { it.name })
            .containsExactly("Transfer, Lend", "Pocket money", "d-mart, kirana, groceries", "Other").inOrder()
        assertThat(adopted.children.first().id).isEqualTo(lend)
        val keys = db.categoryDao().getAll().associate { it.name to it.seedKey }
        assertThat(keys["family"]).isEqualTo("family")
        assertThat(keys["Transfer, Lend"]).isEqualTo("family_transfer_lend")
        assertThat(keys["d-mart, kirana, groceries"]).isEqualTo("family_groceries")
        assertThat(keys["Pocket money"]).isNull()
        assertThat(db.categoryDao().getAll().count { it.seedKey == "family_other" }).isEqualTo(1)
        assertThat(settings.seededCategoriesVersion()).isEqualTo(SeedCategories.VERSION)
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
