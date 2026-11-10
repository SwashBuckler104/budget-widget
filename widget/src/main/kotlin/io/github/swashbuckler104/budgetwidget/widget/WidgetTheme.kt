package io.github.swashbuckler104.budgetwidget.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
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

/** Income green and spending red, fixed hues tuned per theme (see [MoneyColors]). */
internal object WidgetMoney {
    val income: ColorProvider = ColorProvider(day = MoneyColors.light.income, night = MoneyColors.dark.income)
    val spend: ColorProvider = ColorProvider(day = MoneyColors.light.spend, night = MoneyColors.dark.spend)
}
