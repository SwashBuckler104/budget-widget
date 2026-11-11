package io.github.swashbuckler104.budgetwidget.feature.analysis

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.data.analysis.AnalysisPeriod
import io.github.swashbuckler104.budgetwidget.core.data.analysis.BucketUnit
import io.github.swashbuckler104.budgetwidget.core.data.analysis.CategorySpend
import io.github.swashbuckler104.budgetwidget.core.data.analysis.MerchantSpend
import io.github.swashbuckler104.budgetwidget.core.data.analysis.PeriodAnalysis
import io.github.swashbuckler104.budgetwidget.core.designsystem.chart.BarChart
import io.github.swashbuckler104.budgetwidget.core.designsystem.chart.DonutChart
import io.github.swashbuckler104.budgetwidget.core.designsystem.chart.DonutSlice
import io.github.swashbuckler104.budgetwidget.core.designsystem.chart.LineChart
import io.github.swashbuckler104.budgetwidget.core.designsystem.chart.LineSeries
import io.github.swashbuckler104.budgetwidget.core.designsystem.chart.ProportionBar
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.EmptyState
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.StatCard
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BudgetTheme
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.tabular
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.core.model.formatInrRounded
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Opens the transaction list for part of the shown period: a category (0 = any) and/or a merchant
 * [query], in `[fromMillis, toMillis)`, optionally one account (0 = any).
 */
data class AnalysisDrillDown(
    val title: String,
    val fromMillis: Long,
    val toMillis: Long,
    val categoryId: Long = 0,
    val accountId: Long = 0,
    val query: String = "",
    /** One merchant exactly; set for merchant drill-downs. */
    val merchantKey: String = "",
    /** `OUT` for spending drill-downs, `IN` for income ones (the totals count one way), "" for both. */
    val direction: String = "",
)

/**
 * Tab 1 (SCOPE.md F7): a week, month, year or custom range; summary, spending and income donuts
 * with drill-down, bars, trend and merchants.
 */
@Composable
fun AnalysisRoute(
    onSortInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onDrillDown: (AnalysisDrillDown) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    AnalysisScreen(
        state = state,
        streak = streak,
        actions = AnalysisActions(
            onShift = viewModel::shift,
            onSelectKind = viewModel::selectKind,
            onSelectRange = viewModel::selectRange,
            onSelectAccount = viewModel::selectAccount,
            onSortInbox = onSortInbox,
            onOpenSettings = onOpenSettings,
            onDrillDown = { title, categoryId, merchant, income ->
                val (from, to) = viewModel.periodRange()
                onDrillDown(
                    AnalysisDrillDown(
                        title = title,
                        fromMillis = from,
                        toMillis = to,
                        categoryId = categoryId,
                        accountId = state.accountId ?: 0,
                        // A merchant without a key can only be found by its name.
                        query = if (merchant != null && merchant.merchantKey == null) merchant.name.orEmpty() else "",
                        merchantKey = merchant?.merchantKey.orEmpty(),
                        direction = if (income) "IN" else "OUT",
                    ),
                )
            },
        ),
        modifier = modifier,
    )
}

internal class AnalysisActions(
    val onShift: (Long) -> Unit,
    val onSelectKind: (PeriodKind) -> Unit,
    val onSelectRange: (start: LocalDate, endInclusive: LocalDate) -> Unit,
    val onSelectAccount: (Long?) -> Unit,
    val onSortInbox: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Title, category id (0 = any), a merchant (null = any) and whether it's money in. */
    val onDrillDown: (String, Long, MerchantSpend?, Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisScreen(
    state: AnalysisUiState,
    streak: Int,
    actions: AnalysisActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.analysis_title)) },
            actions = {
                IconButton(onClick = actions.onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.analysis_settings))
                }
            },
        )
    }) { padding ->
        val swipe = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Swipe between periods (SCOPE.md F7 "swipe/arrow").
                .pointerInput(state.canGoBack, state.canGoForward) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            when {
                                drag > swipe && state.canGoBack -> actions.onShift(-1)
                                drag < -swipe && state.canGoForward -> actions.onShift(1)
                            }
                        },
                    ) { _, amount -> drag += amount }
                },
        ) {
            item(key = "switcher") { PeriodSwitcher(state, actions) }
            if (streak > 0 ||
                state.pending > 0
            ) {
                item(key = "status") { StatusRow(streak, state.pending, actions.onSortInbox) }
            }
            val analysis = state.analysis
            when {
                state.loading || analysis == null -> Unit

                analysis.transactionCount == 0 -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = stringResource(R.string.analysis_empty_title),
                        body = stringResource(R.string.analysis_empty_body),
                    )
                }

                else -> {
                    item(key = "summary") { Summary(analysis, state.isCurrent) }
                    if (analysis.categories.isNotEmpty() || analysis.incomeCategories.isNotEmpty()) {
                        item(key = "categories") { CategoriesCard(analysis, state.accountId, actions) }
                    }
                    item(key = "daily") { DailyCard(analysis) }
                    item(key = "trend") { TrendCard(analysis) }
                    if (analysis.topMerchants.isNotEmpty()) {
                        item(key = "merchants") { MerchantsCard(analysis.topMerchants, analysis.spentPaise, actions) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSwitcher(state: AnalysisUiState, actions: AnalysisActions) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val kinds = PeriodKind.entries
    val kindDescription = stringResource(R.string.analysis_period_kind)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = kindDescription },
        ) {
            kinds.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = state.period.kind == kind,
                    onClick = { if (kind == PeriodKind.CUSTOM) picking = true else actions.onSelectKind(kind) },
                    shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                    icon = {},
                    label = { Text(stringResource(kind.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val custom = state.period is AnalysisPeriod.Custom
            if (!custom) {
                IconButton(onClick = { actions.onShift(-1) }, enabled = state.canGoBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        stringResource(R.string.analysis_previous_period),
                    )
                }
            }
            AnimatedContent(
                targetState = state.period,
                transitionSpec = {
                    val forward = targetState.start > initialState.start
                    slideInHorizontally { if (forward) it else -it } togetherWith
                        slideOutHorizontally { if (forward) -it else it }
                },
                label = "period",
                modifier = Modifier.weight(1f),
            ) { period ->
                val label = periodLabel(period, state.today)
                if (period is AnalysisPeriod.Custom) {
                    val change = stringResource(R.string.analysis_change_range, label)
                    TextButton(
                        onClick = { picking = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = change },
                    ) {
                        Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.EditCalendar, contentDescription = null)
                    }
                } else {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { heading() },
                    )
                }
            }
            if (!custom) {
                IconButton(onClick = { actions.onShift(1) }, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.analysis_next_period))
                }
            }
        }
        if (picking) {
            RangePickerDialog(
                initial = state.period,
                today = state.today,
                onConfirm = { start, end ->
                    actions.onSelectRange(start, end)
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
        if (state.accounts.size > 1) AccountFilter(state.accounts, state.accountId, actions.onSelectAccount)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    initial: AnalysisPeriod,
    today: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC midnights; local dates map to them one to one.
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val todayMillis = today.utcMillis()
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.utcMillis(),
        initialSelectedEndDateMillis = minOf(initial.endInclusive, today).utcMillis(),
        yearRange = IntRange(today.year - YEARS_BACK, today.year),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis

            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val start = picker.selectedStartDateMillis
            TextButton(
                enabled = start != null,
                onClick = {
                    if (start != null) onConfirm(start.utcDate(), (picker.selectedEndDateMillis ?: start).utcDate())
                },
            ) { Text(stringResource(R.string.analysis_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.analysis_cancel)) } },
    ) {
        DateRangePicker(
            state = picker,
            title = {
                Text(
                    stringResource(R.string.analysis_pick_range),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

private val PeriodKind.label: Int
    get() = when (this) {
        PeriodKind.WEEK -> R.string.analysis_period_week
        PeriodKind.MONTH -> R.string.analysis_period_month
        PeriodKind.YEAR -> R.string.analysis_period_year
        PeriodKind.CUSTOM -> R.string.analysis_period_range
    }

@Composable
private fun AccountFilter(accounts: List<Account>, selected: Long?, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = {
                Text(
                    accounts.firstOrNull {
                        it.id == selected
                    }?.label ?: stringResource(R.string.analysis_all_accounts),
                )
            },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.analysis_all_accounts)) }, onClick = {
                onSelect(null)
                open = false
            })
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text(account.label) }, onClick = {
                    onSelect(account.id)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun StatusRow(streak: Int, pending: Int, onSortInbox: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (streak > 0) {
            AssistChip(onClick = {}, label = { Text(pluralStringResource(R.plurals.analysis_streak, streak, streak)) })
        }
        if (pending > 0) {
            AssistChip(
                onClick = onSortInbox,
                label = { Text(pluralStringResource(R.plurals.analysis_inbox_banner, pending, pending)) },
            )
        }
    }
}

/** Spent as the hero, with the comparison; income, net and average per day beneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(analysis: PeriodAnalysis, current: Boolean) {
    val money = BudgetTheme.money
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.analysis_spent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    formatInr(analysis.spentPaise),
                    style = MaterialTheme.typography.displaySmall.tabular(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                analysis.changePercent?.let { change ->
                    val more = change > 0
                    val arrow = when {
                        more -> "▲ "
                        change < 0 -> "▼ "
                        else -> ""
                    }
                    val text = comparisonText(analysis.period, current, arrow + "${abs(change)}%")
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background((if (more) money.spendContainer else money.incomeContainer))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // Three across at normal sizes; one per row once large text can't fit a third of the width.
        val perRow = if (LocalDensity.current.fontScale > LARGE_FONT) 1 else STATS
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = perRow,
        ) {
            StatCard(
                compact = true,
                label = stringResource(R.string.analysis_income),
                value = formatInrRounded(analysis.incomePaise),
                valueColor = if (analysis.incomePaise > 0) money.income else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val net = analysis.netPaise
            StatCard(
                compact = true,
                label = stringResource(R.string.analysis_net),
                value = (if (net > 0) "+" else "") + formatInrRounded(net),
                valueColor = if (net >= 0) money.income else money.spend,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                compact = true,
                label = stringResource(R.string.analysis_per_day),
                value = formatInrRounded(analysis.averagePerDayPaise),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Donut by top-level category, for spending or income; a tapped slice (or row) shows its
 * subcategories and a link to the list.
 */
@Composable
private fun CategoriesCard(analysis: PeriodAnalysis, accountId: Long?, actions: AnalysisActions) {
    // Start on income only when there's no spending to show.
    var income by rememberSaveable { mutableStateOf(false) }
    val showIncome = (income && analysis.incomeCategories.isNotEmpty()) || analysis.categories.isEmpty()
    // Remembered by category (TO_SORT_ID for "To sort"), not slice position: sorting an item or
    // switching account reorders the slices.
    var selectedId by rememberSaveable(analysis.period, accountId, showIncome) { mutableStateOf<Long?>(null) }
    val toSortName = stringResource(R.string.analysis_to_sort)
    val toSortColor = MaterialTheme.colorScheme.outline
    val slices = if (showIncome) analysis.incomeCategories else analysis.categories
    val total = if (showIncome) analysis.incomePaise else analysis.spentPaise
    val pick = selectedId?.let { id ->
        slices.indexOfFirst { (it.category?.id ?: TO_SORT_ID) == id }
    }?.takeIf { it >= 0 }
    val select: (Int?) -> Unit = { index -> selectedId = index?.let { slices[it].category?.id ?: TO_SORT_ID } }
    val drillDown = { title: String, categoryId: Long -> actions.onDrillDown(title, categoryId, null, showIncome) }
    ChartCard(stringResource(if (showIncome) R.string.analysis_income_categories else R.string.analysis_categories)) {
        if (analysis.categories.isNotEmpty() && analysis.incomeCategories.isNotEmpty()) {
            DirectionToggle(showIncome, onChange = { income = it })
        }
        val description = slices.joinToString(", ") { "${nameOf(it, toSortName)} ${formatInr(it.totalPaise)}" }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            DonutChart(
                slices = slices.map { DonutSlice(it.totalPaise, colorOf(it, toSortColor)) },
                selected = pick,
                onSelect = select,
                description = stringResource(
                    if (showIncome) R.string.analysis_income_donut_description else R.string.analysis_donut_description,
                    description,
                ),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth(DONUT_WIDTH)
                    .padding(vertical = 8.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val focus = pick?.let(slices::get)
                Text(
                    focus?.let { nameOf(it, toSortName) }
                        ?: stringResource(if (showIncome) R.string.analysis_received else R.string.analysis_spent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(CENTER_TEXT_WIDTH),
                    textAlign = TextAlign.Center,
                )
                Text(
                    formatInr(focus?.totalPaise ?: total),
                    style = MaterialTheme.typography.titleLarge.tabular(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        slices.forEachIndexed { index, slice ->
            SpendRow(
                emoji = slice.category?.emoji,
                color = colorOf(slice, toSortColor),
                name = nameOf(slice, toSortName),
                totalPaise = slice.totalPaise,
                ofPaise = total,
                selected = index == pick,
                onClick = { select(if (pick == index) null else index) },
            )
            if (index == pick) {
                SliceDetail(slice, toSortName, drillDown, actions.onSortInbox)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionToggle(income: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(false to R.string.analysis_show_spending, true to R.string.analysis_show_income).forEachIndexed {
                index,
                (value, label),
            ->
            SegmentedButton(
                selected = income == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun SliceDetail(
    slice: CategorySpend,
    toSortName: String,
    onDrillDown: (title: String, categoryId: Long) -> Unit,
    onSortInbox: () -> Unit,
) {
    val category = slice.category
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, bottom = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 4.dp),
    ) {
        slice.children.forEach { child ->
            SpendRow(
                emoji = child.category?.emoji,
                color = colorOf(child, MaterialTheme.colorScheme.outline),
                name = nameOf(child, toSortName),
                totalPaise = child.totalPaise,
                ofPaise = slice.totalPaise,
                selected = false,
                onClick = { child.category?.let { onDrillDown(it.name, it.id) } },
            )
        }
        if (category != null) {
            TextButton(
                onClick = { onDrillDown(category.name, category.id) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(pluralStringResource(R.plurals.analysis_see_transactions, slice.count, slice.count))
            }
        } else {
            TextButton(onClick = onSortInbox, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.analysis_sort_now))
            }
        }
    }
}

@Composable
private fun SpendRow(
    emoji: String?,
    color: Color,
    name: String,
    totalPaise: Long,
    ofPaise: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fraction = if (ofPaise > 0) totalPaise.toFloat() / ofPaise else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        CategoryAvatar(emoji ?: TO_SORT_EMOJI, color.toArgb(), size = 32.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatInr(totalPaise), style = MaterialTheme.typography.bodyMedium.tabular())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProportionBar(
                    fraction = fraction,
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(fraction * PERCENT).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 36.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun DailyCard(analysis: PeriodAnalysis) {
    val monthly = analysis.period.unit == BucketUnit.MONTH
    val average = analysis.averagePerBucketPaise
    ChartCard(stringResource(if (monthly) R.string.analysis_monthly else R.string.analysis_daily)) {
        BarChart(
            values = analysis.buckets,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            highlightColor = MaterialTheme.colorScheme.primary,
            highlight = analysis.todayBucket,
            reference = average.takeIf { it > 0 },
            referenceColor = MaterialTheme.colorScheme.tertiary,
            description = stringResource(
                if (monthly) R.string.analysis_monthly_description else R.string.analysis_daily_description,
                formatInr(analysis.buckets.maxOrNull() ?: 0),
                formatInrRounded(average),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        AxisLabels(axisLabels(analysis.period))
        Legend(
            listOf(
                MaterialTheme.colorScheme.tertiary to
                    stringResource(R.string.analysis_average, formatInrRounded(average)),
            ),
        )
    }
}

@Composable
private fun TrendCard(analysis: PeriodAnalysis) {
    val thisPeriod = MaterialTheme.colorScheme.primary
    val lastPeriod = MaterialTheme.colorScheme.outline
    val (thisLabel, lastLabel) = trendLabels(analysis.period)
    ChartCard(stringResource(R.string.analysis_trend)) {
        LineChart(
            series = listOf(
                LineSeries(analysis.previousCumulative, lastPeriod, dashed = true),
                LineSeries(analysis.cumulative, thisPeriod),
            ),
            days = maxOf(analysis.buckets.size, analysis.previousCumulative.size),
            description = stringResource(
                R.string.analysis_trend_description,
                formatInr(analysis.cumulative.lastOrNull() ?: 0),
                formatInr(analysis.previousCumulative.getOrNull(analysis.cumulative.size - 1) ?: 0),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        Legend(listOf(thisPeriod to thisLabel, lastPeriod to lastLabel))
    }
}

@Composable
private fun MerchantsCard(merchants: List<MerchantSpend>, spentPaise: Long, actions: AnalysisActions) {
    val unknown = stringResource(R.string.analysis_unknown_merchant)
    ChartCard(stringResource(R.string.analysis_top_merchants)) {
        merchants.forEachIndexed { index, merchant ->
            val name = merchant.name ?: unknown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = merchant.name != null) { actions.onDrillDown(name, 0, merchant, false) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.analysis_payments, merchant.count, merchant.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatInr(merchant.totalPaise), style = MaterialTheme.typography.bodyLarge.tabular())
                    if (spentPaise > 0) {
                        Text(
                            "${(merchant.totalPaise * PERCENT.toFloat() / spentPaise).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun AxisLabels(labels: List<String>) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        labels.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Legend(entries: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        entries.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun nameOf(slice: CategorySpend, toSort: String): String = slice.category?.name ?: toSort

private fun colorOf(slice: CategorySpend, toSort: Color): Color = slice.category?.let { Color(it.colorArgb) } ?: toSort

/** "This week", "November", "2026", "1 Oct - 11 Nov"; the year only when it isn't this year. */
@Composable
private fun periodLabel(period: AnalysisPeriod, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    fun day(date: LocalDate) =
        date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", locale))
    return when (period) {
        is AnalysisPeriod.Week -> when (period) {
            AnalysisPeriod.Week.of(today) -> stringResource(R.string.analysis_this_week)
            AnalysisPeriod.Week.of(today.minusWeeks(1)) -> stringResource(R.string.analysis_last_week)
            else -> "${day(period.start)} - ${day(period.endInclusive)}"
        }

        is AnalysisPeriod.Month -> period.month.format(
            DateTimeFormatter.ofPattern(if (period.month.year == today.year) "LLLL" else "LLLL yyyy", locale),
        )

        is AnalysisPeriod.Year -> period.year.toString()

        is AnalysisPeriod.Custom -> if (period.start == period.endInclusive) {
            day(period.start)
        } else {
            "${day(period.start)} - ${day(period.endInclusive)}"
        }
    }
}

/** First, middle and last bucket labels under the bar chart. */
@Composable
private fun axisLabels(period: AnalysisPeriod): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    val count = PeriodAnalysis.bucketCount(period)
    val indexes = listOf(0, count / 2, count - 1).distinct()
    val pattern = when {
        period.unit == BucketUnit.MONTH -> "MMM"
        period is AnalysisPeriod.Month -> "d"
        period is AnalysisPeriod.Week -> "EEE"
        else -> "d MMM"
    }
    val format = DateTimeFormatter.ofPattern(pattern, locale)
    return indexes.map { PeriodAnalysis.bucketStart(period, it).format(format) }
}

@Composable
private fun comparisonText(period: AnalysisPeriod, current: Boolean, change: String): String = when (period) {
    is AnalysisPeriod.Week -> stringResource(
        if (current) R.string.analysis_vs_same_weekday else R.string.analysis_vs_last_week,
        change,
    )

    is AnalysisPeriod.Month -> stringResource(
        if (current) R.string.analysis_vs_same_day else R.string.analysis_vs_last_month,
        change,
    )

    is AnalysisPeriod.Year -> stringResource(
        if (current) R.string.analysis_vs_same_day_last_year else R.string.analysis_vs_last_year,
        change,
    )

    is AnalysisPeriod.Custom -> stringResource(R.string.analysis_vs_previous_days, change, period.days)
}

@Composable
private fun trendLabels(period: AnalysisPeriod): Pair<String, String> = when (period) {
    is AnalysisPeriod.Week -> stringResource(R.string.analysis_this_week) to stringResource(R.string.analysis_last_week)

    is AnalysisPeriod.Month -> stringResource(R.string.analysis_this_month) to
        stringResource(R.string.analysis_last_month)

    is AnalysisPeriod.Year -> stringResource(R.string.analysis_this_year) to stringResource(R.string.analysis_last_year)

    is AnalysisPeriod.Custom -> stringResource(R.string.analysis_this_range) to
        stringResource(R.string.analysis_range_before)
}

private val SWIPE_DISTANCE = 72.dp
private const val YEARS_BACK = 10
private const val TO_SORT_EMOJI = "📥"
private const val TO_SORT_ID = -1L
private const val LARGE_FONT = 1.3f
private const val STATS = 3
private const val DONUT_WIDTH = 0.62f
private const val CENTER_TEXT_WIDTH = 0.4f
private const val PERCENT = 100
