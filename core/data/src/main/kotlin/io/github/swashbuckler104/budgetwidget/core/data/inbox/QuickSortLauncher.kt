package io.github.swashbuckler104.budgetwidget.core.data.inbox

import android.content.Context
import android.content.Intent

/**
 * Builds intents that open the Quick-Sort sheet, which lives in `:app`. Notifications and widgets
 * use it so they don't depend on the app module. The intents start a new task: callers launch them
 * from outside an activity (PendingIntents).
 */
interface QuickSortLauncher {
    /** Sort one transaction, then close. */
    fun transaction(context: Context, transactionId: Long): Intent

    /** Walk the whole inbox, oldest first. */
    fun inbox(context: Context): Intent
}
