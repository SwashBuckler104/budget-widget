package io.github.swashbuckler104.budgetwidget.notifications.day

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first

/**
 * Arms the two day alarms with inexact `setWindow` (SCOPE.md F6: no exact alarms). Each alarm
 * re-arms the next one when it fires. Alarms don't survive a reboot or force-stop and we have no
 * boot receiver (ADR-0013), so every process start arms them again; arming is idempotent.
 */
internal class DayTickScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val clock: WallClock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /**
     * [userChange]: the user just changed the nudge settings, so only a time still ahead counts.
     * Otherwise (process start, alarms, clock changes) a nudge that was armed and is due is kept.
     */
    suspend fun arm(userChange: Boolean = false) {
        val now = Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.systemDefault())
        // Not a wakeup: widget text can wait until the screen is on.
        set(AlarmManager.RTC, DaySchedule.nextTick(now).toInstant().toEpochMilli(), DayTickReceiver.ACTION_TICK)
        val nudge = settings.nudge.first()
        if (nudge.enabled) {
            val last = settings.lastNudgeEpochDay()?.let(LocalDate::ofEpochDay)
            val armed = settings.armedNudgeAt()?.let { Instant.ofEpochMilli(it).atZone(now.zone) }
            val at = if (userChange) {
                DaySchedule.nextNudge(now, nudge.minuteOfDay, last)
            } else {
                DaySchedule.rearmNudge(now, nudge.minuteOfDay, last, armed)
            }
            val atMillis = at.toInstant().toEpochMilli()
            settings.setArmedNudgeAt(atMillis)
            // A wakeup: the reminder is meant to arrive at its time, screen on or off.
            set(AlarmManager.RTC_WAKEUP, atMillis, DayTickReceiver.ACTION_NUDGE)
        } else {
            settings.setArmedNudgeAt(null)
            alarms.cancel(pendingIntent(DayTickReceiver.ACTION_NUDGE))
        }
    }

    private fun set(type: Int, atMillis: Long, action: String) {
        alarms.setWindow(type, atMillis, WINDOW.inWholeMilliseconds, pendingIntent(action))
    }

    private fun pendingIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DayTickReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        /** Android 14+ widens shorter windows to 10 minutes anyway. */
        val WINDOW = 10.minutes
    }
}
