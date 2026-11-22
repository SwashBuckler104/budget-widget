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

    /** The most recently used [AlwaysCandidate]s, newest first; the merchant's latest display name. */
    @Query(
        """
        SELECT r.merchantKey, c.id AS categoryId, c.name AS category,
          COALESCE((SELECT t.merchantDisplay FROM transactions t WHERE t.merchantKey = r.merchantKey
            AND t.merchantDisplay IS NOT NULL ORDER BY t.occurredAt DESC LIMIT 1), r.merchantKey) AS merchant
        FROM merchant_rule r JOIN category c ON c.id = r.categoryId
        WHERE r.always = 0 AND r.confirmCount >= $ALWAYS_OFFER_AFTER AND c.archived = 0
        ORDER BY r.lastUsedAt DESC LIMIT 20
        """,
    )
    fun observeAlwaysCandidates(): Flow<List<AlwaysCandidate>>

    /** Turns "always" on or off for [merchantKey] (ADR-0062). */
    @Query("UPDATE merchant_rule SET always = :always WHERE merchantKey = :merchantKey")
    suspend fun setAlways(merchantKey: String, always: Boolean)

    @Query("DELETE FROM merchant_rule WHERE merchantKey = :merchantKey")
    suspend fun delete(merchantKey: String)

    @Query("UPDATE merchant_rule SET categoryId = :categoryId WHERE merchantKey = :merchantKey")
    suspend fun move(merchantKey: String, categoryId: Long)

    companion object {
        /** Purple offers "always" once a merchant went to the same category this often (ADR-0063). */
        const val ALWAYS_OFFER_AFTER = 3
    }
}

data class RuleCount(val categoryId: Long, val rules: Int)

/** A merchant sorted into the same category [MerchantRuleDao.ALWAYS_OFFER_AFTER]+ times, not yet "always". */
data class AlwaysCandidate(val merchantKey: String, val merchant: String, val categoryId: Long, val category: String)
