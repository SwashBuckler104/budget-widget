package io.github.swashbuckler104.budgetwidget.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(
        step: OnboardingStep,
        smsGranted: Boolean = false,
        smsBlocked: Boolean = false,
        canPin: Boolean = true,
    ) = compose.setContent {
        OnboardingScreen(
            state = OnboardingState(
                step = step,
                smsGranted = smsGranted,
                smsBlocked = smsBlocked,
                notificationsGranted = false,
                backfill = Backfill.DAYS_90,
                groups = emptyList(),
                canPinWidget = canPin,
                samsung = true,
            ),
            actions = OnboardingActions(
                onNext = { calls += "next" },
                onBack = { calls += "back" },
                onAllowSms = { calls += "sms" },
                onAllowNotifications = { calls += "notifications" },
                onOpenAppInfo = { calls += "appinfo" },
                onChooseBackfill = { calls += "backfill:$it" },
                onPinWidget = { calls += "pin" },
                onFinish = { calls += "finish" },
            ),
        )
    }

    @Test
    fun `the SMS step discloses before asking and can be skipped`() {
        show(OnboardingStep.SMS)
        compose.onNodeWithText("Personal messages are skipped and never stored", substring = true).assertExists()
        compose.onNodeWithContentDescription("Step 2 of 7").assertExists()
        compose.onNodeWithText("Allow SMS access").performScrollTo().performClick()
        compose.onNodeWithText("Not now").performClick()
        assertThat(calls).containsExactly("sms", "next").inOrder()
    }

    @Test
    fun `a blocked request shows the restricted settings guide`() {
        show(OnboardingStep.SMS, smsBlocked = true)
        compose.onNodeWithText("Allow restricted settings", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Open App info").performScrollTo().performClick()
        assertThat(calls).containsExactly("appinfo")
    }

    @Test
    fun `history choice, samsung tip and finishing`() {
        show(OnboardingStep.HISTORY, smsGranted = true)
        compose.onNodeWithText("Start fresh").performClick()
        compose.onNodeWithText("Back").performClick()
        assertThat(calls).containsExactly("backfill:FRESH", "back").inOrder()
    }

    @Test
    fun `the last step adds the widget and starts`() {
        show(OnboardingStep.WIDGET)
        compose.onNodeWithText("Add the widget").performClick()
        compose.onNodeWithText("Start").performClick()
        assertThat(calls).containsExactly("pin", "finish").inOrder()
    }
}
