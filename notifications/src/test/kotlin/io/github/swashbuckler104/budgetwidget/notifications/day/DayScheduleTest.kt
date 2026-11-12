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
    fun `the next nudge is today's while still ahead and not sent, else tomorrow's`() {
        val nine = 21 * 60
        assertThat(DaySchedule.nextNudge(at(10, 12), nine, null)).isEqualTo(at(10, 21))
        assertThat(DaySchedule.nextNudge(at(10, 12), nine, LocalDate.of(2026, 11, 10))).isEqualTo(at(11, 21))
        // A time just picked that has already passed today never fires today.
        assertThat(DaySchedule.nextNudge(at(10, 21, 5), nine, null)).isEqualTo(at(11, 21))
    }

    @Test
    fun `re-arming keeps only a nudge that was armed, is due and hasn't run`() {
        val nine = 21 * 60
        // Process restarted at 21:05 while the 21:00 alarm was still pending: fire it now.
        assertThat(DaySchedule.rearmNudge(at(10, 21, 5), nine, null, armedAt = at(10, 21))).isEqualTo(at(10, 21))
        // Already ran today.
        assertThat(DaySchedule.rearmNudge(at(10, 21, 5), nine, LocalDate.of(2026, 11, 10), at(10, 21)))
            .isEqualTo(at(11, 21))
        // The user moved the time to 20:00 at 20:30 (armed for tomorrow): a restart keeps tomorrow.
        assertThat(DaySchedule.rearmNudge(at(10, 20, 35), 20 * 60, null, armedAt = at(11, 20))).isEqualTo(at(11, 20))
        // Nothing armed (first start after install).
        assertThat(DaySchedule.rearmNudge(at(10, 21, 5), nine, null, armedAt = null)).isEqualTo(at(11, 21))
        // Armed but over the grace hour.
        assertThat(DaySchedule.rearmNudge(at(10, 22, 30), nine, null, armedAt = at(10, 21))).isEqualTo(at(11, 21))
    }

    @Test
    fun `a DST gap moves a tick forward instead of dropping it`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 02:00-03:00 doesn't exist in Berlin; a 02:30 nudge happens at 03:30.
        val before = LocalDateTime.of(2026, 3, 29, 1, 0).atZone(berlin)
        assertThat(DaySchedule.nextNudge(before, 2 * 60 + 30, null).toLocalTime().toString()).isEqualTo("03:30")
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
