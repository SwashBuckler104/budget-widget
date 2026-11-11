package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.backup.DataTransfer
import io.github.swashbuckler104.budgetwidget.core.data.backup.RestoreResult
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NudgeSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [loaded] false until the stored values arrive, so the form never flashes defaults. */
data class SettingsUiState(
    val loaded: Boolean = false,
    val notifications: NotificationSettings = NotificationSettings(),
    val nudge: NudgeSettings = NudgeSettings(),
    val autoSort: Boolean = false,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val unparsedCount: Int = 0,
    /** A backup, restore, export or reset is running: its buttons are disabled. */
    val busy: Boolean = false,
)

/** One-off results shown in a snackbar. */
sealed interface SettingsMessage {
    data object BackedUp : SettingsMessage

    data object Exported : SettingsMessage

    data class Restored(val transactions: Int) : SettingsMessage

    data object NotABackup : SettingsMessage

    data object BackupTooNew : SettingsMessage

    data object FileFailed : SettingsMessage

    data class StartedFresh(val removed: Int) : SettingsMessage

    data class ImportStarted(val days: Int) : SettingsMessage
}

/** The settings screen (SCOPE.md F9). File work goes through Storage Access Framework URIs. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val transfer: DataTransfer,
    private val sync: SmsSync,
    private val clock: WallClock,
    private val resolver: ContentResolver,
    transactions: TransactionRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = messageChannel.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        combine(settings.notifications, settings.nudge, settings.autoSort, ::Triple),
        settings.appearance,
        transactions.observeUnparsedCount(),
        busy,
    ) { (notifications, nudge, auto), appearance, unparsed, working ->
        SettingsUiState(
            loaded = true,
            notifications = notifications,
            nudge = nudge,
            autoSort = auto,
            appearance = appearance,
            unparsedCount = unparsed,
            busy = working,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setNotificationsEnabled(enabled: Boolean) = launch { settings.setNotificationsEnabled(enabled) }

    /** [rupees] is what the user typed: digits only, blank means 0. Anything else is ignored. */
    fun setMinAmount(rupees: String) {
        val value = parseRupees(rupees) ?: return
        launch { settings.setNotifyMinAmountPaise(value * PAISE_PER_RUPEE) }
    }

    fun setAutoSort(enabled: Boolean) = launch { settings.setAutoSort(enabled) }

    fun setNudgeEnabled(enabled: Boolean) = launch { settings.setNudgeEnabled(enabled) }

    /** [hour] 0-23 and [minute] 0-59, local time. */
    fun setNudgeTime(hour: Int, minute: Int) = launch { settings.setNudgeMinute(hour * MINUTES_PER_HOUR + minute) }

    fun setTheme(theme: ThemeMode) = launch { settings.setTheme(theme) }

    fun setDynamicColor(enabled: Boolean) = launch { settings.setDynamicColor(enabled) }

    /** Imports inbox SMS from the last [days] days (flagged as backfill, SCOPE.md F1/F9). */
    fun importOlder(days: Int) {
        sync.backfill(clock.nowMillis() - days.days.inWholeMilliseconds)
        messageChannel.trySend(SettingsMessage.ImportStarted(days))
    }

    fun backup(uri: Uri, appVersion: String) = fileWork {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
        output.use { transfer.backup(it, appVersion) }
        SettingsMessage.BackedUp
    }

    fun exportCsv(uri: Uri) = fileWork {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
        output.use { transfer.exportCsv(it) }
        SettingsMessage.Exported
    }

    fun restore(uri: Uri) = fileWork {
        val input = resolver.openInputStream(uri) ?: throw IOException("no stream")
        when (val result = input.use { transfer.restore(it) }) {
            is RestoreResult.Restored -> SettingsMessage.Restored(result.transactions)
            RestoreResult.NotABackup -> SettingsMessage.NotABackup
            is RestoreResult.TooNew -> SettingsMessage.BackupTooNew
        }
    }

    fun startFresh() = fileWork { SettingsMessage.StartedFresh(transfer.startFresh()) }

    /** Runs one data operation at a time; a second tap while one runs is ignored. */
    private fun fileWork(block: suspend () -> SettingsMessage) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val message = try {
                block()
            } catch (_: IOException) {
                SettingsMessage.FileFailed
            } catch (_: SecurityException) {
                // The document provider revoked access or refused the URI.
                SettingsMessage.FileFailed
            } finally {
                busy.value = false
            }
            messageChannel.send(message)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val PAISE_PER_RUPEE = 100L
        private const val MINUTES_PER_HOUR = 60

        /** Up to ₹99,99,99,999 (9 digits) so the paise value can never overflow. */
        const val MAX_RUPEE_DIGITS = 9

        internal fun parseRupees(text: String): Long? = when {
            text.isEmpty() -> 0
            text.length > MAX_RUPEE_DIGITS || !text.all { it in '0'..'9' } -> null
            else -> text.toLong()
        }
    }
}
