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
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NudgeSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

        override fun backfill(sinceMillis: Long) {
            backfills += sinceMillis
        }
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
        val transfer = DataTransfer(db, store, checkpoint, CategorySeeder(db, store), InboxEvents(emptySet())) { now }
        val repository = TransactionRepository(db.transactionDao(), db.unparsedSmsDao(), db.accountDao())
        return SettingsViewModel(store, transfer, sync, { now }, context.contentResolver, repository) to store
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
        viewModel.state.test {
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
    fun `backup to a file and restore from it report back, and junk files change nothing`() = runTest {
        val (viewModel, _) = viewModel()
        val file = File(tmp.root, "backup.json")
        viewModel.messages.test {
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
}
