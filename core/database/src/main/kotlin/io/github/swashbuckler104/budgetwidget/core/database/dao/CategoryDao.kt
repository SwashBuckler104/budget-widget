package io.github.swashbuckler104.budgetwidget.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    /** Top-level categories first, then by display order. */
    @Query("SELECT * FROM category ORDER BY parentId IS NOT NULL, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY parentId IS NOT NULL, sortOrder, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM category WHERE parentId IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Int

    /** A sibling with the same name, ignoring case (names are unique among siblings). */
    @Query(
        "SELECT * FROM category WHERE parentId IS :parentId AND name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1",
    )
    suspend fun findSibling(parentId: Long?, name: String, excludeId: Long): CategoryEntity?

    @Query("UPDATE category SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Rewrites the display order of [orderedIds] (siblings) to 0, 1, 2, ... */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    /** Archiving a category archives its subcategories too; un-archiving restores them. */
    @Query("UPDATE category SET archived = :archived WHERE id = :id OR parentId = :id")
    suspend fun setArchivedWithChildren(id: Long, archived: Boolean)

    @Query("SELECT id FROM category WHERE parentId = :id")
    suspend fun childIds(id: Long): List<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId IN (:ids)")
    suspend fun countTransactionsUsing(ids: List<Long>): Int

    @Query("DELETE FROM category WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}
