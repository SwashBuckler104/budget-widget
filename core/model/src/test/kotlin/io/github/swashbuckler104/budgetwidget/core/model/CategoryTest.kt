package io.github.swashbuckler104.budgetwidget.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryTest {
    private fun cat(id: Long, parent: Long? = null, archived: Boolean = false) =
        Category(id, parent, "c$id", "🙂", 0, CategoryKind.EXPENSE, 0, archived, false)

    @Test
    fun `a category without subcategories is its own leaf`() {
        assertThat(CategoryGroup(cat(1), emptyList()).pickableLeaves.map { it.id }).containsExactly(1L)
    }

    @Test
    fun `a category with subcategories offers only active subcategories`() {
        val group = CategoryGroup(cat(1), listOf(cat(2, 1), cat(3, 1, archived = true)))
        assertThat(group.pickableLeaves.map { it.id }).containsExactly(2L)
    }

    @Test
    fun `all subcategories archived or parent archived means nothing to pick`() {
        assertThat(CategoryGroup(cat(1), listOf(cat(2, 1, archived = true))).pickableLeaves).isEmpty()
        assertThat(CategoryGroup(cat(1, archived = true), listOf(cat(2, 1))).pickableLeaves).isEmpty()
    }

    @Test
    fun `kinds accept matching directions and transfers accept both`() {
        assertThat(CategoryKind.EXPENSE.accepts(Direction.OUT)).isTrue()
        assertThat(CategoryKind.EXPENSE.accepts(Direction.IN)).isFalse()
        assertThat(CategoryKind.INCOME.accepts(Direction.IN)).isTrue()
        assertThat(CategoryKind.TRANSFER.accepts(Direction.IN)).isTrue()
        assertThat(CategoryKind.TRANSFER.accepts(Direction.OUT)).isTrue()
    }
}
