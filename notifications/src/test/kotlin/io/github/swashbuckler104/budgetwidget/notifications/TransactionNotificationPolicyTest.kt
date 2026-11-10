package io.github.swashbuckler104.budgetwidget.notifications

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.datastore.NotificationSettings
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import kotlin.time.Duration.Companion.hours
import org.junit.Test

class TransactionNotificationPolicyTest {
    private val now = 100.hours.inWholeMilliseconds
    private val on = NotificationSettings()

    private fun notify(
        origin: IngestOrigin = IngestOrigin.REALTIME,
        amount: Long = 450_00,
        at: Long = now,
        settings: NotificationSettings = on,
    ) = TransactionNotificationPolicy.shouldNotify(origin, amount, at, now, settings)

    @Test
    fun `real-time transactions notify unless turned off or below the minimum`() {
        assertThat(notify()).isTrue()
        assertThat(notify(settings = on.copy(enabled = false))).isFalse()
        assertThat(notify(amount = 99_99, settings = on.copy(minAmountPaise = 100_00))).isFalse()
        assertThat(notify(amount = 100_00, settings = on.copy(minAmountPaise = 100_00))).isTrue()
    }

    @Test
    fun `the inbox scan notifies only for the last 24 hours and backfill never`() {
        assertThat(notify(IngestOrigin.RECONCILE, at = now - 24.hours.inWholeMilliseconds)).isTrue()
        assertThat(notify(IngestOrigin.RECONCILE, at = now - 25.hours.inWholeMilliseconds)).isFalse()
        assertThat(notify(IngestOrigin.BACKFILL)).isFalse()
    }

    @Test
    fun `actions are the top 3 suggestions, padded with Ignore`() {
        val cats = (1L..5L).map { category(it) }
        assertThat(quickActions(cats, possibleDuplicate = false))
            .containsExactly(QuickAction.Sort(cats[0]), QuickAction.Sort(cats[1]), QuickAction.Sort(cats[2]))
            .inOrder()
        assertThat(quickActions(cats.take(1), possibleDuplicate = false))
            .containsExactly(QuickAction.Sort(cats[0]), QuickAction.Ignore)
            .inOrder()
        assertThat(quickActions(emptyList(), possibleDuplicate = false)).containsExactly(QuickAction.Ignore)
    }

    @Test
    fun `a possible duplicate leads with Same payment`() {
        val cats = (1L..5L).map { category(it) }
        assertThat(quickActions(cats, possibleDuplicate = true))
            .containsExactly(QuickAction.SamePayment, QuickAction.Sort(cats[0]), QuickAction.Sort(cats[1]))
            .inOrder()
        assertThat(quickActions(emptyList(), possibleDuplicate = true))
            .containsExactly(QuickAction.SamePayment, QuickAction.Ignore)
            .inOrder()
    }

    private fun category(id: Long) = Category(id, null, "C$id", "🙂", 0, CategoryKind.EXPENSE, id.toInt(), false, false)
}
