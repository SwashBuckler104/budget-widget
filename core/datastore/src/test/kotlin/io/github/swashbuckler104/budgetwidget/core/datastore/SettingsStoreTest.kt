package io.github.swashbuckler104.budgetwidget.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `auto-sort is off by default and seeding starts at version 0`() = runTest {
        val store =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    tmp.root.resolve("settings.preferences_pb")
                },
            )
        assertThat(store.autoSortEnabled()).isFalse()
        assertThat(store.seededCategoriesVersion()).isEqualTo(0)
        store.setAutoSort(true)
        store.setSeededCategoriesVersion(1)
        assertThat(store.autoSortEnabled()).isTrue()
        assertThat(store.seededCategoriesVersion()).isEqualTo(1)
    }

    @Test
    fun `notifications are on for every amount by default`() = runTest {
        val store =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    tmp.root.resolve("settings.preferences_pb")
                },
            )
        assertThat(store.notifications.first()).isEqualTo(NotificationSettings(enabled = true, minAmountPaise = 0))
        store.setNotificationsEnabled(false)
        store.setNotifyMinAmountPaise(500_00)
        assertThat(
            store.notifications.first(),
        ).isEqualTo(NotificationSettings(enabled = false, minAmountPaise = 500_00))
        store.setNotifyMinAmountPaise(-1)
        assertThat(store.notifications.first().minAmountPaise).isEqualTo(0)
    }

    @Test
    fun `nudge defaults to 21_00 and day markers start empty`() = runTest {
        val store =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    tmp.root.resolve("settings.preferences_pb")
                },
            )
        assertThat(store.nudge.first()).isEqualTo(NudgeSettings(enabled = true, minuteOfDay = 1260))
        assertThat(store.lastNudgeEpochDay()).isNull()
        assertThat(store.dayTrackingSince()).isNull()
        store.setNudgeEnabled(false)
        store.setNudgeMinute(24 * 60 + 30)
        store.setLastNudgeEpochDay(20_000)
        store.setDayTrackingSince(19_999)
        assertThat(store.nudge.first()).isEqualTo(NudgeSettings(enabled = false, minuteOfDay = 30))
        assertThat(store.lastNudgeEpochDay()).isEqualTo(20_000)
        assertThat(store.dayTrackingSince()).isEqualTo(19_999)
    }
}
