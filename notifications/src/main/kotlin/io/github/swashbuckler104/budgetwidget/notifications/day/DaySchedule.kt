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

    /** The next nudge still ahead: today's if its time is still to come and it hasn't run, else tomorrow's. */
    fun nextNudge(now: ZonedDateTime, minuteOfDay: Int, lastNudgeDay: LocalDate?): ZonedDateTime {
        val time = nudgeTime(minuteOfDay)
        val today = now.toLocalDate()
        val target = at(today, time, now)
        return if (lastNudgeDay != today && now.isBefore(target)) target else at(today.plusDays(1), time, now)
    }

    /**
     * Where to arm the nudge when re-arming at process start or after a clock change. A nudge that
     * was armed ([armedAt]) and is now due but hasn't run (its alarm was pending when the process
     * died or was re-armed) is kept, so it fires at once; anything else gets [nextNudge]. Only an
     * alarm that was really armed qualifies, so a time the user moved into the past never fires.
     */
    fun rearmNudge(
        now: ZonedDateTime,
        minuteOfDay: Int,
        lastNudgeDay: LocalDate?,
        armedAt: ZonedDateTime?,
    ): ZonedDateTime {
        val pending = armedAt != null &&
            armedAt.toLocalDate() == now.toLocalDate() &&
            lastNudgeDay != now.toLocalDate() &&
            !armedAt.isAfter(now) &&
            isNudgeDue(now, minuteOfDay)
        return if (pending) armedAt else nextNudge(now, minuteOfDay, lastNudgeDay)
    }

    /**
     * Whether a nudge alarm firing at [now] may post: today's nudge time has passed by at most
     * [NUDGE_GRACE]. A later delivery (Doze, or a 23:55 nudge landing after midnight) belongs to a
     * day that is over or nearly so: "left to sort today" would be wrong, so it is skipped.
     */
    fun isNudgeDue(now: ZonedDateTime, minuteOfDay: Int): Boolean {
        val target = at(now.toLocalDate(), nudgeTime(minuteOfDay), now)
        return !now.isBefore(target) && now.isBefore(target.plus(NUDGE_GRACE.toJavaDuration()))
    }

    private fun nudgeTime(minuteOfDay: Int) = LocalTime.of(
        minuteOfDay / MINUTES_PER_HOUR,
        minuteOfDay % MINUTES_PER_HOUR,
    )

    private fun at(day: LocalDate, time: LocalTime, now: ZonedDateTime): ZonedDateTime =
        day.atTime(time).atZone(now.zone)

    private const val MINUTES_PER_HOUR = 60
}
