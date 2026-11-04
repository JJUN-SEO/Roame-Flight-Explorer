package travel.roame.takehome.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.model.AirportSuggestion
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/** From and To, each with its suggestions and hint (S13, S14, UI_SPEC §1). [onOriginFocused] asks for location (D30). */
@Composable
fun SearchHeader(state: ExplorerState, store: ExplorerStore, onOriginFocused: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        SearchField(
            label = stringResource(R.string.search_from),
            query = state.originQuery,
            pick = state.originPick,
            suggestions = state.originSuggestions,
            hint = state.originHint,
            onQueryChange = store::setOriginQuery,
            onPick = store::pickOrigin,
            // P5: location only ranks origins, so only From asks for it.
            onFocused = onOriginFocused,
        )
        SearchField(
            label = stringResource(R.string.search_to),
            query = state.destinationQuery,
            pick = state.destinationPick,
            suggestions = state.destinationSuggestions,
            hint = state.destinationHint,
            onQueryChange = store::setDestinationQuery,
            onPick = store::pickDestination,
        )
    }
}

@Composable
private fun SearchField(
    label: String,
    query: String,
    pick: AirportSuggestion?,
    suggestions: List<AirportSuggestion>,
    hint: List<AirportSuggestion>,
    onQueryChange: (String) -> Unit,
    onPick: (List<String>) -> Unit,
    onFocused: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // What the user typed. The store's copy arrives a moment later, so the field doesn't read it back while
    // typing; it only follows the store when the store empties it (Reset).
    var text by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { if (query.isEmpty()) text = "" }

    val shown = pick?.let { airportLabel(it) } ?: text
    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = shown,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            },
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ms_search), contentDescription = null) },
            trailingIcon = if (shown.isNotEmpty()) {
                {
                    val clear = stringResource(R.string.clear_field, label)
                    IconButton(onClick = {
                        text = ""
                        onQueryChange("")
                    }) { Icon(painterResource(R.drawable.ms_close), contentDescription = clear) }
                }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(28.dp),
        )
        if (hint.isNotEmpty()) {
            Text(
                hintText(hint),
                Modifier.padding(start = 16.dp, top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (focused && suggestions.isNotEmpty()) {
            SuggestionList(suggestions) { suggestion ->
                onPick(suggestion.codes)
                focusManager.clearFocus()
            }
        }
    }
}

/** At most 5 rows; the store has already capped and ranked them (S14). */
@Composable
private fun SuggestionList(suggestions: List<AirportSuggestion>, onTap: (AirportSuggestion) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider()
                Text(
                    airportLabel(suggestion),
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onTap(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = if (suggestion.isCityRow) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** "Tokyo (HND)", or "Tokyo (all airports)" for a city row (S14). */
@Composable
private fun airportLabel(suggestion: AirportSuggestion): String =
    if (suggestion.isCityRow) stringResource(R.string.city_label, suggestion.city)
    else stringResource(R.string.airport_label, suggestion.city, suggestion.codes.single())

/** S13: the first two, then "+N more". */
@Composable
private fun hintText(hint: List<AirportSuggestion>): String {
    val shown = hint.take(2).map { airportLabel(it) }.joinToString(", ")
    val more = hint.size - 2
    return if (more > 0) stringResource(R.string.hint_more, shown, more) else stringResource(R.string.hint, shown)
}

/** UI_SPEC §4: an empty result that isn't an error, with the active filters and a way back. */
@Composable
fun FilteredEmpty(state: ExplorerState, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.filtered_empty_title), style = MaterialTheme.typography.titleLarge)
        ActiveFilterList(state)
        TextButton(onClick = onReset) { Text(stringResource(R.string.reset_filters)) }
    }
}
