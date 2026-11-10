package io.github.swashbuckler104.budgetwidget.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.swashbuckler104.budgetwidget.core.data.analysis.CategorySpend
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.core.model.formatInrRounded
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 4x2: today and this month (with the comparison) over the 7-day bars. From about 4x3 the top 3
 * categories with proportional bars join in (SCOPE.md F5). The whole widget opens Analysis.
 */
@Composable
internal fun InsightWidgetContent(snapshot: InsightSnapshot, open: Intent?) {
    val size = LocalSize.current
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = PADDING, vertical = 12.dp)
            .let { m -> open?.let { m.clickable(actionStartActivity(it)) } ?: m },
    ) {
        Header(snapshot)
        val rows = categoryRows(size.height, snapshot.topCategories.size)
        // At the 110 dp minimum there's no room for weekday labels under a readable chart.
        val showDays = size.height >= SHOW_DAYS
        val fixed = if (showDays) FIXED else FIXED - DAY_LABELS
        val reserved = fixed + if (rows > 0) CATEGORY_HEADER + CATEGORY_ROW * rows else 0.dp
        val chartHeight = (size.height - reserved).coerceIn(MIN_CHART, MAX_CHART)
        Spacer(GlanceModifier.height(8.dp))
        Chart(snapshot, size.width - PADDING * 2, chartHeight, showDays)
        if (rows > 0) {
            Spacer(GlanceModifier.height(10.dp))
            Label(context.getString(R.string.insight_top_categories), 12.sp, muted = true)
            snapshot.topCategories.take(rows).forEach {
                CategoryRow(it, snapshot.monthSpentPaise, size.width - PADDING * 2)
            }
        }
    }
}

@Composable
private fun Header(snapshot: InsightSnapshot) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Bottom, modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Label(context.getString(R.string.insight_today), 12.sp, muted = true)
            Label(formatInr(snapshot.todaySpentPaise), 22.sp, bold = true, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            val monthName = snapshot.month.month.getDisplayName(
                DateTextStyle.FULL,
                context.resources.configuration.locales[0],
            )
            Label(monthName, 12.sp, muted = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                snapshot.changePercent?.let { change ->
                    val more = change > 0
                    val arrow = if (more) {
                        "▲"
                    } else if (change < 0) {
                        "▼"
                    } else {
                        ""
                    }
                    Label(
                        "$arrow${abs(change)}%",
                        12.sp,
                        bold = true,
                        color = if (more) WidgetMoney.spend else WidgetMoney.income,
                        modifier = GlanceModifier.semantics {
                            contentDescription = context.getString(
                                if (more) R.string.insight_more_than_last else R.string.insight_less_than_last,
                                abs(change),
                            )
                        },
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                Label(formatInrRounded(snapshot.monthSpentPaise), 16.sp, bold = true, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Chart(snapshot: InsightSnapshot, width: Dp, height: Dp, showDays: Boolean) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val widthPx = (width.value * density).roundToInt()
    val heightPx = (height.value * density).roundToInt()
    val masks = remember(snapshot.lastWeek, widthPx, heightPx) {
        InsightBars.render(snapshot.lastWeek, snapshot.lastWeek.lastIndex, widthPx, heightPx)
    }
    val description = context.getString(
        R.string.insight_chart_description,
        formatInr(snapshot.todaySpentPaise),
        formatInr(snapshot.lastWeek.maxOrNull() ?: 0),
    )
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Box(modifier = GlanceModifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            Image(
                provider = ImageProvider(masks.base),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondary),
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
            Image(
                provider = ImageProvider(masks.highlighted),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        val locale = context.resources.configuration.locales[0]
        val dayFormat = DateTimeFormatter.ofPattern("EEEEE", locale)
        if (showDays) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
                snapshot.lastWeekDays.forEachIndexed { index, day ->
                    Text(
                        text = day.format(dayFormat),
                        maxLines = 1,
                        style = TextStyle(
                            color = if (index == snapshot.lastWeekDays.lastIndex) {
                                GlanceTheme.colors.primary
                            } else {
                                GlanceTheme.colors.onSurfaceVariant
                            },
                            fontSize = 10.sp.capped(),
                            fontWeight = if (index ==
                                snapshot.lastWeekDays.lastIndex
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            textAlign = TextAlign.Center,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(spend: CategorySpend, monthPaise: Long, width: Dp) {
    val context = LocalContext.current
    val name = spend.category?.name ?: context.getString(R.string.insight_to_sort)
    val emoji = spend.category?.emoji ?: "📥"
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Label("$emoji  $name", 13.sp, maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Label(formatInrRounded(spend.totalPaise), 13.sp, bold = true, maxLines = 1)
        }
        val fraction = if (monthPaise > 0) spend.totalPaise.toFloat() / monthPaise else 0f
        val color = spend.category?.let { ColorProvider(Color(it.colorArgb)) } ?: GlanceTheme.colors.outline
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(BAR)
                .cornerRadius(3.dp)
                .background(GlanceTheme.colors.surfaceVariant),
        ) {
            Box(
                modifier = GlanceModifier
                    .width((width * fraction.coerceIn(0f, 1f)).coerceAtLeast(BAR))
                    .height(BAR)
                    .cornerRadius(3.dp)
                    .background(color),
            ) {}
        }
    }
}

@Composable
private fun Label(
    text: String,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    bold: Boolean = false,
    muted: Boolean = false,
    color: ColorProvider? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color ?: if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
            fontSize = size.capped(),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
    )
}

/**
 * How many category rows fit under a chart of at least [CHART_WITH_CATEGORIES] (0 when none or
 * fewer than [available]): the chart is the point of the widget, categories come second.
 */
internal fun categoryRows(height: Dp, available: Int): Int {
    val room = height - FIXED - CHART_WITH_CATEGORIES - CATEGORY_HEADER
    if (room < CATEGORY_ROW) return 0
    return minOf(available, (room.value / CATEGORY_ROW.value).toInt())
}

private val PADDING = 14.dp

/** Header (~48 dp), day labels (~16 dp), vertical padding (24 dp) and gaps. */
private val FIXED = 96.dp
private val DAY_LABELS = 16.dp
private val SHOW_DAYS = 130.dp
private val CATEGORY_HEADER = 26.dp
private val CATEGORY_ROW = 32.dp
private val CHART_WITH_CATEGORIES = 56.dp
private val MIN_CHART = 24.dp
private val MAX_CHART = 72.dp
private val BAR = 6.dp
