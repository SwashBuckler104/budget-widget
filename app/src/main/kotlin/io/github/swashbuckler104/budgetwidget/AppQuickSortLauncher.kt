package io.github.swashbuckler104.budgetwidget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.swashbuckler104.budgetwidget.core.data.inbox.QuickSortLauncher
import javax.inject.Inject

/**
 * Opens [QuickSortActivity] from notifications and widgets. Each target gets its own data URI so
 * PendingIntents for different transactions never collapse into one, and CLEAR_TASK replaces a
 * sheet that is already open with the one just tapped.
 */
internal class AppQuickSortLauncher @Inject constructor() : QuickSortLauncher {
    override fun transaction(context: Context, transactionId: Long): Intent =
        QuickSortActivity.intent(context, transactionId).external("budgetwidget://quicksort/transaction/$transactionId")

    override fun inbox(context: Context): Intent =
        QuickSortActivity.intent(context, transactionId = null).external("budgetwidget://quicksort/inbox")

    private fun Intent.external(uri: String): Intent = setData(uri.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
