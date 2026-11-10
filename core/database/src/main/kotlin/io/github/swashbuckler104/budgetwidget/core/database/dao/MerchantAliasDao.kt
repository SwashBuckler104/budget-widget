package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantAliasEntity

@Dao
interface MerchantAliasDao {
    @Query("SELECT displayName FROM merchant_alias WHERE merchantKey = :merchantKey")
    suspend fun displayName(merchantKey: String): String?

    @Upsert
    suspend fun upsert(alias: MerchantAliasEntity)
}
