package io.github.swashbuckler104.budgetwidget.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus

internal fun inMemoryDb(): BudgetDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(),
    BudgetDatabase::class.java,
).build()

internal fun testTxn(
    hash: String,
    key: String? = null,
    amount: Long = 100_00,
    at: Long = 0,
    direction: Direction = Direction.OUT,
    type: ParserType = ParserType.EXPENSE,
) = TransactionEntity(
    txnHash = hash,
    source = TransactionSource.SMS,
    amountPaise = amount,
    direction = direction,
    parserType = type,
    merchantRaw = key,
    merchantDisplay = key,
    merchantKey = key,
    accountId = null,
    occurredAt = at,
    smsSender = null,
    smsBody = null,
    reference = null,
    balanceAfterPaise = null,
    isCard = false,
    status = TransactionStatus.PENDING,
    isBackfill = false,
    createdAt = 0,
    updatedAt = 0,
)
