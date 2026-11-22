package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCheckTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `play installs go to the store listing, everything else to the release page`() {
        assertThat(updatePageUrl("com.android.vending", "a.b")).isEqualTo("market://details?id=a.b")
        assertThat(updatePageUrl(null, "a.b")).isEqualTo(RELEASES_URL)
        assertThat(updatePageUrl("com.google.android.packageinstaller", "a.b")).isEqualTo(RELEASES_URL)
    }

    @Test
    fun `the reminder says why it asks and offers check or later`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            UpdateReminderDialog("1.0.3", onCheck = { calls += "check" }, onLater = { calls += "later" })
        }
        compose.onNodeWithText("version 1.0.3", substring = true).assertExists()
        compose.onNodeWithText("no internet access", substring = true).assertExists()
        compose.onNodeWithText("Check for updates").performClick()
        compose.onNodeWithText("Later").performClick()
        assertThat(calls).containsExactly("check", "later").inOrder()
    }
}
