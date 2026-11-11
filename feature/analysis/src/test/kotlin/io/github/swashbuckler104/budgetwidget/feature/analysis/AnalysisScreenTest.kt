package io.github.swashbuckler104.budgetwidget.feature.analysis

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.analysis.AnalysisPeriod
import io.github.swashbuckler104.budgetwidget.core.data.analysis.PeriodAnalysis
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

    private val housing = Category(1, null, "Housing", "🏠", 0xFFFFF176.toInt(), CategoryKind.EXPENSE, 0, false, true)
    private val rent = Category(2, 1, "Rent", "🔑", 0xFFFFF176.toInt(), CategoryKind.EXPENSE, 0, false, true)
    private val power = Category(3, 1, "Electricity", "💡", 0xFFFFF176.toInt(), CategoryKind.EXPENSE, 1, false, true)
    private val salary = Category(4, null, "Salary", "💼", 0xFF7CFF6B.toInt(), CategoryKind.INCOME, 1, false, true)
    private val categories = listOf(housing, rent, power, salary).associateBy { it.id }
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

    private val today = LocalDate.of(2026, 11, 10)

    private fun show(analysis: PeriodAnalysis, streak: Int = 3, pending: Int = 1) = compose.setContent {
        AnalysisScreen(
            state = AnalysisUiState(
                loading = false,
                period = analysis.period,
                today = today,
                firstDate = LocalDate.of(2026, 10, 1),
                analysis = analysis,
                pending = pending,
            ),
            streak = streak,
            actions = AnalysisActions(
                onShift = { calls += "shift:$it" },
                onSelectKind = { calls += "kind:$it" },
                onSelectRange = { start, end -> calls += "range:$start:$end" },
                onSelectAccount = { calls += "account:$it" },
                onSortInbox = { calls += "sort" },
                onOpenSettings = { calls += "settings" },
                onDrillDown = { title, category, merchant, income ->
                    calls += "drill:$title:$category:${merchant?.merchantKey}" + if (income) ":in" else ""
                },
            ),
        )
    }

    private fun analysis(
        extra: List<Transaction> = emptyList(),
        period: AnalysisPeriod = AnalysisPeriod.Month(month),
    ) = PeriodAnalysis.compute(
        listOf(
            tx(1, 1, 18_000_00, rent.id, "Landlord"),
            tx(2, 2, 1_500_00, power.id, "BESCOM"),
            tx(3, 3, 300_00, null, "Cafe"),
        ) + extra,
        categories,
        period,
        today,
        ZoneOffset.UTC,
    )

    @Test
    fun `summary, streak and inbox chips, and month arrows`() {
        show(analysis())
        // The hero card and the donut's center both show the month's total.
        scrollTo("Where it went")
        compose.onAllNodesWithText("₹19,800").assertCountEquals(2)
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText("🔥 3-day streak").assertExists()
        compose.onNodeWithText("📥 1 to sort").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithText("Year").performClick()
        assertThat(calls).containsExactly("sort", "shift:-1", "kind:YEAR").inOrder()
    }

    @Test
    fun `tapping a category shows subcategories and drills down`() {
        show(analysis())
        scrollTo("Housing")
        compose.onNodeWithText("Housing").performClick()
        compose.onNodeWithText("Rent").performScrollTo().assertExists()
        compose.onNodeWithText("See 2 transactions").performScrollTo().performClick()
        compose.onNodeWithText("Rent").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Landlord"))
        compose.onNodeWithText("Landlord").performClick()
        assertThat(
            calls,
        ).containsExactly("drill:Housing:1:null", "drill:Rent:2:null", "drill:Landlord:0:landlord").inOrder()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `an empty period says so`() {
        show(PeriodAnalysis.compute(emptyList(), categories, AnalysisPeriod.Month(month), today, ZoneOffset.UTC), 0, 0)
        compose.onNodeWithText("Nothing in this period").assertExists()
    }

    @Test
    fun `income gets its own donut and drills down to money in`() {
        val pay = tx(4, 5, 60_000_00, salary.id, "Employer").copy(direction = Direction.IN)
        show(analysis(listOf(pay)))
        scrollTo("Where it went")
        // The segmented button, not the Income stat card.
        compose.onNode(hasText("Income") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Where it came from").assertExists()
        compose.onNodeWithText("Received").assertExists()
        compose.onNodeWithText("Salary").performScrollTo().performClick()
        compose.onNodeWithText("See 1 transaction").performScrollTo().performClick()
        assertThat(calls).containsExactly("drill:Salary:${salary.id}:null:in")
    }

    @Test
    fun `a custom range shows its dates and compares with the days before`() {
        show(analysis(period = AnalysisPeriod.Custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3))))
        compose.onNodeWithText("1 Nov - 3 Nov").assertExists()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
    }
}
