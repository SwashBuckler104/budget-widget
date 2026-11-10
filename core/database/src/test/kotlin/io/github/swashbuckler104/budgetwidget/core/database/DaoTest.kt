package io.github.swashbuckler104.budgetwidget.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
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
class DaoTest {
    private lateinit var db: BudgetDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `account lookup matches a null last4`() = runTest {
        val dao = db.accountDao()
        val id = dao.insert(account("Slice", null))
        assertThat(dao.find("Slice", null)?.id).isEqualTo(id)
        assertThat(dao.find("Slice", "1234")).isNull()
    }

    @Test
    fun `same txnHash is inserted once`() = runTest {
        val dao = db.transactionDao()
        val accountId = db.accountDao().insert(account("HDFC Bank", "1234"))
        assertThat(dao.insertOrIgnore(txn("h1", accountId))).isGreaterThan(0L)
        assertThat(dao.insertOrIgnore(txn("h1", accountId))).isEqualTo(-1L)
        assertThat(dao.count()).isEqualTo(1)
    }

    @Test
    fun `cross-source duplicate needs another bank, same amount and direction, inside the window`() = runTest {
        val dao = db.transactionDao()
        val federal = db.accountDao().insert(account("Federal Bank", "1111"))
        val jupiter = db.accountDao().insert(account("Jupiter", "2222"))
        val original = dao.insertOrIgnore(txn("a", federal, amount = 50_000, at = 1_000_000))
        dao.insertOrIgnore(txn("b", federal, amount = 50_000, at = 1_000_100)) // same bank
        dao.insertOrIgnore(txn("c", federal, amount = 50_000, at = 1_000_100, direction = Direction.IN))
        val newer = dao.insertOrIgnore(txn("d", jupiter, amount = 50_000, at = 1_000_200))

        val found = dao.findCrossSourceDuplicate(
            newer,
            50_000,
            Direction.OUT,
            1_000_200 - 600_000,
            1_000_200 + 600_000,
            "Jupiter",
        )
        assertThat(found?.id).isEqualTo(original)

        assertThat(dao.findCrossSourceDuplicate(newer, 49_999, Direction.OUT, 0, 2_000_000, "Jupiter")).isNull()
        assertThat(dao.findCrossSourceDuplicate(newer, 50_000, Direction.OUT, 1_000_150, 2_000_000, "Jupiter")).isNull()
    }

    @Test
    fun `unparsed list ignores repeats and keeps the newest 200`() = runTest {
        val dao = db.unparsedSmsDao()
        repeat(205) { i -> dao.insertCapped(unparsed("hash$i", receivedAt = i.toLong())) }
        dao.insertCapped(unparsed("hash204", receivedAt = 999))
        assertThat(dao.count()).isEqualTo(200)
        assertThat(dao.observeCount().first()).isEqualTo(200)
    }

    @Test
    fun `transactions list carries the account's bank and last4`() = runTest {
        val accountId = db.accountDao().insert(account("Axis Bank", "4321"))
        db.transactionDao().insertOrIgnore(txn("x", accountId))
        val row = db.transactionDao().observeAllWithAccount().first().single()
        assertThat(row.bankName).isEqualTo("Axis Bank")
        assertThat(row.accountLast4).isEqualTo("4321")
    }

    @Test
    fun `waiting-at counts what was in the inbox at that instant, without backfill`() = runTest {
        val acc = db.accountDao().insert(account("HDFC Bank", "1"))
        val dao = db.transactionDao()
        val sortedLater = dao.insertOrIgnore(txn("late", acc).copy(createdAt = 100))
        val sortedBefore = dao.insertOrIgnore(txn("early", acc).copy(createdAt = 100))
        dao.insertOrIgnore(txn("open", acc).copy(createdAt = 150))
        dao.insertOrIgnore(txn("after", acc).copy(createdAt = 300))
        dao.insertOrIgnore(txn("old", acc).copy(createdAt = 50, isBackfill = true))
        dao.ignore(sortedBefore, IgnoreReason.USER, now = 180)
        dao.ignore(sortedLater, IgnoreReason.USER, now = 250)
        // Re-sorting later keeps the first sort time.
        dao.ignore(sortedBefore, IgnoreReason.DUPLICATE, now = 900)

        assertThat(dao.countWaitingAt(200)).isEqualTo(2) // "late" and "open"
        assertThat(dao.countWaitingAt(100)).isEqualTo(0)
        assertThat(dao.countOpen()).isEqualTo(2) // "open" and "after"
        assertThat(dao.get(sortedBefore)!!.sortedAt).isEqualTo(180)
    }

    @Test
    fun `spending skips income, ignored rows and transfer categories`() = runTest {
        val acc = db.accountDao().insert(account("HDFC Bank", "1"))
        val dao = db.transactionDao()
        val transfer = db.categoryDao().insert(category("Savings", CategoryKind.TRANSFER))
        val food = db.categoryDao().insert(category("Food", CategoryKind.EXPENSE))
        val a = dao.insertOrIgnore(txn("a", acc, amount = 100_00, at = 10))
        dao.insertOrIgnore(txn("b", acc, amount = 50_00, at = 20)) // pending counts
        dao.insertOrIgnore(txn("in", acc, amount = 999_00, at = 20, direction = Direction.IN))
        val ignored = dao.insertOrIgnore(txn("ign", acc, amount = 7_00, at = 20))
        val moved = dao.insertOrIgnore(txn("mv", acc, amount = 3_00, at = 20))
        dao.insertOrIgnore(txn("late", acc, amount = 1_00, at = 100))
        dao.categorize(a, food, note = null, auto = false, now = 1)
        dao.categorize(moved, transfer, note = null, auto = false, now = 1)
        dao.ignore(ignored, IgnoreReason.USER, now = 1)

        assertThat(dao.spentBetween(0, 100)).isEqualTo(150_00)
    }

    private fun category(name: String, kind: CategoryKind) =
        CategoryEntity(parentId = null, name = name, iconKey = "🙂", colorArgb = 0, kind = kind, sortOrder = 0)

    private fun account(bank: String, last4: String?) =
        AccountEntity(bankName = bank, last4 = last4, kind = AccountKind.BANK, createdAt = 0)

    private fun unparsed(hash: String, receivedAt: Long) = UnparsedSmsEntity(
        sender = "AD-HDFCBK",
        bodyHash = hash,
        body = "otp",
        receivedAt = receivedAt,
        reason = UnparsedReason.NO_MATCH,
    )

    private fun txn(
        hash: String,
        accountId: Long,
        amount: Long = 10_000,
        at: Long = 0,
        direction: Direction = Direction.OUT,
    ) = TransactionEntity(
        txnHash = hash,
        source = TransactionSource.SMS,
        amountPaise = amount,
        direction = direction,
        parserType = null,
        merchantRaw = null,
        merchantDisplay = null,
        merchantKey = null,
        accountId = accountId,
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
