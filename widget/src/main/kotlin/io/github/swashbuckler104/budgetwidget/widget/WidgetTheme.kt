package io.github.swashbuckler104.budgetwidget.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BrandColors
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.MoneyColors
import io.github.swashbuckler104.budgetwidget.core.model.CategoryPalette

/**
 * Widgets follow the wallpaper (dynamic color) on Android 12+, like the app, and use the app's
 * brand palette below. Light and dark come from the launcher's configuration, so a theme switch
 * needs no redraw from us.
 */
@Composable
internal fun WidgetTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = BrandGlanceColors, content = content)
    }
}

private val BrandGlanceColors = ColorProviders(light = BrandColors.light, dark = BrandColors.dark)

/**
 * Widget text follows the system font size up to [MAX_FONT_SCALE]. Widget cells don't grow, so
 * larger text was clipped at 200% (chip labels cut in half, footer buttons cut off).
 */
@Composable
internal fun TextUnit.capped(max: Float = MAX_FONT_SCALE): TextUnit {
    val scale = LocalContext.current.resources.configuration.fontScale
    return if (scale <= max) this else (value * max / scale).sp
}

/** How much larger widget text may get than its design size. */
internal const val MAX_FONT_SCALE = 1.3f

/**
 * Chip and button labels in fixed-height boxes don't grow at all (they were clipped at 1.3x on
 * the emulator); every chip and button has a full TalkBack label instead.
 */
internal const val CONTROL_FONT_SCALE = 1f

/** The font scale widget text actually renders at (see [capped]). */
@Composable
internal fun widgetFontScale(): Float =
    LocalContext.current.resources.configuration.fontScale.coerceIn(1f, MAX_FONT_SCALE)

/** Income green and spending red, fixed hues tuned per theme (see [MoneyColors]). */
internal object WidgetMoney {
    val income: ColorProvider = ColorProvider(day = MoneyColors.light.income, night = MoneyColors.dark.income)
    val spend: ColorProvider = ColorProvider(day = MoneyColors.light.spend, night = MoneyColors.dark.spend)
}

/**
 * Translucent surfaces (docs/DECISIONS.md ADR-0033): shape drawables whose colors live in
 * `res/color*`. The launcher resolves them, so day/night and wallpaper changes need no redraw.
 */
internal object WidgetSurfaces {
    val background: ImageProvider get() = ImageProvider(R.drawable.widget_background)
    val glass: ImageProvider get() = ImageProvider(R.drawable.widget_glass)
    val quiet: ImageProvider get() = ImageProvider(R.drawable.widget_quiet)
    val chip: ImageProvider get() = ImageProvider(R.drawable.widget_chip)
    val track: ImageProvider get() = ImageProvider(R.drawable.widget_track)
}

/** A category's bright stored color at night, its darker same-hue shade by day (ADR-0032). */
internal fun categoryColorProvider(argb: Int): ColorProvider =
    ColorProvider(day = Color(CategoryPalette.forLightSurface(argb)), night = Color(argb))
