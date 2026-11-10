package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The name the user gave a merchant ("apply to all from this merchant", SCOPE.md F7). New SMS
 * from the same `merchantKey` get [displayName] instead of the parser's text. Schema v4.
 */
@Entity(tableName = "merchant_alias")
data class MerchantAliasEntity(@PrimaryKey val merchantKey: String, val displayName: String)
