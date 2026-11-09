package io.github.swashbuckler104.budgetwidget.core.data

/** Current time as epoch milliseconds (UTC). Injected so tests control time. */
fun interface WallClock {
    fun nowMillis(): Long
}
