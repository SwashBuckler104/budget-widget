package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind

/** Auto-created on the first SMS for a (bank, last 4 digits) pair. Times are epoch ms (UTC). */
@Entity(
    tableName = "account",
    indices = [Index(value = ["bankName", "last4"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    /** Null when the SMS names no account (some wallets and UPI apps). */
    val last4: String?,
    val nickname: String? = null,
    val kind: AccountKind,
    val lastBalancePaise: Long? = null,
    val hidden: Boolean = false,
    val createdAt: Long,
)
