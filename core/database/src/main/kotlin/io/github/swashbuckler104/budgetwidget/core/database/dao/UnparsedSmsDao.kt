package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedSmsDao {
    /** Ignores a body that is already stored (same `bodyHash`). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(entity: UnparsedSmsEntity): Long

    /** Keeps only the newest [max] rows. */
    @Query(
        """
        DELETE FROM unparsed_sms WHERE id NOT IN
          (SELECT id FROM unparsed_sms ORDER BY receivedAt DESC, id DESC LIMIT :max)
        """,
    )
    suspend fun trimTo(max: Int)

    @Transaction
    suspend fun insertCapped(entity: UnparsedSmsEntity) {
        insertOrIgnore(entity)
        trimTo(MAX_ROWS)
    }

    @Query("SELECT COUNT(*) FROM unparsed_sms")
    fun observeCount(): Flow<Int>

    /** Ids of the messages the parser couldn't read; not the ones the user reported a wrong amount for. */
    @Query("SELECT id FROM unparsed_sms WHERE reason != 'WRONG_AMOUNT'")
    fun observeUnreadIds(): Flow<List<Long>>

    /** Newest first. */
    @Query("SELECT * FROM unparsed_sms ORDER BY receivedAt DESC, id DESC")
    fun observeAll(): Flow<List<UnparsedSmsEntity>>

    @Query("SELECT * FROM unparsed_sms")
    suspend fun getAll(): List<UnparsedSmsEntity>

    @Query("UPDATE unparsed_sms SET body = :body WHERE id = :id")
    suspend fun updateBody(id: Long, body: String)

    @Query("SELECT * FROM unparsed_sms WHERE id = :id")
    suspend fun get(id: Long): UnparsedSmsEntity?

    @Query("DELETE FROM unparsed_sms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM unparsed_sms")
    suspend fun deleteAll()

    /** Entries received before [beforeMillis] (epoch ms, UTC; ADR-0045). */
    @Query("DELETE FROM unparsed_sms WHERE receivedAt < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int

    @Query("SELECT COUNT(*) FROM unparsed_sms")
    suspend fun count(): Int

    companion object {
        /** SCOPE.md F2: the unparsed list holds at most 200 messages. */
        const val MAX_ROWS = 200
    }
}
