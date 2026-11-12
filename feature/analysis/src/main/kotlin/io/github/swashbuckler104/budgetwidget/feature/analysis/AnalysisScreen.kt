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
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.data.analysis.CategorySpend
import io.github.swashbuckler104.budgetwidget.core.data.analysis.MerchantSpend
import io.github.swashbuckler104.budgetwidget.core.data.analysis.MonthAnalysis
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Opens the transaction list for part of the shown month: a category (0 = any) and/or a merchant
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
    /** `OUT` for spending drill-downs (the totals only count money out), "" for both ways. */
    val direction: String = "",
)

/** Tab 1 (SCOPE.md F7): month summary, category donut with drill-down, daily bars, trends, merchants. */
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
            onShiftMonth = viewModel::shiftMonth,
            onSelectAccount = viewModel::selectAccount,
            onSortInbox = onSortInbox,
            onOpenSettings = onOpenSettings,
            onDrillDown = { title, categoryId, merchant ->
                val (from, to) = viewModel.monthRange()
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
                        direction = "OUT",
                    ),
                )
            },
        ),
        modifier = modifier,
    )
}

internal class AnalysisActions(
    val onShiftMonth: (Long) -> Unit,
    val onSelectAccount: (Long?) -> Unit,
    val onSortInbox: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Title, category id (0 = any) and a merchant (null = any). */
    val onDrillDown: (String, Long, MerchantSpend?) -> Unit,
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
                // Swipe between months (SCOPE.md F7 "swipe/arrow").
                .pointerInput(state.canGoBack, state.canGoForward) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            when {
                                drag > swipe && state.canGoBack -> actions.onShiftMonth(-1)
                                drag < -swipe && state.canGoForward -> actions.onShiftMonth(1)
                            }
                        },
                    ) { _, amount -> drag += amount }
                },
        ) {
            item(key = "switcher") { MonthSwitcher(state, actions) }
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
                    item(key = "summary") { Summary(analysis, state.month == state.currentMonth) }
                    if (analysis.categories.isNotEmpty()) {
                        item(key = "categories") { CategoriesCard(analysis, state.accountId, actions) }
                    }
                    item(key = "daily") { DailyCard(analysis, state.month == state.currentMonth) }
                    item(key = "trend") { TrendCard(analysis) }
                    if (analysis.topMerchants.isNotEmpty()) {
                        item(key = "merchants") { MerchantsCard(analysis.topMerchants, analysis.spentPaise, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(state: AnalysisUiState, actions: AnalysisActions) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { actions.onShiftMonth(-1) }, enabled = state.canGoBack) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.analysis_previous_month))
        }
        AnimatedContent(
            targetState = state.month,
            transitionSpec = {
                val forward = targetState > initialState
                slideInHorizontally { if (forward) it else -it } togetherWith
                    slideOutHorizontally { if (forward) -it else it }
            },
            label = "month",
            modifier = Modifier.weight(1f),
        ) { month ->
            Text(
                monthLabel(month, state.currentMonth),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
        }
        IconButton(onClick = { actions.onShiftMonth(1) }, enabled = state.canGoForward) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.analysis_next_month))
        }
    }
    if (state.accounts.size > 1) AccountFilter(state.accounts, state.accountId, actions.onSelectAccount)
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
private fun Summary(analysis: MonthAnalysis, current: Boolean) {
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
                    val text = stringResource(
                        if (current) R.string.analysis_vs_same_day else R.string.analysis_vs_last_month,
                        (
                            if (more) {
                                "▲ "
                            } else if (change < 0) {
                                "▼ "
                            } else {
                                ""
                            }
                            ) + "${abs(change)}%",
                    )
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

/** Donut by top-level category; a tapped slice (or row) shows its subcategories and a link to the list. */
@Composable
private fun CategoriesCard(analysis: MonthAnalysis, accountId: Long?, actions: AnalysisActions) {
    // Remembered by category (TO_SORT_ID for "To sort"), not slice position: sorting an item or
    // switching account reorders the slices.
    var selectedId by rememberSaveable(analysis.month, accountId) { mutableStateOf<Long?>(null) }
    val toSortName = stringResource(R.string.analysis_to_sort)
    val toSortColor = MaterialTheme.colorScheme.outline
    val slices = analysis.categories
    val pick = selectedId?.let { id ->
        slices.indexOfFirst { (it.category?.id ?: TO_SORT_ID) == id }
    }?.takeIf { it >= 0 }
    val select: (Int?) -> Unit = { index -> selectedId = index?.let { slices[it].category?.id ?: TO_SORT_ID } }
    ChartCard(stringResource(R.string.analysis_categories)) {
        val description = slices.joinToString(", ") { "${nameOf(it, toSortName)} ${formatInr(it.totalPaise)}" }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            DonutChart(
                slices = slices.map { DonutSlice(it.totalPaise, colorOf(it, toSortColor)) },
                selected = pick,
                onSelect = select,
                description = stringResource(R.string.analysis_donut_description, description),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth(DONUT_WIDTH)
                    .padding(vertical = 8.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val focus = pick?.let(slices::get)
                Text(
                    focus?.let { nameOf(it, toSortName) } ?: stringResource(R.string.analysis_spent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(CENTER_TEXT_WIDTH),
                    textAlign = TextAlign.Center,
                )
                Text(
                    formatInr(focus?.totalPaise ?: analysis.spentPaise),
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
                ofPaise = analysis.spentPaise,
                selected = index == pick,
                onClick = { select(if (pick == index) null else index) },
            )
            if (index == pick) {
                SliceDetail(slice, toSortName, actions)
            }
        }
    }
}

@Composable
private fun SliceDetail(slice: CategorySpend, toSortName: String, actions: AnalysisActions) {
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
                onClick = { child.category?.let { actions.onDrillDown(it.name, it.id, null) } },
            )
        }
        if (category != null) {
            TextButton(
                onClick = { actions.onDrillDown(category.name, category.id, null) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(pluralStringResource(R.plurals.analysis_see_transactions, slice.count, slice.count))
            }
        } else {
            TextButton(onClick = actions.onSortInbox, modifier = Modifier.padding(horizontal = 8.dp)) {
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
private fun DailyCard(analysis: MonthAnalysis, current: Boolean) {
    val today = if (current) analysis.daysCounted - 1 else null
    ChartCard(stringResource(R.string.analysis_daily)) {
        BarChart(
            values = analysis.daily,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            highlightColor = MaterialTheme.colorScheme.primary,
            highlight = today,
            reference = analysis.averagePerDayPaise.takeIf { it > 0 },
            referenceColor = MaterialTheme.colorScheme.tertiary,
            description = stringResource(
                R.string.analysis_daily_description,
                formatInr(analysis.daily.maxOrNull() ?: 0),
                formatInrRounded(analysis.averagePerDayPaise),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        AxisLabels(listOf("1", "${analysis.daily.size / 2}", "${analysis.daily.size}"))
        Legend(
            listOf(
                MaterialTheme.colorScheme.tertiary to
                    stringResource(R.string.analysis_average, formatInrRounded(analysis.averagePerDayPaise)),
            ),
        )
    }
}

@Composable
private fun TrendCard(analysis: MonthAnalysis) {
    val thisMonth = MaterialTheme.colorScheme.primary
    val lastMonth = MaterialTheme.colorScheme.outline
    ChartCard(stringResource(R.string.analysis_trend)) {
        LineChart(
            series = listOf(
                LineSeries(analysis.previousCumulative, lastMonth, dashed = true),
                LineSeries(analysis.cumulative, thisMonth),
            ),
            days = maxOf(analysis.daily.size, analysis.previousCumulative.size),
            description = stringResource(
                R.string.analysis_trend_description,
                formatInr(analysis.cumulative.lastOrNull() ?: 0),
                formatInr(analysis.previousCumulative.getOrNull(analysis.cumulative.size - 1) ?: 0),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        Legend(
            listOf(
                thisMonth to stringResource(R.string.analysis_this_month),
                lastMonth to stringResource(R.string.analysis_last_month),
            ),
        )
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
                    .clickable(enabled = merchant.name != null) { actions.onDrillDown(name, 0, merchant) }
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

@Composable
private fun monthLabel(month: YearMonth, current: YearMonth): String = month.format(
    DateTimeFormatter.ofPattern(if (month.year == current.year) "LLLL" else "LLLL yyyy"),
)

private val SWIPE_DISTANCE = 72.dp
private const val TO_SORT_EMOJI = "📥"
private const val TO_SORT_ID = -1L
private const val LARGE_FONT = 1.3f
private const val STATS = 3
private const val DONUT_WIDTH = 0.62f
private const val CENTER_TEXT_WIDTH = 0.4f
private const val PERCENT = 100
