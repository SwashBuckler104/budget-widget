package io.github.swashbuckler104.budgetwidget.feature.purple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.purple.AlwaysOffer
import io.github.swashbuckler104.budgetwidget.core.data.purple.PurpleNote
import io.github.swashbuckler104.budgetwidget.core.data.purple.PurpleTip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurpleOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(note: PurpleNote?) = compose.setContent {
        PurpleOverlay(
            note = note,
            version = "2.0.0",
            tabs = listOf(PurpleTip.TOUR_TRANSACTIONS, PurpleTip.TOUR_ANALYSIS, PurpleTip.TOUR_CATEGORIES),
            onPrimary = { calls += "primary:${it.tip}" },
            onSecondary = { calls += "secondary:${it.tip}" },
        )
    }

    @Test
    fun `a tour step introduces Purple and offers next or skip`() {
        show(PurpleNote(PurpleTip.TOUR_TRANSACTIONS))
        compose.onNodeWithContentDescription("Purple").assertExists()
        compose.onNodeWithText("Hi, I'm Purple", substring = true).assertExists()
        compose.onNodeWithText("1 of 4").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertThat(calls).containsExactly("primary:TOUR_TRANSACTIONS", "secondary:TOUR_TRANSACTIONS").inOrder()
    }

    @Test
    fun `the last tour step has one button`() {
        show(PurpleNote(PurpleTip.TOUR_SETTINGS))
        compose.onNodeWithText("4 of 4").assertExists()
        compose.onNodeWithText("Got it").assertExists()
        compose.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun `notices say what happened and what to do`() {
        show(PurpleNote(PurpleTip.UNREAD_SMS, unreadSms = 2, newestUnreadId = 9))
        compose.onNodeWithText("I couldn't read 2 bank SMS", substring = true).assertExists()
        compose.onNodeWithText("Help").performClick()
        assertThat(calls).containsExactly("primary:UNREAD_SMS")
    }

    @Test
    fun `the update notice names the version`() {
        show(PurpleNote(PurpleTip.UPDATE))
        compose.onNodeWithText("Version 2.0.0", substring = true).assertExists()
        compose.onNodeWithText("Later").performClick()
        assertThat(calls).containsExactly("secondary:UPDATE")
    }

    @Test
    fun `the always offer names the merchant and the category`() {
        show(PurpleNote(PurpleTip.ALWAYS, always = AlwaysOffer("swiggy", "Swiggy", 7, "Food")))
        compose.onNodeWithText("You put Swiggy in Food", substring = true).assertExists()
        compose.onNodeWithText("Always").performClick()
        assertThat(calls).containsExactly("primary:ALWAYS")
    }

    @Test
    fun `a touch outside the bubble sets it aside and still reaches the screen`() {
        var searched = 0
        compose.setContent {
            PurpleLayer(
                note = PurpleNote(PurpleTip.UNREAD_SMS, unreadSms = 2, newestUnreadId = 9),
                version = "2.0.0",
                tabs = emptyList(),
                onPrimary = { calls += "primary:${it.tip}" },
                onSecondary = { calls += "secondary:${it.tip}" },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                    Button(onClick = { searched++ }) { Text("Search") }
                }
            }
        }
        compose.onNodeWithText("I couldn't read 2 bank SMS", substring = true).assertExists()
        compose.onNodeWithText("Search").performClick()
        assertThat(searched).isEqualTo(1)
        compose.onNodeWithContentDescription("Purple").assertDoesNotExist()
        // Set aside, not answered.
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a touch on the bubble keeps it and answers it`() {
        compose.setContent {
            PurpleLayer(
                note = PurpleNote(PurpleTip.UPDATE),
                version = "2.0.0",
                tabs = emptyList(),
                onPrimary = { calls += "primary:${it.tip}" },
                onSecondary = { calls += "secondary:${it.tip}" },
            ) {}
        }
        compose.onNodeWithText("Version 2.0.0", substring = true).performClick()
        compose.onNodeWithContentDescription("Purple").assertExists()
        compose.onNodeWithText("Later").performClick()
        assertThat(calls).containsExactly("secondary:UPDATE")
    }

    @Test
    fun `nothing to say shows nothing`() {
        show(null)
        compose.onNodeWithContentDescription("Purple").assertDoesNotExist()
    }
}
