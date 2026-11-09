package io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickSortContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val groceries =
        Category(5, null, "Groceries", "🛒", 0, CategoryKind.EXPENSE, 0, archived = false, isSeed = true)
    private val transaction = Transaction(
        id = 1, source = TransactionSource.SMS, amountPaise = 45_000, direction = Direction.OUT, parserType = null,
        merchantDisplay = "BLINKIT", bankName = "Axis Bank", accountLast4 = "2207", occurredAt = 0, isCard = true,
        status = TransactionStatus.PENDING, isBackfill = false, duplicateOfId = null, merchantKey = "blinkit",
    )

    @Test
    fun `shows the transaction and sorts with one tap on a suggestion`() {
        var chosen: Triple<Category, String, Boolean>? = null
        var ignored = false
        compose.setContent {
            QuickSortContent(
                state = QuickSortUiState(
                    loading = false,
                    transaction = transaction,
                    remaining = 3,
                    suggestions = listOf(groceries),
                ),
                onChoose = { c, note, remember -> chosen = Triple(c, note, remember) },
                onIgnore = { ignored = true },
                onSkip = {},
                onClose = {},
            )
        }
        compose.onNodeWithText("-₹450").assertExists()
        compose.onNodeWithText("BLINKIT").assertExists()
        compose.onNodeWithText("3 to sort").assertExists()
        compose.onNodeWithText("Groceries").performClick()
        assertThat(chosen).isEqualTo(Triple(groceries, "", true))
        compose.onNodeWithText("Ignore").performScrollTo().performClick()
        assertThat(ignored).isTrue()
    }

    @Test
    fun `an empty inbox shows the done state`() {
        compose.setContent {
            QuickSortContent(QuickSortUiState(loading = false, finished = true), { _, _, _ -> }, {}, {}, {})
        }
        compose.onNodeWithText("All sorted ✓").assertExists()
    }
}
