package io.github.swashbuckler104.budgetwidget.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.suggest.SuggestionEngine
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TransactionNotifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private lateinit var db: BudgetDatabase
    private lateinit var settings: SettingsStore
    private lateinit var notifier: TransactionNotifier
    private lateinit var handler: NotificationActionHandler
    private val now = 1_700_000_000_000L
    private var account = 0L
    private var widgetRefreshes = 0

    private val launcher = object : QuickSortLauncher {
        override fun transaction(context: Context, transactionId: Long) = Intent("sort").putExtra("id", transactionId)

        override fun inbox(context: Context) = Intent("inbox")
    }

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).allowMainThreadQueries().build()
        NotificationChannels.ensure(context)
    }

    @After
    fun tearDown() = db.close()

    /** Builds the object graph inside the test's scope (DataStore needs a live scope). */
    private fun TestScope.wire() {
        settings =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(tmp.root, "s.preferences_pb")
                },
            )
        val clock = { now }
        val repo = TransactionRepository(db.transactionDao(), db.unparsedSmsDao(), db.accountDao())
        notifier = TransactionNotifier(
            context,
            repo,
            SuggestionEngine(db, clock),
            settings,
            TransactionNotificationFactory(context, launcher),
            clock,
        )
        handler = NotificationActionHandler(
            SortTransactionUseCase(db, clock, InboxEvents(setOf(notifier))),
            notifier,
            object : InboxWidgets {
                override suspend fun refreshNow() {
                    widgetRefreshes++
                }
            },
        )
    }

    private suspend fun category(name: String, archived: Boolean = false) = db.categoryDao().insert(
        CategoryEntity(
            parentId = null,
            name = name,
            iconKey = "🍔",
            colorArgb = 0,
            kind = CategoryKind.EXPENSE,
            sortOrder = 0,
            archived = archived,
        ),
    )

    private suspend fun txn(
        hash: String,
        amount: Long = 450_00,
        merchant: String? = "Swiggy",
        duplicateOf: Long? = null,
    ): Long {
        if (account == 0L) {
            account = db.accountDao().insert(
                AccountEntity(bankName = "HDFC Bank", last4 = "1234", kind = AccountKind.BANK, createdAt = 0),
            )
        }
        return db.transactionDao().insertOrIgnore(
            TransactionEntity(
                txnHash = hash,
                source = TransactionSource.SMS,
                amountPaise = amount,
                direction = Direction.OUT,
                parserType = ParserType.EXPENSE,
                merchantRaw = merchant,
                merchantDisplay = merchant,
                merchantKey = merchant?.lowercase(),
                accountId = account,
                occurredAt = now,
                smsSender = null,
                smsBody = null,
                reference = null,
                balanceAfterPaise = null,
                isCard = false,
                status = TransactionStatus.PENDING,
                isBackfill = false,
                duplicateOfId = duplicateOf,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun shown(): List<Notification> = shadowOf(manager).allNotifications

    private fun transactionNotifications() = manager.activeNotifications.filter {
        TransactionNotifier.idFromTag(it.tag) !=
            null
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun `a new transaction posts amount, merchant, account and 3 category buttons`() = runTest {
        wire()
        listOf("Food", "Fuel", "Rent", "Gym").forEach { category(it) }
        val id = txn("a")

        notifier.onPendingAdded(id, IngestOrigin.REALTIME)

        val active = transactionNotifications().single()
        assertThat(active.tag).isEqualTo("txn:$id")
        val n = active.notification
        assertThat(n.channelId).isEqualTo(NotificationChannels.TRANSACTIONS)
        assertThat(n.title()).isEqualTo("-₹450 · Swiggy")
        assertThat(n.text()).startsWith("HDFC Bank ••1234 · ")
        assertThat(n.actions.map { it.title.toString() }).containsExactly("🍔 Food", "🍔 Fuel", "🍔 Rent").inOrder()
        assertThat(n.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        assertThat(n.publicVersion.title()).doesNotContain("450")
    }

    @Test
    fun `settings and origin can suppress the notification`() = runTest {
        wire()
        category("Food")
        settings.setNotifyMinAmountPaise(1_000_00)
        notifier.onPendingAdded(txn("small"), IngestOrigin.REALTIME)
        settings.setNotifyMinAmountPaise(0)
        notifier.onPendingAdded(txn("old"), IngestOrigin.BACKFILL)
        settings.setNotificationsEnabled(false)
        notifier.onPendingAdded(txn("off"), IngestOrigin.REALTIME)
        assertThat(shown()).isEmpty()
    }

    @Test
    fun `without the permission nothing is posted`() = runTest {
        wire()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.onPendingAdded(txn("a"), IngestOrigin.REALTIME)
        assertThat(shown()).isEmpty()
    }

    @Test
    fun `two or more get a silent summary that stays until the last child goes`() = runTest {
        wire()
        category("Food")
        val a = txn("a")
        val b = txn("b")
        notifier.onPendingAdded(a, IngestOrigin.REALTIME)
        assertThat(manager.activeNotifications.filter { it.notification.isGroupSummary() }).isEmpty()

        notifier.onPendingAdded(b, IngestOrigin.REALTIME)
        val summary = manager.activeNotifications.single { it.notification.isGroupSummary() }.notification
        assertThat(summary.title()).isEqualTo("2 transactions to sort")
        assertThat(summary.group).isEqualTo(TransactionNotificationFactory.GROUP)

        notifier.cancel(a)
        // The summary stays while a child is up: cancelling it would cancel b's notification too
        // (Android cancels a summary's children; the Robolectric shadow doesn't model that).
        assertThat(transactionNotifications().map { it.tag }).containsExactly("txn:$b")
        val summary2 = manager.activeNotifications.single { it.notification.isGroupSummary() }.notification
        assertThat(summary2.title()).isEqualTo("1 transaction to sort")
        notifier.cancel(b)
        assertThat(manager.activeNotifications).isEmpty()
    }

    @Test
    fun `a button sorts the transaction and removes its notification`() = runTest {
        wire()
        val food = category("Food")
        val id = txn("a")
        notifier.onPendingAdded(id, IngestOrigin.REALTIME)

        handler.handle(NotificationCommand.Sort(id, food))

        with(db.transactionDao().get(id)!!) {
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(categoryId).isEqualTo(food)
        }
        // Like the sheet's default, a notification sort teaches the merchant rule.
        assertThat(db.merchantRuleDao().get("swiggy")!!.categoryId).isEqualTo(food)
        assertThat(manager.activeNotifications).isEmpty()
        assertThat(widgetRefreshes).isEqualTo(1)
    }

    @Test
    fun `a stale button never re-sorts, and a vanished category refreshes the buttons`() = runTest {
        wire()
        val food = category("Food")
        val fuel = category("Fuel")
        val id = txn("a")
        notifier.onPendingAdded(id, IngestOrigin.REALTIME)

        db.categoryDao().setArchivedWithChildren(food, true)
        handler.handle(NotificationCommand.Sort(id, food))
        assertThat(db.transactionDao().get(id)!!.status).isEqualTo(TransactionStatus.PENDING)
        val refreshed = transactionNotifications().single().notification
        assertThat(refreshed.actions.map { it.title.toString() }).containsExactly("🍔 Fuel", "Ignore").inOrder()

        handler.handle(NotificationCommand.Sort(id, fuel))
        notifier.onPendingAdded(id, IngestOrigin.REALTIME) // ignored: no longer pending
        handler.handle(NotificationCommand.Ignore(id))
        assertThat(db.transactionDao().get(id)!!.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(manager.activeNotifications).isEmpty()
    }

    @Test
    fun `a possible duplicate offers Same payment, which ignores it as a duplicate`() = runTest {
        wire()
        category("Food")
        val original = txn("a")
        val copy = txn("b", duplicateOf = original)
        notifier.onPendingAdded(copy, IngestOrigin.REALTIME)
        val n = transactionNotifications().single().notification
        assertThat(n.actions.first().title.toString()).isEqualTo("Same payment")
        assertThat(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
            .contains("Looks like the same payment as ₹450 at ")

        handler.handle(NotificationCommand.SamePayment(copy))
        with(db.transactionDao().get(copy)!!) {
            assertThat(status).isEqualTo(TransactionStatus.IGNORED)
            assertThat(ignoreReason).isEqualTo(IgnoreReason.DUPLICATE)
        }
        assertThat(manager.activeNotifications).isEmpty()
    }

    @Test
    fun `button intents survive the round trip and stay distinct per transaction`() {
        val sort = NotificationCommand.toIntent(context, NotificationCommand.Sort(5, 9))
        val other = NotificationCommand.toIntent(context, NotificationCommand.Sort(6, 9))
        assertThat(NotificationCommand.fromIntent(sort)).isEqualTo(NotificationCommand.Sort(5, 9))
        assertThat(
            NotificationCommand.fromIntent(NotificationCommand.toIntent(context, NotificationCommand.SamePayment(5))),
        )
            .isEqualTo(NotificationCommand.SamePayment(5))
        assertThat(sort.filterEquals(other)).isFalse()
        assertThat(NotificationCommand.fromIntent(Intent("bogus"))).isNull()
    }

    @Test
    fun `pruneStale and cancelAll clear what should not be up`() = runTest {
        wire()
        category("Food")
        val a = txn("a")
        val b = txn("b")
        notifier.onPendingAdded(a, IngestOrigin.REALTIME)
        notifier.onPendingAdded(b, IngestOrigin.REALTIME)
        db.transactionDao().ignore(a, IgnoreReason.USER, now) // sorted without the listener
        notifier.pruneStale()
        assertThat(transactionNotifications().map { it.tag }).containsExactly("txn:$b")
        notifier.cancelAll()
        assertThat(manager.activeNotifications).isEmpty()
    }

    @Test
    fun `the summary counts every posted notification and follows swipes`() = runTest {
        wire()
        category("Food")
        val ids = listOf(txn("a"), txn("b"), txn("c"))
        ids.forEach { notifier.onPendingAdded(it, IngestOrigin.REALTIME) }
        fun summaryTitle() = manager.activeNotifications.singleOrNull {
            it.notification.isGroupSummary()
        }?.notification?.title()
        assertThat(summaryTitle()).isEqualTo("3 transactions to sort")
        // Every notification carries a delete intent that reports the swipe back.
        assertThat(transactionNotifications().all { it.notification.deleteIntent != null }).isTrue()

        manager.cancel("txn:${ids[0]}", 1) // what a swipe does to the system list
        handler.handle(NotificationCommand.Dismissed(ids[0]))
        assertThat(summaryTitle()).isEqualTo("2 transactions to sort")
        handler.handle(NotificationCommand.Dismissed(ids[1]))
        assertThat(summaryTitle()).isEqualTo("1 transaction to sort") // kept while a child is up
        handler.handle(NotificationCommand.Dismissed(ids[2]))
        assertThat(summaryTitle()).isNull()
        assertThat(db.transactionDao().get(ids[0])!!.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `refreshing a transaction sorted meanwhile removes its notification instead`() = runTest {
        wire()
        category("Food")
        val id = txn("a")
        notifier.onPendingAdded(id, IngestOrigin.REALTIME)
        db.transactionDao().ignore(id, IgnoreReason.USER, now) // sorted, listener not yet run
        notifier.refresh(id)
        assertThat(manager.activeNotifications).isEmpty()
    }

    private fun Notification.isGroupSummary() = flags and Notification.FLAG_GROUP_SUMMARY != 0
}
