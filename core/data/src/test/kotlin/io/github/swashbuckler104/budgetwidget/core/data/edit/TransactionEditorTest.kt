package io.github.swashbuckler104.budgetwidget.core.data.edit

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.inbox.RecordingInboxListener
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortResult
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionEditorTest {
    private lateinit var db: BudgetDatabase
    private lateinit var editor: TransactionEditor
    private val listener = RecordingInboxListener()
    private var now = 5_000L

    @Before
    fun setUp() {
        db = inMemoryDb()
        val sort = SortTransactionUseCase(db, { now }, InboxEvents(setOf(listener)))
        editor = TransactionEditor(db, sort, InboxEvents(setOf(listener)), { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cat(name: String, kind: CategoryKind = CategoryKind.EXPENSE, parent: Long? = null) =
        db.categoryDao().insert(
            CategoryEntity(parentId = parent, name = name, iconKey = "🙂", colorArgb = 0, kind = kind, sortOrder = 0),
        )

    @Test
    fun `save renames, changes direction and sorts, and apply-to-all renames the merchant and new SMS`() = runTest {
        val salary = cat("Salary", CategoryKind.INCOME)
        val tx = db.transactionDao()
        val a = tx.insertOrIgnore(testTxn("a", key = "acme"))
        val b = tx.insertOrIgnore(testTxn("b", key = "acme"))

        val result = editor.save(
            TransactionEdit(
                id = a,
                direction = Direction.IN,
                categoryId = salary,
                note = " june ",
                merchantName = "  Acme   Corp ",
                applyNameToMerchant = true,
            ),
        )

        assertThat(result).isInstanceOf(EditResult.Saved::class.java)
        assertThat((result as EditResult.Saved).renamed).isEqualTo(1)
        with(tx.get(a)!!) {
            assertThat(direction).isEqualTo(Direction.IN)
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(categoryId).isEqualTo(salary)
            assertThat(note).isEqualTo("june")
            assertThat(merchantDisplay).isEqualTo("Acme Corp")
            assertThat(editedByUser).isTrue()
        }
        assertThat(tx.get(b)!!.merchantDisplay).isEqualTo("Acme Corp")
        assertThat(db.merchantAliasDao().displayName("acme")).isEqualTo("Acme Corp")
        assertThat(db.merchantRuleDao().get("acme")!!.categoryId).isEqualTo(salary)
        assertThat(listener.events).containsExactly("sorted:$a")
    }

    @Test
    fun `save refuses a category that does not fit the direction, and SMS amounts are read-only`() = runTest {
        val salary = cat("Salary", CategoryKind.INCOME)
        val a = db.transactionDao().insertOrIgnore(testTxn("a", amount = 450_00))

        assertThat(editor.save(TransactionEdit(a, Direction.OUT, salary, null, null)))
            .isEqualTo(EditResult.CategoryMismatch)
        editor.save(TransactionEdit(a, Direction.OUT, null, null, null, amountPaise = 1_00))
        assertThat(db.transactionDao().get(a)!!.amountPaise).isEqualTo(450_00)
    }

    @Test
    fun `naming an unknown merchant gives it a key`() = runTest {
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = null))
        editor.save(TransactionEdit(a, Direction.OUT, null, null, "Corner Shop"))
        assertThat(db.transactionDao().get(a)!!.merchantKey).isEqualTo("corner shop")
    }

    @Test
    fun `manual transactions are stored sorted, can be edited and deleted with undo`() = runTest {
        val cash = cat("Snacks")
        val saved = editor.addManual(ManualTransaction(120_00, Direction.OUT, cash, "Tea stall", null, 1_000))
        val id = (saved as EditResult.Saved).id
        with(db.transactionDao().get(id)!!) {
            assertThat(source).isEqualTo(TransactionSource.MANUAL)
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(sortedAt).isEqualTo(now)
            assertThat(txnHash).isNull()
        }

        editor.save(
            TransactionEdit(id, Direction.OUT, null, null, "Tea stall", amountPaise = 150_00, occurredAt = 2_000),
        )
        assertThat(db.transactionDao().get(id)!!.amountPaise).isEqualTo(150_00)
        assertThat(db.transactionDao().get(id)!!.occurredAt).isEqualTo(2_000)

        val deleted = editor.deleteManual(id)
        assertThat(deleted.changed).isEqualTo(1)
        assertThat(db.transactionDao().get(id)).isNull()
        editor.undo(deleted.undo!!)
        assertThat(db.transactionDao().get(id)!!.amountPaise).isEqualTo(150_00)
    }

    @Test
    fun `manual add validates amount and category`() = runTest {
        val parent = cat("Food")
        cat("Groceries", parent = parent)
        val salary = cat("Salary", CategoryKind.INCOME)
        assertThat(editor.addManual(ManualTransaction(0, Direction.OUT, parent, null, null, 0)))
            .isEqualTo(EditResult.InvalidAmount)
        assertThat(editor.addManual(ManualTransaction(1_00, Direction.OUT, parent, null, null, 0)))
            .isEqualTo(EditResult.SortFailed(SortResult.NotALeaf))
        assertThat(editor.addManual(ManualTransaction(1_00, Direction.OUT, salary, null, null, 0)))
            .isEqualTo(EditResult.CategoryMismatch)
    }

    @Test
    fun `SMS transactions cannot be deleted`() = runTest {
        val a = db.transactionDao().insertOrIgnore(testTxn("a"))
        assertThat(editor.deleteManual(a)).isEqualTo(Undoable(0, null))
        assertThat(db.transactionDao().get(a)).isNotNull()
    }

    @Test
    fun `undoing a sort puts the inbox item and the merchant rule back`() = runTest {
        val food = cat("Food")
        val fuel = cat("Fuel")
        db.merchantRuleDao().upsert(MerchantRuleEntity("swiggy", food, confirmCount = 4, lastUsedAt = 1))
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "swiggy"))
        val b = db.transactionDao().insertOrIgnore(testTxn("b", key = "zomato"))

        val done = editor.categorize(listOf(a, b), fuel, rememberMerchant = true)
        assertThat(done.changed).isEqualTo(2)
        assertThat(db.merchantRuleDao().get("swiggy")!!.categoryId).isEqualTo(fuel)

        editor.undo(done.undo!!)
        with(db.transactionDao().get(a)!!) {
            assertThat(status).isEqualTo(TransactionStatus.PENDING)
            assertThat(categoryId).isNull()
            assertThat(sortedAt).isNull()
        }
        assertThat(db.merchantRuleDao().get("swiggy")).isEqualTo(MerchantRuleEntity("swiggy", food, 4, 1))
        assertThat(db.merchantRuleDao().get("zomato")).isNull()
    }

    @Test
    fun `bulk sorts without remembering leave rules alone`() = runTest {
        val food = cat("Food")
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "swiggy"))
        editor.categorize(listOf(a), food, rememberMerchant = false)
        assertThat(db.merchantRuleDao().get("swiggy")).isNull()
    }

    @Test
    fun `ignore, unignore, merge and keep both`() = runTest {
        val tx = db.transactionDao()
        val first = tx.insertOrIgnore(testTxn("a"))
        val dup = tx.insertOrIgnore(testTxn("b"))
        tx.setDuplicateOf(dup, first)

        val ignored = editor.ignore(listOf(first))
        assertThat(tx.get(first)!!.status).isEqualTo(TransactionStatus.IGNORED)
        editor.undo(ignored.undo!!)
        assertThat(tx.get(first)!!.status).isEqualTo(TransactionStatus.PENDING)

        editor.merge(dup)
        assertThat(tx.get(dup)!!.ignoreReason).isEqualTo(IgnoreReason.DUPLICATE)
        editor.unignore(dup)
        assertThat(tx.get(dup)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(tx.get(dup)!!.ignoreReason).isNull()

        editor.keepBoth(dup)
        assertThat(tx.get(dup)!!.duplicateOfId).isNull()
    }

    @Test
    fun `report wrong amount files the SMS in the unparsed list`() = runTest {
        val a = db.transactionDao().insertOrIgnore(
            testTxn("a").copy(smsBody = "Rs 450 debited", smsSender = "AD-HDFCBK"),
        )
        val manual = (
            editor.addManual(
                ManualTransaction(1_00, Direction.OUT, cat("X"), null, null, 0),
            ) as EditResult.Saved
            ).id

        assertThat(editor.reportWrongAmount(a)).isTrue()
        assertThat(editor.reportWrongAmount(manual)).isFalse()
        assertThat(db.unparsedSmsDao().count()).isEqualTo(1)
        assertThat(db.unparsedSmsDao().observeCount().first()).isEqualTo(1)
    }

    @Test
    fun `new SMS from a renamed merchant use the alias`() = runTest {
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "swiggy"))
        editor.save(TransactionEdit(a, Direction.OUT, null, null, "Swiggy Food", applyNameToMerchant = true))
        assertThat(db.merchantAliasDao().displayName("swiggy")).isEqualTo("Swiggy Food")
        assertThat(UnparsedReason.WRONG_AMOUNT.name).isEqualTo("WRONG_AMOUNT")
    }

    @Test
    fun `a bulk sort skips transactions the category can't hold`() = runTest {
        val food = cat("Food")
        val pay = db.transactionDao().insertOrIgnore(testTxn("pay"))
        val salary = db.transactionDao().insertOrIgnore(testTxn("in", direction = Direction.IN))

        val done = editor.categorize(listOf(pay, salary), food, rememberMerchant = false)

        assertThat(done.changed).isEqualTo(1)
        assertThat(done.skipped).isEqualTo(1)
        assertThat(db.transactionDao().get(salary)!!.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `undo leaves alone a row edited after the action`() = runTest {
        val food = cat("Food")
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "shop"))
        val b = db.transactionDao().insertOrIgnore(testTxn("b", key = "other"))
        val done = editor.categorize(listOf(a, b), food, rememberMerchant = false)

        now += 1_000
        editor.save(TransactionEdit(a, Direction.OUT, null, "kept", "shop"))
        assertThat(editor.undo(done.undo!!)).isEqualTo(1)

        assertThat(db.transactionDao().get(a)!!.note).isEqualTo("kept")
        assertThat(db.transactionDao().get(a)!!.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(db.transactionDao().get(b)!!.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `a merchant rename can be undone, alias included`() = runTest {
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "swiggy"))
        val b = db.transactionDao().insertOrIgnore(testTxn("b", key = "swiggy"))

        val saved = editor.save(
            TransactionEdit(a, Direction.OUT, null, null, "Swiggy Food", applyNameToMerchant = true),
        )
            as EditResult.Saved
        assertThat(saved.renamed).isEqualTo(1)
        editor.undo(saved.undo!!)

        assertThat(db.transactionDao().get(a)!!.merchantDisplay).isEqualTo("swiggy")
        assertThat(db.transactionDao().get(b)!!.merchantDisplay).isEqualTo("swiggy")
        assertThat(db.merchantAliasDao().displayName("swiggy")).isNull()
    }

    @Test
    fun `saving an auto-sorted transaction with its category confirms it`() = runTest {
        val food = cat("Food")
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "shop"))
        db.transactionDao().categorize(a, food, null, auto = true, now = 1)

        editor.save(TransactionEdit(a, Direction.OUT, food, null, "shop"))

        assertThat(db.transactionDao().get(a)!!.autoCategorized).isFalse()
        assertThat(db.merchantRuleDao().get("shop")!!.categoryId).isEqualTo(food)
    }

    @Test
    fun `a failed sort rolls the whole edit back`() = runTest {
        val parent = cat("Food")
        cat("Groceries", parent = parent)
        val a = db.transactionDao().insertOrIgnore(testTxn("a", key = "shop"))

        val result = editor.save(TransactionEdit(a, Direction.OUT, parent, "lost", "New name"))

        assertThat(result).isEqualTo(EditResult.SortFailed(SortResult.NotALeaf))
        with(db.transactionDao().get(a)!!) {
            assertThat(note).isNull()
            assertThat(merchantDisplay).isEqualTo("shop")
            assertThat(editedByUser).isFalse()
        }
        assertThat(listener.events).isEmpty()
    }
}
