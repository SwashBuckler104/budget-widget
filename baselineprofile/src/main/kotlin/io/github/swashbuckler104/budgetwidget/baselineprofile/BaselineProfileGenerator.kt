package io.github.swashbuckler104.budgetwidget.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods used on cold start and in the main journeys (SCOPE.md §6:
 * first frame under 500 ms), compiled ahead of time on install. Run with
 * `./gradlew :app:generateBaselineProfile` on an API 28+ emulator; see baselineprofile/README.md.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndTabs() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // A fresh install opens setup: walk through it the way a user skipping permissions would.
        repeat(SETUP_STEPS) {
            val button = device.findObject(By.text("Next")) ?: device.findObject(By.text("Not now"))
            button?.click()
            device.waitForIdle()
        }
        device.findObject(By.text("Start"))?.click()
        device.wait(Until.hasObject(By.text("Transactions")), TIMEOUT_MS)
        listOf("Analysis", "Categories", "Transactions").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "io.github.swashbuckler104.budgetwidget"
        const val SETUP_STEPS = 6
        const val TIMEOUT_MS = 5_000L
    }
}
