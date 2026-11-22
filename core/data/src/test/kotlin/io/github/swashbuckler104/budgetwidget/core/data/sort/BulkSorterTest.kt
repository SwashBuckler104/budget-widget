package io.github.swashbuckler104.budgetwidget.core.data.sort

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.inbox.RecordingInboxListener
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BulkSorterTest {
    private lateinit var db: BudgetDatabase
    private lateinit var bulk: BulkSorter
    private val listener = RecordingInboxListener()
    private var other = 0L
    private var otherIncome = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        val events = InboxEvents(setOf(listener))
        bulk = BulkSorter(db, SortTransactionUseCase(db, { 5_000L }, events), events)
        other = cat("Other", "other", CategoryKind.EXPENSE)
        val income = cat("Income", "income", CategoryKind.INCOME)
        otherIncome = cat("Other", "other_income", CategoryKind.INCOME, parent = income)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cat(name: String, key: String, kind: CategoryKind, parent: Long? = null) =
        db.categoryDao().insert(
            CategoryEntity(
                parentId = parent,
                name = name,
                iconKey = "📦",
                colorArgb = 0,
                kind = kind,
                sortOrder = 0,
                isSeed = true,
                seedKey = key,
            ),
        )

    @Test
    fun `files payments under Other and money in under Income Other, without learning rules`() = runTest {
        val tx = db.transactionDao()
        val out = tx.insertOrIgnore(testTxn("a", key = "swiggy"))
        val inc = tx.insertOrIgnore(testTxn("b", key = "acme", direction = Direction.IN))

        assertThat(bulk.fileUnderOther(onlyBackfill = false)).isEqualTo(2)

        assertThat(tx.get(out)!!.categoryId).isEqualTo(other)
        assertThat(tx.get(inc)!!.categoryId).isEqualTo(otherIncome)
        assertThat(tx.get(out)!!.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(tx.get(out)!!.autoCategorized).isFalse()
        assertThat(db.merchantRuleDao().get("swiggy")).isNull()
        assertThat(listener.events).containsExactly("sorted:$out", "sorted:$inc")
    }

    @Test
    fun `only backfill leaves live payments, duplicates and sorted ones alone`() = runTest {
        val tx = db.transactionDao()
        val imported = tx.insertOrIgnore(testTxn("a").copy(isBackfill = true))
        val live = tx.insertOrIgnore(testTxn("b"))
        val duplicate = tx.insertOrIgnore(testTxn("c").copy(isBackfill = true))
        tx.setDuplicateOf(duplicate, imported)
        val ignored = tx.insertOrIgnore(testTxn("d").copy(isBackfill = true, status = TransactionStatus.IGNORED))

        assertThat(bulk.fileUnderOther(onlyBackfill = true)).isEqualTo(1)

        assertThat(tx.get(imported)!!.categoryId).isEqualTo(other)
        assertThat(tx.get(live)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(duplicate)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(ignored)!!.status).isEqualTo(TransactionStatus.IGNORED)
    }

    @Test
    fun `a direction whose category is gone or no longer a leaf stays in the inbox`() = runTest {
        val tx = db.transactionDao()
        db.categoryDao().deleteIds(listOf(otherIncome))
        cat("Fuel", "fuel_sub", CategoryKind.EXPENSE, parent = other)
        val out = tx.insertOrIgnore(testTxn("a"))
        val inc = tx.insertOrIgnore(testTxn("b", direction = Direction.IN))

        assertThat(bulk.fileUnderOther(onlyBackfill = false)).isEqualTo(0)

        assertThat(tx.get(out)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(inc)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(listener.events).isEmpty()
    }
}
