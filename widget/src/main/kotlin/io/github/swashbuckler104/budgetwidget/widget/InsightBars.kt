package io.github.swashbuckler104.budgetwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/**
 * Renders the Insight widget's 7-day bars (SCOPE.md F5: charts are bitmaps, Glance can't draw).
 * The bitmaps are alpha masks ([Bitmap.Config.ALPHA_8]): the widget tints them with theme color
 * providers, so light/dark and wallpaper color changes apply without re-rendering, and a mask is a
 * quarter the size of an ARGB bitmap in the RemoteViews parcel.
 */
internal object InsightBars {
    /** [base] holds every bar except [highlight]; [highlighted] holds only that one. */
    class Masks(val base: Bitmap, val highlighted: Bitmap)

    /**
     * [values] scaled to the largest (a zero day gets a stub so the axis reads as 7 days), drawn
     * into a [widthPx] x [heightPx] area. Sizes are clamped to at least 1 px.
     */
    fun render(values: List<Long>, highlight: Int, widthPx: Int, heightPx: Int): Masks {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val base = createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val highlighted = createBitmap(width, height, Bitmap.Config.ALPHA_8)
        if (values.isEmpty()) return Masks(base, highlighted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OPAQUE }
        val stubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OPAQUE
            alpha = STUB_ALPHA
        }
        val maxValue = values.max().coerceAtLeast(1)
        val slot = width.toFloat() / values.size
        val barWidth = slot * BAR_FRACTION
        // Gently rounded tops; full pills would turn short bars into dots.
        val radius = barWidth * CORNER_FRACTION
        val stub = minOf(barWidth, height * STUB_FRACTION)
        values.forEachIndexed { index, value ->
            val barHeight = if (value > 0) maxOf(height * value.toFloat() / maxValue, stub) else stub
            val left = index * slot + (slot - barWidth) / 2f
            val rect = RectF(left, height - barHeight, left + barWidth, height.toFloat())
            val canvas = Canvas(if (index == highlight) highlighted else base)
            canvas.drawRoundRect(rect, radius, radius, if (value > 0) paint else stubPaint)
        }
        return Masks(base, highlighted)
    }

    private const val OPAQUE = 0xFF000000.toInt()
    private const val STUB_ALPHA = 90
    private const val BAR_FRACTION = 0.58f
    private const val STUB_FRACTION = 0.06f
    private const val CORNER_FRACTION = 0.3f
}
