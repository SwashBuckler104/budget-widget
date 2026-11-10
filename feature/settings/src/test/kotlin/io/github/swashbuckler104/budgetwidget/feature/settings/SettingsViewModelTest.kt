package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.NudgeSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

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
        val store =
            SettingsStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(tmp.root, "s.preferences_pb")
                },
            )
        val viewModel = SettingsViewModel(store)
        viewModel.state.test {
            assertThat(awaitItem().loaded).isFalse()
            assertThat(awaitItem()).isEqualTo(SettingsUiState(loaded = true))
            viewModel.setNotificationsEnabled(false)
            viewModel.setMinAmount("250")
            viewModel.setMinAmount("abc")
            viewModel.setAutoSort(true)
            viewModel.setNudgeEnabled(false)
            viewModel.setNudgeTime(20, 30)
            cancelAndIgnoreRemainingEvents()
        }
        // The writes hop onto DataStore's own scope; let them land before reading back.
        advanceUntilIdle()
        assertThat(
            store.notifications.first(),
        ).isEqualTo(NotificationSettings(enabled = false, minAmountPaise = 250_00))
        assertThat(store.autoSortEnabled()).isTrue()
        assertThat(store.nudge.first()).isEqualTo(NudgeSettings(enabled = false, minuteOfDay = 20 * 60 + 30))
    }
}
