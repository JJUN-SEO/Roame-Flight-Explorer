package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import travel.roame.takehome.android.R
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.presentation.DataStatus
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import travel.roame.takehome.presentation.UserMessageKind

/**
 * Three tabs, Explore, Saved and Account, with Roame's tab bar drawn the same as on iOS (UI_SPEC §1, D38). The app only
 * renders the shared state and sends intents. [failNextWrite] is set in debug builds only (TEST_PLAN §6).
 */
@Composable
fun ExplorerApp(store: ExplorerStore, appearance: MutableState<Appearance>, failNextWrite: (() -> Unit)? = null) {
    val state by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AppTab.Explore) }
    // A short message of the app's own, such as the debug hook's confirmation, shown like a failed write's.
    var note by remember { mutableStateOf<String?>(null) }
    val debugArmed = stringResource(R.string.debug_fail_next)
    val onDebugLongPress = failNextWrite?.let { arm ->
        {
            arm()
            note = debugArmed
        }
    }

    SuccessHaptics(state)
    // Kept here, outside the tabs, so List or Map, the list's position, the map's camera and the map itself (D31)
    // survive tab switches.
    val view = ResultsView(
        sheet = rememberSaveable { mutableStateOf(SheetStop.Half) },
        listState = rememberLazyListState(),
        mapCamera = rememberSaveable { mutableStateOf<DoubleArray?>(null) },
        map = rememberMapHolder(),
    )
    ScrollToTopOnNewSearch(state, view.listState)
    // D32: asked when the app opens. Here, above the tabs, so switching tabs doesn't read the location again.
    UserLocation(store::setUserLocation)
    // D36: Explore opens on the landing page. The search bar and the banner open the search page, whose button shows
    // the results; a deal shows its results straight away.
    var page by rememberSaveable { mutableStateOf(ExplorePage.Landing) }
    val landingScroll = rememberScrollState()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                // D31: the results stay composed under the other pages and tabs, so the map is never detached from the
                // window. The landing page stays too, so it keeps its scroll position.
                val exploring = tab == AppTab.Explore
                Box(Modifier.fillMaxSize().notPlacedUnless(exploring && page == ExplorePage.Results)) {
                    ResultsScreen(
                        state,
                        store,
                        view,
                        active = exploring && page == ExplorePage.Results,
                        onBack = { page = ExplorePage.Landing },
                        onEditSearch = { page = ExplorePage.Search },
                        onDebugLongPress = onDebugLongPress,
                    )
                }
                Box(Modifier.fillMaxSize().notPlacedUnless(exploring && page == ExplorePage.Landing)) {
                    LandingScreen(
                        state,
                        landingScroll,
                        onSearch = { page = ExplorePage.Search },
                        onDestination = { deal ->
                            store.openDestination(deal.codes)
                            page = ExplorePage.Results
                        },
                        onOffer = { offer ->
                            store.openOffer(offer.flight.id)
                            page = ExplorePage.Results
                        },
                        onRetry = store::refresh,
                    )
                }
                // Made fresh each time, so it opens on its form.
                if (exploring && page == ExplorePage.Search) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SearchScreen(state, store, onClose = { page = ExplorePage.Landing }, onSearch = { page = ExplorePage.Results })
                    }
                }
                when (tab) {
                    AppTab.Saved -> SavedScreen(state, store, onDebugLongPress)
                    AppTab.Account -> AccountScreen(appearance, onToast = { note = it })
                    AppTab.Explore -> Unit
                }
            }
            AppTabBar(tab, state.saved.size, onSelect = { tab = it })
        }
        Toasts(state, store, note, onNoteShown = { note = null })
    }
}

/** Explore's three pages (D36): the landing page, the search page and the results. */
private enum class ExplorePage { Landing, Search, Results }

/**
 * At the top of the screen, as on iOS, where they can't cover the card's Save or Remove button. V7: each failed write
 * is shown once, with Retry, for 6 s; the app's own notes for 3 s.
 */
@Composable
private fun BoxScope.Toasts(state: ExplorerState, store: ExplorerStore, note: String?, onNoteShown: () -> Unit) {
    val message = state.messages.firstOrNull()
    val shownMessage = rememberLast(message)
    val shownNote = rememberLast(note)
    val reduceMotion = rememberReduceMotion()
    val enter = if (reduceMotion) fadeIn() else slideInVertically { -it } + fadeIn()
    val exit = if (reduceMotion) fadeOut() else slideOutVertically { -it } + fadeOut()
    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = message != null, enter = enter, exit = exit) {
            val shown = shownMessage ?: return@AnimatedVisibility
            val text = stringResource(
                when (shown.kind) {
                    UserMessageKind.SaveFailed -> R.string.save_failed
                    UserMessageKind.RemoveFailed -> R.string.remove_failed
                },
            )
            LaunchedEffect(shown.id) {
                delay(6_000)
                store.messageShown(shown.id)
            }
            AppToast(text, stringResource(R.string.retry)) {
                store.messageShown(shown.id)
                when (shown.kind) {
                    UserMessageKind.SaveFailed -> store.toggleSave(shown.flightId)
                    UserMessageKind.RemoveFailed -> store.removeSaved(shown.flightId)
                }
            }
        }
        AnimatedVisibility(visible = note != null, enter = enter, exit = exit) {
            val shown = shownNote ?: return@AnimatedVisibility
            LaunchedEffect(shown) {
                delay(3_000)
                onNoteShown()
            }
            AppToast(shown)
        }
    }
}

/** The last non-null value, so a toast can still show its text while it slides away. */
@Composable
private fun <T : Any> rememberLast(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}

/**
 * UI_SPEC §5: a new search or filter starts the list from the top. Otherwise the list keeps the old top flight in view,
 * and after Reset the first rows are hidden above it. A save or a refresh keeps the position, and so does rotation,
 * because the first value is only remembered.
 */
@Composable
private fun ScrollToTopOnNewSearch(state: ExplorerState, listState: LazyListState) {
    val search = listOf(state.originQuery, state.destinationQuery, state.originPick, state.destinationPick, state.filters)
    var shown by remember { mutableStateOf(search) }
    LaunchedEffect(search) {
        // Works while the map is showing too: the list applies it when it next lays out.
        if (search != shown) listState.requestScrollToItem(0)
        shown = search
    }
}

/** UI_SPEC §5: a haptic when a save or remove succeeds, that is when a write ends without an error message. */
@Composable
private fun SuccessHaptics(state: ExplorerState) {
    val haptics = LocalHapticFeedback.current
    var pendingBefore by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(state.pendingWriteIds) {
        val finished = pendingBefore - state.pendingWriteIds
        if (finished.any { id -> state.messages.none { it.flightId == id } }) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        pendingBefore = state.pendingWriteIds
    }
}

/**
 * D31: kept composed but not placed while the Saved tab is shown, so the map stays attached. Nothing is drawn, tapped
 * or read by TalkBack then. It's still measured, so the map keeps its size and the list its layout.
 */
private fun Modifier.notPlacedUnless(placed: Boolean): Modifier = if (placed) {
    this
} else {
    clearAndSetSemantics {}.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }
}

/** R11: the refresh on launch shows a thin bar over the data, never a full-screen loader. */
@Composable
internal fun RefreshProgress(data: DataStatus.Ready) {
    if (data.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
}

/**
 * R6, T4: the last refresh failed, so say how old the data is. There's no Retry (D29): the app refreshes
 * when it opens, and the next success clears the banner (R11).
 */
@Composable
internal fun StaleBanner(data: DataStatus.Ready) {
    if (data.refreshError == null) return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(R.string.stale_banner, syncTime(data.lastSuccessAt, System.currentTimeMillis())),
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** V8, L7: every saved flight whatever the filters, with its own selection and card. */
@Composable
private fun SavedScreen(state: ExplorerState, store: ExplorerStore, onDebugLongPress: (() -> Unit)?) {
    BackHandler(enabled = state.savedSelected != null) { store.selectSaved(null) }
    if (state.saved.isEmpty()) {
        EmptyState(R.drawable.ms_favorite, stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_body))
        return
    }
    val selected = state.savedSelected
    WithSelectionCard(
        card = selected?.let { saved ->
            {
                FlightCard(
                    saved.flight,
                    isPending = saved.flight.id in state.pendingWriteIds,
                    onClose = { store.selectSaved(null) },
                    onToggleSave = { store.removeSaved(saved.flight.id) },
                    isListed = saved.isListed,
                    onDebugLongPress = onDebugLongPress,
                )
            }
        },
    ) { bottomInset ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.saved, key = { it.flight.id }) { saved ->
                FlightRow(
                    saved.flight,
                    isSelected = saved.flight.id == selected?.flight?.id,
                    isPending = saved.flight.id in state.pendingWriteIds,
                    onClick = { store.selectSaved(saved.flight.id) },
                    onToggleSave = { store.removeSaved(saved.flight.id) },
                    isListed = saved.isListed,
                )
            }
        }
    }
}

/** Lays the selection card over the bottom of [content], which pads by [Dp] so its last row can scroll clear of the card. */
@Composable
private fun WithSelectionCard(card: (@Composable () -> Unit)?, content: @Composable (bottomInset: Dp) -> Unit) {
    // Starts at 0 for each new card, so the inset stays 0 until the card has really been measured.
    var cardHeight by remember(card == null) { mutableIntStateOf(0) }
    val inset = if (cardHeight == 0) 0.dp else with(LocalDensity.current) { cardHeight.toDp() } + 16.dp
    Box(Modifier.fillMaxSize()) {
        content(inset)
        if (card != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).onSizeChanged { cardHeight = it.height }) { card() }
        }
    }
}

/** UI_SPEC §4 first load: skeleton rows with a label. */
@Composable
internal fun LoadingList() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        repeat(7) {
            Box(
                Modifier.fillMaxWidth().height(92.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp)),
            )
        }
    }
}

/** R7, R9: the cause, in the words UI_SPEC §4 gives it, with Try again. */
@Composable
internal fun UnavailableMessage(error: FetchError, onRetry: () -> Unit) {
    val (title, body) = when (error) {
        FetchError.Offline -> stringResource(R.string.error_offline_title) to stringResource(R.string.error_offline_body)
        FetchError.Timeout -> stringResource(R.string.error_timeout) to null
        is FetchError.Server -> stringResource(R.string.error_server, error.status) to null
        FetchError.InvalidData -> stringResource(R.string.error_invalid_data) to null
        FetchError.Unknown -> stringResource(R.string.error_unknown) to null
    }
    EmptyState(R.drawable.ms_cloud_off, title, body) {
        PrimaryButton(stringResource(R.string.try_again), onRetry)
    }
}
