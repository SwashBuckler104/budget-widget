package io.github.swashbuckler104.budgetwidget.core.data.day

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the widget's empty state and the evening nudge show. Money is paise. */
data class DayStatus(
    /** Non-backfill transactions waiting now: what stands between today and "closed". */
    val open: Int,
    val todaySpentPaise: Long,
    val streak: Int,
)

/**
 * Close the day (SCOPE.md §3, F6). A day is closed when no (non-backfill) transaction was waiting
 * in the inbox when it ended. Days are recorded once, after they end, from `createdAt`/`sortedAt`,
 * so it doesn't matter when this runs: a late or missed midnight run gives the same answer.
 * Nothing is sorted or deleted at midnight.
 */
@Singleton
class DayCloseRepository @Inject constructor(
    private val db: BudgetDatabase,
    private val settings: SettingsStore,
    private val clock: WallClock,
) {
    private val mutex = Mutex()

    /** The latest recorded day; changes whenever a day is recorded (to refresh streak displays). */
    fun observeLatestDay(): Flow<LocalDate?> = db.dayCloseDao().observeLatest().map { row ->
        row?.date?.let(LocalDate::parse)
    }

    /** Records every finished day not recorded yet. Idempotent. Returns how many it recorded. */
    suspend fun closeFinishedDays(zone: ZoneId = ZoneId.systemDefault()): Int = mutex.withLock {
        val today = today(zone)
        val since = settings.dayTrackingSince()?.let(LocalDate::ofEpochDay)
            ?: return@withLock 0.also { settings.setDayTrackingSince(today.toEpochDay()) }
        val dao = db.dayCloseDao()
        val days = DayMath.daysToClose(dao.latestDate()?.let(LocalDate::parse), since, today, MAX_CATCH_UP_DAYS)
        db.withTransaction {
            days.forEach { day ->
                val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val waiting = db.transactionDao().countWaitingAt(end)
                dao.insert(DayCloseEntity(date = day.toString(), pendingAtMidnight = waiting, closed = waiting == 0))
            }
        }
        days.size
    }

    /** Non-backfill transactions waiting now (cheap; no day bookkeeping). */
    suspend fun openCount(): Int = db.transactionDao().countOpen()

    /** Records finished days first, so the streak is right even before the midnight run. */
    suspend fun status(zone: ZoneId = ZoneId.systemDefault()): DayStatus {
        closeFinishedDays(zone)
        val today = today(zone)
        val open = db.transactionDao().countOpen()
        val closed = db.dayCloseDao().recent(MAX_STREAK_LOOKUP).associate { LocalDate.parse(it.date) to it.closed }
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return DayStatus(
            open = open,
            todaySpentPaise = db.transactionDao().spentBetween(start, end),
            streak = DayMath.streak(closed, today, todayClear = open == 0),
        )
    }

    private fun today(zone: ZoneId): LocalDate = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()

    companion object {
        /** After a longer gap (phone off for months) older days are simply not recorded. */
        const val MAX_CATCH_UP_DAYS = 62

        /** Streaks are shown up to this many days back; plenty for "🔥 N-day streak". */
        const val MAX_STREAK_LOOKUP = 3_660
    }
}
