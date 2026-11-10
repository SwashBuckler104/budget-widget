package io.github.swashbuckler104.budgetwidget.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** SCOPE.md F9. Grows with each milestone: M3 notifications, M4 the evening reminder. */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var systemAllows by rememberSaveable { mutableStateOf(true) }
    // The user can flip the permission in system settings while we're in the background.
    LifecycleResumeEffect(Unit) {
        systemAllows = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val openSystemSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val activity = LocalActivity.current

    // Whether Android would show the dialog: false both before and after a denied request means
    // no dialog appeared ("don't ask again"), and only system settings can grant it now.
    fun rationale() = activity != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    var rationaleBefore by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemAllows = granted
        if (!granted && !rationaleBefore && !rationale()) openSystemSettings()
    }
    SettingsScreen(
        state = state,
        notificationsBlocked = !systemAllows,
        actions = SettingsActions(
            onBack = onBack,
            setNotificationsEnabled = viewModel::setNotificationsEnabled,
            setMinAmount = viewModel::setMinAmount,
            setAutoSort = viewModel::setAutoSort,
            setNudgeEnabled = viewModel::setNudgeEnabled,
            setNudgeTime = viewModel::setNudgeTime,
            allowNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    rationaleBefore = rationale()
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openSystemSettings()
                }
            },
        ),
        modifier = modifier,
    )
}

internal class SettingsActions(
    val onBack: () -> Unit,
    val setNotificationsEnabled: (Boolean) -> Unit,
    val setMinAmount: (String) -> Unit,
    val setAutoSort: (Boolean) -> Unit,
    val setNudgeEnabled: (Boolean) -> Unit,
    val setNudgeTime: (hour: Int, minute: Int) -> Unit,
    val allowNotifications: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    notificationsBlocked: Boolean,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_notifications))
            val notifications = state.notifications
            if (notifications.enabled && notificationsBlocked) {
                BlockedCard(onAllow = actions.allowNotifications)
            }
            SwitchRow(
                title = stringResource(R.string.settings_notifications),
                summary = stringResource(R.string.settings_notifications_summary),
                checked = notifications.enabled,
                onCheckedChange = actions.setNotificationsEnabled,
            )
            MinAmountField(
                initialPaise = notifications.minAmountPaise,
                enabled = notifications.enabled,
                onChange = actions.setMinAmount,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_day))
            val nudge = state.nudge
            val time = formatMinuteOfDay(nudge.minuteOfDay)
            SwitchRow(
                title = stringResource(R.string.settings_nudge),
                summary = stringResource(R.string.settings_nudge_summary, time),
                checked = nudge.enabled,
                onCheckedChange = actions.setNudgeEnabled,
            )
            var picking by rememberSaveable { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_nudge_time)) },
                supportingContent = { Text(time) },
                modifier = Modifier.clickable(enabled = nudge.enabled, role = Role.Button) { picking = true },
            )
            if (picking) {
                NudgeTimeDialog(
                    minuteOfDay = nudge.minuteOfDay,
                    onConfirm = { hour, minute ->
                        actions.setNudgeTime(hour, minute)
                        picking = false
                    },
                    onDismiss = { picking = false },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_sorting))
            SwitchRow(
                title = stringResource(R.string.settings_auto_sort),
                summary = stringResource(R.string.settings_auto_sort_summary),
                checked = state.autoSort,
                onCheckedChange = actions.setAutoSort,
            )
        }
    }
}

/** Local time in the user's 12/24-hour preference, e.g. "9:00 PM". */
@Composable
private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val context = LocalContext.current
    val time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    // From the configuration, so a language change recomposes this text.
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NudgeTimeDialog(minuteOfDay: Int, onConfirm: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(
        initialHour = minuteOfDay / MINUTES_PER_HOUR,
        initialMinute = minuteOfDay % MINUTES_PER_HOUR,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_nudge_time)) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(picker.hour, picker.minute)
            }) { Text(stringResource(R.string.settings_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The whole row toggles, so the touch target is large and TalkBack reads one switch. */
@Composable
internal fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MinAmountField(
    initialPaise: Long,
    enabled: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local text: the stored value is whole rupees, but the user may be mid-edit (e.g. blank).
    var text by rememberSaveable {
        mutableStateOf((initialPaise / PAISE_PER_RUPEE).takeIf { it > 0 }?.toString().orEmpty())
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (SettingsViewModel.parseRupees(input) != null) {
                text = input
                onChange(input)
            }
        },
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_min_amount)) },
        prefix = { Text(stringResource(R.string.settings_rupee_prefix)) },
        placeholder = { Text("0") },
        supportingText = { Text(stringResource(R.string.settings_min_amount_summary)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun BlockedCard(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                stringResource(R.string.settings_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAllow) { Text(stringResource(R.string.settings_allow)) }
        }
    }
}

private const val PAISE_PER_RUPEE = 100L
private const val MINUTES_PER_HOUR = 60
