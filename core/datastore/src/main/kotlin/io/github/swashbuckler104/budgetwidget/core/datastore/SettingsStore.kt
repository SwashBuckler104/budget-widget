package io.github.swashbuckler104.budgetwidget.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Transaction notifications (SCOPE.md F3). [minAmountPaise]: only transactions of at least this
 * amount (paise) notify; 0 notifies for every amount.
 */
data class NotificationSettings(val enabled: Boolean = true, val minAmountPaise: Long = 0)

/** User settings (SCOPE.md F9) plus one-time setup markers. */
@Singleton
class SettingsStore @Inject constructor(@SettingsPreferences private val dataStore: DataStore<Preferences>) {
    /** "Auto-sort merchants I've confirmed 3+ times" (SCOPE.md F8). Off by default. */
    val autoSort: Flow<Boolean> = dataStore.data.map { it[AUTO_SORT] ?: false }

    suspend fun autoSortEnabled(): Boolean = autoSort.first()

    suspend fun setAutoSort(enabled: Boolean) {
        dataStore.edit { it[AUTO_SORT] = enabled }
    }

    val notifications: Flow<NotificationSettings> = dataStore.data.map {
        NotificationSettings(
            enabled = it[NOTIFICATIONS_ENABLED] ?: true,
            minAmountPaise = it[NOTIFY_MIN_PAISE] ?: 0,
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    /** Negative amounts are stored as 0 (notify for everything). */
    suspend fun setNotifyMinAmountPaise(paise: Long) {
        dataStore.edit { it[NOTIFY_MIN_PAISE] = paise.coerceAtLeast(0) }
    }

    /** Version of the seed category set already inserted (0 = never), so deleted seeds stay deleted. */
    suspend fun seededCategoriesVersion(): Int = dataStore.data.map { it[SEED_VERSION] ?: 0 }.first()

    suspend fun setSeededCategoriesVersion(version: Int) {
        dataStore.edit { it[SEED_VERSION] = version }
    }

    private companion object {
        val AUTO_SORT = booleanPreferencesKey("auto_sort")
        val SEED_VERSION = intPreferencesKey("seeded_categories_version")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_MIN_PAISE = longPreferencesKey("notify_min_paise")
    }
}
