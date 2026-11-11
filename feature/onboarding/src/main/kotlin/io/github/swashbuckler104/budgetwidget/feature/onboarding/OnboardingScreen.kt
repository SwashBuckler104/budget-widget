package io.github.swashbuckler104.budgetwidget.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.CategoryAvatar
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

/**
 * First-run setup (SCOPE.md F1). [onPinWidget] asks the launcher to add the Quick-Sort widget;
 * null when the launcher can't (then the step explains how to add it by hand).
 */
@Composable
fun OnboardingRoute(
    onFinish: () -> Unit,
    onPinWidget: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val step by viewModel.step.collectAsStateWithLifecycle()
    val backfill by viewModel.backfill.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var smsGranted by remember { mutableStateOf(hasSms(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotifications(context)) }
    // True when only App info can grant SMS now (see smsAccessBlocked).
    var smsBlocked by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        smsGranted = hasSms(context)
        notificationsGranted = hasNotifications(context)
        onPauseOrDispose {}
    }
    fun rationale() = activity != null && activity.shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)
    var rationaleBefore by remember { mutableStateOf(false) }
    var requestedAt by remember { mutableLongStateOf(0L) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // From the system, not the result map: a cancelled request returns an empty map.
        smsGranted = hasSms(context)
        smsBlocked = smsAccessBlocked(
            granted = smsGranted,
            rationaleBefore = rationaleBefore,
            rationaleAfter = rationale(),
            elapsedMillis = SystemClock.elapsedRealtime() - requestedAt,
        )
        if (smsGranted) viewModel.next()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
        viewModel.next()
    }
    OnboardingScreen(
        state = OnboardingState(
            step = OnboardingStep.entries[step],
            smsGranted = smsGranted,
            smsBlocked = smsBlocked,
            notificationsGranted = notificationsGranted,
            backfill = backfill,
            groups = groups,
            canPinWidget = onPinWidget != null,
            samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
        ),
        actions = OnboardingActions(
            onNext = viewModel::next,
            onBack = viewModel::back,
            onAllowSms = {
                rationaleBefore = rationale()
                requestedAt = SystemClock.elapsedRealtime()
                smsLauncher.launch(SMS_PERMISSIONS)
            },
            onAllowNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.next()
                }
            },
            onOpenAppInfo = { openAppInfo(context) },
            onChooseBackfill = viewModel::chooseBackfill,
            onPinWidget = { onPinWidget?.invoke() },
            onFinish = { viewModel.finish(smsGranted, onFinish) },
        ),
        modifier = modifier,
    )
}

internal data class OnboardingState(
    val step: OnboardingStep,
    val smsGranted: Boolean,
    val smsBlocked: Boolean,
    val notificationsGranted: Boolean,
    val backfill: Backfill,
    val groups: List<CategoryGroup>,
    val canPinWidget: Boolean,
    val samsung: Boolean,
)

internal class OnboardingActions(
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onAllowSms: () -> Unit,
    val onAllowNotifications: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onChooseBackfill: (Backfill) -> Unit,
    val onPinWidget: () -> Unit,
    val onFinish: () -> Unit,
)

@Composable
internal fun OnboardingScreen(state: OnboardingState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { BottomBar(state, actions) },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "step",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { step ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> Welcome()

                    OnboardingStep.SMS -> SmsStep(state, actions)

                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        state.notificationsGranted,
                        actions.onAllowNotifications,
                    )

                    OnboardingStep.BATTERY -> BatteryStep(state.samsung, actions.onOpenAppInfo)

                    OnboardingStep.HISTORY -> HistoryStep(state.backfill, state.smsGranted, actions.onChooseBackfill)

                    OnboardingStep.CATEGORIES -> CategoriesStep(state.groups)

                    OnboardingStep.WIDGET -> WidgetStep(state.canPinWidget, actions.onPinWidget)
                }
            }
        }
    }
}

/** Back, progress dots and the step's main button, which always moves on (permissions are optional). */
@Composable
private fun BottomBar(state: OnboardingState, actions: OnboardingActions) {
    val last = state.step == OnboardingStep.entries.last()
    val position = stringResource(R.string.onboarding_progress, state.step.ordinal + 1, OnboardingStep.entries.size)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (state.step != OnboardingStep.WELCOME) {
                TextButton(onClick = actions.onBack) { Text(stringResource(R.string.onboarding_back)) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = position },
        ) {
            OnboardingStep.entries.forEach {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (it ==
                                state.step
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when {
                last -> Button(onClick = actions.onFinish) { Text(stringResource(R.string.onboarding_done)) }

                state.step == OnboardingStep.SMS && !state.smsGranted ->
                    TextButton(onClick = actions.onNext) { Text(stringResource(R.string.onboarding_not_now)) }

                state.step == OnboardingStep.NOTIFICATIONS && !state.notificationsGranted ->
                    TextButton(onClick = actions.onNext) { Text(stringResource(R.string.onboarding_not_now)) }

                else -> Button(onClick = actions.onNext) { Text(stringResource(R.string.onboarding_next)) }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.Lock, stringResource(R.string.onboarding_welcome_title))
        Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
        Bullet(stringResource(R.string.onboarding_welcome_point_sort))
        Bullet(stringResource(R.string.onboarding_welcome_point_widget))
        Bullet(stringResource(R.string.onboarding_welcome_point_offline))
    }
}

/** The prominent disclosure before the SMS permission prompt (SCOPE.md F1, §7). */
@Composable
private fun SmsStep(state: OnboardingState, actions: OnboardingActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.Sms, stringResource(R.string.onboarding_sms_title))
        Text(stringResource(R.string.onboarding_sms_body), style = MaterialTheme.typography.bodyLarge)
        Bullet(stringResource(R.string.onboarding_sms_point_banks))
        Bullet(stringResource(R.string.onboarding_sms_point_device))
        Bullet(stringResource(R.string.onboarding_sms_point_store))
        when {
            state.smsGranted -> Granted(stringResource(R.string.onboarding_sms_granted))

            state.smsBlocked -> RestrictedGuide(actions.onOpenAppInfo)

            else -> Button(onClick = actions.onAllowSms, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_sms_allow))
            }
        }
    }
}

/** SCOPE.md F1 step 4: the path through App info for sideloaded installs on Android 13+. */
@Composable
private fun RestrictedGuide(onOpenAppInfo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onboarding_restricted_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.onboarding_restricted_body), style = MaterialTheme.typography.bodyMedium)
            listOf(
                R.string.onboarding_restricted_step_1,
                R.string.onboarding_restricted_step_2,
                R.string.onboarding_restricted_step_3,
                R.string.onboarding_restricted_step_4,
            ).forEachIndexed { index, step ->
                Text("${index + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onOpenAppInfo) { Text(stringResource(R.string.onboarding_open_app_info)) }
        }
    }
}

@Composable
private fun NotificationsStep(granted: Boolean, onAllow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.NotificationsActive, stringResource(R.string.onboarding_notifications_title))
        Text(stringResource(R.string.onboarding_notifications_body), style = MaterialTheme.typography.bodyLarge)
        if (granted) {
            Granted(stringResource(R.string.onboarding_notifications_granted))
        } else {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_notifications_allow))
            }
        }
    }
}

/** SCOPE.md F1 step 5: optional; never declares REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. */
@Composable
private fun BatteryStep(samsung: Boolean, onOpenAppInfo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.BatteryChargingFull, stringResource(R.string.onboarding_battery_title))
        Text(stringResource(R.string.onboarding_battery_body), style = MaterialTheme.typography.bodyLarge)
        Bullet(stringResource(R.string.onboarding_battery_unrestricted))
        if (samsung) Bullet(stringResource(R.string.onboarding_battery_samsung))
        OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_open_app_info))
        }
    }
}

@Composable
private fun HistoryStep(selected: Backfill, smsGranted: Boolean, onChoose: (Backfill) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.History, stringResource(R.string.onboarding_history_title))
        Text(stringResource(R.string.onboarding_history_body), style = MaterialTheme.typography.bodyLarge)
        Column(modifier = Modifier.selectableGroup()) {
            Backfill.entries.forEach { choice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = choice == selected, role = Role.RadioButton) { onChoose(choice) },
                ) {
                    RadioButton(selected = choice == selected, onClick = null)
                    Column {
                        Text(stringResource(choice.title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(choice.summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!smsGranted && selected != Backfill.FRESH) {
            Text(
                stringResource(R.string.onboarding_history_needs_sms),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesStep(groups: List<CategoryGroup>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.Category, stringResource(R.string.onboarding_categories_title))
        Text(stringResource(R.string.onboarding_categories_body), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    CategoryAvatar(group.category.emoji, group.category.colorArgb, size = 28.dp)
                    Text(group.category.name, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WidgetStep(canPin: Boolean, onPin: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepHeader(Icons.Rounded.Widgets, stringResource(R.string.onboarding_widget_title))
        Text(stringResource(R.string.onboarding_widget_body), style = MaterialTheme.typography.bodyLarge)
        if (canPin) {
            Button(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_widget_add))
            }
        } else {
            Text(stringResource(R.string.onboarding_widget_manual), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Granted(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
        Spacer(Modifier.weight(1f))
    }
}

private val Backfill.title: Int
    get() = when (this) {
        Backfill.DAYS_30 -> R.string.onboarding_history_30
        Backfill.DAYS_90 -> R.string.onboarding_history_90
        Backfill.FRESH -> R.string.onboarding_history_fresh
    }

private val Backfill.summary: Int
    get() = when (this) {
        Backfill.DAYS_30 -> R.string.onboarding_history_30_summary
        Backfill.DAYS_90 -> R.string.onboarding_history_90_summary
        Backfill.FRESH -> R.string.onboarding_history_fresh_summary
    }

private fun hasSms(context: Context) =
    SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private fun hasNotifications(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
