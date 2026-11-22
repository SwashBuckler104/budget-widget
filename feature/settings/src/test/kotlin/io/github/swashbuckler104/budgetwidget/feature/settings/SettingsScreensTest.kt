package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.datastore.SmsRetention
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
                setSmsRetention = { calls += "keep:$it" },
                importOlder = { calls += "import:$it" },
                startFresh = { calls += "fresh" },
                restore = { calls += "restore" },
                backup = { calls += "backup:$it" },
                openSmsIssues = { calls += "unparsed" },
                checkUpdates = { calls += "updates" },
            ),
            appVersion = "2.0.0",
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
    fun `check for updates names the installed version and opens the page`() {
        showSettings()
        scrollTo("Check for updates")
        compose.onNodeWithText("You have version 2.0.0", substring = true).assertExists()
        compose.onNodeWithText("Check for updates").performClick()
        assertThat(calls).containsExactly("updates")
    }

    @Test
    fun `keep original SMS text explains what goes before offering the limits`() {
        showSettings()
        scrollTo("Keep original SMS text")
        compose.onNodeWithText("Forever").performClick()
        compose.onNodeWithText("Amount, merchant, account and category stay", substring = true).assertExists()
        compose.onNodeWithText("30 days").performClick()
        compose.onNodeWithText("90 days").assertDoesNotExist()
        assertThat(calls).containsExactly("keep:${SmsRetention.DAYS_30}")
    }

    @Test
    fun `a backup password must be long enough and typed twice`() {
        showSettings()
        scrollTo("Back up")
        compose.onNodeWithText("Back up").performClick()
        compose.onNodeWithText("Protect with a password").performClick()
        val save = compose.onNodeWithText("Choose where to save")
        save.assertIsNotEnabled()
        compose.onNodeWithText("Password").performTextInput("short")
        compose.onNodeWithText("At least 8 characters").assertExists()
        compose.onNodeWithText("Password").performTextInput("-but-now-long")
        compose.onNodeWithText("Repeat password").performTextInput("short-but-now-lonG")
        compose.onNodeWithText("Passwords don't match").assertExists()
        save.assertIsNotEnabled()
        compose.onNodeWithText("Repeat password").performTextClearance()
        compose.onNodeWithText("Repeat password").performTextInput("short-but-now-long")
        save.assertIsEnabled().performClick()

        // Without the box ticked it is a plain backup.
        scrollTo("Back up")
        compose.onNodeWithText("Back up").performClick()
        compose.onNodeWithText("Choose where to save").performClick()
        assertThat(calls).containsExactly("backup:short-but-now-long", "backup:null").inOrder()
    }

    @Test
    fun `the restore password prompt shows a wrong password and passes the next try`() {
        var tried: String? = null
        var state by mutableStateOf(SettingsUiState(loaded = true, restorePrompt = RestorePrompt()))
        compose.setContent {
            SettingsScreen(
                state = state,
                notificationsBlocked = false,
                actions = SettingsActions(
                    onBack = {},
                    setNotificationsEnabled = {},
                    setMinAmount = {},
                    setAutoSort = {},
                    setNudgeEnabled = {},
                    setNudgeTime = { _, _ -> },
                    allowNotifications = {},
                    restoreWithPassphrase = { tried = it },
                ),
            )
        }
        compose.onNodeWithText("Wrong password, or the file was changed").assertDoesNotExist()
        compose.onNodeWithText("Password").performTextInput("guess")
        compose.onAllNodesWithText("Restore").filterToOne(hasAnyAncestor(isDialog())).performClick()
        assertThat(tried).isEqualTo("guess")
        state = state.copy(restorePrompt = RestorePrompt(wrong = true))
        compose.onNodeWithText("Wrong password, or the file was changed").assertExists()
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
        compose.onNodeWithContentDescription("Edit HDFC Bank ··1234").performClick()
        compose.onNodeWithText("Leave empty to use the bank name").assertExists()
        compose.onNode(hasText("Nickname") and hasSetText()).performTextReplacement("Salary")
        compose.onNodeWithText("Save").performClick()
        // The starting balance didn't change, so it isn't written (that would reset the balance).
        assertThat(calls).containsExactly("hide:7:true", "rename:7:Salary").inOrder()
    }

    @Test
    fun `an account's starting balance is set, shown, and a card's is money owed`() {
        val bank = Account(7, "HDFC Bank", "1234", null, AccountKind.BANK, hidden = false, balancePaise = null)
        val card = Account(8, "Kotak", "0082", null, AccountKind.CARD, hidden = false, balancePaise = null)
        compose.setContent {
            AccountsScreen(
                accounts = listOf(bank, card),
                onBack = {},
                onRename = { id, name -> calls += "rename:$id:$name" },
                onToggleVisibility = { _, _ -> },
                openings = mapOf(8L to -1_500_00L),
                onSetOpening = { id, paise -> calls += "opening:$id:$paise" },
            )
        }
        compose.onNodeWithText("Started owing ₹1,500").assertExists()
        compose.onNodeWithContentDescription("Edit HDFC Bank ··1234").performClick()
        compose.onNode(hasText("Starting balance") and hasSetText()).performTextReplacement("52,340.50")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithContentDescription("Edit Kotak ··0082").performClick()
        compose.onNode(hasText("Amount owed at the start") and hasSetText()).performTextReplacement("")
        compose.onNodeWithText("Save").performClick()
        assertThat(calls).containsExactly("opening:7:5234050", "opening:8:null").inOrder()
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
    fun `add as transaction opens the sheet for that message and says when it was added`() {
        val sms = UnparsedSms(3, "VM-NEWBNK-S", "Rs 10 credited to A/c XX1", 0, UnparsedReason.UNKNOWN_SENDER)
        val noBody = UnparsedSms(4, "VM-OLDBNK-S", null, 0, UnparsedReason.NO_MATCH)
        compose.setContent {
            UnparsedScreen(
                items = listOf(sms, noBody),
                onBack = {},
                onDelete = {},
                onDeleteAll = {},
                addSheet = { id, onDone ->
                    calls += "sheet:$id"
                    TextButton(onClick = { onDone(true) }) {
                        Text("fake save")
                    }
                },
            )
        }
        // Only a message with text can be added.
        compose.onAllNodesWithText("Add as transaction").assertCountEquals(1)
        compose.onNodeWithText("Add as transaction").performClick()
        compose.onNodeWithText("fake save").performClick()
        compose.onNodeWithText("Added to transactions").assertExists()
        compose.onNodeWithText("fake save").assertDoesNotExist()
        assertThat(calls.distinct()).containsExactly("sheet:3")
    }

    @Test
    fun `about shows the no-internet badge only when true`() {
        compose.setContent {
            AboutScreen(
                version = "1.0.0",
                offline = true,
                onBack = {},
                onOpenSource = {},
                onOpenParser = {},
                onCheckUpdates = { calls += "updates" },
            )
        }
        compose.onNodeWithText("Version 1.0.0").assertExists()
        compose.onNodeWithText("No internet permission").assertExists()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        assertThat(calls).containsExactly("updates")
    }
}

private fun hasSetText() =
    androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetText)
