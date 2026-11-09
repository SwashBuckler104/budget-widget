package io.github.swashbuckler104.budgetwidget.feature.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Tab 3: categories and subcategories (SCOPE.md F7). */
@Composable
fun CategoriesRoute(modifier: Modifier = Modifier, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is CategoriesMessage.InUse -> {
                    val result = snackbar.showSnackbar(
                        resources.getQuantityString(
                            R.plurals.categories_in_use,
                            message.transactions,
                            message.category.name,
                            message.transactions,
                        ),
                        actionLabel = resources.getString(R.string.categories_archive),
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(message.category, true)
                }

                is CategoriesMessage.Archived -> {
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.categories_archived_message, message.category.name),
                        actionLabel = resources.getString(R.string.categories_undo),
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.setArchived(message.category, false)
                }
            }
        }
    }
    val actions = remember(viewModel) {
        CategoriesActions(
            setShowArchived = viewModel::setShowArchived,
            startCreate = viewModel::startCreate,
            startEdit = viewModel::startEdit,
            reorderGroups = viewModel::reorderGroups,
            move = viewModel::move,
            setArchived = viewModel::setArchived,
            delete = viewModel::delete,
            showRules = viewModel::showRules,
        )
    }
    CategoriesScreen(state = state, actions = actions, snackbar = snackbar, modifier = modifier)
    viewModel.editor?.let {
        CategoryEditorSheet(
            it,
            onChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }
    state.rulesFor?.let { category ->
        MerchantRulesSheet(
            category = category,
            rules = state.rules,
            groups = state.groups,
            onDelete = viewModel::deleteRule,
            onMove = viewModel::moveRule,
            onDismiss = { viewModel.showRules(null) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(
    state: CategoriesUiState,
    actions: CategoriesActions,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val groups = state.visibleGroups
    // Local copy so the list follows the finger while dragging; saved when the drag ends.
    // The list follows the finger from a local copy. Every swap is saved at once: the library
    // applies its last swap asynchronously, so saving only on drag end could store a stale order
    // and leave the screen out of sync with the database. Database updates are ignored mid-drag.
    var order by remember { mutableStateOf(groups) }
    var dragActive by remember { mutableStateOf(false) }
    LaunchedEffect(groups, dragActive) { if (!dragActive) order = groups }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.category.id == from.key }
        val toIndex = order.indexOfFirst { it.category.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            actions.reorderGroups(order.map { it.category.id })
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                actions = {
                    IconToggleButton(checked = state.showArchived, onCheckedChange = actions.setShowArchived) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = stringResource(R.string.categories_show_archived),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // The text/icon overload of ExtendedFloatingActionButton leaves its label out of the
            // accessibility tree (seen as an unlabeled "Button" on API 36); this overload exposes it.
            ExtendedFloatingActionButton(onClick = { actions.startCreate(null) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.categories_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(order, key = { it.category.id }) { group ->
                ReorderableItem(reorderState, key = group.category.id) { dragging ->
                    val id = group.category.id
                    CategoryGroupItem(
                        group = group,
                        state = state,
                        actions = actions,
                        dragging = dragging,
                        expanded = id in expanded,
                        onToggleExpand = { expanded = if (id in expanded) expanded - id else expanded + id },
                        onDelete = { pendingDelete = it },
                        dragHandle = { handleModifier ->
                            IconButton(
                                onClick = {},
                                modifier = handleModifier.draggableHandle(
                                    onDragStarted = { dragActive = true },
                                    onDragStopped = { dragActive = false },
                                ),
                            ) {
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    contentDescription = stringResource(
                                        R.string.categories_reorder,
                                        group.category.name,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.categories_delete_title, category.name)) },
            text = { Text(stringResource(R.string.categories_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.delete(category)
                    pendingDelete = null
                }) { Text(stringResource(R.string.categories_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.categories_cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryGroupItem(
    group: CategoryGroup,
    state: CategoriesUiState,
    actions: CategoriesActions,
    dragging: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: (Category) -> Unit,
    dragHandle: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parent = group.category
    Surface(tonalElevation = if (dragging) 6.dp else 0.dp, modifier = modifier) {
        Column {
            CategoryRow(
                category = parent,
                subtitle = groupSubtitle(group, state.ruleCounts[parent.id] ?: 0),
                canMoveUp = state.canMove(parent, -1),
                canMoveDown = state.canMove(parent, 1),
                actions = actions,
                onRules = { actions.showRules(parent) },
                onDelete = { onDelete(parent) },
                leading = { dragHandle(Modifier) },
                trailing = {
                    if (group.children.isNotEmpty()) {
                        val label =
                            stringResource(
                                if (expanded) R.string.categories_collapse else R.string.categories_expand,
                                parent.name,
                            )
                        IconButton(onClick = onToggleExpand) {
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = label,
                            )
                        }
                    }
                },
            )
            if (expanded) {
                group.children.forEach { child ->
                    CategoryRow(
                        category = child,
                        subtitle = ruleSubtitle(state.ruleCounts[child.id] ?: 0),
                        canMoveUp = state.canMove(child, -1),
                        canMoveDown = state.canMove(child, 1),
                        actions = actions,
                        onRules = { actions.showRules(child) },
                        onDelete = { onDelete(child) },
                        modifier = Modifier.padding(start = 40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun groupSubtitle(group: CategoryGroup, rules: Int): String {
    val kind = stringResource(kindLabel(group.category.kind))
    val parts = buildList {
        add(kind)
        if (group.children.isNotEmpty()) {
            add(
                pluralStringResource(R.plurals.categories_subcategory_count, group.children.size, group.children.size),
            )
        }
        if (rules > 0) add(pluralStringResource(R.plurals.categories_rule_count, rules, rules))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ruleSubtitle(rules: Int): String? = if (rules >
    0
) {
    pluralStringResource(R.plurals.categories_rule_count, rules, rules)
} else {
    null
}

internal fun kindLabel(kind: CategoryKind): Int = when (kind) {
    CategoryKind.EXPENSE -> R.string.categories_kind_expense
    CategoryKind.INCOME -> R.string.categories_kind_income
    CategoryKind.TRANSFER -> R.string.categories_kind_transfer
}

@Composable
private fun CategoryRow(
    category: Category,
    subtitle: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: CategoriesActions,
    onRules: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val moveUp = stringResource(R.string.categories_move_up)
    val moveDown = stringResource(R.string.categories_move_down)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { actions.startEdit(category) }
            .alpha(if (category.archived) 0.5f else 1f)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(moveUp) {
                        actions.move(category, -1)
                        true
                    }.takeIf { canMoveUp },
                    CustomAccessibilityAction(moveDown) {
                        actions.move(category, 1)
                        true
                    }.takeIf { canMoveDown },
                )
            },
    ) {
        leading?.invoke()
        CategoryAvatar(category.emoji, category.colorArgb, modifier = Modifier.padding(horizontal = 8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                subtitle,
                stringResource(R.string.categories_archived_label).takeIf {
                    category.archived
                },
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.categories_more, category.name))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val entries = buildList {
                add(MenuEntry(R.string.categories_edit) { actions.startEdit(category) })
                if (category.parentId ==
                    null
                ) {
                    add(MenuEntry(R.string.categories_add_sub) { actions.startCreate(category) })
                }
                add(MenuEntry(R.string.categories_rules, action = onRules))
                add(MenuEntry(R.string.categories_move_up, canMoveUp) { actions.move(category, -1) })
                add(MenuEntry(R.string.categories_move_down, canMoveDown) { actions.move(category, 1) })
                if (category.archived) {
                    add(MenuEntry(R.string.categories_unarchive) { actions.setArchived(category, false) })
                } else {
                    add(MenuEntry(R.string.categories_archive) { actions.setArchived(category, true) })
                }
                add(MenuEntry(R.string.categories_delete, action = onDelete))
            }
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label)) },
                    enabled = entry.enabled,
                    onClick = {
                        menu = false
                        entry.action()
                    },
                )
            }
        }
    }
}

private class MenuEntry(val label: Int, val enabled: Boolean = true, val action: () -> Unit)
