package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.database.model.TransactionRow
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

    /** Every transaction as a light row (no SMS body), newest first. */
    @Query(
        """
        SELECT $ROW_COLUMNS FROM transactions t LEFT JOIN account a ON a.id = t.accountId
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun observeRows(): Flow<List<TransactionRow>>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<TransactionEntity>

    /** A manual transaction (SMS ones go through [insertOrIgnore]). Returns the new row id. */
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /** Puts rows back exactly as they were (undo). Existing rows only; see [insert] for deleted ones. */
    @Update
    suspend fun update(rows: List<TransactionEntity>)

    /** Only manual transactions can be deleted; SMS ones are ignored instead (SCOPE.md F7). */
    @Query("DELETE FROM transactions WHERE id = :id AND source = 'MANUAL'")
    suspend fun deleteManual(id: Long): Int

    /** "Keep both" on a possible cross-bank duplicate (SCOPE.md F2). */
    @Query("UPDATE transactions SET duplicateOfId = NULL, updatedAt = :now WHERE id = :id")
    suspend fun clearDuplicate(id: Long, now: Long): Int

    /** Back to the inbox. `sortedAt` keeps its first value (closing a day is computed from it). */
    @Query(
        """
        UPDATE transactions SET status = 'PENDING', ignoreReason = NULL, updatedAt = :now
        WHERE id = :id AND status = 'IGNORED'
        """,
    )
    suspend fun unignore(id: Long, now: Long): Int

    /** "Apply to all from this merchant": renames every transaction with [merchantKey]. */
    @Query("UPDATE transactions SET merchantDisplay = :displayName, updatedAt = :now WHERE merchantKey = :merchantKey")
    suspend fun renameMerchant(merchantKey: String, displayName: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * Emits after every write to the table (Room re-runs the query on each invalidation, even if
     * the count is unchanged): a cheap change signal that doesn't load any rows.
     */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

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

    @Query(
        """
        SELECT t.*, a.bankName AS bankName, a.last4 AS accountLast4
        FROM transactions t LEFT JOIN account a ON a.id = t.accountId
        WHERE t.id = :id
        """,
    )
    suspend fun getWithAccount(id: Long): TransactionWithAccount?

    @Query("SELECT id FROM transactions WHERE status = 'PENDING'")
    suspend fun pendingIds(): List<Long>

    /** Marks [id] as sorted into [categoryId] (a leaf). Clears any earlier ignore. */
    @Query(
        """
        UPDATE transactions SET status = 'CATEGORIZED', categoryId = :categoryId, ignoreReason = NULL,
          autoCategorized = :auto, note = :note, updatedAt = :now, sortedAt = COALESCE(sortedAt, :now)
        WHERE id = :id
        """,
    )
    suspend fun categorize(id: Long, categoryId: Long, note: String?, auto: Boolean, now: Long): Int

    @Query(
        """
        UPDATE transactions SET status = 'IGNORED', ignoreReason = :reason, categoryId = NULL,
          autoCategorized = 0, updatedAt = :now, sortedAt = COALESCE(sortedAt, :now)
        WHERE id = :id
        """,
    )
    suspend fun ignore(id: Long, reason: IgnoreReason, now: Long): Int

    /**
     * Transactions that were waiting in the inbox at the instant [atMillis] (epoch ms): stored
     * before it and not yet sorted then. Backfilled history never counts (SCOPE.md F6).
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE isBackfill = 0 AND createdAt < :atMillis
          AND (status = 'PENDING' OR (sortedAt IS NOT NULL AND sortedAt >= :atMillis))
        """,
    )
    suspend fun countWaitingAt(atMillis: Long): Int

    /** The inbox that decides whether today closes: PENDING, backfill excluded. */
    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING' AND isBackfill = 0")
    suspend fun countOpen(): Int

    /**
     * Money spent in [fromMillis, toMillis): outgoing, not ignored, and not sorted into a transfer
     * category (moving money to your own accounts or investments isn't spending). Paise.
     */
    @Query(
        """
        SELECT COALESCE(SUM(t.amountPaise), 0) FROM transactions t LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.direction = 'OUT' AND t.status != 'IGNORED'
          AND t.occurredAt >= :fromMillis AND t.occurredAt < :toMillis
          AND (c.kind IS NULL OR c.kind != 'TRANSFER')
        """,
    )
    suspend fun spentBetween(fromMillis: Long, toMillis: Long): Long

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

/** Columns of [TransactionRow], for queries joining `transactions t` with `account a`. */
internal const val ROW_COLUMNS = """
    t.id, t.source, t.amountPaise, t.direction, t.parserType, t.merchantDisplay, t.merchantKey, t.accountId,
    t.occurredAt, t.isCard, t.status, t.ignoreReason, t.categoryId, t.autoCategorized, t.isBackfill, t.note,
    t.duplicateOfId, t.editedByUser, a.bankName AS bankName, a.last4 AS accountLast4
"""

data class CategoryUsage(val categoryId: Long, val uses: Int, val lastUsed: Long)
