package io.github.swashbuckler104.budgetwidget.core.data.category

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Inserts the seed categories once. The inserted version is remembered in settings, so seeds the
 * user deletes or renames are never re-created.
 */
@Singleton
class CategorySeeder @Inject constructor(private val db: BudgetDatabase, private val settings: SettingsStore) {
    private val mutex = Mutex()

    suspend fun ensureSeeded() = mutex.withLock {
        if (settings.seededCategoriesVersion() >= SeedCategories.VERSION) return@withLock
        db.withTransaction {
            val dao = db.categoryDao()
            // A restored backup or an earlier partial run may already hold seeds: skip those keys.
            val existing = dao.getAll().filter { it.seedKey != null }.associate { it.seedKey!! to it.id }
            SeedCategories.groups.forEach { group ->
                val parent = group.category
                // New seeds go after anything already there, in seed order.
                val parentId =
                    existing[parent.key] ?: dao.insert(entity(parent, null, group, dao.maxSortOrder(null) + 1))
                parent.children.forEachIndexed { childIndex, child ->
                    if (child.key !in existing) dao.insert(entity(child, parentId, group, childIndex))
                }
            }
        }
        settings.setSeededCategoriesVersion(SeedCategories.VERSION)
    }

    private fun entity(seed: SeedCategory, parentId: Long?, group: SeedGroup, sortOrder: Int) = CategoryEntity(
        parentId = parentId,
        name = seed.name,
        iconKey = seed.emoji,
        colorArgb = group.colorArgb,
        kind = group.kind,
        sortOrder = sortOrder,
        isSeed = true,
        seedKey = seed.key,
    )
}
