package io.github.swashbuckler104.budgetwidget.core.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.category.CategorySeeder
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxListener
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOutcome
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantAliasEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataTransferTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val databases = mutableListOf<BudgetDatabase>()
    private val now = LocalDate.of(2026, 11, 11).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private var replaced = 0

    @After
    fun tearDown() = databases.forEach { it.close() }

    private var reconciles = 0
    private val sync = object : SmsSync {
        override fun schedulePeriodicReconcile() = Unit

        override fun reconcileNow() {
            reconciles++
        }

        override fun backfill(sinceMillis: Long) = Unit
    }

    /** Treats every sender as a bank and every message as unreadable: enough to reach the pipeline lock. */
    private object AnyBankParser : SmsParser {
        override fun isKnownBankSender(sender: String) = true

        override fun parse(sms: RawSms): SmsParseResult = SmsParseResult.Unparsed(UnparsedReason.NO_MATCH)
    }

    private class Env(
        val db: BudgetDatabase,
        val settings: SettingsStore,
        val checkpoint: IngestCheckpointStore,
        val transfer: DataTransfer,
        val pipeline: IngestPipeline,
    )

    private fun env(scope: TestScope, name: String): Env {
        val db = inMemoryDb().also { databases += it }
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                tmp.root.resolve("$name.preferences_pb")
            },
        )
        val checkpoint = IngestCheckpointStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                tmp.root.resolve("$name-i.preferences_pb")
            },
        )
        val listener = object : InboxListener {
            override suspend fun onDataReplaced() {
                replaced++
            }
        }
        val pipeline = IngestPipeline(AnyBankParser, db, { now }, { false }, InboxEvents(emptySet()))
        val transfer = DataTransfer(
            db,
            settings,
            checkpoint,
            CategorySeeder(db, settings),
            InboxEvents(setOf(listener)),
            { now },
            pipeline,
            sync,
        )
        return Env(db, settings, checkpoint, transfer, pipeline)
    }

    /** Seeded categories, an account, two transactions (one a duplicate of the other), rules and names. */
    private suspend fun Env.fill() {
        CategorySeeder(db, settings).ensureSeeded()
        val groceries = db.categoryDao().getAll().single { it.seedKey == "groceries" }.id
        val account = db.accountDao().insert(
            AccountEntity(
                bankName = "HDFC Bank",
                last4 = "1234",
                nickname = "Salary",
                kind = AccountKind.BANK,
                createdAt = 1,
            ),
        )
        val first = db.transactionDao().insertOrIgnore(
            testTxn("a", key = "dmart", at = now - 1000).copy(
                status = TransactionStatus.CATEGORIZED,
                categoryId = groceries,
                accountId = account,
                note = "=SUM(A1), \"weekly\"",
            ),
        )
        db.transactionDao().insertOrIgnore(testTxn("b", key = "dmart", at = now - 500).copy(duplicateOfId = first))
        db.merchantRuleDao().upsert(MerchantRuleEntity("dmart", groceries, 3, now))
        db.merchantAliasDao().upsert(MerchantAliasEntity("dmart", "DMart"))
        settings.setTheme(ThemeMode.DARK)
        settings.setAutoSort(true)
    }

    @Test
    fun `a backup restores into an empty install exactly`() = runTest {
        val source = env(this, "source")
        source.fill()
        val bytes = ByteArrayOutputStream().also { source.transfer.backup(it, "1.0.0") }.toByteArray()

        val target = env(this, "target")
        target.db.unparsedSmsDao().insertCapped(
            UnparsedSmsEntity(
                sender = "AD-X",
                bodyHash = "h",
                body = "b",
                receivedAt = 1,
                reason = UnparsedReason.NO_MATCH,
            ),
        )
        val result = target.transfer.restore(bytes.inputStream())

        assertThat(result).isEqualTo(RestoreResult.Restored(transactions = 2, categories = 58))
        val sourceDao = source.db.backupDao()
        val targetDao = target.db.backupDao()
        assertThat(targetDao.transactions()).isEqualTo(sourceDao.transactions())
        assertThat(targetDao.categories()).isEqualTo(sourceDao.categories())
        assertThat(targetDao.accounts()).isEqualTo(sourceDao.accounts())
        assertThat(targetDao.rules()).isEqualTo(sourceDao.rules())
        assertThat(targetDao.aliases()).isEqualTo(sourceDao.aliases())
        assertThat(target.db.unparsedSmsDao().count()).isEqualTo(0)
        assertThat(target.settings.appearance.first().theme).isEqualTo(ThemeMode.DARK)
        assertThat(target.settings.autoSortEnabled()).isTrue()
        assertThat(replaced).isEqualTo(1)
    }

    @Test
    fun `junk, other formats, newer versions and broken references change nothing`() = runTest {
        val target = env(this, "target")
        target.fill()
        val before = target.db.backupDao().transactions()

        assertThat(target.transfer.restore("not json".byteInputStream())).isEqualTo(RestoreResult.NotABackup)
        assertThat(
            target.transfer.restore("""{"format":"other"}""".byteInputStream()),
        ).isEqualTo(RestoreResult.NotABackup)
        val newer = """{"format":"budget-widget-backup","version":99,"exportedAt":0,"appVersion":"9","seedVersion":2,
            "categories":[],"accounts":[],"transactions":[],"merchantRules":[],"settings":{}}"""
        assertThat(target.transfer.restore(newer.byteInputStream())).isEqualTo(RestoreResult.TooNew(99))
        // A transaction pointing at a category the file doesn't have.
        val broken = """{"format":"budget-widget-backup","version":1,"exportedAt":0,"appVersion":"1","seedVersion":2,
            "categories":[],"accounts":[],"merchantRules":[],"settings":{},
            "transactions":[{"id":1,"source":"SMS","amountPaise":100,"direction":"OUT","occurredAt":0,
            "status":"CATEGORIZED","categoryId":999,"createdAt":0,"updatedAt":0}]}"""
        assertThat(target.transfer.restore(broken.byteInputStream())).isEqualTo(RestoreResult.NotABackup)

        assertThat(target.db.backupDao().transactions()).isEqualTo(before)
        assertThat(replaced).isEqualTo(0)
    }

    @Test
    fun `start fresh keeps categories and rules and stops older SMS coming back`() = runTest {
        val e = env(this, "fresh")
        e.fill()
        e.checkpoint.advanceTo(now - 60_000)
        val categories = e.db.backupDao().categories()

        assertThat(e.transfer.startFresh(ZoneOffset.UTC)).isEqualTo(2)

        assertThat(e.db.backupDao().transactions()).isEmpty()
        assertThat(e.db.backupDao().categories()).isEqualTo(categories)
        assertThat(e.db.backupDao().rules()).hasSize(1)
        assertThat(e.db.backupDao().accounts()).hasSize(1)
        assertThat(e.checkpoint.get()).isEqualTo(now)
        assertThat(e.checkpoint.floor()).isEqualTo(now)
        assertThat(e.settings.dayTrackingSince()).isEqualTo(LocalDate.of(2026, 11, 11).toEpochDay())
        assertThat(replaced).isEqualTo(1)
    }

    @Test
    fun `csv has signed rupees, category columns and escapes quotes and formulas`() = runTest {
        val e = env(this, "csv")
        e.fill()
        e.db.transactionDao().insertOrIgnore(
            testTxn("c", key = "employer", amount = 50_000_25, at = now, direction = Direction.IN),
        )
        val csv = ByteArrayOutputStream().also { e.transfer.exportCsv(it, ZoneOffset.UTC) }.toString(Charsets.UTF_8)
        val lines = csv.split("\r\n").filter { it.isNotEmpty() }

        assertThat(lines.first()).isEqualTo(
            "date,time,amount_inr,direction,status,category,subcategory,merchant,account,note,source,reference",
        )
        assertThat(lines).hasSize(4)
        assertThat(lines[1]).startsWith("2026-11-11,12:00,50000.25,IN,PENDING,")
        assertThat(lines.last()).isEqualTo(
            "2026-11-11,11:59,-100.00,OUT,CATEGORIZED,Food & Drinks,Groceries,dmart,Salary," +
                "\"'=SUM(A1), \"\"weekly\"\"\",SMS,",
        )
    }

    @Test
    fun `restore over existing data replaces it, rewinds the scan and asks for one`() = runTest {
        val source = env(this, "source")
        source.fill()
        source.db.dayCloseDao().insert(DayCloseEntity("2026-11-10", pendingAtMidnight = 0, closed = true))
        val bytes = ByteArrayOutputStream().also { source.transfer.backup(it, "1.0.0") }.toByteArray()

        val target = env(this, "target")
        target.fill()
        target.db.transactionDao().insertOrIgnore(testTxn("only-in-target", key = "zomato", at = now))
        target.checkpoint.startAt(now + 60_000)
        target.transfer.restore(bytes.inputStream())

        assertThat(target.db.backupDao().transactions().map { it.txnHash }).containsExactly("a", "b")
        assertThat(target.db.backupDao().dayCloses()).isEqualTo(source.db.backupDao().dayCloses())
        // The backup was made at `now`: SMS after it are scanned again, and the newer floor is gone.
        assertThat(target.checkpoint.get()).isEqualTo(now)
        assertThat(target.checkpoint.floor()).isNull()
        assertThat(reconciles).isEqualTo(1)
    }

    @Test
    fun `a scan that started before start fresh can't write into the new data`() = runTest {
        val e = env(this, "stale")
        val scanStartedAt = e.pipeline.generation()
        e.transfer.startFresh(ZoneOffset.UTC)

        val outcome = e.pipeline.ingest(
            RawSms("AD-HDFCBK", "old message", now - 1),
            IngestOrigin.BACKFILL,
            scanStartedAt,
        )
        assertThat(outcome).isEqualTo(IngestOutcome.Superseded)
        // A scan that starts afterwards (or a single new SMS) still goes through.
        val fresh = e.pipeline.ingest(RawSms("AD-HDFCBK", "new message", now), IngestOrigin.REALTIME)
        assertThat(fresh).isEqualTo(IngestOutcome.Unparsed(UnparsedReason.NO_MATCH))
    }
}
