package io.github.swashbuckler104.budgetwidget.core.data.sort

import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject

/** Whether new transactions from well-known merchants are sorted automatically (default off). */
fun interface AutoSortSetting {
    suspend fun enabled(): Boolean
}

internal class SettingsAutoSort @Inject constructor(private val settings: SettingsStore) : AutoSortSetting {
    override suspend fun enabled(): Boolean = settings.autoSortEnabled()
}
