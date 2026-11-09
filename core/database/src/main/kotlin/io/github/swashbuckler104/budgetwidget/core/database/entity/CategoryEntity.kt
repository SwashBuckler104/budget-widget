package io.github.swashbuckler104.budgetwidget.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind

/** Two-level category tree; transactions reference leaves. Populated from M2. */
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
    indices = [Index("parentId")],
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
)
