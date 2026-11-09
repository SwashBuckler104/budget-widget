package io.github.swashbuckler104.budgetwidget.feature.transactions.debug

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.Transaction
import io.github.swashbuckler104.budgetwidget.core.model.formatInr
import io.github.swashbuckler104.budgetwidget.feature.transactions.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
private const val BACKFILL_DAYS = 30

/** M1 acceptance screen: every stored transaction, newest first, plus manual sync controls. */
@Composable
fun DebugTransactionsRoute(modifier: Modifier = Modifier, viewModel: DebugTransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            SMS_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
        if (granted) viewModel.syncNow()
    }
    DebugTransactionsScreen(
        state = state,
        smsGranted = granted,
        onRequestSms = { launcher.launch(SMS_PERMISSIONS) },
        onSyncNow = viewModel::syncNow,
        onBackfill = { viewModel.backfill(BACKFILL_DAYS) },
        modifier = modifier,
    )
}

@Composable
internal fun DebugTransactionsScreen(
    state: DebugTransactionsState,
    smsGranted: Boolean,
    onRequestSms: () -> Unit,
    onSyncNow: () -> Unit,
    onBackfill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        pluralStringResource(
                            R.plurals.debug_transaction_count,
                            state.transactions.size,
                            state.transactions.size,
                        ) +
                            " · " +
                            pluralStringResource(
                                R.plurals.debug_unparsed_count,
                                state.unparsedCount,
                                state.unparsedCount,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (smsGranted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSyncNow) { Text(stringResource(R.string.debug_sync_now)) }
                            OutlinedButton(onClick = onBackfill) {
                                Text(pluralStringResource(R.plurals.debug_backfill, BACKFILL_DAYS, BACKFILL_DAYS))
                            }
                        }
                    } else {
                        Button(onClick = onRequestSms) { Text(stringResource(R.string.debug_grant_sms)) }
                    }
                    HorizontalDivider()
                }
            }
            if (!state.loading && state.transactions.isEmpty()) {
                item { Text(stringResource(R.string.debug_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            items(state.transactions, key = { it.id }) { TransactionRow(it) }
        }
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
private fun TransactionRow(transaction: Transaction, modifier: Modifier = Modifier) {
    val sign = if (transaction.direction == Direction.IN) "+" else "-"
    val amountColor = if (transaction.direction ==
        Direction.IN
    ) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val account = listOfNotNull(transaction.bankName, transaction.accountLast4?.let { "··$it" }).joinToString(" ")
    val time = timeFormat.format(Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()))
    val flags = buildList {
        add(transaction.status.name.lowercase())
        transaction.parserType?.let { add(it.name.lowercase()) }
        if (transaction.isCard) add(stringResource(R.string.debug_flag_card))
        if (transaction.isBackfill) add(stringResource(R.string.debug_flag_backfill))
        transaction.duplicateOfId?.let { add(stringResource(R.string.debug_flag_duplicate, it)) }
    }.joinToString(" · ")
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                transaction.merchantDisplay ?: stringResource(R.string.debug_unknown_merchant),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                sign + formatInr(transaction.amountPaise),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
            )
        }
        Text(
            "$account · $time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(flags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
