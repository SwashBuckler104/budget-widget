package io.github.swashbuckler104.budgetwidget.feature.transactions.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.MoneyText
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import kotlinx.coroutines.launch

/**
 * One transaction in the list: category avatar, merchant, `Category · Account · 8:42 PM`, signed
 * amount. Ignored ones are dimmed and struck through; a selected row shows a check instead of the
 * avatar. Tap edits, long-press selects (SCOPE.md F7).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    time: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ignored = transaction.status == TransactionStatus.IGNORED
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        label = "rowBackground",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.transactions_select),
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Leading(transaction, category, selected)
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (ignored) DIMMED else 1f),
        ) {
            Text(
                merchantName(transaction),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (ignored) TextDecoration.LineThrough else null,
            )
            Text(
                metaLine(transaction, category, time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MoneyText(
            transaction.amountPaise,
            transaction.direction,
            style = MaterialTheme.typography.titleSmall,
            muted = ignored,
        )
    }
}

@Composable
private fun Leading(transaction: Transaction, category: Category?, selected: Boolean) {
    when {
        selected -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }

        category != null -> CategoryAvatar(category.emoji, category.colorArgb)

        else -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val icon = when {
                transaction.status == TransactionStatus.IGNORED -> Icons.Rounded.DoNotDisturbOn
                transaction.source == TransactionSource.MANUAL -> Icons.Rounded.Payments
                else -> Icons.Rounded.Sell
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun metaLine(transaction: Transaction, category: Category?, time: String): String {
    val status = when {
        transaction.status == TransactionStatus.PENDING -> stringResource(R.string.transactions_to_sort)
        transaction.status == TransactionStatus.IGNORED -> stringResource(R.string.transactions_ignored)
        category != null && transaction.autoCategorized -> stringResource(R.string.transactions_auto, category.name)
        else -> category?.name
    }
    return listOfNotNull(status, accountLabel(transaction), time).joinToString(" · ")
}

@Composable
internal fun accountLabel(transaction: Transaction): String? = if (transaction.source == TransactionSource.MANUAL) {
    stringResource(R.string.transactions_cash)
} else {
    listOfNotNull(
        transaction.bankName,
        transaction.accountLast4?.let {
            "··$it"
        },
    ).joinToString(" ").ifEmpty { null }
}

@Composable
internal fun merchantName(transaction: Transaction): String =
    transaction.merchantDisplay ?: stringResource(R.string.transactions_unknown_merchant)

/**
 * A pending transaction in the pinned inbox: tap sorts it in the Quick-Sort sheet. A possible
 * duplicate carries "Looks like the same payment as ₹X at 8:40 PM" with Merge / Keep both.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InboxCard(
    entry: InboxEntry,
    time: String,
    duplicateTime: String?,
    selected: Boolean,
    onSort: () -> Unit,
    onLongClick: () -> Unit,
    onMerge: () -> Unit,
    onKeepBoth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = entry.transaction
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = onSort,
                onClickLabel = stringResource(R.string.transactions_sort_action),
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.transactions_select),
            )
            .semantics { this.selected = selected },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    merchantName(t),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(
                    t.amountPaise,
                    t.direction,
                    style = MaterialTheme.typography.titleMedium,
                    emphasizeSpend = true,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(accountLabel(t), time).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.overdue) Tag(stringResource(R.string.transactions_overdue), MaterialTheme.colorScheme.error)
                if (t.isBackfill) {
                    Tag(stringResource(R.string.transactions_backfill), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val original = entry.duplicateOf
            if (original != null && duplicateTime != null) {
                DuplicateBanner(original, duplicateTime, onMerge, onKeepBoth)
            }
        }
    }
}

@Composable
private fun DuplicateBanner(original: Transaction, time: String, onMerge: () -> Unit, onKeepBoth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp),
    ) {
        Text(
            stringResource(R.string.transactions_duplicate, formatInr(original.amountPaise), time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onKeepBoth) { Text(stringResource(R.string.transactions_keep_both)) }
            OutlinedButton(onClick = onMerge) { Text(stringResource(R.string.transactions_merge)) }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Swipe actions (SCOPE.md F7): right = categorize (opens the picker), left = ignore, or back to
 * the inbox for an ignored one. The row springs back; the action happens with an undo snackbar.
 * TalkBack users get the same two actions as custom actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeActions(
    transaction: Transaction,
    enabled: Boolean,
    onCategorize: () -> Unit,
    onIgnoreOrRestore: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ignored = transaction.status == TransactionStatus.IGNORED
    val categorizeLabel = stringResource(R.string.transactions_categorize)
    val ignoreLabel = stringResource(if (ignored) R.string.transactions_restore else R.string.transactions_ignore)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onCategorize()
                SwipeToDismissBoxValue.EndToStart -> onIgnoreOrRestore()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val toEnd = direction == SwipeToDismissBoxValue.StartToEnd
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.surfaceContainerHighest
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    val icon = when {
                        toEnd -> Icons.Rounded.Sell
                        ignored -> Icons.AutoMirrored.Rounded.Undo
                        else -> Icons.Rounded.DoNotDisturbOn
                    }
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (toEnd) categorizeLabel else ignoreLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        modifier = modifier.semantics {
            if (enabled) {
                customActions = listOf(
                    CustomAccessibilityAction(categorizeLabel) {
                        onCategorize()
                        true
                    },
                    CustomAccessibilityAction(ignoreLabel) {
                        onIgnoreOrRestore()
                        true
                    },
                )
            }
        },
    ) { content() }
}

/** `-₹1,240 · +₹500` for a day header; spending only when there was no income. */
@Composable
internal fun DayTotal(spentPaise: Long, incomePaise: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (spentPaise > 0) MoneyText(spentPaise, Direction.OUT, style = MaterialTheme.typography.labelLarge)
        if (incomePaise > 0) {
            MoneyText(incomePaise, Direction.IN, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val DIMMED = 0.55f
