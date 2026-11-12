package io.github.swashbuckler104.budgetwidget.core.data.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxListener
import io.github.swashbuckler104.budgetwidget.core.data.inbox.RecordingInboxListener
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.ParsedSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IngestPipelineTest {
    private lateinit var db: BudgetDatabase
    private val parser = FakeParser()
    private var autoSort = false
    private val listener = RecordingInboxListener()
    private lateinit var pipeline: IngestPipeline

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            ).build()
        pipeline = IngestPipeline(parser, db, { NOW }, { autoSort }, InboxEvents(setOf(listener)))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `messages from non-bank senders are never stored`() = runTest {
        val outcome = pipeline.ingest(RawSms("+919800000000", "see you at 7", 1), IngestOrigin.REALTIME)
        assertThat(outcome).isEqualTo(IngestOutcome.NotFinancial)
        assertThat(parser.parsed).isEmpty()
        assertThat(db.transactionDao().count()).isEqualTo(0)
        assertThat(db.unparsedSmsDao().count()).isEqualTo(0)
    }

    @Test
    fun `a transaction is stored once with account, keys and PENDING status`() = runTest {
        val sms = RawSms(BANK, "Rs 150.00 debited to swiggy.food@paytm", 1_000)
        parser.next = txn(hash = "h1", merchant = "swiggy.food@paytm", balance = 99_00)

        val first = pipeline.ingest(sms, IngestOrigin.REALTIME)
        val second = pipeline.ingest(sms, IngestOrigin.RECONCILE)

        assertThat(first).isInstanceOf(IngestOutcome.Inserted::class.java)
        assertThat(second).isEqualTo(IngestOutcome.AlreadyStored)
        val row = db.transactionDao().get((first as IngestOutcome.Inserted).id)!!
        assertThat(row.amountPaise).isEqualTo(15_000)
        assertThat(row.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(row.merchantKey).isEqualTo("swiggy food")
        assertThat(row.merchantDisplay).isEqualTo("swiggy.food@paytm")
        assertThat(row.smsBody).isEqualTo(sms.body)
        assertThat(row.isBackfill).isFalse()
        assertThat(row.createdAt).isEqualTo(NOW)
        assertThat(db.accountDao().find("HDFC Bank", "1234")?.lastBalancePaise).isEqualTo(99_00)
    }

    @Test
    fun `concurrent deliveries of the same SMS store one row`() = runTest {
        parser.next = txn(hash = "same")
        val outcomes = List(8) {
            async { pipeline.ingest(RawSms(BANK, "body", 1_000), IngestOrigin.REALTIME) }
        }.awaitAll()
        assertThat(outcomes.count { it is IngestOutcome.Inserted }).isEqualTo(1)
        assertThat(db.transactionDao().count()).isEqualTo(1)
    }

    @Test
    fun `backfill rows are flagged`() = runTest {
        parser.next = txn(hash = "old")
        val outcome = pipeline.ingest(RawSms(BANK, "body", 1_000), IngestOrigin.BACKFILL) as IngestOutcome.Inserted
        assertThat(db.transactionDao().get(outcome.id)!!.isBackfill).isTrue()
    }

    @Test
    fun `same payment from another bank within 10 minutes is linked, not merged`() = runTest {
        parser.next = txn(hash = "federal", bank = "Federal Bank", at = 1_000_000)
        val original = pipeline.ingest(RawSms(BANK, "a", 1_000_000), IngestOrigin.REALTIME) as IngestOutcome.Inserted
        parser.next = txn(hash = "jupiter", bank = "Jupiter", last4 = "9999", at = 1_000_000 + 9 * 60_000)
        val second = pipeline.ingest(RawSms(BANK, "b", 1_540_000), IngestOrigin.REALTIME) as IngestOutcome.Inserted
        parser.next = txn(hash = "late", bank = "Slice", last4 = null, at = 1_000_000 + 30 * 60_000)
        val unrelated = pipeline.ingest(RawSms(BANK, "c", 2_800_000), IngestOrigin.REALTIME) as IngestOutcome.Inserted

        assertThat(second.duplicateOfId).isEqualTo(original.id)
        assertThat(unrelated.duplicateOfId).isNull()
        assertThat(db.transactionDao().get(second.id)!!.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(db.transactionDao().count()).isEqualTo(3)
    }

    @Test
    fun `balance alerts update the account without creating a transaction`() = runTest {
        parser.next = SmsParseResult.BalanceUpdate("SBI", "4321", 1_234_56)
        assertThat(
            pipeline.ingest(RawSms(BANK, "Avl bal", 1), IngestOrigin.REALTIME),
        ).isEqualTo(IngestOutcome.BalanceUpdated)
        assertThat(db.transactionDao().count()).isEqualTo(0)
        assertThat(db.accountDao().find("SBI", "4321")?.lastBalancePaise).isEqualTo(1_234_56)
    }

    @Test
    fun `unparsed bank messages go to the capped list once`() = runTest {
        parser.next = SmsParseResult.Unparsed(UnparsedReason.NO_MATCH)
        repeat(2) { pipeline.ingest(RawSms(BANK, "Your OTP is 123456", 1), IngestOrigin.REALTIME) }
        repeat(210) { i -> pipeline.ingest(RawSms(BANK, "promo $i", 10L + i), IngestOrigin.RECONCILE) }
        assertThat(db.unparsedSmsDao().count()).isEqualTo(200)
        assertThat(db.transactionDao().observeRows().first()).isEmpty()
    }

    @Test
    fun `auto-sort uses rules confirmed 3+ times, only when enabled, never for possible duplicates`() = runTest {
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
        db.merchantRuleDao().upsert(MerchantRuleEntity("swiggy food", food, 3, 0))

        parser.next = txn(hash = "off", merchant = "swiggy.food@paytm")
        val off = pipeline.ingest(RawSms(BANK, "a", 1), IngestOrigin.REALTIME) as IngestOutcome.Inserted
        assertThat(off.autoSorted).isFalse()

        autoSort = true
        parser.next = txn(hash = "on", merchant = "swiggy.food@paytm", bank = "Axis Bank", at = 100_000_000)
        val on = pipeline.ingest(RawSms(BANK, "b", 2), IngestOrigin.REALTIME) as IngestOutcome.Inserted
        assertThat(on.autoSorted).isTrue()
        with(db.transactionDao().get(on.id)!!) {
            assertThat(status).isEqualTo(TransactionStatus.CATEGORIZED)
            assertThat(categoryId).isEqualTo(food)
            assertThat(autoCategorized).isTrue()
        }

        // Same amount from another bank within 10 minutes: a possible duplicate stays PENDING.
        parser.next =
            txn(hash = "dup", merchant = "swiggy.food@paytm", bank = "Slice", last4 = null, at = 100_000_000 + 60_000)
        val dup = pipeline.ingest(RawSms(BANK, "c", 3), IngestOrigin.REALTIME) as IngestOutcome.Inserted
        assertThat(dup.duplicateOfId).isEqualTo(on.id)
        assertThat(dup.autoSorted).isFalse()

        db.merchantRuleDao().upsert(MerchantRuleEntity("swiggy food", food, 2, 0))
        parser.next = txn(hash = "weak", merchant = "swiggy.food@paytm", at = 900_000_000)
        assertThat(
            (pipeline.ingest(RawSms(BANK, "d", 4), IngestOrigin.REALTIME) as IngestOutcome.Inserted).autoSorted,
        ).isFalse()
    }

    @Test
    fun `listeners hear about new pending rows only, after they are stored`() = runTest {
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
        db.merchantRuleDao().upsert(MerchantRuleEntity("swiggy food", food, 3, 0))
        autoSort = true
        val seen = mutableListOf<TransactionStatus?>()
        val checking = object : InboxListener {
            override suspend fun onPendingAdded(transactionId: Long, origin: IngestOrigin) {
                seen += db.transactionDao().get(transactionId)?.status
            }
        }
        val pipeline = IngestPipeline(parser, db, { NOW }, { autoSort }, InboxEvents(linkedSetOf(listener, checking)))

        parser.next = txn(hash = "new")
        val inserted = pipeline.ingest(RawSms(BANK, "a", 1), IngestOrigin.RECONCILE) as IngestOutcome.Inserted
        parser.next = txn(hash = "new")
        pipeline.ingest(RawSms(BANK, "a", 1), IngestOrigin.REALTIME)
        parser.next = txn(hash = "auto", merchant = "swiggy.food@paytm", at = 900_000_000)
        pipeline.ingest(RawSms(BANK, "b", 2), IngestOrigin.REALTIME)

        assertThat(listener.events).containsExactly("added:${inserted.id}:RECONCILE")
        assertThat(seen).containsExactly(TransactionStatus.PENDING)
    }

    private class FakeParser : SmsParser {
        var next: SmsParseResult = SmsParseResult.Unparsed(UnparsedReason.NO_MATCH)
        val parsed = mutableListOf<RawSms>()

        override fun isKnownBankSender(sender: String) = sender == BANK

        override fun parse(sms: RawSms): SmsParseResult {
            parsed += sms
            return next
        }
    }

    private fun txn(
        hash: String,
        merchant: String? = "Shop",
        bank: String = "HDFC Bank",
        last4: String? = "1234",
        at: Long = 1_000,
        balance: Long? = null,
    ) = SmsParseResult.Transaction(
        ParsedSms(
            txnHash = hash,
            amountPaise = 15_000,
            direction = Direction.OUT,
            parserType = ParserType.EXPENSE,
            isCard = false,
            merchant = merchant,
            reference = null,
            bankName = bank,
            accountLast4 = last4,
            balanceAfterPaise = balance,
            sender = BANK,
            body = "Rs 150.00 debited to swiggy.food@paytm",
            occurredAt = at,
        ),
    )

    private companion object {
        const val BANK = "AD-HDFCBK"
        const val NOW = 42L
    }
}
