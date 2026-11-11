package io.github.swashbuckler104.budgetwidget.core.data.analysis

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How a period's chart is split: one bar per day, or per calendar month. */
enum class BucketUnit { DAY, MONTH }

/**
 * The span of local dates the Analysis tab looks at (SCOPE.md F7, owner's request on 2026-11-11):
 * a week, a month, a year or a custom range. [start] and [endInclusive] are local dates.
 */
sealed interface AnalysisPeriod {
    val start: LocalDate
    val endInclusive: LocalDate

    /** The period just before this one, of the same kind and length; what it's compared with. */
    fun previous(): AnalysisPeriod

    /** The period [steps] periods later (earlier when negative); null for a custom range. */
    fun shift(steps: Long): AnalysisPeriod?

    val unit: BucketUnit get() = BucketUnit.DAY

    /** A string that [decode] turns back into this period, for saved state. */
    fun encode(): String

    val days: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    /** A Monday-to-Sunday week. */
    data class Week(override val start: LocalDate) : AnalysisPeriod {
        init {
            require(start.dayOfWeek == DayOfWeek.MONDAY) { "a week starts on Monday" }
        }

        override val endInclusive: LocalDate get() = start.plusDays(DAYS_PER_WEEK - 1)

        override fun previous() = Week(start.minusWeeks(1))

        override fun shift(steps: Long) = Week(start.plusWeeks(steps))

        override fun encode() = "W:$start"

        companion object {
            fun of(date: LocalDate) = Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        }
    }

    data class Month(val month: YearMonth) : AnalysisPeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val endInclusive: LocalDate get() = month.atEndOfMonth()

        override fun previous() = Month(month.minusMonths(1))

        override fun shift(steps: Long) = Month(month.plusMonths(steps))

        override fun encode() = "M:$month"

        companion object {
            fun of(date: LocalDate) = Month(YearMonth.from(date))
        }
    }

    data class Year(val year: Int) : AnalysisPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val endInclusive: LocalDate get() = LocalDate.of(year, 12, 31)
        override val unit: BucketUnit get() = BucketUnit.MONTH

        override fun previous() = Year(year - 1)

        override fun shift(steps: Long) = Year(Math.toIntExact(year + steps))

        override fun encode() = "Y:$year"
    }

    /** Any range of whole days; long ranges are charted by month. */
    data class Custom(override val start: LocalDate, override val endInclusive: LocalDate) : AnalysisPeriod {
        init {
            require(!endInclusive.isBefore(start)) { "a range ends on or after its start" }
        }

        override val unit: BucketUnit get() = if (days > MAX_DAILY_DAYS) BucketUnit.MONTH else BucketUnit.DAY

        override fun previous() = Custom(start.minusDays(days.toLong()), start.minusDays(1))

        override fun shift(steps: Long): AnalysisPeriod? = null

        override fun encode() = "C:$start:$endInclusive"
    }

    companion object {
        private const val DAYS_PER_WEEK = 7L

        /** Longer custom ranges get one bar per month instead of one per day. */
        const val MAX_DAILY_DAYS = 92

        /** The inverse of [encode]; null for anything it didn't produce. */
        fun decode(text: String): AnalysisPeriod? = runCatching {
            val parts = text.split(':')
            when (parts.first()) {
                "W" -> Week(LocalDate.parse(parts[1]))
                "M" -> Month(YearMonth.parse(parts[1]))
                "Y" -> Year(parts[1].toInt())
                "C" -> Custom(LocalDate.parse(parts[1]), LocalDate.parse(parts[2]))
                else -> null
            }
        }.getOrNull()
    }
}
