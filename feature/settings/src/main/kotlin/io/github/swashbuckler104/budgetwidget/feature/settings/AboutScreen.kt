package io.github.swashbuckler104.budgetwidget.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/** About (SCOPE.md F9): version, license, credits, privacy statement and the no-internet badge. */
@Composable
fun AboutRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }
    val offline = remember(context) { !requestsInternet(context) }
    AboutScreen(
        version = version,
        offline = offline,
        onBack = onBack,
        onOpenSource = { openLink(context, SOURCE_URL) },
        onOpenParser = { openLink(context, PARSER_URL) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    version: String,
    offline: Boolean,
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenParser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.settings_about))
            }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Column {
                Text(stringResource(R.string.settings_app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.settings_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (offline) OfflineBadge()
            Block(stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_body))
            Block(stringResource(R.string.settings_license_title), stringResource(R.string.settings_license_body))
            LinkRow(
                title = stringResource(R.string.settings_source),
                summary = SOURCE_URL,
                onClick = onOpenSource,
            )
            Block(stringResource(R.string.settings_credits_title), stringResource(R.string.settings_credits_body))
            LinkRow(
                title = stringResource(R.string.settings_parser_link),
                summary = PARSER_URL,
                onClick = onOpenParser,
            )
        }
    }
}

@Composable
private fun OfflineBadge() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null)
            Column {
                Text(stringResource(R.string.settings_offline_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.settings_offline_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Checked at runtime from the installed package, so the badge can't claim something untrue. */
private fun requestsInternet(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET)
} catch (_: PackageManager.NameNotFoundException) {
    true
}

/** Opens a web page in the user's browser (this app has no network access of its own). */
private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed: the address is on screen to type in elsewhere.
    }
}

private const val SOURCE_URL = "https://github.com/SwashBuckler104/budget-widget"
private const val PARSER_URL = "https://github.com/sarim2000/pennywiseai-tracker"
