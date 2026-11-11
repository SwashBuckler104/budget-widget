package io.github.swashbuckler104.budgetwidget.feature.onboarding

/**
 * Whether a denied SMS request means only App info can grant it now (the restricted-settings
 * guide, SCOPE.md F1 step 4). Android gives no direct signal, so this combines the two it does give:
 * - no dialog was shown: a restricted setting (sideloaded app, Android 13+) or an earlier
 *   "don't ask again" makes the result arrive at once, faster than a person can tap;
 * - the rationale flag went from true to false: the user just denied a second time, which
 *   Android treats as "don't ask again".
 * A dialog the user dismissed (Back, tap outside) is neither: it can be asked again.
 */
internal fun smsAccessBlocked(
    granted: Boolean,
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
    elapsedMillis: Long,
): Boolean = !granted && (elapsedMillis < NO_DIALOG_MILLIS || (rationaleBefore && !rationaleAfter))

/** Faster than anyone can read and answer a permission dialog. */
internal const val NO_DIALOG_MILLIS = 400L
