package io.github.swashbuckler104.budgetwidget.feature.purple

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.purple.Purple
import io.github.swashbuckler104.budgetwidget.core.data.purple.PurpleNote
import io.github.swashbuckler104.budgetwidget.core.data.purple.PurpleTip
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class PurpleViewModel @Inject constructor(private val purple: Purple) : ViewModel() {
    fun note(installedAtMillis: Long): Flow<PurpleNote?> = purple.note(installedAtMillis)

    fun answer(note: PurpleNote, accepted: Boolean) {
        viewModelScope.launch { purple.answer(note, accepted) }
    }

    fun skipTour() {
        viewModelScope.launch { purple.skipTour() }
    }
}

/** Where the app sends the user from Purple's buttons. */
class PurpleActions(
    /** Opens the store listing or the release page (ADR-0060). */
    val onCheckUpdates: () -> Unit,
    /** "Help improve the parser" (ADR-0043). */
    val onOpenParserFeedback: () -> Unit,
    /** Quick-Sort over the whole inbox. */
    val onSortAll: () -> Unit,
)

/**
 * Purple, the in-app helper (ADR-0063): one short note at a time, over the main screen. Tour steps
 * point at their tab in the bottom bar; everything else appears at the top right, under the app
 * bar. Put it over the tab content, inside the bottom bar's padding.
 *
 * [visible]: a tab is showing (not Settings or a drill-down). [tabs]: the tour steps in bottom bar
 * order, so each points at its own tab.
 */
@Composable
fun PurpleHost(
    visible: Boolean,
    tabs: List<PurpleTip>,
    actions: PurpleActions,
    modifier: Modifier = Modifier,
    viewModel: PurpleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val info = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val flow = remember(viewModel, info) { viewModel.note(info.lastUpdateTime) }
    val note by flow.collectAsStateWithLifecycle(initialValue = null)
    // Hidden as soon as it is answered, before the stored answer comes back: no double taps.
    var answered by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = note?.takeIf { visible && it.key != answered }

    fun answer(current: PurpleNote, accepted: Boolean) {
        answered = current.key
        viewModel.answer(current, accepted)
    }
    PurpleOverlay(
        note = shown,
        version = info.versionName.orEmpty(),
        tabs = tabs,
        onPrimary = { current ->
            when (current.tip) {
                PurpleTip.UPDATE -> actions.onCheckUpdates()
                PurpleTip.UNREAD_SMS -> actions.onOpenParserFeedback()
                PurpleTip.SORT_ALL -> actions.onSortAll()
                else -> Unit
            }
            answer(current, accepted = true)
        },
        onSecondary = { current ->
            if (current.tip.isTour) {
                answered = current.key
                viewModel.skipTour()
            } else {
                answer(current, accepted = false)
            }
        },
        modifier = modifier,
    )
}

/** Stable per note, so a newer note of the same kind (more unread SMS) shows again. */
private val PurpleNote.key: String
    get() = listOfNotNull(tip.id, always?.merchantKey, newestUnreadId.takeIf { it > 0 }).joinToString(":")

@Composable
internal fun PurpleOverlay(
    note: PurpleNote?,
    version: String,
    tabs: List<PurpleTip>,
    onPrimary: (PurpleNote) -> Unit,
    onSecondary: (PurpleNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = note,
            contentKey = { it?.key },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "purple",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            if (current == null) return@AnimatedContent
            val tab = tabs.indexOf(current.tip).takeIf { it >= 0 }
            Box(Modifier.fillMaxSize()) {
                val bubble = @Composable { bubbleModifier: Modifier, caretFraction: Float? ->
                    PurpleBubble(
                        note = current,
                        version = version,
                        onPrimary = { onPrimary(current) },
                        onSecondary = { onSecondary(current) },
                        caretFraction = caretFraction,
                        modifier = bubbleModifier,
                    )
                }
                if (tab != null) {
                    // Over the bottom bar, its caret over the tab's center.
                    val center = (tab + 0.5f) / tabs.size
                    bubble(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), center)
                } else {
                    bubble(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = APP_BAR, end = EDGE, start = EDGE),
                        null,
                    )
                }
            }
        }
    }
}

private val EDGE = 12.dp

/** Below the tab screens' top app bar (Material's 64dp). */
private val APP_BAR = 64.dp
