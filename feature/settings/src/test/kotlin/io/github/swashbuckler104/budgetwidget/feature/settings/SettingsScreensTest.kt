package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import io.github.swashbuckler104.budgetwidget.core.model.Account
import io.github.swashbuckler104.budgetwidget.core.model.AccountKind
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedSms
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun showSettings() = compose.setContent {
        SettingsScreen(
            state = SettingsUiState(loaded = true, unparsedCount = 3),
            notificationsBlocked = false,
            actions = SettingsActions(
                onBack = {},
                setNotificationsEnabled = {},
                setMinAmount = {},
                setAutoSort = {},
                setNudgeEnabled = {},
                setNudgeTime = { _, _ -> },
                allowNotifications = {},
                setTheme = { calls += "theme:$it" },
                importOlder = { calls += "import:$it" },
                startFresh = { calls += "fresh" },
                restore = { calls += "restore" },
                openSmsIssues = { calls += "unparsed" },
            ),
        )
    }

    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Test
    fun `theme, import, unparsed and the confirmed data actions`() {
        showSettings()
        scrollTo("Theme")
        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark").performClick()
        scrollTo("Import older SMS")
        compose.onNodeWithText("Import older SMS").performClick()
        compose.onNodeWithText("Last 90 days").performClick()
        scrollTo("Unparsed bank SMS")
        compose.onNodeWithText("3 messages the app couldn't read").performClick()

        scrollTo("Start fresh")
        compose.onNodeWithText("Start fresh").performClick()
        // Nothing happens until the dialog is confirmed.
        assertThat(calls).doesNotContain("fresh")
        compose.onNodeWithText("Delete and start fresh").performClick()
        scrollTo("Restore")
        compose.onNodeWithText("Restore").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Restore").performClick()
        compose.onNodeWithText("Choose file").performClick()

        assertThat(
            calls,
        ).containsExactly("theme:${ThemeMode.DARK}", "import:90", "unparsed", "fresh", "restore").inOrder()
    }

    @Test
    fun `accounts can be renamed and hidden`() {
        val account = Account(7, "HDFC Bank", "1234", null, AccountKind.BANK, hidden = false, balancePaise = 12_345_00)
        compose.setContent {
            AccountsScreen(
                accounts = listOf(account),
                onBack = {},
                onRename = { id, name -> calls += "rename:$id:$name" },
                onToggleVisibility = { id, hidden -> calls += "hide:$id:$hidden" },
            )
        }
        compose.onNodeWithText("Balance ₹12,345").assertExists()
        compose.onNodeWithContentDescription("Show HDFC Bank ··1234 in filters").performClick()
        compose.onNodeWithContentDescription("Rename HDFC Bank ··1234").performClick()
        compose.onNodeWithText("Leave empty to use the bank name").assertExists()
        compose.onNode(hasText("HDFC Bank ··1234") and hasSetText()).performTextReplacement("Salary")
        compose.onNodeWithText("Save").performClick()
        assertThat(calls).containsExactly("hide:7:true", "rename:7:Salary").inOrder()
    }

    @Test
    fun `unparsed messages show sender, reason and body and can be deleted`() {
        val sms = UnparsedSms(3, "VM-NEWBNK-S", "Rs 10 credited to A/c XX1", 0, UnparsedReason.UNKNOWN_SENDER)
        compose.setContent {
            UnparsedScreen(
                items = listOf(sms),
                onBack = {},
                onDelete = { calls += "delete:$it" },
                onDeleteAll = { calls += "all" },
            )
        }
        compose.onNodeWithText("Rs 10 credited to A/c XX1").assertExists()
        compose.onNodeWithText("Bank not supported yet", substring = true).assertExists()
        compose.onNodeWithContentDescription("Delete message from VM-NEWBNK-S").performClick()
        compose.onNodeWithContentDescription("Delete all").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertThat(calls).containsExactly("delete:3", "all").inOrder()
    }

    @Test
    fun `about shows the no-internet badge only when true`() {
        compose.setContent {
            AboutScreen(version = "1.0.0", offline = true, onBack = {}, onOpenSource = {}, onOpenParser = {})
        }
        compose.onNodeWithText("Version 1.0.0").assertExists()
        compose.onNodeWithText("No internet permission").assertExists()
    }
}

private fun hasSetText() =
    androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetText)
