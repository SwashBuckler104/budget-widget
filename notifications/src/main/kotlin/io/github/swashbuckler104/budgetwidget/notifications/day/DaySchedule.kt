package io.github.swashbuckler104.budgetwidget.notifications.day

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/** When the day's alarms fire (SCOPE.md F4/F6). All times are local wall-clock times. */
internal object DaySchedule {
    /**
     * Widget refreshes: just after midnight (record the finished day, new "today") and on each hour
     * of the countdown (`~3 h left`, `~2 h left`, `~1 h left`).
     */
    val TICKS: List<LocalTime> =
        listOf(LocalTime.of(0, 5), LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(23, 0))

    /** A nudge may still go out this long after its time (alarm deferred, app restarted meanwhile). */
    val NUDGE_GRACE = 1.hours

    /** The first tick strictly after [now]. DST gaps shift a tick forward, never skip it. */
    fun nextTick(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        return (TICKS.map { at(today, it, now) } + at(today.plusDays(1), TICKS.first(), now)).first { it.isAfter(now) }
    }

    /**
     * Today's nudge while it is still due (not sent today and at most [NUDGE_GRACE] late; an alarm
     * set in the past fires at once), otherwise tomorrow's.
     */
    fun nextNudge(now: ZonedDateTime, minuteOfDay: Int, lastNudgeDay: LocalDate?): ZonedDateTime {
        val time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
        val today = now.toLocalDate()
        val target = at(today, time, now)
        val due = lastNudgeDay != today && now.isBefore(target.plus(NUDGE_GRACE.toJavaDuration()))
        return if (due) target else at(today.plusDays(1), time, now)
    }

    private fun at(day: LocalDate, time: LocalTime, now: ZonedDateTime): ZonedDateTime =
        day.atTime(time).atZone(now.zone)

    private const val MINUTES_PER_HOUR = 60
}
