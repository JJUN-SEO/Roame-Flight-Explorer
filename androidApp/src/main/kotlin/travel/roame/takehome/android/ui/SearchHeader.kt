package travel.roame.takehome.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.model.AirportSuggestion
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/** From and To, each with its suggestions and hint (S13, S14, UI_SPEC §1). */
@Composable
fun SearchHeader(state: ExplorerState, store: ExplorerStore, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        SearchField(
            label = stringResource(R.string.search_from),
            query = state.originQuery,
            pick = state.originPick,
            suggestions = state.originSuggestions,
            hint = state.originHint,
            onQueryChange = store::setOriginQuery,
            onPick = store::pickOrigin,
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

/** One search field with its hint and suggestions. [onSubmit] runs on the keyboard's Search key, after the field lets go. */
@Composable
internal fun SearchField(
    label: String,
    query: String,
    pick: AirportSuggestion?,
    suggestions: List<AirportSuggestion>,
    hint: List<AirportSuggestion>,
    onQueryChange: (String) -> Unit,
    onPick: (List<String>) -> Unit,
    onSubmit: () -> Unit = {},
    autoFocus: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // What the user typed. The store's copy arrives a moment later, so the field doesn't read back its own echoes,
    // or a fast typist would lose letters. A text it didn't send is a change from elsewhere (Reset, a deal, a swap),
    // and the field takes it.
    var text by rememberSaveable { mutableStateOf(query) }
    val echoes = remember { Echoes() }
    LaunchedEffect(query) { echoes.arrived(query)?.let { text = it } }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) focusRequester.requestFocus() }

    val shown = pick?.let { airportLabel(it) } ?: text
    Column(Modifier.padding(top = 8.dp)) {
        // The same field as iOS's (D38): a white pill with a search icon, the field's name, the text and ✕.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                .border(1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ms_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(label, Modifier.width(40.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = shown,
                onValueChange = {
                    text = it
                    echoes.sent(it)
                    onQueryChange(it)
                },
                modifier = Modifier.weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = label }
                    .onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onSubmit()
                }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (shown.isEmpty()) {
                            Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                },
            )
            if (shown.isNotEmpty()) {
                val clear = stringResource(R.string.clear_field, label)
                IconButton(onClick = {
                    text = ""
                    echoes.sent("")
                    onQueryChange("")
                }) { Icon(painterResource(R.drawable.ms_close), contentDescription = clear, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
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

/**
 * Tells the store's echoes of what the field typed from changes made elsewhere. Echoes arrive late and in order, so
 * until the store shows the last text sent, any earlier text sent is an echo; anything else is an outside change.
 */
private class Echoes {
    private val pending = mutableSetOf<String>()
    private var last: String? = null

    fun sent(text: String) {
        pending += text
        last = text
    }

    /** The store's text, or null when it's only an echo the field already shows. */
    fun arrived(query: String): String? {
        if (query == last) {
            pending.clear()
            last = null
            return null
        }
        if (query in pending) return null
        pending.clear()
        last = null
        return query
    }
}

/** At most 5 rows; the store has already capped and ranked them (S14). */
@Composable
private fun SuggestionList(suggestions: List<AirportSuggestion>, onTap: (AirportSuggestion) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        // The card colour with a shadow, as on iOS; Material's tonal elevation would tint it pink.
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Column {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
internal fun airportLabel(suggestion: AirportSuggestion): String =
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
    EmptyState(R.drawable.ms_search_off, stringResource(R.string.filtered_empty_title), body = null, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ActiveFilterList(state)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.reset_filters), onReset)
        }
    }
}
