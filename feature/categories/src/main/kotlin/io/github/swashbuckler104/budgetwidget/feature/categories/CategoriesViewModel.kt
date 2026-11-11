package io.github.swashbuckler104.budgetwidget.feature.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryDraft
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryResult
import io.github.swashbuckler104.budgetwidget.core.data.category.DeleteResult
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.CategoryPalette
import io.github.swashbuckler104.budgetwidget.core.model.MerchantRule
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/edit form. [parent] is set when adding or editing a subcategory. */
data class EditorState(
    val editingId: Long?,
    val parent: Category?,
    val name: String,
    val emoji: String,
    val colorArgb: Int,
    val kind: CategoryKind,
    val error: CategoryResult? = null,
) {
    val isSubcategory: Boolean get() = parent != null
}

data class CategoriesUiState(
    val loading: Boolean = true,
    val groups: List<CategoryGroup> = emptyList(),
    val ruleCounts: Map<Long, Int> = emptyMap(),
    val showArchived: Boolean = false,
    val rulesFor: Category? = null,
    val rules: List<MerchantRule> = emptyList(),
) {
    /** What the list shows: archived groups and subcategories only with [showArchived]. */
    val visibleGroups: List<CategoryGroup>
        get() = if (showArchived) {
            groups
        } else {
            groups.filterNot {
                it.category.archived
            }.map { g -> g.copy(children = g.children.filterNot { it.archived }) }
        }

    /** Visible siblings of [category] (top-level categories, or one parent's subcategories), in order. */
    fun siblingsOf(category: Category): List<Category> {
        val visible = visibleGroups
        return if (category.parentId == null) {
            visible.map { it.category }
        } else {
            visible.firstOrNull { it.category.id == category.parentId }?.children.orEmpty()
        }
    }

    /** Whether [category] can move [offset] places among its visible siblings. */
    fun canMove(category: Category, offset: Int): Boolean {
        val siblings = siblingsOf(category)
        val index = siblings.indexOfFirst { it.id == category.id }
        return index >= 0 && (index + offset) in siblings.indices
    }
}

sealed interface CategoriesMessage {
    data class InUse(val category: Category, val transactions: Int) : CategoriesMessage

    data class Archived(val category: Category) : CategoriesMessage
}

/** Callbacks for the categories UI, so child composables never see the ViewModel. */
class CategoriesActions(
    val setShowArchived: (Boolean) -> Unit,
    val startCreate: (parent: Category?) -> Unit,
    val startEdit: (Category) -> Unit,
    val reorderGroups: (orderedIds: List<Long>) -> Unit,
    val move: (Category, offset: Int) -> Unit,
    val setArchived: (Category, archived: Boolean) -> Unit,
    val delete: (Category) -> Unit,
    val showRules: (Category?) -> Unit,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repository: CategoryRepository) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    /**
     * The open add/edit form. Snapshot state, not part of [state]: text fields must get their value
     * back synchronously, or fast typing drops and reorders characters.
     */
    var editor: EditorState? by mutableStateOf(null)
        private set
    private val rulesFor = MutableStateFlow<Category?>(null)
    private val messageChannel = Channel<CategoriesMessage>(Channel.BUFFERED)

    /** One-shot messages for the snackbar. */
    val messages: Flow<CategoriesMessage> = messageChannel.receiveAsFlow()

    private val rules: Flow<List<MerchantRule>> = rulesFor.flatMapLatest { c ->
        c?.let { repository.observeRules(it.id) }
            ?: flowOf(emptyList())
    }

    val state: StateFlow<CategoriesUiState> = combine(
        combine(repository.observeGroups(), repository.observeRuleCounts(), ::Pair),
        showArchived,
        combine(rulesFor, rules, ::Pair),
    ) { (groups, counts), archived, (ruleCategory, ruleList) ->
        CategoriesUiState(
            loading = false,
            groups = groups,
            ruleCounts = counts,
            showArchived = archived,
            rulesFor = ruleCategory,
            rules = ruleList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState())

    fun setShowArchived(show: Boolean) {
        showArchived.value = show
    }

    fun startCreate(parent: Category?) {
        editor = EditorState(
            editingId = null,
            parent = parent,
            name = "",
            emoji = parent?.emoji ?: DEFAULT_EMOJI,
            colorArgb = parent?.colorArgb ?: CATEGORY_COLORS.first(),
            kind = parent?.kind ?: CategoryKind.EXPENSE,
        )
    }

    fun startEdit(category: Category) {
        val parent = category.parentId?.let { id -> state.value.groups.firstOrNull { it.category.id == id }?.category }
        editor =
            EditorState(category.id, parent, category.name, category.emoji, category.colorArgb, category.kind)
    }

    fun updateEditor(transform: (EditorState) -> EditorState) {
        editor = editor?.let(transform)?.copy(error = null)
    }

    fun dismissEditor() {
        editor = null
    }

    fun saveEditor() {
        val form = editor ?: return
        viewModelScope.launch {
            val draft = CategoryDraft(form.name, form.emoji, form.colorArgb, form.kind)
            val result =
                form.editingId?.let { repository.update(it, draft) } ?: repository.create(form.parent?.id, draft)
            // Only touch the form if it's still the one that was saved (the user may have closed it).
            if (editor?.editingId == form.editingId && editor?.parent == form.parent) {
                editor = if (result is CategoryResult.Saved) null else editor?.copy(error = result)
            }
        }
    }

    /** Persists a new order of top-level categories (after a drag). */
    fun reorderGroups(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorder(orderedIds) }
    }

    /** Moves [category] one place up (-1) or down (+1) among its siblings (accessible reorder). */
    fun move(category: Category, offset: Int) {
        val siblings = siblingsOf(category)
        val from = siblings.indexOfFirst { it.id == category.id }
        val to = from + offset
        if (from < 0 || to !in siblings.indices) return
        val ids = siblings.map { it.id }.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.reorder(ids) }
    }

    fun setArchived(category: Category, archived: Boolean) {
        viewModelScope.launch {
            repository.setArchived(category.id, archived)
            if (archived) messageChannel.send(CategoriesMessage.Archived(category))
        }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            when (val result = repository.delete(category.id)) {
                is DeleteResult.InUse -> messageChannel.send(CategoriesMessage.InUse(category, result.transactions))
                DeleteResult.Deleted, DeleteResult.NotFound -> Unit
            }
        }
    }

    fun showRules(category: Category?) {
        rulesFor.value = category
    }

    fun deleteRule(rule: MerchantRule) {
        viewModelScope.launch { repository.deleteRule(rule.merchantKey) }
    }

    fun moveRule(rule: MerchantRule, target: Category) {
        viewModelScope.launch { repository.moveRule(rule.merchantKey, target.id) }
    }

    private fun siblingsOf(category: Category): List<Category> = state.value.siblingsOf(category)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_EMOJI = "📌"
    }
}

/** Colors offered for top-level categories (bright shades, darkened on light surfaces). */
val CATEGORY_COLORS: List<Int> = CategoryPalette.colors

/** Emoji offered in the editor (Emoji 5.0 or older, so they render on Android 8); any emoji can be typed. */
val CATEGORY_EMOJI: List<String> = listOf(
    "🍽️", "🛒", "🍝", "🛵", "☕", "🍺", "🚗", "⛽", "🚕", "🚌", "🅿️", "🛍️", "👕", "💻", "🏠", "📦",
    "📃", "💡", "📶", "🔑", "🚰", "📺", "🏥", "💊", "💉", "🛡️", "🎓", "📚", "🎬", "🎟️", "🎮", "🎵",
    "👪", "🎁", "💇", "👶", "🐶", "✈️", "🏨", "🚆", "🏦", "📈", "🔁", "🤝", "💰", "💼", "💸", "📌",
)
