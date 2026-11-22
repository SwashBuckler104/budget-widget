package io.github.swashbuckler104.budgetwidget.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun `play installs go to the store listing, everything else to the release page`() {
        assertThat(updatePageUrl("com.android.vending", "a.b")).isEqualTo("market://details?id=a.b")
        assertThat(updatePageUrl(null, "a.b")).isEqualTo(RELEASES_URL)
        assertThat(updatePageUrl("com.google.android.packageinstaller", "a.b")).isEqualTo(RELEASES_URL)
    }
}
