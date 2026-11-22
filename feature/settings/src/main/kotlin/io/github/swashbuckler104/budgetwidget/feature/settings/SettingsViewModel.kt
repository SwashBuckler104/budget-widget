package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.backup.DataTransfer
import io.github.swashbuckler104.budgetwidget.core.data.backup.RestoreResult
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsBodyRetention
import io.github.swashbuckler104.budgetwidget.core.data.ingest.SmsSync
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NudgeSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SmsRetention
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [loaded] false until the stored values arrive, so the form never flashes defaults. */
data class SettingsUiState(
    val loaded: Boolean = false,
    val notifications: NotificationSettings = NotificationSettings(),
    val nudge: NudgeSettings = NudgeSettings(),
    val autoSort: Boolean = false,
    /** Purple's tips (ADR-0063); its notices show either way. */
    val purpleTips: Boolean = true,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val secureScreen: Boolean = true,
    val smsRetention: SmsRetention = SmsRetention.FOREVER,
    val unparsedCount: Int = 0,
    /** A backup, restore, export or reset is running: its buttons are disabled. */
    val busy: Boolean = false,
    /** The chosen backup is password-protected: ask for the password (ADR-0044). */
    val restorePrompt: RestorePrompt? = null,
)

/** [wrong]: the last password tried didn't open the file. */
data class RestorePrompt(val wrong: Boolean = false)

/** One-off results shown in a snackbar. */
sealed interface SettingsMessage {
    data object BackedUp : SettingsMessage

    data object Exported : SettingsMessage

    data class Restored(val transactions: Int) : SettingsMessage

    data object NotABackup : SettingsMessage

    data object BackupTooNew : SettingsMessage

    data object BackupTooLarge : SettingsMessage

    data object FileFailed : SettingsMessage

    /** The app was closed while the file picker was open; the backup settings were lost. */
    data object BackupNotSaved : SettingsMessage

    data class StartedFresh(val removed: Int) : SettingsMessage

    data class ImportStarted(val days: Int) : SettingsMessage
}

private class PendingBackup(val passphrase: CharArray?) {
    fun clear() = passphrase?.fill(' ')
}

/** The settings screen (SCOPE.md F9). File work goes through Storage Access Framework URIs. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val transfer: DataTransfer,
    private val sync: SmsSync,
    private val clock: WallClock,
    private val resolver: ContentResolver,
    private val retention: SmsBodyRetention,
    transactions: TransactionRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)

    /** The password for the backup whose file picker is open; memory only, never saved state. */
    private var pendingBackup: PendingBackup? = null
    private var restoreUri: Uri? = null
    private val restorePrompt = MutableStateFlow<RestorePrompt?>(null)
    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = messageChannel.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        combine(settings.notifications, settings.nudge, settings.autoSort, settings.purpleTips) { n, d, a, p ->
            Triple(n, d, a to p)
        },
        settings.appearance,
        transactions.observeUnparsedCount(),
        combine(busy, restorePrompt, ::Pair),
        combine(settings.secureScreen, settings.smsRetention, ::Pair),
    ) { (notifications, nudge, sorting), appearance, unparsed, (working, prompt), (secure, keepSms) ->
        SettingsUiState(
            loaded = true,
            notifications = notifications,
            nudge = nudge,
            autoSort = sorting.first,
            purpleTips = sorting.second,
            appearance = appearance,
            secureScreen = secure,
            smsRetention = keepSms,
            unparsedCount = unparsed,
            busy = working,
            restorePrompt = prompt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setNotificationsEnabled(enabled: Boolean) = launch { settings.setNotificationsEnabled(enabled) }

    /** [rupees] is what the user typed: digits only, blank means 0. Anything else is ignored. */
    fun setMinAmount(rupees: String) {
        val value = parseRupees(rupees) ?: return
        launch { settings.setNotifyMinAmountPaise(value * PAISE_PER_RUPEE) }
    }

    fun setAutoSort(enabled: Boolean) = launch { settings.setAutoSort(enabled) }

    fun setPurpleTips(enabled: Boolean) = launch { settings.setPurpleTips(enabled) }

    fun setNudgeEnabled(enabled: Boolean) = launch { settings.setNudgeEnabled(enabled) }

    /** [hour] 0-23 and [minute] 0-59, local time. */
    fun setNudgeTime(hour: Int, minute: Int) = launch { settings.setNudgeMinute(hour * MINUTES_PER_HOUR + minute) }

    fun setTheme(theme: ThemeMode) = launch { settings.setTheme(theme) }

    fun setSecureScreen(enabled: Boolean) = launch { settings.setSecureScreen(enabled) }

    /** A shorter limit drops older SMS text right away, not at the next scan (ADR-0045). */
    fun setSmsRetention(keep: SmsRetention) = launch {
        settings.setSmsRetention(keep)
        retention.apply(keep)
    }

    fun setDynamicColor(enabled: Boolean) = launch { settings.setDynamicColor(enabled) }

    /** Imports inbox SMS from the last [days] days (flagged as backfill, SCOPE.md F1/F9). */
    fun importOlder(days: Int) {
        sync.backfill(clock.nowMillis() - days.days.inWholeMilliseconds)
        messageChannel.trySend(SettingsMessage.ImportStarted(days))
    }

    /**
     * Call before opening the file picker. A blank [passphrase] means a plain backup. Kept only in
     * memory: if the process dies while the picker is open, [backup] refuses rather than silently
     * writing an unprotected file.
     */
    fun prepareBackup(passphrase: String?) {
        pendingBackup?.clear()
        pendingBackup = PendingBackup(passphrase?.takeIf { it.isNotEmpty() }?.toCharArray())
    }

    /** The file picker was closed without choosing a file. */
    fun cancelBackup() {
        pendingBackup?.clear()
        pendingBackup = null
    }

    fun backup(uri: Uri, appVersion: String) {
        val pending = pendingBackup ?: run {
            messageChannel.trySend(SettingsMessage.BackupNotSaved)
            return
        }
        pendingBackup = null
        fileWork {
            try {
                openOutput(uri).use { transfer.backup(it, appVersion, pending.passphrase) }
            } finally {
                pending.clear()
            }
            SettingsMessage.BackedUp
        }
    }

    fun exportCsv(uri: Uri) = fileWork {
        openOutput(uri).use { transfer.exportCsv(it) }
        SettingsMessage.Exported
    }

    /** Truncating write; some document providers only accept plain "w" (a new file is empty anyway). */
    private fun openOutput(uri: Uri): OutputStream = try {
        resolver.openOutputStream(uri, "wt")
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } ?: resolver.openOutputStream(uri, "w") ?: throw IOException("no stream")

    fun restore(uri: Uri) {
        restoreUri = uri
        restorePrompt.value = null
        restoreFrom(uri, null)
    }

    /** The password for the backup that [SettingsUiState.restorePrompt] asks about. */
    fun restoreWithPassphrase(passphrase: String) {
        val uri = restoreUri ?: return
        restoreFrom(uri, passphrase.toCharArray())
    }

    fun cancelRestore() {
        restoreUri = null
        restorePrompt.value = null
    }

    private fun restoreFrom(uri: Uri, passphrase: CharArray?) = fileWork {
        val input = resolver.openInputStream(uri) ?: throw IOException("no stream")
        val result = try {
            input.use { transfer.restore(it, passphrase) }
        } finally {
            passphrase?.fill(' ')
        }
        when (result) {
            RestoreResult.NeedsPassphrase -> restorePrompt.value = RestorePrompt()
            RestoreResult.WrongPassphrase -> restorePrompt.value = RestorePrompt(wrong = true)
            else -> cancelRestore()
        }
        when (result) {
            is RestoreResult.Restored -> SettingsMessage.Restored(result.transactions)
            RestoreResult.NotABackup -> SettingsMessage.NotABackup
            is RestoreResult.TooNew -> SettingsMessage.BackupTooNew
            RestoreResult.TooLarge -> SettingsMessage.BackupTooLarge
            RestoreResult.NeedsPassphrase, RestoreResult.WrongPassphrase -> null
        }
    }

    fun startFresh() = fileWork { SettingsMessage.StartedFresh(transfer.startFresh()) }

    /** Runs one data operation at a time; a second tap while one runs is ignored. */
    private fun fileWork(block: suspend () -> SettingsMessage?) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val message = try {
                // Document providers do binder calls and may upload on close: never on the main thread.
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                SettingsMessage.FileFailed
            } catch (_: RuntimeException) {
                // A provider refusing the URI or mode (SecurityException, IllegalArgumentException,
                // UnsupportedOperationException...): nothing was changed.
                SettingsMessage.FileFailed
            } finally {
                busy.value = false
            }
            message?.let { messageChannel.send(it) }
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
