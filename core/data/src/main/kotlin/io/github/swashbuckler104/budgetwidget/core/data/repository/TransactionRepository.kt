package io.github.swashbuckler104.budgetwidget.core.data.repository

import io.github.swashbuckler104.budgetwidget.core.data.ingest.UnparsedDismissals
import io.github.swashbuckler104.budgetwidget.core.database.dao.AccountDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.TransactionDao
import io.github.swashbuckler104.budgetwidget.core.database.dao.UnparsedSmsDao
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.model.TransactionRow
import io.github.swashbuckler104.budgetwidget.core.database.model.TransactionWithAccount
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionDetail
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedSms
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val unparsedSmsDao: UnparsedSmsDao,
    private val accountDao: AccountDao,
    private val dismissals: UnparsedDismissals = UnparsedDismissals.None,
) {
    /** Every transaction, newest first (list rows: no SMS body). */
    fun observeTransactions(): Flow<List<Transaction>> = transactionDao.observeRows().map { rows ->
        rows.map { it.toModel() }
    }

    /** Emits after any write to transactions (inserts, sorts, edits); carries no data. */
    fun observeChanges(): Flow<Unit> = transactionDao.observeCount().map { }

    fun observeUnparsedCount(): Flow<Int> = unparsedSmsDao.observeCount()

    /** The inbox: every PENDING transaction, oldest first (SCOPE.md §3). */
    fun observePending(): Flow<List<Transaction>> = transactionDao.observePending().map { rows ->
        rows.map { it.toModel() }
    }

    fun observeTransaction(id: Long): Flow<Transaction?> = transactionDao.observeWithAccount(id).map { it?.toModel() }

    /** One transaction with its original SMS, for the edit sheet; null once it is deleted. */
    fun observeDetail(id: Long): Flow<TransactionDetail?> = transactionDao.observeWithAccount(id).map { row ->
        row?.let {
            TransactionDetail(
                transaction = it.toModel(),
                smsSender = it.transaction.smsSender,
                smsBody = it.transaction.smsBody,
                reference = it.transaction.reference,
                balanceAfterPaise = it.transaction.balanceAfterPaise,
            )
        }
    }

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Newest first. */
    fun observeUnparsed(): Flow<List<UnparsedSms>> = unparsedSmsDao.observeAll().map { rows ->
        rows.map { UnparsedSms(it.id, it.sender, it.body, it.receivedAt, it.reason) }
    }

    /** Deletes one entry; a later inbox rescan won't record it again ([UnparsedDismissals]). */
    suspend fun deleteUnparsed(id: Long) {
        unparsedSmsDao.receivedAt(id)?.let { dismissals.dismissUpTo(it) }
        unparsedSmsDao.delete(id)
    }

    suspend fun clearUnparsed() {
        unparsedSmsDao.newestReceivedAt()?.let { dismissals.dismissUpTo(it) }
        unparsedSmsDao.deleteAll()
    }

    /** Blank clears the nickname. */
    suspend fun setAccountNickname(id: Long, nickname: String?) = accountDao.setNickname(id, nickname)

    /** Hidden accounts leave the account filters; their transactions still count. */
    suspend fun setAccountHidden(id: Long, hidden: Boolean) = accountDao.setHidden(id, hidden)

    suspend fun get(id: Long): Transaction? = transactionDao.getWithAccount(id)?.toModel()

    suspend fun hasAny(): Boolean = transactionDao.count() > 0

    suspend fun pendingIds(): Set<Long> = transactionDao.pendingIds().toSet()
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
        merchantKey = merchantKey,
        categoryId = categoryId,
        autoCategorized = autoCategorized,
        note = note,
        accountId = accountId,
        ignoreReason = ignoreReason,
        editedByUser = editedByUser,
    )
}

private fun TransactionRow.toModel() = Transaction(
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
    merchantKey = merchantKey,
    categoryId = categoryId,
    autoCategorized = autoCategorized,
    note = note,
    accountId = accountId,
    ignoreReason = ignoreReason,
    editedByUser = editedByUser,
)

private fun AccountEntity.toModel() = Account(id, bankName, last4, nickname, kind, hidden, lastBalancePaise)
