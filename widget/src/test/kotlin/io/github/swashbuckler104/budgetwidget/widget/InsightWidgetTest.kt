package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// Native graphics, so Canvas really rasterizes the bar masks.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class InsightWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 10)
    private val now = today.atTime(18, 0).toInstant(zone).toEpochMilli()

    private val food =
        Category(1, null, "Food & Drinks", "🍽️", 0xFFEF6C00.toInt(), CategoryKind.EXPENSE, 0, false, true)
    private val fuel =
        Category(2, null, "Fuel", "⛽", 0xFF455A64.toInt(), CategoryKind.EXPENSE, 1, false, true)
    private val savings = Category(3, null, "Savings", "🏦", 0, CategoryKind.TRANSFER, 2, false, true)
    private val groups = listOf(food, fuel, savings).map { CategoryGroup(it, emptyList()) }

    private var nextId = 1L
    private fun tx(date: LocalDate, amount: Long, category: Category?) = Transaction(
        id = nextId++, source = TransactionSource.SMS, amountPaise = amount, direction = Direction.OUT,
        parserType = null,
        merchantDisplay = "Shop", bankName = null, accountLast4 = null,
        occurredAt = date.atTime(10, 0).toInstant(zone).toEpochMilli(), isCard = false,
        status = if (category == null) TransactionStatus.PENDING else TransactionStatus.CATEGORIZED,
        isBackfill = false, duplicateOfId = null, categoryId = category?.id,
    )

    private fun snapshot() = InsightSnapshot.compute(
        listOf(
            tx(today, 240_00, food),
            tx(today.minusDays(1), 1_000_00, fuel),
            tx(today.minusDays(3), 500_00, null),
            tx(today.minusDays(2), 9_000_00, savings),
            tx(LocalDate.of(2026, 10, 5), 800_00, fuel),
        ),
        groups,
        now,
        zone,
    )

    @Test
    fun `snapshot covers today, the month vs last month so far, the week and top categories`() {
        val s = snapshot()
        assertThat(s.todaySpentPaise).isEqualTo(240_00)
        assertThat(s.monthSpentPaise).isEqualTo(1_740_00)
        assertThat(s.changePercent).isEqualTo(117)
        assertThat(s.lastWeek).containsExactly(0L, 0L, 0L, 500_00L, 0L, 1_000_00L, 240_00L).inOrder()
        assertThat(s.lastWeekDays.last()).isEqualTo(today)
        assertThat(s.topCategories.map { it.category?.name }).containsExactly("Fuel", null, "Food & Drinks").inOrder()
    }

    @Test
    fun `bars are masks with today drawn apart from the other days`() {
        val masks = InsightBars.render(listOf(0L, 50L, 100L), highlight = 2, widthPx = 30, heightPx = 20)
        // Day 3 (highest, full height) is only in the highlight mask.
        assertThat(alpha(masks.highlighted, 25, 1)).isGreaterThan(0)
        assertThat(alpha(masks.base, 25, 1)).isEqualTo(0)
        // Day 2 is half height in the base mask: drawn at the bottom, empty at the top.
        assertThat(alpha(masks.base, 15, 18)).isGreaterThan(0)
        assertThat(alpha(masks.base, 15, 2)).isEqualTo(0)
    }

    private fun alpha(bitmap: Bitmap, x: Int, y: Int) = Color.alpha(bitmap.getPixel(x, y))

    @Test
    fun `4x2 shows today, the month and the chart, without categories`() = runGlanceAppWidgetUnitTest(GLANCE_TIMEOUT) {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 120.dp))
        provideComposable { InsightWidgetContent(snapshot(), Intent("analysis")) }

        onNode(hasText("Today")).assertExists()
        onNode(hasText("₹240")).assertExists()
        onNode(hasText("November")).assertExists()
        onNode(hasText("₹1,740")).assertExists()
        onNode(hasContentDescription("117% more than last month so far")).assertExists()
        onNode(hasContentDescription("Spending over the last 7 days. Today ₹240, highest day ₹1,000.")).assertExists()
        onNode(hasText("Top categories this month")).assertDoesNotExist()
    }

    @Test
    fun `category rows only take the room a decent chart leaves`() {
        assertThat(categoryRows(120.dp, 3)).isEqualTo(0)
        assertThat(categoryRows(225.dp, 3)).isEqualTo(1)
        assertThat(categoryRows(300.dp, 3)).isEqualTo(3)
        assertThat(categoryRows(400.dp, 2)).isEqualTo(2)
        // Larger text, taller rows: fewer fit.
        assertThat(categoryRows(300.dp, 5, fontScale = 1.3f)).isEqualTo(2)
    }

    @Test
    fun `tall sizes add the top three categories`() = runGlanceAppWidgetUnitTest(GLANCE_TIMEOUT) {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 300.dp))
        provideComposable { InsightWidgetContent(snapshot(), Intent("analysis")) }

        onNode(hasText("Top categories this month")).assertExists()
        onNode(hasText("⛽  Fuel")).assertExists()
        onNode(hasText("📥  To sort")).assertExists()
        onNode(hasText("🍽️  Food & Drinks")).assertExists()
    }
}
