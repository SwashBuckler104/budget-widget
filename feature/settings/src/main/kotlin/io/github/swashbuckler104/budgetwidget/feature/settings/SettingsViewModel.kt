package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [loaded] false until the stored values arrive, so the form never flashes defaults. */
data class SettingsUiState(
    val loaded: Boolean = false,
    val notifications: NotificationSettings = NotificationSettings(),
    val autoSort: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(settings.notifications, settings.autoSort) { notifications, auto ->
        SettingsUiState(loaded = true, notifications = notifications, autoSort = auto)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setNotificationsEnabled(enabled: Boolean) = launch { settings.setNotificationsEnabled(enabled) }

    /** [rupees] is what the user typed: digits only, blank means 0. Anything else is ignored. */
    fun setMinAmount(rupees: String) {
        val value = parseRupees(rupees) ?: return
        launch { settings.setNotifyMinAmountPaise(value * PAISE_PER_RUPEE) }
    }

    fun setAutoSort(enabled: Boolean) = launch { settings.setAutoSort(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val PAISE_PER_RUPEE = 100L

        /** Up to ₹99,99,99,999 (9 digits) so the paise value can never overflow. */
        const val MAX_RUPEE_DIGITS = 9

        internal fun parseRupees(text: String): Long? = when {
            text.isEmpty() -> 0
            text.length > MAX_RUPEE_DIGITS || !text.all { it in '0'..'9' } -> null
            else -> text.toLong()
        }
    }
}
