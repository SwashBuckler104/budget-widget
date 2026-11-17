package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.feedback.FeedbackMessage
import io.github.swashbuckler104.budgetwidget.core.data.feedback.ParserFeedbackReport
import io.github.swashbuckler104.budgetwidget.core.data.feedback.ParserFeedbackScanner
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FeedbackState {
    data object Intro : FeedbackState

    data object Scanning : FeedbackState

    data object NoPermission : FeedbackState

    /** What will be shared, exactly; the user can remove any message. Kept in memory only. */
    data class Ready(val messages: List<FeedbackMessage>) : FeedbackState
}

/**
 * "Help improve the parser" (ADR-0043). The scan result lives only in this view model: nothing is
 * stored, and the budget (transactions, streaks, widgets, notifications) is never touched.
 */
@HiltViewModel
class ParserFeedbackViewModel @Inject constructor(
    private val scanner: ParserFeedbackScanner,
    private val clock: WallClock,
    private val resolver: ContentResolver,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FeedbackState>(FeedbackState.Intro)
    val state: StateFlow<FeedbackState> = mutableState.asStateFlow()

    private val savedChannel = Channel<Boolean>(Channel.BUFFERED)

    /** true: the file was saved; false: it could not be written. */
    val saved: Flow<Boolean> = savedChannel.receiveAsFlow()

    /** [keepAmounts]: share real transaction amounts; everything else stays masked (ADR-0046). */
    fun scan(keepAmounts: Boolean = false) {
        if (mutableState.value == FeedbackState.Scanning) return
        mutableState.value = FeedbackState.Scanning
        viewModelScope.launch {
            val found = scanner.scan(ParserFeedbackScanner.DAYS, clock.nowMillis(), keepAmounts)
            mutableState.value = found?.let { FeedbackState.Ready(it) } ?: FeedbackState.NoPermission
        }
    }

    fun remove(index: Int) = mutableState.update { s ->
        if (s is FeedbackState.Ready) s.copy(messages = s.messages.filterIndexed { i, _ -> i != index }) else s
    }

    /** "Remove all from <sender>". */
    fun removeSender(sender: String) = mutableState.update { s ->
        if (s is FeedbackState.Ready) s.copy(messages = s.messages.filterNot { it.sender == sender }) else s
    }

    private fun messages() = (state.value as? FeedbackState.Ready)?.messages.orEmpty()

    /** The email body: a line for people, then the same JSON Lines as the file (ADR-0047). */
    fun emailBody(appVersion: String): String = ParserFeedbackReport.emailBody(messages(), appVersion)

    fun fileContent(appVersion: String): String = ParserFeedbackReport.jsonLines(messages(), appVersion)

    fun save(uri: Uri, appVersion: String) {
        val text = fileContent(appVersion)
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    // Truncating, so overwriting a longer file leaves no tail of it behind;
                    // some document providers only accept plain "w" (a new file is empty anyway).
                    val output = try {
                        resolver.openOutputStream(uri, "wt")
                    } catch (_: IOException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    } ?: resolver.openOutputStream(uri, "w") ?: throw IOException("no stream")
                    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            savedChannel.send(ok)
        }
    }
}

@Composable
fun ParserFeedbackRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ParserFeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion = remember(context) { appVersion(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.feedback_saved)
    val failedText = stringResource(R.string.settings_msg_file_failed)
    val noMailText = stringResource(R.string.feedback_no_mail_app)
    val address = stringResource(R.string.feedback_email_address)
    val subject = stringResource(R.string.feedback_email_subject)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON_LINES)) { uri ->
        uri?.let { viewModel.save(it, appVersion) }
    }
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { ok -> snackbar.showSnackbar(if (ok) savedText else failedText) }
    }
    ParserFeedbackScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onScan = { viewModel.scan(it) },
        onRemove = viewModel::remove,
        onEmail = {
            // The user's own mail app sends it: the app has no internet permission (ADR-0043).
            val mail = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, viewModel.emailBody(appVersion))
                // Only apps that handle mailto: (mail apps) are offered, not every share target.
                selector = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
            }
            try {
                context.startActivity(Intent.createChooser(mail, null))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(noMailText) }
            }
        },
        onRemoveSender = viewModel::removeSender,
        onSave = { save.launch("budget-widget-parser-feedback.jsonl") },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParserFeedbackScreen(
    state: FeedbackState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onScan: (keepAmounts: Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onEmail: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveSender: (String) -> Unit = {},
) {
    val messages = (state as? FeedbackState.Ready)?.messages.orEmpty()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            if (messages.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                    ) {
                        OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.feedback_save))
                        }
                        Button(onClick = onEmail, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.feedback_email))
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item { FeedbackHeader(state, messages.size, onScan) }
            val senders = messages.groupingBy { it.sender }.eachCount()
            if (senders.size > 1 || senders.values.any { it > 1 }) {
                item { SenderSummary(senders, onRemoveSender) }
            }
            itemsIndexed(messages) { index, message -> FeedbackCard(message) { onRemove(index) } }
        }
    }
}

@Composable
private fun FeedbackHeader(state: FeedbackState, count: Int, onScan: (keepAmounts: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        val body = MaterialTheme.typography.bodyMedium
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        when (state) {
            FeedbackState.Intro -> {
                Text(stringResource(R.string.feedback_intro), style = body)
                var keepAmounts by rememberSaveable { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = keepAmounts, role = Role.Checkbox) { keepAmounts = it },
                ) {
                    Checkbox(checked = keepAmounts, onCheckedChange = null)
                    Column {
                        Text(stringResource(R.string.feedback_keep_amounts), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.feedback_keep_amounts_summary), style = body, color = muted)
                    }
                }
                Button(onClick = { onScan(keepAmounts) }) { Text(stringResource(R.string.feedback_scan)) }
            }

            FeedbackState.Scanning -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.feedback_scanning), style = body)
            }

            FeedbackState.NoPermission -> Text(stringResource(R.string.feedback_no_permission), style = body)

            is FeedbackState.Ready -> if (count == 0) {
                Text(stringResource(R.string.feedback_nothing), style = body)
            } else {
                Text(
                    pluralStringResource(R.plurals.feedback_preview, count, count),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.feedback_preview_hint), style = body, color = muted)
            }
        }
    }
}

/** One row per sender with "Remove all", so a whole bank's messages go in one tap. */
@Composable
private fun SenderSummary(senders: Map<String, Int>, onRemoveSender: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            stringResource(R.string.feedback_by_sender),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        senders.forEach { (sender, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    pluralStringResource(R.plurals.feedback_sender_count, count, sender, count),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val description = stringResource(R.string.feedback_remove_all_from, sender)
                TextButton(
                    onClick = { onRemoveSender(sender) },
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Text(stringResource(R.string.feedback_remove_all))
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(message: FeedbackMessage, onRemove: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${message.sender} · ${message.month}" + if (message.count > 1) " · ×${message.count}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.feedback_remove, message.sender),
                    )
                }
            }
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

/** JSON Lines has no registered type; octet-stream keeps pickers from renaming the file. */
private const val MIME_JSON_LINES = "application/octet-stream"
