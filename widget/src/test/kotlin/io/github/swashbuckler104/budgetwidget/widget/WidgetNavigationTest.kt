package io.github.swashbuckler104.budgetwidget.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetNavigationTest {
    private val ids = listOf(10L, 20L, 30L)

    @Test
    fun `stepping wraps around and starts from the oldest`() {
        assertThat(WidgetNavigation.step(ids, 20, 1)).isEqualTo(30)
        assertThat(WidgetNavigation.step(ids, 30, 1)).isEqualTo(10)
        assertThat(WidgetNavigation.step(ids, 10, -1)).isEqualTo(30)
        assertThat(WidgetNavigation.step(ids, null, 1)).isEqualTo(20) // unknown = oldest
        assertThat(WidgetNavigation.step(emptyList(), 10, 1)).isNull()
    }

    @Test
    fun `after sorting the next one takes its place`() {
        assertThat(WidgetNavigation.afterRemoving(ids, 20)).isEqualTo(30)
        assertThat(WidgetNavigation.afterRemoving(ids, 30)).isEqualTo(10)
        assertThat(WidgetNavigation.afterRemoving(listOf(10L), 10)).isNull()
        assertThat(WidgetNavigation.afterRemoving(ids, 99)).isEqualTo(10)
    }

    @Test
    fun `chip labels keep the first part of the name`() {
        assertThat(shortLabel("Restaurant, Fast-food")).isEqualTo("Restaurant")
        assertThat(shortLabel("Fuel")).isEqualTo("Fuel")
        assertThat(shortLabel(", odd")).isEqualTo(", odd")
    }

    @Test
    fun `an explicit refresh signals live widget sessions`() = runTest {
        val signal = WidgetRefreshSignal()
        val widgets = GlanceInboxWidgets(ApplicationProvider.getApplicationContext(), signal)
        val before = signal.ticks.value
        widgets.refreshNow()
        assertThat(signal.ticks.value).isEqualTo(before + 1)
    }
}
