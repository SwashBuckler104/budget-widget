package io.github.swashbuckler104.budgetwidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.TransactionSource
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickSortWidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val intents = WidgetIntents(Intent("inbox"), { Intent("txn$it") }, Intent("app"))
    private val now = 1_700_000_000_000L

    private fun txn(
        id: Long,
        amount: Long = 450_00,
        merchant: String? = "Swiggy",
        direction: Direction = Direction.OUT,
    ) = Transaction(
        id = id,
        source = TransactionSource.SMS,
        amountPaise = amount,
        direction = direction,
        parserType = ParserType.EXPENSE,
        merchantDisplay = merchant,
        bankName = "HDFC Bank",
        accountLast4 = "1234",
        occurredAt = now,
        isCard = false,
        status = TransactionStatus.PENDING,
        isBackfill = false,
        duplicateOfId = null,
    )

    private val chips = listOf(
        "Groceries",
        "Restaurant, Fast-food",
        "Fuel",
        "Public transport",
        "Drug-store, Chemist",
        "Phone",
    )
        .mapIndexed { i, name -> Category(i + 1L, null, name, "🙂", 0, CategoryKind.EXPENSE, i, false, true) }

    private fun snapshot(
        pending: Int = 3,
        focus: Transaction? = txn(1),
        hoursLeft: Int? = null,
        streak: Int = 0,
        overdue: Boolean = false,
    ) = WidgetSnapshot(
        pending = pending,
        overdue = if (overdue) 1 else 0,
        focus = focus,
        focusOverdue = overdue,
        chips = if (focus == null) emptyList() else chips,
        upNext = if (focus == null) emptyList() else listOf(txn(2, 120_00, "Uber"), txn(3, 99_00, null)),
        todaySpentPaise = 1_240_00,
        streak = streak,
        hoursLeft = hoursLeft,
        now = now,
    )

    @Test
    fun `standard size shows header, card, six chips and the footer`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        provideComposable { QuickSortWidgetContent(snapshot(), intents) }

        onNode(hasText("3 to sort · closes at midnight")).assertExists()
        onNode(hasText("-₹450")).assertExists()
        onNode(hasText("Swiggy")).assertExists()
        chips.forEach { onNode(hasContentDescription("Sort as ${it.name}")).assertExists() }
        onNode(hasText("Restaurant")).assertExists()
        onNode(hasContentDescription("Ignore")).assertExists()
        onNode(hasContentDescription("More")).assertExists()
        onNode(hasContentDescription("Previous transaction")).assertExists()
        onNode(hasContentDescription("Next transaction")).assertExists()
        onNode(hasText("Up next")).assertDoesNotExist()
    }

    @Test
    fun `short 4x2 fits four chips plus Ignore and More, and counts down late`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 120.dp))
        provideComposable { QuickSortWidgetContent(snapshot(hoursLeft = 2, overdue = true), intents) }

        onNode(hasText("3 to sort · ~2 h left")).assertExists()
        onNode(hasText("Overdue")).assertExists()
        chips.take(4).forEach { onNode(hasContentDescription("Sort as ${it.name}")).assertExists() }
        onNode(hasContentDescription("Sort as Drug-store, Chemist")).assertDoesNotExist()
        onNode(hasContentDescription("Ignore")).assertExists()
        onNode(hasContentDescription("More")).assertExists()
    }

    @Test
    fun `large size lists the next two`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 260.dp))
        provideComposable { QuickSortWidgetContent(snapshot(), intents) }

        onNode(hasText("Up next")).assertExists()
        onNode(hasText("-₹120 · Uber")).assertExists()
        onNode(hasText("-₹99 · Unknown merchant")).assertExists()
    }

    @Test
    fun `an empty inbox shows today's spending and the streak`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        provideComposable { QuickSortWidgetContent(snapshot(pending = 0, focus = null, streak = 12), intents) }

        onNode(hasText("All sorted ✓")).assertExists()
        onNode(hasText("Today ₹1,240 · 🔥 12-day streak")).assertExists()
    }

    @Test
    fun `compact size shows the count or Sorted`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(120.dp, 120.dp))
        provideComposable { QuickSortWidgetContent(snapshot(pending = 105, overdue = true), intents) }
        onNode(hasText("105")).assertExists()
        onNode(hasText("to sort")).assertExists()
        onNode(hasText("Overdue")).assertExists()
    }

    @Test
    fun `compact empty shows Sorted and the streak`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(120.dp, 120.dp))
        provideComposable { QuickSortWidgetContent(snapshot(pending = 0, focus = null, streak = 1), intents) }
        onNode(hasText("Sorted ✓")).assertExists()
        onNode(hasText("🔥 1-day streak")).assertExists()
    }

    @Test
    fun `income is signed plus`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        provideComposable {
            QuickSortWidgetContent(snapshot(focus = txn(1, 50_000_00, "ACME", Direction.IN)), intents)
        }
        onNode(hasText("+₹50,000")).assertExists()
    }
}
