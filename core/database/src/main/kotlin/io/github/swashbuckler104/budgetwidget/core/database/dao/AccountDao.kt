package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    /** `IS` matches a null [last4] too, which `=` would not. */
    @Query("SELECT * FROM account WHERE bankName = :bankName AND last4 IS :last4 LIMIT 1")
    suspend fun find(bankName: String, last4: String?): AccountEntity?

    /** Every account, by bank then last 4 digits. */
    @Query("SELECT * FROM account ORDER BY bankName COLLATE NOCASE, last4")
    fun observeAll(): Flow<List<AccountEntity>>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Query("UPDATE account SET lastBalancePaise = :balancePaise WHERE id = :id")
    suspend fun updateBalance(id: Long, balancePaise: Long)
}
