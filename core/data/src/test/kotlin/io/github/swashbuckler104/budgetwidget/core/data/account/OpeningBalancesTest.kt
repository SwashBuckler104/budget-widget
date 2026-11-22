package io.github.swashbuckler104.budgetwidget.core.data.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningBalancesTest {
    private lateinit var db: BudgetDatabase
    private lateinit var balances: OpeningBalances
    private var now = 10_000L
    private var account = 0L
    private var seeded = 0L

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var settings: SettingsStore

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                tmp.root.resolve("s.preferences_pb")
            },
        )
        balances = OpeningBalances(db, settings) { now }
        account = db.accountDao().insert(
            AccountEntity(bankName = "HDFC Bank", last4 = "8812", kind = AccountKind.BANK, createdAt = 0),
        )
        seeded = db.categoryDao().insert(
            CategoryEntity(
                parentId = null,
                name = "Opening balance",
                iconKey = "🏁",
                colorArgb = 0,
                kind = CategoryKind.TRANSFER,
                sortOrder = 0,
                isSeed = true,
                seedKey = "opening_balance",
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun rows() = db.backupDao().transactions().filter {
        it.txnHash?.startsWith("opening-balance:") ==
            true
    }

    @Test
    fun `setting a balance stores one sorted transfer and the account's balance`() = runTest {
        balances.set(account, 52_340_00)

        val row = rows().single()
        assertThat(row.source).isEqualTo(TransactionSource.MANUAL)
        assertThat(row.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(row.categoryId).isEqualTo(seeded)
        assertThat(row.direction).isEqualTo(Direction.IN)
        assertThat(row.amountPaise).isEqualTo(52_340_00)
        assertThat(row.accountId).isEqualTo(account)
        assertThat(row.merchantDisplay).isEqualTo("Opening balance")
        assertThat(db.accountDao().find("HDFC Bank", "8812")!!.lastBalancePaise).isEqualTo(52_340_00)
        assertThat(balances.observe().first()).containsExactly(account, 52_340_00L)
    }

    @Test
    fun `changing it updates the same row, a debt is money out, and null removes it`() = runTest {
        balances.set(account, 1_000_00)
        now = 20_000L
        balances.set(account, -2_500_00)

        val row = rows().single()
        assertThat(row.direction).isEqualTo(Direction.OUT)
        assertThat(row.amountPaise).isEqualTo(2_500_00)
        assertThat(row.updatedAt).isEqualTo(20_000L)
        assertThat(balances.observe().first()).containsExactly(account, -2_500_00L)

        balances.set(account, null)
        assertThat(rows()).isEmpty()
        assertThat(balances.observe().first()).isEmpty()
    }

    @Test
    fun `without the seeded category a top-level transfer category takes its key`() = runTest {
        db.categoryDao().deleteIds(listOf(seeded))

        balances.set(account, 100_00)
        balances.set(account, 200_00)

        val category = db.categoryDao().bySeedKey("opening_balance")!!
        assertThat(category.parentId).isNull()
        assertThat(category.kind).isEqualTo(CategoryKind.TRANSFER)
        assertThat(db.categoryDao().getAll().count { it.name == "Opening balance" }).isEqualTo(1)
        assertThat(rows().single().categoryId).isEqualTo(category.id)
    }

    @Test
    fun `the prompt shows once an account is known, until a balance is set or it is dismissed`() = runTest {
        assertThat(balances.promptVisible().first()).isTrue()
        balances.set(account, 100_00)
        assertThat(balances.promptVisible().first()).isFalse()
        balances.set(account, null)
        assertThat(balances.promptVisible().first()).isTrue()
        balances.dismissPrompt()
        assertThat(balances.promptVisible().first()).isFalse()
    }

    @Test
    fun `no prompt before any visible account exists`() = runTest {
        db.accountDao().setHidden(account, true)
        assertThat(balances.promptVisible().first()).isFalse()
    }
}
