package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind

/**
 * Two-level category tree; transactions reference leaves. [iconKey] holds one emoji. [seedKey] is
 * a stable id for seed categories (e.g. `food_delivery`) that survives renames; null for the
 * user's own categories. Added in schema version 2.
 */
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("parentId"), Index(value = ["seedKey"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val kind: CategoryKind,
    val sortOrder: Int,
    val archived: Boolean = false,
    val isSeed: Boolean = false,
    val seedKey: String? = null,
)
