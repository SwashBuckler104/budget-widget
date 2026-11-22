package io.github.swashbuckler104.budgetwidget.core.data.purple

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.sort.SortTransactionUseCase
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.data.update.UpdateReminder
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.InboxSignals
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlin.time.Duration.Companion.days
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
class PurpleTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BudgetDatabase
    private lateinit var settings: SettingsStore
    private lateinit var purple: Purple
    private var now = 100.days.inWholeMilliseconds

    /** Installed today: the update notice isn't due. */
    private val installedAt get() = now

    @Before
    fun setUp() {
        db = inMemoryDb()
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) {
                tmp.root.resolve("s.preferences_pb")
            },
        )
        val sort = SortTransactionUseCase(db, { now }, InboxEvents(emptySet()))
        purple = Purple(db, settings, UpdateReminder(settings) { now }, sort)
    }

    @After
    fun tearDown() = db.close()

    private val quiet = PurpleInputs(
        seen = PurpleTip.TOUR.map { it.id }.toSet(),
        tipsOn = true,
        updateDue = false,
        unreadIds = emptyList(),
        unreadMark = 0,
        inbox = InboxSignals(pending = 0, duplicates = 0),
        alwaysOffers = emptyList(),
    )
    private val offer = AlwaysOffer("swiggy", "Swiggy", 7, "Food")

    @Test
    fun `the tour comes first, one step at a time, in bar order`() {
        val fresh = quiet.copy(seen = emptySet(), updateDue = true)
        assertThat(Purple.pick(fresh)).isEqualTo(PurpleNote(PurpleTip.TOUR_TRANSACTIONS))
        assertThat(Purple.pick(fresh)!!.tourStep).isEqualTo(1)
        val two = fresh.copy(seen = setOf(PurpleTip.TOUR_TRANSACTIONS.id))
        assertThat(Purple.pick(two)!!.tip).isEqualTo(PurpleTip.TOUR_ANALYSIS)
    }

    @Test
    fun `notices come before tips, and still show with tips off`() {
        val busy = quiet.copy(
            updateDue = true,
            unreadIds = listOf(3, 4),
            inbox = InboxSignals(pending = 5, duplicates = 1),
            alwaysOffers = listOf(offer),
        )
        assertThat(Purple.pick(busy)!!.tip).isEqualTo(PurpleTip.UPDATE)
        val unread = Purple.pick(busy.copy(updateDue = false))!!
        assertThat(unread).isEqualTo(PurpleNote(PurpleTip.UNREAD_SMS, unreadSms = 2, newestUnreadId = 4))
        assertThat(Purple.pick(busy.copy(updateDue = false, unreadMark = 4))!!.tip).isEqualTo(PurpleTip.DUPLICATE)

        val off = busy.copy(seen = emptySet(), tipsOn = false)
        assertThat(Purple.pick(off)!!.tip).isEqualTo(PurpleTip.UPDATE)
        assertThat(Purple.pick(off.copy(updateDue = false))!!.tip).isEqualTo(PurpleTip.UNREAD_SMS)
        assertThat(Purple.pick(off.copy(updateDue = false, unreadMark = 4))).isNull()
    }

    @Test
    fun `tips show once each, sort all only when a few payments wait`() {
        assertThat(Purple.pick(quiet)).isNull()
        assertThat(Purple.pick(quiet.copy(inbox = InboxSignals(2, 0)))).isNull()
        assertThat(Purple.pick(quiet.copy(inbox = InboxSignals(3, 0)))!!.tip).isEqualTo(PurpleTip.SORT_ALL)
        val seen = quiet.copy(
            seen = quiet.seen + PurpleTip.SORT_ALL.id + PurpleTip.DUPLICATE.id,
            inbox = InboxSignals(9, 2),
        )
        assertThat(Purple.pick(seen)).isNull()
    }

    @Test
    fun `always is offered per merchant and stops after two declines`() {
        val two = quiet.copy(alwaysOffers = listOf(offer, offer.copy(merchantKey = "zomato", merchant = "Zomato")))
        assertThat(Purple.pick(two)!!.always).isEqualTo(offer)
        val askedOnce = two.copy(seen = two.seen + "always:swiggy")
        assertThat(Purple.pick(askedOnce)!!.always!!.merchantKey).isEqualTo("zomato")
        val declinedTwice = two.copy(
            seen = two.seen + "always_declined:a" + "always_declined:b",
        )
        assertThat(Purple.pick(declinedTwice)).isNull()
    }

    @Test
    fun `answers are remembered - tour skip, unread mark, always accepted`() = runTest {
        assertThat(purple.note(installedAt).first()!!.tip).isEqualTo(PurpleTip.TOUR_TRANSACTIONS)
        purple.answer(purple.note(installedAt).first()!!, accepted = true)
        assertThat(purple.note(installedAt).first()!!.tip).isEqualTo(PurpleTip.TOUR_ANALYSIS)
        purple.skipTour()
        assertThat(purple.note(installedAt).first()).isNull()

        db.unparsedSmsDao().insertOrIgnore(
            UnparsedSmsEntity(
                sender = "AD-HDFCBK",
                bodyHash = "h",
                body = null,
                receivedAt = 0,
                reason = UnparsedReason.NO_MATCH,
            ),
        )
        db.unparsedSmsDao().insertOrIgnore(
            UnparsedSmsEntity(
                sender = "AD-HDFCBK",
                bodyHash = "w",
                body = null,
                receivedAt = 0,
                reason = UnparsedReason.WRONG_AMOUNT,
            ),
        )
        val unread = purple.note(installedAt).first()!!
        assertThat(unread.tip).isEqualTo(PurpleTip.UNREAD_SMS)
        assertThat(unread.unreadSms).isEqualTo(1)
        purple.answer(unread, accepted = false)
        assertThat(purple.note(installedAt).first()).isNull()

        val food = db.categoryDao().insert(
            CategoryEntity(
                parentId = null,
                name = "Food",
                iconKey = "🍽️",
                colorArgb = 0,
                kind = CategoryKind.EXPENSE,
                sortOrder = 0,
            ),
        )
        val sort = SortTransactionUseCase(db, { now }, InboxEvents(emptySet()))
        repeat(3) { sort.categorize(db.transactionDao().insertOrIgnore(testTxn("s$it", key = "swiggy")), food) }
        val waiting = db.transactionDao().insertOrIgnore(testTxn("w", key = "swiggy"))
        val always = purple.note(installedAt).first()!!
        assertThat(always.always).isEqualTo(AlwaysOffer("swiggy", "swiggy", food, "Food"))
        purple.answer(always, accepted = true)
        assertThat(db.merchantRuleDao().get("swiggy")!!.always).isTrue()
        assertThat(db.transactionDao().get(waiting)!!.status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(purple.note(installedAt).first()).isNull()
    }

    @Test
    fun `the update notice shows once due and goes quiet when answered`() = runTest {
        purple.skipTour()
        val installed = now - UpdateReminder.REMIND_AFTER_DAYS.days.inWholeMilliseconds
        val note = purple.note(installed).first()!!
        assertThat(note.tip).isEqualTo(PurpleTip.UPDATE)
        purple.answer(note, accepted = true)
        assertThat(purple.note(installed).first()).isNull()
    }
}
