package io.github.swashbuckler104.budgetwidget.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    private lateinit var db: BudgetDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `parents come first and siblings follow sortOrder`() = runTest {
        val dao = db.categoryDao()
        val food = dao.insert(cat("Food", sort = 1))
        val bills = dao.insert(cat("Bills", sort = 0))
        dao.insert(cat("Groceries", parent = food, sort = 1))
        dao.insert(cat("Restaurants", parent = food, sort = 0))
        assertThat(dao.getAll().map { it.name }).containsExactly("Bills", "Food", "Restaurants", "Groceries").inOrder()

        dao.reorder(listOf(food, bills))
        assertThat(
            dao.getAll().filter {
                it.parentId == null
            }.map { it.name },
        ).containsExactly("Food", "Bills").inOrder()
        assertThat(dao.maxSortOrder(null)).isEqualTo(1)
        assertThat(dao.maxSortOrder(food)).isEqualTo(1)
    }

    @Test
    fun `sibling names are matched ignoring case`() = runTest {
        val dao = db.categoryDao()
        val food = dao.insert(cat("Food"))
        val groceries = dao.insert(cat("Groceries", parent = food))
        assertThat(dao.findSibling(food, "groceries", excludeId = 0)?.id).isEqualTo(groceries)
        assertThat(dao.findSibling(food, "groceries", excludeId = groceries)).isNull()
        assertThat(dao.findSibling(null, "groceries", excludeId = 0)).isNull()
    }

    @Test
    fun `archiving a category archives its subcategories`() = runTest {
        val dao = db.categoryDao()
        val food = dao.insert(cat("Food"))
        dao.insert(cat("Groceries", parent = food))
        dao.setArchivedWithChildren(food, true)
        assertThat(dao.getAll().all { it.archived }).isTrue()
        dao.setArchivedWithChildren(food, false)
        assertThat(dao.getAll().none { it.archived }).isTrue()
    }

    @Test
    fun `categorize, ignore and usage queries`() = runTest {
        val dao = db.categoryDao()
        val food = dao.insert(cat("Food"))
        val fuel = dao.insert(cat("Fuel"))
        val tx = db.transactionDao()
        val a = tx.insertOrIgnore(txn("a", key = "swiggy", amount = 400_00, at = 1_000))
        val b = tx.insertOrIgnore(txn("b", key = "swiggy", amount = 500_00, at = 2_000))
        val c = tx.insertOrIgnore(txn("c", key = "hp petrol", amount = 2_000_00, at = 3_000))
        val d = tx.insertOrIgnore(txn("d", key = "swiggy", amount = 450_00, at = 4_000))

        tx.categorize(a, food, note = "lunch", auto = false, now = 10)
        tx.categorize(b, food, note = null, auto = false, now = 20)
        tx.categorize(c, fuel, note = null, auto = false, now = 30)
        tx.ignore(d, IgnoreReason.USER, now = 40)

        assertThat(tx.observePending().first()).isEmpty()
        assertThat(tx.get(a)!!.note).isEqualTo("lunch")
        assertThat(tx.get(d)!!.status).isEqualTo(TransactionStatus.IGNORED)
        assertThat(tx.usageForMerchant("swiggy").map { it.categoryId to it.uses }).containsExactly(food to 2)
        assertThat(
            tx.usageInAmountBand(Direction.OUT, 300_00, 700_00, sinceMillis = 1_500).map {
                it.categoryId
            },
        ).containsExactly(food)
        assertThat(tx.usageForDirection(Direction.OUT).map { it.categoryId }).containsExactly(food, fuel).inOrder()
        assertThat(tx.usageForDirection(Direction.IN)).isEmpty()
        assertThat(dao.countTransactionsUsing(listOf(food))).isEqualTo(2)
    }

    @Test
    fun `pending inbox is oldest first`() = runTest {
        val tx = db.transactionDao()
        tx.insertOrIgnore(txn("new", at = 2_000))
        tx.insertOrIgnore(txn("old", at = 1_000))
        assertThat(tx.observePending().first().map { it.transaction.txnHash }).containsExactly("old", "new").inOrder()
    }

    @Test
    fun `merchant rules upsert, move, count and cascade with their category`() = runTest {
        val dao = db.categoryDao()
        val rules = db.merchantRuleDao()
        val food = dao.insert(cat("Food"))
        val funCategory = dao.insert(cat("Fun"))
        rules.upsert(MerchantRuleEntity("swiggy", food, 1, 1))
        rules.upsert(MerchantRuleEntity("swiggy", food, 2, 2))
        rules.upsert(MerchantRuleEntity("pvr", food, 1, 3))
        assertThat(rules.get("swiggy")!!.confirmCount).isEqualTo(2)
        rules.move("pvr", funCategory)
        assertThat(rules.observeForCategory(funCategory).first().map { it.merchantKey }).containsExactly("pvr")
        assertThat(
            rules.observeCountsByCategory().first().associate {
                it.categoryId to it.rules
            },
        ).containsExactly(food, 1, funCategory, 1)
        dao.deleteIds(listOf(funCategory))
        assertThat(rules.get("pvr")).isNull()
    }

    private fun cat(name: String, parent: Long? = null, sort: Int = 0) = CategoryEntity(
        parentId = parent,
        name = name,
        iconKey = "🙂",
        colorArgb = 0,
        kind = CategoryKind.EXPENSE,
        sortOrder = sort,
    )

    private fun txn(hash: String, key: String? = null, amount: Long = 100_00, at: Long = 0) = TransactionEntity(
        txnHash = hash,
        source = TransactionSource.SMS,
        amountPaise = amount,
        direction = Direction.OUT,
        parserType = null,
        merchantRaw = null,
        merchantDisplay = null,
        merchantKey = key,
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
    )
}
