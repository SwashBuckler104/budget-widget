package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Learned `merchantKey -> category` rule (SCOPE.md F8). Used from M2. [lastUsedAt] is epoch ms. */
@Entity(
    tableName = "merchant_rule",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val confirmCount: Int,
    val lastUsedAt: Long,
)
