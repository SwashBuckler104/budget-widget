package io.github.swashbuckler104.budgetwidget.core.data.inbox

/** Home-screen widgets showing the inbox. Implemented in `:widget`. */
interface InboxWidgets {
    /**
     * Redraws every widget now. Short-lived entry points (workers, notification buttons, alarms)
     * call it before they finish; inside the app, widgets follow database changes on their own.
     */
    suspend fun refreshNow()
}
