package io.github.swashbuckler104.budgetwidget.core.data.account

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.TransferSeeds
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What each account held when tracking started (ADR-0059): a baseline, never income. Each account
 * has at most one manual transaction in Transfers > Opening balance (a TRANSFER category, so every
 * total already leaves it out), keyed by `txnHash`, and setting it also sets the account's balance.
 * Amounts are paise; negative means money owed (a credit card's dues).
 */
class OpeningBalances @Inject constructor(
    private val db: BudgetDatabase,
    private val settings: SettingsStore,
    private val clock: WallClock,
) {
    /** Account id to opening balance, for accounts that have one. */
    fun observe(): Flow<Map<Long, Long>> =
        db.transactionDao().observeOpeningBalances().map { rows -> rows.associate { it.accountId to it.balancePaise } }

    /**
     * Whether the inbox asks for starting balances: once an account is known (they appear with the
     * first bank SMS, so a Start-fresh install asks later), none has one yet, and it wasn't dismissed.
     */
    fun promptVisible(): Flow<Boolean> = combine(
        db.accountDao().observeAll(),
        observe(),
        settings.openingBalancePromptDismissed,
    ) { accounts, openings, dismissed -> !dismissed && openings.isEmpty() && accounts.any { !it.hidden } }
        .distinctUntilChanged()

    suspend fun dismissPrompt() = settings.dismissOpeningBalancePrompt()

    /** Sets [accountId]'s opening balance, or removes it when [balancePaise] is null. */
    suspend fun set(accountId: Long, balancePaise: Long?) = db.withTransaction {
        val dao = db.transactionDao()
        val existing = dao.byHash(hash(accountId))
        val now = clock.nowMillis()
        when {
            balancePaise == null -> existing?.let { dao.deleteManual(it.id) }

            existing != null -> dao.updateAmount(existing.id, balancePaise.absoluteValue, direction(balancePaise), now)

            else -> {
                val category = category()
                dao.insertOrIgnore(
                    TransactionEntity(
                        txnHash = hash(accountId),
                        source = TransactionSource.MANUAL,
                        amountPaise = balancePaise.absoluteValue,
                        direction = direction(balancePaise),
                        parserType = null,
                        merchantRaw = null,
                        // The category's own (renamable, localizable) name, not a string from here.
                        merchantDisplay = category.name,
                        merchantKey = null,
                        accountId = accountId,
                        occurredAt = now,
                        smsSender = null,
                        smsBody = null,
                        reference = null,
                        balanceAfterPaise = balancePaise,
                        isCard = false,
                        status = TransactionStatus.CATEGORIZED,
                        categoryId = category.id,
                        isBackfill = false,
                        editedByUser = true,
                        createdAt = now,
                        updatedAt = now,
                        sortedAt = now,
                    ),
                )
            }
        }
        if (balancePaise != null) db.accountDao().updateBalance(accountId, balancePaise)
    }

    /**
     * The seeded category (even archived: that only hides it from pickers), or a new top-level one
     * that takes over the seed key when the user deleted it.
     */
    private suspend fun category(): CategoryEntity {
        val dao = db.categoryDao()
        dao.bySeedKey(TransferSeeds.OPENING_BALANCE)?.let { return it }
        val created = CategoryEntity(
            parentId = null,
            name = FALLBACK_NAME,
            iconKey = "🏁",
            colorArgb = FALLBACK_COLOR,
            kind = CategoryKind.TRANSFER,
            sortOrder = dao.maxSortOrder(null) + 1,
            isSeed = true,
            seedKey = TransferSeeds.OPENING_BALANCE,
        )
        return created.copy(id = dao.insert(created))
    }

    private fun direction(balancePaise: Long) = if (balancePaise < 0) Direction.OUT else Direction.IN

    private companion object {
        fun hash(accountId: Long) = "opening-balance:$accountId"

        const val FALLBACK_NAME = "Opening balance"
        const val FALLBACK_COLOR = 0xFF8C92AC.toInt()
    }
}
