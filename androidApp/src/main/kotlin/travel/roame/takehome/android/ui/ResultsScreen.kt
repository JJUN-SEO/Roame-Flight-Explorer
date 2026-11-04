package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import travel.roame.takehome.android.R
import travel.roame.takehome.presentation.DataStatus
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the results sheet rests (UI_SPEC §2.1): its header only, half the map, or the whole area. */
internal enum class SheetStop { Peek, Half, Full }

/**
 * What the results show and where, kept across pages, tabs and rotation. The map itself is kept across pages and tabs,
 * but made again when the activity is recreated, because it holds the activity (D31).
 */
internal class ResultsView(
    val sheet: MutableState<SheetStop>,
    val listState: LazyListState,
    val mapCamera: MutableState<DoubleArray?>,
    val map: MapHolder,
)

/**
 * The results, like Airbnb's (Step 6.3d, D36, UI_SPEC §2.1), drawn the same as iOS's ResultsScreen (D38): the search
 * summary with ← and Filters, the active filters, then the map with the flight list as a sheet over it. Selecting a
 * flight, from the list or a marker, drops the sheet so the map frames its route, with the selection card above it.
 */
@Composable
internal fun ResultsScreen(
    state: ExplorerState,
    store: ExplorerStore,
    view: ResultsView,
    active: Boolean,
    onBack: () -> Unit,
    onEditSearch: () -> Unit,
    onDebugLongPress: (() -> Unit)?,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    // UI_SPEC §5: back deselects first, then leaves the results.
    BackHandler(enabled = active) { if (state.selected != null) store.select(null) else onBack() }
    // The fields used to live here and held the focus; let go of it when the results are left.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) { if (!active) focusManager.clearFocus() }

    Column(Modifier.fillMaxSize()) {
        ResultsHeader(state, store, onBack, onEditSearch, onFilters = { filtersOpen = true })
        when (val data = state.data) {
            DataStatus.Loading -> LoadingList()
            is DataStatus.Unavailable -> UnavailableMessage(data.error, store::refresh)
            is DataStatus.Ready -> {
                StaleBanner(data)
                RefreshProgress(data)
                if (state.options.isEmpty) {
                    // UI_SPEC §4: a successful empty response is not an error (R4).
                    EmptyState(R.drawable.ms_flight, stringResource(R.string.synced_empty_title), stringResource(R.string.synced_empty_body))
                } else {
                    MapWithSheet(state, store, view, active, onDebugLongPress, onFilters = { filtersOpen = true })
                }
            }
        }
    }
    if (filtersOpen) FiltersSheet(state, store, onDismiss = { filtersOpen = false })
}

/** ← to the landing page, the search summary (tap it to change the search), and Filters with its count. */
@Composable
private fun ResultsHeader(state: ExplorerState, store: ExplorerStore, onBack: () -> Unit, onEditSearch: () -> Unit, onFilters: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = stringResource(R.string.back)) }
            SearchPill(state, onEditSearch, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FiltersButton(state.activeFilters.size, onFilters)
        }
        ActiveFilterBar(state, store, onOpen = onFilters)
    }
}

/** Airbnb's filter button: a round outlined button with the count of active filters on a badge. */
@Composable
private fun FiltersButton(count: Int, onClick: () -> Unit) {
    val description = if (count > 0) pluralStringResource(R.plurals.filters_button, count, count) else stringResource(R.string.filters_button_none)
    Box(Modifier.size(48.dp)) {
        Box(
            Modifier.size(48.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ms_tune), contentDescription = null)
        }
        if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * The map fills the area; the list is a sheet over it that rests at [SheetStop]s. Only the sheet's header drags it
 * while the list is fully open and scrolls; otherwise the whole sheet drags. The same rules as iOS's.
 */
@Composable
private fun MapWithSheet(
    state: ExplorerState,
    store: ExplorerStore,
    view: ResultsView,
    active: Boolean,
    onDebugLongPress: (() -> Unit)?,
    onFilters: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var openMarker by rememberSaveable { mutableStateOf<String?>(null) }
    var stop by view.sheet
    var cardHeight by remember(state.selected?.id) { mutableIntStateOf(0) }

    // A new selection, from the list, a marker or a deal, drops the sheet so the map shows the route.
    LaunchedEffect(state.selected?.id) { if (state.selected != null) stop = SheetStop.Peek }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val peek = with(density) { PeekHeight.toPx() }
        fun anchor(at: SheetStop) = when (at) {
            SheetStop.Full -> 0f
            SheetStop.Half -> height * 0.5f
            SheetStop.Peek -> (height - peek).coerceAtLeast(0f)
        }
        // One Animatable, so a drag or a new animation stops whatever animation is running.
        val offset = remember { Animatable(anchor(stop)) }
        var dragging by remember { mutableStateOf(false) }
        // Follows the resting place whenever it changes from outside (a selection, Map) or the area resizes.
        LaunchedEffect(stop, height) { if (!dragging) offset.animateTo(anchor(stop)) }
        val settle: (Float) -> Unit = { velocity ->
            val stops = SheetStop.entries.sortedBy { anchor(it) }
            val at = offset.value
            val target = when {
                // A flick goes one stop in its direction; otherwise the nearest stop.
                velocity < -FlingVelocity -> stops.lastOrNull { anchor(it) < at - 1f } ?: stops.first()
                velocity > FlingVelocity -> stops.firstOrNull { anchor(it) > at + 1f } ?: stops.last()
                else -> stops.minBy { abs(anchor(it) - at) }
            }
            scope.launch {
                offset.animateTo(anchor(target), initialVelocity = velocity)
                dragging = false
            }
            stop = target
        }
        val drag = rememberDraggableState { delta ->
            dragging = true
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(anchor(SheetStop.Full), anchor(SheetStop.Peek))) }
        }

        val showCard = state.selected != null && stop == SheetStop.Peek
        val covered = with(density) { (height - anchor(stop)).toDp() } + if (showCard && cardHeight > 0) with(density) { cardHeight.toDp() } + 12.dp else 0.dp
        // Made the first time the results show, not while the landing page is up; then kept (D31).
        if (active || view.map.view != null) {
            ExplorerMap(
                state,
                holder = view.map,
                visible = active,
                camera = view.mapCamera,
                bottomInset = covered,
                insetReady = !showCard || cardHeight > 0,
                onMarkerTap = { openMarker = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        val selected = state.selected
        if (showCard && selected != null) {
            Box(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(0, (offset.value - cardHeight - with(density) { 12.dp.toPx() }).roundToInt()) }
                    .padding(horizontal = 16.dp)
                    .onSizeChanged { cardHeight = it.height },
            ) {
                FlightCard(
                    selected,
                    isPending = selected.id in state.pendingWriteIds,
                    onClose = { store.select(null) },
                    onToggleSave = { store.toggleSave(selected.id) },
                    onDebugLongPress = onDebugLongPress,
                )
            }
        }

        val sheetDrag = Modifier.draggable(drag, Orientation.Vertical, onDragStopped = { settle(it) })
        val full = stop == SheetStop.Full && !dragging
        Column(
            Modifier.fillMaxWidth()
                .height(with(density) { height.toDp() })
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .shadow(12.dp, SheetShape)
                .background(MaterialTheme.colorScheme.background, SheetShape)
                .then(if (full) Modifier else sheetDrag),
        ) {
            SheetHeader(state, Modifier.then(if (full) sheetDrag else Modifier))
            if (state.results.isEmpty()) {
                FilteredEmpty(state, onReset = store::resetAll)
            } else {
                Box(Modifier.weight(1f)) {
                    ResultsList(state, store, view.listState, scrollEnabled = full)
                    if (full) {
                        // Airbnb's way back to the map from the full list.
                        MapButton(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { stop = SheetStop.Peek }
                    }
                }
            }
        }
    }

    // M5: the sheet follows the results live, and closes when its destination drops out.
    val marker = state.markers.firstOrNull { it.code == openMarker }
    if (marker != null) {
        MarkerSheet(marker, state, store, onDismiss = { openMarker = null })
    } else if (openMarker != null) {
        LaunchedEffect(Unit) { openMarker = null }
    }
}

/** The handle and the count (C1). Spoken as one element that says the list can be dragged. */
@Composable
private fun SheetHeader(state: ExplorerState, modifier: Modifier) {
    val handle = stringResource(R.string.results_sheet_handle)
    val count = resultCountText(state)
    Column(
        modifier.fillMaxWidth().height(PeekHeight).clearAndSetSemantics {
            contentDescription = "$count. $handle"
            heading()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
        Text(
            if (state.results.isEmpty()) stringResource(R.string.filtered_empty_title) else count,
            Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultsList(state: ExplorerState, store: ExplorerStore, listState: LazyListState, scrollEnabled: Boolean) {
    LazyColumn(
        state = listState,
        userScrollEnabled = scrollEnabled,
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.results, key = { it.id }) { flight ->
            FlightRow(
                flight,
                isSelected = flight.id == state.selected?.id,
                isPending = flight.id in state.pendingWriteIds,
                onClick = { store.select(flight.id) },
                onToggleSave = { store.toggleSave(flight.id) },
            )
        }
    }
}

/** A dark pill with a map icon, over the full list. */
@Composable
private fun MapButton(modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(R.string.show_map)
    Row(
        modifier.height(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF2B2B2B))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ms_map), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** The sheet's header: the handle and the count, which is all that shows at [SheetStop.Peek]. */
private val PeekHeight = 64.dp

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** Faster than this, in pixels a second, a release goes on to the next stop rather than the nearest. */
private const val FlingVelocity = 1_000f
