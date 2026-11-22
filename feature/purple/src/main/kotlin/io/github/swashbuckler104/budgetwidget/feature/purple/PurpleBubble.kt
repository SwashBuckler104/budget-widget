package io.github.swashbuckler104.budgetwidget.feature.purple

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.data.purple.PurpleNote
import io.github.swashbuckler104.budgetwidget.core.data.purple.PurpleTip

/** Purple's own color, lighter on dark surfaces so it stays readable. */
@Composable
internal fun purpleColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFC8A2FF) else Color(0xFF7B3FE4)

/** A small purple face: Purple's avatar. Decorative; the bubble names Purple for TalkBack. */
@Composable
internal fun PurpleAvatar(modifier: Modifier = Modifier) {
    val body = purpleColor()
    val face = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(32.dp)) {
        val r = size.minDimension / 2
        drawCircle(body, radius = r)
        val eye = r * 0.16f
        drawCircle(face, radius = eye, center = Offset(center.x - r * 0.35f, center.y - r * 0.15f))
        drawCircle(face, radius = eye, center = Offset(center.x + r * 0.35f, center.y - r * 0.15f))
        drawArc(
            color = face,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - r * 0.45f, center.y - r * 0.2f),
            size = Size(r * 0.9f, r * 0.7f),
            style = Stroke(width = r * 0.12f),
        )
    }
}

/** What Purple says for [note], with its two buttons: (text, primary, secondary or null). */
@Composable
private fun words(note: PurpleNote, version: String): Triple<String, String, String?> {
    val lastStep = note.tourStep == PurpleTip.TOUR.size
    return when (note.tip) {
        PurpleTip.TOUR_TRANSACTIONS -> tour(R.string.purple_tour_transactions, lastStep)

        PurpleTip.TOUR_ANALYSIS -> tour(R.string.purple_tour_analysis, lastStep)

        PurpleTip.TOUR_CATEGORIES -> tour(R.string.purple_tour_categories, lastStep)

        PurpleTip.TOUR_SETTINGS -> tour(R.string.purple_tour_settings, lastStep)

        PurpleTip.UPDATE -> Triple(
            stringResource(R.string.purple_update, version),
            stringResource(R.string.purple_update_check),
            stringResource(R.string.purple_update_later),
        )

        PurpleTip.UNREAD_SMS -> Triple(
            pluralStringResource(R.plurals.purple_unread_sms, note.unreadSms, note.unreadSms),
            stringResource(R.string.purple_unread_help),
            stringResource(R.string.purple_not_now),
        )

        PurpleTip.DUPLICATE -> Triple(
            stringResource(R.string.purple_duplicate),
            stringResource(R.string.purple_done),
            null,
        )

        PurpleTip.SORT_ALL -> Triple(
            stringResource(R.string.purple_sort_all),
            stringResource(R.string.purple_sort_all_action),
            stringResource(R.string.purple_done),
        )

        PurpleTip.ALWAYS -> Triple(
            stringResource(R.string.purple_always, note.always?.merchant.orEmpty(), note.always?.category.orEmpty()),
            stringResource(R.string.purple_always_yes),
            stringResource(R.string.purple_always_no),
        )
    }
}

@Composable
private fun tour(text: Int, lastStep: Boolean) = Triple(
    stringResource(text),
    stringResource(if (lastStep) R.string.purple_done else R.string.purple_next),
    if (lastStep) null else stringResource(R.string.purple_skip),
)

/**
 * Purple's speech bubble: avatar, one or two short sentences and up to two buttons. With
 * [caretFraction] (0..1 across the bubble's parent) a caret under it points down at that spot.
 */
@Composable
internal fun PurpleBubble(
    note: PurpleNote,
    version: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    caretFraction: Float? = null,
) {
    val (text, primary, secondary) = words(note, version)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val name = stringResource(R.string.purple_name)
    // Pointing at a tab: the bubble leans toward it, and the caret sits over its center.
    val lean = when {
        caretFraction == null -> Alignment.End
        caretFraction < 1f / 3 -> Alignment.Start
        caretFraction > 2f / 3 -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    Column(modifier, horizontalAlignment = lean) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = container,
            border = BorderStroke(1.dp, purpleColor().copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = if (caretFraction != null) CARET_EDGE else 0.dp)
                .widthIn(max = MAX_WIDTH)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
                PurpleAvatar(Modifier.semantics { contentDescription = name })
                Column(Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = purpleColor())
                        if (note.tip.isTour) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                stringResource(R.string.purple_step, note.tourStep, PurpleTip.TOUR.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (secondary != null) TextButton(onClick = onSecondary) { Text(secondary) }
                        TextButton(onClick = onPrimary) { Text(primary) }
                    }
                }
            }
        }
        if (caretFraction != null) Caret(caretFraction, container)
    }
}

/** A small triangle at [fraction] of the full width, pointing down. */
@Composable
private fun Caret(fraction: Float, color: Color) {
    Layout(content = {
        Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                },
                color,
            )
        }
    }, modifier = Modifier.fillMaxWidth().offset(y = (-1).dp)) { measurables, constraints ->
        val caret = measurables.single().measure(Constraints())
        val width = constraints.maxWidth
        layout(width, caret.height) {
            val x = (width * fraction - caret.width / 2f).toInt().coerceIn(0, (width - caret.width).coerceAtLeast(0))
            caret.place(x, 0)
        }
    }
}

private val MAX_WIDTH = 340.dp
private val CARET_EDGE = 12.dp
