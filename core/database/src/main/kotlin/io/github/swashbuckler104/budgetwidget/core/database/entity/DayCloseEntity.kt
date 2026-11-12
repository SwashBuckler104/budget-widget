package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per local calendar day, written at midnight rollover (SCOPE.md F6). */
@Entity(tableName = "day_close")
data class DayCloseEntity(
    /** Local date, `YYYY-MM-DD`. */
    @PrimaryKey val date: String,
    /** Non-backfill transactions still waiting in the inbox when the day ended. */
    val pendingAtMidnight: Int,
    val closed: Boolean,
)
