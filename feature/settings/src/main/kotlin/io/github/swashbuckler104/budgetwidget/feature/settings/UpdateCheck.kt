package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.update.UpdateReminder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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

@HiltViewModel
class UpdateReminderViewModel @Inject constructor(private val reminder: UpdateReminder) : ViewModel() {
    fun due(installedAtMillis: Long): Flow<Boolean> = reminder.due(installedAtMillis)

    fun snooze() {
        viewModelScope.launch { reminder.snooze() }
    }
}

/** Shows the "check for a newer version" reminder when it is due (ADR-0060). Put it on the main screen. */
@Composable
fun UpdateReminderHost(viewModel: UpdateReminderViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val info = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val due by remember(info) { viewModel.due(info.lastUpdateTime) }.collectAsStateWithLifecycle(initialValue = false)
    if (due) {
        UpdateReminderDialog(
            version = info.versionName.orEmpty(),
            onCheck = {
                openUpdatePage(context)
                viewModel.snooze()
            },
            onLater = viewModel::snooze,
        )
    }
}

@Composable
internal fun UpdateReminderDialog(version: String, onCheck: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.settings_update_title)) },
        text = { Text(stringResource(R.string.settings_update_body, version)) },
        confirmButton = { TextButton(onClick = onCheck) { Text(stringResource(R.string.settings_update_check)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.settings_update_later)) } },
    )
}
