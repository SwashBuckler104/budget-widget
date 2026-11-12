package io.github.swashbuckler104.budgetwidget.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryPaletteTest {
    private fun hue(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        return when {
            delta == 0f -> 0f
            max == r -> 60f * ((g - b) / delta).mod(6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
    }

    private fun chroma(argb: Int): Float {
        val channels = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
        return (channels.max() - channels.min()) / 255f
    }

    @Test
    fun `palette shades are bright on a black surface and all different`() {
        assertThat(CategoryPalette.colors).containsNoDuplicates()
        CategoryPalette.colors.forEach {
            // At least 4.5:1 against black (WCAG AA); contrast on black = 21 / contrast on white.
            assertThat(21.0 / CategoryPalette.contrastOnWhite(it)).isAtLeast(4.5)
        }
    }

    @Test
    fun `every legacy color moves to a palette shade and others stay`() {
        CategoryPalette.legacy.forEach { (old, new) ->
            assertThat(CategoryPalette.colors).contains(new)
            assertThat(CategoryPalette.upgrade(old)).isEqualTo(new)
        }
        val custom = 0xFF112233.toInt()
        assertThat(CategoryPalette.upgrade(custom)).isEqualTo(custom)
        assertThat(CategoryPalette.upgrade(CategoryPalette.colors[3])).isEqualTo(CategoryPalette.colors[3])
    }

    @Test
    fun `light surface shades reach 3 to 1 on white and keep the hue`() {
        CategoryPalette.colors.forEach { color ->
            val shade = CategoryPalette.forLightSurface(color)
            assertThat(CategoryPalette.contrastOnWhite(shade)).isAtLeast(CategoryPalette.MIN_CONTRAST_ON_WHITE)
            assertThat(shade ushr 24).isEqualTo(0xFF)
            // Rounding to 8-bit channels moves the hue slightly; near-greys (silver, white) have no hue to keep.
            if (chroma(color) >= 0.15f) assertThat(hue(shade)).isWithin(4f).of(hue(color))
        }
    }

    @Test
    fun `colors already dark enough are untouched`() {
        val navy = 0xFF1A237E.toInt()
        assertThat(CategoryPalette.forLightSurface(navy)).isEqualTo(navy)
        assertThat(CategoryPalette.forLightSurface(0xFF000000.toInt())).isEqualTo(0xFF000000.toInt())
    }
}
