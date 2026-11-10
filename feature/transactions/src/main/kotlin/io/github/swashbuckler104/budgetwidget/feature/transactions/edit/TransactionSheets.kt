package io.github.swashbuckler104.budgetwidget.feature.transactions.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.data.edit.ManualTransaction
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEdit
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.MoneyText
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionDetail
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.accepts
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.core.model.parseInrToPaise
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the edit sheet can do besides saving. */
internal class EditActions(
    val onSave: (TransactionEdit) -> Unit,
    val onIgnore: () -> Unit,
    val onUnignore: () -> Unit,
    val onDelete: () -> Unit,
    val onReportWrongAmount: () -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTransactionSheet(
    detail: TransactionDetail,
    categories: Map<Long, Category>,
    groups: List<CategoryGroup>,
    actions: EditActions,
) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        EditTransactionContent(detail, categories, groups, actions)
    }
}

/** The edit form (SCOPE.md F7 "Edit sheet"). State is saved per transaction id. */
@Composable
internal fun EditTransactionContent(
    detail: TransactionDetail,
    categories: Map<Long, Category>,
    groups: List<CategoryGroup>,
    actions: EditActions,
) {
    val t = detail.transaction
    val manual = t.source == TransactionSource.MANUAL
    val original = t.categoryId?.let(categories::get)
    var type by rememberSaveable(t.id) { mutableStateOf(EntryType.of(t.direction, original)) }
    var transferDirection by rememberSaveable(t.id) { mutableStateOf(t.direction) }
    var categoryId by rememberSaveable(t.id) { mutableStateOf(t.categoryId) }
    var name by rememberSaveable(t.id) { mutableStateOf(t.merchantDisplay.orEmpty()) }
    var applyToAll by rememberSaveable(t.id) { mutableStateOf(false) }
    var note by rememberSaveable(t.id) { mutableStateOf(t.note.orEmpty()) }
    var remember by rememberSaveable(t.id) { mutableStateOf(true) }
    var amountText by rememberSaveable(t.id) { mutableStateOf(formatInr(t.amountPaise).removePrefix("₹")) }
    var occurredAt by rememberSaveable(t.id) { mutableLongStateOf(t.occurredAt) }
    var showSms by rememberSaveable(t.id) { mutableStateOf(false) }

    val direction = type.direction(transferDirection)
    // A category that no longer fits the type must be chosen again.
    val category = categoryId?.let(categories::get)?.takeIf { it.kind == type.kind && it.kind.accepts(direction) }
    val amount = if (manual) parseInrToPaise(amountText) else t.amountPaise
    val needsCategory = t.status == TransactionStatus.CATEGORIZED && category == null
    val canSave = amount != null && !needsCategory

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Text(
            stringResource(R.string.edit_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (manual) {
            AmountField(amountText, { amountText = it }, error = amount == null)
            DateTimeRow(occurredAt, { occurredAt = it })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoneyText(
                    t.amountPaise,
                    direction,
                    style = MaterialTheme.typography.headlineMedium,
                    emphasizeSpend = true,
                )
                Text(
                    listOfNotNull(
                        t.bankName,
                        t.accountLast4?.let { "··$it" },
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                            .format(Instant.ofEpochMilli(t.occurredAt).atZone(ZoneId.systemDefault())),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FormSection(stringResource(R.string.edit_type)) {
            TypeSelector(type, { type = it })
            if (type == EntryType.TRANSFER) TransferDirection(transferDirection) { transferDirection = it }
        }
        FormSection(stringResource(R.string.edit_category)) {
            CategoryField(groups, type, category, onPick = { categoryId = it.id })
            if (needsCategory) {
                Text(
                    stringResource(R.string.edit_category_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        FormSection(stringResource(R.string.edit_merchant)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                placeholder = { Text(stringResource(R.string.transactions_unknown_merchant)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (t.merchantKey != null && name.trim() != t.merchantDisplay.orEmpty()) {
                CheckRow(stringResource(R.string.edit_apply_to_all), applyToAll) { applyToAll = it }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(MAX_NOTE) },
            label = { Text(stringResource(R.string.edit_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (t.merchantKey != null && category != null && category.id != t.categoryId) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.edit_remember, t.merchantDisplay ?: name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = remember, onCheckedChange = { remember = it })
            }
        }

        if (detail.smsBody != null) SmsBlock(detail, showSms, { showSms = it }, actions.onReportWrongAmount)

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                manual -> TextButton(onClick = actions.onDelete) {
                    Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                }

                t.status == TransactionStatus.IGNORED ->
                    OutlinedButton(onClick = actions.onUnignore) { Text(stringResource(R.string.transactions_restore)) }

                else -> OutlinedButton(onClick = actions.onIgnore) {
                    Text(stringResource(R.string.transactions_ignore))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = canSave,
                onClick = {
                    actions.onSave(
                        TransactionEdit(
                            id = t.id,
                            direction = direction,
                            categoryId = category?.id,
                            note = note,
                            merchantName = name,
                            applyNameToMerchant = applyToAll,
                            rememberMerchant = remember,
                            amountPaise = if (manual) amount else null,
                            occurredAt = if (manual) occurredAt else null,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.edit_save)) }
        }
    }
}

@Composable
private fun SmsBlock(detail: TransactionDetail, open: Boolean, onOpen: (Boolean) -> Unit, onReport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onOpen(!open) }) {
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            Text(stringResource(R.string.edit_original_sms))
        }
        if (open) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    detail.smsSender?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    SelectionContainer {
                        Text(
                            detail.smsBody.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
            TextButton(onClick = onReport) { Text(stringResource(R.string.edit_report_amount)) }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheck),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Add manual transaction" (SCOPE.md F7): amount, type, category, date/time, merchant and note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTransactionSheet(
    groups: List<CategoryGroup>,
    categories: Map<Long, Category>,
    nowMillis: Long,
    onAdd: (ManualTransaction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AddTransactionContent(groups, categories, nowMillis, onAdd)
    }
}

@Composable
internal fun AddTransactionContent(
    groups: List<CategoryGroup>,
    categories: Map<Long, Category>,
    nowMillis: Long,
    onAdd: (ManualTransaction) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    var transferDirection by rememberSaveable { mutableStateOf(Direction.OUT) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var occurredAt by rememberSaveable { mutableLongStateOf(nowMillis) }
    var triedSave by rememberSaveable { mutableStateOf(false) }

    val direction = type.direction(transferDirection)
    val category = categoryId?.let(categories::get)?.takeIf { it.kind == type.kind }
    val amount = parseInrToPaise(amountText)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Text(
            stringResource(R.string.add_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        AmountField(amountText, { amountText = it }, error = triedSave && amount == null)
        FormSection(stringResource(R.string.edit_type)) {
            TypeSelector(type, { type = it })
            if (type == EntryType.TRANSFER) TransferDirection(transferDirection) { transferDirection = it }
        }
        FormSection(stringResource(R.string.edit_category)) {
            CategoryField(groups, type, category, onPick = { categoryId = it.id })
            if (triedSave && category == null) {
                Text(
                    stringResource(R.string.edit_category_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        FormSection(stringResource(R.string.add_when)) { DateTimeRow(occurredAt, { occurredAt = it }) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            label = { Text(stringResource(R.string.add_merchant)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(MAX_NOTE) },
            label = { Text(stringResource(R.string.edit_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                triedSave = true
                if (amount != null && category != null) {
                    onAdd(ManualTransaction(amount, direction, category.id, name, note, occurredAt))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.add_save)) }
    }
}

private const val MAX_NAME = 60
private const val MAX_NOTE = 500
