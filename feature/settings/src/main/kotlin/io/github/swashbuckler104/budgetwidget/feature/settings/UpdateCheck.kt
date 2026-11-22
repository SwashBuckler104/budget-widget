package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri

/*
 * Update check without network access (ADR-0060): the app can't ask a server whether a newer
 * version exists, so it hands over to whoever can, the store it came from or the release page.
 */

private const val PLAY_STORE = "com.android.vending"
internal const val RELEASES_URL = "https://github.com/SwashBuckler104/budget-widget/releases/latest"

/** Where to look for a newer version: the Play listing for Play installs, else the GitHub releases. */
internal fun updatePageUrl(installer: String?, packageName: String): String =
    if (installer == PLAY_STORE) "market://details?id=$packageName" else RELEASES_URL

/** The app that installed this one, when Android says (API 30+); null means unknown (sideloaded). */
private fun installer(context: Context): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    try {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
} else {
    null
}

/** Opens the update page in the store or browser; falls back to the release page. */
fun openUpdatePage(context: Context) {
    fun open(url: String) =
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    try {
        open(updatePageUrl(installer(context), context.packageName))
    } catch (_: ActivityNotFoundException) {
        try {
            open(RELEASES_URL)
        } catch (_: ActivityNotFoundException) {
            // No browser: About shows the address to type in elsewhere.
        }
    }
}
