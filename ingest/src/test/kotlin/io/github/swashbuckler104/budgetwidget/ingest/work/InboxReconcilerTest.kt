package io.github.swashbuckler104.budgetwidget.ingest.work

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.ingest.parser.PennyWiseSmsParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Real parser, real Room and DataStore; only the system inbox is faked. */
@RunWith(AndroidJUnit4::class)
class InboxReconcilerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BudgetDatabase
    private val inbox = FakeInbox()
    private var now = DAY * 100

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
    fun `first scan looks back one day, then rescans from checkpoint minus overlap`() = runTest {
        val (reconciler, checkpoint) = reconciler(this)
        inbox.messages += hdfc(500, at = now - 2 * DAY) // older than the first-run window
        inbox.messages += hdfc(600, at = now - HOUR)
        inbox.messages += personal(at = now - HOUR)

        assertThat(reconciler.reconcile()).isEqualTo(InboxReconciler.Result.Scanned(2))
        assertThat(inbox.lastAfter).isEqualTo(now - DAY)
        assertThat(checkpoint.get()).isEqualTo(now - HOUR)
        assertThat(amounts()).containsExactly(60_000L)

        // The receiver missed one; the next scan picks it up, re-reads the overlap, stores no duplicates.
        now += HOUR
        inbox.messages += hdfc(700, at = now - MINUTE)
        assertThat(reconciler.reconcile()).isEqualTo(InboxReconciler.Result.Scanned(3))
        assertThat(inbox.lastAfter).isEqualTo(now - HOUR - HOUR - 10 * MINUTE)
        assertThat(amounts()).containsExactly(60_000L, 70_000L)
        assertThat(db.unparsedSmsDao().count()).isEqualTo(0)
    }

    @Test
    fun `receiver and inbox scan of the same SMS store one row`() = runTest {
        val (reconciler, _) = reconciler(this)
        val sms = hdfc(250, at = now - MINUTE)
        pipeline().ingest(sms.copy(receivedAt = sms.receivedAt - 3_000), IngestOrigin.REALTIME) // SC timestamp differs
        inbox.messages += sms
        reconciler.reconcile()
        assertThat(db.transactionDao().count()).isEqualTo(1)
    }

    @Test
    fun `backfill imports history flagged as backfill`() = runTest {
        val (reconciler, checkpoint) = reconciler(this)
        inbox.messages += hdfc(100, at = now - 20 * DAY)
        inbox.messages += hdfc(200, at = now - 5 * DAY)
        assertThat(reconciler.backfill(now - 30 * DAY)).isEqualTo(InboxReconciler.Result.Scanned(2))
        val rows = db.transactionDao().observeAllWithAccount().first()
        assertThat(rows.map { it.transaction.isBackfill }).containsExactly(true, true)
        assertThat(checkpoint.get()).isEqualTo(now - 5 * DAY)
    }

    @Test
    fun `a message dated in the future does not push the checkpoint past now`() = runTest {
        val (reconciler, checkpoint) = reconciler(this)
        inbox.messages += hdfc(100, at = now + DAY)
        reconciler.reconcile()
        assertThat(checkpoint.get()).isEqualTo(now)
    }

    @Test
    fun `without READ_SMS nothing is read`() = runTest {
        val (reconciler, checkpoint) = reconciler(this)
        inbox.readable = false
        assertThat(reconciler.reconcile()).isEqualTo(InboxReconciler.Result.NoPermission)
        assertThat(checkpoint.get()).isNull()
    }

    private fun pipeline() = IngestPipeline(PennyWiseSmsParser(), db) { now }

    private fun reconciler(scope: TestScope): Pair<InboxReconciler, IngestCheckpointStore> {
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            tmp.root.resolve("ingest.preferences_pb")
        }
        val checkpoint = IngestCheckpointStore(store)
        return InboxReconciler(inbox, checkpoint, pipeline()) { now } to checkpoint
    }

    private suspend fun amounts() = db.transactionDao().observeAllWithAccount().first().map {
        it.transaction.amountPaise
    }

    private fun hdfc(rupees: Int, at: Long) = RawSms(
        "CP-HDFCBK-S",
        "Rs.$rupees.00 debited from A/c XX8812 on 03-Nov-26 to grocer@upi (UPI Ref No 6301998877$rupees)",
        at,
    )

    private fun personal(at: Long) = RawSms("+919812345678", "call me", at)

    private class FakeInbox : SmsInboxSource {
        val messages = mutableListOf<RawSms>()
        var readable = true
        var lastAfter: Long? = null

        override fun canRead() = readable

        override suspend fun forEachSince(afterMillis: Long, action: suspend (RawSms) -> Unit) {
            lastAfter = afterMillis
            messages.filter { it.receivedAt > afterMillis }.sortedBy { it.receivedAt }.forEach { action(it) }
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
