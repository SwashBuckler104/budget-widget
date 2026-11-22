package io.github.swashbuckler104.budgetwidget.feature.transactions.quicksort

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.sort.MerchantScope
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.feature.transactions.setSheetContent
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
        compose.setSheetContent {
            QuickSortContent(
                state = QuickSortUiState(
                    loading = false,
                    transaction = transaction,
                    remaining = 3,
                    suggestions = listOf(groceries),
                ),
                onChoose = { c, note, remember, _ -> chosen = Triple(c, note, remember) },
                onIgnore = { ignored = true },
                onSkip = {},
                onClose = {},
            )
        }
        compose.onNodeWithText("\u2212₹450").assertExists()
        compose.onNodeWithText("BLINKIT").assertExists()
        compose.onNodeWithText("3 to sort").assertExists()
        compose.onNodeWithText("Groceries").performClick()
        assertThat(chosen).isEqualTo(Triple(groceries, "", true))
        compose.onNodeWithText("Ignore").performScrollTo().performClick()
        assertThat(ignored).isTrue()
    }

    @Test
    fun `the whole remember row is one switch with a state`() {
        var chosen: Triple<Category, String, Boolean>? = null
        compose.setSheetContent {
            QuickSortContent(
                state = QuickSortUiState(
                    loading = false,
                    transaction = transaction,
                    remaining = 1,
                    suggestions = listOf(groceries),
                ),
                onChoose = { c, note, remember, _ -> chosen = Triple(c, note, remember) },
                onIgnore = {},
                onSkip = {},
                onClose = {},
            )
        }
        val row = compose.onNode(hasText("Remember for BLINKIT") and isToggleable())
        row.performScrollTo().assertIsOn().performClick()
        row.assertIsOff()
        compose.onNodeWithText("Groceries").performScrollTo().performClick()
        assertThat(chosen).isEqualTo(Triple(groceries, "", false))
    }

    @Test
    fun `earlier and newer payments are two separate checks, newer turns remember on`() {
        var chosen: Pair<Boolean, MerchantScope>? = null
        compose.setSheetContent {
            QuickSortContent(
                state = QuickSortUiState(
                    loading = false,
                    transaction = transaction,
                    remaining = 1,
                    suggestions = listOf(groceries),
                ),
                onChoose = { _, _, remember, scope -> chosen = remember to scope },
                onIgnore = {},
                onSkip = {},
                onClose = {},
            )
        }
        compose.onNode(hasText("Remember for BLINKIT") and isToggleable()).performScrollTo().performClick()
        val earlier = compose.onNode(hasText("Earlier payments go here") and isToggleable())
        val newer = compose.onNode(hasText("Newer payments go here") and isToggleable())
        earlier.performScrollTo().assertIsOff()
        newer.performScrollTo().assertIsOff().performClick()
        newer.assertIsOn()
        earlier.assertIsOff()
        compose.onNode(hasText("Remember for BLINKIT") and isToggleable()).assertIsOn()
        compose.onNodeWithText("Groceries").performScrollTo().performClick()
        assertThat(chosen).isEqualTo(true to MerchantScope(earlier = false, newer = true))
    }

    @Test
    fun `earlier payments alone leave remember as the user set it`() {
        var chosen: Pair<Boolean, MerchantScope>? = null
        compose.setSheetContent {
            QuickSortContent(
                state = QuickSortUiState(
                    loading = false,
                    transaction = transaction,
                    remaining = 1,
                    suggestions = listOf(groceries),
                ),
                onChoose = { _, _, remember, scope -> chosen = remember to scope },
                onIgnore = {},
                onSkip = {},
                onClose = {},
            )
        }
        compose.onNode(hasText("Remember for BLINKIT") and isToggleable()).performScrollTo().performClick()
        compose.onNode(hasText("Earlier payments go here") and isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Groceries").performScrollTo().performClick()
        assertThat(chosen).isEqualTo(false to MerchantScope(earlier = true, newer = false))
    }

    @Test
    fun `an empty inbox shows the done state`() {
        compose.setSheetContent {
            QuickSortContent(QuickSortUiState(loading = false, finished = true), { _, _, _, _ -> }, {}, {}, {})
        }
        compose.onNodeWithText("All sorted ✓").assertExists()
    }
}
