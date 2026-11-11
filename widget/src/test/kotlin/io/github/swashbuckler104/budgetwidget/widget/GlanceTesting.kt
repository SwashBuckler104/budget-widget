package io.github.swashbuckler104.budgetwidget.widget

import kotlin.time.Duration.Companion.seconds

/**
 * The Glance unit-test harness defaults to 2 s, which the first test of a class can miss under a
 * loaded full build (Robolectric and Glance start cold). A real hang still fails.
 */
internal val GLANCE_TIMEOUT = 15.seconds
