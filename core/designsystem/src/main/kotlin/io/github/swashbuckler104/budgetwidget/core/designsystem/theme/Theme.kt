package io.github.swashbuckler104.budgetwidget.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.swashbuckler104.budgetwidget.core.model.CategoryPalette

/**
 * App theme: Material 3 with dynamic color on Android 12+ and the brand palette (a calm teal, see
 * [BrandColors]) below or when dynamic color is off. Money colors ([MoneyColors]) are fixed hues,
 * harmonised for light and dark, so income always reads green and spending red whatever the
 * wallpaper.
 */
@Composable
fun BudgetWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandColors.dark

        else -> BrandColors.light
    }
    MaterialTheme(colorScheme = colorScheme, typography = BudgetTypography, shapes = BudgetShapes, content = content)
}

/** Income green and spending red, readable on every surface role of either theme. */
@Immutable
data class MoneyColors(
    val income: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val spend: Color,
    val spendContainer: Color,
    val onSpendContainer: Color,
) {
    companion object {
        val light = MoneyColors(
            income = Color(0xFF1B7A3D),
            incomeContainer = Color(0xFFC8F0CF),
            onIncomeContainer = Color(0xFF00210B),
            spend = Color(0xFFB3261E),
            spendContainer = Color(0xFFFFDAD5),
            onSpendContainer = Color(0xFF410001),
        )
        val dark = MoneyColors(
            income = Color(0xFF7FD993),
            incomeContainer = Color(0xFF00531F),
            onIncomeContainer = Color(0xFFC8F0CF),
            spend = Color(0xFFFFB4AA),
            spendContainer = Color(0xFF8C1D18),
            onSpendContainer = Color(0xFFFFDAD5),
        )
    }
}

/** Shortcut: `BudgetTheme.money.income`. Follows the color scheme in use (light or dark surface). */
object BudgetTheme {
    val money: MoneyColors
        @Composable
        @ReadOnlyComposable
        get() = if (isDarkSurface) MoneyColors.dark else MoneyColors.light

    /**
     * A category's stored color as drawn on the current surface: the bright shade on dark, a
     * darker shade of the same hue on light (see [CategoryPalette.forLightSurface]).
     */
    @Composable
    @ReadOnlyComposable
    fun categoryColor(argb: Int): Color = Color(if (isDarkSurface) argb else CategoryPalette.forLightSurface(argb))

    /**
     * The disc behind a category's emoji. On dark surfaces it is nearly solid: any thin tint of a
     * bright shade over black blends into a muddy olive or brown. On light surfaces it is a pastel tint.
     */
    @Composable
    @ReadOnlyComposable
    fun categoryContainer(argb: Int): Color =
        categoryColor(argb).copy(alpha = if (isDarkSurface) DARK_CONTAINER_ALPHA else LIGHT_CONTAINER_ALPHA)

    private val isDarkSurface: Boolean
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE

    private const val DARK_SURFACE = 0.5f
    private const val DARK_CONTAINER_ALPHA = 0.85f
    private const val LIGHT_CONTAINER_ALPHA = 0.2f
}

/** Brand palette (seed #006B5E), used when dynamic color is unavailable or off. */
object BrandColors {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF006B5E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9EF2E0),
        onPrimaryContainer = Color(0xFF00201B),
        inversePrimary = Color(0xFF82D5C4),
        secondary = Color(0xFF4A635E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E1),
        onSecondaryContainer = Color(0xFF05201B),
        tertiary = Color(0xFF456179),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCCE5FF),
        onTertiaryContainer = Color(0xFF001E31),
        background = Color(0xFFF5FBF8),
        onBackground = Color(0xFF171D1B),
        surface = Color(0xFFF5FBF8),
        onSurface = Color(0xFF171D1B),
        surfaceVariant = Color(0xFFDAE5E1),
        onSurfaceVariant = Color(0xFF3F4946),
        surfaceTint = Color(0xFF006B5E),
        inverseSurface = Color(0xFF2B3230),
        inverseOnSurface = Color(0xFFECF2EF),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF6F7976),
        outlineVariant = Color(0xFFBEC9C5),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF5FBF8),
        surfaceDim = Color(0xFFD5DBD8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F2),
        surfaceContainer = Color(0xFFE9EFEC),
        surfaceContainerHigh = Color(0xFFE3EAE7),
        surfaceContainerHighest = Color(0xFFDEE4E1),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF82D5C4),
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF005046),
        onPrimaryContainer = Color(0xFF9EF2E0),
        inversePrimary = Color(0xFF006B5E),
        secondary = Color(0xFFB1CCC5),
        onSecondary = Color(0xFF1C3530),
        secondaryContainer = Color(0xFF334B46),
        onSecondaryContainer = Color(0xFFCCE8E1),
        tertiary = Color(0xFFADCAE5),
        onTertiary = Color(0xFF143349),
        tertiaryContainer = Color(0xFF2D4960),
        onTertiaryContainer = Color(0xFFCCE5FF),
        background = Color(0xFF0E1513),
        onBackground = Color(0xFFDEE4E1),
        surface = Color(0xFF0E1513),
        onSurface = Color(0xFFDEE4E1),
        surfaceVariant = Color(0xFF3F4946),
        onSurfaceVariant = Color(0xFFBEC9C5),
        surfaceTint = Color(0xFF82D5C4),
        inverseSurface = Color(0xFFDEE4E1),
        inverseOnSurface = Color(0xFF2B3230),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF899390),
        outlineVariant = Color(0xFF3F4946),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF343B39),
        surfaceDim = Color(0xFF0E1513),
        surfaceContainerLowest = Color(0xFF09100E),
        surfaceContainerLow = Color(0xFF171D1B),
        surfaceContainer = Color(0xFF1B211F),
        surfaceContainerHigh = Color(0xFF252B29),
        surfaceContainerHighest = Color(0xFF303634),
    )
}

/** Material 3 type scale; headlines a touch heavier so amounts and totals carry the screen. */
private val BudgetTypography: Typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val BudgetShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tabular (fixed-width) digits, so amounts line up in lists and don't jitter while they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
