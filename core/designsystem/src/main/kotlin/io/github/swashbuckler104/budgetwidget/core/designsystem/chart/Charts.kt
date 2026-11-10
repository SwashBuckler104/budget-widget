package io.github.swashbuckler104.budgetwidget.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/** One donut slice: a non-negative [value] drawn in [color]. */
data class DonutSlice(val value: Long, val color: Color)

/**
 * A donut of [slices] (clockwise from 12 o'clock) with a gap between slices. Tapping a slice calls
 * [onSelect] with its index (or null for the hole); the [selected] slice is drawn wider and the
 * others faded. [description] is what TalkBack reads (the chart itself is not focusable twice).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    thickness: Dp = 28.dp,
    trackColor: Color = Color.Transparent,
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(durationMillis = 600))
    }
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(slices) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width.coerceAtMost(size.height) / 2f
                    val distance = hypot(tap.x - center.x, tap.y - center.y)
                    if (distance < radius - thickness.toPx() * 1.6f || distance > radius) {
                        onSelect(null)
                        return@detectTapGestures
                    }
                    // Angle from 12 o'clock, clockwise, in [0, 1).
                    val angle = (atan2(tap.y - center.y, tap.x - center.x) + PI / 2).let {
                        if (it <
                            0
                        ) {
                            it + 2 * PI
                        } else {
                            it
                        }
                    }
                    val fraction = (angle / (2 * PI)).toFloat()
                    var start = 0f
                    slices.forEachIndexed { index, slice ->
                        val end = start + slice.value.toFloat() / total
                        if (fraction in start..end) {
                            onSelect(if (selected == index) null else index)
                            return@detectTapGestures
                        }
                        start = end
                    }
                }
            },
    ) {
        val stroke = thickness.toPx()
        val wide = stroke * 1.25f
        val diameter = size.minDimension - wide
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        if (trackColor != Color.Transparent) {
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        }
        val gap = if (slices.count { it.value > 0 } > 1) GAP_DEGREES else 0f
        var start = -90f
        slices.forEachIndexed { index, slice ->
            val angle = 360f * slice.value / total * sweep.value
            if (angle > 0f) {
                val faded = selected != null && selected != index
                drawArc(
                    color = if (faded) slice.color.copy(alpha = 0.35f) else slice.color,
                    startAngle = start + gap / 2,
                    sweepAngle = max(angle - gap, 0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(if (selected == index) wide else stroke, cap = StrokeCap.Butt),
                )
            }
            start += angle
        }
    }
}

/**
 * Vertical bars for [values] (one per day), scaled to the largest. [highlight] is drawn in
 * [highlightColor] (today), the rest in [color]; an optional dashed [reference] line (the daily
 * average) uses [referenceColor].
 */
@Composable
fun BarChart(
    values: List<Long>,
    color: Color,
    highlightColor: Color,
    description: String,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    reference: Long? = null,
    referenceColor: Color = color,
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(durationMillis = 500))
    }
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val maxValue = max(values.max(), reference ?: 0L).coerceAtLeast(1)
        val slot = size.width / values.size
        val barWidth = (slot * 0.62f).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        values.forEachIndexed { index, value ->
            if (value <= 0) return@forEachIndexed
            val height = (size.height * value / maxValue * grow.value).coerceAtLeast(barWidth.coerceAtMost(4.dp.toPx()))
            drawRoundRect(
                color = if (index == highlight) highlightColor else color,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
        if (reference != null && reference > 0) {
            val y = size.height - size.height * reference / maxValue
            drawLine(
                color = referenceColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
    }
}

/** One line of a [LineChart]: cumulative values by day; [dashed] for the comparison series. */
data class LineSeries(val values: List<Long>, val color: Color, val dashed: Boolean = false)

/**
 * Lines over a shared x axis of [days] slots (a month), scaled to the largest value of any
 * series. Series may be shorter than [days] (the current month stops at today).
 */
@Composable
fun LineChart(series: List<LineSeries>, days: Int, description: String, modifier: Modifier = Modifier) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(series) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 700))
    }
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val maxValue = series.maxOfOrNull { it.values.maxOrNull() ?: 0L }?.coerceAtLeast(1) ?: return@Canvas
        val step = if (days > 1) size.width / (days - 1) else size.width
        val stroke = 2.5.dp.toPx()
        series.forEach { line ->
            val count = (line.values.size * reveal.value).toInt().coerceAtLeast(if (line.values.isEmpty()) 0 else 1)
            if (count == 0) return@forEach
            val path = Path()
            line.values.take(count).forEachIndexed { index, value ->
                val x = index * step
                val y = size.height - (size.height - stroke) * value / maxValue - stroke / 2
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = line.color,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (line.dashed) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        )
                    } else {
                        null
                    },
                ),
            )
            if (!line.dashed) {
                val last = line.values[count - 1]
                drawCircle(
                    color = line.color,
                    radius = stroke * 1.6f,
                    center = Offset(
                        (count - 1) * step,
                        size.height - (size.height - stroke) * last / maxValue - stroke / 2,
                    ),
                )
            }
        }
    }
}

/** A thin rounded progress bar: [fraction] of the width in [color] over [trackColor]. */
@Composable
fun ProportionBar(fraction: Float, color: Color, trackColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width >
            0f
        ) {
            drawRoundRect(color, size = Size(width.coerceAtLeast(size.height), size.height), cornerRadius = radius)
        }
    }
}

private const val GAP_DEGREES = 2.5f
