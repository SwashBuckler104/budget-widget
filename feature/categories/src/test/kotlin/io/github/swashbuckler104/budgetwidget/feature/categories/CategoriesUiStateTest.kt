package io.github.swashbuckler104.budgetwidget.feature.categories

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import org.junit.Test

class CategoriesUiStateTest {
    private fun cat(id: Long, parent: Long? = null, archived: Boolean = false) =
        Category(id, parent, "c$id", "🙂", 0, CategoryKind.EXPENSE, 0, archived, false)

    private val state = CategoriesUiState(
        loading = false,
        groups = listOf(
            CategoryGroup(cat(1), listOf(cat(11, 1), cat(12, 1, archived = true), cat(13, 1))),
            CategoryGroup(cat(2, archived = true), emptyList()),
            CategoryGroup(cat(3), emptyList()),
        ),
    )

    @Test
    fun `archived categories are hidden unless shown`() {
        assertThat(state.visibleGroups.map { it.category.id }).containsExactly(1L, 3L).inOrder()
        assertThat(state.visibleGroups.first().children.map { it.id }).containsExactly(11L, 13L).inOrder()
        assertThat(state.copy(showArchived = true).visibleGroups).hasSize(3)
    }

    @Test
    fun `moves stay within visible siblings`() {
        assertThat(state.canMove(cat(1), -1)).isFalse()
        assertThat(state.canMove(cat(1), 1)).isTrue()
        assertThat(state.canMove(cat(3), 1)).isFalse()
        assertThat(state.canMove(cat(11, 1), 1)).isTrue()
        assertThat(state.canMove(cat(13, 1), 1)).isFalse()
        assertThat(state.canMove(cat(12, 1, archived = true), 1)).isFalse()
    }

    @Test
    fun `typed emoji keep only the first character, including joined emoji`() {
        assertThat(firstGrapheme(" 🐶🐱 ")).isEqualTo("🐶")
        assertThat(firstGrapheme("👨‍👩‍👧x")).isEqualTo("👨‍👩‍👧")
        assertThat(firstGrapheme("  ")).isEmpty()
    }
}
