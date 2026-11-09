package io.github.swashbuckler104.budgetwidget.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryResult
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CategoryEditorSheet(
    state: EditorState,
    onChange: ((EditorState) -> EditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            val title = when {
                state.editingId != null -> R.string.categories_editor_edit
                state.parent != null -> R.string.categories_editor_new_sub
                else -> R.string.categories_editor_new
            }
            // Save lives in the header: the icon grid below is long.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryAvatar(state.emoji.ifBlank { "?" }, state.colorArgb, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                    state.parent?.let {
                        Text(
                            stringResource(R.string.categories_editor_in, it.name),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Button(onClick = onSave) { Text(stringResource(R.string.categories_save)) }
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = { name -> onChange { it.copy(name = name.take(CategoryRepository.MAX_NAME)) } },
                label = { Text(stringResource(R.string.categories_editor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                isError = state.error.isNameError(),
                supportingText = errorText(state.error)?.let { { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.categories_editor_icon), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CATEGORY_EMOJI.forEach { emoji ->
                    val selected = emoji == state.emoji
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            )
                            .clickable(role = Role.RadioButton) { onChange { it.copy(emoji = emoji) } }
                            .semantics { this.selected = selected },
                    ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                }
            }
            OutlinedTextField(
                value = state.emoji,
                onValueChange = { text -> onChange { it.copy(emoji = firstGrapheme(text)) } },
                label = { Text(stringResource(R.string.categories_editor_custom_emoji)) },
                singleLine = true,
                isError = state.error == CategoryResult.BlankEmoji,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.isSubcategory) {
                Text(stringResource(R.string.categories_editor_color), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CATEGORY_COLORS.forEachIndexed { index, color ->
                        val selected = color == state.colorArgb
                        val label = stringResource(R.string.categories_editor_color_n, index + 1)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(role = Role.RadioButton) { onChange { it.copy(colorArgb = color) } }
                                .semantics {
                                    this.selected = selected
                                    contentDescription = label
                                },
                        )
                    }
                }
                Text(stringResource(R.string.categories_editor_kind), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CategoryKind.entries.forEachIndexed { index, kind ->
                        SegmentedButton(
                            selected = state.kind == kind,
                            onClick = { onChange { it.copy(kind = kind) } },
                            shape = SegmentedButtonDefaults.itemShape(index, CategoryKind.entries.size),
                        ) { Text(stringResource(kindLabel(kind))) }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.categories_editor_inherits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun CategoryResult?.isNameError() =
    this == CategoryResult.BlankName || this == CategoryResult.DuplicateName || this == CategoryResult.NameTooLong

private fun errorText(result: CategoryResult?): Int? = when (result) {
    CategoryResult.BlankName -> R.string.categories_error_blank
    CategoryResult.DuplicateName -> R.string.categories_error_duplicate
    CategoryResult.NameTooLong -> R.string.categories_error_long
    CategoryResult.BlankEmoji -> R.string.categories_error_emoji
    CategoryResult.TooDeep -> R.string.categories_error_depth
    CategoryResult.NotFound -> R.string.categories_error_missing
    is CategoryResult.Saved, null -> null
}

/** Keeps one user-perceived character (emoji can be several code points). */
internal fun firstGrapheme(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(trimmed)
    val end = iterator.next()
    return if (end == java.text.BreakIterator.DONE) trimmed else trimmed.substring(0, end)
}
