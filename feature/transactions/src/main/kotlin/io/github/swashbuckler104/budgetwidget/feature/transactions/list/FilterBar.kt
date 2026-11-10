package io.github.swashbuckler104.budgetwidget.feature.transactions.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.data.list.StatusFilter
import io.github.swashbuckler104.budgetwidget.core.data.list.TransactionFilter
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryChip
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryPicker
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.accepts
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Filter chips under the search field (SCOPE.md F7): status, type, category, account, dates. */
@Composable
internal fun FilterBar(
    filter: TransactionFilter,
    categories: Map<Long, Category>,
    groups: List<CategoryGroup>,
    accounts: List<Account>,
    onFilter: ((TransactionFilter) -> TransactionFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        val statusLabels = mapOf(
            StatusFilter.PENDING to R.string.filter_status_pending,
            StatusFilter.CATEGORIZED to R.string.filter_status_sorted,
            StatusFilter.AUTO_SORTED to R.string.filter_status_auto,
            StatusFilter.IGNORED to R.string.filter_status_ignored,
        )
        MenuChip(
            label = filter.status?.let { stringResource(statusLabels.getValue(it)) }
                ?: stringResource(R.string.filter_status),
            active = filter.status != null,
            options = listOf<Pair<String, StatusFilter?>>(stringResource(R.string.filter_any) to null) +
                StatusFilter.entries.map { stringResource(statusLabels.getValue(it)) to it },
            onPick = { status -> onFilter { it.copy(status = status) } },
        )
        MenuChip(
            label = when (filter.direction) {
                Direction.OUT -> stringResource(R.string.filter_money_out)
                Direction.IN -> stringResource(R.string.filter_money_in)
                null -> stringResource(R.string.filter_type)
            },
            active = filter.direction != null,
            options = listOf(
                stringResource(R.string.filter_any) to null,
                stringResource(R.string.filter_money_out) to Direction.OUT,
                stringResource(R.string.filter_money_in) to Direction.IN,
            ),
            onPick = { direction -> onFilter { it.copy(direction = direction) } },
        )
        val category = filter.categoryId?.let(categories::get)
        FilterChip(
            selected = category != null,
            onClick = { pickingCategory = true },
            label = { Text(category?.let { "${it.emoji} ${it.name}" } ?: stringResource(R.string.filter_category)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        if (accounts.isNotEmpty()) {
            MenuChip(
                label = accounts.firstOrNull { it.id == filter.accountId }?.label
                    ?: stringResource(R.string.filter_account),
                active = filter.accountId != null,
                options = listOf<Pair<String, Long?>>(stringResource(R.string.filter_any) to null) +
                    accounts.filterNot { it.hidden }.map { it.label to it.id },
                onPick = { id -> onFilter { it.copy(accountId = id) } },
            )
        }
        FilterChip(
            selected = filter.fromMillis != null,
            onClick = { pickingDates = true },
            label = { Text(dateRangeLabel(filter) ?: stringResource(R.string.filter_dates)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        if (filter.copy(query = "").isActive) {
            InputChip(
                selected = false,
                onClick = onClear,
                label = { Text(stringResource(R.string.filter_clear)) },
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
            )
        }
    }
    if (pickingCategory) {
        CategoryFilterSheet(
            groups = groups,
            selectedId = filter.categoryId,
            onPick = { id ->
                onFilter { it.copy(categoryId = id) }
                pickingCategory = false
            },
            onDismiss = { pickingCategory = false },
        )
    }
    if (pickingDates) {
        DateRangeDialog(
            filter = filter,
            onRange = { from, to -> onFilter { it.copy(fromMillis = from, toMillis = to) } },
            onDismiss = { pickingDates = false },
        )
    }
}

@Composable
private fun <T> MenuChip(label: String, active: Boolean, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onPick(value)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun dateRangeLabel(filter: TransactionFilter): String? {
    val from = filter.fromMillis ?: return null
    val zone = ZoneId.systemDefault()
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
    // toMillis is exclusive: the next day's start.
    val end = filter.toMillis?.let { Instant.ofEpochMilli(it - 1).atZone(zone).toLocalDate() } ?: start
    return if (start == end) start.format(format) else "${start.format(format)} - ${end.format(format)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(filter: TransactionFilter, onRange: (Long?, Long?) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()

    // The picker works in UTC midnights; the filter in local-day instants.
    fun toUtc(epochMillis: Long?): Long? = epochMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    fun localDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toUtc(filter.fromMillis),
        initialSelectedEndDateMillis = toUtc(filter.toMillis?.minus(1)),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.let(::localDate)
                    val end = state.selectedEndDateMillis?.let(::localDate) ?: start
                    onRange(
                        start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                        end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                    )
                    onDismiss()
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onRange(null, null)
                onDismiss()
            }) { Text(stringResource(R.string.filter_any_time)) }
        },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

/** Pick a whole top-level category (its subcategories included) or one leaf. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterSheet(
    groups: List<CategoryGroup>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter_category),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.filter_any)) }
            }
            groups.filterNot { it.category.archived }.forEach { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(group.category.id) }
                        .padding(vertical = 4.dp),
                ) {
                    CategoryAvatar(group.category.emoji, group.category.colorArgb, size = 32.dp)
                    Text(
                        group.category.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (group.category.id == selectedId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                val leaves = group.children.filterNot { it.archived }
                if (leaves.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        leaves.forEach { leaf ->
                            CategoryChip(leaf, onClick = { onPick(leaf.id) }, selected = leaf.id == selectedId)
                        }
                    }
                }
            }
        }
    }
}

/** The picker behind swipe-right and bulk "Categorize": every leaf that fits [direction] (all for mixed). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickCategorySheet(
    title: String,
    groups: List<CategoryGroup>,
    direction: Direction?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            CategoryPicker(
                groups = if (direction == null) groups else groups.filter { it.category.kind.accepts(direction) },
                onPick = onPick,
            )
        }
    }
}
