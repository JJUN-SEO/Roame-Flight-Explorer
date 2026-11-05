package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

private enum class SearchStep { Form, From, To, Dates }

/**
 * The full-screen search, like Roame's (Step 6.3c, UI_SPEC §2.0b), drawn the same as iOS's SearchScreen (D38): From and
 * To, with a swap button (S15), the dates on the shared calendar (K1–K5) and the cabin, then "Search flights". Every
 * choice reaches the store as it's made, so closing keeps it. Back leaves a step first, then the page.
 */
@Composable
fun SearchScreen(state: ExplorerState, store: ExplorerStore, onClose: () -> Unit, onSearch: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(SearchStep.Form) }
    var cabinOpen by rememberSaveable { mutableStateOf(false) }
    // K3: a start whose end hasn't been picked yet. "Any dates" clears it with the filter.
    val awaitingStart = rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (step == SearchStep.Form) onClose() else step = SearchStep.Form }

    when (step) {
        SearchStep.Form -> Column(Modifier.fillMaxSize()) {
            SubpageTopBar(stringResource(R.string.search_title), onBack = onClose, closes = true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                SearchForm(state, store, onOpen = { step = it }, onCabin = { cabinOpen = true })
                Spacer(Modifier.height(24.dp))
                PrimaryButton(stringResource(R.string.search_button), onSearch, Modifier.fillMaxWidth())
            }
        }
        SearchStep.From -> AirportStep(stringResource(R.string.search_from), onDone = { step = SearchStep.Form }) { done ->
            SearchField(
                label = stringResource(R.string.search_from),
                query = state.originQuery,
                pick = state.originPick,
                suggestions = state.originSuggestions,
                hint = state.originHint,
                onQueryChange = store::setOriginQuery,
                onPick = { codes ->
                    store.pickOrigin(codes)
                    done()
                },
                onSubmit = done,
                autoFocus = true,
            )
        }
        SearchStep.To -> AirportStep(stringResource(R.string.search_to), onDone = { step = SearchStep.Form }) { done ->
            SearchField(
                label = stringResource(R.string.search_to),
                query = state.destinationQuery,
                pick = state.destinationPick,
                suggestions = state.destinationSuggestions,
                hint = state.destinationHint,
                onQueryChange = store::setDestinationQuery,
                onPick = { codes ->
                    store.pickDestination(codes)
                    done()
                },
                onSubmit = done,
                autoFocus = true,
            )
        }
        SearchStep.Dates -> Column(Modifier.fillMaxSize()) {
            SubpageTopBar(kindName(FilterKind.Dates), onBack = { step = SearchStep.Form })
            DateRangeCalendar(state, store, awaitingStart, Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextLink(stringResource(R.string.any_dates), onClick = {
                    store.clearFilter(FilterKind.Dates)
                    awaitingStart.value = null
                })
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.done), onClick = { step = SearchStep.Form })
            }
        }
    }
    if (cabinOpen) FilterSheet(FilterKind.Cabin, state, store, onDismiss = { cabinOpen = false })
}

/** Roame's search card: four rows in one bordered card, with the swap button on the line between From and To. */
@Composable
private fun SearchForm(state: ExplorerState, store: ExplorerStore, onOpen: (SearchStep) -> Unit, onCabin: () -> Unit) {
    val placeholder = stringResource(R.string.search_placeholder)
    val from = state.originPick?.let { airportLabel(it) } ?: state.originQuery.takeIf { it.isNotBlank() }
    val to = state.destinationPick?.let { airportLabel(it) } ?: state.destinationQuery.takeIf { it.isNotBlank() }
    val start = state.filters.dateStart ?: state.options.minDate
    val end = state.filters.dateEnd ?: state.options.maxDate
    val dates = if (start != null && end != null) stringResource(R.string.date_range, chipDate(start), chipDate(end)) else kindName(FilterKind.Dates)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Box {
            Column {
                FieldRow(R.drawable.ms_flight_takeoff, stringResource(R.string.search_from), from, placeholder) { onOpen(SearchStep.From) }
                Divider()
                FieldRow(R.drawable.ms_flight_land, stringResource(R.string.search_to), to, placeholder) { onOpen(SearchStep.To) }
            }
            SwapButton(onClick = store::swapSides, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        }
        Divider()
        FieldRow(R.drawable.ms_calendar, kindName(FilterKind.Dates), dates, placeholder) { onOpen(SearchStep.Dates) }
        Divider()
        FieldRow(
            R.drawable.ms_seat,
            kindName(FilterKind.Cabin),
            state.filters.cabin ?: stringResource(R.string.landing_any_cabin),
            placeholder,
            trailing = R.drawable.ms_arrow_down,
            onClick = onCabin,
        )
    }
}

/** A field's name over its value, or the placeholder in grey; one button for TalkBack. */
@Composable
private fun FieldRow(
    @DrawableRes icon: Int,
    label: String,
    value: String?,
    placeholder: String,
    @DrawableRes trailing: Int? = null,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.search_field_description, label, value ?: placeholder)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
        if (trailing != null) Icon(painterResource(trailing), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** S15: a grey circle with ⇅, sitting on the line between From and To, as in Roame's search. */
@Composable
private fun SwapButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.search_swap)
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ms_swap_vert), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Picking From or To: the field, focused, with its suggestions under it. A pick or the Search key returns to the form. */
@Composable
private fun AirportStep(title: String, onDone: () -> Unit, field: @Composable (done: () -> Unit) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubpageTopBar(title, onBack = onDone)
        Column(Modifier.padding(horizontal = 16.dp)) { field(onDone) }
    }
}
