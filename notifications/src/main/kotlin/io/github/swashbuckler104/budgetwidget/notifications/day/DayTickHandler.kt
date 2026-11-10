package io.github.swashbuckler104.budgetwidget.notifications.day

import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.day.DayCloseRepository
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxWidgets
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What a day alarm (or a clock change) does. Every step is idempotent. */
internal class DayTickHandler @Inject constructor(
    private val scheduler: DayTickScheduler,
    private val days: DayCloseRepository,
    private val widgets: InboxWidgets,
    private val nudge: NudgeNotifier,
    private val settings: SettingsStore,
    private val clock: WallClock,
) {
    enum class Kind { TICK, NUDGE, CLOCK_CHANGED }

    suspend fun handle(kind: Kind) {
        if (kind == Kind.NUDGE && settings.nudge.first().enabled) {
            // Mark first: a re-arm must not schedule today's nudge again.
            val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
            settings.setLastNudgeEpochDay(today.toEpochDay())
            nudge.nudge()
        }
        scheduler.arm()
        days.closeFinishedDays()
        widgets.refreshNow()
    }
}
