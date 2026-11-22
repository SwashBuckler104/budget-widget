package io.github.swashbuckler104.budgetwidget.core.data.sort

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.category.OtherSeeds
import io.github.swashbuckler104.budgetwidget.core.data.category.TransferSeeds
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import javax.inject.Inject

/**
 * Files the inbox under the catch-all categories in one go (ADR-0058): payments go to Other, money
 * coming in to Income > Other, and what the parser read as a transfer (a wallet top-up) to
 * Transfers > Between my accounts, so it never counts as spending or income. Meant for a whole
 * imported history the user doesn't want to sort one by one; they can re-sort any of it later.
 *
 * Merchant rules don't learn from it (Other says nothing about the merchant), possible duplicates
 * stay in the inbox (they need a look), and a direction whose category was deleted, archived or
 * given subcategories stays in the inbox too.
 */
class BulkSorter @Inject constructor(
    private val db: BudgetDatabase,
    private val sort: SortTransactionUseCase,
    private val events: InboxEvents,
) {
    /** Returns how many transactions were filed. With [onlyBackfill], only imported history. */
    suspend fun fileUnderOther(onlyBackfill: Boolean): Int {
        val filed = db.withTransaction {
            val categories = db.categoryDao()
            val target = mapOf(
                Direction.OUT to categories.bySeedKey(OtherSeeds.OTHER)?.id,
                Direction.IN to categories.bySeedKey(OtherSeeds.OTHER_INCOME)?.id,
            )
            val transfer = categories.bySeedKey(TransferSeeds.OWN_TRANSFER)?.id
            db.transactionDao().bulkFilable(onlyBackfill).mapNotNull { pending ->
                val categoryId =
                    (if (pending.parserType == ParserType.TRANSFER) transfer else target[pending.direction])
                        ?: return@mapNotNull null
                val result = sort.categorizeLocked(
                    transactionId = pending.id,
                    categoryId = categoryId,
                    note = null,
                    rememberMerchant = false,
                    requirePending = true,
                )
                pending.id.takeIf { result == SortResult.Done }
            }
        }
        // After the commit, like every other sort: cancels notifications, refreshes widgets.
        filed.forEach { events.sorted(it) }
        return filed.size
    }
}
