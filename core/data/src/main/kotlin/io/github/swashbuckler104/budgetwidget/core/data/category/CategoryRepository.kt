package io.github.swashbuckler104.budgetwidget.core.data.category

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.database.entity.MerchantRuleEntity
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.MerchantRule
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a user can set on a category. Subcategories inherit kind and color from their parent. */
data class CategoryDraft(val name: String, val emoji: String, val colorArgb: Int, val kind: CategoryKind)

sealed interface CategoryResult {
    data class Saved(val id: Long) : CategoryResult

    data object BlankName : CategoryResult

    data object NameTooLong : CategoryResult

    data object DuplicateName : CategoryResult

    data object BlankEmoji : CategoryResult

    /** Only two levels: a subcategory can't have subcategories. */
    data object TooDeep : CategoryResult

    data object NotFound : CategoryResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** Transactions still point at it or its subcategories: archive instead (SCOPE.md F7). */
    data class InUse(val transactions: Int) : DeleteResult

    data object NotFound : DeleteResult
}

class CategoryRepository @Inject constructor(private val db: BudgetDatabase) {
    private val dao get() = db.categoryDao()
    private val rules get() = db.merchantRuleDao()

    /** Every category grouped under its parent, in display order (archived included). */
    fun observeGroups(): Flow<List<CategoryGroup>> = dao.observeAll().map { rows -> group(rows.map { it.toModel() }) }

    fun observeRuleCounts(): Flow<Map<Long, Int>> = rules.observeCountsByCategory().map { list ->
        list.associate {
            it.categoryId to
                it.rules
        }
    }

    fun observeRules(categoryId: Long): Flow<List<MerchantRule>> = rules.observeForCategory(categoryId).map { list ->
        list.map { it.toModel() }
    }

    suspend fun create(parentId: Long?, draft: CategoryDraft): CategoryResult = db.withTransaction {
        val parent = parentId?.let { dao.get(it) ?: return@withTransaction CategoryResult.NotFound }
        if (parent?.parentId != null) return@withTransaction CategoryResult.TooDeep
        validate(parentId, draft, excludeId = 0)?.let { return@withTransaction it }
        val id = dao.insert(
            CategoryEntity(
                parentId = parentId,
                name = draft.name.trim(),
                iconKey = draft.emoji.trim(),
                colorArgb = parent?.colorArgb ?: draft.colorArgb,
                kind = parent?.kind ?: draft.kind,
                sortOrder = dao.maxSortOrder(parentId) + 1,
            ),
        )
        CategoryResult.Saved(id)
    }

    /** Renames/recolors. A parent's new color and kind flow down to its subcategories. */
    suspend fun update(id: Long, draft: CategoryDraft): CategoryResult = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction CategoryResult.NotFound
        validate(current.parentId, draft, excludeId = id)?.let { return@withTransaction it }
        val parent = current.parentId?.let { dao.get(it) }
        dao.update(
            current.copy(
                name = draft.name.trim(),
                iconKey = draft.emoji.trim(),
                colorArgb = parent?.colorArgb ?: draft.colorArgb,
                kind = parent?.kind ?: draft.kind,
            ),
        )
        if (parent == null) {
            dao.getAll().filter {
                it.parentId == id
            }.forEach { dao.update(it.copy(colorArgb = draft.colorArgb, kind = draft.kind)) }
        }
        CategoryResult.Saved(id)
    }

    /** [orderedIds] are siblings (all top-level, or all children of one parent) in their new order. */
    suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchivedWithChildren(id, archived)

    /** Hard delete only when nothing references it or its subcategories; their rules go with it. */
    suspend fun delete(id: Long): DeleteResult = db.withTransaction {
        dao.get(id) ?: return@withTransaction DeleteResult.NotFound
        val ids = dao.childIds(id) + id
        val used = dao.countTransactionsUsing(ids)
        if (used > 0) return@withTransaction DeleteResult.InUse(used)
        dao.deleteIds(ids - id)
        dao.deleteIds(listOf(id))
        DeleteResult.Deleted
    }

    suspend fun deleteRule(merchantKey: String) = rules.delete(merchantKey)

    suspend fun moveRule(merchantKey: String, categoryId: Long) = rules.move(merchantKey, categoryId)

    private suspend fun validate(parentId: Long?, draft: CategoryDraft, excludeId: Long): CategoryResult? {
        val name = draft.name.trim()
        return when {
            name.isEmpty() -> CategoryResult.BlankName
            name.length > MAX_NAME -> CategoryResult.NameTooLong
            draft.emoji.isBlank() -> CategoryResult.BlankEmoji
            dao.findSibling(parentId, name, excludeId) != null -> CategoryResult.DuplicateName
            else -> null
        }
    }

    companion object {
        const val MAX_NAME = 40
    }
}

internal fun group(categories: List<Category>): List<CategoryGroup> {
    val children = categories.filter { it.parentId != null }.groupBy { it.parentId }
    return categories.filter { it.parentId == null }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map { parent ->
            CategoryGroup(parent, children[parent.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id })))
        }
}

internal fun CategoryEntity.toModel() = Category(
    id = id,
    parentId = parentId,
    name = name,
    emoji = iconKey,
    colorArgb = colorArgb,
    kind = kind,
    sortOrder = sortOrder,
    archived = archived,
    isSeed = isSeed,
)

internal fun MerchantRuleEntity.toModel() = MerchantRule(merchantKey, categoryId, confirmCount, lastUsedAt)
