package io.github.swashbuckler104.budgetwidget.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    @Test
    fun `small amounts have no grouping`() {
        assertThat(formatInr(0)).isEqualTo("₹0")
        assertThat(formatInr(5)).isEqualTo("₹0.05")
        assertThat(formatInr(50_000)).isEqualTo("₹500")
        assertThat(formatInr(99_999)).isEqualTo("₹999.99")
    }

    @Test
    fun `uses Indian lakh and crore grouping`() {
        assertThat(formatInr(100_000)).isEqualTo("₹1,000")
        assertThat(formatInr(12_345_678)).isEqualTo("₹1,23,456.78")
        assertThat(formatInr(1_000_000_000)).isEqualTo("₹1,00,00,000")
    }

    @Test
    fun `negative amounts keep the sign in front`() {
        assertThat(formatInr(-150_050)).isEqualTo("-₹1,500.50")
    }

    @Test
    fun `extreme values do not overflow`() {
        assertThat(formatInr(Long.MIN_VALUE)).isEqualTo("-₹92,23,37,20,36,85,47,758.08")
        assertThat(formatInr(Long.MAX_VALUE)).isEqualTo("₹92,23,37,20,36,85,47,758.07")
    }
}
