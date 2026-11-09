package io.github.swashbuckler104.budgetwidget.core.database.model

import androidx.room.Embedded
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity

/** A transaction joined with its account's bank name and last 4 digits. */
data class TransactionWithAccount(
    @Embedded val transaction: TransactionEntity,
    val bankName: String?,
    val accountLast4: String?,
)
