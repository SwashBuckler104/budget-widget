package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.model.TransactionWithAccount
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
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

    @Query(
        """
        SELECT t.*, a.bankName AS bankName, a.last4 AS accountLast4
        FROM transactions t LEFT JOIN account a ON a.id = t.accountId
        WHERE t.status = 'PENDING'
        ORDER BY t.occurredAt ASC, t.id ASC
        """,
    )
    fun observePending(): Flow<List<TransactionWithAccount>>

    @Query(
        """
        SELECT t.*, a.bankName AS bankName, a.last4 AS accountLast4
        FROM transactions t LEFT JOIN account a ON a.id = t.accountId
        WHERE t.id = :id
        """,
    )
    fun observeWithAccount(id: Long): Flow<TransactionWithAccount?>

    /** Marks [id] as sorted into [categoryId] (a leaf). Clears any earlier ignore. */
    @Query(
        """
        UPDATE transactions SET status = 'CATEGORIZED', categoryId = :categoryId, ignoreReason = NULL,
          autoCategorized = :auto, note = :note, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun categorize(id: Long, categoryId: Long, note: String?, auto: Boolean, now: Long): Int

    @Query(
        """
        UPDATE transactions SET status = 'IGNORED', ignoreReason = :reason, categoryId = NULL,
          autoCategorized = 0, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun ignore(id: Long, reason: IgnoreReason, now: Long): Int

    /** How often each category was used for [merchantKey], most used (then most recent) first. */
    @Query(
        """
        SELECT categoryId, COUNT(*) AS uses, MAX(updatedAt) AS lastUsed FROM transactions
        WHERE merchantKey = :merchantKey AND status = 'CATEGORIZED' AND categoryId IS NOT NULL
        GROUP BY categoryId ORDER BY uses DESC, lastUsed DESC
        """,
    )
    suspend fun usageForMerchant(merchantKey: String): List<CategoryUsage>

    /** Category usage for money moving in [direction] with an amount in [minPaise]..[maxPaise] since [sinceMillis]. */
    @Query(
        """
        SELECT categoryId, COUNT(*) AS uses, MAX(updatedAt) AS lastUsed FROM transactions
        WHERE status = 'CATEGORIZED' AND categoryId IS NOT NULL AND direction = :direction
          AND amountPaise BETWEEN :minPaise AND :maxPaise AND occurredAt >= :sinceMillis
        GROUP BY categoryId ORDER BY uses DESC, lastUsed DESC
        """,
    )
    suspend fun usageInAmountBand(
        direction: Direction,
        minPaise: Long,
        maxPaise: Long,
        sinceMillis: Long,
    ): List<CategoryUsage>

    @Query(
        """
        SELECT categoryId, COUNT(*) AS uses, MAX(updatedAt) AS lastUsed FROM transactions
        WHERE status = 'CATEGORIZED' AND categoryId IS NOT NULL AND direction = :direction
        GROUP BY categoryId ORDER BY uses DESC, lastUsed DESC
        """,
    )
    suspend fun usageForDirection(direction: Direction): List<CategoryUsage>
}

data class CategoryUsage(val categoryId: Long, val uses: Int, val lastUsed: Long)
