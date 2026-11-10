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
        scope.launch {
            // The first value is the process start; later ones are the user changing the setting.
            var first = true
            settings.nudge.distinctUntilChanged().collect {
                scheduler.arm(userChange = !first)
                first = false
            }
        }
        scope.launch {
            // Catch up on days that ended while no process ran (alarms are gone after a reboot),
            // and redraw: re-arming may have replaced a tick that was still waiting (a non-wakeup
            // alarm held while the screen was off), whose countdown text would otherwise go stale.
            days.closeFinishedDays()
            widgets.refreshNow()
        }
    }
}
