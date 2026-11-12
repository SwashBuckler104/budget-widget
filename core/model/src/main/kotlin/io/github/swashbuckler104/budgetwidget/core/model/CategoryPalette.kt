package io.github.swashbuckler104.budgetwidget.core.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Category colors (docs/DECISIONS.md ADR-0032, ADR-0035). The stored value is the bright, pastel or neon
 * shade that reads well on a dark or black surface; on a light surface it is drawn darker with
 * [forLightSurface], keeping the hue, so a lemon-yellow slice stays visible on white.
 */
object CategoryPalette {
    val colors: List<Int> = listOf(
        0xFFFFF176, // lemon yellow
        0xFFEAB308, // travertine gold
        0xFFF59E0B, // amber glow
        0xFFFFAB40, // neon orange
        0xFFFF7A7A, // coral
        0xFFF43F5E, // rose neon
        0xFFFF6FB5, // hot pink
        0xFFE07CFF, // magenta
        0xFF8B5CF6, // electric violet
        0xFFA99BFF, // lavender
        0xFF8C92AC, // periwinkle
        0xFF3B82F6, // electric cobalt
        0xFF7AA7FF, // blue
        0xFF5CD3FF, // sky
        0xFF4DFFF0, // aqua
        0xFF64FFDA, // terminal cyan
        0xFF10B981, // mint glow
        0xFF7CFF6B, // neon green
        0xFF7FFF00, // electric chartreuse
        0xFFD4FF4A, // lime
        0xFFCFD8DC, // silver
        0xFFFFFFFF, // glowing white
    ).map { it.toInt() }

    /** Pre-1.0 dark palette (and its seeds) to the bright shade of the same family. */
    val legacy: Map<Int, Int> = mapOf(
        0xFFEF6C00 to 0xFFFFAB40, // orange: Food & Drinks
        0xFFAD1457 to 0xFFFF6FB5, // pink: Shopping
        0xFF5D4037 to 0xFFFFF176, // brown: Housing
        0xFF1565C0 to 0xFF5CD3FF, // blue: Transportation
        0xFF546E7A to 0xFFCFD8DC, // blue grey: Vehicle
        0xFF8E24AA to 0xFFE07CFF, // purple: Life & Entertainment
        0xFF283593 to 0xFF7AA7FF, // indigo: Communication, PC
        0xFFC62828 to 0xFFFF7A7A, // red: Financial expenses
        0xFF00838F to 0xFF4DFFF0, // cyan: Family
        0xFF558B2F to 0xFFD4FF4A, // light green: Investments
        0xFF2E7D32 to 0xFF7CFF6B, // green: Income
        0xFF00695C to 0xFFA99BFF, // teal (palette only)
        0xFF9E9D24 to 0xFFFFF176, // olive (palette only)
    ).entries.associate { (old, new) -> old.toInt() to new.toInt() }

    /** [argb] from an older install or backup, moved to the current palette; other colors unchanged. */
    fun upgrade(argb: Int): Int = legacy[argb] ?: argb

    /**
     * The shade of [argb] to draw on a light surface: same hue and saturation, HSL lightness lowered
     * just enough for at least [MIN_CONTRAST_ON_WHITE]:1 against white (the WCAG level for graphics).
     * Colors that already meet it are returned unchanged.
     */
    fun forLightSurface(argb: Int): Int {
        if (contrastOnWhite(argb) >= MIN_CONTRAST_ON_WHITE) return argb
        val alpha = argb ushr 24
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2f
        val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * ((g - b) / delta).mod(6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        var l = lightness
        var shade = argb
        while (contrastOnWhite(shade) < MIN_CONTRAST_ON_WHITE && l > 0f) {
            l = (l - LIGHTNESS_STEP).coerceAtLeast(0f)
            shade = hsl(alpha, hue, saturation, l)
        }
        return shade
    }

    /** WCAG contrast ratio of the opaque [argb] against white. */
    fun contrastOnWhite(argb: Int): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * linear(argb shr 16 and 0xFF) +
            0.7152 * linear(argb shr 8 and 0xFF) +
            0.0722 * linear(argb and 0xFF)
        return 1.05 / (luminance + 0.05)
    }

    private fun hsl(alpha: Int, hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val x = chroma * (1f - abs((hue / 60f).mod(2f) - 1f))
        val m = lightness - chroma / 2f
        val (r, g, b) = when ((hue / 60f).toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    const val MIN_CONTRAST_ON_WHITE = 3.0
    private const val LIGHTNESS_STEP = 0.01f
}
