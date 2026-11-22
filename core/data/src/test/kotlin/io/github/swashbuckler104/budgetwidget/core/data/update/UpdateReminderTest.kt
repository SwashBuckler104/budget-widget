package io.github.swashbuckler104.budgetwidget.core.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateReminderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private val day = 1.days.inWholeMilliseconds

    private fun reminder(scope: TestScope) = UpdateReminder(
        SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { tmp.root.resolve("s.preferences_pb") },
        ),
    ) { now }

    @Test
    fun `quiet for a fresh install, due once this version is 60 days old`() = runTest {
        val reminder = reminder(this)
        val installed = 1_000 * day
        now = installed + 59 * day
        assertThat(reminder.due(installed).first()).isFalse()
        now = installed + 60 * day
        assertThat(reminder.due(installed).first()).isTrue()
    }

    @Test
    fun `an answer quiets it for 30 days, an update resets the clock`() = runTest {
        val reminder = reminder(this)
        val installed = 1_000 * day
        now = installed + 70 * day
        reminder.snooze()
        now += 29 * day
        assertThat(reminder.due(installed).first()).isFalse()
        now += day
        assertThat(reminder.due(installed).first()).isTrue()
        // A newer version was installed just now.
        assertThat(reminder.due(now).first()).isFalse()
    }
}
