package io.github.swashbuckler104.budgetwidget.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.datastore.AppearanceSettings
import io.github.swashbuckler104.budgetwidget.core.datastore.ThemeMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** SCOPE.md F9: notifications, reminder, sorting, appearance, SMS import, data and about. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSmsIssues: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(messageText(resources, it)) }
    }
    val appVersion = remember(context) { appVersion(context) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        uri?.let { viewModel.backup(it, appVersion) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_CSV)) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }
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
        snackbar = snackbar,
        actions = SettingsActions(
            onBack = onBack,
            setNotificationsEnabled = viewModel::setNotificationsEnabled,
            setMinAmount = viewModel::setMinAmount,
            setAutoSort = viewModel::setAutoSort,
            setNudgeEnabled = viewModel::setNudgeEnabled,
            setNudgeTime = viewModel::setNudgeTime,
            setTheme = viewModel::setTheme,
            setDynamicColor = viewModel::setDynamicColor,
            importOlder = viewModel::importOlder,
            openAccounts = onOpenAccounts,
            openSmsIssues = onOpenSmsIssues,
            openAbout = onOpenAbout,
            exportCsv = { export.launch(fileName("transactions", "csv")) },
            backup = { backup.launch(fileName("backup", "json")) },
            restore = { restore.launch(arrayOf(MIME_JSON, "text/plain", "application/octet-stream")) },
            startFresh = viewModel::startFresh,
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
    val setTheme: (ThemeMode) -> Unit = {},
    val setDynamicColor: (Boolean) -> Unit = {},
    val importOlder: (days: Int) -> Unit = {},
    val openAccounts: () -> Unit = {},
    val openSmsIssues: () -> Unit = {},
    val openAbout: () -> Unit = {},
    val exportCsv: () -> Unit = {},
    val backup: () -> Unit = {},
    val restore: () -> Unit = {},
    val startFresh: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    notificationsBlocked: Boolean,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            AppearanceSection(state.appearance, actions)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SmsSection(state.unparsedCount, actions)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DataSection(state.busy, actions)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LinkRow(
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary),
                onClick = actions.openAbout,
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

@Composable
private fun AppearanceSection(appearance: AppearanceSettings, actions: SettingsActions) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_appearance))
        var picking by rememberSaveable { mutableStateOf(false) }
        LinkRow(
            title = stringResource(R.string.settings_theme),
            summary = stringResource(appearance.theme.label),
            onClick = { picking = true },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = appearance.dynamicColor,
                onCheckedChange = actions.setDynamicColor,
            )
        }
        if (picking) {
            ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries.map { stringResource(it.label) },
                selected = appearance.theme.ordinal,
                onPick = {
                    actions.setTheme(ThemeMode.entries[it])
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
    }
}

@Composable
private fun SmsSection(unparsedCount: Int, actions: SettingsActions) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_sms))
        var picking by rememberSaveable { mutableStateOf(false) }
        LinkRow(
            title = stringResource(R.string.settings_import_older),
            summary = stringResource(R.string.settings_import_older_summary),
            onClick = { picking = true },
        )
        LinkRow(
            title = stringResource(R.string.settings_unparsed),
            summary = pluralStringResource(R.plurals.settings_unparsed_summary, unparsedCount, unparsedCount),
            onClick = actions.openSmsIssues,
        )
        LinkRow(
            title = stringResource(R.string.settings_accounts),
            summary = stringResource(R.string.settings_accounts_summary),
            onClick = actions.openAccounts,
        )
        if (picking) {
            ChoiceDialog(
                title = stringResource(R.string.settings_import_older),
                options = IMPORT_DAYS.map { pluralStringResource(R.plurals.settings_import_days, it, it) },
                selected = null,
                onPick = {
                    actions.importOlder(IMPORT_DAYS[it])
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
    }
}

@Composable
private fun DataSection(busy: Boolean, actions: SettingsActions) {
    Column {
        SectionHeader(stringResource(R.string.settings_section_data))
        var confirmRestore by rememberSaveable { mutableStateOf(false) }
        var confirmFresh by rememberSaveable { mutableStateOf(false) }
        LinkRow(
            title = stringResource(R.string.settings_export_csv),
            summary = stringResource(R.string.settings_export_csv_summary),
            onClick = actions.exportCsv,
            enabled = !busy,
        )
        LinkRow(
            title = stringResource(R.string.settings_backup),
            summary = stringResource(R.string.settings_backup_summary),
            onClick = actions.backup,
            enabled = !busy,
        )
        LinkRow(
            title = stringResource(R.string.settings_restore),
            summary = stringResource(R.string.settings_restore_summary),
            onClick = { confirmRestore = true },
            enabled = !busy,
        )
        LinkRow(
            title = stringResource(R.string.settings_start_fresh),
            summary = stringResource(R.string.settings_start_fresh_summary),
            onClick = { confirmFresh = true },
            enabled = !busy,
            destructive = true,
        )
        if (confirmRestore) {
            ConfirmDialog(
                title = stringResource(R.string.settings_restore_confirm_title),
                body = stringResource(R.string.settings_restore_confirm_body),
                confirm = stringResource(R.string.settings_restore_choose),
                onConfirm = {
                    confirmRestore = false
                    actions.restore()
                },
                onDismiss = { confirmRestore = false },
            )
        }
        if (confirmFresh) {
            ConfirmDialog(
                title = stringResource(R.string.settings_start_fresh_confirm_title),
                body = stringResource(R.string.settings_start_fresh_confirm_body),
                confirm = stringResource(R.string.settings_start_fresh_confirm),
                destructive = true,
                onConfirm = {
                    confirmFresh = false
                    actions.startFresh()
                },
                onDismiss = { confirmFresh = false },
            )
        }
    }
}

/** A row that opens something: a screen, a dialog or a file picker. */
@Composable
internal fun LinkRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(title, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        supportingContent = { Text(summary) },
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = index == selected, role = Role.RadioButton) { onPick(index) },
                    ) {
                        RadioButton(selected = index == selected, onClick = null)
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private fun messageText(resources: Resources, message: SettingsMessage): String = when (message) {
    SettingsMessage.BackedUp -> resources.getString(R.string.settings_msg_backed_up)

    SettingsMessage.Exported -> resources.getString(R.string.settings_msg_exported)

    is SettingsMessage.Restored ->
        resources.getQuantityString(R.plurals.settings_msg_restored, message.transactions, message.transactions)

    SettingsMessage.NotABackup -> resources.getString(R.string.settings_msg_not_backup)

    SettingsMessage.BackupTooNew -> resources.getString(R.string.settings_msg_too_new)

    SettingsMessage.FileFailed -> resources.getString(R.string.settings_msg_file_failed)

    is SettingsMessage.StartedFresh ->
        resources.getQuantityString(R.plurals.settings_msg_started_fresh, message.removed, message.removed)

    is SettingsMessage.ImportStarted ->
        resources.getQuantityString(R.plurals.settings_msg_import_started, message.days, message.days)
}

/** `budget-widget-backup-2026-11-11.json`: dated, so several backups don't overwrite each other. */
private fun fileName(kind: String, extension: String) = "budget-widget-$kind-${LocalDate.now()}.$extension"

internal fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (_: PackageManager.NameNotFoundException) {
    ""
}

private const val MIME_JSON = "application/json"
private const val MIME_CSV = "text/csv"
private const val DISABLED_ALPHA = 0.38f
private val IMPORT_DAYS = listOf(30, 90, 365)
