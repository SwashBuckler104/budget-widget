package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.model.TransactionWithAccount
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Returns the new row id, or -1 when a row with the same `txnHash` already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query(
        """
        SELECT t.*, a.bankName AS bankName, a.last4 AS accountLast4
        FROM transactions t LEFT JOIN account a ON a.id = t.accountId
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeAllWithAccount(): Flow<List<TransactionWithAccount>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * Earliest transaction that may be the same payment reported by another bank or app
     * (SCOPE.md F2): same amount and direction, within [fromMillis]..[toMillis], from an
     * account of a different bank, and not itself marked as a duplicate.
     */
    @Query(
        """
        SELECT t.* FROM transactions t JOIN account a ON a.id = t.accountId
        WHERE t.id != :excludeId
          AND t.amountPaise = :amountPaise
          AND t.direction = :direction
          AND t.occurredAt BETWEEN :fromMillis AND :toMillis
          AND a.bankName != :bankName
          AND t.duplicateOfId IS NULL
        ORDER BY t.occurredAt ASC, t.id ASC
        LIMIT 1
        """,
    )
    suspend fun findCrossSourceDuplicate(
        excludeId: Long,
        amountPaise: Long,
        direction: Direction,
        fromMillis: Long,
        toMillis: Long,
        bankName: String,
    ): TransactionEntity?

    @Query("UPDATE transactions SET duplicateOfId = :duplicateOfId WHERE id = :id")
    suspend fun setDuplicateOf(id: Long, duplicateOfId: Long)
}
