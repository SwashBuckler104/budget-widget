package io.github.swashbuckler104.budgetwidget.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
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
}
