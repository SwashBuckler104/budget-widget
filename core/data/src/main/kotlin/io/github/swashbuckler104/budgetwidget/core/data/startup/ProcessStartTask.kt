package io.github.swashbuckler104.budgetwidget.core.data.startup

import kotlinx.coroutines.CoroutineScope

/**
 * Work every process start needs (create notification channels, arm reminders, keep widgets in
 * sync). Modules contribute implementations to a Hilt set (`@IntoSet`); the Application runs them
 * all from `onCreate`. [start] must return quickly: launch longer work in [scope], which lives as
 * long as the process.
 */
fun interface ProcessStartTask {
    fun start(scope: CoroutineScope)
}
