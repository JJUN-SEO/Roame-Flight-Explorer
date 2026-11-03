package travel.roame.takehome.android.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import travel.roame.takehome.android.R
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.DataStatus
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/** Two tabs (UI_SPEC §1). The app only renders the shared state and sends intents. */
@Composable
fun ExplorerApp(store: ExplorerStore) {
    val state by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("✈") }, label = { Text(stringResource(R.string.tab_explore)) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("♡") }, label = { Text(stringResource(R.string.tab_saved)) })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> ExploreScreen(state, store)
                else -> SavedScreen(state)
            }
        }
    }
}

@Composable
private fun ExploreScreen(state: ExplorerState, store: ExplorerStore) {
    when (val data = state.data) {
        DataStatus.Loading -> LoadingList()
        is DataStatus.Unavailable -> UnavailableMessage(data.error, store::refresh)
        is DataStatus.Ready -> when {
            // UI_SPEC §4: a successful empty response is not an error (R4).
            state.options.isEmpty -> Message(stringResource(R.string.synced_empty_title), stringResource(R.string.synced_empty_body))
            else -> Column {
                var openSheet by rememberSaveable { mutableStateOf<FilterKind?>(null) }
                SearchHeader(state, store)
                FilterBar(state, store, onOpen = { openSheet = it })
                ResultSummary(state, onReset = store::resetAll)
                if (state.results.isEmpty()) FilteredEmpty(state, onReset = store::resetAll) else ResultsList(state)
                openSheet?.let { kind -> FilterSheet(kind, state, store, onDismiss = { openSheet = null }) }
            }
        }
    }
}

@Composable
private fun ResultsList(state: ExplorerState) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // UI_SPEC §5: scrolling the list dismisses the keyboard.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focusManager.clearFocus() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.results, key = { it.id }) { flight -> FlightRow(flight) }
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
private fun SavedScreen(state: ExplorerState) {
    if (state.saved.isEmpty()) {
        Message(stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_body))
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.saved, key = { it.flight.id }) { saved -> FlightRow(saved.flight) }
        }
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
