package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.swashbuckler104.budgetwidget.core.database.entity.AccountEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.DayCloseEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantAliasEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.TransactionEntity

/**
 * Whole-table reads and writes for backup, restore and "start fresh" (SCOPE.md F9). Callers wrap
 * them in one database transaction.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM category ORDER BY id")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM account ORDER BY id")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun transactions(): List<TransactionEntity>

    @Query("SELECT * FROM merchant_rule ORDER BY merchantKey")
    suspend fun rules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_alias ORDER BY merchantKey")
    suspend fun aliases(): List<MerchantAliasEntity>

    @Query("SELECT * FROM day_close ORDER BY date")
    suspend fun dayCloses(): List<DayCloseEntity>

    @Insert
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert
    suspend fun insertRules(items: List<MerchantRuleEntity>)

    @Insert
    suspend fun insertAliases(items: List<MerchantAliasEntity>)

    @Insert
    suspend fun insertDayCloses(items: List<DayCloseEntity>)

    @Query("UPDATE transactions SET duplicateOfId = :duplicateOfId WHERE id = :id")
    suspend fun setDuplicateOf(id: Long, duplicateOfId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM merchant_rule")
    suspend fun deleteRules()

    @Query("DELETE FROM merchant_alias")
    suspend fun deleteAliases()

    @Query("DELETE FROM day_close")
    suspend fun deleteDayCloses()

    @Query("DELETE FROM unparsed_sms")
    suspend fun deleteUnparsed()

    @Query("DELETE FROM account")
    suspend fun deleteAccounts()

    /** Subcategories first: a parent can't go while a child still points at it. */
    @Query("DELETE FROM category WHERE parentId IS NOT NULL")
    suspend fun deleteSubcategories()

    @Query("DELETE FROM category")
    suspend fun deleteCategories()
}
