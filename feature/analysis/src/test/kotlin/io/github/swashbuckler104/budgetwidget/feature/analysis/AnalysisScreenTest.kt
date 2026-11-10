package io.github.swashbuckler104.budgetwidget.feature.analysis

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.analysis.MonthAnalysis
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val housing = Category(1, null, "Housing", "🏠", 0xFF5D4037.toInt(), CategoryKind.EXPENSE, 0, false, true)
    private val rent = Category(2, 1, "Rent", "🔑", 0xFF5D4037.toInt(), CategoryKind.EXPENSE, 0, false, true)
    private val power = Category(3, 1, "Electricity", "💡", 0xFF5D4037.toInt(), CategoryKind.EXPENSE, 1, false, true)
    private val categories = listOf(housing, rent, power).associateBy { it.id }
    private val month = YearMonth.of(2026, 11)

    private fun tx(id: Long, day: Int, amount: Long, category: Long?, merchant: String) = Transaction(
        id = id, source = TransactionSource.SMS, amountPaise = amount, direction = Direction.OUT, parserType = null,
        merchantDisplay = merchant, bankName = null, accountLast4 = null,
        occurredAt = LocalDate.of(2026, 11, day).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        isCard = false,
        status = if (category == null) TransactionStatus.PENDING else TransactionStatus.CATEGORIZED,
        isBackfill = false, duplicateOfId = null, merchantKey = merchant.lowercase(), categoryId = category,
    )

    private val calls = mutableListOf<String>()

    private fun show(analysis: MonthAnalysis, streak: Int = 3, pending: Int = 1) = compose.setContent {
        AnalysisScreen(
            state = AnalysisUiState(
                loading = false,
                month = month,
                currentMonth = month,
                firstMonth = month.minusMonths(1),
                analysis = analysis,
                pending = pending,
            ),
            streak = streak,
            actions = AnalysisActions(
                onShiftMonth = { calls += "shift:$it" },
                onSelectAccount = { calls += "account:$it" },
                onSortInbox = { calls += "sort" },
                onOpenSettings = { calls += "settings" },
                onDrillDown = { title, category, query -> calls += "drill:$title:$category:$query" },
            ),
        )
    }

    private fun analysis() = MonthAnalysis.compute(
        listOf(
            tx(1, 1, 18_000_00, rent.id, "Landlord"),
            tx(2, 2, 1_500_00, power.id, "BESCOM"),
            tx(3, 3, 300_00, null, "Cafe"),
        ),
        categories,
        month,
        LocalDate.of(2026, 11, 10),
        ZoneOffset.UTC,
    )

    @Test
    fun `summary, streak and inbox chips, and month arrows`() {
        show(analysis())
        // The hero card and the donut's center both show the month's total.
        compose.onAllNodesWithText("₹19,800").assertCountEquals(2)
        compose.onNodeWithText("🔥 3-day streak").assertExists()
        compose.onNodeWithText("📥 1 to sort").performClick()
        compose.onNodeWithContentDescription("Previous month").performClick()
        assertThat(calls).containsExactly("sort", "shift:-1").inOrder()
    }

    @Test
    fun `tapping a category shows subcategories and drills down`() {
        show(analysis())
        compose.onNodeWithText("Housing").performScrollTo().performClick()
        compose.onNodeWithText("Rent").performScrollTo().assertExists()
        compose.onNodeWithText("See 2 transactions").performScrollTo().performClick()
        compose.onNodeWithText("Rent").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Landlord"))
        compose.onNodeWithText("Landlord").performClick()
        assertThat(calls).containsExactly("drill:Housing:1:", "drill:Rent:2:", "drill:Landlord:0:Landlord").inOrder()
    }

    @Test
    fun `an empty month says so`() {
        show(MonthAnalysis.compute(emptyList(), categories, month, LocalDate.of(2026, 11, 10), ZoneOffset.UTC), 0, 0)
        compose.onNodeWithText("Nothing this month").assertExists()
    }
}
