package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.backup.DataTransfer
import io.github.swashbuckler104.budgetwidget.core.data.category.CategorySeeder
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsBodyRetention
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NudgeSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SmsRetention
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BudgetDatabase
    private val backfills = mutableListOf<Long>()
    private val now = 100L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private val sync = object : SmsSync {
        override fun schedulePeriodicReconcile() = Unit

        override fun reconcileNow() = Unit

        override fun backfill(sinceMillis: Long, fileUnderOtherBefore: Long?) {
            backfills += sinceMillis
        }
    }

    private object NoParser : SmsParser {
        override fun isKnownBankSender(sender: String) = false

        override fun parse(sms: RawSms): SmsParseResult = SmsParseResult.Unparsed(UnparsedReason.NO_MATCH)
    }

    private fun TestScope.viewModel(): Pair<SettingsViewModel, SettingsStore> {
        val store =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(tmp.root, "s.preferences_pb")
                },
            )
        val checkpoint = IngestCheckpointStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "i.preferences_pb") },
        )
        val pipeline = IngestPipeline(NoParser, db, { now }, { false }, InboxEvents(emptySet()))
        val transfer =
            DataTransfer(db, store, checkpoint, CategorySeeder(db, store), InboxEvents(emptySet()), {
                now
            }, pipeline, sync, NoParser)
        val repository = TransactionRepository(db.transactionDao(), db.unparsedSmsDao(), db.accountDao())
        val retention = SmsBodyRetention(db, store) { now }
        return SettingsViewModel(store, transfer, sync, { now }, context.contentResolver, retention, repository) to
            store
    }

    @Test
    fun `rupee input accepts blank and up to 9 digits only`() {
        assertThat(SettingsViewModel.parseRupees("")).isEqualTo(0)
        assertThat(SettingsViewModel.parseRupees("500")).isEqualTo(500)
        assertThat(SettingsViewModel.parseRupees("999999999")).isEqualTo(999_999_999)
        assertThat(SettingsViewModel.parseRupees("1000000000")).isNull()
        assertThat(SettingsViewModel.parseRupees("12.5")).isNull()
        assertThat(SettingsViewModel.parseRupees("-1")).isNull()
        assertThat(SettingsViewModel.parseRupees("१२")).isNull()
    }

    @Test
    fun `changes are stored and reflected in the state`() = runTest {
        val (viewModel, store) = viewModel()
        viewModel.state.test(timeout = IO_TIMEOUT) {
            assertThat(awaitItem().loaded).isFalse()
            assertThat(awaitItem()).isEqualTo(SettingsUiState(loaded = true))
            viewModel.setNotificationsEnabled(false)
            viewModel.setMinAmount("250")
            viewModel.setMinAmount("abc")
            viewModel.setAutoSort(true)
            viewModel.setNudgeEnabled(false)
            viewModel.setNudgeTime(20, 30)
            viewModel.setTheme(ThemeMode.DARK)
            viewModel.setDynamicColor(false)
            cancelAndIgnoreRemainingEvents()
        }
        // The writes hop onto DataStore's own scope; let them land before reading back.
        advanceUntilIdle()
        assertThat(
            store.notifications.first(),
        ).isEqualTo(NotificationSettings(enabled = false, minAmountPaise = 250_00))
        assertThat(store.autoSortEnabled()).isTrue()
        assertThat(store.nudge.first()).isEqualTo(NudgeSettings(enabled = false, minuteOfDay = 20 * 60 + 30))
        assertThat(store.appearance.first()).isEqualTo(AppearanceSettings(ThemeMode.DARK, dynamicColor = false))
    }

    @Test
    fun `a shorter SMS limit is shown and drops older SMS text right away`() = runTest {
        val (viewModel, store) = viewModel()
        val day = 24L * 60 * 60 * 1000
        val id = db.transactionDao().insert(
            TransactionEntity(
                txnHash = "old",
                source = TransactionSource.SMS,
                amountPaise = 100_00,
                direction = Direction.OUT,
                parserType = ParserType.EXPENSE,
                merchantRaw = null,
                merchantDisplay = null,
                merchantKey = null,
                accountId = null,
                occurredAt = now - 31 * day,
                smsSender = "AD-BANKSM",
                smsBody = "Rs 100 debited",
                reference = null,
                balanceAfterPaise = null,
                isCard = false,
                status = TransactionStatus.IGNORED,
                isBackfill = false,
                createdAt = 0,
                updatedAt = 0,
            ),
        )

        viewModel.setSmsRetention(SmsRetention.DAYS_30)

        assertThat(viewModel.state.first { it.smsRetention == SmsRetention.DAYS_30 }.loaded).isTrue()
        assertThat(store.smsRetention.first()).isEqualTo(SmsRetention.DAYS_30)
        withContext(Dispatchers.Default) {
            withTimeout(IO_TIMEOUT) {
                while (db.transactionDao().get(id)!!.smsBody != null) delay(10)
            }
        }
    }

    @Test
    fun `backup to a file and restore from it report back, and junk files change nothing`() = runTest {
        val (viewModel, _) = viewModel()
        val file = File(tmp.root, "backup.json")
        viewModel.messages.test(timeout = IO_TIMEOUT) {
            viewModel.prepareBackup(null)
            viewModel.backup(Uri.fromFile(file), "1.0.0")
            assertThat(awaitItem()).isEqualTo(SettingsMessage.BackedUp)
            assertThat(file.readText()).contains("\"format\":\"budget-widget-backup\"")

            viewModel.restore(Uri.fromFile(file))
            assertThat(awaitItem()).isEqualTo(SettingsMessage.Restored(0))

            val junk = File(tmp.root, "junk.json").apply { writeText("hello") }
            viewModel.restore(Uri.fromFile(junk))
            assertThat(awaitItem()).isEqualTo(SettingsMessage.NotABackup)

            viewModel.restore(Uri.fromFile(File(tmp.root, "missing.json")))
            assertThat(awaitItem()).isEqualTo(SettingsMessage.FileFailed)

            viewModel.startFresh()
            assertThat(awaitItem()).isEqualTo(SettingsMessage.StartedFresh(0))

            viewModel.importOlder(30)
            assertThat(awaitItem()).isEqualTo(SettingsMessage.ImportStarted(30))
        }
        assertThat(backfills).containsExactly(now - 30L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `a password-protected backup asks for its password on restore, again after a wrong one`() = runTest {
        val (viewModel, _) = viewModel()
        val file = File(tmp.root, "protected.json")
        viewModel.messages.test(timeout = IO_TIMEOUT) {
            viewModel.prepareBackup("long enough")
            viewModel.backup(Uri.fromFile(file), "1.0.3")
            assertThat(awaitItem()).isEqualTo(SettingsMessage.BackedUp)
            assertThat(file.readText()).contains("budget-widget-backup-encrypted")

            // A picker result without a prepared backup (the app was killed meanwhile) writes nothing.
            val orphan = File(tmp.root, "orphan.json")
            viewModel.backup(Uri.fromFile(orphan), "1.0.3")
            assertThat(awaitItem()).isEqualTo(SettingsMessage.BackupNotSaved)
            assertThat(orphan.exists()).isFalse()

            viewModel.restore(Uri.fromFile(file))
            viewModel.state.first { it.restorePrompt == RestorePrompt(wrong = false) && !it.busy }
            viewModel.restoreWithPassphrase("not it")
            viewModel.state.first { it.restorePrompt == RestorePrompt(wrong = true) && !it.busy }
            viewModel.restoreWithPassphrase("long enough")
            assertThat(awaitItem()).isEqualTo(SettingsMessage.Restored(0))
            // The prompt closes once the backup opened.
            viewModel.state.first { it.restorePrompt == null }
        }
    }

    private companion object {
        /**
         * Turbine waits in wall-clock time, and these flows read real files (DataStore) and a real
         * Room database on background threads. Under the full parallel test run the default 3 s
         * was sometimes too short; 20 s only matters when something is really stuck.
         */
        val IO_TIMEOUT = 20.seconds
    }
}
