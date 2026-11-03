package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import travel.roame.takehome.android.R
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.DataStatus
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import travel.roame.takehome.presentation.UserMessageKind

/**
 * Two tabs (UI_SPEC §1). The app only renders the shared state and sends intents.
 * [failNextWrite] is set in debug builds only (TEST_PLAN §6).
 */
@Composable
fun ExplorerApp(store: ExplorerStore, failNextWrite: (() -> Unit)? = null) {
    val state by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val debugArmed = stringResource(R.string.debug_fail_next)
    val onDebugLongPress = failNextWrite?.let { arm ->
        {
            arm()
            scope.launch { snackbar.showSnackbar(debugArmed) }
            Unit
        }
    }

    WriteMessages(state, store, snackbar)
    SuccessHaptics(state)
    var cardInset by remember { mutableStateOf(0.dp) }
    // Kept here, outside both tabs, so List or Map, the list's position and the map's camera survive tab switches.
    val view = ExploreView(
        showMap = rememberSaveable { mutableStateOf(false) },
        listState = rememberLazyListState(),
        mapCamera = rememberSaveable { mutableStateOf<DoubleArray?>(null) },
    )
    ScrollToTopOnNewSearch(state, view.listState)

    Scaffold(
        // Above the selection card, so a message never hides the card's Save or Remove button.
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = cardInset)) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("✈") }, label = { Text(stringResource(R.string.tab_explore)) })
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (state.saved.isNotEmpty()) Badge { Text(groupedCount(state.saved.size)) } }) { Text("♡") }
                    },
                    label = { Text(stringResource(R.string.tab_saved)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> ExploreScreen(state, store, view, onDebugLongPress, onCardInset = { cardInset = it })
                else -> SavedScreen(state, store, onDebugLongPress, onCardInset = { cardInset = it })
            }
        }
    }
}

/** V7: each failed write is shown once as a snackbar with Retry, then marked shown. */
@Composable
private fun WriteMessages(state: ExplorerState, store: ExplorerStore, snackbar: SnackbarHostState) {
    val message = state.messages.firstOrNull() ?: return
    val text = stringResource(
        when (message.kind) {
            UserMessageKind.SaveFailed -> R.string.save_failed
            UserMessageKind.RemoveFailed -> R.string.remove_failed
        },
    )
    val retry = stringResource(R.string.retry)
    LaunchedEffect(message.id) {
        val result = snackbar.showSnackbar(text, actionLabel = retry, duration = SnackbarDuration.Long)
        store.messageShown(message.id)
        if (result == SnackbarResult.ActionPerformed) {
            when (message.kind) {
                UserMessageKind.SaveFailed -> store.toggleSave(message.flightId)
                UserMessageKind.RemoveFailed -> store.removeSaved(message.flightId)
            }
        }
    }
}

/** What Explore shows and where, kept across tab switches and rotation. */
private class ExploreView(val showMap: MutableState<Boolean>, val listState: LazyListState, val mapCamera: MutableState<DoubleArray?>)

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

@Composable
private fun ExploreScreen(
    state: ExplorerState,
    store: ExplorerStore,
    view: ExploreView,
    onDebugLongPress: (() -> Unit)?,
    onCardInset: (Dp) -> Unit,
) {
    // UI_SPEC §5: back closes a sheet first (the sheet handles that), then deselects, then exits.
    BackHandler(enabled = state.selected != null) { store.select(null) }
    when (val data = state.data) {
        DataStatus.Loading -> LoadingList()
        is DataStatus.Unavailable -> UnavailableMessage(data.error, store::refresh)
        is DataStatus.Ready -> when {
            // UI_SPEC §4: a successful empty response is not an error (R4).
            state.options.isEmpty -> Message(stringResource(R.string.synced_empty_title), stringResource(R.string.synced_empty_body))
            else -> Column {
                var openSheet by rememberSaveable { mutableStateOf<FilterKind?>(null) }
                var openMarker by rememberSaveable { mutableStateOf<String?>(null) }
                var showMap by view.showMap
                SearchHeader(state, store)
                FilterBar(state, store, onOpen = { openSheet = it })
                ResultSummary(state, onReset = store::resetAll)
                // L4: switching changes only the view; the selection and filters stay in the store.
                ViewSwitch(showMap, onChange = { showMap = it })
                val selected = state.selected
                WithSelectionCard(
                    card = selected?.let { flight ->
                        {
                            FlightCard(
                                flight,
                                isPending = flight.id in state.pendingWriteIds,
                                onClose = { store.select(null) },
                                onToggleSave = { store.toggleSave(flight.id) },
                                onDebugLongPress = onDebugLongPress,
                            )
                        }
                    },
                    onInset = onCardInset,
                ) { bottomInset ->
                    val reduceMotion = rememberReduceMotion()
                    Crossfade(showMap, animationSpec = if (reduceMotion) snap() else tween(200), label = "List or Map") { map ->
                        when {
                            map -> ExplorerMap(
                                state,
                                camera = view.mapCamera,
                                bottomInset = bottomInset,
                                onMarkerTap = { openMarker = it },
                                onReset = store::resetAll,
                                modifier = Modifier.fillMaxSize(),
                            )
                            state.results.isEmpty() -> FilteredEmpty(state, onReset = store::resetAll)
                            else -> ResultsList(state, store, view.listState, bottomInset)
                        }
                    }
                }
                openSheet?.let { kind -> FilterSheet(kind, state, store, onDismiss = { openSheet = null }) }
                // M5: the sheet follows the results live, and closes when its destination drops out.
                val marker = state.markers.firstOrNull { it.code == openMarker }
                if (showMap && marker != null) {
                    MarkerSheet(marker, state, store, onDismiss = { openMarker = null })
                } else if (openMarker != null) {
                    LaunchedEffect(Unit) { openMarker = null }
                }
            }
        }
    }
}

/** UI_SPEC §1: List | Map. */
@Composable
private fun ViewSwitch(showMap: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegmentedButton(selected = !showMap, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
            Text(stringResource(R.string.view_list))
        }
        SegmentedButton(selected = showMap, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
            Text(stringResource(R.string.view_map))
        }
    }
}

@Composable
private fun ResultsList(state: ExplorerState, store: ExplorerStore, listState: LazyListState, bottomInset: Dp) {
    val focusManager = LocalFocusManager.current
    // UI_SPEC §5: scrolling the list dismisses the keyboard.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focusManager.clearFocus() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.results, key = { it.id }) { flight ->
            FlightRow(
                flight,
                isSelected = flight.id == state.selected?.id,
                isPending = flight.id in state.pendingWriteIds,
                onClick = {
                    focusManager.clearFocus()
                    store.select(flight.id)
                },
                onToggleSave = { store.toggleSave(flight.id) },
            )
        }
    }
}

/** V8, L7: every saved flight whatever the filters, with its own selection and card. */
@Composable
private fun SavedScreen(state: ExplorerState, store: ExplorerStore, onDebugLongPress: (() -> Unit)?, onCardInset: (Dp) -> Unit) {
    BackHandler(enabled = state.savedSelected != null) { store.selectSaved(null) }
    if (state.saved.isEmpty()) {
        Message(stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_body))
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
        onInset = onCardInset,
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

/**
 * Lays the selection card over the bottom of [content], which pads by [Dp] so its last row can scroll clear of the card.
 * [onInset] reports the same height, so snackbars can sit above the card.
 */
@Composable
private fun WithSelectionCard(card: (@Composable () -> Unit)?, onInset: (Dp) -> Unit, content: @Composable (bottomInset: Dp) -> Unit) {
    // Starts at 0 for each new card, so the inset stays 0 until the card has really been measured.
    var cardHeight by remember(card == null) { mutableIntStateOf(0) }
    val inset = if (cardHeight == 0) 0.dp else with(LocalDensity.current) { cardHeight.toDp() } + 16.dp
    LaunchedEffect(inset) { onInset(inset) }
    DisposableEffect(Unit) { onDispose { onInset(0.dp) } }
    Box(Modifier.fillMaxSize()) {
        content(inset)
        if (card != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).onSizeChanged { cardHeight = it.height }) { card() }
        }
    }
}

/** UI_SPEC §4 first load: skeleton rows with a label. */
@Composable
private fun LoadingList() {
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

/** R7, R9: the cause, in the words UI_SPEC §4 gives it, with Retry. */
@Composable
private fun UnavailableMessage(error: FetchError, onRetry: () -> Unit) {
    val (title, body) = when (error) {
        FetchError.Offline -> stringResource(R.string.error_offline_title) to stringResource(R.string.error_offline_body)
        FetchError.Timeout -> stringResource(R.string.error_timeout) to null
        is FetchError.Server -> stringResource(R.string.error_server, error.status) to null
        FetchError.InvalidData -> stringResource(R.string.error_invalid_data) to null
        FetchError.Unknown -> stringResource(R.string.error_unknown) to null
    }
    Message(title, body, icon = "⚠") {
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
private fun Message(title: String, body: String?, icon: String? = null, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Text(icon, style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        action()
    }
}
