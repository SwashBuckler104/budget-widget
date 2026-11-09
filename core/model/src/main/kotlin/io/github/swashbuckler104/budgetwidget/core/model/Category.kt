package io.github.swashbuckler104.budgetwidget.core.model

/**
 * A category or subcategory (two levels max, SCOPE.md F7). Transactions point at a leaf: a
 * subcategory, or a category without subcategories. [emoji] is the icon (one emoji, shown in the
 * app, notifications and widgets); [colorArgb] is a packed ARGB color.
 */
data class Category(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val emoji: String,
    val colorArgb: Int,
    val kind: CategoryKind,
    val sortOrder: Int,
    val archived: Boolean,
    val isSeed: Boolean,
)

/** A top-level category with its subcategories, both in display order. */
data class CategoryGroup(val category: Category, val children: List<Category>) {
    /** Leaves usable in pickers: the subcategories, or the category itself when it has none. */
    val pickableLeaves: List<Category>
        get() = if (category.archived) {
            emptyList()
        } else {
            children.filterNot {
                it.archived
            }.ifEmpty { if (children.isEmpty()) listOf(category) else emptyList() }
        }
}

/** Learned `merchantKey -> category` rule (SCOPE.md F8). [lastUsedAt] is epoch ms (UTC). */
data class MerchantRule(val merchantKey: String, val categoryId: Long, val confirmCount: Int, val lastUsedAt: Long)

/** Whether a category of this kind can classify money moving in [direction]. */
fun CategoryKind.accepts(direction: Direction): Boolean = when (this) {
    CategoryKind.EXPENSE -> direction == Direction.OUT
    CategoryKind.INCOME -> direction == Direction.IN
    CategoryKind.TRANSFER -> true
}
