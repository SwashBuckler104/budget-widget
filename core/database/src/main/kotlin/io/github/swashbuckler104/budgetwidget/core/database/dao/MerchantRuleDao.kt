package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rule WHERE merchantKey = :merchantKey")
    suspend fun get(merchantKey: String): MerchantRuleEntity?

    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("SELECT * FROM merchant_rule WHERE categoryId = :categoryId ORDER BY confirmCount DESC, lastUsedAt DESC")
    fun observeForCategory(categoryId: Long): Flow<List<MerchantRuleEntity>>

    @Query("SELECT categoryId, COUNT(*) AS rules FROM merchant_rule GROUP BY categoryId")
    fun observeCountsByCategory(): Flow<List<RuleCount>>

    @Query("DELETE FROM merchant_rule WHERE merchantKey = :merchantKey")
    suspend fun delete(merchantKey: String)

    @Query("UPDATE merchant_rule SET categoryId = :categoryId WHERE merchantKey = :merchantKey")
    suspend fun move(merchantKey: String, categoryId: Long)
}

data class RuleCount(val categoryId: Long, val rules: Int)
