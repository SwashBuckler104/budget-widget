package io.github.swashbuckler104.budgetwidget.feature.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionBlockTest {
    @Test
    fun `restricted setting or don't ask again shows the guide`() {
        // No dialog at all: the result is back almost at once.
        assertThat(
            smsAccessBlocked(granted = false, rationaleBefore = false, rationaleAfter = false, elapsedMillis = 40),
        )
            .isTrue()
        // Second explicit denial: the rationale flag drops.
        assertThat(
            smsAccessBlocked(granted = false, rationaleBefore = true, rationaleAfter = false, elapsedMillis = 3_000),
        )
            .isTrue()
    }

    @Test
    fun `a first denial or a dismissed dialog can be asked again`() {
        // First "Don't allow": Android now shows a rationale.
        assertThat(
            smsAccessBlocked(granted = false, rationaleBefore = false, rationaleAfter = true, elapsedMillis = 2_000),
        )
            .isFalse()
        // Back or a tap outside the dialog: no rationale change, but a person took their time.
        assertThat(
            smsAccessBlocked(granted = false, rationaleBefore = false, rationaleAfter = false, elapsedMillis = 2_000),
        )
            .isFalse()
        assertThat(
            smsAccessBlocked(granted = true, rationaleBefore = false, rationaleAfter = false, elapsedMillis = 10),
        )
            .isFalse()
    }
}
