package io.github.swashbuckler104.budgetwidget.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.BrandColors
import io.github.swashbuckler104.budgetwidget.core.designsystem.theme.MoneyColors

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
internal fun TextUnit.capped(): TextUnit {
    val scale = LocalContext.current.resources.configuration.fontScale
    return if (scale <= MAX_FONT_SCALE) this else (value * MAX_FONT_SCALE / scale).sp
}

private const val MAX_FONT_SCALE = 1.3f

/** Income green and spending red, fixed hues tuned per theme (see [MoneyColors]). */
internal object WidgetMoney {
    val income: ColorProvider = ColorProvider(day = MoneyColors.light.income, night = MoneyColors.dark.income)
    val spend: ColorProvider = ColorProvider(day = MoneyColors.light.spend, night = MoneyColors.dark.spend)
}
