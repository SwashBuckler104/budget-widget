package io.github.swashbuckler104.budgetwidget.feature.transactions.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryPicker
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.tabular
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The edit sheet's type toggle (SCOPE.md F7 "Expense / Income / Transfer"). */
internal enum class EntryType(val kind: CategoryKind) {
    EXPENSE(CategoryKind.EXPENSE),
    INCOME(CategoryKind.INCOME),
    TRANSFER(CategoryKind.TRANSFER),
    ;

    /** Money direction for this type; a transfer keeps [transferDirection]. */
    fun direction(transferDirection: Direction): Direction = when (this) {
        EXPENSE -> Direction.OUT
        INCOME -> Direction.IN
        TRANSFER -> transferDirection
    }

    companion object {
        fun of(direction: Direction, category: Category?): EntryType = when {
            category?.kind == CategoryKind.TRANSFER -> TRANSFER
            direction == Direction.IN -> INCOME
            else -> EXPENSE
        }
    }
}

/** Top-level groups whose kind fits [type] (subcategories share their parent's kind). */
internal fun List<CategoryGroup>.forType(type: EntryType): List<CategoryGroup> =
    filter { it.category.kind == type.kind }

@Composable
internal fun TypeSelector(type: EntryType, onType: (EntryType) -> Unit, modifier: Modifier = Modifier) {
    val labels = mapOf(
        EntryType.EXPENSE to R.string.edit_type_expense,
        EntryType.INCOME to R.string.edit_type_income,
        EntryType.TRANSFER to R.string.edit_type_transfer,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        EntryType.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == type,
                onClick = { onType(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, EntryType.entries.size),
                label = { Text(stringResource(labels.getValue(entry))) },
            )
        }
    }
}

/** Money out / Money in, for transfers (which can go either way). */
@Composable
internal fun TransferDirection(direction: Direction, onDirection: (Direction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = direction == Direction.OUT,
            onClick = { onDirection(Direction.OUT) },
            label = { Text(stringResource(R.string.edit_money_out)) },
        )
        FilterChip(
            selected = direction == Direction.IN,
            onClick = { onDirection(Direction.IN) },
            label = { Text(stringResource(R.string.edit_money_in)) },
        )
    }
}

/**
 * The chosen category with a Change toggle that opens the full picker, limited to [type]. With
 * nothing chosen the picker starts open.
 */
@Composable
internal fun CategoryField(
    groups: List<CategoryGroup>,
    type: EntryType,
    selected: Category?,
    onPick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(selected == null) }
    // A type switch that invalidates the category opens the picker right away.
    LaunchedEffect(selected == null) { if (selected == null) open = true }
    Column(modifier = modifier) {
        OutlinedCard(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                if (selected != null) {
                    CategoryAvatar(selected.emoji, selected.colorArgb, size = 32.dp)
                    Text(selected.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                } else {
                    Text(
                        stringResource(R.string.edit_choose_category),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (open) R.string.edit_hide_categories else R.string.edit_change,
                    ),
                )
            }
        }
        if (open) {
            CategoryPicker(
                groups = groups.forType(type),
                onPick = {
                    onPick(it)
                    open = false
                },
                selectedId = selected?.id,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Big rupee amount input with a numeric keyboard. */
@Composable
internal fun AmountField(text: String, onText: (String) -> Unit, error: Boolean, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            onText(value.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_AMOUNT_CHARS))
        },
        label = { Text(stringResource(R.string.edit_amount)) },
        prefix = { Text("₹") },
        isError = error,
        supportingText = if (error) {
            { Text(stringResource(R.string.edit_amount_invalid)) }
        } else {
            null
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.tabular(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Date and time chips that open the Material pickers. [epochMillis] is the instant (UTC). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeRow(epochMillis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
    var picking by rememberSaveable { mutableStateOf(Picking.NONE) }
    val locale = LocalConfiguration.current.locales[0]
    val dateText = local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val timeText = local.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        AssistChip(
            onClick = { picking = Picking.DATE },
            label = { Text(dateText) },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        )
        AssistChip(
            onClick = { picking = Picking.TIME },
            label = { Text(timeText) },
            leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        )
    }
    when (picking) {
        Picking.DATE -> {
            // The date picker works in UTC midnights.
            val state = rememberDatePickerState(
                initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = PastAndToday,
            )
            DatePickerDialog(
                onDismissRequest = { picking = Picking.NONE },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utc ->
                            val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(date.atTime(local.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                        }
                        picking = Picking.NONE
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { picking = Picking.NONE }) { Text(stringResource(android.R.string.cancel)) }
                },
            ) { DatePicker(state = state) }
        }

        Picking.TIME -> {
            val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
            AlertDialog(
                onDismissRequest = { picking = Picking.NONE },
                confirmButton = {
                    TextButton(onClick = {
                        val time = LocalTime.of(state.hour, state.minute)
                        onChange(local.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli())
                        picking = Picking.NONE
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { picking = Picking.NONE }) { Text(stringResource(android.R.string.cancel)) }
                },
                text = { TimePicker(state = state) },
            )
        }

        Picking.NONE -> Unit
    }
}

private enum class Picking { NONE, DATE, TIME }

/** No future dates: a spend that hasn't happened yet would skew today's and the month's totals. */
@OptIn(ExperimentalMaterial3Api::class)
private object PastAndToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

/** A labelled column block for sheets. */
@Composable
internal fun FormSection(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

private const val MAX_AMOUNT_CHARS = 18
