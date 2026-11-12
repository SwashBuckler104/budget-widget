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

/** Evening nudge (SCOPE.md F6). [minuteOfDay]: local time, minutes after midnight (21:00 = 1260). */
data class NudgeSettings(val enabled: Boolean = true, val minuteOfDay: Int = DEFAULT_NUDGE_MINUTE) {
    companion object {
        const val DEFAULT_NUDGE_MINUTE = 21 * 60
        const val MINUTES_PER_DAY = 24 * 60
    }
}

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

    val nudge: Flow<NudgeSettings> = dataStore.data.map {
        NudgeSettings(
            enabled = it[NUDGE_ENABLED] ?: true,
            minuteOfDay = it[NUDGE_MINUTE] ?: NudgeSettings.DEFAULT_NUDGE_MINUTE,
        )
    }

    suspend fun setNudgeEnabled(enabled: Boolean) {
        dataStore.edit { it[NUDGE_ENABLED] = enabled }
    }

    /** Out-of-range values wrap into one day. */
    suspend fun setNudgeMinute(minuteOfDay: Int) {
        dataStore.edit { it[NUDGE_MINUTE] = minuteOfDay.mod(NudgeSettings.MINUTES_PER_DAY) }
    }

    /** Local day (epoch day) the nudge last ran, so a re-armed alarm never nudges twice a day. */
    suspend fun lastNudgeEpochDay(): Long? = dataStore.data.map { it[LAST_NUDGE_DAY] }.first()

    suspend fun setLastNudgeEpochDay(epochDay: Long) {
        dataStore.edit { it[LAST_NUDGE_DAY] = epochDay }
    }

    /**
     * When the nudge alarm was last armed for (epoch ms), or null when none is armed. Lets a
     * process restart re-arm exactly the nudge that was pending, rather than any recent time.
     */
    suspend fun armedNudgeAt(): Long? = dataStore.data.map { it[ARMED_NUDGE_AT] }.first()

    suspend fun setArmedNudgeAt(epochMillis: Long?) {
        dataStore.edit { if (epochMillis == null) it.remove(ARMED_NUDGE_AT) else it[ARMED_NUDGE_AT] = epochMillis }
    }

    /**
     * First local day (epoch day) that day-closing tracks. Set on first use, so installing or
     * upgrading never judges days from before the feature existed.
     */
    suspend fun dayTrackingSince(): Long? = dataStore.data.map { it[DAY_TRACKING_SINCE] }.first()

    suspend fun setDayTrackingSince(epochDay: Long) {
        dataStore.edit { it[DAY_TRACKING_SINCE] = epochDay }
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
        val NUDGE_ENABLED = booleanPreferencesKey("nudge_enabled")
        val NUDGE_MINUTE = intPreferencesKey("nudge_minute_of_day")
        val LAST_NUDGE_DAY = longPreferencesKey("last_nudge_epoch_day")
        val DAY_TRACKING_SINCE = longPreferencesKey("day_tracking_since_epoch_day")
        val ARMED_NUDGE_AT = longPreferencesKey("armed_nudge_at")
    }
}
