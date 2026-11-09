package io.github.swashbuckler104.budgetwidget.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MerchantKeyNormalizerTest {
    @Test
    fun `blank input has no key`() {
        assertThat(MerchantKeyNormalizer.normalize(null)).isNull()
        assertThat(MerchantKeyNormalizer.normalize("  ")).isNull()
        assertThat(MerchantKeyNormalizer.normalize("1234567")).isNull()
    }

    @Test
    fun `lowercases and collapses whitespace`() {
        assertThat(MerchantKeyNormalizer.normalize("  SWIGGY   Food ")).isEqualTo("swiggy food")
    }

    @Test
    fun `strips UPI handles`() {
        assertThat(MerchantKeyNormalizer.normalize("swiggy.food@paytm")).isEqualTo("swiggy food")
        assertThat(MerchantKeyNormalizer.normalize("Zomato@ybl")).isEqualTo("zomato")
    }

    @Test
    fun `strips reference numbers and long digit runs`() {
        assertThat(MerchantKeyNormalizer.normalize("UPI-638903921672-CORN")).isEqualTo("upi corn")
        assertThat(MerchantKeyNormalizer.normalize("Amazon Ref No 12345AB")).isEqualTo("amazon")
        assertThat(MerchantKeyNormalizer.normalize("BLINKIT 0042")).isEqualTo("blinkit")
    }

    @Test
    fun `keeps short numbers that are part of a name`() {
        assertThat(MerchantKeyNormalizer.normalize("7-Eleven 24x7")).isEqualTo("7 eleven 24x7")
    }

    @Test
    fun `same merchant written differently gets the same key`() {
        val keys = listOf("BLINKIT", "Blinkit.", "blinkit 9876543").map(MerchantKeyNormalizer::normalize)
        assertThat(keys.toSet()).containsExactly("blinkit")
    }

    @Test
    fun `display keeps case but collapses whitespace`() {
        assertThat(MerchantKeyNormalizer.display("  Swiggy \n Food ")).isEqualTo("Swiggy Food")
        assertThat(MerchantKeyNormalizer.display(" ")).isNull()
    }
}
