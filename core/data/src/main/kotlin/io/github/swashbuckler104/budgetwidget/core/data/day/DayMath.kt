package io.github.swashbuckler104.budgetwidget.core.data.day

import java.time.LocalDate
import java.time.LocalTime

/** Pure date arithmetic for closing days and streaks (SCOPE.md F6). */
object DayMath {
    /**
     * Days still to be recorded: from the day after [lastRecorded] (or [trackingSince]) through
     * yesterday, never before [trackingSince], and at most [maxDays] (the oldest are dropped: after
     * a long gap there is no streak to save anyway).
     */
    fun daysToClose(
        lastRecorded: LocalDate?,
        trackingSince: LocalDate,
        today: LocalDate,
        maxDays: Int,
    ): List<LocalDate> {
        val first = maxOf(lastRecorded?.plusDays(1) ?: trackingSince, trackingSince, today.minusDays(maxDays.toLong()))
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it < today }.toList()
    }

    /**
     * Consecutive closed days ending yesterday, plus today once its inbox is empty ([todayClear]):
     * clearing the inbox counts straight away; a new transaction takes today back out.
     * [closed] maps recorded days to whether they closed; a missing day breaks the streak.
     */
    fun streak(closed: Map<LocalDate, Boolean>, today: LocalDate, todayClear: Boolean): Int {
        var count = 0
        var day = today.minusDays(1)
        while (closed[day] == true) {
            count++
            day = day.minusDays(1)
        }
        return count + if (todayClear) 1 else 0
    }

    /** SCOPE.md F4: the widget counts down only in the day's last 3 hours. */
    const val COUNTDOWN_HOURS = 3

    /**
     * Whole hours left before midnight, rounded up, in the last [COUNTDOWN_HOURS] (21:00-23:59 ->
     * 3, 2, 1); null earlier ("closes at midnight"). Coarse on purpose: widgets can't tick, and
     * refreshes happen on the hour (see the day ticks in `:notifications`).
     */
    fun hoursLeft(now: LocalTime): Int? = (HOURS_PER_DAY - now.hour).takeIf { it <= COUNTDOWN_HOURS }

    private const val HOURS_PER_DAY = 24
}
