package io.github.swashbuckler104.budgetwidget.notifications.day

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class DayScheduleTest {
    private val india = ZoneId.of("Asia/Kolkata")

    private fun at(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = india): ZonedDateTime =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone)

    @Test
    fun `ticks run just after midnight and on each countdown hour`() {
        assertThat(DaySchedule.nextTick(at(10, 12))).isEqualTo(at(10, 21))
        assertThat(DaySchedule.nextTick(at(10, 21))).isEqualTo(at(10, 22))
        assertThat(DaySchedule.nextTick(at(10, 23, 30))).isEqualTo(at(11, 0, 5))
        assertThat(DaySchedule.nextTick(at(11, 0, 1))).isEqualTo(at(11, 0, 5))
    }

    @Test
    fun `the nudge fires today until sent or an hour late, then tomorrow`() {
        val nine = 21 * 60
        assertThat(DaySchedule.nextNudge(at(10, 12), nine, null)).isEqualTo(at(10, 21))
        // Re-armed at 21:05 before the alarm went off: still today (fires at once).
        assertThat(DaySchedule.nextNudge(at(10, 21, 5), nine, null)).isEqualTo(at(10, 21))
        assertThat(DaySchedule.nextNudge(at(10, 21, 5), nine, LocalDate.of(2026, 11, 10))).isEqualTo(at(11, 21))
        assertThat(DaySchedule.nextNudge(at(10, 22, 30), nine, null)).isEqualTo(at(11, 21))
    }

    @Test
    fun `a DST gap moves a tick forward instead of dropping it`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 02:00-03:00 doesn't exist in Berlin; a 02:30 nudge happens at 03:30.
        val before = LocalDateTime.of(2026, 3, 29, 1, 0).atZone(berlin)
        assertThat(DaySchedule.nextNudge(before, 2 * 60 + 30, null).toLocalTime().toString()).isEqualTo("03:30")
    }

    @Test
    fun `a time the user just picked counts only if it is still ahead`() {
        val eight = 20 * 60
        assertThat(DaySchedule.nextNudge(at(10, 20, 30), eight, null, allowLate = false)).isEqualTo(at(11, 20))
        assertThat(DaySchedule.nextNudge(at(10, 19, 30), eight, null, allowLate = false)).isEqualTo(at(10, 20))
    }

    @Test
    fun `a nudge may post only on its own day and within the grace hour`() {
        val nine = 21 * 60
        assertThat(DaySchedule.isNudgeDue(at(10, 20, 59), nine)).isFalse()
        assertThat(DaySchedule.isNudgeDue(at(10, 21, 0), nine)).isTrue()
        assertThat(DaySchedule.isNudgeDue(at(10, 21, 59), nine)).isTrue()
        assertThat(DaySchedule.isNudgeDue(at(10, 22, 0), nine)).isFalse()
        // A 23:55 nudge delivered at 00:02 belongs to a day that is over.
        assertThat(DaySchedule.isNudgeDue(at(11, 0, 2), 23 * 60 + 55)).isFalse()
    }
}
