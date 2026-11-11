package io.github.swashbuckler104.budgetwidget.core.data.category

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.dao.CategoryDao
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Inserts the seed categories. The seeded version is remembered in settings and only groups added
 * after it are inserted, so seeds the user deletes or renames are never re-created. A new group
 * adopts a category the user already made with the same name (and its same-named subcategories)
 * instead of duplicating it, keeping the user's name, icon, color, kind and order.
 */
@Singleton
class CategorySeeder @Inject constructor(private val db: BudgetDatabase, private val settings: SettingsStore) {
    private val mutex = Mutex()

    suspend fun ensureSeeded() = mutex.withLock {
        val seeded = settings.seededCategoriesVersion()
        if (seeded >= SeedCategories.VERSION) return@withLock
        db.withTransaction {
            val dao = db.categoryDao()
            // A restored backup or an earlier partial run may already hold seeds: skip those keys.
            val existing = dao.getAll().filter { it.seedKey != null }.associate { it.seedKey!! to it.id }
            SeedCategories.groups.filter { it.since > seeded }.forEach { group ->
                val parent = group.category
                val adopted = if (parent.key in existing) null else dao.adopt(parent, null)
                // New seeds go after anything already there, in seed order.
                val parentId = existing[parent.key]
                    ?: adopted
                    ?: dao.insert(entity(parent, null, group, dao.maxSortOrder(null) + 1))
                val fresh = existing[parent.key] == null && adopted == null
                parent.children.forEachIndexed { childIndex, child ->
                    if (child.key in existing || (!fresh && dao.adopt(child, parentId) != null)) return@forEachIndexed
                    val order = if (fresh) childIndex else dao.maxSortOrder(parentId) + 1
                    dao.insert(entity(child, parentId, group, order))
                }
            }
        }
        settings.setSeededCategoriesVersion(SeedCategories.VERSION)
    }

    /** Marks the user's unseeded category named like [seed] under [parentId] as that seed; its id or null. */
    private suspend fun CategoryDao.adopt(seed: SeedCategory, parentId: Long?): Long? {
        val own = findSibling(parentId, seed.name, excludeId = -1)?.takeIf { it.seedKey == null } ?: return null
        update(own.copy(isSeed = true, seedKey = seed.key))
        return own.id
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
