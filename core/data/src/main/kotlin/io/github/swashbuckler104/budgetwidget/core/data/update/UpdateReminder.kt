package io.github.swashbuckler104.budgetwidget.core.data.update

import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * When to suggest looking for a newer version (ADR-0060). The app has no internet permission, so
 * it can't know a release exists; it asks once this version has been installed for
 * [REMIND_AFTER_DAYS] days, and again every [SNOOZE_DAYS] days after the user answers. Times are
 * epoch ms (UTC).
 */
class UpdateReminder @Inject constructor(private val settings: SettingsStore, private val clock: WallClock) {
    /** [installedAtMillis]: when this version was installed (`PackageInfo.lastUpdateTime`). */
    fun due(installedAtMillis: Long): Flow<Boolean> = settings.updateReminderSnoozedUntil.map { snoozedUntil ->
        val now = clock.nowMillis()
        now - installedAtMillis >= REMIND_AFTER_DAYS.days.inWholeMilliseconds && now >= snoozedUntil
    }

    /** Either answer (checked, or later) quiets it for [SNOOZE_DAYS] days. */
    suspend fun snooze() =
        settings.setUpdateReminderSnoozedUntil(clock.nowMillis() + SNOOZE_DAYS.days.inWholeMilliseconds)

    companion object {
        const val REMIND_AFTER_DAYS = 60
        const val SNOOZE_DAYS = 30
    }
}
