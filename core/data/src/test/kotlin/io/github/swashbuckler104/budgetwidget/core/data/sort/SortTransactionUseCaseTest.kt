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
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SortTransactionUseCaseTest {
    private lateinit var db: BudgetDatabase
    private lateinit var sort: SortTransactionUseCase
    private var now = 1_000L
    private val listener = RecordingInboxListener()

    @Before
    fun setUp() {
        db = inMemoryDb()
        sort = SortTransactionUseCase(db, { now }, InboxEvents(setOf(listener)))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cat(name: String, parent: Long? = null, archived: Boolean = false) = db.categoryDao().insert(
        CategoryEntity(
            parentId = parent,
            name = name,
            iconKey = "🙂",
            colorArgb = 0,
            kind = CategoryKind.EXPENSE,
            sortOrder = 0,
            archived = archived,
        ),
    )

    @Test
    fun `categorize stores leaf, note and learns the merchant rule`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        val tx = db.transactionDao()
        val a = tx.insertOrIgnore(testTxn("a", key = "swiggy"))
        val b = tx.insertOrIgnore(testTxn("b", key = "swiggy"))
        val c = tx.insertOrIgnore(testTxn("c", key = "swiggy"))

        assertThat(sort.categorize(a, food, note = "  lunch  ")).isEqualTo(SortResult.Done)
        with(tx.get(a)!!) {
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(categoryId).isEqualTo(food)
            assertThat(note).isEqualTo("lunch")
            assertThat(updatedAt).isEqualTo(1_000)
        }
        assertThat(db.merchantRuleDao().get("swiggy")!!.confirmCount).isEqualTo(1)
        now = 2_000
        sort.categorize(b, food)
        assertThat(db.merchantRuleDao().get("swiggy")!!.confirmCount).isEqualTo(2)
        sort.categorize(c, fuel)
        with(db.merchantRuleDao().get("swiggy")!!) {
            assertThat(categoryId).isEqualTo(fuel)
            assertThat(confirmCount).isEqualTo(1)
            assertThat(lastUsedAt).isEqualTo(2_000)
        }
    }

    @Test
    fun `always sorts the merchant's earlier payments that fit and keeps their notes`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        val tx = db.transactionDao()
        val waiting = tx.insertOrIgnore(testTxn("waiting", key = "swiggy"))
        val elsewhere = tx.insertOrIgnore(testTxn("elsewhere", key = "swiggy"))
        sort.categorize(elsewhere, fuel, note = "late night")
        val ignored = tx.insertOrIgnore(testTxn("ignored", key = "swiggy"))
        sort.ignore(ignored)
        val duplicate = tx.insertOrIgnore(testTxn("dup", key = "swiggy"))
        tx.setDuplicateOf(duplicate, waiting)
        val refund = tx.insertOrIgnore(testTxn("refund", key = "swiggy", direction = Direction.IN))
        val leg = tx.insertOrIgnore(testTxn("leg", key = "swiggy"))
        tx.setLinkedTo(leg, refund)
        val other = tx.insertOrIgnore(testTxn("other", key = "zomato"))
        val now0 = tx.insertOrIgnore(testTxn("now", key = "swiggy"))
        listener.events.clear()

        now = 5_000
        assertThat(sort.categorize(now0, food, scope = MerchantScope.BOTH)).isEqualTo(SortResult.Done)

        with(db.merchantRuleDao().get("swiggy")!!) {
            assertThat(categoryId).isEqualTo(food)
            assertThat(always).isTrue()
        }
        with(tx.get(waiting)!!) {
            assertThat(categoryId).isEqualTo(food)
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(autoCategorized).isTrue()
        }
        with(tx.get(elsewhere)!!) {
            assertThat(categoryId).isEqualTo(food)
            assertThat(note).isEqualTo("late night")
        }
        assertThat(tx.get(ignored)!!.status).isEqualTo(TransactionStatus.IGNORED)
        assertThat(tx.get(duplicate)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(refund)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(leg)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(other)!!.status).isEqualTo(TransactionStatus.PENDING)
        // Only what left the inbox is reported (notifications, widgets).
        assertThat(listener.events).containsExactly("sorted:$now0", "sorted:$waiting")
    }

    @Test
    fun `earlier only moves stored payments and leaves new ones to the inbox`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        val tx = db.transactionDao()
        val waiting = tx.insertOrIgnore(testTxn("waiting", key = "swiggy"))
        val elsewhere = tx.insertOrIgnore(testTxn("elsewhere", key = "swiggy"))
        sort.categorize(elsewhere, fuel)
        val now0 = tx.insertOrIgnore(testTxn("now", key = "swiggy"))

        sort.categorize(now0, food, scope = MerchantScope(earlier = true))

        assertThat(tx.get(waiting)!!.categoryId).isEqualTo(food)
        assertThat(tx.get(elsewhere)!!.categoryId).isEqualTo(food)
        with(db.merchantRuleDao().get("swiggy")!!) {
            assertThat(always).isFalse()
            assertThat(categoryId).isEqualTo(food)
        }
    }

    @Test
    fun `newer only makes the rule always and leaves stored payments alone`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        val tx = db.transactionDao()
        val waiting = tx.insertOrIgnore(testTxn("waiting", key = "swiggy"))
        val elsewhere = tx.insertOrIgnore(testTxn("elsewhere", key = "swiggy"))
        sort.categorize(elsewhere, fuel)
        val now0 = tx.insertOrIgnore(testTxn("now", key = "swiggy"))
        listener.events.clear()

        sort.categorize(now0, food, scope = MerchantScope(newer = true))

        assertThat(tx.get(waiting)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(elsewhere)!!.categoryId).isEqualTo(fuel)
        with(db.merchantRuleDao().get("swiggy")!!) {
            assertThat(always).isTrue()
            assertThat(categoryId).isEqualTo(food)
        }
        assertThat(listener.events).containsExactly("sorted:$now0")
        assertThat(sort.forMerchant("zomato", food, MerchantScope(newer = true))).isEqualTo(0)
        assertThat(db.merchantRuleDao().get("zomato")!!.always).isTrue()
    }

    @Test
    fun `sorting the merchant elsewhere ends always, the same category keeps it`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        val tx = db.transactionDao()
        val a = tx.insertOrIgnore(testTxn("a", key = "swiggy"))
        val b = tx.insertOrIgnore(testTxn("b", key = "swiggy"))
        val c = tx.insertOrIgnore(testTxn("c", key = "swiggy"))
        sort.categorize(a, food, scope = MerchantScope.BOTH)
        sort.categorize(b, food)
        assertThat(db.merchantRuleDao().get("swiggy")!!.always).isTrue()
        sort.categorize(c, fuel)
        with(db.merchantRuleDao().get("swiggy")!!) {
            assertThat(always).isFalse()
            assertThat(categoryId).isEqualTo(fuel)
        }
    }

    @Test
    fun `always from outside a sort moves the payments and can be stopped`() = runTest {
        val food = cat("Food")
        val groups = cat("Group")
        cat("Leaf", parent = groups)
        val tx = db.transactionDao()
        val a = tx.insertOrIgnore(testTxn("a", key = "swiggy"))
        val b = tx.insertOrIgnore(testTxn("b", key = "swiggy"))

        assertThat(sort.forMerchant("swiggy", groups, MerchantScope.BOTH)).isNull()
        assertThat(tx.get(a)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(sort.forMerchant("swiggy", food, MerchantScope.BOTH)).isEqualTo(2)
        assertThat(tx.get(a)!!.categoryId).isEqualTo(food)
        assertThat(tx.get(b)!!.categoryId).isEqualTo(food)
        assertThat(db.merchantRuleDao().get("swiggy")!!.always).isTrue()

        sort.stopAlways("swiggy")
        with(db.merchantRuleDao().get("swiggy")!!) {
            assertThat(always).isFalse()
            assertThat(categoryId).isEqualTo(food)
        }
    }

    @Test
    fun `not remembering leaves the rule alone`() = runTest {
        val food = cat("Food")
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "swiggy"))
        sort.categorize(a, food, rememberMerchant = false)
        assertThat(db.merchantRuleDao().get("swiggy")).isNull()
    }

    @Test
    fun `only active leaves are accepted`() = runTest {
        val food = cat("Food")
        cat("Groceries", parent = food)
        val old = cat("Old", archived = true)
        val a = db.transactionDao().insertOrIgnore(testTxn("a"))
        assertThat(sort.categorize(a, food)).isEqualTo(SortResult.NotALeaf)
        assertThat(sort.categorize(a, old)).isEqualTo(SortResult.CategoryArchived)
        assertThat(sort.categorize(a, 999)).isEqualTo(SortResult.CategoryNotFound)
        assertThat(sort.categorize(999, food)).isEqualTo(SortResult.TransactionNotFound)
        assertThat(db.transactionDao().get(a)!!.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `ignore marks IGNORED by the user and clears the category`() = runTest {
        val food = cat("Food")
        val a = db.transactionDao().insertOrIgnore(testTxn("a"))
        sort.categorize(a, food)
        assertThat(sort.ignore(a)).isEqualTo(SortResult.Done)
        with(db.transactionDao().get(a)!!) {
            assertThat(status).isEqualTo(TransactionStatus.IGNORED)
            assertThat(ignoreReason).isEqualTo(IgnoreReason.USER)
            assertThat(categoryId).isNull()
        }
        assertThat(sort.ignore(999)).isEqualTo(SortResult.TransactionNotFound)
    }

    @Test
    fun `requirePending makes a stale sort or ignore a no-op`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "swiggy"))
        sort.categorize(a, food)

        assertThat(sort.categorize(a, fuel, requirePending = true)).isEqualTo(SortResult.NotPending)
        assertThat(sort.ignore(a, requirePending = true)).isEqualTo(SortResult.NotPending)
        with(db.transactionDao().get(a)!!) {
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(categoryId).isEqualTo(food)
        }
        // The stale tap must not teach the rule either.
        assertThat(db.merchantRuleDao().get("swiggy")!!.categoryId).isEqualTo(food)
    }

    @Test
    fun `duplicate ignore keeps the reason`() = runTest {
        val a = db.transactionDao().insertOrIgnore(testTxn("a"))
        assertThat(sort.ignore(a, IgnoreReason.DUPLICATE, requirePending = true)).isEqualTo(SortResult.Done)
        assertThat(db.transactionDao().get(a)!!.ignoreReason).isEqualTo(IgnoreReason.DUPLICATE)
    }

    @Test
    fun `listeners hear about successful sorts only`() = runTest {
        val food = cat("Food")
        val a = db.transactionDao().insertOrIgnore(testTxn("a"))
        val b = db.transactionDao().insertOrIgnore(testTxn("b"))

        sort.categorize(a, 999)
        sort.categorize(a, food)
        sort.categorize(a, food, requirePending = true)
        sort.ignore(b)
        sort.ignore(999)

        assertThat(listener.events).containsExactly("sorted:$a", "sorted:$b").inOrder()
    }

    @Test
    fun `a category that can't hold the money's direction is refused`() = runTest {
        val food = cat("Food")
        val salary = db.transactionDao().insertOrIgnore(testTxn("in", direction = Direction.IN))
        assertThat(sort.categorize(salary, food)).isEqualTo(SortResult.KindMismatch)
        assertThat(db.transactionDao().get(salary)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(listener.events).isEmpty()
    }
}
