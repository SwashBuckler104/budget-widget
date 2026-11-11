package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
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
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Activities the widget opens. [app] is the launcher intent (null only if it vanished). */
internal class WidgetIntents(val inbox: Intent, val transaction: (Long) -> Intent, val app: Intent?)

/** Picks the layout for the size Glance is rendering (one of [QuickSortWidget]'s breakpoints). */
@Composable
internal fun QuickSortWidgetContent(snapshot: WidgetSnapshot, intents: WidgetIntents) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(10.dp),
    ) {
        val focus = snapshot.focus
        when {
            size.width < QuickSortWidget.WIDE.width -> Compact(snapshot, intents)
            focus == null -> Empty(snapshot, intents)
            size.height < QuickSortWidget.STANDARD.height -> Short(snapshot, focus, intents)
            else -> Tall(snapshot, focus, intents, showUpNext = size.height >= QuickSortWidget.LARGE.height)
        }
    }
}

/** 2x2: the count (or "Sorted ✓" and the streak); tap opens the sheet. */
@Composable
private fun Compact(snapshot: WidgetSnapshot, intents: WidgetIntents) {
    val res = LocalContext.current.resources
    val open = if (snapshot.pending > 0) intents.inbox else intents.app
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().clickableOrNot(open?.let { actionStartActivity(it) }),
    ) {
        if (snapshot.pending == 0) {
            Label(res.getString(R.string.widget_sorted), size = 18.sp, bold = true, color = GlanceTheme.colors.primary)
            if (snapshot.streak > 0) Label(streakText(snapshot.streak), size = 13.sp, muted = true)
        } else {
            Label(snapshot.pending.toString(), size = 40.sp, bold = true, color = GlanceTheme.colors.primary)
            Label(res.getQuantityString(R.plurals.widget_to_sort_label, snapshot.pending), size = 14.sp, muted = true)
            if (snapshot.overdue >
                0
            ) {
                Label(res.getString(R.string.widget_overdue), size = 12.sp, color = GlanceTheme.colors.error)
            }
        }
    }
}

/** Inbox empty (SCOPE.md F4): `All sorted ✓ · Today ₹1,240 · 🔥 12-day streak`. */
@Composable
private fun Empty(snapshot: WidgetSnapshot, intents: WidgetIntents) {
    val res = LocalContext.current.resources
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().clickableOrNot(intents.app?.let { actionStartActivity(it) }),
    ) {
        Label(res.getString(R.string.widget_all_sorted), size = 22.sp, bold = true, color = GlanceTheme.colors.primary)
        Spacer(GlanceModifier.height(4.dp))
        val today = res.getString(R.string.widget_today, formatInr(snapshot.todaySpentPaise))
        val line = if (snapshot.streak > 0) "$today · ${streakText(snapshot.streak)}" else today
        Label(line, size = 14.sp, muted = true)
    }
}

/** 4x2 on short launchers: chip row ends with Ignore and More instead of a footer. */
@Composable
private fun Short(snapshot: WidgetSnapshot, focus: Transaction, intents: WidgetIntents) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(snapshot, focus, withArrows = true)
        Card(snapshot, focus, intents)
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            snapshot.chips.take(SHORT_CHIPS).forEach { Chip(focus, it, height = 40.dp) }
            TextChip(LocalContext.current.getString(R.string.widget_ignore), ignore(focus), height = 40.dp)
            TextChip(
                LocalContext.current.getString(R.string.widget_more),
                actionStartActivity(intents.transaction(focus.id)),
                height = 40.dp,
                primary = true,
            )
        }
    }
}

/** 4x2 standard and 4x3+ large: 6 chips, footer `‹ Ignore More ›`, optionally the next two. */
@Composable
private fun Tall(snapshot: WidgetSnapshot, focus: Transaction, intents: WidgetIntents, showUpNext: Boolean) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(snapshot, focus, withArrows = false)
        Card(snapshot, focus, intents)
        // Launchers often give more height than the breakpoint: share it out between the blocks
        // instead of leaving one empty band above the footer.
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            snapshot.chips.take(MAX_CHIPS).forEach { Chip(focus, it, height = 52.dp) }
        }
        if (showUpNext && snapshot.upNext.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Label(context.getString(R.string.widget_up_next), size = 12.sp, muted = true)
            snapshot.upNext.forEach { Label(oneLine(context.resources, it), size = 13.sp, maxLines = 1) }
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().height(52.dp)) {
            Arrow(focus, previous = true)
            TextChip(context.getString(R.string.widget_ignore), ignore(focus), height = 52.dp)
            TextChip(
                context.getString(R.string.widget_more),
                actionStartActivity(intents.transaction(focus.id)),
                height = 52.dp,
                primary = true,
            )
            Arrow(focus, previous = false)
        }
    }
}

/** `3 to sort · closes at midnight` (or `~2 h left` in the day's last hours). */
@Composable
private fun Header(snapshot: WidgetSnapshot, focus: Transaction, withArrows: Boolean) {
    val res = LocalContext.current.resources
    val deadline = snapshot.hoursLeft?.let { res.getQuantityString(R.plurals.widget_hours_left, it, it) }
        ?: res.getString(R.string.widget_closes_midnight)
    val count = res.getQuantityString(R.plurals.widget_to_sort, snapshot.pending, snapshot.pending)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Label(count, size = 13.sp, bold = true, color = GlanceTheme.colors.primary, maxLines = 1)
        Label(" · $deadline", size = 12.sp, muted = true, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (withArrows) {
            Arrow(focus, previous = true, compact = true)
            Arrow(focus, previous = false, compact = true)
        }
    }
}

/** Amount (signed: color is never the only signal), merchant, account, time; tap = full sheet. */
@Composable
private fun Card(snapshot: WidgetSnapshot, focus: Transaction, intents: WidgetIntents) {
    val res = LocalContext.current.resources
    val incoming = focus.direction == Direction.IN
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .cornerRadius(14.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(intents.transaction(focus.id)))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(
                (if (incoming) "+" else "-") + formatInr(focus.amountPaise),
                size = 18.sp,
                bold = true,
                color = if (incoming) WidgetMoney.income else WidgetMoney.spend,
            )
            Spacer(GlanceModifier.width(8.dp))
            Label(
                focus.merchantDisplay ?: res.getString(R.string.widget_unknown_merchant),
                size = 14.sp,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        val meta = listOfNotNull(
            account(focus),
            time(LocalContext.current, focus.occurredAt, snapshot.now),
        ).joinToString(" · ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(meta, size = 12.sp, muted = true, maxLines = 1)
            if (snapshot.focusOverdue) {
                Spacer(GlanceModifier.width(6.dp))
                Label(
                    res.getString(R.string.widget_overdue),
                    size = 12.sp,
                    bold = true,
                    color = GlanceTheme.colors.error,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Chip(focus: Transaction, category: Category, height: Dp) {
    val label = LocalContext.current.getString(R.string.widget_sort_as, category.name)
    val action = actionRunCallback<SortChipAction>(
        actionParametersOf(WidgetKeys.transaction to focus.id, WidgetKeys.category to category.id),
    )
    ChipBox(action, height, label, GlanceTheme.colors.secondaryContainer) {
        Label(category.emoji, size = 16.sp, maxLines = 1, maxScale = CONTROL_FONT_SCALE)
        Label(
            shortLabel(category.name),
            size = 10.sp,
            maxLines = 1,
            color = GlanceTheme.colors.onSecondaryContainer,
            maxScale = CONTROL_FONT_SCALE,
        )
    }
}

/** Footer actions: [primary] is the filled one (More), the rest are quiet (Ignore). */
@Composable
private fun RowScope.TextChip(text: String, action: Action, height: Dp, primary: Boolean = false) {
    val background = if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
    val content = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant
    ChipBox(action, height, text, background) {
        Label(text, size = 13.sp, bold = true, maxLines = 1, color = content, maxScale = CONTROL_FONT_SCALE)
    }
}

@Composable
private fun RowScope.ChipBox(
    action: Action,
    height: Dp,
    description: String,
    background: ColorProvider,
    content: @Composable () -> Unit,
) {
    Box(modifier = GlanceModifier.defaultWeight().height(height).padding(2.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(12.dp)
                .clickable(action)
                .semantics { contentDescription = description },
        ) { content() }
    }
}

@Composable
private fun Arrow(focus: Transaction, previous: Boolean, compact: Boolean = false) {
    val context = LocalContext.current
    val description = context.getString(if (previous) R.string.widget_previous else R.string.widget_next)
    val action = actionRunCallback<StepAction>(
        actionParametersOf(WidgetKeys.transaction to focus.id, WidgetKeys.delta to if (previous) -1 else 1),
    )
    // 48 dp touch target in the footer; the header's arrows are smaller to fit a short widget.
    val box = if (compact) 28.dp else 48.dp
    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size(box).clickable(action)) {
        Image(
            provider = ImageProvider(if (previous) R.drawable.ic_chevron_start else R.drawable.ic_chevron_end),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(if (compact) 20.dp else 24.dp),
        )
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
    maxScale: Float = MAX_FONT_SCALE,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color ?: if (muted) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
            fontSize = size.capped(maxScale),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
    )
}

@Composable
private fun streakText(days: Int): String =
    LocalContext.current.resources.getQuantityString(R.plurals.widget_streak, days, days)

private fun ignore(focus: Transaction): Action =
    actionRunCallback<IgnoreAction>(actionParametersOf(WidgetKeys.transaction to focus.id))

private fun GlanceModifier.clickableOrNot(action: Action?): GlanceModifier = action?.let { clickable(it) } ?: this

private fun account(t: Transaction): String? =
    listOfNotNull(t.bankName, t.accountLast4?.let { "··$it" }).joinToString(" ").ifEmpty { null }

/** Time for today's transactions, `9 Nov` for older ones (a numeric date reads differently per locale). */
private fun time(context: Context, epochMillis: Long, now: Long): String =
    if (localDate(epochMillis) == localDate(now)) {
        DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.formatDateTime(
            context,
            epochMillis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
        )
    }

private fun localDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun oneLine(res: Resources, t: Transaction): String {
    val sign = if (t.direction == Direction.IN) "+" else "-"
    return "$sign${formatInr(t.amountPaise)} · ${t.merchantDisplay ?: res.getString(R.string.widget_unknown_merchant)}"
}

/** "Restaurant, Fast-food" -> "Restaurant": chips have room for one word or so. */
internal fun shortLabel(name: String): String = name.substringBefore(',').trim().ifEmpty { name }

private const val MAX_CHIPS = 6
private const val SHORT_CHIPS = 4
