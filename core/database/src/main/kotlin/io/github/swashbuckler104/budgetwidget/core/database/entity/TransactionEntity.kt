package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus

/**
 * SCOPE.md §5 `transaction`. The table is named `transactions` because `TRANSACTION` is an SQL
 * keyword. Money columns are paise; time columns are epoch ms (UTC). [smsBody] is only ever
 * set for financial messages (a parsed transaction), never for personal SMS.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["duplicateOfId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["txnHash"], unique = true),
        Index(value = ["status", "occurredAt"]),
        Index(value = ["categoryId", "occurredAt"]),
        Index(value = ["merchantKey"]),
        Index(value = ["accountId"]),
        Index(value = ["duplicateOfId"]),
        Index(value = ["amountPaise", "occurredAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Idempotency key for SMS transactions; null for manual ones. */
    val txnHash: String?,
    val source: TransactionSource,
    val amountPaise: Long,
    val direction: Direction,
    val parserType: ParserType?,
    val merchantRaw: String?,
    val merchantDisplay: String?,
    val merchantKey: String?,
    val accountId: Long?,
    val occurredAt: Long,
    val smsSender: String?,
    val smsBody: String?,
    val reference: String?,
    val balanceAfterPaise: Long?,
    val isCard: Boolean,
    val status: TransactionStatus,
    val ignoreReason: IgnoreReason? = null,
    val categoryId: Long? = null,
    val autoCategorized: Boolean = false,
    val isBackfill: Boolean,
    val note: String? = null,
    val duplicateOfId: Long? = null,
    val editedByUser: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * When the transaction first left the inbox (categorized or ignored); null while PENDING. With
     * [createdAt] it tells whether the transaction was waiting in the inbox at any past instant,
     * which is what closing a day needs (SCOPE.md F6). Edits later don't move it.
     */
    val sortedAt: Long? = null,
)
