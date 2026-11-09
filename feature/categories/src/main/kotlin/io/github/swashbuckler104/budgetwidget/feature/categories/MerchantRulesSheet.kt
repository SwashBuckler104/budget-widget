package io.github.swashbuckler104.budgetwidget.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryPicker
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.MerchantRule

/** Learned merchant rules of one category: view, delete, move (SCOPE.md F7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MerchantRulesSheet(
    category: Category,
    rules: List<MerchantRule>,
    groups: List<CategoryGroup>,
    onDelete: (MerchantRule) -> Unit,
    onMove: (MerchantRule, Category) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moving by remember { mutableStateOf<MerchantRule?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.categories_rules_title, category.emoji, category.name),
                style = MaterialTheme.typography.titleLarge,
            )
            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.categories_rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rules.forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.merchantKey, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            pluralStringResource(
                                R.plurals.categories_rule_confirmed,
                                rule.confirmCount,
                                rule.confirmCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { moving = rule }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.DriveFileMove,
                            contentDescription = stringResource(R.string.categories_rule_move, rule.merchantKey),
                        )
                    }
                    IconButton(onClick = { onDelete(rule) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.categories_rule_delete, rule.merchantKey),
                        )
                    }
                }
            }
        }
    }
    moving?.let { rule ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(stringResource(R.string.categories_rule_move_title, rule.merchantKey)) },
            text = {
                CategoryPicker(
                    groups = groups,
                    selectedId = rule.categoryId,
                    onPick = { target ->
                        onMove(rule, target)
                        moving = null
                    },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { moving = null }) { Text(stringResource(R.string.categories_cancel)) }
            },
        )
    }
}
