package io.github.swashbuckler104.budgetwidget.notifications.day

import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.data.startup.ProcessStartTask
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Re-arms the day alarms at every process start and whenever the nudge settings change. */
internal class DayTicksStartup @Inject constructor(
    private val scheduler: DayTickScheduler,
    private val settings: SettingsStore,
    private val days: DayCloseRepository,
    private val widgets: InboxWidgets,
) : ProcessStartTask {
    override fun start(scope: CoroutineScope) {
        scope.launch { settings.nudge.distinctUntilChanged().collect { scheduler.arm() } }
        // Catch up on days that ended while no process ran (alarms are gone after a reboot).
        scope.launch { if (days.closeFinishedDays() > 0) widgets.refreshNow() }
    }
}
