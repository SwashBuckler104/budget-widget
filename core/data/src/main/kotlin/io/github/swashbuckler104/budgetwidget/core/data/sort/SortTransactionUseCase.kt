package io.github.swashbuckler104.budgetwidget.core.data.sort

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.model.IgnoreReason
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.accepts
import javax.inject.Inject

sealed interface SortResult {
    data object Done : SortResult

    data object TransactionNotFound : SortResult

    /** Asked to sort only a PENDING transaction, and it was already sorted elsewhere. */
    data object NotPending : SortResult

    data object CategoryNotFound : SortResult

    /** Transactions point at leaves only (SCOPE.md F7). */
    data object NotALeaf : SortResult

    data object CategoryArchived : SortResult

    /** The category's kind can't hold money moving this way (an income category for a payment). */
    data object KindMismatch : SortResult
}

/**
 * The one way a transaction gets sorted: from the Quick-Sort sheet, notification actions and
 * widget chips (SCOPE.md §5 `CategorizeUseCase`). Every successful sort is reported to the
 * [InboxEvents] listeners (for example to cancel the transaction's notification).
 *
 * `requirePending`: notifications and widgets act on a snapshot that can be stale (the user may
 * have sorted the transaction somewhere else since); they pass `true` so a stale tap is a no-op.
 */
class SortTransactionUseCase @Inject constructor(
    private val db: BudgetDatabase,
    private val clock: WallClock,
    private val events: InboxEvents,
) {
    /**
     * Sorts [transactionId] into [categoryId]. With [rememberMerchant], the merchant's rule learns
     * the choice (SCOPE.md F8): same category -> confirmCount + 1, different -> reset to 1.
     * With [always] (ADR-0062), the rule becomes an "always" rule and every earlier payment from
     * the merchant that fits the category is sorted there too.
     */
    suspend fun categorize(
        transactionId: Long,
        categoryId: Long,
        note: String? = null,
        rememberMerchant: Boolean = true,
        requirePending: Boolean = false,
        always: Boolean = false,
    ): SortResult {
        var alsoSorted = emptyList<Long>()
        val result = reported(transactionId) {
            db.withTransaction {
                categorizeLocked(transactionId, categoryId, note, rememberMerchant, requirePending).also {
                    if (it == SortResult.Done && always) alsoSorted = alwaysLocked(transactionId, categoryId)
                }
            }
        }
        alsoSorted.forEach { events.sorted(it) }
        return result
    }

    /**
     * "Always sort [merchantKey] into [categoryId]" from outside a sort (Purple's offer, the
     * merchant rules list). Returns how many of its payments moved; null when the category can't
     * take them (gone, archived or a group).
     */
    suspend fun always(merchantKey: String, categoryId: Long): Int? {
        val moved = db.withTransaction {
            val category = db.categoryDao().get(categoryId)
            if (category == null || category.archived || db.categoryDao().childIds(categoryId).isNotEmpty()) {
                return@withTransaction null
            }
            alwaysLocked(merchantKey, categoryId, exceptId = null)
        } ?: return null
        moved.pending.forEach { events.sorted(it) }
        return moved.count
    }

    /** Back to a learned rule: new payments from [merchantKey] wait in the inbox again. */
    suspend fun stopAlways(merchantKey: String) = db.merchantRuleDao().setAlways(merchantKey, false)

    /** What [alwaysLocked] changed: [count] payments moved, [pending] of them left the inbox. */
    internal class Moved(val count: Int, val pending: List<Long>)

    /** [alwaysLocked] for the merchant of [transactionId], just sorted; returns the ids that left the inbox. */
    internal suspend fun alwaysLocked(transactionId: Long, categoryId: Long): List<Long> {
        val key = db.transactionDao().get(transactionId)?.merchantKey ?: return emptyList()
        return alwaysLocked(key, categoryId, exceptId = transactionId).pending
    }

    /**
     * Makes [merchantKey] an "always [categoryId]" rule (ADR-0062) and sorts its other payments
     * there: those still in the inbox and those sorted elsewhere. Left alone: [exceptId], ignored
     * ones, possible duplicates still in the inbox, legs of a transfer between own accounts, and
     * money moving the way the category can't hold (a refund for an expense). Call inside
     * `withTransaction`; report [Moved.pending] with [InboxEvents.sorted] after the commit.
     */
    private suspend fun alwaysLocked(merchantKey: String, categoryId: Long, exceptId: Long?): Moved {
        val dao = db.transactionDao()
        val category = db.categoryDao().get(categoryId) ?: return Moved(0, emptyList())
        val now = clock.nowMillis()
        val rules = db.merchantRuleDao()
        val rule = rules.get(merchantKey) ?: nextRule(null, merchantKey, categoryId, now)
        rules.upsert(rule.copy(categoryId = categoryId, always = true))
        val moving = dao.byMerchantKey(merchantKey).filter { t ->
            t.id != exceptId &&
                t.status != TransactionStatus.IGNORED &&
                !(t.status == TransactionStatus.PENDING && t.duplicateOfId != null) &&
                t.linkedToId == null &&
                t.categoryId != categoryId &&
                category.kind.accepts(t.direction)
        }
        moving.forEach { dao.sortFromRule(it.id, categoryId, now) }
        return Moved(moving.size, moving.filter { it.status == TransactionStatus.PENDING }.map { it.id })
    }

    /**
     * [categorize] without its own transaction or event, for callers that sort as part of a larger
     * write ([TransactionEditor][io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEditor]):
     * call inside `withTransaction` and report [InboxEvents.sorted] after the commit.
     */
    internal suspend fun categorizeLocked(
        transactionId: Long,
        categoryId: Long,
        note: String?,
        rememberMerchant: Boolean,
        requirePending: Boolean,
    ): SortResult {
        val tx = db.transactionDao().get(transactionId) ?: return SortResult.TransactionNotFound
        if (requirePending && tx.status != TransactionStatus.PENDING) return SortResult.NotPending
        val category = db.categoryDao().get(categoryId) ?: return SortResult.CategoryNotFound
        if (category.archived) return SortResult.CategoryArchived
        if (db.categoryDao().childIds(categoryId).isNotEmpty()) return SortResult.NotALeaf
        if (!category.kind.accepts(tx.direction)) return SortResult.KindMismatch

        val now = clock.nowMillis()
        db.transactionDao().categorize(transactionId, categoryId, cleanNote(note), auto = false, now = now)
        val key = tx.merchantKey
        // A different category undoes an "always" rule; the same one keeps it (nextRule).
        if (rememberMerchant && key != null) {
            val rules = db.merchantRuleDao()
            rules.upsert(nextRule(rules.get(key), key, categoryId, now))
        }
        return SortResult.Done
    }

    /**
     * Takes [transactionId] out of the inbox without a category. [IgnoreReason.DUPLICATE] is the
     * "same payment" merge (SCOPE.md F2): the transaction stays stored but is never counted.
     */
    suspend fun ignore(
        transactionId: Long,
        reason: IgnoreReason = IgnoreReason.USER,
        requirePending: Boolean = false,
    ): SortResult = reported(transactionId) {
        db.withTransaction {
            val tx = db.transactionDao().get(transactionId) ?: return@withTransaction SortResult.TransactionNotFound
            if (requirePending && tx.status != TransactionStatus.PENDING) return@withTransaction SortResult.NotPending
            db.transactionDao().ignore(transactionId, reason, clock.nowMillis())
            SortResult.Done
        }
    }

    private suspend inline fun reported(transactionId: Long, block: () -> SortResult): SortResult =
        block().also { if (it == SortResult.Done) events.sorted(transactionId) }

    private fun cleanNote(note: String?): String? = note?.trim()?.take(MAX_NOTE)?.ifEmpty { null }

    companion object {
        const val MAX_NOTE = 500

        internal fun nextRule(current: MerchantRuleEntity?, key: String, categoryId: Long, now: Long) =
            MerchantRuleEntity(
                merchantKey = key,
                categoryId = categoryId,
                confirmCount = if (current?.categoryId == categoryId) current.confirmCount + 1 else 1,
                lastUsedAt = now,
                always = current?.categoryId == categoryId && current.always,
            )
    }
}
