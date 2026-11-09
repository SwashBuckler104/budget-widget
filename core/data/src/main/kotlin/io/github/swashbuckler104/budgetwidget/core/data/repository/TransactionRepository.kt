package io.github.swashbuckler104.budgetwidget.core.data.repository

import io.github.swashbuckler104.budgetwidget.core.database.dao.TransactionDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.UnparsedSmsDao
import io.github.swashbuckler104.budgetwidget.core.database.model.TransactionWithAccount
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val unparsedSmsDao: UnparsedSmsDao,
) {
    /** Every transaction, newest first. */
    fun observeTransactions(): Flow<List<Transaction>> = transactionDao.observeAllWithAccount().map { rows ->
        rows.map { it.toModel() }
    }

    fun observeUnparsedCount(): Flow<Int> = unparsedSmsDao.observeCount()
}

private fun TransactionWithAccount.toModel() = with(transaction) {
    Transaction(
        id = id,
        source = source,
        amountPaise = amountPaise,
        direction = direction,
        parserType = parserType,
        merchantDisplay = merchantDisplay,
        bankName = bankName,
        accountLast4 = accountLast4,
        occurredAt = occurredAt,
        isCard = isCard,
        status = status,
        isBackfill = isBackfill,
        duplicateOfId = duplicateOfId,
    )
}
