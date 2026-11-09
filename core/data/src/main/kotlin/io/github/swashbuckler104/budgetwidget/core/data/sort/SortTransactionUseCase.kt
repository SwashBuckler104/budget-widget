package io.github.swashbuckler104.budgetwidget.core.data.sort

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import javax.inject.Inject

sealed interface SortResult {
    data object Done : SortResult

    data object TransactionNotFound : SortResult

    data object CategoryNotFound : SortResult

    /** Transactions point at leaves only (SCOPE.md F7). */
    data object NotALeaf : SortResult

    data object CategoryArchived : SortResult
}

/**
 * The one way a transaction gets sorted: from the Quick-Sort sheet now, and from notification
 * actions and widget chips later (SCOPE.md §5 `CategorizeUseCase`).
 */
class SortTransactionUseCase @Inject constructor(private val db: BudgetDatabase, private val clock: WallClock) {
    /**
     * Sorts [transactionId] into [categoryId]. With [rememberMerchant], the merchant's rule learns
     * the choice (SCOPE.md F8): same category -> confirmCount + 1, different -> reset to 1.
     */
    suspend fun categorize(
        transactionId: Long,
        categoryId: Long,
        note: String? = null,
        rememberMerchant: Boolean = true,
    ): SortResult = db.withTransaction {
        val tx = db.transactionDao().get(transactionId) ?: return@withTransaction SortResult.TransactionNotFound
        val category = db.categoryDao().get(categoryId) ?: return@withTransaction SortResult.CategoryNotFound
        if (category.archived) return@withTransaction SortResult.CategoryArchived
        if (db.categoryDao().childIds(categoryId).isNotEmpty()) return@withTransaction SortResult.NotALeaf

        val now = clock.nowMillis()
        db.transactionDao().categorize(transactionId, categoryId, cleanNote(note), auto = false, now = now)
        val key = tx.merchantKey
        if (rememberMerchant && key != null) {
            val rules = db.merchantRuleDao()
            rules.upsert(nextRule(rules.get(key), key, categoryId, now))
        }
        SortResult.Done
    }

    suspend fun ignore(transactionId: Long): SortResult {
        val updated = db.transactionDao().ignore(transactionId, IgnoreReason.USER, clock.nowMillis())
        return if (updated == 0) SortResult.TransactionNotFound else SortResult.Done
    }

    private fun cleanNote(note: String?): String? = note?.trim()?.take(MAX_NOTE)?.ifEmpty { null }

    companion object {
        const val MAX_NOTE = 500

        internal fun nextRule(current: MerchantRuleEntity?, key: String, categoryId: Long, now: Long) =
            MerchantRuleEntity(
                merchantKey = key,
                categoryId = categoryId,
                confirmCount = if (current?.categoryId == categoryId) current.confirmCount + 1 else 1,
                lastUsedAt = now,
            )
    }
}
