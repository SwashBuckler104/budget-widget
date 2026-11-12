package io.github.swashbuckler104.budgetwidget.core.data.day

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class DayMathTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    @Test
    fun `days to close run from the last recorded day through yesterday`() {
        assertThat(DayMath.daysToClose(d(7), since(1), today, 62)).containsExactly(d(8), d(9)).inOrder()
        assertThat(DayMath.daysToClose(d(9), since(1), today, 62)).isEmpty()
        assertThat(DayMath.daysToClose(null, since(10), today, 62)).isEmpty()
        assertThat(DayMath.daysToClose(null, since(8), today, 62)).containsExactly(d(8), d(9)).inOrder()
    }

    @Test
    fun `catch-up is capped and never goes before tracking started`() {
        assertThat(DayMath.daysToClose(null, since(1), today, 3)).containsExactly(d(7), d(8), d(9)).inOrder()
        assertThat(DayMath.daysToClose(LocalDate.of(2026, 10, 1), since(9), today, 62)).containsExactly(d(9))
    }

    @Test
    fun `streak counts back from yesterday and adds a clear today`() {
        val closed = mapOf(d(9) to true, d(8) to true, d(7) to false, d(6) to true)
        assertThat(DayMath.streak(closed, today, todayClear = false)).isEqualTo(2)
        assertThat(DayMath.streak(closed, today, todayClear = true)).isEqualTo(3)
        assertThat(DayMath.streak(mapOf(d(8) to true), today, todayClear = false)).isEqualTo(0) // gap
        assertThat(DayMath.streak(emptyMap(), today, todayClear = true)).isEqualTo(1)
    }

    private fun since(day: Int) = d(day)

    @Test
    fun `countdown shows only in the last three hours`() {
        assertThat(DayMath.hoursLeft(LocalTime.of(20, 59))).isNull()
        assertThat(DayMath.hoursLeft(LocalTime.of(21, 0))).isEqualTo(3)
        assertThat(DayMath.hoursLeft(LocalTime.of(22, 30))).isEqualTo(2)
        assertThat(DayMath.hoursLeft(LocalTime.of(23, 59))).isEqualTo(1)
        assertThat(DayMath.hoursLeft(LocalTime.MIDNIGHT)).isNull()
    }
}
