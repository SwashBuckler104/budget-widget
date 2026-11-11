package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.EmptyState
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedSms
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "Unparsed bank SMS" list (SCOPE.md F2/F9): bank messages that didn't become transactions,
 * kept so the user can copy one into a parser bug report, or delete them.
 */
@HiltViewModel
class UnparsedViewModel @Inject constructor(private val transactions: TransactionRepository) : ViewModel() {
    /** Null while loading. */
    val items: StateFlow<List<UnparsedSms>?> = transactions.observeUnparsed()
        .map<List<UnparsedSms>, List<UnparsedSms>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun delete(id: Long) {
        viewModelScope.launch { transactions.deleteUnparsed(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { transactions.clearUnparsed() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun UnparsedRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: UnparsedViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    UnparsedScreen(items, onBack, viewModel::delete, viewModel::deleteAll, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnparsedScreen(
    items: List<UnparsedSms>?,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val copied = stringResource(R.string.settings_unparsed_copied)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_unparsed)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (!items.isNullOrEmpty()) {
                        IconButton(onClick = { confirmAll = true }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.settings_unparsed_delete_all),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            items == null -> Unit

            items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.MarkEmailRead,
                title = stringResource(R.string.settings_unparsed_empty_title),
                body = stringResource(R.string.settings_unparsed_empty_body),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.settings_unparsed_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(items, key = { it.id }) { sms ->
                    UnparsedCard(
                        sms = sms,
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(sms.sender, copyText(sms))))
                                snackbar.showSnackbar(copied)
                            }
                        },
                        onDelete = { onDelete(sms.id) },
                    )
                }
            }
        }
    }
    if (confirmAll) {
        ConfirmDialog(
            title = stringResource(R.string.settings_unparsed_delete_all),
            body = stringResource(R.string.settings_unparsed_delete_all_body),
            confirm = stringResource(R.string.settings_delete),
            destructive = true,
            onConfirm = {
                confirmAll = false
                onDeleteAll()
            },
            onDismiss = { confirmAll = false },
        )
    }
}

@Composable
private fun UnparsedCard(sms: UnparsedSms, onCopy: () -> Unit, onDelete: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(sms.receivedAt).atZone(ZoneId.systemDefault()))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sms.sender, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$time · ${stringResource(sms.reason.label)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCopy, enabled = sms.body != null) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.settings_unparsed_copy, sms.sender),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.settings_unparsed_delete, sms.sender),
                    )
                }
            }
            SelectionContainer {
                Text(
                    sms.body ?: stringResource(R.string.settings_unparsed_no_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** What goes on the clipboard for a bug report: sender and body, nothing about the user. */
private fun copyText(sms: UnparsedSms) = "Sender: ${sms.sender}\n${sms.body.orEmpty()}"

private val UnparsedReason.label: Int
    get() = when (this) {
        UnparsedReason.NO_MATCH -> R.string.settings_unparsed_reason_no_match
        UnparsedReason.UNSUPPORTED_CURRENCY -> R.string.settings_unparsed_reason_currency
        UnparsedReason.INVALID_AMOUNT -> R.string.settings_unparsed_reason_amount
        UnparsedReason.PARSER_ERROR -> R.string.settings_unparsed_reason_error
        UnparsedReason.WRONG_AMOUNT -> R.string.settings_unparsed_reason_wrong_amount
        UnparsedReason.UNKNOWN_SENDER -> R.string.settings_unparsed_reason_unknown_sender
    }
