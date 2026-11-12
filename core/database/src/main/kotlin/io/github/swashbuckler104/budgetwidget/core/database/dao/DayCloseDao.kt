package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayCloseDao {
    /** A day's row is written once and never changed (IGNORE keeps the first). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: DayCloseEntity)

    /** Latest recorded day, `YYYY-MM-DD` (sorts like a date). */
    @Query("SELECT MAX(date) FROM day_close")
    suspend fun latestDate(): String?

    /** Most recent days first. */
    @Query("SELECT * FROM day_close ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DayCloseEntity>

    @Query("SELECT * FROM day_close ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<DayCloseEntity?>
}
